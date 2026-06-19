package com.seminario.plugin.sql.battle;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.seminario.plugin.sql.battle.wave.AlmacenGrant;
import com.seminario.plugin.sql.battle.wave.BattleWaveBank;
import com.seminario.plugin.sql.battle.wave.BattleWaveDefinition;
import com.seminario.plugin.sql.battle.wave.EnemySpawn;

/**
 * H2 in-memory database for SQL BATTLE game mode.
 *
 * Schema: 6 tables designed to force INNER JOIN usage.
 *
 * Tables:
 *   jugador       – player state (hp, mana, puntos_accion, oleada_actual, etapa_actual)
 *   tipos_item    – item catalog: name, category, mana cost, wave unlock (read-only for players)
 *   almacen       – pre-wave stockpile, FK → tipos_item
 *   inventario    – items committed for current wave, FK → tipos_item
 *   tipos_enemigo – enemy type definitions: name, weakness, description (read-only for players)
 *   enemigos      – wave enemies: tipo_id FK → tipos_enemigo, hp, estado, etapa_aparicion
 *
 * Key JOINs (forced by design):
 *   almacen   INNER JOIN tipos_item    ON almacen.item_id   = tipos_item.id
 *   inventario INNER JOIN tipos_item   ON inventario.item_id = tipos_item.id
 *   enemigos  INNER JOIN tipos_enemigo ON enemigos.tipo_id   = tipos_enemigo.id
 */
public class BattleSQLDatabase {

    private static final String DB_URL_PREFIX = "jdbc:h2:mem:sqlbattle_arena_";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    private Connection connection;
    private final Logger logger;
    private final String dbUrl;

    public BattleSQLDatabase(Logger logger, String arenaKey) {
        this.logger = logger;
        this.dbUrl = DB_URL_PREFIX + sanitizeArenaKey(arenaKey) + ";DB_CLOSE_DELAY=-1";
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Creates the schema, seeds reference data, and loads wave 1.
     * @return true if initialization succeeded
     */
    public boolean initialize() {
        try {
            Class.forName("org.h2.Driver");
            connection = DriverManager.getConnection(dbUrl, DB_USER, DB_PASSWORD);
            logger.info("[SQLBattle] Conectado a la base de datos H2 en memoria");

            createSharedSchema();
            seedReferenceData();
            createPrivateSchema();
            seedInitialGameState();

            logger.info("[SQLBattle] Base de datos inicializada correctamente");
            return true;

        } catch (ClassNotFoundException e) {
            logger.severe("[SQLBattle] Driver H2 no encontrado: " + e.getMessage());
            return false;
        } catch (SQLException e) {
            logger.severe("[SQLBattle] Error al inicializar la base de datos: " + e.getMessage());
            return false;
        }
    }

    /**
     * Resets mutable game state for a fresh game.
     * Reference tables (tipos_item, tipos_enemigo) and the schema are preserved.
     */
    public void resetForNewGame() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM inventario");
            stmt.execute("DELETE FROM almacen");
            stmt.execute("DELETE FROM jugador");
        }
        seedInitialGameState();
        logger.info("[SQLBattle] Estado de juego reiniciado");
    }

    /**
     * Transitions to the given sequential wave number using the BattleWaveBank.
     * Clears enemies and inventory, applies almacen grants, and spawns new enemies.
     * Wave numbers wrap cyclically when they exceed the total defined waves.
     */
    public void loadWave(int waveNumber) throws SQLException {
        BattleWaveDefinition def = BattleWaveBank.getByWaveNumber(waveNumber);
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM enemigos");
            // inventario is intentionally preserved so items carry over between waves
            stmt.execute("UPDATE jugador SET oleada_actual = " + waveNumber
                    + ", etapa_actual = 0, puntos_accion = 5 WHERE id = 1");
        }
        applyAlmacenGrants(def.getAlmacenGrants());
        applyDynamicWaveRewards(waveNumber);
        spawnEnemies(def.getEnemies());
        logger.info("[SQLBattle] Oleada " + waveNumber + " preparada: " + def.getName());
    }

    /**
     * Transitions to an explicit wave definition (e.g. a randomly chosen variant).
     */
    public void loadWaveDefinition(BattleWaveDefinition def) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM enemigos");
            // inventario is intentionally preserved so items carry over between waves
            stmt.execute("UPDATE jugador SET oleada_actual = " + def.getWaveId()
                    + ", etapa_actual = 0, puntos_accion = 5 WHERE id = 1");
        }
        applyAlmacenGrants(def.getAlmacenGrants());
        applyDynamicWaveRewards(def.getWaveId());
        spawnEnemies(def.getEnemies());
        logger.info("[SQLBattle] Oleada cargada: " + def.getName());
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("[SQLBattle] Conexion cerrada");
            } catch (SQLException e) {
                logger.warning("[SQLBattle] Error al cerrar la conexion: " + e.getMessage());
            }
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public int getPlayerActionPoints() throws SQLException {
        return getPlayerIntField("puntos_accion", 5);
    }

    public void setPlayerActionPoints(int points) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE jugador SET puntos_accion = ? WHERE id = 1")) {
            ps.setInt(1, Math.max(0, points));
            ps.executeUpdate();
        }
    }

    public int getCurrentWaveNumber() throws SQLException {
        return getPlayerIntField("oleada_actual", 1);
    }

    public int getCurrentStage() throws SQLException {
        return getPlayerIntField("etapa_actual", 0);
    }

    public void setCurrentStage(int stage) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE jugador SET etapa_actual = ? WHERE id = 1")) {
            ps.setInt(1, Math.max(0, Math.min(stage, 3)));
            ps.executeUpdate();
        }
    }

    public List<BattleEnemyRow> getEnemiesForStage(int stage) throws SQLException {
        List<BattleEnemyRow> enemies = new ArrayList<>();
        String sql = "SELECT id, tipo_id, hp, etapa_aparicion FROM enemigos WHERE estado = 'vivo' AND etapa_aparicion = ? ORDER BY id ASC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, stage);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    enemies.add(new BattleEnemyRow(
                        rs.getInt("id"),
                        rs.getInt("tipo_id"),
                        rs.getInt("hp"),
                        rs.getInt("etapa_aparicion")
                    ));
                }
            }
        }
        return enemies;
    }

    public int getPreparedSummonQuantity(int itemId, int maxActiveStage) throws SQLException {
        String sql = "SELECT COALESCE(SUM(i.cantidad), 0) AS total "
            + "FROM inventario i INNER JOIN tipos_item t ON i.item_id = t.id "
            + "WHERE i.item_id = ? AND t.categoria = 'invocacion' AND i.activo_en_etapa <= ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ps.setInt(2, maxActiveStage);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Math.max(0, rs.getInt("total"));
                }
            }
        }
        return 0;
    }

    public int getPreparedSummonQuantityForStage(int itemId, int exactStage) throws SQLException {
        String sql = "SELECT COALESCE(SUM(i.cantidad), 0) AS total "
            + "FROM inventario i INNER JOIN tipos_item t ON i.item_id = t.id "
            + "WHERE i.item_id = ? AND t.categoria = 'invocacion' AND i.activo_en_etapa = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ps.setInt(2, exactStage);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Math.max(0, rs.getInt("total"));
                }
            }
        }
        return 0;
    }

    /**
     * Captures current quantities in inventario by item_id.
     * Intended to be used before/after a modifying query in the same transaction.
     */
    public Map<Integer, Integer> snapshotInventarioQuantities() throws SQLException {
        Map<Integer, Integer> snapshot = new HashMap<>();
        String sql = "SELECT item_id, cantidad FROM inventario";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                snapshot.put(rs.getInt("item_id"), Math.max(0, rs.getInt("cantidad")));
            }
        }
        return snapshot;
    }

    /**
     * Applies deduction to almacen based on positive quantity deltas in inventario.
     * If almacen does not have enough stock for any increased item, throws SQLException.
     */
    public void consumeAlmacenForInventarioIncrease(Map<Integer, Integer> beforeSnapshot) throws SQLException {
        Map<Integer, Integer> afterSnapshot = snapshotInventarioQuantities();

        String updateSql = "UPDATE almacen SET cantidad = cantidad - ? WHERE item_id = ? AND cantidad >= ?";
        try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
            for (Map.Entry<Integer, Integer> after : afterSnapshot.entrySet()) {
                int itemId = after.getKey();
                int before = beforeSnapshot.getOrDefault(itemId, 0);
                int delta = after.getValue() - before;
                if (delta <= 0) {
                    continue;
                }

                ps.setInt(1, delta);
                ps.setInt(2, itemId);
                ps.setInt(3, delta);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    AlmacenItemStatus status = getAlmacenItemStatus(itemId);
                    String itemName = status != null ? status.itemName : ("item_id=" + itemId);
                    int available = status != null ? status.availableQuantity : 0;
                    if (available <= 0) {
                        throw new SQLException("Stock insuficiente en almacen: no quedan unidades de '" + itemName + "'.");
                    }
                    throw new SQLException("Stock insuficiente en almacen para '" + itemName
                            + "': disponibles " + available + ", intentaste reservar " + delta + ".");
                }
            }
        }
    }

    private AlmacenItemStatus getAlmacenItemStatus(int itemId) throws SQLException {
        String sql = "SELECT t.nombre, COALESCE(a.cantidad, 0) AS cantidad "
            + "FROM tipos_item t LEFT JOIN almacen a ON a.item_id = t.id WHERE t.id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new AlmacenItemStatus(rs.getString("nombre"), Math.max(0, rs.getInt("cantidad")));
                }
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Schema
    // -------------------------------------------------------------------------

    private void createSharedSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            // Reference: item catalog (read-only for players)
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS tipos_item (" +
                "  id               INT PRIMARY KEY," +
                "  nombre           VARCHAR(50)  NOT NULL," +
                "  categoria        VARCHAR(20)  NOT NULL" +
                "    CHECK (categoria IN ('arma','hechizo','invocacion','armadura','consumible'))," +
                "  costo_mana       INT DEFAULT 0 CHECK (costo_mana >= 0)," +
                "  oleada_desbloqueo INT DEFAULT 1 CHECK (oleada_desbloqueo >= 1)" +
                ")"
            );

            // Reference: enemy type catalog (read-only for players)
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS tipos_enemigo (" +
                "  id          INT PRIMARY KEY," +
                "  nombre      VARCHAR(50)  NOT NULL," +
                "  debilidad   VARCHAR(30)," +
                "  descripcion VARCHAR(200)" +
                ")"
            );

            // Shared wave state: enemies in the current wave (arena-wide)
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS enemigos (" +
                "  id              INT PRIMARY KEY," +
                "  tipo_id         INT NOT NULL," +
                "  hp              INT NOT NULL CHECK (hp > 0)," +
                "  hp_max          INT NOT NULL CHECK (hp_max > 0)," +
                "  estado          VARCHAR(20) DEFAULT 'vivo'" +
                "    CHECK (estado IN ('vivo','derrotado','aturdido'))," +
                "  etapa_aparicion INT DEFAULT 1 CHECK (etapa_aparicion BETWEEN 1 AND 3)," +
                "  FOREIGN KEY (tipo_id) REFERENCES tipos_enemigo(id)" +
                ")"
            );
        }
    }

    private void createPrivateSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Per-player connection-local state
            stmt.execute(
                "CREATE LOCAL TEMPORARY TABLE IF NOT EXISTS jugador (" +
                "  id            INT PRIMARY KEY," +
                "  nombre        VARCHAR(50) NOT NULL," +
                "  hp            INT NOT NULL CHECK (hp >= 0)," +
                "  mana          INT NOT NULL CHECK (mana >= 0)," +
                "  puntos_accion INT NOT NULL CHECK (puntos_accion >= 0)," +
                "  oleada_actual INT DEFAULT 1 CHECK (oleada_actual > 0)," +
                "  etapa_actual  INT DEFAULT 0 CHECK (etapa_actual BETWEEN 0 AND 3)" +
                ")"
            );

            stmt.execute(
                "CREATE LOCAL TEMPORARY TABLE IF NOT EXISTS almacen (" +
                "  item_id  INT PRIMARY KEY," +
                "  cantidad INT NOT NULL CHECK (cantidad >= 0)" +
                ")"
            );

            stmt.execute(
                "CREATE LOCAL TEMPORARY TABLE IF NOT EXISTS inventario (" +
                "  item_id         INT PRIMARY KEY," +
                "  cantidad        INT NOT NULL CHECK (cantidad >= 0)," +
                "  activo_en_etapa INT DEFAULT 1 CHECK (activo_en_etapa BETWEEN 1 AND 3)," +
                "  position        INT DEFAULT -1" +
                ")"
            );
        }
    }

    // -------------------------------------------------------------------------
    // Reference data (static catalog, not reset between games)
    // -------------------------------------------------------------------------

    private void seedReferenceData() throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            // Extended item catalog with wave-based unlocks.
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (1,  'Espada de Hierro',        'arma',        0, 1)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (2,  'Espada de Diamante',      'arma',        0, 3)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (3,  'Hacha de Madera',         'arma',        0, 1)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (4,  'Arco Elfico',             'arma',        0, 2)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (5,  'Armadura de Hierro',      'armadura',    0, 2)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (6,  'Armadura de Diamante',    'armadura',    0, 4)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (7,  'Hechizo de Fuego',        'hechizo',    30, 3)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (8,  'Hechizo de Hielo',        'hechizo',    25, 3)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (9,  'Pocion de Vida',          'consumible',  0, 1)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (10, 'Invocacion de Golem',     'invocacion', 40, 3)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (11, 'Flechas',                 'consumible',  0, 2)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (12, 'Escudo',                  'armadura',    0, 2)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (13, 'Filete Cocido',           'consumible',  0, 1)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (14, 'Pantalones de Hierro',    'armadura',    0, 2)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (15, 'Pantalones de Diamante',  'armadura',    0, 4)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (16, 'Casco de Hierro',         'armadura',    0, 2)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (17, 'Casco de Diamante',       'armadura',    0, 5)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (18, 'Botas de Hierro',         'armadura',    0, 2)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (19, 'Botas de Diamante',       'armadura',    0, 5)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (20, 'Hechizo Lluvia de Flechas','hechizo',   35, 4)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (21, 'TNT Temporizada',         'consumible',  0, 6)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (22, 'Ballesta de Asedio',      'arma',        0, 4)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (23, 'Manzana Dorada',          'consumible',  0, 5)");
            stmt.execute("MERGE INTO tipos_item (id, nombre, categoria, costo_mana, oleada_desbloqueo) KEY(id) VALUES (24, 'Antorcha',                'consumible',  0, 1)");

            // 8 enemy types with distinct weaknesses
            stmt.execute("MERGE INTO tipos_enemigo (id, nombre, debilidad, descripcion) KEY(id) VALUES (1, 'Zombi',          'luz',       'Muerto viviente lento pero resistente')");
            stmt.execute("MERGE INTO tipos_enemigo (id, nombre, debilidad, descripcion) KEY(id) VALUES (2, 'Esqueleto',      'espada',    'Arquero preciso, fragil en cuerpo a cuerpo')");
            stmt.execute("MERGE INTO tipos_enemigo (id, nombre, debilidad, descripcion) KEY(id) VALUES (3, 'Arana',          'fuego',     'Trepa paredes, veneno en cada ataque')");
            stmt.execute("MERGE INTO tipos_enemigo (id, nombre, debilidad, descripcion) KEY(id) VALUES (4, 'Creeper',        'hielo',     'Explosivo, se congela con hielo')");
            stmt.execute("MERGE INTO tipos_enemigo (id, nombre, debilidad, descripcion) KEY(id) VALUES (5, 'Enderman',       'agua',      'Teletransporte, evasivo y veloz')");
            stmt.execute("MERGE INTO tipos_enemigo (id, nombre, debilidad, descripcion) KEY(id) VALUES (6, 'Golem de Hierro','hacha',     'Inmune a flechas, vulnerable al hacha de madera')");
            stmt.execute("MERGE INTO tipos_enemigo (id, nombre, debilidad, descripcion) KEY(id) VALUES (7, 'Bruja',          'fuego',     'Lanza pociones, debil al fuego directo')");
            stmt.execute("MERGE INTO tipos_enemigo (id, nombre, debilidad, descripcion) KEY(id) VALUES (8, 'Dragon',         'hielo',     'Jefe final. Solo aparece en oleadas avanzadas')");
        }
    }

    // -------------------------------------------------------------------------
    // Game state seed
    // -------------------------------------------------------------------------

    private void seedInitialGameState() throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            // Player starting values
            stmt.execute(
                "INSERT INTO jugador (id, nombre, hp, mana, puntos_accion, oleada_actual, etapa_actual)"
                + " VALUES (1, 'Aventurero', 100, 100, 5, 1, 0)"
            );

            // Starting stockpile (wave 1): only starter gear is available; advanced items start at 0.
            stmt.execute(
                "INSERT INTO almacen (item_id, cantidad) VALUES"
                + " (1, 2),"    // Espada de Hierro
                + " (2, 0),"    // Espada de Diamante
                + " (3, 2),"    // Hacha de Madera
                + " (4, 0),"    // Arco Elfico
                + " (5, 0),"    // Armadura de Hierro
                + " (6, 0),"    // Armadura de Diamante
                + " (7, 0),"    // Hechizo de Fuego
                + " (8, 0),"    // Hechizo de Hielo
                + " (9, 4),"    // Pocion de Vida
                + " (10, 0),"   // Invocacion de Golem
                + " (11, 0),"   // Flechas
                + " (12, 0),"   // Escudo
                + " (13, 8),"   // Filete Cocido
                + " (14, 0),"   // Pantalones de Hierro
                + " (15, 0),"   // Pantalones de Diamante
                + " (16, 0),"   // Casco de Hierro
                + " (17, 0),"   // Casco de Diamante
                + " (18, 0),"   // Botas de Hierro
                + " (19, 0),"   // Botas de Diamante
                + " (20, 0),"   // Hechizo Lluvia de Flechas
                + " (21, 0),"   // TNT Temporizada
                + " (22, 0),"   // Ballesta de Asedio
                + " (23, 0)"    // Manzana Dorada
            );

        }
    }

    private static String sanitizeArenaKey(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "default";
        }
        String normalized = raw.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        return normalized.isEmpty() ? "default" : normalized;
    }

    // -------------------------------------------------------------------------
    // Wave loading helpers
    // -------------------------------------------------------------------------

    /**
     * Inserts enemies from a wave definition, assigning sequential IDs starting at 1.
     */
    private void spawnEnemies(List<EnemySpawn> enemies) throws SQLException {
        String sql = "INSERT INTO enemigos (id, tipo_id, hp, hp_max, estado, etapa_aparicion) VALUES (?, ?, ?, ?, 'vivo', ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < enemies.size(); i++) {
                EnemySpawn e = enemies.get(i);
                ps.setInt(1, i + 1);
                ps.setInt(2, e.getTipoId());
                ps.setInt(3, e.getHp());
                ps.setInt(4, e.getHp());
                ps.setInt(5, e.getEtapaAparicion());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Applies almacen grants for a wave:
     *   - If the item exists in almacen, its cantidad is incremented.
     *   - If the item does not exist yet, it is inserted.
     */
    private void applyAlmacenGrants(List<AlmacenGrant> grants) throws SQLException {
        if (grants == null || grants.isEmpty()) {
            return;
        }
        String updateSql = "UPDATE almacen SET cantidad = cantidad + ? WHERE item_id = ?";
        String insertSql = "INSERT INTO almacen (item_id, cantidad) VALUES (?, ?)";
        try (PreparedStatement upd = connection.prepareStatement(updateSql);
             PreparedStatement ins = connection.prepareStatement(insertSql)) {
            for (AlmacenGrant grant : grants) {
                upd.setInt(1, grant.getAddCantidad());
                upd.setInt(2, grant.getItemId());
                int rows = upd.executeUpdate();
                if (rows == 0) {
                    ins.setInt(1, grant.getItemId());
                    ins.setInt(2, grant.getAddCantidad());
                    ins.executeUpdate();
                }
            }
        }
    }

    private void applyDynamicWaveRewards(int waveNumber) throws SQLException {
        int normalizedWave = Math.max(1, waveNumber);

        // Base progression resources every wave.
        addToAlmacen(13, 2 + (normalizedWave / 3));
        addToAlmacen(9, (normalizedWave >= 3) ? 2 : 1);
        addToAlmacen(11, 10 + (normalizedWave * 6));

        // Periodic spikes.
        if ((normalizedWave % 2) == 0) {
            addToAlmacen(11, 12);
        }
        if ((normalizedWave % 2) == 1 && normalizedWave >= 3) {
            addToAlmacen(10, 1);
        }

        // Unlock tiers (0 -> >=1) and per-wave growth after unlock.
        unlockAtWave(normalizedWave, 2, 4, 1);
        unlockAtWave(normalizedWave, 2, 5, 1);
        unlockAtWave(normalizedWave, 2, 12, 1);
        unlockAtWave(normalizedWave, 2, 14, 1);
        unlockAtWave(normalizedWave, 2, 16, 1);
        unlockAtWave(normalizedWave, 2, 18, 1);

        unlockAtWave(normalizedWave, 3, 2, 1);
        unlockAtWave(normalizedWave, 3, 7, 1);
        unlockAtWave(normalizedWave, 3, 8, 1);
        if (normalizedWave >= 3) {
            addToAlmacen(7, 1);
            addToAlmacen(8, 1);
        }

        unlockAtWave(normalizedWave, 4, 6, 1);
        unlockAtWave(normalizedWave, 4, 15, 1);
        unlockAtWave(normalizedWave, 4, 20, 1);
        unlockAtWave(normalizedWave, 4, 22, 1);
        if (normalizedWave >= 4) {
            addToAlmacen(20, 1);
        }

        unlockAtWave(normalizedWave, 5, 17, 1);
        unlockAtWave(normalizedWave, 5, 19, 1);
        unlockAtWave(normalizedWave, 5, 23, 1);
        if (normalizedWave >= 5) {
            addToAlmacen(23, 1);
        }

        unlockAtWave(normalizedWave, 6, 21, 1);
        if (normalizedWave >= 6 && (normalizedWave % 2) == 0) {
            addToAlmacen(21, 1);
        }
    }

    private void addToAlmacen(int itemId, int amount) throws SQLException {
        if (amount <= 0) {
            return;
        }
        String updateSql = "UPDATE almacen SET cantidad = cantidad + ? WHERE item_id = ?";
        String insertSql = "INSERT INTO almacen (item_id, cantidad) VALUES (?, ?)";
        try (PreparedStatement upd = connection.prepareStatement(updateSql);
             PreparedStatement ins = connection.prepareStatement(insertSql)) {
            upd.setInt(1, amount);
            upd.setInt(2, itemId);
            int rows = upd.executeUpdate();
            if (rows == 0) {
                ins.setInt(1, itemId);
                ins.setInt(2, amount);
                ins.executeUpdate();
            }
        }
    }

    private void unlockAtWave(int currentWave, int unlockWave, int itemId, int quantity) throws SQLException {
        if (currentWave >= unlockWave) {
            ensureMinimumAlmacenQuantity(itemId, quantity);
        }
    }

    private void ensureMinimumAlmacenQuantity(int itemId, int minQuantity) throws SQLException {
        String selectSql = "SELECT cantidad FROM almacen WHERE item_id = ?";
        try (PreparedStatement select = connection.prepareStatement(selectSql)) {
            select.setInt(1, itemId);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    int current = rs.getInt("cantidad");
                    if (current >= minQuantity) {
                        return;
                    }
                    int delta = minQuantity - current;
                    addToAlmacen(itemId, delta);
                    return;
                }
            }
        }
        addToAlmacen(itemId, minQuantity);
    }

    private int getPlayerIntField(String columnName, int defaultValue) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + columnName + " FROM jugador WHERE id = 1")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return defaultValue;
    }

    private static final class AlmacenItemStatus {
        private final String itemName;
        private final int availableQuantity;

        private AlmacenItemStatus(String itemName, int availableQuantity) {
            this.itemName = itemName;
            this.availableQuantity = availableQuantity;
        }
    }

    public static class BattleEnemyRow {
        private final int enemyId;
        private final int tipoId;
        private final int hp;
        private final int stage;

        public BattleEnemyRow(int enemyId, int tipoId, int hp, int stage) {
            this.enemyId = enemyId;
            this.tipoId = tipoId;
            this.hp = hp;
            this.stage = stage;
        }

        public int getEnemyId() {
            return enemyId;
        }

        public int getTipoId() {
            return tipoId;
        }

        public int getHp() {
            return hp;
        }

        public int getStage() {
            return stage;
        }
    }

    public static class InventoryItemRow {
        private final int itemId;
        private final int cantidad;
        private final String nombre;
        private final String categoria;
        private final int position;

        public InventoryItemRow(int itemId, int cantidad, String nombre, String categoria) {
            this(itemId, cantidad, nombre, categoria, -1);
        }

        public InventoryItemRow(int itemId, int cantidad, String nombre, String categoria, int position) {
            this.itemId = itemId;
            this.cantidad = cantidad;
            this.nombre = nombre;
            this.categoria = categoria;
            this.position = position;
        }

        public int getItemId() { return itemId; }
        public int getCantidad() { return cantidad; }
        public String getNombre() { return nombre; }
        public String getCategoria() { return categoria; }
        /** Bukkit slot index where this item was last placed, or -1 if not yet placed. */
        public int getPosition() { return position; }
    }

    /**
     * Inserts a gift item into inventario if it is not already present.
     * Returns true if the row was inserted, false if it already existed.
     */
    public boolean insertGiftItemIfAbsent(int itemId, int cantidad, int stage) throws SQLException {
        String check = "SELECT COUNT(*) FROM inventario WHERE item_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(check)) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) return false;
            }
        }
        String insert = "INSERT INTO inventario (item_id, cantidad, activo_en_etapa, position) VALUES (?, ?, ?, -1)";
        try (PreparedStatement ps = connection.prepareStatement(insert)) {
            ps.setInt(1, itemId);
            ps.setInt(2, cantidad);
            ps.setInt(3, stage);
            ps.executeUpdate();
        }
        return true;
    }

    /**
     * Returns a snapshot of item_id → position for all rows currently in inventario.
     * Used to detect which items were removed after a DELETE so the physical inventory can be synced.
     */
    public Map<Integer, Integer> snapshotInventarioPositions() throws SQLException {
        Map<Integer, Integer> snap = new HashMap<>();
        String sql = "SELECT item_id, position FROM inventario";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                snap.put(rs.getInt("item_id"), rs.getInt("position"));
            }
        }
        return snap;
    }

    /**
     * Returns the number of non-armor items currently in inventario
     * (category != 'armadura'). Used to enforce the 7-slot hotbar limit.
     */
    public int getHotbarItemCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM inventario i JOIN tipos_item t ON i.item_id = t.id "
            + "WHERE t.categoria != 'armadura'";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    /**
     * Updates the position (Bukkit slot index) of an item in inventario.
     * Use -1 to mark as unassigned.
     */
    public void updateInventarioPosition(int itemId, int position) throws SQLException {
        String sql = "UPDATE inventario SET position = ? WHERE item_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, position);
            ps.setInt(2, itemId);
            ps.executeUpdate();
        }
    }

    /**
     * Returns items in inventario that activate at exactly the given stage,
     * excluding invocacion items (those are handled as spawned entities).
     */
    public List<InventoryItemRow> getInventoryItemsForExactStage(int stage) throws SQLException {
        List<InventoryItemRow> items = new ArrayList<>();
        String sql = "SELECT i.item_id, i.cantidad, t.nombre, t.categoria, i.position "
            + "FROM inventario i INNER JOIN tipos_item t ON i.item_id = t.id "
            + "WHERE i.activo_en_etapa = ? AND t.categoria != 'invocacion'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, stage);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new InventoryItemRow(
                        rs.getInt("item_id"),
                        rs.getInt("cantidad"),
                        rs.getString("nombre"),
                        rs.getString("categoria"),
                        rs.getInt("position")
                    ));
                }
            }
        }
        return items;
    }
}
