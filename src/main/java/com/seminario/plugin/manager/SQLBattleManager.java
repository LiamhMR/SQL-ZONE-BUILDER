package com.seminario.plugin.manager;

import java.sql.Connection;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Witch;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import com.seminario.plugin.config.ConfigManager;
import com.seminario.plugin.model.SQLBattleWorld;
import com.seminario.plugin.sql.battle.BattleExecutionResult;
import com.seminario.plugin.sql.battle.BattleQueryExecutor;
import com.seminario.plugin.sql.battle.BattleQueryValidator;
import com.seminario.plugin.sql.battle.BattleSQLDatabase;
import com.seminario.plugin.sql.battle.BattleValidationResult;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Manages SQL Battle world configuration and scenario setup.
 */
public class SQLBattleManager {

    private static final Logger logger = Logger.getLogger(SQLBattleManager.class.getName());
    private static final long BETWEEN_WAVES_TIME = 1000L; // daytime
    private static final long ACTIVE_WAVE_TIME = 14000L;  // early night: spiders aggressive, skeletons safe from burning
    private static final double PREPARATION_RADIUS = 4.5D;
    private static final double ENTRY_ZONE_VERTICAL_TOLERANCE = 2.0D;
    private static final int DEFAULT_WAVE_NUMBER = 1;
    private static final int BASE_PREWAVE_ACTION_POINTS = 5;
    private static final int SECOND_PREWAVE_ACTION_POINTS = 7;
    private static final int MAX_PREWAVE_ACTION_POINTS = 20;
    private static final int MIN_ACTION_POINT_COST = 1;
    private static final int FIRST_WAVE_STAGE = 1;
    private static final int GOLEM_SUMMON_ITEM_ID = 10;
    private static final int MAX_SUMMONED_GOLEMS = 4;
    private static final int MAX_PREVIEW_ROWS = 5;
    private static final int MAX_BOOK_ROWS = 30;
    private static final int MAX_ARENA_PARTICIPANTS = 4;
    private static final int LOBBY_COUNTDOWN_SECONDS = 90;
    private static final int FAST_START_COUNTDOWN_SECONDS = 10;
    private static final double CASTLE_PLAYER_PRIORITY_RADIUS = 20.0D;
    private static final double CASTLE_PULL_STRENGTH = 0.18D;
    private static final double CASTLE_DOMINATION_REQUIRED_SECONDS = 90.0D;
    private static final double CASTLE_DOMINATION_MIN_REAL_SECONDS = 30.0D;
    private static final long CASTLE_PARTICLE_INTERVAL_MILLIS = 2500L;
    private static final Sound[] PREWAVE_RELAX_MUSIC = new Sound[] {
        Sound.MUSIC_DISC_MALL,
        Sound.MUSIC_DISC_MELLOHI,
        Sound.MUSIC_DISC_FAR,
        Sound.MUSIC_DISC_WAIT
    };
    private static final Sound[] WAVE_BATTLE_MUSIC = new Sound[] {
        Sound.MUSIC_DISC_BLOCKS,
        Sound.MUSIC_DISC_CHIRP,
        Sound.MUSIC_DISC_CAT,
        Sound.MUSIC_DISC_STRAD,
        Sound.MUSIC_DISC_WARD
    };

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final NamespacedKey sqlBattleOwnerKey;
    private final NamespacedKey sqlBattleSessionKey;
    private final NamespacedKey sqlBattleRoleKey;
    private final NamespacedKey sqlBattlePrewaveStartItemKey;
    private final NamespacedKey sqlBattlePrewaveLeaveItemKey;
    private final NamespacedKey sqlBattleSpectatorTpItemKey;
    private final Map<UUID, Integer> playerForcedStage;
    private final Map<UUID, Integer> spectatorTargetCursor;
    private final Map<String, Boolean> worldWaveActive;
    private final Map<UUID, BattlePlayerSession> playerSessions;
    private final Map<String, BattleArenaSession> arenaSessions;
    private final Map<UUID, String> playerArenaWorld;
    private boolean gamemodeEnforcementEnabled;

    public SQLBattleManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.sqlBattleOwnerKey = new NamespacedKey(plugin, "sqlbattle_owner");
        this.sqlBattleSessionKey = new NamespacedKey(plugin, "sqlbattle_session");
        this.sqlBattleRoleKey = new NamespacedKey(plugin, "sqlbattle_role");
        this.sqlBattlePrewaveStartItemKey = new NamespacedKey(plugin, "sqlbattle_prewave_start_item");
        this.sqlBattlePrewaveLeaveItemKey = new NamespacedKey(plugin, "sqlbattle_prewave_leave_item");
        this.sqlBattleSpectatorTpItemKey = new NamespacedKey(plugin, "sqlbattle_spectator_tp_item");
        this.playerForcedStage = new HashMap<>();
        this.spectatorTargetCursor = new HashMap<>();
        this.worldWaveActive = new HashMap<>();
        this.playerSessions = new HashMap<>();
        this.arenaSessions = new HashMap<>();
        this.playerArenaWorld = new HashMap<>();
        this.gamemodeEnforcementEnabled = true;
    }

    public boolean createSQLBattle(World world) {
        if (configManager.isSQLBattle(world.getName())) {
            return false;
        }

        SQLBattleWorld battleWorld = new SQLBattleWorld(world.getName());
        configManager.addSQLBattle(battleWorld);
        setWaveActive(world.getName(), false); // inactivo: mobs desactivados mientras no inicia la oleada
        logger.info("Created SQL Battle in world: " + world.getName());
        return true;
    }

    public boolean removeSQLBattle(String worldName) {
        stopSessionsForWorld(worldName);
        worldWaveActive.remove(worldName);
        return configManager.removeSQLBattle(worldName);
    }

    public boolean isSQLBattle(String worldName) {
        return configManager.isSQLBattle(worldName);
    }

    public SQLBattleWorld getSQLBattle(String worldName) {
        return configManager.getSQLBattle(worldName);
    }

    public Map<String, SQLBattleWorld> getAllSQLBattles() {
        return configManager.getAllSQLBattles();
    }

    public boolean setStartLocation(String worldName, Location location) {
        return setWaveStartLocation(worldName, location);
    }

    public boolean setWorldEntryLocation(String worldName, Location location) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null) {
            return false;
        }

        battleWorld.setWorldEntryLocation(location);
        configManager.updateSQLBattle(battleWorld);
        return true;
    }

    public boolean setEntryZone(String worldName, Location pos1, Location pos2) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null) {
            return false;
        }

        battleWorld.setEntryZonePos1(pos1);
        battleWorld.setEntryZonePos2(pos2);
        configManager.updateSQLBattle(battleWorld);
        return true;
    }

    public boolean setWaveStartLocation(String worldName, Location location) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null) {
            return false;
        }

        battleWorld.setWaveStartLocation(location);
        configManager.updateSQLBattle(battleWorld);
        return true;
    }

    public boolean setPreparationLocation(String worldName, Location location) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null) {
            return false;
        }

        battleWorld.setPreparationLocation(location);
        configManager.updateSQLBattle(battleWorld);
        return true;
    }

    public boolean setCheckpointLocation(String worldName, Location location) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null) {
            return false;
        }

        battleWorld.setCheckpointLocation(location);
        configManager.updateSQLBattle(battleWorld);
        return true;
    }

    public boolean setSummonZone(String worldName, Location pos1, Location pos2) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null) {
            return false;
        }

        battleWorld.setSummonZonePos1(pos1);
        battleWorld.setSummonZonePos2(pos2);
        configManager.updateSQLBattle(battleWorld);
        return true;
    }

    public boolean setEnemySpawnZone(String worldName, Location pos1, Location pos2) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null) {
            return false;
        }

        battleWorld.setEnemySpawnPos1(pos1);
        battleWorld.setEnemySpawnPos2(pos2);
        configManager.updateSQLBattle(battleWorld);
        return true;
    }

    public boolean setCastleZone(String worldName, Location pos1, Location pos2) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null) {
            return false;
        }

        battleWorld.setCastleZonePos1(pos1);
        battleWorld.setCastleZonePos2(pos2);
        configManager.updateSQLBattle(battleWorld);
        return true;
    }

    public boolean isReady(String worldName) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        return battleWorld != null && battleWorld.isConfigured();
    }

    public boolean isExpandedReady(String worldName) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        return battleWorld != null && battleWorld.isExpandedConfigured();
    }

    /**
     * Sends SQL Battle configuration diagnostics and paints configured zones with particles.
     */
    public boolean debugShowConfiguration(Player viewer, String worldName) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null) {
            return false;
        }

        viewer.sendMessage(ChatColor.GOLD + "=== SQL Battle Debug: " + worldName + " ===");
        viewer.sendMessage(ChatColor.WHITE + "Configuración base completa: "
            + (battleWorld.isConfigured() ? ChatColor.GREEN + "sí" : ChatColor.RED + "no"));
        viewer.sendMessage(ChatColor.WHITE + "Modelo extendido completo: "
            + (battleWorld.isExpandedConfigured() ? ChatColor.GREEN + "sí" : ChatColor.RED + "no"));

        if (battleWorld.isExpandedConfigured()) {
            viewer.sendMessage(ChatColor.GREEN + "Estado: Mundo completamente configurado para SQL Battle.");
        } else {
            viewer.sendMessage(ChatColor.RED + "Estado: Configuración incompleta para SQL Battle.");
            viewer.sendMessage(ChatColor.YELLOW + "Faltantes detectados:");
            if (!battleWorld.hasEntryZone() && !battleWorld.hasWorldEntryLocation()) {
                viewer.sendMessage(ChatColor.GRAY + "- Zona de entrada SQL (//wand + 'entry')");
            }
            if (!battleWorld.hasWaveStartLocation()) {
                viewer.sendMessage(ChatColor.GRAY + "- Punto de inicio de oleada (wavestart)");
            }
            if (!battleWorld.hasCheckpointLocation()) {
                viewer.sendMessage(ChatColor.GRAY + "- Checkpoint");
            }
            if (!battleWorld.hasPreparationLocation()) {
                viewer.sendMessage(ChatColor.GRAY + "- Zona prewave");
            }
            if (!battleWorld.hasSummonZone()) {
                viewer.sendMessage(ChatColor.GRAY + "- Zona de invocación (summonzone)");
            }
            if (!battleWorld.hasEnemySpawnZone()) {
                viewer.sendMessage(ChatColor.GRAY + "- Zona de spawn enemigo (enemyspawn)");
            }
        }

        if (battleWorld.hasEntryZone()) {
            paintRegionDebug(viewer, battleWorld.getEntryZonePos1(), battleWorld.getEntryZonePos2(), Particle.END_ROD, "EntryZone");
        } else {
            paintPointDebug(viewer, battleWorld.getWorldEntryLocation(), Particle.END_ROD, "Entry");
        }
        paintPointDebug(viewer, battleWorld.getWaveStartLocation(), Particle.CRIT, "WaveStart");
        paintPointDebug(viewer, battleWorld.getCheckpointLocation(), Particle.TOTEM, "Checkpoint");
        paintPointDebug(viewer, battleWorld.getPreparationLocation(), Particle.ENCHANTMENT_TABLE, "Prewave");
        paintRegionDebug(viewer, battleWorld.getSummonZonePos1(), battleWorld.getSummonZonePos2(), Particle.VILLAGER_HAPPY, "SummonZone");
        paintRegionDebug(viewer, battleWorld.getEnemySpawnPos1(), battleWorld.getEnemySpawnPos2(), Particle.FLAME, "EnemySpawn");
        paintRegionDebug(viewer, battleWorld.getCastleZonePos1(), battleWorld.getCastleZonePos2(), Particle.SOUL_FIRE_FLAME, "CastleZone");

        viewer.sendMessage(ChatColor.AQUA + "Partículas debug emitidas para zonas configuradas.");
        return true;
    }

    private void paintPointDebug(Player viewer, Location location, Particle particle, String label) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        Location base = location.clone().add(0.0D, 0.2D, 0.0D);
        for (int index = 0; index < 12; index++) {
            double y = index * 0.2D;
            viewer.spawnParticle(particle, base.getX(), base.getY() + y, base.getZ(), 1, 0.05D, 0.05D, 0.05D, 0.0D);
        }

        double radius = 1.3D;
        for (int angle = 0; angle < 360; angle += 18) {
            double rad = Math.toRadians(angle);
            double x = base.getX() + (Math.cos(rad) * radius);
            double z = base.getZ() + (Math.sin(rad) * radius);
            viewer.spawnParticle(particle, x, base.getY(), z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        viewer.sendMessage(ChatColor.GRAY + "[Debug] " + label + ": "
            + location.getWorld().getName() + " @ "
            + String.format("%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ()));
    }

    private void paintRegionDebug(Player viewer, Location pos1, Location pos2, Particle particle, String label) {
        if (pos1 == null || pos2 == null || pos1.getWorld() == null || pos2.getWorld() == null) {
            return;
        }

        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX()) + 1.0D;
        double minY = Math.min(pos1.getY(), pos2.getY());
        double maxY = Math.max(pos1.getY(), pos2.getY()) + 1.0D;
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1.0D;

        // Lower rectangle
        drawLine(viewer, new Location(pos1.getWorld(), minX, minY, minZ), new Location(pos1.getWorld(), maxX, minY, minZ), particle);
        drawLine(viewer, new Location(pos1.getWorld(), maxX, minY, minZ), new Location(pos1.getWorld(), maxX, minY, maxZ), particle);
        drawLine(viewer, new Location(pos1.getWorld(), maxX, minY, maxZ), new Location(pos1.getWorld(), minX, minY, maxZ), particle);
        drawLine(viewer, new Location(pos1.getWorld(), minX, minY, maxZ), new Location(pos1.getWorld(), minX, minY, minZ), particle);

        // Upper rectangle
        drawLine(viewer, new Location(pos1.getWorld(), minX, maxY, minZ), new Location(pos1.getWorld(), maxX, maxY, minZ), particle);
        drawLine(viewer, new Location(pos1.getWorld(), maxX, maxY, minZ), new Location(pos1.getWorld(), maxX, maxY, maxZ), particle);
        drawLine(viewer, new Location(pos1.getWorld(), maxX, maxY, maxZ), new Location(pos1.getWorld(), minX, maxY, maxZ), particle);
        drawLine(viewer, new Location(pos1.getWorld(), minX, maxY, maxZ), new Location(pos1.getWorld(), minX, maxY, minZ), particle);

        // Vertical edges
        drawLine(viewer, new Location(pos1.getWorld(), minX, minY, minZ), new Location(pos1.getWorld(), minX, maxY, minZ), particle);
        drawLine(viewer, new Location(pos1.getWorld(), maxX, minY, minZ), new Location(pos1.getWorld(), maxX, maxY, minZ), particle);
        drawLine(viewer, new Location(pos1.getWorld(), maxX, minY, maxZ), new Location(pos1.getWorld(), maxX, maxY, maxZ), particle);
        drawLine(viewer, new Location(pos1.getWorld(), minX, minY, maxZ), new Location(pos1.getWorld(), minX, maxY, maxZ), particle);

        viewer.sendMessage(ChatColor.GRAY + "[Debug] " + label + ": "
            + pos1.getWorld().getName() + " ["
            + String.format("%.1f, %.1f, %.1f", pos1.getX(), pos1.getY(), pos1.getZ())
            + " -> "
            + String.format("%.1f, %.1f, %.1f", pos2.getX(), pos2.getY(), pos2.getZ())
            + "]");
    }

    private void drawLine(Player viewer, Location from, Location to, Particle particle) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double distance = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
        int points = Math.max(6, Math.min(180, (int) (distance * 4.0D)));

        for (int index = 0; index <= points; index++) {
            double t = (double) index / (double) points;
            double x = from.getX() + (dx * t);
            double y = from.getY() + (dy * t);
            double z = from.getZ() + (dz * t);
            viewer.spawnParticle(particle, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    public void setWaveActive(String worldName, boolean active) {
        worldWaveActive.put(worldName, active);
        World world = plugin.getServer().getWorld(worldName);
        if (world != null) {
            world.setDifficulty(active ? Difficulty.NORMAL : Difficulty.PEACEFUL);
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(0);
            world.setThunderDuration(0);
            world.setTime(active ? ACTIVE_WAVE_TIME : BETWEEN_WAVES_TIME);
        }
    }

    public boolean isWaveActive(String worldName) {
        return worldWaveActive.getOrDefault(worldName, false);
    }

    public boolean setWorldDifficulty(String worldName, Difficulty difficulty) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            return false;
        }
        world.setDifficulty(difficulty);
        return true;
    }

    public Difficulty getWorldDifficulty(String worldName) {
        World world = plugin.getServer().getWorld(worldName);
        return world != null ? world.getDifficulty() : null;
    }

    public boolean startForPlayer(Player player) {
        String worldName = player.getWorld().getName();
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null || !battleWorld.isConfigured()) {
            return false;
        }

        BattleArenaSession arena = getOrCreateArena(worldName);
        UUID playerId = player.getUniqueId();

        if (arena.state == ArenaState.IN_GAME || isArenaInProgress(arena) || hasActiveMatchSessions(worldName)) {
            movePlayerToSpectator(player, arena, battleWorld, true);
            player.sendMessage(ChatColor.YELLOW + "Partida en curso. Entraste como espectador.");
            return true;
        }

        if (arena.participants.contains(playerId)) {
            if (arena.state == ArenaState.STARTING || arena.state == ArenaState.WAITING) {
                givePrewaveStartItem(player);
                givePrewaveLeaveItem(player);
            }
            if (arena.countdownBar != null) {
                arena.countdownBar.addPlayer(player);
            }
            player.sendMessage(ChatColor.YELLOW + "Ya estas registrado como participante en SQL Battle.");
            return true;
        }
        if (arena.spectators.contains(playerId)) {
            applySimulatedSpectatorMode(player);
            givePrewaveLeaveItem(player);
            giveSpectatorTeleportItem(player);
            player.sendMessage(ChatColor.YELLOW + "Ya estas registrado como espectador en SQL Battle.");
            return true;
        }

        if (arena.participants.size() >= MAX_ARENA_PARTICIPANTS) {
            movePlayerToSpectator(player, arena, battleWorld, true);
            player.sendMessage(ChatColor.YELLOW + "Arena llena (max " + MAX_ARENA_PARTICIPANTS + "). Entraste como espectador.");
            return true;
        }

        arena.participants.add(playerId);
        playerArenaWorld.put(playerId, worldName);
        clearSimulatedSpectatorMode(player);
        player.setGameMode(GameMode.ADVENTURE);
        clearQueryResultBooks(player);
        clearPrewaveStartItem(player);
        givePrewaveStartItem(player);
        givePrewaveLeaveItem(player);

        if (arena.countdownBar != null) {
            arena.countdownBar.addPlayer(player);
            updateArenaCountdownBar(arena);
        }

        int current = arena.participants.size();
        player.sendMessage(ChatColor.GREEN + "Te uniste como participante de SQL Battle (" + current + "/" + MAX_ARENA_PARTICIPANTS + ").");

        if (arena.state == ArenaState.WAITING) {
            if (current == 1) {
                startArenaCountdown(arena);
            } else {
                broadcastArenaMessage(arena, ChatColor.AQUA + player.getName() + ChatColor.GRAY + " se unio al lobby (" + current + "/" + MAX_ARENA_PARTICIPANTS + ").");
            }
            return true;
        }

        arena.voteEligible.add(playerId);
        player.sendMessage(ChatColor.YELLOW + "Te uniste con countdown activo. Puedes votar en esta ronda.");
        broadcastArenaMessage(arena, ChatColor.AQUA + player.getName() + ChatColor.GRAY + " se unio al lobby en curso (" + current + "/" + MAX_ARENA_PARTICIPANTS + ").");
        return true;
    }

    public int startForWorld(World world) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(world.getName());
        if (battleWorld == null || !battleWorld.isConfigured()) {
            return -1;
        }

        int count = 0;
        for (Player player : world.getPlayers()) {
            if (startForPlayer(player)) {
                count++;
            }
        }
        return count;
    }

    private BattleArenaSession getOrCreateArena(String worldName) {
        return arenaSessions.computeIfAbsent(worldName, BattleArenaSession::new);
    }

    private boolean isArenaInProgress(BattleArenaSession arena) {
        for (UUID participantId : arena.participants) {
            BattlePlayerSession session = playerSessions.get(participantId);
            if (session == null) {
                continue;
            }
            if (session.phase == BattleSessionPhase.PREPARATION || session.phase == BattleSessionPhase.WAVE_ACTIVE) {
                return true;
            }
        }
        return false;
    }

    private boolean hasActiveMatchSessions(String worldName) {
        for (BattlePlayerSession session : playerSessions.values()) {
            if (!session.worldName.equalsIgnoreCase(worldName)) {
                continue;
            }
            if (session.phase == BattleSessionPhase.PREPARATION
                    || session.phase == BattleSessionPhase.WAVE_ACTIVE
                    || session.phase == BattleSessionPhase.BETWEEN_WAVES) {
                return true;
            }
        }
        return false;
    }

    private void startArenaCountdown(BattleArenaSession arena) {
        stopArenaCountdown(arena);
        clearArenaDominationBar(arena);
        arena.state = ArenaState.STARTING;
        arena.countdownRemaining = LOBBY_COUNTDOWN_SECONDS;
        arena.countdownMax = LOBBY_COUNTDOWN_SECONDS;
        arena.startVotes.clear();
        arena.voteEligible.clear();
        arena.prewaveStartVotes.clear();
        arena.waveReadyPlayers.clear();
        arena.voteEligible.addAll(arena.participants);
        createArenaCountdownBar(arena);
        updateArenaCountdownBar(arena);
        setWaveActive(arena.worldName, false);

        broadcastArenaMessage(arena, ChatColor.GOLD + "=== SQL Battle Multijugador ===");
        broadcastArenaMessage(arena, ChatColor.YELLOW + "Countdown iniciado: " + LOBBY_COUNTDOWN_SECONDS + "s.");
        broadcastArenaMessage(arena, ChatColor.GRAY + "Usa el item de iniciar oleada para votar y acelerar a 10s.");

        arena.countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (arena.state != ArenaState.STARTING) {
                stopArenaCountdown(arena);
                return;
            }

            if (arena.participants.isEmpty()) {
                resetArenaToWaiting(arena, false);
                return;
            }

            int seconds = arena.countdownRemaining;
            if (seconds <= 0) {
                startArenaMatch(arena);
                return;
            }

            if (seconds <= 10 || seconds % 15 == 0) {
                broadcastArenaMessage(arena, ChatColor.AQUA + "Inicio en " + seconds + "s...");
            }

            updateArenaCountdownBar(arena);

            arena.countdownRemaining = Math.max(0, arena.countdownRemaining - 1);
        }, 20L, 20L);
    }

    private void stopArenaCountdown(BattleArenaSession arena) {
        if (arena.countdownTask != null) {
            arena.countdownTask.cancel();
            arena.countdownTask = null;
        }
    }

    private void resetArenaToWaiting(BattleArenaSession arena, boolean notify) {
        stopArenaCountdown(arena);
        clearArenaDominationBar(arena);
        arena.state = ArenaState.WAITING;
        arena.countdownRemaining = 0;
        arena.countdownMax = 0;
        arena.voteEligible.clear();
        arena.startVotes.clear();
        arena.prewaveStartVotes.clear();
        arena.waveReadyPlayers.clear();
        clearArenaCountdownBar(arena);
        if (notify) {
            broadcastArenaMessage(arena, ChatColor.YELLOW + "Lobby SQL Battle en espera de participantes.");
        }
    }

    private void startArenaMatch(BattleArenaSession arena) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(arena.worldName);
        if (battleWorld == null || !battleWorld.isConfigured()) {
            resetArenaToWaiting(arena, true);
            return;
        }

        List<Player> onlineParticipants = new ArrayList<>();
        for (UUID participantId : new ArrayList<>(arena.participants)) {
            Player participant = Bukkit.getPlayer(participantId);
            if (participant == null || !participant.isOnline() || !participant.getWorld().getName().equalsIgnoreCase(arena.worldName)) {
                arena.participants.remove(participantId);
                arena.voteEligible.remove(participantId);
                arena.startVotes.remove(participantId);
                playerArenaWorld.remove(participantId);
                continue;
            }
            onlineParticipants.add(participant);
        }

        if (onlineParticipants.isEmpty()) {
            resetArenaToWaiting(arena, true);
            return;
        }

        stopArenaCountdown(arena);
        clearArenaCountdownBar(arena);
        clearArenaDominationBar(arena);
        arena.state = ArenaState.IN_GAME;

        int participantCount = Math.max(1, Math.min(MAX_ARENA_PARTICIPANTS, onlineParticipants.size()));
        broadcastArenaMessage(arena, ChatColor.GREEN + "¡Partida SQL Battle iniciada con " + participantCount + " jugador(es)!");

        int started = 0;
        for (Player participant : onlineParticipants) {
            if (beginPreparationSession(participant, battleWorld, participantCount)) {
                started++;
            } else {
                arena.participants.remove(participant.getUniqueId());
                arena.voteEligible.remove(participant.getUniqueId());
                arena.startVotes.remove(participant.getUniqueId());
                playerArenaWorld.remove(participant.getUniqueId());
                participant.sendMessage(ChatColor.RED + "No se pudo iniciar tu sesion SQL Battle.");
            }
        }

        for (UUID spectatorId : new ArrayList<>(arena.spectators)) {
            Player spectator = Bukkit.getPlayer(spectatorId);
            if (spectator == null || !spectator.isOnline()) {
                arena.spectators.remove(spectatorId);
                playerArenaWorld.remove(spectatorId);
                continue;
            }
            applySimulatedSpectatorMode(spectator);
            givePrewaveLeaveItem(spectator);
            giveSpectatorTeleportItem(spectator);
            if (battleWorld.hasCheckpointLocation()) {
                spectator.teleport(battleWorld.getCheckpointLocation());
            } else if (battleWorld.hasPreparationLocation()) {
                spectator.teleport(battleWorld.getPreparationLocation());
            }
            spectator.sendMessage(ChatColor.AQUA + "Estas espectando la partida SQL Battle.");
        }

        if (started <= 0) {
            finishArenaMatch(arena, ChatColor.RED + "La partida no pudo iniciar por errores de sesion.");
        }
    }

    private void movePlayerToSpectator(Player player, BattleArenaSession arena, SQLBattleWorld battleWorld, boolean teleport) {
        UUID playerId = player.getUniqueId();
        arena.participants.remove(playerId);
        arena.voteEligible.remove(playerId);
        arena.startVotes.remove(playerId);
        arena.spectators.add(playerId);
        playerArenaWorld.put(playerId, arena.worldName);

        applySimulatedSpectatorMode(player);
        clearPrewaveStartItem(player);
        givePrewaveLeaveItem(player);
        giveSpectatorTeleportItem(player);
        if (teleport) {
            if (battleWorld.hasCheckpointLocation()) {
                player.teleport(battleWorld.getCheckpointLocation());
            } else if (battleWorld.hasPreparationLocation()) {
                player.teleport(battleWorld.getPreparationLocation());
            }
        }
    }

    private void broadcastArenaMessage(BattleArenaSession arena, String message) {
        for (UUID playerId : arena.getAllPlayers()) {
            Player target = Bukkit.getPlayer(playerId);
            if (target != null && target.isOnline()) {
                target.sendMessage(message);
            }
        }
    }

    /**
     * Sends player to the configured checkpoint and updates respawn there.
     */
    public boolean respawnAtCheckpoint(Player player) {
        String worldName = player.getWorld().getName();
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null || !battleWorld.hasCheckpointLocation()) {
            return false;
        }

        player.teleport(battleWorld.getCheckpointLocation());
        player.setBedSpawnLocation(battleWorld.getCheckpointLocation(), true);
        return true;
    }

    /**
     * Resets a player state for manual SQL Battle testing.
     */
    public boolean resetPlayerForDebug(Player player) {
        String worldName = player.getWorld().getName();
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null || (!battleWorld.hasStartLocation() && !battleWorld.hasCheckpointLocation())) {
            return false;
        }

        endPreparationSession(player, true);

        if (battleWorld.hasCheckpointLocation()) {
            player.teleport(battleWorld.getCheckpointLocation());
            player.setBedSpawnLocation(battleWorld.getCheckpointLocation(), true);
        } else {
            player.teleport(battleWorld.getStartLocation());
        }

        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) != null
            ? player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()
            : 20.0D;
        player.setHealth(Math.min(maxHealth, 20.0D));
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        clearForcedStage(player);
        return true;
    }

    public int resetWorldForDebug(World world) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(world.getName());
        if (battleWorld == null || (!battleWorld.hasStartLocation() && !battleWorld.hasCheckpointLocation())) {
            return -1;
        }

        setWaveActive(world.getName(), false); // sin oleada activa: PEACEFUL

        int count = 0;
        for (Player player : world.getPlayers()) {
            if (resetPlayerForDebug(player)) {
                count++;
            }
        }
        return count;
    }

    public void setForcedStage(Player player, int stage) {
        playerForcedStage.put(player.getUniqueId(), stage);
    }

    public int getForcedStage(Player player) {
        return playerForcedStage.getOrDefault(player.getUniqueId(), -1);
    }

    public void clearForcedStage(Player player) {
        playerForcedStage.remove(player.getUniqueId());
        updatePreparationSidebar(player);
    }

    public boolean shouldCapturePreparationChat(Player player) {
        BattlePlayerSession session = getActiveSession(player);
        return session != null
            && session.phase == BattleSessionPhase.PREPARATION
            && isPlayerInPreparationZone(player);
    }

    public void handlePreparationChat(Player player, String message) {
        BattlePlayerSession session = getActiveSession(player);
        if (session == null) {
            return;
        }

        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            player.sendMessage(ChatColor.RED + "La consulta está vacía.");
            return;
        }

        if (trimmed.equalsIgnoreCase("help") || trimmed.equalsIgnoreCase("ayuda")) {
            showPreparationHelp(player);
            return;
        }

        if (trimmed.equalsIgnoreCase("costos") || trimmed.equalsIgnoreCase("costes") || trimmed.equalsIgnoreCase("costs")) {
            showPreparationCosts(player);
            return;
        }

        if (trimmed.equalsIgnoreCase("status") || trimmed.equalsIgnoreCase("estado")) {
            showPreparationStatus(player, session);
            return;
        }

        if (trimmed.equalsIgnoreCase("sugerencias") || trimmed.equalsIgnoreCase("tips")
                || trimmed.equalsIgnoreCase("queries") || trimmed.equalsIgnoreCase("consultas")) {
            showPreparationSuggestions(player);
            return;
        }

        if (trimmed.equalsIgnoreCase("exit") || trimmed.equalsIgnoreCase("salir")) {
            cleanupPlayerSession(player);
            player.sendMessage(ChatColor.YELLOW + "Sesión SQL Battle finalizada.");
            return;
        }

        processBattleQuery(player, session, trimmed);
    }

    public void cleanupPlayerSession(Player player) {
        endPreparationSession(player, true);
        removePlayerFromArena(player, true);
    }

    public boolean leaveBattleSessionFromItem(Player player) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(player.getWorld().getName());
        boolean hadArena = playerArenaWorld.containsKey(player.getUniqueId());
        boolean hadSession = getActiveSession(player) != null;
        if (!hadArena && !hadSession) {
            return false;
        }

        cleanupPlayerSession(player);
        teleportPlayerToHub(player, battleWorld);
        player.sendMessage(ChatColor.YELLOW + "Saliste de la partida SQL Battle.");
        return true;
    }

    public boolean forceStartWaveFromPreparation(Player player) {
        BattlePlayerSession session = getActiveSession(player);
        if (session == null) {
            return registerLobbyStartVote(player);
        }
        if (session.phase != BattleSessionPhase.PREPARATION) {
            player.sendMessage(ChatColor.RED + "Solo puedes usar este item durante la prewave.");
            return false;
        }

        return registerPrewaveStartVote(player, session, "La oleada fue iniciada por votacion del equipo.");
    }

    public boolean executePreparationSuggestion(Player player, int suggestionId) {
        BattlePlayerSession session = getActiveSession(player);
        if (session == null) {
            player.sendMessage(ChatColor.RED + "No tienes una sesión SQL Battle activa.");
            return false;
        }
        if (session.phase != BattleSessionPhase.PREPARATION) {
            player.sendMessage(ChatColor.RED + "Las sugerencias solo se pueden ejecutar durante prewave.");
            return false;
        }
        if (!isPlayerInPreparationZone(player)) {
            player.sendMessage(ChatColor.RED + "Debes estar dentro de la zona prewave/entry para ejecutar sugerencias.");
            return false;
        }

        String suggestedQuery = getSuggestedQueryById(suggestionId);
        if (suggestedQuery == null) {
            player.sendMessage(ChatColor.RED + "Sugerencia inválida. Usa IDs del 1 al 4.");
            return false;
        }

        processBattleQuery(player, session, suggestedQuery);
        return true;
    }

    public void showSchemaOverview(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== SQL Battle Schema (PK/FK) ===");
        player.sendMessage(ChatColor.YELLOW + "jugador" + ChatColor.GRAY + "(id PK, nombre, hp, mana, puntos_accion, oleada_actual, etapa_actual)");
        player.sendMessage(ChatColor.YELLOW + "tipos_item" + ChatColor.GRAY + "(id PK, nombre, categoria, costo_mana, oleada_desbloqueo)");
        player.sendMessage(ChatColor.YELLOW + "almacen" + ChatColor.GRAY + "(item_id PK, cantidad)");
        player.sendMessage(ChatColor.DARK_GRAY + "  FK: almacen.item_id -> tipos_item.id");
        player.sendMessage(ChatColor.YELLOW + "inventario" + ChatColor.GRAY + "(item_id PK, cantidad, activo_en_etapa)");
        player.sendMessage(ChatColor.DARK_GRAY + "  FK: inventario.item_id -> tipos_item.id");
        player.sendMessage(ChatColor.YELLOW + "tipos_enemigo" + ChatColor.GRAY + "(id PK, nombre, debilidad, descripcion)");
        player.sendMessage(ChatColor.YELLOW + "enemigos" + ChatColor.GRAY + "(id PK, tipo_id FK, hp, hp_max, estado, etapa_aparicion)");
        player.sendMessage(ChatColor.DARK_GRAY + "  FK: enemigos.tipo_id -> tipos_enemigo.id");
        player.sendMessage(ChatColor.GRAY + "Checks: categoria valida, hp/hp_max > 0, etapa 1-3, activo_en_etapa 1-3, cantidad >= 0");
        player.sendMessage(ChatColor.DARK_GRAY + "JOIN tipico: enemigos.tipo_id -> tipos_enemigo.id, inventario/almacen.item_id -> tipos_item.id");
    }

    public void stopSessionsForWorld(String worldName) {
        Map<UUID, BattlePlayerSession> snapshot = new HashMap<>(playerSessions);
        for (Map.Entry<UUID, BattlePlayerSession> entry : snapshot.entrySet()) {
            BattlePlayerSession session = entry.getValue();
            if (!session.worldName.equalsIgnoreCase(worldName)) {
                continue;
            }

            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                endPreparationSession(player, true);
            } else {
                session.database.close();
                playerSessions.remove(entry.getKey());
            }
        }

        BattleArenaSession arena = arenaSessions.get(worldName);
        if (arena != null) {
            finishArenaMatch(arena, ChatColor.YELLOW + "Arena SQL Battle detenida por administracion.");
            arenaSessions.remove(worldName);
        }
    }

    public void shutdown() {
        for (BattleArenaSession arena : arenaSessions.values()) {
            stopArenaCountdown(arena);
        }
        arenaSessions.clear();
        playerArenaWorld.clear();

        for (BattlePlayerSession session : playerSessions.values()) {
            session.database.close();
        }
        playerSessions.clear();
    }

    public void logStatus(String worldName) {
        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null) {
            logger.warning("SQL Battle not found: " + worldName);
            return;
        }

        Difficulty difficulty = getWorldDifficulty(worldName);
        logger.info("SQL Battle '" + worldName + "' ready=" + battleWorld.isConfigured()
            + ", waveActive=" + isWaveActive(worldName)
            + ", difficulty=" + (difficulty != null ? difficulty : "unknown"));
    }

    private boolean beginPreparationSession(Player player, SQLBattleWorld battleWorld, int teamSize) {
        endPreparationSession(player, false);

        BattleSQLDatabase database = new BattleSQLDatabase(plugin.getLogger(), battleWorld.getWorldName());
        if (!database.initialize()) {
            return false;
        }

        try {
            database.loadWave(DEFAULT_WAVE_NUMBER);
            database.setPlayerActionPoints(calculatePreparationActionPoints(DEFAULT_WAVE_NUMBER, teamSize));
            int forcedStage = getForcedStage(player);
            if (forcedStage > 0) {
                database.setCurrentStage(forcedStage);
            }
        } catch (Exception e) {
            database.close();
            logger.warning("Could not initialize SQL Battle wave for player '" + player.getName() + "': " + e.getMessage());
            return false;
        }

        BattlePlayerSession session = new BattlePlayerSession(battleWorld, database, teamSize);
        playerSessions.put(player.getUniqueId(), session);

        setWaveActive(battleWorld.getWorldName(), false);
        clearQueryResultBooks(player);
        player.setGameMode(GameMode.ADVENTURE);
        player.teleport(battleWorld.getPreparationLocation());
        player.setBedSpawnLocation(battleWorld.getCheckpointLocation(), true);
        givePrewaveStartItem(player);
        givePrewaveLeaveItem(player);
        playPreparationMusic(player);

        createPreparationSidebar(player);
        showPreparationIntro(player);
        return true;
    }

    private boolean registerLobbyStartVote(Player player) {
        String worldName = player.getWorld().getName();
        BattleArenaSession arena = arenaSessions.get(worldName);
        if (arena == null || arena.state != ArenaState.STARTING) {
            player.sendMessage(ChatColor.RED + "No hay countdown activo para votar inicio.");
            return false;
        }

        UUID playerId = player.getUniqueId();
        if (!arena.participants.contains(playerId)) {
            player.sendMessage(ChatColor.RED + "Solo los participantes pueden votar para iniciar.");
            return false;
        }

        if (!arena.startVotes.add(playerId)) {
            player.sendMessage(ChatColor.YELLOW + "Ya registraste tu voto para iniciar.");
            return false;
        }

        int eligibleVotes = arena.participants.size();
        int currentVotes = 0;
        for (UUID voterId : arena.startVotes) {
            if (arena.participants.contains(voterId)) {
                currentVotes++;
            }
        }
        broadcastArenaMessage(arena, ChatColor.AQUA + player.getName() + ChatColor.GRAY
            + " voto para iniciar (" + currentVotes + "/" + eligibleVotes + ").");

        if (eligibleVotes > 0 && currentVotes >= eligibleVotes && arena.countdownRemaining > FAST_START_COUNTDOWN_SECONDS) {
            arena.countdownRemaining = FAST_START_COUNTDOWN_SECONDS;
            arena.countdownMax = FAST_START_COUNTDOWN_SECONDS;
            updateArenaCountdownBar(arena);
            broadcastArenaMessage(arena, ChatColor.GOLD + "Todos votaron. Countdown reducido a " + FAST_START_COUNTDOWN_SECONDS + "s.");
        }

        return true;
    }

    private void removePlayerFromArena(Player player, boolean notifyArena) {
        UUID playerId = player.getUniqueId();
        String worldName = playerArenaWorld.remove(playerId);
        if (worldName == null) {
            return;
        }

        BattleArenaSession arena = arenaSessions.get(worldName);
        if (arena == null) {
            return;
        }

        boolean wasParticipant = arena.participants.remove(playerId);
        boolean wasSpectator = arena.spectators.remove(playerId);
        arena.voteEligible.remove(playerId);
        arena.startVotes.remove(playerId);
        arena.prewaveStartVotes.remove(playerId);
        arena.waveReadyPlayers.remove(playerId);

        if (!wasParticipant && !wasSpectator) {
            return;
        }

        clearSimulatedSpectatorMode(player);
        spectatorTargetCursor.remove(playerId);
        clearPrewaveStartItem(player);

        if (arena.countdownBar != null) {
            arena.countdownBar.removePlayer(player);
        }

        if (notifyArena) {
            broadcastArenaMessage(arena, ChatColor.GRAY + player.getName() + " salio de SQL Battle.");
        }

        if (arena.participants.isEmpty()) {
            if (arena.state == ArenaState.IN_GAME) {
                finishArenaMatch(arena, ChatColor.RED + "Partida finalizada: no quedan participantes.");
            } else {
                resetArenaToWaiting(arena, false);
            }
            return;
        }

    }

    private void endPreparationSession(Player player, boolean restoreMainScoreboard) {
        BattlePlayerSession session = playerSessions.remove(player.getUniqueId());
        if (session != null) {
            removeTrackedEntities(session.enemyEntityIds);
            removeTrackedEntities(session.summonedEntityIds);
            session.database.close();
        }

        if (restoreMainScoreboard) {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) {
                player.setScoreboard(manager.getMainScoreboard());
            }
        }

        stopBattleMusic(player);
        clearPrewaveStartItem(player);
    }

    private BattlePlayerSession getActiveSession(Player player) {
        return playerSessions.get(player.getUniqueId());
    }

    private boolean isPlayerInPreparationZone(Player player) {
        BattlePlayerSession session = getActiveSession(player);
        if (session == null) {
            return false;
        }

        SQLBattleWorld battleWorld = configManager.getSQLBattle(session.worldName);
        if (battleWorld == null) {
            return false;
        }

        Location current = player.getLocation();

        // Prefer wand-defined region over radius-based check
        if (battleWorld.hasEntryZone()) {
            return isInsideRegion(battleWorld.getEntryZonePos1(), battleWorld.getEntryZonePos2(), current);
        }

        if (!battleWorld.hasPreparationLocation()) {
            return false;
        }

        Location prep = battleWorld.getPreparationLocation();
        if (prep.getWorld() == null || current.getWorld() == null) {
            return false;
        }
        if (!prep.getWorld().equals(current.getWorld())) {
            return false;
        }

        return prep.distance(current) <= PREPARATION_RADIUS;
    }

    private boolean isInsideRegion(Location pos1, Location pos2, Location check) {
        return isInsideRegion(pos1, pos2, check, ENTRY_ZONE_VERTICAL_TOLERANCE);
    }

    private boolean isInsideRegion(Location pos1, Location pos2, Location check, double verticalTolerance) {
        if (pos1 == null || pos2 == null || check == null) return false;
        if (pos1.getWorld() == null || check.getWorld() == null) return false;
        if (!pos1.getWorld().equals(check.getWorld())) return false;
        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY()) - verticalTolerance;
        double maxY = Math.max(pos1.getY(), pos2.getY()) + verticalTolerance;
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        double x = check.getX(), y = check.getY(), z = check.getZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public void tickCastleSystems(double deltaSeconds) {
        for (BattleArenaSession arena : arenaSessions.values()) {
            if (arena.state != ArenaState.IN_GAME) {
                clearArenaDominationBar(arena);
                continue;
            }

            SQLBattleWorld battleWorld = getSQLBattle(arena.worldName);
            if (battleWorld == null || !battleWorld.hasCastleZone()) {
                clearArenaDominationBar(arena);
                continue;
            }

            World world = Bukkit.getWorld(arena.worldName);
            if (world == null) {
                clearArenaDominationBar(arena);
                continue;
            }

            Location castleCenter = getRegionCenter(battleWorld.getCastleZonePos1(), battleWorld.getCastleZonePos2());
            if (castleCenter == null) {
                clearArenaDominationBar(arena);
                continue;
            }

            emitCastleZoneParticles(arena, battleWorld, world);

            List<Player> activeWavePlayers = new ArrayList<>();
            for (UUID participantId : arena.participants) {
                Player participant = Bukkit.getPlayer(participantId);
                BattlePlayerSession session = playerSessions.get(participantId);
                if (participant == null || !participant.isOnline() || session == null) {
                    continue;
                }
                if (session.phase == BattleSessionPhase.WAVE_ACTIVE) {
                    activeWavePlayers.add(participant);
                }
            }

            int enemiesInsideCastle = 0;
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!isBattleEnemyEntity(entity)) {
                    continue;
                }

                Player priorityTarget = findNearestActivePlayer(entity.getLocation(), activeWavePlayers, CASTLE_PLAYER_PRIORITY_RADIUS);
                if (entity instanceof Mob mob) {
                    if (priorityTarget != null) {
                        mob.setTarget(priorityTarget);
                    } else {
                        // No nearby player: gently pull enemies toward castle center.
                        Location current = entity.getLocation();
                        double dx = castleCenter.getX() - current.getX();
                        double dz = castleCenter.getZ() - current.getZ();
                        double length = Math.sqrt((dx * dx) + (dz * dz));
                        if (length > 0.001D) {
                            double vx = (dx / length) * CASTLE_PULL_STRENGTH;
                            double vz = (dz / length) * CASTLE_PULL_STRENGTH;
                            entity.setVelocity(entity.getVelocity().multiply(0.85D).add(new org.bukkit.util.Vector(vx, 0.0D, vz)));
                        }
                    }
                }

                if (isInsideRegion(battleWorld.getCastleZonePos1(), battleWorld.getCastleZonePos2(), entity.getLocation(), 0.0D)) {
                    enemiesInsideCastle++;
                }
            }

            if (enemiesInsideCastle <= 0) {
                clearArenaDominationBar(arena);
                continue;
            }

            if (arena.dominationStartedAtMillis <= 0L) {
                arena.dominationStartedAtMillis = System.currentTimeMillis();
            }

            arena.dominationProgressSeconds += enemiesInsideCastle * deltaSeconds;
            long elapsedMillis = System.currentTimeMillis() - arena.dominationStartedAtMillis;
            double elapsedSeconds = Math.max(0.0D, elapsedMillis / 1000.0D);

            updateArenaDominationBar(arena);

            if (arena.dominationProgressSeconds >= CASTLE_DOMINATION_REQUIRED_SECONDS
                    && elapsedSeconds >= CASTLE_DOMINATION_MIN_REAL_SECONDS) {
                finishArenaMatch(arena, ChatColor.DARK_RED + "¡Los enemigos dominaron el castillo!");
            }
        }
    }

    private Player findNearestActivePlayer(Location from, List<Player> candidates, double maxDistance) {
        Player best = null;
        double bestDistanceSq = maxDistance * maxDistance;
        for (Player candidate : candidates) {
            if (candidate.getWorld() == null || from.getWorld() == null || !candidate.getWorld().equals(from.getWorld())) {
                continue;
            }
            double distSq = candidate.getLocation().distanceSquared(from);
            if (distSq <= bestDistanceSq) {
                bestDistanceSq = distSq;
                best = candidate;
            }
        }
        return best;
    }

    private Location getRegionCenter(Location pos1, Location pos2) {
        if (pos1 == null || pos2 == null || pos1.getWorld() == null || pos2.getWorld() == null) {
            return null;
        }
        if (!pos1.getWorld().equals(pos2.getWorld())) {
            return null;
        }
        double centerX = (pos1.getX() + pos2.getX()) / 2.0D;
        double centerY = (pos1.getY() + pos2.getY()) / 2.0D;
        double centerZ = (pos1.getZ() + pos2.getZ()) / 2.0D;
        return new Location(pos1.getWorld(), centerX, centerY, centerZ);
    }

    private void emitCastleZoneParticles(BattleArenaSession arena, SQLBattleWorld battleWorld, World world) {
        long now = System.currentTimeMillis();
        if (now - arena.lastCastleParticleMillis < CASTLE_PARTICLE_INTERVAL_MILLIS) {
            return;
        }
        arena.lastCastleParticleMillis = now;

        Location pos1 = battleWorld.getCastleZonePos1();
        Location pos2 = battleWorld.getCastleZonePos2();
        if (pos1 == null || pos2 == null || pos1.getWorld() == null || !pos1.getWorld().equals(world)) {
            return;
        }

        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double maxY = Math.max(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());

        double y = minY + 1.0D;
        if (maxY > minY + 1.0D) {
            y = ThreadLocalRandom.current().nextDouble(minY + 0.8D, maxY + 0.2D);
        }

        Particle.DustOptions[] palette = new Particle.DustOptions[] {
            new Particle.DustOptions(Color.fromRGB(255, 84, 84), 1.25f),
            new Particle.DustOptions(Color.fromRGB(255, 170, 0), 1.25f),
            new Particle.DustOptions(Color.fromRGB(72, 198, 255), 1.25f),
            new Particle.DustOptions(Color.fromRGB(120, 255, 120), 1.25f)
        };

        for (int i = 0; i < 12; i++) {
            boolean onXEdge = ThreadLocalRandom.current().nextBoolean();
            double x = onXEdge
                ? (ThreadLocalRandom.current().nextBoolean() ? minX : maxX)
                : ThreadLocalRandom.current().nextDouble(minX, maxX + 0.001D);
            double z = onXEdge
                ? ThreadLocalRandom.current().nextDouble(minZ, maxZ + 0.001D)
                : (ThreadLocalRandom.current().nextBoolean() ? minZ : maxZ);

            Particle.DustOptions color = palette[i % palette.length];
            world.spawnParticle(Particle.REDSTONE, x + 0.5D, y, z + 0.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D, color);
        }
    }

    private void updateArenaDominationBar(BattleArenaSession arena) {
        if (arena.dominationBar == null) {
            arena.dominationBar = Bukkit.createBossBar("Dominacion", BarColor.RED, BarStyle.SOLID);
        }

        for (UUID playerId : arena.getAllPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                arena.dominationBar.addPlayer(player);
            }
        }

        double remaining = Math.max(0.0D, CASTLE_DOMINATION_REQUIRED_SECONDS - arena.dominationProgressSeconds);
        double progress = Math.max(0.0D, Math.min(1.0D, remaining / CASTLE_DOMINATION_REQUIRED_SECONDS));
        arena.dominationBar.setProgress(progress);
        arena.dominationBar.setTitle(ChatColor.DARK_RED + "Dominacion del castillo: " + ChatColor.RED + (int) Math.ceil(remaining) + "s");
    }

    private void clearArenaDominationBar(BattleArenaSession arena) {
        if (arena.dominationBar != null) {
            arena.dominationBar.removeAll();
            arena.dominationBar = null;
        }
        arena.dominationProgressSeconds = 0.0D;
        arena.dominationStartedAtMillis = 0L;
    }

    private void processBattleQuery(Player player, BattlePlayerSession session, String query) {
        try {
            int currentPoints = session.database.getPlayerActionPoints();
            if (!hasAnyAffordableQuery(currentPoints)) {
                registerPrewaveStartVote(player, session, "La oleada inicia cuando todo el equipo este listo.");
                return;
            }

            BattleValidationResult validation = session.validator.validate(query);
            if (!validation.isAllowed()) {
                player.sendMessage(ChatColor.RED + validation.getReason());
                player.sendMessage(ChatColor.GRAY + "No se descontaron puntos de acción.");
                updatePreparationSidebar(player);
                return;
            }

            int cost = validation.getActionPointCost();
            if (cost > currentPoints) {
                if (!hasAnyAffordableQuery(currentPoints)) {
                    registerPrewaveStartVote(player, session, "La oleada inicia cuando todo el equipo este listo.");
                    return;
                }

                player.sendMessage(ChatColor.RED + "No tienes suficientes puntos de acción para esa consulta.");
                player.sendMessage(ChatColor.GRAY + "Costo: " + cost + " AP | Disponibles: " + currentPoints + " AP");
                updatePreparationSidebar(player);
                return;
            }

            Connection connection = session.database.getConnection();
            boolean originalAutoCommit = connection.getAutoCommit();
            Map<Integer, Integer> inventarioBeforeInsert = null;

            BattleExecutionResult result;
            try {
                connection.setAutoCommit(false);
                if ("INSERT".equalsIgnoreCase(validation.getQueryType())) {
                    inventarioBeforeInsert = session.database.snapshotInventarioQuantities();
                }

                result = session.executor.execute(connection, query);
                if (!result.isSuccess()) {
                    connection.rollback();
                    player.sendMessage(ChatColor.RED + "Consulta rechazada o fallida: "
                        + formatBattleQueryFailureMessage(result.getErrorMessage()));
                    player.sendMessage(ChatColor.GRAY + "No se descontaron puntos de acción.");
                    updatePreparationSidebar(player);
                    return;
                }

                if ("INSERT".equalsIgnoreCase(result.getQueryType()) && inventarioBeforeInsert != null) {
                    session.database.consumeAlmacenForInventarioIncrease(inventarioBeforeInsert);
                }

                connection.commit();
            } catch (Exception txError) {
                try {
                    connection.rollback();
                } catch (Exception ignored) {
                }
                player.sendMessage(ChatColor.RED + "No se pudo ejecutar la consulta: "
                    + formatBattleQueryFailureMessage(txError.getMessage()));
                player.sendMessage(ChatColor.GRAY + "No se descontaron puntos de acción.");
                updatePreparationSidebar(player);
                return;
            } finally {
                try {
                    connection.setAutoCommit(originalAutoCommit);
                } catch (Exception ignored) {
                }
            }

            if (!result.isSuccess()) {
                player.sendMessage(ChatColor.RED + "Consulta rechazada o fallida: "
                    + formatBattleQueryFailureMessage(result.getErrorMessage()));
                player.sendMessage(ChatColor.GRAY + "No se descontaron puntos de acción.");
                updatePreparationSidebar(player);
                return;
            }

            int remainingPoints = Math.max(0, currentPoints - cost);
            session.database.setPlayerActionPoints(remainingPoints);

            player.sendMessage(ChatColor.GREEN + "Consulta ejecutada correctamente." );
            player.sendMessage(ChatColor.WHITE + "Tipo: " + ChatColor.GRAY + result.getQueryType()
                    + ChatColor.WHITE + " | Costo: " + ChatColor.GRAY + cost + " AP"
                    + ChatColor.WHITE + " | Restantes: " + ChatColor.GRAY + remainingPoints + " AP");
            player.sendMessage(ChatColor.WHITE + "Filas afectadas/devueltas: " + ChatColor.GRAY + result.getRowsAffected());
            if (!result.getTablesAccessed().isEmpty()) {
                player.sendMessage(ChatColor.WHITE + "Tablas: " + ChatColor.GRAY + String.join(", ", result.getTablesAccessed()));
            }
            if (result.isUsedJoin()) {
                player.sendMessage(ChatColor.AQUA + "Bonus por JOIN: +" + result.getJoinBonusPercent() + "%");
            }

            showQueryResultPreview(player, result);
            deliverQueryResultBook(player, query, result);

            updatePreparationSidebar(player);

            if (!hasAnyAffordableQuery(remainingPoints)) {
                registerPrewaveStartVote(player, session, "La oleada inicia cuando todo el equipo este listo.");
            }
        } catch (Exception e) {
            logger.warning("Error while executing SQL Battle query for '" + player.getName() + "': " + e.getMessage());
            player.sendMessage(ChatColor.RED + "Error interno al procesar la consulta.");
            player.sendMessage(ChatColor.GRAY + "No se descontaron puntos de acción.");
            updatePreparationSidebar(player);
        }
    }

    private void showPreparationIntro(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== SQL Battle: Preparación ===");
        player.sendMessage(ChatColor.GREEN + "Escribe consultas SQL en el chat dentro de la zona prewave.");
        player.sendMessage(ChatColor.GRAY + "Comandos: help, costos, estado, sugerencias, salir");
        player.sendMessage(ChatColor.GRAY + "Las consultas erróneas no descuentan puntos.");
        player.sendMessage(ChatColor.GRAY + "Cuando ya no tengas AP suficientes, la oleada comenzará automáticamente.");
        player.sendMessage(ChatColor.GRAY + "También puedes usar el item 'Iniciar oleada' para comenzar sin gastar todo el AP.");
        showPreparationSuggestions(player);
    }

    private void showWaveIntro(Player player, BattlePlayerSession session) {
        player.sendMessage(ChatColor.RED + "=== SQL Battle: Oleada activa ===");
        player.sendMessage(ChatColor.YELLOW + "La fase de preparación terminó. Defiéndete de la oleada actual.");
        player.sendMessage(ChatColor.GRAY + "Oleada: " + session.lastKnownWave + " | Etapa: " + session.lastKnownStage);
        player.sendMessage(ChatColor.GRAY + "Enemigos desplegados: " + getTrackedLiveCount(session.enemyEntityIds)
            + " | Invocaciones: " + getTrackedLiveCount(session.summonedEntityIds));
    }

    private void showPreparationHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Ayuda SQL Battle ===");
        player.sendMessage(ChatColor.WHITE + "Chat SQL activo solo dentro del prewave.");
        player.sendMessage(ChatColor.WHITE + "SELECT cuesta 1 AP");
        player.sendMessage(ChatColor.WHITE + "INSERT cuesta 2 AP");
        player.sendMessage(ChatColor.WHITE + "UPDATE cuesta 2 AP");
        player.sendMessage(ChatColor.WHITE + "DELETE cuesta 3 AP");
        player.sendMessage(ChatColor.GRAY + "Comandos rápidos: costos, estado, sugerencias, salir");
    }

    private void showPreparationSuggestions(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Sugerencias SQL (Prewave) ===");
        player.sendMessage(ChatColor.GRAY + "Haz click en una sugerencia para copiarla al chat y editarla antes de enviarla." );
        for (int id = 1; id <= 4; id++) {
            String title = getSuggestionTitleById(id);
            String query = getSuggestedQueryById(id);
            if (title == null || query == null) {
                continue;
            }

            Component line = Component.text("[" + id + "] ", NamedTextColor.AQUA)
                .append(Component.text(title, NamedTextColor.WHITE))
                .append(Component.text("  (copiar al chat)", NamedTextColor.GREEN))
                .clickEvent(ClickEvent.suggestCommand(query));

            player.sendMessage(line);
            player.sendMessage(ChatColor.DARK_GRAY + query);
        }
    }

    private String getSuggestionTitleById(int suggestionId) {
        return switch (suggestionId) {
            case 1 -> "Ver recursos disponibles";
            case 2 -> "Ver items baratos por oleada";
            case 3 -> "Preparar equipo básico";
            case 4 -> "Preparar invocación etapa 3";
            default -> null;
        };
    }

    private String getSuggestedQueryById(int suggestionId) {
        return switch (suggestionId) {
            case 1 -> "SELECT a.item_id, t.nombre, a.cantidad FROM almacen a INNER JOIN tipos_item t ON a.item_id = t.id ORDER BY a.cantidad DESC LIMIT 5";
            case 2 -> "SELECT id, nombre, categoria, costo_mana, oleada_desbloqueo FROM tipos_item WHERE oleada_desbloqueo <= 3 ORDER BY oleada_desbloqueo ASC, costo_mana ASC LIMIT 8";
            case 3 -> "INSERT INTO inventario (item_id, cantidad, activo_en_etapa) VALUES (1, 1, 1)";
            case 4 -> "INSERT INTO inventario (item_id, cantidad, activo_en_etapa) VALUES (7, 1, 2)";
            default -> null;
        };
    }

    private void showPreparationCosts(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Costos SQL Battle ===");
        player.sendMessage(ChatColor.WHITE + "SELECT: " + ChatColor.GREEN + "1 AP");
        player.sendMessage(ChatColor.WHITE + "INSERT: " + ChatColor.YELLOW + "2 AP");
        player.sendMessage(ChatColor.WHITE + "UPDATE: " + ChatColor.YELLOW + "2 AP");
        player.sendMessage(ChatColor.WHITE + "DELETE: " + ChatColor.RED + "3 AP");
    }

    private void showPreparationStatus(Player player, BattlePlayerSession session) {
        try {
            int points = session.database.getPlayerActionPoints();
            int wave = session.database.getCurrentWaveNumber();
            int stage = getDisplayedStage(player, session);
            player.sendMessage(ChatColor.GOLD + "=== Estado SQL Battle ===");
            player.sendMessage(ChatColor.WHITE + "Fase: " + ChatColor.AQUA + session.phase.getDisplayName());
            player.sendMessage(ChatColor.WHITE + "Sesión: " + ChatColor.GRAY + session.getSessionAgeSeconds() + "s");
            player.sendMessage(ChatColor.WHITE + "Oleada: " + ChatColor.AQUA + wave);
            player.sendMessage(ChatColor.WHITE + "Etapa: " + ChatColor.AQUA + stage);
            player.sendMessage(ChatColor.WHITE + "Puntos de acción: " + ChatColor.GREEN + points);
            player.sendMessage(ChatColor.WHITE + "Modelo extendido: " + (session.hasExpandedZoneModel() ? ChatColor.GREEN + "listo" : ChatColor.YELLOW + "parcial"));
            if (!session.lastPreparationEndReason.isEmpty()) {
                player.sendMessage(ChatColor.WHITE + "Última transición: " + ChatColor.GRAY + session.lastPreparationEndReason);
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "No se pudo consultar el estado actual.");
        }
    }

    private void createPreparationSidebar(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }

        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("sqlbattle", "dummy",
                net.kyori.adventure.text.Component.text("SQL Battle", net.kyori.adventure.text.format.NamedTextColor.GOLD));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        updatePreparationSidebarContent(player, scoreboard, objective);
        player.setScoreboard(scoreboard);

        BattlePlayerSession session = getActiveSession(player);
        if (session != null) {
            session.scoreboard = scoreboard;
        }
    }

    private void updatePreparationSidebar(Player player) {
        BattlePlayerSession session = getActiveSession(player);
        if (session == null || session.scoreboard == null) {
            return;
        }

        Objective objective = session.scoreboard.getObjective("sqlbattle");
        if (objective == null) {
            return;
        }

        updatePreparationSidebarContent(player, session.scoreboard, objective);
    }

    private void updatePreparationSidebarContent(Player player, Scoreboard scoreboard, Objective objective) {
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        BattlePlayerSession session = getActiveSession(player);
        if (session == null) {
            return;
        }

        int points = 0;
        int wave = DEFAULT_WAVE_NUMBER;
        int stage = 0;
        try {
            points = session.database.getPlayerActionPoints();
            wave = session.database.getCurrentWaveNumber();
            stage = getDisplayedStage(player, session);
            session.lastKnownWave = wave;
            session.lastKnownStage = stage;
        } catch (Exception e) {
            logger.warning("Could not update SQL Battle sidebar for '" + player.getName() + "': " + e.getMessage());
        }

        int score = 12;
        objective.getScore(ChatColor.GOLD + "Fase: " + ChatColor.WHITE + session.phase.getSidebarLabel()).setScore(score--);
        objective.getScore(ChatColor.YELLOW + "Oleada: " + ChatColor.WHITE + wave).setScore(score--);
        objective.getScore(ChatColor.YELLOW + "Etapa: " + ChatColor.WHITE + stage).setScore(score--);
        objective.getScore(ChatColor.GREEN + "AP: " + ChatColor.WHITE + points).setScore(score--);
        objective.getScore(ChatColor.BLACK.toString()).setScore(score--);
        objective.getScore(ChatColor.AQUA + "SELECT: 1 AP").setScore(score--);
        objective.getScore(ChatColor.GOLD + "INSERT: 2 AP").setScore(score--);
        objective.getScore(ChatColor.BLUE + "UPDATE: 2 AP").setScore(score--);
        objective.getScore(ChatColor.RED + "DELETE: 3 AP").setScore(score--);
        objective.getScore(ChatColor.DARK_BLUE.toString()).setScore(score--);
        objective.getScore(ChatColor.GRAY + "help | costos").setScore(score--);
        objective.getScore(ChatColor.DARK_GRAY + "estado | salir").setScore(score--);
    }

    private void showQueryResultPreview(Player player, BattleExecutionResult result) {
        List<Map<String, Object>> rows = result.getRows();
        if (rows.isEmpty()) {
            return;
        }

        int previewCount = Math.min(MAX_PREVIEW_ROWS, rows.size());
        player.sendMessage(ChatColor.GOLD + "=== Resultado SQL (preview " + previewCount + "/" + rows.size() + ") ===");

        for (int i = 0; i < previewCount; i++) {
            Map<String, Object> row = rows.get(i);
            StringBuilder line = new StringBuilder();
            boolean first = true;

            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (!first) {
                    line.append(ChatColor.DARK_GRAY).append(" | ");
                }
                line.append(ChatColor.AQUA).append(entry.getKey())
                    .append(ChatColor.WHITE).append("=")
                    .append(ChatColor.GRAY).append(String.valueOf(entry.getValue()));
                first = false;
            }

            player.sendMessage(ChatColor.WHITE + "#" + (i + 1) + " " + line);
        }

        if (rows.size() > previewCount) {
            player.sendMessage(ChatColor.GRAY + "... y " + (rows.size() - previewCount) + " filas mas.");
        }
    }

    private void deliverQueryResultBook(Player player, String query, BattleExecutionResult result) {
        List<Map<String, Object>> rows = result.getRows();
        if (rows.isEmpty()) {
            return;
        }

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.setTitle("Resultado SQL Battle");
        meta.setAuthor("SQL Battle");

        List<String> pages = new ArrayList<>();
        pages.add(buildSummaryPage(query, result));

        int maxRows = Math.min(MAX_BOOK_ROWS, rows.size());
        for (int i = 0; i < maxRows; i++) {
            Map<String, Object> row = rows.get(i);
            StringBuilder page = new StringBuilder();
            page.append("Fila #").append(i + 1).append("\n\n");

            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String value = String.valueOf(entry.getValue());
                if (value.length() > 90) {
                    value = value.substring(0, 90) + "...";
                }
                page.append(entry.getKey()).append(": ").append(value).append("\n");
            }

            pages.add(page.toString());
        }

        if (rows.size() > maxRows) {
            pages.add("Resultados truncados\n\nMostrando " + maxRows + " de " + rows.size() + " filas.");
        }

        meta.setPages(pages);
        book.setItemMeta(meta);
        player.getInventory().addItem(book);
        player.sendMessage(ChatColor.GRAY + "Se agrego un libro con el resultado completo a tu inventario.");
    }

    private void clearQueryResultBooks(Player player) {
        int removed = 0;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (!isSqlBattleResultBook(item)) {
                continue;
            }
            player.getInventory().setItem(slot, null);
            removed++;
        }

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isSqlBattleResultBook(offHand)) {
            player.getInventory().setItemInOffHand(null);
            removed++;
        }

        if (removed > 0) {
            player.sendMessage(ChatColor.GRAY + "Se limpiaron " + removed + " libro(s) de resultados SQL Battle.");
        }
    }

    private boolean isSqlBattleResultBook(ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK) {
            return false;
        }
        if (!(item.getItemMeta() instanceof BookMeta meta)) {
            return false;
        }
        String title = meta.getTitle();
        return title != null && title.equalsIgnoreCase("Resultado SQL Battle");
    }

    private String buildSummaryPage(String query, BattleExecutionResult result) {
        String compactQuery = query.replace("\n", " ").trim();
        if (compactQuery.length() > 220) {
            compactQuery = compactQuery.substring(0, 220) + "...";
        }

        return "SQL Battle Result\n\n"
            + "Tipo: " + result.getQueryType() + "\n"
            + "Filas: " + result.getRowsAffected() + "\n"
            + "JOIN: " + (result.isUsedJoin() ? ("Si (+" + result.getJoinBonusPercent() + "%)") : "No") + "\n\n"
            + "Query:\n" + compactQuery;
    }

    private int getDisplayedStage(Player player, BattlePlayerSession session) throws Exception {
        int forcedStage = getForcedStage(player);
        if (forcedStage > 0) {
            return forcedStage;
        }
        return session.database.getCurrentStage();
    }

    private boolean hasAnyAffordableQuery(int actionPoints) {
        return actionPoints >= MIN_ACTION_POINT_COST;
    }

    private int calculatePreparationActionPoints(int waveNumber, int teamSize) {
        int normalizedWave = Math.max(DEFAULT_WAVE_NUMBER, waveNumber);
        int normalizedTeam = Math.max(1, Math.min(MAX_ARENA_PARTICIPANTS, teamSize));
        double teamMultiplier = 0.9D + (0.1D * normalizedTeam);

        int baseAp;
        if (normalizedWave == 1) {
            baseAp = BASE_PREWAVE_ACTION_POINTS; // 5 AP
        } else if (normalizedWave == 2) {
            baseAp = SECOND_PREWAVE_ACTION_POINTS; // 7 AP
        } else {
            // Desde la tercera prewave, escala de 1 en 1: 8, 9, 10...
            baseAp = SECOND_PREWAVE_ACTION_POINTS + (normalizedWave - 2);
        }

        int computed = (int) Math.round(baseAp * teamMultiplier);
        return Math.min(MAX_PREWAVE_ACTION_POINTS, computed);
    }

    private void givePrewaveStartItem(Player player) {
        clearPrewaveStartItem(player);

        ItemStack startItem = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta meta = startItem.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.setDisplayName(ChatColor.GOLD + "Iniciar oleada");
        meta.setLore(List.of(
            ChatColor.GRAY + "Click derecho para comenzar",
            ChatColor.GRAY + "la oleada inmediatamente."
        ));
        meta.getPersistentDataContainer().set(sqlBattlePrewaveStartItemKey, PersistentDataType.BYTE, (byte) 1);
        startItem.setItemMeta(meta);
        player.getInventory().setItem(8, startItem);
    }

    private void givePrewaveLeaveItem(Player player) {
        ItemStack leaveItem = new ItemStack(Material.BARRIER);
        ItemMeta meta = leaveItem.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.setDisplayName(ChatColor.RED + "Salir de la partida");
        meta.setLore(List.of(
            ChatColor.GRAY + "Click derecho para abandonar",
            ChatColor.GRAY + "tu sesion SQL Battle."
        ));
        meta.getPersistentDataContainer().set(sqlBattlePrewaveLeaveItemKey, PersistentDataType.BYTE, (byte) 1);
        leaveItem.setItemMeta(meta);
        player.getInventory().setItem(7, leaveItem);
    }

    private void giveSpectatorTeleportItem(Player player) {
        ItemStack tpItem = new ItemStack(Material.COMPASS);
        ItemMeta meta = tpItem.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.setDisplayName(ChatColor.AQUA + "TP a jugador");
        meta.setLore(List.of(
            ChatColor.GRAY + "Click derecho para teletransportarte",
            ChatColor.GRAY + "al siguiente jugador activo."
        ));
        meta.getPersistentDataContainer().set(sqlBattleSpectatorTpItemKey, PersistentDataType.BYTE, (byte) 1);
        tpItem.setItemMeta(meta);
        player.getInventory().setItem(6, tpItem);
    }

    public void clearPrewaveStartItem(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (isPrewaveStartItem(item) || isPrewaveLeaveItem(item) || isSpectatorTeleportItem(item)) {
                player.getInventory().setItem(slot, null);
            }
        }

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isPrewaveStartItem(offHand) || isPrewaveLeaveItem(offHand) || isSpectatorTeleportItem(offHand)) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    public boolean isPrewaveStartItem(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_POWDER || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(sqlBattlePrewaveStartItemKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public boolean isPrewaveLeaveItem(ItemStack item) {
        if (item == null || item.getType() != Material.BARRIER || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(sqlBattlePrewaveLeaveItemKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public boolean isSpectatorTeleportItem(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(sqlBattleSpectatorTpItemKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public boolean teleportSpectatorToNextPlayer(Player spectator) {
        if (!isPlayerBattleSpectator(spectator)) {
            spectator.sendMessage(ChatColor.RED + "Solo los espectadores pueden usar este item.");
            return false;
        }

        applySimulatedSpectatorMode(spectator);

        String worldName = playerArenaWorld.get(spectator.getUniqueId());
        if (worldName == null) {
            spectator.sendMessage(ChatColor.RED + "No estás vinculado a una arena SQL Battle.");
            return false;
        }

        BattleArenaSession arena = arenaSessions.get(worldName);
        if (arena == null) {
            spectator.sendMessage(ChatColor.RED + "No se encontró la arena activa.");
            return false;
        }

        List<Player> candidates = new ArrayList<>();
        for (UUID participantId : arena.participants) {
            Player participant = Bukkit.getPlayer(participantId);
            if (participant == null || !participant.isOnline() || participant.isDead()) {
                continue;
            }
            if (!participant.getWorld().getName().equalsIgnoreCase(worldName)) {
                continue;
            }
            candidates.add(participant);
        }

        if (candidates.isEmpty()) {
            spectator.sendMessage(ChatColor.YELLOW + "No hay jugadores activos para espectear en este momento.");
            return false;
        }

        int cursor = spectatorTargetCursor.getOrDefault(spectator.getUniqueId(), 0);
        int index = Math.floorMod(cursor, candidates.size());
        Player target = candidates.get(index);
        spectatorTargetCursor.put(spectator.getUniqueId(), index + 1);

        spectator.teleport(target.getLocation());
        spectator.sendMessage(ChatColor.AQUA + "Ahora estás specteando a " + ChatColor.WHITE + target.getName() + ChatColor.AQUA + ".");
        return true;
    }

    public void enforceSimulatedSpectatorState(Player player) {
        if (!isPlayerBattleSpectator(player)) {
            return;
        }
        applySimulatedSpectatorMode(player);
    }

    private void applySimulatedSpectatorMode(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvulnerable(true);
        player.setCollidable(false);
        player.setCanPickupItems(false);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));
    }

    private void clearSimulatedSpectatorMode(Player player) {
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setInvulnerable(false);
        player.setCollidable(true);
        player.setCanPickupItems(true);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    private void beginWavePhase(Player player, BattlePlayerSession session, String reason) {
        if (session.phase != BattleSessionPhase.PREPARATION) {
            return;
        }

        clearPrewaveStartItem(player);
        playWaveMusic(player);
        session.phase = BattleSessionPhase.WAVE_ACTIVE;
        session.lastPreparationEndReason = reason;
        session.lastKnownStage = FIRST_WAVE_STAGE;
        try {
            session.lastKnownWave = session.database.getCurrentWaveNumber();
        } catch (Exception e) {
            session.lastKnownWave = DEFAULT_WAVE_NUMBER;
        }

        try {
            session.database.setCurrentStage(FIRST_WAVE_STAGE);
        } catch (Exception e) {
            logger.warning("Could not set SQL Battle stage for player '" + player.getName() + "': " + e.getMessage());
        }

        if (session.waveStartLocation != null) {
            player.teleport(session.waveStartLocation);
        }
        if (session.checkpointLocation != null) {
            player.setBedSpawnLocation(session.checkpointLocation, true);
        }

        player.setGameMode(GameMode.ADVENTURE);
        setWaveActive(session.worldName, true);
        spawnWaveEntities(player, session);
        giveInventoryItemsToPlayer(player, session, FIRST_WAVE_STAGE);
        player.getInventory().addItem(new ItemStack(Material.TORCH, 1));
        updatePreparationSidebar(player);

        player.sendMessage(ChatColor.AQUA + reason);
        player.sendMessage(ChatColor.GREEN + "+1 Antorcha gratis para la oleada.");
        showWaveIntro(player, session);
    }

    private void spawnWaveEntities(Player player, BattlePlayerSession session) {
        session.enemyEntityIds.clear();
        session.summonedEntityIds.clear();

        spawnStageEnemies(player, session, session.lastKnownStage);
        spawnPreparedSummons(player, session, session.lastKnownStage);
    }

    private void spawnStageEnemies(Player player, BattlePlayerSession session, int stage) {
        try {
            List<BattleSQLDatabase.BattleEnemyRow> enemies = session.database.getEnemiesForStage(stage);
            int multiplier = getEnemyMultiplier(session);
            for (BattleSQLDatabase.BattleEnemyRow enemyRow : enemies) {
                for (int copy = 0; copy < multiplier; copy++) {
                    Location spawnLocation = getRandomRegionLocation(session.enemySpawnPos1, session.enemySpawnPos2);
                    if (spawnLocation == null) {
                        continue;
                    }

                    LivingEntity spawned = spawnEnemyEntity(player, session, enemyRow, spawnLocation);
                    if (spawned != null) {
                        session.enemyEntityIds.add(spawned.getUniqueId());
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Could not spawn SQL Battle enemies for player '" + player.getName() + "': " + e.getMessage());
        }
    }

    private void spawnPreparedSummons(Player player, BattlePlayerSession session, int stage) {
        if (session.summonZonePos1 == null || session.summonZonePos2 == null) {
            return;
        }

        try {
            int golemCount = Math.min(MAX_SUMMONED_GOLEMS, session.database.getPreparedSummonQuantityForStage(GOLEM_SUMMON_ITEM_ID, stage));
            for (int index = 0; index < golemCount; index++) {
                Location summonLocation = getRandomRegionLocation(session.summonZonePos1, session.summonZonePos2);
                if (summonLocation == null) {
                    continue;
                }

                IronGolem golem = (IronGolem) summonLocation.getWorld().spawnEntity(summonLocation, EntityType.IRON_GOLEM);
                golem.setPlayerCreated(true);
                golem.setCustomName("Golem SQL de " + player.getName());
                golem.setCustomNameVisible(true);
                golem.setRemoveWhenFarAway(false);
                tagBattleEntity(golem, session, player.getUniqueId(), "summon");
                applyConfiguredHealth(golem, 50.0D);
                session.summonedEntityIds.add(golem.getUniqueId());
            }
        } catch (Exception e) {
            logger.warning("Could not spawn SQL Battle summons for player '" + player.getName() + "': " + e.getMessage());
        }
    }

    private LivingEntity spawnEnemyEntity(Player player, BattlePlayerSession session,
            BattleSQLDatabase.BattleEnemyRow enemyRow, Location spawnLocation) {
        EntityType entityType = mapEnemyType(enemyRow.getTipoId());
        Entity spawnedEntity = spawnLocation.getWorld().spawnEntity(spawnLocation, entityType);
        if (!(spawnedEntity instanceof LivingEntity livingEntity)) {
            spawnedEntity.remove();
            return null;
        }

        livingEntity.setCustomName(getEnemyDisplayName(enemyRow.getTipoId()) + " [SQL]");
        livingEntity.setCustomNameVisible(true);
        livingEntity.setRemoveWhenFarAway(false);
        tagBattleEntity(livingEntity, session, player.getUniqueId(), "enemy");
        applyConfiguredHealth(livingEntity, enemyRow.getHp());
        configureEnemyBehavior(livingEntity, player);
        return livingEntity;
    }

    private void configureEnemyBehavior(LivingEntity entity, Player player) {
        if (entity instanceof Mob mob) {
            mob.setTarget(player);
        }

        if (entity instanceof Creeper creeper) {
            creeper.setExplosionRadius(2);
        } else if (entity instanceof Phantom phantom) {
            phantom.setSize(8);
        } else if (entity instanceof Ravager ravager) {
            ravager.setPatrolLeader(false);
        } else if (entity instanceof Zombie zombie) {
            zombie.setShouldBurnInDay(false);
        } else if (entity instanceof Spider spider) {
            spider.setCanPickupItems(false);
        } else if (entity instanceof Enderman) {
            // Enderman has no direct pickup toggle here; keep default behaviour for now.
        } else if (entity instanceof Witch witch) {
            witch.setCanPickupItems(false);
        }
    }

    private void applyConfiguredHealth(LivingEntity entity, double health) {
        AttributeInstance attribute = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(Math.max(1.0D, health));
        }
        entity.setHealth(Math.max(1.0D, Math.min(health, entity.getMaxHealth())));
    }

    private EntityType mapEnemyType(int tipoId) {
        return switch (tipoId) {
            case 1 -> EntityType.ZOMBIE;
            case 2 -> EntityType.SKELETON;
            case 3 -> EntityType.SPIDER;
            case 4 -> EntityType.CREEPER;
            case 5 -> EntityType.ENDERMAN;
            case 6 -> EntityType.RAVAGER;
            case 7 -> EntityType.WITCH;
            case 8 -> EntityType.PHANTOM;
            default -> EntityType.ZOMBIE;
        };
    }

    private String getEnemyDisplayName(int tipoId) {
        return switch (tipoId) {
            case 1 -> "Zombi";
            case 2 -> "Esqueleto";
            case 3 -> "Araña";
            case 4 -> "Creeper";
            case 5 -> "Enderman";
            case 6 -> "Golem de Hierro";
            case 7 -> "Bruja";
            case 8 -> "Dragón";
            default -> "Enemigo";
        };
    }

    private void giveInventoryItemsToPlayer(Player player, BattlePlayerSession session, int stage) {
        try {
            List<BattleSQLDatabase.InventoryItemRow> items = session.database.getInventoryItemsForExactStage(stage);
            if (items.isEmpty()) {
                return;
            }
            for (BattleSQLDatabase.InventoryItemRow row : items) {
                Material mat = mapItemIdToMaterial(row.getItemId());
                if (mat == null) {
                    continue;
                }
                if (mat == Material.POTION) {
                    for (int i = 0; i < row.getCantidad(); i++) {
                        ItemStack potion = new ItemStack(Material.POTION);
                        org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) potion.getItemMeta();
                        if (meta != null) {
                            meta.setBasePotionData(new org.bukkit.potion.PotionData(org.bukkit.potion.PotionType.INSTANT_HEAL, false, false));
                            meta.setDisplayName(ChatColor.RED + "Pocion de Vida");
                            potion.setItemMeta(meta);
                        }
                        player.getInventory().addItem(potion);
                    }
                } else {
                    player.getInventory().addItem(new ItemStack(mat, row.getCantidad()));
                }
                player.sendMessage(ChatColor.GREEN + "+" + row.getCantidad() + "x " + row.getNombre() + " "
                    + ChatColor.GRAY + "(etapa " + stage + ")");
            }
        } catch (Exception e) {
            logger.warning("Could not give SQL Battle items to player '" + player.getName() + "': " + e.getMessage());
        }
    }

    private String formatBattleQueryFailureMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return "Error desconocido.";
        }

        String message = rawMessage.trim();
        String normalized = message.toLowerCase();
        if (normalized.contains("stock insuficiente en almacen")) {
            return message;
        }
        if (normalized.contains("primary key") || normalized.contains("unique index") || normalized.contains("unique constraint")) {
            return "Ese item ya estaba preparado en inventario. Usa UPDATE inventario SET cantidad = ... WHERE item_id = ...";
        }
        return message;
    }

    public boolean giveDebugItem(Player player, String itemToken) {
        int itemId = resolveDebugItemId(itemToken);
        if (itemId < 0) {
            player.sendMessage(ChatColor.RED + "Item SQL Battle desconocido: " + itemToken);
            player.sendMessage(ChatColor.GRAY + "Ejemplos: 8, hielo, fuego, lluvia, tnt, espada_hierro");
            return false;
        }

        ItemStack stack = createBattleItemStack(itemId, 1);
        if (stack == null) {
            player.sendMessage(ChatColor.RED + "Ese item no se puede entregar como item físico para debug.");
            return false;
        }

        player.getInventory().addItem(stack);
        player.sendMessage(ChatColor.GREEN + "Recibiste 1x " + getBattleItemDebugName(itemId) + ChatColor.GRAY + " para pruebas.");
        return true;
    }

    public List<String> getDebugItemSuggestions() {
        return List.of(
            "1", "2", "4", "7", "8", "9", "11", "13", "20", "21", "22", "23",
            "espada_hierro", "espada_diamante", "arco", "fuego", "hielo", "pocion",
            "flechas", "filete", "lluvia", "tnt", "ballesta", "manzana_dorada"
        );
    }

    public Location getBattleRespawnLocation(Player player) {
        BattlePlayerSession session = playerSessions.get(player.getUniqueId());
        if (session != null) {
            if (session.checkpointLocation != null) {
                return session.checkpointLocation.clone();
            }
            if (session.preparationLocation != null) {
                return session.preparationLocation.clone();
            }
        }

        String worldName = playerArenaWorld.get(player.getUniqueId());
        if (worldName == null) {
            return null;
        }

        SQLBattleWorld battleWorld = configManager.getSQLBattle(worldName);
        if (battleWorld == null) {
            return null;
        }
        if (battleWorld.hasCheckpointLocation()) {
            return battleWorld.getCheckpointLocation().clone();
        }
        if (battleWorld.hasPreparationLocation()) {
            return battleWorld.getPreparationLocation().clone();
        }
        if (battleWorld.hasWaveStartLocation()) {
            return battleWorld.getWaveStartLocation().clone();
        }
        return null;
    }

    public boolean shouldAutoRespawnBattlePlayer(Player player) {
        return playerArenaWorld.containsKey(player.getUniqueId()) || playerSessions.containsKey(player.getUniqueId());
    }

    private int resolveDebugItemId(String itemToken) {
        String normalized = normalizeBattleItemToken(itemToken);
        return switch (normalized) {
            case "1", "espadadehierro", "espadahierro", "iron_sword", "ironsword" -> 1;
            case "2", "espadadediamante", "espadadiamante", "diamond_sword", "diamondsword" -> 2;
            case "3", "hachademadera", "woodenaxe", "wooden_axe" -> 3;
            case "4", "arco", "arcoelfico", "bow" -> 4;
            case "5", "armaduradehierro", "pecheradehierro", "ironchestplate" -> 5;
            case "6", "armaduradediamante", "pecheradediamante", "diamondchestplate" -> 6;
            case "7", "fuego", "hechizodefuego", "fire" -> 7;
            case "8", "hielo", "hechizodehielo", "ice" -> 8;
            case "9", "pocion", "pociondevida", "heal", "healing" -> 9;
            case "11", "flechas", "arrow", "arrows" -> 11;
            case "12", "escudo", "shield" -> 12;
            case "13", "filete", "filetecocido", "cookedbeef", "beef" -> 13;
            case "14", "pantalonesdehierro", "ironleggings" -> 14;
            case "15", "pantalonesdediamante", "diamondleggings" -> 15;
            case "16", "cascodehierro", "ironhelmet" -> 16;
            case "17", "cascodediamante", "diamondhelmet" -> 17;
            case "18", "botasdehierro", "ironboots" -> 18;
            case "19", "botasdediamante", "diamondboots" -> 19;
            case "20", "lluvia", "lluviadeflechas", "arrowrain" -> 20;
            case "21", "tnt", "tnttemporizada", "timedtnt" -> 21;
            case "22", "ballesta", "ballestadeasedio", "crossbow" -> 22;
            case "23", "manzana", "manzanadorada", "goldenapple" -> 23;
            default -> -1;
        };
    }

    private String normalizeBattleItemToken(String token) {
        if (token == null) {
            return "";
        }
        String normalized = Normalizer.normalize(token, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase();
        return normalized.replaceAll("[^a-z0-9]", "");
    }

    private String getBattleItemDebugName(int itemId) {
        return switch (itemId) {
            case 1 -> "Espada de Hierro";
            case 2 -> "Espada de Diamante";
            case 3 -> "Hacha de Madera";
            case 4 -> "Arco Elfico";
            case 5 -> "Armadura de Hierro";
            case 6 -> "Armadura de Diamante";
            case 7 -> "Hechizo de Fuego";
            case 8 -> "Hechizo de Hielo";
            case 9 -> "Pocion de Vida";
            case 11 -> "Flechas";
            case 12 -> "Escudo";
            case 13 -> "Filete Cocido";
            case 14 -> "Pantalones de Hierro";
            case 15 -> "Pantalones de Diamante";
            case 16 -> "Casco de Hierro";
            case 17 -> "Casco de Diamante";
            case 18 -> "Botas de Hierro";
            case 19 -> "Botas de Diamante";
            case 20 -> "Hechizo Lluvia de Flechas";
            case 21 -> "TNT Temporizada";
            case 22 -> "Ballesta de Asedio";
            case 23 -> "Manzana Dorada";
            default -> "Item SQL Battle";
        };
    }

    private ItemStack createBattleItemStack(int itemId, int amount) {
        Material material = mapItemIdToMaterial(itemId);
        if (material == null) {
            return null;
        }

        if (material == Material.POTION) {
            ItemStack potion = new ItemStack(Material.POTION, Math.max(1, amount));
            org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) potion.getItemMeta();
            if (meta != null) {
                meta.setBasePotionData(new org.bukkit.potion.PotionData(org.bukkit.potion.PotionType.INSTANT_HEAL, false, false));
                meta.setDisplayName(ChatColor.RED + "Pocion de Vida");
                potion.setItemMeta(meta);
            }
            return potion;
        }

        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            switch (itemId) {
                case 7 -> {
                    meta.setDisplayName(ChatColor.GOLD + "Hechizo de Fuego");
                    meta.setLore(List.of(
                        ChatColor.GRAY + "Click derecho durante la oleada.",
                        ChatColor.DARK_GRAY + "Explota al impactar."
                    ));
                }
                case 8 -> {
                    meta.setDisplayName(ChatColor.AQUA + "Hechizo de Hielo");
                    meta.setLore(List.of(
                        ChatColor.GRAY + "Click derecho durante la oleada.",
                        ChatColor.DARK_GRAY + "Congela y ralentiza enemigos."
                    ));
                }
                case 20 -> {
                    meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Hechizo Lluvia de Flechas");
                    meta.setLore(List.of(
                        ChatColor.GRAY + "Click derecho durante la oleada.",
                        ChatColor.DARK_GRAY + "Invoca una lluvia sobre enemigos cercanos."
                    ));
                }
                case 21 -> {
                    meta.setDisplayName(ChatColor.RED + "TNT Temporizada");
                    meta.setLore(List.of(
                        ChatColor.GRAY + "Click derecho durante la oleada.",
                        ChatColor.DARK_GRAY + "Se lanza y explota tras 5 segundos."
                    ));
                }
                default -> {
                }
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material mapItemIdToMaterial(int itemId) {
        return switch (itemId) {
            case 1  -> Material.IRON_SWORD;
            case 2  -> Material.DIAMOND_SWORD;
            case 3  -> Material.WOODEN_AXE;
            case 4  -> Material.BOW;
            case 5  -> Material.IRON_CHESTPLATE;
            case 6  -> Material.DIAMOND_CHESTPLATE;
            case 7  -> Material.FIRE_CHARGE;
            case 8  -> Material.SNOWBALL;
            case 9  -> Material.POTION;
            case 11 -> Material.ARROW;
            case 12 -> Material.SHIELD;
            case 13 -> Material.COOKED_BEEF;
            case 14 -> Material.IRON_LEGGINGS;
            case 15 -> Material.DIAMOND_LEGGINGS;
            case 16 -> Material.IRON_HELMET;
            case 17 -> Material.DIAMOND_HELMET;
            case 18 -> Material.IRON_BOOTS;
            case 19 -> Material.DIAMOND_BOOTS;
            case 20 -> Material.SPECTRAL_ARROW;
            case 21 -> Material.TNT;
            case 22 -> Material.CROSSBOW;
            case 23 -> Material.GOLDEN_APPLE;
            default -> null; // 10 = golem (spawned as entity), unknown items ignored
        };
    }

    public void handleBattleEntityDamage(LivingEntity entity, double finalDamage) {
        String role = entity.getPersistentDataContainer().get(sqlBattleRoleKey, PersistentDataType.STRING);
        if (!"enemy".equalsIgnoreCase(role)) {
            return;
        }

        String current = entity.getCustomName();
        if (current == null) {
            return;
        }

        // Strip any existing HP tag appended after " [SQL]"
        String marker = " [SQL]";
        int markerIdx = current.indexOf(marker);
        String base = markerIdx >= 0 ? current.substring(0, markerIdx + marker.length()) : current;

        double newHp = Math.max(0.0, entity.getHealth() - finalDamage);
        double maxHp = entity.getMaxHealth();
        entity.setCustomName(base + " " + ChatColor.RED + (int) Math.ceil(newHp) + "/" + (int) maxHp + "\u2764");
    }

    public boolean isBattleEnemyEntity(LivingEntity entity) {
        String role = entity.getPersistentDataContainer().get(sqlBattleRoleKey, PersistentDataType.STRING);
        return "enemy".equalsIgnoreCase(role);
    }

    public boolean isBattleManagedEntity(LivingEntity entity) {
        String role = entity.getPersistentDataContainer().get(sqlBattleRoleKey, PersistentDataType.STRING);
        return "enemy".equalsIgnoreCase(role) || "summon".equalsIgnoreCase(role);
    }

    public boolean isPlayerInActiveWave(Player player) {
        BattlePlayerSession session = getActiveSession(player);
        return session != null && session.phase == BattleSessionPhase.WAVE_ACTIVE;
    }

    public boolean isPlayerInBattleSession(Player player) {
        return getActiveSession(player) != null;
    }

    public boolean isGamemodeEnforcementEnabled() {
        return gamemodeEnforcementEnabled;
    }

    public void setGamemodeEnforcementEnabled(boolean enabled) {
        this.gamemodeEnforcementEnabled = enabled;
    }

    public void enforceBattleParticipantState(Player player) {
        if (!gamemodeEnforcementEnabled) {
            return;
        }

        if (!isPlayerInBattleArena(player) || isPlayerBattleSpectator(player)) {
            return;
        }

        if (player.getGameMode() != GameMode.ADVENTURE) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        if (player.isFlying()) {
            player.setFlying(false);
        }
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
        }
        if (player.isInvulnerable()) {
            player.setInvulnerable(false);
        }
        if (!player.isCollidable()) {
            player.setCollidable(true);
        }
    }

    public boolean isPlayerBattleSpectator(Player player) {
        String worldName = playerArenaWorld.get(player.getUniqueId());
        if (worldName == null) {
            return false;
        }
        BattleArenaSession arena = arenaSessions.get(worldName);
        if (arena == null) {
            return false;
        }
        return arena.spectators.contains(player.getUniqueId());
    }

    public int getGlobalBattlePoints(UUID playerId) {
        return configManager.getSQLBattleGlobalPoints(playerId);
    }

    public Map<UUID, Integer> getGlobalBattlePointsRanking() {
        Map<UUID, Integer> raw = configManager.getSQLBattleGlobalPointsMap();
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(raw.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        Map<UUID, Integer> ordered = new LinkedHashMap<>();
        for (Map.Entry<UUID, Integer> entry : sorted) {
            ordered.put(entry.getKey(), entry.getValue());
        }
        return ordered;
    }

    public boolean isPlayerInBattleArena(Player player) {
        return playerArenaWorld.containsKey(player.getUniqueId());
    }

    public boolean arePlayersInSameBattleArena(Player a, Player b) {
        String worldA = playerArenaWorld.get(a.getUniqueId());
        if (worldA == null) {
            return false;
        }
        String worldB = playerArenaWorld.get(b.getUniqueId());
        return worldA.equalsIgnoreCase(worldB);
    }

    public void handleBattlePlayerDeath(Player player) {
        String worldName = playerArenaWorld.get(player.getUniqueId());
        if (worldName == null) {
            return;
        }

        BattleArenaSession arena = arenaSessions.get(worldName);
        if (arena == null || !arena.participants.contains(player.getUniqueId())) {
            return;
        }

        BattlePlayerSession session = playerSessions.get(player.getUniqueId());
        if (session == null || session.phase != BattleSessionPhase.WAVE_ACTIVE) {
            return;
        }

        incrementArenaDeaths(arena, player.getUniqueId());
        if (session.checkpointLocation != null) {
            player.setBedSpawnLocation(session.checkpointLocation, true);
        }
    }

    private void tagBattleEntity(LivingEntity entity, BattlePlayerSession session, UUID ownerId, String role) {
        entity.getPersistentDataContainer().set(sqlBattleOwnerKey, PersistentDataType.STRING, ownerId.toString());
        entity.getPersistentDataContainer().set(sqlBattleSessionKey, PersistentDataType.STRING, session.sessionId.toString());
        entity.getPersistentDataContainer().set(sqlBattleRoleKey, PersistentDataType.STRING, role);
    }

    private Location getRandomRegionLocation(Location pos1, Location pos2) {
        if (pos1 == null || pos2 == null || pos1.getWorld() == null || pos2.getWorld() == null) {
            return null;
        }

        World world = pos1.getWorld();
        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        int randomX = (int) Math.round(minX + Math.random() * (maxX - minX));
        int randomZ = (int) Math.round(minZ + Math.random() * (maxZ - minZ));
        int safeY = world.getHighestBlockYAt(randomX, randomZ) + 1;
        return new Location(world, randomX + 0.5D, safeY, randomZ + 0.5D);
    }

    private void removeTrackedEntities(Set<UUID> entityIds) {
        for (UUID entityId : entityIds) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        entityIds.clear();
    }

    private int getTrackedLiveCount(Set<UUID> entityIds) {
        int count = 0;
        for (UUID entityId : entityIds) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity instanceof LivingEntity livingEntity && livingEntity.isValid() && !livingEntity.isDead()) {
                count++;
            }
        }
        return count;
    }

    public void handleBattleEntityDeath(LivingEntity entity) {
        BattleEntitySessionRef ref = resolveEntitySessionRef(entity);
        if (ref == null) {
            return;
        }

        if ("summon".equalsIgnoreCase(ref.role)) {
            ref.session.summonedEntityIds.remove(entity.getUniqueId());
            return;
        }

        if (!"enemy".equalsIgnoreCase(ref.role) || ref.session.phase != BattleSessionPhase.WAVE_ACTIVE) {
            return;
        }

        BattleArenaSession arena = arenaSessions.get(ref.session.worldName);
        Player killer = entity.getKiller();
        if (arena != null && killer != null && arena.participants.contains(killer.getUniqueId())) {
            incrementArenaKills(arena, killer.getUniqueId());
        }

        handleEnemyElimination(ref.ownerId, ref.session, entity.getUniqueId());
    }

    public void handleBattleEntityRemoved(LivingEntity entity) {
        BattleEntitySessionRef ref = resolveEntitySessionRef(entity);
        if (ref == null) {
            return;
        }

        if ("summon".equalsIgnoreCase(ref.role)) {
            ref.session.summonedEntityIds.remove(entity.getUniqueId());
            return;
        }

        if (!"enemy".equalsIgnoreCase(ref.role) || ref.session.phase != BattleSessionPhase.WAVE_ACTIVE) {
            return;
        }

        handleEnemyElimination(ref.ownerId, ref.session, entity.getUniqueId());
    }

    public void reconcileActiveWaveProgress() {
        for (Map.Entry<UUID, BattlePlayerSession> entry : playerSessions.entrySet()) {
            UUID ownerId = entry.getKey();
            BattlePlayerSession session = entry.getValue();
            if (session == null || session.phase != BattleSessionPhase.WAVE_ACTIVE) {
                continue;
            }

            pruneInvalidTrackedEntities(session.enemyEntityIds);
            if (getTrackedLiveCount(session.enemyEntityIds) > 0) {
                continue;
            }

            handleEnemyElimination(ownerId, session, null);
        }
    }

    private void handleEnemyElimination(UUID ownerId, BattlePlayerSession session, UUID removedEnemyId) {
        if (removedEnemyId != null) {
            session.enemyEntityIds.remove(removedEnemyId);
        }

        pruneInvalidTrackedEntities(session.enemyEntityIds);

        if (getTrackedLiveCount(session.enemyEntityIds) > 0) {
            return;
        }

        Player player = Bukkit.getPlayer(ownerId);
        if (player == null) {
            return;
        }

        advanceStageOrCompleteWave(player, session);
    }

    private void pruneInvalidTrackedEntities(Set<UUID> entityIds) {
        if (entityIds.isEmpty()) {
            return;
        }

        Set<UUID> stale = new HashSet<>();
        for (UUID entityId : entityIds) {
            Entity entity = Bukkit.getEntity(entityId);
            if (!(entity instanceof LivingEntity livingEntity) || !livingEntity.isValid() || livingEntity.isDead()) {
                stale.add(entityId);
            }
        }

        if (!stale.isEmpty()) {
            entityIds.removeAll(stale);
        }
    }

    private BattleEntitySessionRef resolveEntitySessionRef(LivingEntity entity) {
        String ownerRaw = entity.getPersistentDataContainer().get(sqlBattleOwnerKey, PersistentDataType.STRING);
        String sessionRaw = entity.getPersistentDataContainer().get(sqlBattleSessionKey, PersistentDataType.STRING);
        String role = entity.getPersistentDataContainer().get(sqlBattleRoleKey, PersistentDataType.STRING);
        if (ownerRaw == null || sessionRaw == null || role == null) {
            return null;
        }

        UUID ownerId;
        UUID sessionId;
        try {
            ownerId = UUID.fromString(ownerRaw);
            sessionId = UUID.fromString(sessionRaw);
        } catch (IllegalArgumentException ex) {
            return null;
        }

        BattlePlayerSession session = playerSessions.get(ownerId);
        if (session == null || !session.sessionId.equals(sessionId)) {
            return null;
        }

        return new BattleEntitySessionRef(ownerId, session, role);
    }

    private void advanceStageOrCompleteWave(Player player, BattlePlayerSession session) {
        int completedStage = session.lastKnownStage;
        int nextStage = findNextStageWithEnemies(session, session.lastKnownStage + 1);
        if (nextStage > 0) {
            session.lastKnownStage = nextStage;
            try {
                session.database.setCurrentStage(nextStage);
            } catch (Exception e) {
                logger.warning("Could not persist SQL Battle stage advancement for player '" + player.getName() + "': " + e.getMessage());
            }

            player.sendMessage(ChatColor.GOLD + "=== SQL Battle: Etapa " + nextStage + " ===");
            player.sendMessage(ChatColor.YELLOW + "Nuevos enemigos e invocaciones han sido desplegados.");
            spawnStageEnemies(player, session, nextStage);
            spawnPreparedSummons(player, session, nextStage);
            giveInventoryItemsToPlayer(player, session, nextStage);
            updatePreparationSidebar(player);
            broadcastStageRanking(session.worldName, "Etapa " + completedStage + " completada");
            return;
        }

        broadcastStageRanking(session.worldName, "Etapa " + completedStage + " completada");
        finishWaveForPlayer(player, session);
    }

    private int findNextStageWithEnemies(BattlePlayerSession session, int startStage) {
        for (int stage = Math.max(FIRST_WAVE_STAGE, startStage); stage <= 3; stage++) {
            try {
                if (!session.database.getEnemiesForStage(stage).isEmpty()) {
                    return stage;
                }
            } catch (Exception e) {
                return -1;
            }
        }
        return -1;
    }

    private void finishWaveForPlayer(Player player, BattlePlayerSession session) {
        session.phase = BattleSessionPhase.BETWEEN_WAVES;
        session.lastPreparationEndReason = "Oleada completada.";
        // Keep the world in active-wave mode until the whole team finishes.
        // Otherwise remaining players lose enemies when the world switches to PEACEFUL/day.

        removeTrackedEntities(session.enemyEntityIds);
        removeTrackedEntities(session.summonedEntityIds);
        clearQueryResultBooks(player);

        if (session.checkpointLocation != null) {
            player.teleport(session.checkpointLocation);
            player.setBedSpawnLocation(session.checkpointLocation, true);
        }

        player.sendMessage(ChatColor.GREEN + "¡Oleada completada! Esperando a tu equipo...");

        BattleArenaSession arena = arenaSessions.get(session.worldName);
        if (arena == null) {
            if (startNextPreparationPhase(player, session)) {
                return;
            }
            updatePreparationSidebar(player);
            player.sendMessage(ChatColor.GRAY + "No se pudo preparar automáticamente la siguiente oleada.");
            player.sendMessage(ChatColor.GRAY + "Usa /sm sqlbattle start para reiniciar manualmente la sesión.");
            return;
        }

        arena.waveReadyPlayers.add(player.getUniqueId());
        int ready = countArenaWaveReady(arena);
        int total = countArenaParticipantsWithSession(arena);
        broadcastArenaMessage(arena, ChatColor.AQUA + player.getName() + ChatColor.GRAY
            + " termino su oleada (" + ready + "/" + total + ").");

        if (ready >= total && total > 0) {
            startNextPreparationForArena(arena);
        }
    }

    private boolean registerPrewaveStartVote(Player player, BattlePlayerSession session, String startReason) {
        if (session.phase != BattleSessionPhase.PREPARATION) {
            player.sendMessage(ChatColor.RED + "Solo puedes votar durante prewave.");
            return false;
        }

        BattleArenaSession arena = arenaSessions.get(session.worldName);
        if (arena == null) {
            beginWavePhase(player, session, startReason);
            return true;
        }

        UUID playerId = player.getUniqueId();
        if (!arena.participants.contains(playerId)) {
            player.sendMessage(ChatColor.RED + "Solo los participantes pueden votar.");
            return false;
        }

        if (!arena.prewaveStartVotes.add(playerId)) {
            player.sendMessage(ChatColor.YELLOW + "Tu voto ya fue registrado.");
            return false;
        }

        int votes = countArenaPrewaveVotes(arena);
        int eligible = countArenaParticipantsInPreparation(arena);
        broadcastArenaMessage(arena, ChatColor.AQUA + player.getName() + ChatColor.GRAY
            + " esta listo para iniciar oleada (" + votes + "/" + eligible + ").");

        if (votes >= eligible && eligible > 0) {
            for (UUID participantId : new ArrayList<>(arena.participants)) {
                Player participant = Bukkit.getPlayer(participantId);
                BattlePlayerSession participantSession = playerSessions.get(participantId);
                if (participant == null || !participant.isOnline() || participantSession == null) {
                    continue;
                }
                if (participantSession.phase != BattleSessionPhase.PREPARATION) {
                    continue;
                }
                beginWavePhase(participant, participantSession, startReason);
            }
            arena.prewaveStartVotes.clear();
        }

        return true;
    }

    private int countArenaParticipantsInPreparation(BattleArenaSession arena) {
        int count = 0;
        for (UUID participantId : arena.participants) {
            BattlePlayerSession participantSession = playerSessions.get(participantId);
            if (participantSession != null && participantSession.phase == BattleSessionPhase.PREPARATION) {
                count++;
            }
        }
        return count;
    }

    private int countArenaPrewaveVotes(BattleArenaSession arena) {
        int count = 0;
        for (UUID voterId : arena.prewaveStartVotes) {
            BattlePlayerSession participantSession = playerSessions.get(voterId);
            if (participantSession != null && participantSession.phase == BattleSessionPhase.PREPARATION) {
                count++;
            }
        }
        return count;
    }

    private int countArenaParticipantsWithSession(BattleArenaSession arena) {
        int count = 0;
        for (UUID participantId : arena.participants) {
            if (playerSessions.containsKey(participantId)) {
                count++;
            }
        }
        return count;
    }

    private int countArenaWaveReady(BattleArenaSession arena) {
        int count = 0;
        for (UUID participantId : arena.waveReadyPlayers) {
            if (playerSessions.containsKey(participantId)) {
                count++;
            }
        }
        return count;
    }

    private void startNextPreparationForArena(BattleArenaSession arena) {
        arena.waveReadyPlayers.clear();
        arena.prewaveStartVotes.clear();
        clearArenaDominationBar(arena);

        for (UUID participantId : new ArrayList<>(arena.participants)) {
            Player participant = Bukkit.getPlayer(participantId);
            BattlePlayerSession session = playerSessions.get(participantId);
            if (participant == null || !participant.isOnline() || session == null) {
                continue;
            }
            if (!startNextPreparationPhase(participant, session)) {
                participant.sendMessage(ChatColor.RED + "No se pudo iniciar tu siguiente prewave.");
            }
        }
    }

    private void incrementArenaKills(BattleArenaSession arena, UUID playerId) {
        int current = arena.killsByPlayer.getOrDefault(playerId, 0);
        arena.killsByPlayer.put(playerId, current + 1);
    }

    private void incrementArenaDeaths(BattleArenaSession arena, UUID playerId) {
        int current = arena.deathsByPlayer.getOrDefault(playerId, 0);
        arena.deathsByPlayer.put(playerId, current + 1);
    }

    private void broadcastStageRanking(String worldName, String label) {
        BattleArenaSession arena = arenaSessions.get(worldName);
        if (arena == null) {
            return;
        }

        List<UUID> ranking = new ArrayList<>(arena.participants);
        ranking.sort((a, b) -> {
            int pointsA = arena.killsByPlayer.getOrDefault(a, 0) - (2 * arena.deathsByPlayer.getOrDefault(a, 0));
            int pointsB = arena.killsByPlayer.getOrDefault(b, 0) - (2 * arena.deathsByPlayer.getOrDefault(b, 0));
            if (pointsA != pointsB) {
                return Integer.compare(pointsB, pointsA);
            }
            return Integer.compare(arena.killsByPlayer.getOrDefault(b, 0), arena.killsByPlayer.getOrDefault(a, 0));
        });

        broadcastArenaMessage(arena, ChatColor.GOLD + "=== Ranking " + label + " ===");
        int position = 1;
        for (UUID playerId : ranking) {
            Player player = Bukkit.getPlayer(playerId);
            String name = player != null ? player.getName() : playerId.toString().substring(0, 8);
            int kills = arena.killsByPlayer.getOrDefault(playerId, 0);
            int deaths = arena.deathsByPlayer.getOrDefault(playerId, 0);
            int points = kills - (2 * deaths);
            broadcastArenaMessage(arena, ChatColor.YELLOW + "#" + position + " " + ChatColor.WHITE + name
                + ChatColor.GRAY + " | Kills: " + kills + " | Muertes: " + deaths + " | Puntos: " + points);
            position++;
        }
    }

    private boolean startNextPreparationPhase(Player player, BattlePlayerSession session) {
        try {
            int currentWave = session.database.getCurrentWaveNumber();
            int nextWave = Math.max(DEFAULT_WAVE_NUMBER, currentWave + 1);

            session.database.loadWave(nextWave);
            int nextPreparationAp = calculatePreparationActionPoints(nextWave, session.teamSizeMultiplier);
            session.database.setPlayerActionPoints(nextPreparationAp);
            session.database.setCurrentStage(0);

            session.phase = BattleSessionPhase.PREPARATION;
            session.lastKnownWave = nextWave;
            session.lastKnownStage = 0;
            session.lastPreparationEndReason = "Prewave de oleada " + nextWave + " iniciada.";

            setWaveActive(session.worldName, false);
            clearQueryResultBooks(player);

            if (session.preparationLocation != null) {
                player.teleport(session.preparationLocation);
            }
            if (session.checkpointLocation != null) {
                player.setBedSpawnLocation(session.checkpointLocation, true);
            }

            givePrewaveStartItem(player);
            givePrewaveLeaveItem(player);
            playPreparationMusic(player);

            updatePreparationSidebar(player);
            player.sendMessage(ChatColor.AQUA + "Comienza la fase prewave de la oleada " + nextWave + ".");
            player.sendMessage(ChatColor.GREEN + "AP reiniciados: " + nextPreparationAp);
            return true;
        } catch (Exception e) {
            logger.warning("Could not initialize next SQL Battle prewave for player '" + player.getName() + "': " + e.getMessage());
            return false;
        }
    }

    private int getEnemyMultiplier(BattlePlayerSession session) {
        BattleArenaSession arena = arenaSessions.get(session.worldName);
        if (arena == null) {
            return Math.max(1, session.teamSizeMultiplier);
        }

        int activeParticipants = 0;
        for (UUID participantId : arena.participants) {
            Player participant = Bukkit.getPlayer(participantId);
            if (participant == null || !participant.isOnline()) {
                continue;
            }
            BattlePlayerSession participantSession = playerSessions.get(participantId);
            if (participantSession == null) {
                continue;
            }
            if (participantSession.phase == BattleSessionPhase.WAVE_ACTIVE || participantSession.phase == BattleSessionPhase.PREPARATION) {
                activeParticipants++;
            }
        }

        if (activeParticipants <= 0) {
            return Math.max(1, session.teamSizeMultiplier);
        }
        return Math.max(1, Math.min(MAX_ARENA_PARTICIPANTS, activeParticipants));
    }

    private void finishArenaMatch(BattleArenaSession arena, String reason) {
        stopArenaCountdown(arena);
        clearArenaCountdownBar(arena);
        clearArenaDominationBar(arena);
        arena.state = ArenaState.FINISHED;

        Map<UUID, Integer> awardedGlobalPoints = persistArenaGlobalPoints(arena);

        setWaveActive(arena.worldName, false);
        SQLBattleWorld battleWorld = configManager.getSQLBattle(arena.worldName);

        Set<UUID> allPlayers = arena.getAllPlayers();
        for (UUID playerId : allPlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                playerArenaWorld.remove(playerId);
                continue;
            }

            if (playerSessions.containsKey(playerId)) {
                endPreparationSession(player, true);
            }

            if (reason != null && !reason.isEmpty()) {
                player.sendMessage(reason);
            }
            if (arena.participants.contains(playerId)) {
                int awarded = awardedGlobalPoints.getOrDefault(playerId, 0);
                int total = getGlobalBattlePoints(playerId);
                if (awarded > 0) {
                    player.sendMessage(ChatColor.AQUA + "Puntos globales SQL Battle: +" + awarded + ChatColor.GRAY + " (Total: " + total + ")");
                } else {
                    player.sendMessage(ChatColor.GRAY + "Puntos globales SQL Battle: " + total);
                }
            }
            stopBattleMusic(player);
            player.sendMessage(ChatColor.GOLD + "Partida finalizada. Regresando al hub...");
            playEpicFinishMusic(player);
            if (player.isDead()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    player.spigot().respawn();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            teleportPlayerToHub(player, battleWorld);
                        }
                    });
                });
            } else {
                teleportPlayerToHub(player, battleWorld);
            }
        }

        for (UUID participantId : new ArrayList<>(arena.participants)) {
            playerArenaWorld.remove(participantId);
            spectatorTargetCursor.remove(participantId);
        }
        for (UUID spectatorId : new ArrayList<>(arena.spectators)) {
            playerArenaWorld.remove(spectatorId);
            spectatorTargetCursor.remove(spectatorId);
        }

        arena.participants.clear();
        arena.spectators.clear();
        arena.voteEligible.clear();
        arena.startVotes.clear();
        arena.prewaveStartVotes.clear();
        arena.waveReadyPlayers.clear();
        arena.killsByPlayer.clear();
        arena.deathsByPlayer.clear();
        arena.state = ArenaState.WAITING;
        arena.countdownRemaining = 0;
        arena.countdownMax = 0;
    }

    private Map<UUID, Integer> persistArenaGlobalPoints(BattleArenaSession arena) {
        Map<UUID, Integer> awardedByPlayer = new HashMap<>();
        for (UUID participantId : arena.participants) {
            int kills = arena.killsByPlayer.getOrDefault(participantId, 0);
            int deaths = arena.deathsByPlayer.getOrDefault(participantId, 0);
            int matchPoints = Math.max(0, kills - (2 * deaths));
            if (matchPoints <= 0) {
                continue;
            }
            configManager.addSQLBattleGlobalPoints(participantId, matchPoints);
            awardedByPlayer.put(participantId, matchPoints);
        }
        return awardedByPlayer;
    }

    private void createArenaCountdownBar(BattleArenaSession arena) {
        if (arena.countdownBar != null) {
            arena.countdownBar.removeAll();
        }

        BossBar bar = Bukkit.createBossBar("SQL Battle", BarColor.YELLOW, BarStyle.SOLID);
        for (UUID playerId : arena.getAllPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                bar.addPlayer(player);
            }
        }
        arena.countdownBar = bar;
    }

    private void updateArenaCountdownBar(BattleArenaSession arena) {
        if (arena.countdownBar == null) {
            return;
        }

        int max = Math.max(1, arena.countdownMax);
        int remaining = Math.max(0, arena.countdownRemaining);
        double progress = Math.max(0.0D, Math.min(1.0D, (double) remaining / (double) max));

        arena.countdownBar.setProgress(progress);
        arena.countdownBar.setTitle(ChatColor.GOLD + "SQL Battle inicia en " + ChatColor.YELLOW + remaining + "s");
        arena.countdownBar.setColor(remaining <= 10 ? BarColor.RED : BarColor.YELLOW);
    }

    private void clearArenaCountdownBar(BattleArenaSession arena) {
        if (arena.countdownBar == null) {
            return;
        }
        arena.countdownBar.removeAll();
        arena.countdownBar = null;
    }

    private void teleportPlayerToHub(Player player, SQLBattleWorld battleWorld) {
        clearSimulatedSpectatorMode(player);
        Location hub = configManager.getServerSpawnpoint();
        if (hub != null) {
            player.teleport(hub);
            player.setGameMode(GameMode.ADVENTURE);
            return;
        }

        if (battleWorld != null && battleWorld.hasWorldEntryLocation()) {
            player.teleport(battleWorld.getWorldEntryLocation());
            player.setGameMode(GameMode.ADVENTURE);
            return;
        }

        List<World> worlds = Bukkit.getWorlds();
        if (!worlds.isEmpty()) {
            player.teleport(worlds.get(0).getSpawnLocation());
        }
        player.setGameMode(GameMode.ADVENTURE);
    }

    private void playEpicFinishMusic(Player player) {
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 0.7f, 1.2f);
            }
        }, 12L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);
            }
        }, 26L);
    }

    private void playPreparationMusic(Player player) {
        playRandomBattleMusic(player, PREWAVE_RELAX_MUSIC, 0.65f, 1.0f);
    }

    private void playWaveMusic(Player player) {
        playRandomBattleMusic(player, WAVE_BATTLE_MUSIC, 0.8f, 1.0f);
    }

    private void playRandomBattleMusic(Player player, Sound[] playlist, float volume, float pitch) {
        if (playlist == null || playlist.length == 0) {
            return;
        }
        stopBattleMusic(player);
        int index = ThreadLocalRandom.current().nextInt(playlist.length);
        player.playSound(player, playlist[index], volume, pitch);
    }

    private void stopBattleMusic(Player player) {
        for (Sound music : PREWAVE_RELAX_MUSIC) {
            player.stopSound(music);
        }
        for (Sound music : WAVE_BATTLE_MUSIC) {
            player.stopSound(music);
        }
    }

    private enum ArenaState {
        WAITING,
        STARTING,
        IN_GAME,
        FINISHED
    }

    private static class BattleArenaSession {
        private final String worldName;
        private final Set<UUID> participants;
        private final Set<UUID> spectators;
        private final Set<UUID> voteEligible;
        private final Set<UUID> startVotes;
        private final Set<UUID> prewaveStartVotes;
        private final Set<UUID> waveReadyPlayers;
        private final Map<UUID, Integer> killsByPlayer;
        private final Map<UUID, Integer> deathsByPlayer;
        private ArenaState state;
        private int countdownRemaining;
        private int countdownMax;
        private BukkitTask countdownTask;
        private BossBar countdownBar;
        private BossBar dominationBar;
        private double dominationProgressSeconds;
        private long dominationStartedAtMillis;
        private long lastCastleParticleMillis;

        private BattleArenaSession(String worldName) {
            this.worldName = worldName;
            this.participants = new HashSet<>();
            this.spectators = new HashSet<>();
            this.voteEligible = new HashSet<>();
            this.startVotes = new HashSet<>();
            this.prewaveStartVotes = new HashSet<>();
            this.waveReadyPlayers = new HashSet<>();
            this.killsByPlayer = new HashMap<>();
            this.deathsByPlayer = new HashMap<>();
            this.state = ArenaState.WAITING;
            this.countdownRemaining = 0;
            this.countdownMax = 0;
            this.countdownBar = null;
            this.dominationBar = null;
            this.dominationProgressSeconds = 0.0D;
            this.dominationStartedAtMillis = 0L;
            this.lastCastleParticleMillis = 0L;
        }

        private Set<UUID> getAllPlayers() {
            Set<UUID> combined = new HashSet<>(participants);
            combined.addAll(spectators);
            return combined;
        }
    }

    private enum BattleSessionPhase {
        PREPARATION("Preparacion"),
        WAVE_ACTIVE("Oleada activa"),
        BETWEEN_WAVES("Entre oleadas"),
        COMPLETED("Completada"),
        FAILED("Fallida");

        private final String displayName;

        BattleSessionPhase(String displayName) {
            this.displayName = displayName;
        }

        private String getDisplayName() {
            return displayName;
        }

        private String getSidebarLabel() {
            return switch (this) {
                case PREPARATION -> "Prep";
                case WAVE_ACTIVE -> "Oleada";
                case BETWEEN_WAVES -> "Pausa";
                case COMPLETED -> "Fin";
                case FAILED -> "Fallo";
            };
        }
    }

    private static class BattlePlayerSession {
        private final String worldName;
        private final BattleSQLDatabase database;
        private final BattleQueryExecutor executor;
        private final BattleQueryValidator validator;
        private final long createdAtMillis;
        private final UUID sessionId;
        private final Location worldEntryLocation;
        private final Location waveStartLocation;
        private final Location checkpointLocation;
        private final Location preparationLocation;
        private final Location summonZonePos1;
        private final Location summonZonePos2;
        private final Location enemySpawnPos1;
        private final Location enemySpawnPos2;
        private final Set<UUID> enemyEntityIds;
        private final Set<UUID> summonedEntityIds;
        private final int teamSizeMultiplier;
        private Scoreboard scoreboard;
        private BattleSessionPhase phase;
        private int lastKnownWave;
        private int lastKnownStage;
        private String lastPreparationEndReason;

        private BattlePlayerSession(SQLBattleWorld battleWorld, BattleSQLDatabase database, int teamSizeMultiplier) {
            this.worldName = battleWorld.getWorldName();
            this.database = database;
            this.executor = new BattleQueryExecutor();
            this.validator = new BattleQueryValidator();
            this.createdAtMillis = System.currentTimeMillis();
            this.sessionId = UUID.randomUUID();
            this.worldEntryLocation = cloneLocation(battleWorld.getWorldEntryLocation());
            this.waveStartLocation = cloneLocation(battleWorld.getWaveStartLocation());
            this.checkpointLocation = cloneLocation(battleWorld.getCheckpointLocation());
            this.preparationLocation = cloneLocation(battleWorld.getPreparationLocation());
            this.summonZonePos1 = cloneLocation(battleWorld.getSummonZonePos1());
            this.summonZonePos2 = cloneLocation(battleWorld.getSummonZonePos2());
            this.enemySpawnPos1 = cloneLocation(battleWorld.getEnemySpawnPos1());
            this.enemySpawnPos2 = cloneLocation(battleWorld.getEnemySpawnPos2());
            this.enemyEntityIds = new HashSet<>();
            this.summonedEntityIds = new HashSet<>();
            this.teamSizeMultiplier = Math.max(1, Math.min(MAX_ARENA_PARTICIPANTS, teamSizeMultiplier));
            this.phase = BattleSessionPhase.PREPARATION;
            this.lastKnownWave = DEFAULT_WAVE_NUMBER;
            this.lastKnownStage = 0;
            this.lastPreparationEndReason = "";
        }

        private long getSessionAgeSeconds() {
            return Math.max(0L, (System.currentTimeMillis() - createdAtMillis) / 1000L);
        }

        private boolean hasExpandedZoneModel() {
            return worldEntryLocation != null
                && waveStartLocation != null
                && preparationLocation != null
                && summonZonePos1 != null
                && summonZonePos2 != null
                && enemySpawnPos1 != null
                && enemySpawnPos2 != null;
        }

        private static Location cloneLocation(Location location) {
            return location != null ? location.clone() : null;
        }
    }

    private static final class BattleEntitySessionRef {
        private final UUID ownerId;
        private final BattlePlayerSession session;
        private final String role;

        private BattleEntitySessionRef(UUID ownerId, BattlePlayerSession session, String role) {
            this.ownerId = ownerId;
            this.session = session;
            this.role = role;
        }
    }
}