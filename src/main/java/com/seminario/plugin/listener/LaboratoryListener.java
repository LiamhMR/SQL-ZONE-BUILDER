package com.seminario.plugin.listener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import com.seminario.plugin.config.ConfigManager;
import com.seminario.plugin.manager.SQLDungeonManager;
import com.seminario.plugin.sql.battle.BattleSQLDatabase;
import com.seminario.plugin.model.MenuType;
import com.seminario.plugin.model.MenuZone;
import com.seminario.plugin.sql.SQLQueryResult;
import com.seminario.plugin.util.SQLResultBook;
import com.seminario.plugin.util.ZoneDetector;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Handles chat events when players are in laboratory zones
 * Allows free SQL experimentation and learning
 */
public class LaboratoryListener implements Listener {
    
    private final ConfigManager configManager;
    private final SQLDungeonManager sqlDungeonManager;
    
    // Track players currently in laboratory zones
    private final Set<UUID> playersInLaboratory;
    private final Set<UUID> playersInLaboratory2;

    // Persistent SQLBattle database instance for LABORATORY2 zones
    private BattleSQLDatabase battleLabDatabase;

    public LaboratoryListener(ConfigManager configManager, SQLDungeonManager sqlDungeonManager) {
        this.configManager = configManager;
        this.sqlDungeonManager = sqlDungeonManager;
        this.playersInLaboratory = new HashSet<>();
        this.playersInLaboratory2 = new HashSet<>();
    }

    /**
     * Set the persistent battle lab database used for LABORATORY2 zones.
     */
    public void setBattleLabDatabase(BattleSQLDatabase db) {
        this.battleLabDatabase = db;
    }
    
    /**
     * Handle chat events when players are in laboratory zones
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        
        // Check lab2 first
        if (playersInLaboratory2.contains(player.getUniqueId())) {
            event.setCancelled(true);
            String message = event.getMessage().trim();
            if (message.equalsIgnoreCase("exit") || message.equalsIgnoreCase("salir")) {
                exitLaboratory2(player);
            } else if (message.equalsIgnoreCase("help") || message.equalsIgnoreCase("ayuda")) {
                showLab2Help(player);
            } else if (message.equalsIgnoreCase("tables") || message.equalsIgnoreCase("tablas")) {
                showAvailableBattleTables(player);
            } else {
                processBattleLabQuery(player, message);
            }
            return;
        }

        // Check if player is in a laboratory zone
        if (!isPlayerInLaboratory(player)) {
            return;
        }
        
        // Cancel normal chat processing
        event.setCancelled(true);
        
        String message = event.getMessage().trim();
        
        // Handle special commands
        if (message.equalsIgnoreCase("exit") || message.equalsIgnoreCase("salir")) {
            exitLaboratory(player);
            return;
        }
        
        if (message.equalsIgnoreCase("help") || message.equalsIgnoreCase("ayuda")) {
            showLaboratoryHelp(player);
            return;
        }
        
        if (message.equalsIgnoreCase("tables") || message.equalsIgnoreCase("tablas")) {
            showAvailableTables(player);
            return;
        }
        
        // Process as SQL query
        processSQLQuery(player, message);
    }
    
    /**
     * Clean up when player quits
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playersInLaboratory.remove(event.getPlayer().getUniqueId());
        playersInLaboratory2.remove(event.getPlayer().getUniqueId());
    }
    
    /**
     * Check if player is currently in a laboratory zone
     */
    private boolean isPlayerInLaboratory(Player player) {
        // First check if we already know they're in laboratory
        if (playersInLaboratory.contains(player.getUniqueId())) {
            return true;
        }
        
        // Check current location against all zones
        for (MenuZone zone : configManager.getAllMenuZones().values()) {
            if (zone.getMenuType() == MenuType.LABORATORY && 
                ZoneDetector.isLocationInZone(player.getLocation(), zone)) {
                // Add to laboratory set
                playersInLaboratory.add(player.getUniqueId());
                
                // Show welcome message
                showLaboratoryWelcome(player);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Process SQL query in laboratory mode
     */
    private void processSQLQuery(Player player, String query) {
        try {
            // Validate and execute query
            SQLQueryResult result = sqlDungeonManager.getValidationEngine().executeQueryForLaboratory(query);
            
            if (result.hasError()) {
                // Show error message
                player.sendMessage(Component.text("❌ Error en la consulta SQL:", NamedTextColor.RED));
                player.sendMessage(Component.text(result.getError(), NamedTextColor.YELLOW));
                player.sendMessage(Component.text("", NamedTextColor.WHITE));
                player.sendMessage(Component.text("💡 Escribe 'help' para ver comandos disponibles", NamedTextColor.AQUA));
                return;
            }
            
            // Success - create result book
            player.sendMessage(Component.text("✅ Consulta ejecutada exitosamente!", NamedTextColor.GREEN));
            
            // Generate and give result book
            ItemStack book = SQLResultBook.createResultBook(player, query, result.getResultSet(), true);
            if (book != null) {
                player.getInventory().addItem(book);
                player.sendMessage(Component.text("📖 Se ha generado un libro con los resultados", NamedTextColor.AQUA));
            }
            
            player.sendMessage(Component.text("", NamedTextColor.WHITE));
            
        } catch (Exception e) {
            player.sendMessage(Component.text("❌ Error interno al procesar la consulta:", NamedTextColor.RED));
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.YELLOW));
        }
    }
    
    /**
     * Show welcome message when entering laboratory
     */
    private void showLaboratoryWelcome(Player player) {
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("🧪 ¡Bienvenido al Laboratorio SQL!", NamedTextColor.GOLD));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("Aquí puedes experimentar libremente con consultas SQL.", NamedTextColor.GREEN));
        player.sendMessage(Component.text("Todas las consultas que escribas se ejecutarán contra la base de datos.", NamedTextColor.AQUA));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("📋 Comandos disponibles:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("• help - Mostrar esta ayuda", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• tables - Ver tablas disponibles", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• exit - Salir del laboratorio", NamedTextColor.WHITE));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("✏️ Escribe cualquier consulta SQL para ejecutarla", NamedTextColor.GREEN));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
    }
    
    /**
     * Show laboratory help
     */
    private void showLaboratoryHelp(Player player) {
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("🧪 Laboratorio SQL - Ayuda", NamedTextColor.GOLD));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("📋 Comandos disponibles:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("• help/ayuda - Mostrar esta ayuda", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• tables/tablas - Ver tablas disponibles", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• exit/salir - Salir del laboratorio", NamedTextColor.WHITE));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("📝 Ejemplos de consultas:", NamedTextColor.AQUA));
        player.sendMessage(Component.text("• SELECT * FROM Jugadores", NamedTextColor.GRAY));
        player.sendMessage(Component.text("• SELECT nombre FROM Jugadores WHERE nivel > 20", NamedTextColor.GRAY));
        player.sendMessage(Component.text("• SELECT COUNT(*) FROM Construcciones", NamedTextColor.GRAY));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("💡 Los resultados se mostrarán en un libro", NamedTextColor.GREEN));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
    }
    
    /**
     * Show available database tables
     */
    private void showAvailableTables(Player player) {
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("📊 Tablas disponibles en la base de datos:", NamedTextColor.GOLD));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• Jugadores - Información de jugadores (id, nombre, nivel, mundo, diamantes, oro, esmeraldas)", NamedTextColor.GREEN));
        player.sendMessage(Component.text("• Inventarios - Items de inventarios (id, jugador_id, item, cantidad, rareza, encantado)", NamedTextColor.GREEN));
        player.sendMessage(Component.text("• Construcciones - Construcciones de jugadores (id, jugador_id, nombre, tipo, bloques_usados)", NamedTextColor.GREEN));
        player.sendMessage(Component.text("• Logros - Logros obtenidos (id, jugador_id, nombre, categoria, fecha_obtenido)", NamedTextColor.GREEN));
        player.sendMessage(Component.text("• Comercio - Transacciones comerciales (id, vendedor_id, comprador_id, item, cantidad, precio)", NamedTextColor.GREEN));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("💡 Usa 'SELECT * FROM [tabla]' para ver todo el contenido de una tabla", NamedTextColor.AQUA));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
    }
    
    /**
     * Handle exit from laboratory
     */
    private void exitLaboratory(Player player) {
        playersInLaboratory.remove(player.getUniqueId());
        player.sendMessage(Component.text("👋 Has salido del Laboratorio SQL", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("El chat ahora funciona normalmente", NamedTextColor.GREEN));
    }
    
    // -------------------------------------------------------------------------
    // LABORATORY2 (SQLBattle schema, SELECT-only)
    // -------------------------------------------------------------------------

    public void addPlayerToLab2(Player player) {
        if (!playersInLaboratory2.contains(player.getUniqueId())) {
            playersInLaboratory2.add(player.getUniqueId());
            showLab2Welcome(player);
        }
    }

    public void removePlayerFromLab2(Player player) {
        if (playersInLaboratory2.remove(player.getUniqueId())) {
            player.sendMessage(Component.text("👋 Has salido del Laboratorio SQL Battle", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("El chat ahora funciona normalmente", NamedTextColor.GREEN));
        }
    }

    public boolean isInLab2(Player player) {
        return playersInLaboratory2.contains(player.getUniqueId());
    }

    private void showLab2Welcome(Player player) {
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("⚔ ¡Bienvenido al Laboratorio SQL Battle!", NamedTextColor.GOLD));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("Aquí puedes explorar la base de datos de SQL Battle.", NamedTextColor.GREEN));
        player.sendMessage(Component.text("Solo se permiten consultas SELECT (no se puede modificar datos).", NamedTextColor.RED));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("📋 Comandos disponibles:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("• help - Mostrar esta ayuda", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• tables - Ver tablas disponibles", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• exit - Salir del laboratorio", NamedTextColor.WHITE));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("✏ Escribe una consulta SELECT para ejecutarla", NamedTextColor.GREEN));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
    }

    private void showLab2Help(Player player) {
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("⚔ Laboratorio SQL Battle - Ayuda", NamedTextColor.GOLD));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("📋 Comandos:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("• help/ayuda - Esta ayuda", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• tables/tablas - Ver tablas disponibles", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• exit/salir - Salir del laboratorio", NamedTextColor.WHITE));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("📝 Ejemplos:", NamedTextColor.AQUA));
        player.sendMessage(Component.text("• SELECT * FROM jugador", NamedTextColor.GRAY));
        player.sendMessage(Component.text("• SELECT nombre, costo_mana FROM tipos_item", NamedTextColor.GRAY));
        player.sendMessage(Component.text("• SELECT e.*, t.nombre FROM enemigos e INNER JOIN tipos_enemigo t ON e.tipo_id = t.id", NamedTextColor.GRAY));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("⚠ Solo consultas SELECT están permitidas", NamedTextColor.RED));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
    }

    private void showAvailableBattleTables(Player player) {
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("📊 Tablas SQL Battle disponibles:", NamedTextColor.GOLD));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("• jugador       – estado del jugador (hp, mana, puntos_accion, oleada_actual, etapa_actual)", NamedTextColor.GREEN));
        player.sendMessage(Component.text("• tipos_item    – catálogo de items (nombre, categoria, costo_mana, oleada_desbloqueo)", NamedTextColor.GREEN));
        player.sendMessage(Component.text("• almacen       – stock pre-oleada, FK → tipos_item", NamedTextColor.GREEN));
        player.sendMessage(Component.text("• inventario    – items equipados en oleada actual, FK → tipos_item", NamedTextColor.GREEN));
        player.sendMessage(Component.text("• tipos_enemigo – definiciones de tipos de enemigo (nombre, debilidad, descripcion)", NamedTextColor.GREEN));
        player.sendMessage(Component.text("• enemigos      – enemigos en la oleada actual, FK → tipos_enemigo", NamedTextColor.GREEN));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("💡 Practica JOINs: almacen/inventario ↔ tipos_item, enemigos ↔ tipos_enemigo", NamedTextColor.AQUA));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
    }

    private void exitLaboratory2(Player player) {
        playersInLaboratory2.remove(player.getUniqueId());
        player.sendMessage(Component.text("👋 Has salido del Laboratorio SQL Battle", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("El chat ahora funciona normalmente", NamedTextColor.GREEN));
    }

    private void processBattleLabQuery(Player player, String query) {
        // Enforce SELECT-only
        String trimmed = query.trim().toUpperCase();
        if (!trimmed.startsWith("SELECT") && !trimmed.startsWith("WITH")) {
            player.sendMessage(Component.text("❌ Solo se permiten consultas SELECT en este laboratorio.", NamedTextColor.RED));
            player.sendMessage(Component.text("⚠ No se permiten INSERT, UPDATE, DELETE, DROP ni DDL.", NamedTextColor.YELLOW));
            return;
        }

        if (battleLabDatabase == null || !battleLabDatabase.isConnected()) {
            player.sendMessage(Component.text("❌ La base de datos Battle Lab no está disponible.", NamedTextColor.RED));
            return;
        }

        try {
            java.sql.Connection conn = battleLabDatabase.getConnection();
            java.sql.Statement stmt = conn.createStatement(
                java.sql.ResultSet.TYPE_SCROLL_INSENSITIVE,
                java.sql.ResultSet.CONCUR_READ_ONLY);
            java.sql.ResultSet rs = stmt.executeQuery(query.trim());

            player.sendMessage(Component.text("✅ Consulta ejecutada exitosamente!", NamedTextColor.GREEN));

            ItemStack book = SQLResultBook.createResultBook(player, query, rs, true);
            if (book != null) {
                player.getInventory().addItem(book);
                player.sendMessage(Component.text("📖 Se ha generado un libro con los resultados", NamedTextColor.AQUA));
            }

        } catch (java.sql.SQLException e) {
            player.sendMessage(Component.text("❌ Error en la consulta SQL:", NamedTextColor.RED));
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.YELLOW));
            player.sendMessage(Component.text("", NamedTextColor.WHITE));
            player.sendMessage(Component.text("💡 Escribe 'help' para ver comandos disponibles", NamedTextColor.AQUA));
        } catch (Exception e) {
            player.sendMessage(Component.text("❌ Error interno al procesar la consulta:", NamedTextColor.RED));
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.YELLOW));
        }
    }

    // -------------------------------------------------------------------------
    // LABORATORY1 (original)
    // -------------------------------------------------------------------------

    /**
     * Add player to laboratory (called from movement detection)
     */
    public void addPlayerToLaboratory(Player player) {
        if (!playersInLaboratory.contains(player.getUniqueId())) {
            playersInLaboratory.add(player.getUniqueId());
            showLaboratoryWelcome(player);
        }
    }
    
    /**
     * Remove player from laboratory (called from movement detection)
     */
    public void removePlayerFromLaboratory(Player player) {
        if (playersInLaboratory.remove(player.getUniqueId())) {
            player.sendMessage(Component.text("👋 Has salido del Laboratorio SQL", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("El chat ahora funciona normalmente", NamedTextColor.GREEN));
        }
    }
    
    /**
     * Check if player is currently tracked as being in laboratory
     */
    public boolean isInLaboratory(Player player) {
        return playersInLaboratory.contains(player.getUniqueId());
    }
}