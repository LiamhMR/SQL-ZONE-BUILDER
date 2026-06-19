package com.seminario.plugin.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import com.seminario.plugin.config.ConfigManager;
import com.seminario.plugin.database.SQLDatabase;
import com.seminario.plugin.model.SQLDifficulty;
import com.seminario.plugin.model.SQLDungeonWorld;
import com.seminario.plugin.model.SQLLevel;
import com.seminario.plugin.sql.SQLChallengeBank;
import com.seminario.plugin.sql.SQLQueryResult;
import com.seminario.plugin.sql.SQLValidationEngine;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Manages SQL Dungeons, levels, and player interactions
 */
public class SQLDungeonManager {
    
    private static final Logger logger = Logger.getLogger(SQLDungeonManager.class.getName());
    
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final SQLDatabase database;
    private final SQLValidationEngine validationEngine;
    private final SpawnpointManager spawnpointManager;
    
    // Player session tracking
    private final Map<Player, Integer> playerCurrentLevel;
    private final Map<Player, Long> playerStartTime;
    private final Map<Player, String> playerCurrentWorld;
    private final Map<String, Map<Player, Integer>> worldPlayerProgress; // worldName -> player -> maxLevelCompleted
    
    // Boss bars for SQL progress display
    private final Map<Player, BossBar> playerBossBars;
    
    // Heart system tracking: player -> [red hearts, yellow hearts]
    private final Map<Player, int[]> playerHearts;
    
    // Failed attempts tracking: player -> failed attempts count for current level
    private final Map<Player, Integer> playerFailedAttempts;
    
    // Dynamic challenges: player -> current challenge for their session
    private final Map<Player, SQLChallengeBank.Challenge> playerCurrentChallenge;
    
    // Super jumps tracking: player -> number of super jumps available for current level
    private final Map<Player, Integer> playerSuperJumps;
    
    // Professor invocations tracking: player -> number of professor invocations available (accumulative)
    private final Map<Player, Integer> playerProfessorInvocations;
    
    // Professor hint tracking: player -> number of hints shown for current challenge (1, 2, or 3)
    private final Map<Player, Integer> playerHintProgress;
    
    // Professor invocations per level tracking: player -> level where they got invocation (to prevent multiple per level)
    private final Map<Player, Integer> playerInvocationLevel;
    
    // Red hearts lost tracking: player -> number of red hearts lost in current level
    private final Map<Player, Integer> playerRedHeartsLost;
    
    // Yellow hearts lost tracking: player -> number of yellow hearts lost (resets when red heart is lost)
    private final Map<Player, Integer> playerYellowHeartsLost;
    
    // SQL Scoreboards for players
    private final Map<Player, Scoreboard> playerScoreboards;
    public SQLDungeonManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.database = new SQLDatabase(plugin);
        this.validationEngine = new SQLValidationEngine(database, plugin);
        this.spawnpointManager = new SpawnpointManager(plugin, configManager);
        this.playerCurrentLevel = new HashMap<>();
        this.playerStartTime = new HashMap<>();
        this.playerCurrentWorld = new HashMap<>();
        this.worldPlayerProgress = new HashMap<>();
        this.playerBossBars = new HashMap<>();
        this.playerHearts = new HashMap<>();
        this.playerFailedAttempts = new HashMap<>();
        this.playerCurrentChallenge = new HashMap<>();
        this.playerSuperJumps = new HashMap<>();
        this.playerProfessorInvocations = new HashMap<>();
        this.playerHintProgress = new HashMap<>();
        this.playerInvocationLevel = new HashMap<>();
        this.playerRedHeartsLost = new HashMap<>();
        this.playerYellowHeartsLost = new HashMap<>();
        this.playerScoreboards = new HashMap<>();
    }
    
    /**
     * Initialize the SQL dungeon system
     * @return true if initialization was successful
     */
    public boolean initialize() {
        logger.info("Inicializando sistema SQL Dungeon...");
        
        if (!database.initialize()) {
            logger.severe("Failed to initialize SQL database");
            return false;
        }
        
        if (!validationEngine.testDatabase()) {
            logger.severe("SQL validation engine test failed");
            return false;
        }
        
        logger.info("Sistema SQL Dungeon inicializado correctamente");
        return true;
    }
    
    /**
     * Create a new SQL dungeon world
     * @param world The world to convert to SQL dungeon
     * @return true if created successfully
     */
    public boolean createSQLDungeon(World world) {
        if (configManager.isSQLDungeon(world.getName())) {
            return false; // Already exists
        }
        
        SQLDungeonWorld sqlWorld = new SQLDungeonWorld(world.getName());
        configManager.addSQLDungeon(sqlWorld);
        
        logger.info("Created SQL Dungeon in world: " + world.getName());
        return true;
    }
    
    /**
     * Clona la configuración de un SQLDungeon existente a un nuevo mundo.
     * Se copian todos los niveles con sus coordenadas; la referencia de mundo
     * en cada Location se reemplaza por el mundo destino.
     *
     * @param sourceWorldName Nombre del mundo origen (ya configurado como SQLDungeon)
     * @param destWorldName   Nombre del mundo destino (debe estar cargado, no ser SQLDungeon)
     * @return true si el clon se completó exitosamente
     */
    public boolean cloneSQLDungeon(String sourceWorldName, String destWorldName) {
        SQLDungeonWorld source = configManager.getSQLDungeon(sourceWorldName);
        if (source == null) return false;

        org.bukkit.World destWorld = org.bukkit.Bukkit.getWorld(destWorldName);
        if (destWorld == null) return false;

        if (configManager.isSQLDungeon(destWorldName)) return false;

        SQLDungeonWorld dest = new SQLDungeonWorld(destWorldName);

        for (com.seminario.plugin.model.SQLLevel srcLevel : source.getLevels().values()) {
            com.seminario.plugin.model.SQLLevel destLevel = new com.seminario.plugin.model.SQLLevel(
                srcLevel.getLevelNumber(),
                srcLevel.getDifficulty(),
                cloneDungeonLocation(srcLevel.getCheckpointLocation(), destWorld)
            );
            destLevel.setEntryLocation(cloneDungeonLocation(srcLevel.getEntryLocation(), destWorld));
            destLevel.setChallenge(srcLevel.getChallenge());
            destLevel.setExpectedQuery(srcLevel.getExpectedQuery());
            destLevel.setHint1(srcLevel.getHint1());
            destLevel.setHint2(srcLevel.getHint2());
            destLevel.setHint3(srcLevel.getHint3());
            dest.getLevels().put(destLevel.getLevelNumber(), destLevel);
        }

        configManager.addSQLDungeon(dest);
        logger.info("Cloned SQL Dungeon config from '" + sourceWorldName + "' to '" + destWorldName + "'");
        return true;
    }

    private org.bukkit.Location cloneDungeonLocation(org.bukkit.Location src, org.bukkit.World destWorld) {
        if (src == null) return null;
        return new org.bukkit.Location(destWorld, src.getX(), src.getY(), src.getZ(), src.getYaw(), src.getPitch());
    }

    /**
     * Check if a world is a SQL dungeon
     * @param worldName The world name
     * @return true if it's a SQL dungeon world
     */
    public boolean isSQLDungeon(String worldName) {
        return configManager.isSQLDungeon(worldName);
    }
    
    /**
     * Get SQL dungeon world by name
     * @param worldName The world name
     * @return SQLDungeonWorld or null if not found
     */
    public SQLDungeonWorld getSQLDungeon(String worldName) {
        return configManager.getSQLDungeon(worldName);
    }
    
    /**
     * Add a level to a SQL dungeon with auto-assigned challenge
     * @param worldName The world name
     * @param levelNumber The level number
     * @param difficulty The difficulty (1-5)
     * @param location The checkpoint location
     * @return true if added successfully
     */
    public boolean addLevel(String worldName, int levelNumber, int difficulty, Location location) {
        SQLDungeonWorld sqlWorld = configManager.getSQLDungeon(worldName);
        if (sqlWorld == null) {
            return false;
        }
        
        SQLDifficulty sqlDifficulty = SQLDifficulty.fromLevel(difficulty);
        if (sqlDifficulty == null) {
            return false;
        }
        
        // Get a random challenge from the bank for this difficulty
        SQLChallengeBank.Challenge challenge = SQLChallengeBank.getRandomChallenge(sqlDifficulty);
        
        SQLLevel level = new SQLLevel(levelNumber, sqlDifficulty, location);
        // Set the challenge data from the bank
        level.setChallenge(challenge.getDescription());
        level.setExpectedQuery(challenge.getExpectedQuery());
        level.setHint1(challenge.getHint1());
        level.setHint2(challenge.getHint2());
        level.setHint3(challenge.getHint3());
        
        boolean success = sqlWorld.addLevel(level);
        if (success) {
            configManager.updateSQLDungeon(sqlWorld);
            logger.info("Added level " + levelNumber + " with difficulty " + sqlDifficulty + " and challenge: " + challenge.getDescription());
        }
        return success;
    }
    
    /**
     * Remove a level from a SQL dungeon
     * @param worldName The world name
     * @param levelNumber The level number
     * @return true if removed successfully
     */
    public boolean removeLevel(String worldName, int levelNumber) {
        SQLDungeonWorld sqlWorld = configManager.getSQLDungeon(worldName);
        if (sqlWorld == null) {
            return false;
        }
        
        boolean success = sqlWorld.removeLevel(levelNumber);
        if (success) {
            configManager.updateSQLDungeon(sqlWorld);
        }
        return success;
    }
    
    /**
     * Set the entry location for a level
     * @param worldName The world name
     * @param levelNumber The level number
     * @param location The entry location
     * @return true if set successfully
     */
    public boolean setLevelEntry(String worldName, int levelNumber, Location location) {
        SQLDungeonWorld sqlWorld = configManager.getSQLDungeon(worldName);
        if (sqlWorld == null) {
            return false;
        }
        
        SQLLevel level = sqlWorld.getLevel(levelNumber);
        if (level == null) {
            return false;
        }
        
        level.setEntryLocation(location);
        configManager.updateSQLDungeon(sqlWorld);
        return true;
    }
    
    /**
     * Start a SQL challenge for a player at a specific level
     * @param player The player
     * @param levelNumber The level number
     */
    public void startChallenge(Player player, int levelNumber) {
        String worldName = player.getWorld().getName();
        SQLDungeonWorld sqlWorld = configManager.getSQLDungeon(worldName);
        
        if (sqlWorld == null) {
            player.sendMessage(Component.text("Este mundo no es un SQL Dungeon", NamedTextColor.RED));
            return;
        }
        
        SQLLevel level = sqlWorld.getLevel(levelNumber);
        if (level == null || !level.isComplete()) {
            player.sendMessage(Component.text("Nivel no encontrado o incompleto", NamedTextColor.RED));
            return;
        }
        
        // Set player session data
        playerCurrentLevel.put(player, levelNumber);
        playerStartTime.put(player, System.currentTimeMillis());
        
        // Send challenge info
        player.sendMessage(Component.text("=== DESAFÍO SQL - NIVEL " + levelNumber + " ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Dificultad: " + level.getDifficulty().getDisplayName(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Tiempo límite: " + level.getDifficulty().getTimeLimit() + " segundos", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Pistas disponibles: " + level.getDifficulty().getHintsAvailable(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        // Get dynamic challenge for this player
        SQLChallengeBank.Challenge playerChallenge = playerCurrentChallenge.get(player);
        String challengeText = (playerChallenge != null) ? playerChallenge.getDescription() : level.getChallenge();
        
        player.sendMessage(Component.text("RETO: " + challengeText, NamedTextColor.AQUA));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("Completa el parkour y responde con la consulta SQL correcta!", NamedTextColor.GREEN));
    }
    
    /**
     * Handle player SQL submission
     * @param player The player
     * @param query The SQL query submitted
     */
    public void handleSQLSubmission(Player player, String query) {
        Integer levelNumber = playerCurrentLevel.get(player);
        if (levelNumber == null) {
            player.sendMessage(Component.text("No tienes un desafío activo", NamedTextColor.RED));
            return;
        }
        
        String worldName = player.getWorld().getName();
        SQLDungeonWorld sqlWorld = configManager.getSQLDungeon(worldName);
        SQLLevel level = sqlWorld.getLevel(levelNumber);
        
        if (level == null) {
            player.sendMessage(Component.text("Error: Nivel no encontrado", NamedTextColor.RED));
            return;
        }
        
        // Check time limit
        Long startTime = playerStartTime.get(player);
        if (startTime != null) {
            long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
            if (elapsedSeconds > level.getDifficulty().getTimeLimit()) {
                player.sendMessage(Component.text("¡Tiempo agotado!", NamedTextColor.RED));
                failChallenge(player);
                return;
            }
        }
        
        // Remove existing result books from player inventory
        com.seminario.plugin.util.SQLResultBook.removeExistingResultBooks(player);
        
        // Validate the SQL query with enhanced results using dynamic challenge
        SQLChallengeBank.Challenge playerChallenge = playerCurrentChallenge.get(player);
        String expectedQuery = (playerChallenge != null) ? playerChallenge.getExpectedQuery() : level.getExpectedQuery();
        com.seminario.plugin.sql.SQLQueryResult result = validationEngine.validateQueryWithResults(query, expectedQuery);
        
        // Generate and give result book to player
        org.bukkit.inventory.ItemStack resultBook = null;
        
        if (result.hasError()) {
            // Create error book
            resultBook = com.seminario.plugin.util.SQLResultBook.createNoResultsBook(player, query, false);
        } else if (result.hasResults()) {
            // Create book with query results
            resultBook = com.seminario.plugin.util.SQLResultBook.createResultBook(player, query, result.getResultSet(), result.isCorrect());
        } else {
            // Create no results book
            resultBook = com.seminario.plugin.util.SQLResultBook.createNoResultsBook(player, query, result.isCorrect());
        }
        
        // Give book to player
        if (resultBook != null) {
            player.getInventory().addItem(resultBook);
            player.sendMessage(Component.text("📖 Revisa el libro para ver los resultados de tu consulta", NamedTextColor.AQUA));
        }
        
        // Handle challenge validation
        if (result.isCorrect()) {
            successChallenge(player, levelNumber, startTime);
        } else {
            player.sendMessage(Component.text("Respuesta incorrecta:", NamedTextColor.RED));
            player.sendMessage(Component.text(result.getFeedback(), NamedTextColor.YELLOW));
            
            if (result.hasError()) {
                player.sendMessage(Component.text("Error: " + result.getError(), NamedTextColor.GRAY));
            }
            
            failChallenge(player);
        }
    }
    
    /**
     * Handle successful challenge completion
     * @param player The player
     * @param levelNumber The completed level
     * @param startTime The start time
     */
    private void successChallenge(Player player, int levelNumber, Long startTime) {
        long completionTime = startTime != null ? (System.currentTimeMillis() - startTime) / 1000 : 0;
        String worldName = playerCurrentWorld.get(player);
        
        // Restore yellow hearts on successful completion
        int[] hearts = playerHearts.getOrDefault(player, new int[]{3, 6});
        hearts[1] = 6; // Restore all yellow hearts
        playerHearts.put(player, hearts);
        playerYellowHeartsLost.put(player, 0); // Reset yellow hearts lost counter
        updatePlayerHealth(player);
        
        player.sendMessage(Component.text("¡CORRECTO!", NamedTextColor.GREEN));
        player.sendMessage(Component.text("Has completado el nivel " + levelNumber, NamedTextColor.GOLD));
        player.sendMessage(Component.text("Tiempo: " + completionTime + " segundos", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("💛 Tus vidas amarillas se han restaurado!", NamedTextColor.YELLOW));
        
        // Complete the level using the new system
        if (worldName != null) {
            completeLevel(player, worldName, levelNumber);
        } else {
            // Fallback - clear session manually
            clearPlayerSession(player);
        }
    }
    
    /**
     * Handle failed challenge
     * @param player The player
     */
    private void failChallenge(Player player) {
        // Get current heart status before removal
        int[] hearts = playerHearts.getOrDefault(player, new int[]{3, 6});
        int yellowHeartsBefore = hearts[1];
        
        // Try to remove a yellow heart first
        // Note: removeYellowHeart() already handles red heart removal when all yellow hearts are depleted
        boolean yellowHeartRemoved = removeYellowHeart(player);
        
        if (yellowHeartRemoved) {
            // Yellow heart was removed successfully
            if (yellowHeartsBefore == 1) {
                // This was the LAST yellow heart - removeYellowHeart() already removed a red heart
                // Just trigger lightning and hint book as punishment
                logger.info("Last yellow heart removed for " + player.getName() + " - triggering lightning");
                handleThirdFailedAttempt(player);
                
                // Check if player has any red hearts left after the automatic red heart removal
                int[] currentHearts = playerHearts.getOrDefault(player, new int[]{3, 6});
                if (currentHearts[0] <= 0) {
                    // No red hearts left - player is completely defeated
                    handleCompleteDefeat(player);
                    return;
                }
            } else {
                // Normal failure behavior - just launch player
                launchPlayerRandomly(player);
            }
        } else {
            // No yellow hearts available - player must be completely defeated
            logger.warning("No yellow hearts to remove for player " + player.getName());
            handleCompleteDefeat(player);
            return;
        }
        
        // Don't clear the session completely - just reset the timer so they can try again
        playerStartTime.put(player, System.currentTimeMillis());
        
        if (hasHeartsRemaining(player)) {
            player.sendMessage(Component.text("Puedes intentar de nuevo cuando estés listo.", NamedTextColor.GRAY));
        }
    }
    
    /**
     * Launch player randomly with no fall damage (punishment for wrong answer)
     * @param player The player
     */
    private void launchPlayerRandomly(Player player) {
        // Implementation similar to ChestportGUI.handleNoChoice()
        double angle = Math.random() * 2 * Math.PI;
        double strength = 1.5 + Math.random() * 0.5;
        
        org.bukkit.util.Vector launchVector = new org.bukkit.util.Vector(
            Math.cos(angle) * strength,
            1.8,
            Math.sin(angle) * strength
        );
        
        player.setVelocity(launchVector);
        
        // Give no fall damage effect
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.SLOW_FALLING, 300, 0));
        
        // Play sound
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_TNT_PRIMED, 1.0f, 1.5f);
    }
    
    /**
     * Handle third failed attempt - strike lightning and give hint book
     * @param player The player who failed 3 times
     */
    private void handleThirdFailedAttempt(Player player) {
        logger.info("Executing handleThirdFailedAttempt for player " + player.getName());
        
        // Strike lightning at player location
        player.getWorld().strikeLightning(player.getLocation());
        logger.info("Lightning struck at player location");
        
        // Dramatic message
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("⚡ ¡ZEUS ESTÁ MOLESTO! ⚡", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Estás fallando demasiado...", NamedTextColor.RED));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("🎁 Pero aquí tienes una pista divina:", NamedTextColor.AQUA));
        
        // Generate hint book with expected results
        generateHintBook(player);
        
        // Play dramatic sound
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);
        
        // Don't reset failed attempts here since we're using heart system
        
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("📖 Revisa el libro para ver qué resultados espera la consulta", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("¡Ahora inténtalo de nuevo con esta información!", NamedTextColor.GREEN));
    }
    
    /**
     * Handle complete defeat (no hearts remaining)
     * @param player The player who lost all hearts
     */
    private void handleCompleteDefeat(Player player) {
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("� ¡HAS PERDIDO TODAS TUS VIDAS! 💀", NamedTextColor.DARK_RED));
        player.sendMessage(Component.text("El SQL Dungeon te ha derrotado completamente...", NamedTextColor.RED));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        
        // Show large title message
        player.sendTitle(
            "Aún no estás listo",
            "practica en el laboratorio", 
            20, 100, 30  // fadeIn, stay, fadeOut (in ticks)
        );
        
        player.sendMessage(Component.text("🌟 Regresando al spawn para que puedas intentar de nuevo", NamedTextColor.YELLOW));
        
        // Reset hearts for next attempt
        resetPlayerHearts(player);
        
        // Teleport to server spawn after a delay with special effects
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Get SpawnpointManager from the main plugin
            if (plugin instanceof com.seminario.plugin.App) {
                com.seminario.plugin.App app = (com.seminario.plugin.App) plugin;
                SpawnpointManager spawnpointManager = app.getSpawnpointManager();
                
                if (spawnpointManager != null) {
                    // Teleport with special effects for defeat
                    spawnpointManager.teleportToSpawnpoint(player, true);
                    player.sendMessage(Component.text("✅ ¡Puedes intentar el dungeon de nuevo cuando estés listo!", NamedTextColor.GREEN));
                } else {
                    // Fallback: teleport to default world spawn
                    org.bukkit.World defaultWorld = plugin.getServer().getWorlds().get(0);
                    player.teleport(defaultWorld.getSpawnLocation());
                    player.sendMessage(Component.text("✅ Enviado al mundo principal", NamedTextColor.GREEN));
                }
            } else {
                // Fallback: teleport to default world spawn
                org.bukkit.World defaultWorld = plugin.getServer().getWorlds().get(0);
                player.teleport(defaultWorld.getSpawnLocation());
                player.sendMessage(Component.text("✅ Enviado al mundo principal", NamedTextColor.GREEN));
            }
        }, 60L); // 3 second delay
        
        // Clear session
        clearPlayerSession(player);
    }
    
    /**
     * Generate a hint book with expected query results
     * @param player The player to give the hint to
     */
    private void generateHintBook(Player player) {
        logger.info("Starting generateHintBook for player " + player.getName());
        
        String worldName = playerCurrentWorld.get(player);
        Integer currentLevel = playerCurrentLevel.get(player);
        
        logger.info("Player world: " + worldName + ", level: " + currentLevel);
        
        if (worldName == null || currentLevel == null) {
            player.sendMessage(Component.text("Error generando pista - sesión inválida", NamedTextColor.RED));
            logger.warning("Invalid session for hint book generation");
            return;
        }
        
        SQLDungeonWorld sqlWorld = getSQLDungeon(worldName);
        if (sqlWorld == null) {
            logger.warning("SQL World not found: " + worldName);
            return;
        }
        
        SQLLevel level = sqlWorld.getLevel(currentLevel);
        
        // Get dynamic challenge for this player or fallback to level
        SQLChallengeBank.Challenge playerChallenge = playerCurrentChallenge.get(player);
        String expectedQuery = (playerChallenge != null) ? playerChallenge.getExpectedQuery() : 
            (level != null ? level.getExpectedQuery() : null);
        
        if (expectedQuery == null) {
            logger.warning("Expected query not found for level " + currentLevel);
            return;
        }
        
        logger.info("Expected query: " + expectedQuery);
        
        try {
            // Execute the expected query to get the correct results
            SQLQueryResult expectedResult = validationEngine.executeQueryForLaboratory(expectedQuery);
            
            if (expectedResult.hasError()) {
                player.sendMessage(Component.text("Error generando pista: " + expectedResult.getError(), NamedTextColor.RED));
                logger.warning("Error executing expected query: " + expectedResult.getError());
                return;
            }
            
            logger.info("Expected query executed successfully");
            
            // Create hint book with expected results
            org.bukkit.inventory.ItemStack hintBook = com.seminario.plugin.util.SQLResultBook.createResultBook(
                player, 
                "PISTA: Estos son los resultados esperados", 
                expectedResult.getResultSet(), 
                true
            );
            
            if (hintBook != null) {
                logger.info("Hint book created successfully");
                
                // Customize book meta for hint
                org.bukkit.inventory.meta.BookMeta bookMeta = (org.bukkit.inventory.meta.BookMeta) hintBook.getItemMeta();
                if (bookMeta != null) {
                    bookMeta.setTitle("⚡ Pista Divina ⚡");
                    bookMeta.setAuthor("Zeus");
                    hintBook.setItemMeta(bookMeta);
                    logger.info("Book meta customized");
                }
                
                player.getInventory().addItem(hintBook);
                logger.info("Hint book added to player inventory");
            } else {
                logger.warning("Failed to create hint book - book is null");
            }
            
        } catch (Exception e) {
            player.sendMessage(Component.text("Error generando libro de pista: " + e.getMessage(), NamedTextColor.RED));
            logger.warning("Error generating hint book for player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get database schema information
     * @return Schema as string
     */
    public String getDatabaseSchema() {
        return validationEngine.getSchemaInfo();
    }
    
    /**
     * Get validation engine for external use
     * @return SQLValidationEngine instance
     */
    public SQLValidationEngine getValidationEngine() {
        return validationEngine;
    }
    
    /**
     * Get all SQL dungeon worlds
     * @return Map of world names to SQLDungeonWorld
     */
    public Map<String, SQLDungeonWorld> getAllSQLWorlds() {
        return configManager.getAllSQLDungeons();
    }
    
    /**
     * Get the plugin instance
     * @return JavaPlugin instance
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }
    
    /**
     * Get available challenges count for a difficulty
     * @param difficulty The difficulty level
     * @return Number of available challenges
     */
    public int getAvailableChallenges(SQLDifficulty difficulty) {
        return SQLChallengeBank.getChallengeCount(difficulty);
    }
    
    /**
     * Get total challenges count across all difficulties
     * @return Total number of challenges in the bank
     */
    public int getTotalChallenges() {
        return SQLChallengeBank.getTotalChallenges();
    }
    
    /**
     * Regenerate challenge for a specific level (get new random challenge)
     * @param worldName The world name
     * @param levelNumber The level number
     * @return true if challenge was regenerated successfully
     */
    public boolean regenerateChallenge(String worldName, int levelNumber) {
        SQLDungeonWorld sqlWorld = configManager.getSQLDungeon(worldName);
        if (sqlWorld == null) {
            return false;
        }
        
        SQLLevel level = sqlWorld.getLevel(levelNumber);
        if (level == null) {
            return false;
        }
        
        // Get a new random challenge for the same difficulty
        SQLChallengeBank.Challenge challenge = SQLChallengeBank.getRandomChallenge(level.getDifficulty());
        
        // Update the level with new challenge data
        level.setChallenge(challenge.getDescription());
        level.setExpectedQuery(challenge.getExpectedQuery());
        level.setHint1(challenge.getHint1());
        level.setHint2(challenge.getHint2());
        level.setHint3(challenge.getHint3());
        
        configManager.updateSQLDungeon(sqlWorld);
        logger.info(String.format("Regenerated challenge for level %d: %s", levelNumber, challenge.getDescription()));
        return true;
    }
    
    /**
     * Start a player session in a SQL Dungeon world
     * @param player The player entering the world
     * @param worldName The SQL Dungeon world name
     */
    public void startPlayerSession(Player player, String worldName) {
        SQLDungeonWorld sqlWorld = getSQLDungeon(worldName);
        if (sqlWorld == null || !sqlWorld.isPlayable()) {
            return;
        }
        
        // Initialize world progress tracking if not exists
        worldPlayerProgress.computeIfAbsent(worldName, k -> new HashMap<>());
        
        // Get player's current progress in this world (0 means no levels completed)
        int currentProgress = worldPlayerProgress.get(worldName).getOrDefault(player, 0);
        
        // Determine starting level (next level to attempt)
        int startLevel = currentProgress + 1;
        
        // Check if there's a level to start
        SQLLevel levelToStart = sqlWorld.getLevel(startLevel);
        if (levelToStart == null) {
            // Player has completed all levels in this world
            player.sendMessage(Component.text("🎉 ¡Felicidades! Has completado todos los niveles de este SQL Dungeon.", NamedTextColor.GOLD));
            player.sendMessage(Component.text("Progreso: " + currentProgress + "/" + sqlWorld.getLevelCount() + " niveles completados", NamedTextColor.GREEN));
            return;
        }
        
        // Assign a random challenge dynamically for this player
        SQLChallengeBank.Challenge dynamicChallenge = SQLChallengeBank.getRandomChallenge(levelToStart.getDifficulty());
        playerCurrentChallenge.put(player, dynamicChallenge);
        
        // Reset hint progress for new challenge
        playerHintProgress.put(player, 0);
        
        // Set up player session
        playerCurrentLevel.put(player, startLevel);
        playerCurrentWorld.put(player, worldName);
        playerStartTime.put(player, System.currentTimeMillis());
        
        // Reset failed attempts for new session
        playerFailedAttempts.put(player, 0);
        
        // Initialize heart system for this player (don't reset if already has hearts)
        initializeHeartSystem(player);
        
        // Reset and give super jumps for the initial level
        resetSuperJumpsForLevel(player);
        
        // Reset red hearts lost counter for new level
        playerRedHeartsLost.put(player, 0);
        
        // Show progress and next level info
        player.sendMessage(Component.text("📊 Tu progreso: " + currentProgress + "/" + sqlWorld.getLevelCount() + " niveles completados", NamedTextColor.AQUA));
        
        if (currentProgress > 0) {
            player.sendMessage(Component.text("🎯 Continuando desde el nivel " + startLevel, NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("🎯 Comenzando desde el nivel " + startLevel, NamedTextColor.YELLOW));
        }
        
        // Show the dynamic challenge
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("📝 CONSULTA SQL:", NamedTextColor.GOLD));
    player.sendMessage(Component.text(dynamicChallenge.getDescription(), NamedTextColor.WHITE));

    // Prepare player's SQL inventory (clear and give tools)
    giveSQLInventoryToPlayer(player);
    
    // Create SQL scoreboard
    createSQLScoreboard(player);
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("⏰ Tiempo límite: " + levelToStart.getDifficulty().getTimeLimit() + " segundos", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("💡 Pistas disponibles: " + levelToStart.getDifficulty().getHintsAvailable(), NamedTextColor.AQUA));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("✏️ Escribe tu consulta SQL en el chat para responder", NamedTextColor.GREEN));
        
        // Teleport to the checkpoint location (where the level was created)
        Location checkpointLocation = levelToStart.getCheckpointLocation();
        if (checkpointLocation != null) {
            player.teleport(checkpointLocation);
            player.sendMessage(Component.text("📍 Teletransportado al nivel " + startLevel, NamedTextColor.GREEN));
        }
        
        // Show entry point information if available
        if (levelToStart.hasEntry()) {
            player.sendMessage(Component.text("💡 Busca el bloque de entrada para activar el desafío", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("⚠️ Este nivel no tiene bloque de entrada configurado", NamedTextColor.YELLOW));
        }
        
        // Create boss bar for progress tracking
        createOrUpdateBossBar(player, worldName);
        
        // Initialize heart system for this player
        initializeHeartSystem(player);
        
        logger.info(String.format("Started SQL session for player %s in world %s at level %d", player.getName(), worldName, startLevel));
    }
    
    /**
     * Complete a level for a player
     * @param player The player who completed the level
     * @param worldName The world name
     * @param levelNumber The completed level number
     */
    public void completeLevel(Player player, String worldName, int levelNumber) {
        // Update world progress
        worldPlayerProgress.computeIfAbsent(worldName, k -> new HashMap<>());
        Map<Player, Integer> worldProgress = worldPlayerProgress.get(worldName);
        
        int currentProgress = worldProgress.getOrDefault(player, 0);
        if (levelNumber > currentProgress) {
            worldProgress.put(player, levelNumber);
            
            player.sendMessage(Component.text("🎉 ¡Nivel " + levelNumber + " completado!", NamedTextColor.GOLD));
            
            // Update boss bar progress
            updateBossBarProgress(player, worldName);
            
            // Check if there's a next level
            SQLDungeonWorld sqlWorld = getSQLDungeon(worldName);
            SQLLevel nextLevel = sqlWorld.getLevel(levelNumber + 1);
            
            if (nextLevel != null) {
                // Start next level automatically
                startNextLevel(player, worldName, levelNumber + 1);
            } else {
                // Player completed all levels - send to server spawn
                player.sendMessage(Component.text("🏆 ¡FELICIDADES! Has completado todo el SQL Dungeon!", NamedTextColor.GOLD));
                player.sendMessage(Component.text("Total de niveles completados: " + levelNumber, NamedTextColor.GREEN));
                player.sendMessage(Component.text("", NamedTextColor.WHITE));
                player.sendMessage(Component.text("🌟 Regresando al spawn del servidor...", NamedTextColor.YELLOW));
                
                // Teleport to server spawn after a short delay
                
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    /*  ALL THIS IS WRONG
                    Location spawnLocation = getServerSpawnLocation();
                    
                    if (spawnLocation != null) {
                        player.teleport(spawnLocation);
                        player.sendMessage(Component.text("✅ ¡Bienvenido de vuelta al spawn!", NamedTextColor.GREEN));
                        
                        // Set Adventure mode and give lobby inventory after completing dungeon
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            com.seminario.plugin.App mainPlugin = (com.seminario.plugin.App) plugin;
                            mainPlugin.getLobbyManager().giveLobbyInventoryWithPostTest(player, true);
                        }, 10L); // Small delay after teleport
                        
                    } else {
                        // Fallback: teleport to default world spawn
                        org.bukkit.World defaultWorld = plugin.getServer().getWorlds().get(0);
                        player.teleport(defaultWorld.getSpawnLocation());
                        player.sendMessage(Component.text("✅ Enviado al mundo principal", NamedTextColor.GREEN));
                        
                        // Set Adventure mode and give lobby inventory even in fallback
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            com.seminario.plugin.App mainPlugin = (com.seminario.plugin.App) plugin;
                            mainPlugin.getLobbyManager().giveLobbyInventoryWithPostTest(player, true);
                        }, 10L);
                    }*/
                   //Use direct function
                   spawnpointManager.teleportToSpawnpoint(player, false);
                }, 60L); // 3 second delay to let them read the congratulations message
                
                // Clear session
                clearPlayerSession(player);
            }
        }
    }
    
    /**
     * Start the next level for a player
     * @param player The player
     * @param worldName The world name
     * @param levelNumber The level to start
     */
    private void startNextLevel(Player player, String worldName, int levelNumber) {
        SQLDungeonWorld sqlWorld = getSQLDungeon(worldName);
        SQLLevel level = sqlWorld.getLevel(levelNumber);
        
        if (level == null) {
            return;
        }
        
        // Assign a new random challenge dynamically for this level
        SQLChallengeBank.Challenge dynamicChallenge = SQLChallengeBank.getRandomChallenge(level.getDifficulty());
        playerCurrentChallenge.put(player, dynamicChallenge);
        
        // Reset hint progress for new challenge
        playerHintProgress.put(player, 0);
        
        // Reset red hearts lost counter for new level
        playerRedHeartsLost.put(player, 0);
        
        // Update player session
        playerCurrentLevel.put(player, levelNumber);
        playerStartTime.put(player, System.currentTimeMillis());
        
        // Don't reset hearts when advancing to next level - keep current heart state
        
        player.sendMessage(Component.text("🎯 Iniciando nivel " + levelNumber + " (" + level.getDifficulty() + ")", NamedTextColor.YELLOW));
        
    // Show the dynamic challenge
    player.sendMessage(Component.text("", NamedTextColor.WHITE));
    player.sendMessage(Component.text("📝 CONSULTA SQL:", NamedTextColor.GOLD));
    player.sendMessage(Component.text(dynamicChallenge.getDescription(), NamedTextColor.WHITE));

    // For each new level, reset and give super jumps and SQL tools
    resetSuperJumpsForLevel(player);
    giveSQLInventoryToPlayer(player);
    
    // Update scoreboard with new level info
    updateSQLScoreboard(player);
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("⏰ Tiempo límite: " + level.getDifficulty().getTimeLimit() + " segundos", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("💡 Pistas disponibles: " + level.getDifficulty().getHintsAvailable(), NamedTextColor.AQUA));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("✏️ Escribe tu consulta SQL en el chat para responder", NamedTextColor.GREEN));
        
        // Teleport to the checkpoint location (where the level was created)
        Location checkpointLocation = level.getCheckpointLocation();
        if (checkpointLocation != null) {
            player.teleport(checkpointLocation);
            player.sendMessage(Component.text("📍 Teletransportado al nivel " + levelNumber, NamedTextColor.GREEN));
        }
        
        // Show entry point information if available
        if (level.hasEntry()) {
            player.sendMessage(Component.text("💡 Busca el bloque de entrada para activar el desafío", NamedTextColor.YELLOW));
        }
        
        // Update boss bar with new level info
        updateBossBarProgress(player, worldName);
    }
    
    /**
     * Get player's current progress in a world
     * @param player The player
     * @param worldName The world name
     * @return Number of completed levels (0 if none)
     */
    public int getPlayerProgress(Player player, String worldName) {
        return worldPlayerProgress.getOrDefault(worldName, new HashMap<>()).getOrDefault(player, 0);
    }
    
    /**
     * Clear a player's active session
     * @param player The player
     */
    public void clearPlayerSession(Player player) {
        playerCurrentLevel.remove(player);
        playerStartTime.remove(player);
        playerCurrentWorld.remove(player);
        playerFailedAttempts.remove(player);
        playerCurrentChallenge.remove(player); // Clear dynamic challenge assignment
        playerSuperJumps.remove(player); // Clear super jumps tracking
        playerYellowHeartsLost.remove(player); // Clear yellow hearts lost counter
        // Note: We don't remove hearts here - they persist even after session ends
        
        // Remove boss bar when session ends
        removeBossBar(player);
        
        // Remove SQL scoreboard when session ends
        removeSQLScoreboard(player);
        
        // Restore player's health to normal when leaving dungeon
        restorePlayerHealth(player);
        
        // Give lobby inventory when returning from SQL world
        com.seminario.plugin.App app = (com.seminario.plugin.App) plugin;
        com.seminario.plugin.manager.LobbyManager lobbyManager = app.getLobbyManager();
        if (lobbyManager != null) {
            lobbyManager.giveLobbyInventoryWithPostTest(player, false);
        }
    }
    
    /**
     * Restore player's health to normal Minecraft values when leaving dungeon
     * @param player The player
     */
    private void restorePlayerHealth(Player player) {
        // Restore max health to 20 (10 hearts)
        if (player.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
        }
        
        // Restore health to full
        player.setHealth(20.0);
        
        // Remove absorption effect (yellow hearts)
        player.removePotionEffect(PotionEffectType.ABSORPTION);
        
        logger.info("Restored " + player.getName() + " health to normal Minecraft values");
    }
    
    /**
     * Get player's current level in active session
     * @param player The player
     * @return Current level or -1 if no active session
     */
    public int getPlayerCurrentLevel(Player player) {
        return playerCurrentLevel.getOrDefault(player, -1);
    }
    
    /**
     * Get player's current world in active session
     * @param player The player
     * @return Current world name or null if no active session
     */
    public String getPlayerCurrentWorld(Player player) {
        return playerCurrentWorld.get(player);
    }
    
    /**
     * Check if player has an active session
     * @param player The player
     * @return true if player has an active session
     */
    public boolean hasActiveSession(Player player) {
        return playerCurrentLevel.containsKey(player) && playerCurrentWorld.containsKey(player);
    }
    
    /**
     * Get the server spawn location
     * @return Server spawn location or null if not found
     */
    private Location getServerSpawnLocation() {
        try {

            // Try to get the main world spawn (usually the first world loaded)
            org.bukkit.World mainWorld = plugin.getServer().getWorlds().get(0);
            
            if (mainWorld != null) {
                return mainWorld.getSpawnLocation();
            }
            
            // Try common world names as fallback
            String[] commonWorldNames = {"world", "spawn", "lobby", "hub"};
            for (String worldName : commonWorldNames) {
                org.bukkit.World world = plugin.getServer().getWorld(worldName);
                if (world != null) {
                    return world.getSpawnLocation();
                }
            }
            
            return null;
        } catch (Exception e) {
            logger.warning(String.format("Failed to get server spawn location: %s", e.getMessage()));
            return null;
        }
    }
    
    /**
     * Get the checkpoint location for a player's current level
     * @param player The player
     * @return The checkpoint location or null if not found
     */
    public Location getCurrentLevelCheckpoint(Player player) {
        String worldName = player.getWorld().getName();
        if (!isSQLDungeon(worldName)) {
            return null;
        }
        
        SQLDungeonWorld sqlWorld = getSQLDungeon(worldName);
        if (sqlWorld == null) {
            return null;
        }
        
        // Get player's current level based on progress
        int currentProgress = getPlayerProgress(player, worldName);
        int currentLevel = currentProgress + 1; // Next level to complete
        
        SQLLevel level = sqlWorld.getLevel(currentLevel);
        if (level != null) {
            return level.getCheckpointLocation();
        }
        
        // If no current level found, try to find the first level
        SQLLevel firstLevel = sqlWorld.getLevel(1);
        return firstLevel != null ? firstLevel.getCheckpointLocation() : null;
    }
    
    /**
     * Handle player death in SQL Dungeon - respawn at current level checkpoint
     * @param player The player who died
     * @return true if death was handled (player was in SQL Dungeon), false otherwise
     */
    public boolean handlePlayerDeath(Player player) {
        String worldName = player.getWorld().getName();
        if (!isSQLDungeon(worldName)) {
            return false; // Not in SQL Dungeon, let normal death handling occur
        }
        
        Location checkpoint = getCurrentLevelCheckpoint(player);
        if (checkpoint == null) {
            // Fallback: teleport to world spawn
            player.teleport(player.getWorld().getSpawnLocation());
            player.sendMessage(Component.text("⚠️ No se encontró checkpoint, enviado al spawn del mundo", NamedTextColor.YELLOW));
        } else {
            // Teleport to current level checkpoint
            player.teleport(checkpoint);
            
            int currentProgress = getPlayerProgress(player, worldName);
            int currentLevel = currentProgress + 1;
            
            player.sendMessage(Component.text("💀 Has muerto, pero no te preocupes...", NamedTextColor.RED));
            player.sendMessage(Component.text("📍 Regresaste al checkpoint del nivel " + currentLevel, NamedTextColor.GREEN));
            player.sendMessage(Component.text("¡Puedes intentar de nuevo!", NamedTextColor.YELLOW));
        }
        
        return true; // Death was handled
    }
    
    /**
     * Create or update boss bar for player in SQL Dungeon
     * @param player The player
     * @param worldName The SQL world name
     */
    public void createOrUpdateBossBar(Player player, String worldName) {
        SQLDungeonWorld sqlWorld = getSQLDungeon(worldName);
        if (sqlWorld == null) {
            return;
        }
        
        int currentProgress = getPlayerProgress(player, worldName);
        int currentLevel = currentProgress + 1;
        int totalLevels = sqlWorld.getLevels().size();
        
        // Get current challenge - use player's assigned dynamic challenge
        SQLLevel level = sqlWorld.getLevel(currentLevel);
        String challengeDescription = "Explorando...";
        if (level != null) {
            // Use the player's current dynamic challenge, not a new random one
            SQLChallengeBank.Challenge playerChallenge = playerCurrentChallenge.get(player);
            if (playerChallenge != null) {
                challengeDescription = playerChallenge.getDescription();
            } else {
                // Fallback to level challenge if no dynamic challenge assigned
                challengeDescription = level.getChallenge() != null ? level.getChallenge() : "Consulta no disponible";
            }
        }
        
        // Calculate progress percentage
        double progressPercentage = (double) currentProgress / totalLevels;
        
        // Create title
        String title = String.format("§6SQL Dungeon - Nivel %d/%d §7| §f%s", 
            currentLevel, totalLevels, challengeDescription);
        
        // Choose color based on difficulty
        BarColor barColor = getBarColorForLevel(level);
        
        // Remove existing boss bar if present
        removeBossBar(player);
        
        // Create new boss bar
        BossBar bossBar = Bukkit.createBossBar(title, barColor, BarStyle.SEGMENTED_10);
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, progressPercentage)));
        bossBar.addPlayer(player);
        bossBar.setVisible(true);
        
        // Store boss bar
        playerBossBars.put(player, bossBar);
        
        logger.info(String.format("Created boss bar for player %s in world %s: Level %d/%d (%.1f%%)",
            player.getName(), worldName, currentLevel, totalLevels, progressPercentage * 100));
    }
    
    /**
     * Update boss bar for player with new challenge info
     * @param player The player
     * @param challengeDescription The new challenge description
     */
    public void updateBossBarChallenge(Player player, String challengeDescription) {
        BossBar bossBar = playerBossBars.get(player);
        if (bossBar == null) {
            return;
        }
        
        String worldName = getPlayerCurrentWorld(player);
        if (worldName == null) {
            return;
        }
        
        SQLDungeonWorld sqlWorld = getSQLDungeon(worldName);
        if (sqlWorld == null) {
            return;
        }
        
        int currentProgress = getPlayerProgress(player, worldName);
        int currentLevel = currentProgress + 1;
        int totalLevels = sqlWorld.getLevels().size();
        
        String title = String.format("§6SQL Dungeon - Nivel %d/%d §7| §f%s", 
            currentLevel, totalLevels, challengeDescription);
        
        bossBar.setTitle(title);
    }
    
    /**
     * Remove boss bar for player
     * @param player The player
     */
    public void removeBossBar(Player player) {
        BossBar bossBar = playerBossBars.remove(player);
        if (bossBar != null) {
            bossBar.removePlayer(player);
            bossBar.setVisible(false);
        }
    }
    
    /**
     * Get bar color based on level difficulty
     * @param level The SQL level
     * @return Appropriate bar color
     */
    private BarColor getBarColorForLevel(SQLLevel level) {
        if (level == null) {
            return BarColor.WHITE;
        }
        
        return switch (level.getDifficulty()) {
            case BASIC -> BarColor.GREEN;
            case INTERMEDIATE -> BarColor.BLUE;
            case ADVANCED -> BarColor.YELLOW;
            case EXPERT -> BarColor.RED;
            case MASTER -> BarColor.PURPLE;
        };
    }
    
    /**
     * Update boss bar progress when player completes a level
     * @param player The player
     * @param worldName The world name
     */
    public void updateBossBarProgress(Player player, String worldName) {
        BossBar bossBar = playerBossBars.get(player);
        if (bossBar == null) {
            // Create new boss bar if it doesn't exist
            createOrUpdateBossBar(player, worldName);
            return;
        }
        
        SQLDungeonWorld sqlWorld = getSQLDungeon(worldName);
        if (sqlWorld == null) {
            return;
        }
        
        int currentProgress = getPlayerProgress(player, worldName);
        int totalLevels = sqlWorld.getLevels().size();
        double progressPercentage = (double) currentProgress / totalLevels;
        
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, progressPercentage)));
        
        // Update color and title for new level
        int currentLevel = currentProgress + 1;
        if (currentLevel <= totalLevels) {
            SQLLevel level = sqlWorld.getLevel(currentLevel);
            BarColor newColor = getBarColorForLevel(level);
            bossBar.setColor(newColor);
            
            if (level != null) {
                // Use the player's current dynamic challenge, not a new random one
                SQLChallengeBank.Challenge playerChallenge = playerCurrentChallenge.get(player);
                String challengeDescription;
                if (playerChallenge != null) {
                    challengeDescription = playerChallenge.getDescription();
                } else {
                    // Fallback to level challenge if no dynamic challenge assigned
                    challengeDescription = level.getChallenge() != null ? level.getChallenge() : "Consulta no disponible";
                }
                
                String title = String.format("§6SQL Dungeon - Nivel %d/%d §7| §f%s", 
                    currentLevel, totalLevels, challengeDescription);
                bossBar.setTitle(title);
            }
        } else {
            // Dungeon completed
            bossBar.setTitle("§a§l¡SQL Dungeon Completado! §6Felicitaciones");
            bossBar.setColor(BarColor.GREEN);
            bossBar.setProgress(1.0);
        }
    }
    
    /**
     * Check if player has a boss bar active
     * @param player The player
     * @return true if player has an active boss bar
     */
    public boolean hasBossBar(Player player) {
        return playerBossBars.containsKey(player);
    }
    
    /**
     * Initialize heart system for a player using native Minecraft health interface
     * @param player The player
     */
    private void initializeHeartSystem(Player player) {
        // Check if player already has hearts set, if not initialize with full hearts
        if (!playerHearts.containsKey(player)) {
            playerHearts.put(player, new int[]{3, 6}); // [red hearts, yellow hearts]
        }
        // Always reset yellow hearts lost counter when initializing system
        playerYellowHeartsLost.put(player, 0);
        updatePlayerHealth(player);
        logger.info("Initialized native heart system for " + player.getName() + " with 3 red and 6 yellow hearts");
    }
    
    /**
     * Update player's visual health using native Minecraft health interface
     * @param player The player
     */
    public void updatePlayerHealth(Player player) {
        int[] hearts = playerHearts.getOrDefault(player, new int[]{3, 6});
        int redHearts = hearts[0];
        int yellowHearts = hearts[1];
        
        // Set max health based on red hearts (each heart = 2 health points)
        double maxHealth = redHearts * 2.0;
        if (player.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
        }
        
        // Set current health to max
        player.setHealth(maxHealth);
        
        // Update absorption hearts (yellow hearts) using potion effect
        player.removePotionEffect(PotionEffectType.ABSORPTION);
        if (yellowHearts > 0) {
            // Absorption level: level 0 = 2 yellow hearts, level 1 = 4 yellow hearts, level 2 = 6 yellow hearts
            // yellowHearts / 2 - 1 gives us the right level (2->0, 4->1, 6->2)
            int absorptionLevel = (yellowHearts / 2) - 1;
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, Integer.MAX_VALUE, absorptionLevel, false, false));
        }
        
        // Update scoreboard to reflect heart changes
        updateSQLScoreboard(player);
        
        logger.info("Updated " + player.getName() + " health: " + redHearts + " red hearts, " + yellowHearts + " yellow hearts");
    }
    
    /**
     * Remove a yellow heart from player (failed attempt)
     * @param player The player
     * @return true if yellow heart was removed, false if no yellow hearts left
     */
    private boolean removeYellowHeart(Player player) {
        int[] hearts = playerHearts.getOrDefault(player, new int[]{3, 6});
        
        if (hearts[1] >= 2) {
            // Remove 2 yellow hearts
            hearts[1] -= 2;
            playerHearts.put(player, hearts);
            
            // Track yellow hearts lost
            int yellowLost = playerYellowHeartsLost.getOrDefault(player, 0) + 2;
            playerYellowHeartsLost.put(player, yellowLost);
            
            updatePlayerHealth(player);
            
            player.sendMessage(Component.text("¡¡ Perdiste 2 vidas amarillas!", NamedTextColor.YELLOW));
            handleThirdFailedAttempt(player); // Check if we need to handle 3rd failed attempt
            // Check if 6 yellow hearts have been lost (time to lose a red heart)
            if (yellowLost >= 6) {
                // Reset yellow hearts lost counter
                playerYellowHeartsLost.put(player, 0);
                
                // Remove a red heart and restore yellow hearts
                if (hearts[0] > 0) {
                    hearts[0]--; // Remove red heart
                    hearts[1] = 6; // Restore yellow hearts to 6
                    playerHearts.put(player, hearts);
                    updatePlayerHealth(player);
                    
                    player.sendMessage(Component.text("❤️ Perdiste una vida roja! Tus vidas amarillas se restauraron.", NamedTextColor.RED));
                    
                    // Grant one super jump when a red heart is lost
                    grantSuperJumps(player, 1);
                    updateSuperJumpItem(player);
                    
                    // Track red hearts lost and potentially grant professor invocation
                    handleRedHeartLoss(player);
                    
                    updateSQLScoreboard(player); // Update scoreboard
                } else {
                    // No red hearts left - player is completely defeated
                    logger.info("Player " + player.getName() + " has no more hearts - triggering complete defeat");
                    handleCompleteDefeat(player);
                }
            }
            
            return true;
        } else if (hearts[1] > 0) {
            // Only 1 yellow heart left - special case
            // Remove the last yellow heart but don't trigger red heart loss yet
            hearts[1] = 0;
            playerHearts.put(player, hearts);
            
            // Track yellow hearts lost
            int yellowLost = playerYellowHeartsLost.getOrDefault(player, 0) + 1;
            playerYellowHeartsLost.put(player, yellowLost);
            
            updatePlayerHealth(player);
            player.sendMessage(Component.text("¡¡ Perdiste tu última vida amarilla!", NamedTextColor.YELLOW));
            
            // Check if 6 yellow hearts have been lost in total
            if (yellowLost >= 6) {
                // Reset yellow hearts lost counter
                playerYellowHeartsLost.put(player, 0);
                
                // Remove a red heart and restore yellow hearts
                if (hearts[0] > 0) {
                    hearts[0]--; // Remove red heart
                    hearts[1] = 6; // Restore yellow hearts to 6
                    playerHearts.put(player, hearts);
                    updatePlayerHealth(player);
                    
                    player.sendMessage(Component.text("❤️ Perdiste una vida roja! Tus vidas amarillas se restauraron.", NamedTextColor.RED));
                    
                    // Grant one super jump when a red heart is lost
                    grantSuperJumps(player, 1);
                    updateSuperJumpItem(player);
                    
                    // Track red hearts lost and potentially grant professor invocation
                    handleRedHeartLoss(player);
                    
                    updateSQLScoreboard(player); // Update scoreboard
                } else {
                    // No red hearts left - player is completely defeated
                    logger.info("Player " + player.getName() + " has no more hearts - triggering complete defeat");
                    handleCompleteDefeat(player);
                }
            }
            
            return true;
        }
        
        // No yellow hearts left and no red hearts - complete defeat
        if (hearts[0] <= 0) {
            logger.info("Player " + player.getName() + " has no more hearts - triggering complete defeat");
            handleCompleteDefeat(player);
        }
        
        return false;
    }
    
    /**
     * Remove a yellow heart when player dies (lava, fall, etc.)
     * @param player The player who died
     * @return true if yellow heart was removed, false if no yellow hearts left
     */
    public boolean removeYellowHeartOnDeath(Player player) {
        // Only process if player is in an active SQL session
        if (!isPlayerInSession(player)) {
            return false;
        }
        
        return removeYellowHeart(player);
    }
    
    /**
     * Remove a yellow heart from player (for damage events)
     * @param player The player
     * @return true if yellow heart was removed, false if no yellow hearts left
     */
    public boolean removeYellowHeartFromDamage(Player player) {
        // Only process if player is in an active SQL session
        if (!isPlayerInSession(player)) {
            return false;
        }
        
        return removeYellowHeart(player);
    }
    
    /**
     * Restore player to checkpoint and update GUI
     * @param player The player
     */
    public void restorePlayerAtCheckpoint(Player player) {
        String worldName = playerCurrentWorld.get(player);
        Integer currentLevel = playerCurrentLevel.get(player);
        
        if (worldName == null || currentLevel == null) {
            player.sendMessage(Component.text("Error: No se pudo encontrar tu checkpoint actual.", NamedTextColor.RED));
            return;
        }
        
        SQLDungeonWorld sqlWorld = getSQLDungeon(worldName);
        if (sqlWorld == null) {
            player.sendMessage(Component.text("Error: Mundo SQL no encontrado.", NamedTextColor.RED));
            return;
        }
        
        SQLLevel level = sqlWorld.getLevel(currentLevel);
        if (level == null) {
            player.sendMessage(Component.text("Error: Nivel no encontrado.", NamedTextColor.RED));
            return;
        }
        
        Location checkpointLocation = level.getCheckpointLocation();
        if (checkpointLocation != null) {
            // Teleport to checkpoint
            player.teleport(checkpointLocation);
            player.sendMessage(Component.text("🏁 Teletransportado al checkpoint del nivel " + currentLevel, NamedTextColor.AQUA));
        } else {
            // Fallback: teleport to world spawn
            player.teleport(player.getWorld().getSpawnLocation());
            player.sendMessage(Component.text("⚠️ No se encontró checkpoint, teletransportado al spawn", NamedTextColor.YELLOW));
        }
        
        // Restore full health
        player.setHealth(player.getMaxHealth());
        player.sendMessage(Component.text("❤️ Tu salud ha sido restaurada", NamedTextColor.GREEN));
        
        // Clear fire and negative effects
        player.setFireTicks(0);
        
        // Update GUI immediately
        updateSQLScoreboard(player);
    }
    
    /**
     * Remove a red heart from player (after 3 failures)
     * @param player The player
     * @return true if red heart was removed, false if no red hearts left
     */
    private boolean removeRedHeart(Player player) {
        int[] hearts = playerHearts.getOrDefault(player, new int[]{3, 6});
        
        if (hearts[0] > 0) {
            hearts[0]--; // Remove red heart
            hearts[1] = 6; // Restore yellow hearts
            playerHearts.put(player, hearts);
            updatePlayerHealth(player);
            
            player.sendMessage(Component.text("❤️ Perdiste una vida roja! Tus vidas amarillas se restauraron.", NamedTextColor.RED));
            
            // Teleport to current level checkpoint
            teleportToCurrentLevelCheckpoint(player);
            
            // Grant one super jump when a red heart is lost
            grantSuperJumps(player, 1);
            updateSuperJumpItem(player);
            
            // Track red hearts lost and potentially grant professor invocation
            handleRedHeartLoss(player);
            
            updateSQLScoreboard(player); // Update scoreboard
            
            return true;
        }
        return false;
    }
    
    /**
     * Teleport player to their current level checkpoint
     * @param player The player
     */
    private void teleportToCurrentLevelCheckpoint(Player player) {
        String worldName = playerCurrentWorld.get(player);
        Integer currentLevel = playerCurrentLevel.get(player);
        
        if (worldName == null || currentLevel == null) {
            player.sendMessage(Component.text("Error: No se pudo encontrar tu checkpoint actual.", NamedTextColor.RED));
            return;
        }
        
        SQLDungeonWorld sqlWorld = getSQLDungeon(worldName);
        if (sqlWorld == null) {
            player.sendMessage(Component.text("Error: Mundo SQL no encontrado.", NamedTextColor.RED));
            return;
        }
        
        SQLLevel level = sqlWorld.getLevel(currentLevel);
        if (level == null) {
            player.sendMessage(Component.text("Error: Nivel no encontrado.", NamedTextColor.RED));
            return;
        }
        
        Location checkpointLocation = level.getCheckpointLocation();
        if (checkpointLocation != null) {
            player.teleport(checkpointLocation);
            player.sendMessage(Component.text("🏁 Has sido teletransportado al checkpoint del nivel " + currentLevel, NamedTextColor.AQUA));
        } else {
            player.sendMessage(Component.text("Error: Checkpoint no encontrado para este nivel.", NamedTextColor.RED));
        }
    }
    
    /**
     * Check if player has any hearts left
     * @param player The player
     * @return true if player has hearts remaining
     */
    private boolean hasHeartsRemaining(Player player) {
        int[] hearts = playerHearts.getOrDefault(player, new int[]{0, 0});
        return hearts[0] > 0 || hearts[1] > 0;
    }

    /**
     * Check if the player currently has an active SQL session
     */
    public boolean isPlayerInSession(Player player) {
        return playerCurrentWorld.containsKey(player) && playerCurrentLevel.containsKey(player);
    }
    
    /**
     * Reset player hearts to full (for new dungeons or special events)
     * @param player The player
     */
    public void resetPlayerHearts(Player player) {
        playerHearts.put(player, new int[]{3, 6});
        updatePlayerHealth(player);
        player.sendMessage(Component.text("💚 Tus corazones han sido restaurados!", NamedTextColor.GREEN));
    }

    /**
     * Give the player the SQL inventory tools: super jump star (slot 8) and exit-to-lobby (slot 9)
     * Clears inventory first.
     * @param player The player
     */
    public void giveSQLInventoryToPlayer(Player player) {
        // Clear inventory
        player.getInventory().clear();

        // Create super jump star (slot index 7 -> position 8)
        org.bukkit.inventory.ItemStack superStar = new org.bukkit.inventory.ItemStack(org.bukkit.Material.NETHER_STAR);
        org.bukkit.inventory.meta.ItemMeta starMeta = superStar.getItemMeta();
        if (starMeta != null) {
            starMeta.displayName(net.kyori.adventure.text.Component.text("Super Salto", net.kyori.adventure.text.format.NamedTextColor.GOLD));
            java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
            int count = playerSuperJumps.getOrDefault(player, 1);
            lore.add(net.kyori.adventure.text.Component.text("Saltos disponibles: " + count, net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            // Convert lore to legacy string for ItemMeta
            java.util.List<String> loreStrings = lore.stream().map(net.kyori.adventure.text.Component::toString).collect(java.util.stream.Collectors.toList());
            starMeta.setLore(loreStrings);
            superStar.setItemMeta(starMeta);
        }

        // Create professor invocation item (slot index 6 -> position 7)
        org.bukkit.inventory.ItemStack professorBook = createProfessorItem(player);
        
        // Create exit-to-lobby item (slot index 8 -> position 9)
        org.bukkit.inventory.ItemStack exitItem = new org.bukkit.inventory.ItemStack(org.bukkit.Material.COMPASS);
        org.bukkit.inventory.meta.ItemMeta exitMeta = exitItem.getItemMeta();
        if (exitMeta != null) {
            exitMeta.displayName(net.kyori.adventure.text.Component.text("Salir al Lobby", net.kyori.adventure.text.format.NamedTextColor.RED));
            exitMeta.setLore(java.util.Arrays.asList("Haz clic derecho para volver al lobby"));
            exitItem.setItemMeta(exitMeta);
        }

        // Place items in hotbar positions 7, 8 and 9 (indices 6, 7 and 8)
        player.getInventory().setItem(6, professorBook);
        player.getInventory().setItem(7, superStar);
        player.getInventory().setItem(8, exitItem);
        
        // Set Adventure mode for SQL Dungeon
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
    }

    /**
     * Reset super jumps for a new level (gives 1 by default)
     */
    public void resetSuperJumpsForLevel(Player player) {
        playerSuperJumps.put(player, 1);
        updateSuperJumpItem(player);
    }

    /**
     * Grant n super jumps to player (adds to current)
     */
    public void grantSuperJumps(Player player, int n) {
        int cur = playerSuperJumps.getOrDefault(player, 0);
        playerSuperJumps.put(player, cur + n);
    }

    /**
     * Use one super jump if available. Returns true if used.
     */
    public boolean useSuperJump(Player player) {
        int cur = playerSuperJumps.getOrDefault(player, 0);
        if (cur <= 0) return false;
        playerSuperJumps.put(player, cur - 1);

        // Perform super jump: boost player upwards by applying velocity
        org.bukkit.util.Vector vel = player.getVelocity();
        // Set an upward velocity suitable for ~5 block jump
        vel.setY(Math.max(1.2, vel.getY() + 1.2));
        player.setVelocity(vel);

        // Play sound and particles
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.0f);
        updateSuperJumpItem(player);
        updateSQLScoreboard(player); // Update scoreboard
        return true;
    }

    /**
     * Update the super jump item lore to reflect current count
     */
    public void updateSuperJumpItem(Player player) {
        org.bukkit.inventory.ItemStack item = player.getInventory().getItem(7);
        if (item == null) return;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        int count = playerSuperJumps.getOrDefault(player, 0);
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("Saltos disponibles: " + count);
        meta.setLore(lore);
        item.setItemMeta(meta);
        player.getInventory().setItem(7, item);
        updateSQLScoreboard(player); // Update scoreboard to reflect new super jump count
    }
    
    /**
     * Create and show SQL scoreboard for player
     * @param player The player
     */
    public void createSQLScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        
        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("sqlstats", "dummy", 
            net.kyori.adventure.text.Component.text("§6§lSQL Dungeon", net.kyori.adventure.text.format.NamedTextColor.GOLD));
        
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        // Add static lines and dynamic content
        updateScoreboardContent(player, scoreboard, objective);
        
        // Set player's scoreboard
        player.setScoreboard(scoreboard);
        playerScoreboards.put(player, scoreboard);
    }
    
    /**
     * Update scoreboard content with current player stats
     * @param player The player
     * @param scoreboard The scoreboard
     * @param objective The objective
     */
    private void updateScoreboardContent(Player player, Scoreboard scoreboard, Objective objective) {
        // Clear existing scores
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }
        
        // Get current level and world info
        String worldName = playerCurrentWorld.get(player);
        Integer currentLevel = playerCurrentLevel.get(player);
        int superJumps = playerSuperJumps.getOrDefault(player, 0);
        int professorInvocations = playerProfessorInvocations.getOrDefault(player, 0);
        int[] hearts = playerHearts.getOrDefault(player, new int[]{3, 6});
        
        // Build scoreboard content (scores go from bottom to top, so we set them backwards)
        int score = 11;
        
        objective.getScore("§7").setScore(score--); // Empty line
        
        // Professor invocations
        objective.getScore("§b🎓 Profesor: §f" + professorInvocations).setScore(score--);
        
        // Super jumps
        objective.getScore("§e⭐ Super Saltos: §f" + superJumps).setScore(score--);
        
        objective.getScore("§6").setScore(score--); // Empty line
        
        // Hearts info
        objective.getScore("§c❤ Corazones Rojos: §f" + hearts[0]).setScore(score--);
        objective.getScore("§e💛 Corazones Amarillos: §f" + hearts[1]).setScore(score--);
        
        objective.getScore("§5").setScore(score--); // Empty line
        
        // Level info
        if (currentLevel != null) {
            objective.getScore("§9📊 Nivel Actual: §f" + currentLevel).setScore(score--);
        }
        
        // World info
        if (worldName != null) {
            String displayWorld = worldName.length() > 12 ? worldName.substring(0, 12) + "..." : worldName;
            objective.getScore("§a🌍 Mundo: §f" + displayWorld).setScore(score--);
        }
        
        objective.getScore("§4").setScore(score--); // Empty line
        
        // Title
        objective.getScore("§6§l=== SQL Stats ===").setScore(score--);
    }
    
    /**
     * Update scoreboard for a player (refresh content)
     * @param player The player
     */
    public void updateSQLScoreboard(Player player) {
        Scoreboard scoreboard = playerScoreboards.get(player);
        if (scoreboard == null) return;
        
        Objective objective = scoreboard.getObjective("sqlstats");
        if (objective == null) return;
        
        updateScoreboardContent(player, scoreboard, objective);
    }
    
    /**
     * Remove SQL scoreboard for player
     * @param player The player
     */
    public void removeSQLScoreboard(Player player) {
        Scoreboard scoreboard = playerScoreboards.remove(player);
        if (scoreboard != null) {
            // Reset to main scoreboard or create empty one
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) {
                player.setScoreboard(manager.getMainScoreboard());
            }
        }
    }

    /**
     * Cleanup and shutdown
     */
    public void shutdown() {
        logger.info("Shutting down SQL Dungeon system...");
        
        // Remove all boss bars
        for (Player player : playerBossBars.keySet()) {
            removeBossBar(player);
        }
        
        // Remove all scoreboards
        for (Player player : playerScoreboards.keySet()) {
            removeSQLScoreboard(player);
        }
        
        // Clear active sessions
        playerCurrentLevel.clear();
        playerStartTime.clear();
        playerCurrentWorld.clear();
        worldPlayerProgress.clear();
        playerBossBars.clear();
        playerFailedAttempts.clear();
        playerSuperJumps.clear();
        playerProfessorInvocations.clear();
        playerHintProgress.clear();
        playerInvocationLevel.clear();
        playerRedHeartsLost.clear();
        playerScoreboards.clear();
        
        // Close database connections
        if (database != null) {
            database.close();
        }
    }
    
    /**
     * Handle red heart loss and potentially grant professor invocation
     * @param player The player who lost a red heart
     */
    private void handleRedHeartLoss(Player player) {
        Integer currentLevel = playerCurrentLevel.get(player);
        if (currentLevel == null) return;
        
        // Track red hearts lost in current level
        int heartsLost = playerRedHeartsLost.getOrDefault(player, 0) + 1;
        playerRedHeartsLost.put(player, heartsLost);
        
        // Check if player has lost 2 red hearts and hasn't gotten invocation for this level yet
        if (heartsLost >= 2) {
            Integer invocationLevel = playerInvocationLevel.get(player);
            
            // Only grant if player hasn't received invocation for this level
            if (invocationLevel == null || !invocationLevel.equals(currentLevel)) {
                grantProfessorInvocation(player, currentLevel);
            }
        }
    }
    
    /**
     * Grant a professor invocation to a player (with limits)
     * @param player The player
     * @param level The level where the invocation was earned
     */
    private void grantProfessorInvocation(Player player, int level) {
        int current = playerProfessorInvocations.getOrDefault(player, 0);
        
        // Check maximum limit (3 invocations)
        if (current >= 3) {
            player.sendMessage(Component.text("🎓 Has alcanzado el máximo de invocaciones de profesor (3)", NamedTextColor.GOLD));
            player.sendMessage(Component.text("Usa algunas antes de poder obtener más", NamedTextColor.YELLOW));
            return;
        }
        
        // Grant the invocation
        playerProfessorInvocations.put(player, current + 1);
        playerInvocationLevel.put(player, level);
        
        player.sendMessage(Component.text("🎓 ¡Has ganado una invocación de profesor!", NamedTextColor.AQUA));
        player.sendMessage(Component.text("Perdiste 2 corazones rojos en este nivel", NamedTextColor.RED));
        player.sendMessage(Component.text("Usa el libro mágico en tu inventario para invocarlo", NamedTextColor.YELLOW));
        
        // Update professor item in inventory
        updateProfessorItem(player);
        
        // Update scoreboard to reflect new invocation count
        updateSQLScoreboard(player);
    }
    
    /**
     * Use a professor invocation (returns true if successful)
     * @param player The player
     * @return true if invocation was used, false if no invocations available
     */
    public boolean useProfessorInvocation(Player player) {
        int current = playerProfessorInvocations.getOrDefault(player, 0);
        if (current <= 0) {
            player.sendMessage(Component.text("❌ No tienes invocaciones de profesor disponibles", NamedTextColor.RED));
            return false;
        }
        
        playerProfessorInvocations.put(player, current - 1);
        updateProfessorItem(player);
        updateSQLScoreboard(player); // Update scoreboard to reflect new invocation count
        return true;
    }
    
    /**
     * Get current professor invocations for a player
     * @param player The player
     * @return Number of invocations available
     */
    public int getProfessorInvocations(Player player) {
        return playerProfessorInvocations.getOrDefault(player, 0);
    }
    
    /**
     * Create professor invocation item
     * @param player The player
     * @return ItemStack of professor book
     */
    private org.bukkit.inventory.ItemStack createProfessorItem(Player player) {
        org.bukkit.inventory.ItemStack professorBook = new org.bukkit.inventory.ItemStack(org.bukkit.Material.ENCHANTED_BOOK);
        org.bukkit.inventory.meta.ItemMeta bookMeta = professorBook.getItemMeta();
        
        if (bookMeta != null) {
            int invocations = playerProfessorInvocations.getOrDefault(player, 0);
            
            bookMeta.displayName(net.kyori.adventure.text.Component.text("🎓 Invocador de Profesor", net.kyori.adventure.text.format.NamedTextColor.AQUA));
            
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("§7Invoca un profesor SQL para recibir pistas");
            lore.add("§7sobre el desafío actual");
            lore.add("");
            lore.add("§6Invocaciones disponibles: §e" + invocations);
            lore.add("");
            if (invocations > 0) {
                lore.add("§a▶ Clic derecho para invocar profesor");
            } else {
                lore.add("§c✗ Sin invocaciones disponibles");
                lore.add("§7¡Pierde un corazón rojo para obtener más!");
            }
            
            bookMeta.setLore(lore);
            professorBook.setItemMeta(bookMeta);
        }
        
        return professorBook;
    }
    
    /**
     * Update professor item in player's inventory
     * @param player The player
     */
    public void updateProfessorItem(Player player) {
        org.bukkit.inventory.ItemStack professorBook = createProfessorItem(player);
        player.getInventory().setItem(6, professorBook);
    }
    
    /**
     * Invoke professor to show hint
     * @param player The player invoking the professor
     */
    public void invokeProfessor(Player player) {
        // Check if player is in SQL session
        if (!isPlayerInSession(player)) {
            player.sendMessage(Component.text("❌ Solo puedes invocar al profesor durante una sesión SQL", NamedTextColor.RED));
            return;
        }
        
        // Check if player has invocations available
        if (!useProfessorInvocation(player)) {
            return;
        }
        
        // Get current challenge
        SQLChallengeBank.Challenge currentChallenge = playerCurrentChallenge.get(player);
        if (currentChallenge == null) {
            player.sendMessage(Component.text("❌ No hay desafío activo para mostrar pistas", NamedTextColor.RED));
            return;
        }
        
        // Get current hint progress for this player
        int hintProgress = playerHintProgress.getOrDefault(player, 0);
        
        // Determine which hint to show (cycle through 1, 2, 3, then repeat)
        String hint;
        int nextHintNumber = (hintProgress % 3) + 1;
        
        switch (nextHintNumber) {
            case 1:
                hint = currentChallenge.getHint1();
                break;
            case 2:
                hint = currentChallenge.getHint2();
                break;
            case 3:
                hint = currentChallenge.getHint3();
                break;
            default:
                hint = currentChallenge.getHint1();
        }
        
        // Update hint progress
        playerHintProgress.put(player, hintProgress + 1);
        
        // Find safe location to spawn professor
        Location professorLocation = findSafeProfessorLocation(player);
        
        // Spawn professor villager
        spawnProfessorVillager(player, professorLocation, hint, nextHintNumber);
        
        player.sendMessage(Component.text("🎓 ¡Profesor invocado! Haz clic en él para recibir tu pista.", NamedTextColor.GREEN));
    }
    
    /**
     * Find a safe location to spawn the professor near the player
     * @param player The player
     * @return Safe location for the professor
     */
    private Location findSafeProfessorLocation(Player player) {
        Location playerLoc = player.getLocation();
        org.bukkit.World world = playerLoc.getWorld();
        
        // Try 2 blocks in front of the player first
        Location frontLoc = playerLoc.clone().add(playerLoc.getDirection().multiply(2));
        
        // Check if the front location is safe
        if (isSafeLocationForProfessor(frontLoc)) {
            return frontLoc;
        }
        
        // Search in a 3x3 grid around the player for a safe spot
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Location testLoc = playerLoc.clone().add(x, 0, z);
                if (isSafeLocationForProfessor(testLoc)) {
                    return testLoc;
                }
            }
        }
        
        // Fallback: spawn at player location + 1 block up
        return playerLoc.clone().add(0, 1, 0);
    }
    
    /**
     * Check if a location is safe for spawning the professor
     * @param location The location to check
     * @return true if safe, false otherwise
     */
    private boolean isSafeLocationForProfessor(Location location) {
        org.bukkit.World world = location.getWorld();
        if (world == null) return false;
        
        // Check if there's a solid block below
        Location belowLoc = location.clone().add(0, -1, 0);
        org.bukkit.Material belowMaterial = world.getBlockAt(belowLoc).getType();
        
        // Check if the spawn location and above are air
        org.bukkit.Material spawnMaterial = world.getBlockAt(location).getType();
        org.bukkit.Material aboveMaterial = world.getBlockAt(location.clone().add(0, 1, 0)).getType();
        
        return belowMaterial.isSolid() && 
               (spawnMaterial == org.bukkit.Material.AIR || !spawnMaterial.isSolid()) &&
               (aboveMaterial == org.bukkit.Material.AIR || !aboveMaterial.isSolid());
    }
    
    /**
     * Spawn professor villager with evolution based on player level
     * @param player The player who invoked the professor
     * @param location The location to spawn the professor
     * @param hint The hint to show
     * @param hintNumber The hint number (1, 2, or 3)
     */
    private void spawnProfessorVillager(Player player, Location location, String hint, int hintNumber) {
        org.bukkit.World world = location.getWorld();
        if (world == null) return;
        
        // Get current level to determine professor evolution
        Integer currentLevel = playerCurrentLevel.get(player);
        if (currentLevel == null) currentLevel = 1;
        
        // Spawn villager with appropriate profession based on level
        org.bukkit.entity.Villager professor = world.spawn(location, org.bukkit.entity.Villager.class);
        
        // Set profession based on level (professor evolution)
        org.bukkit.entity.Villager.Profession profession;
        String professorTitle;
        
        if (currentLevel <= 3) {
            profession = org.bukkit.entity.Villager.Profession.NONE;
            professorTitle = "🎓 Profesor Aprendiz";
        } else if (currentLevel <= 6) {
            profession = org.bukkit.entity.Villager.Profession.LIBRARIAN;
            professorTitle = "📚 Profesor Experto";
        } else if (currentLevel <= 9) {
            profession = org.bukkit.entity.Villager.Profession.CLERIC;
            professorTitle = "⚡ Profesor Maestro";
        } else {
            profession = org.bukkit.entity.Villager.Profession.ARMORER;
            professorTitle = "👑 Gran Maestro SQL";
        }
        
        professor.setProfession(profession);
        professor.setCustomName(professorTitle);
        professor.setCustomNameVisible(true);
        professor.setAI(false); // Make villager stationary
        professor.setSilent(false);
        
        // Add metadata to identify this as a SQL professor
        professor.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(plugin, "sql_professor"), 
            org.bukkit.persistence.PersistentDataType.STRING, 
            player.getUniqueId().toString()
        );
        
        professor.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(plugin, "professor_hint"), 
            org.bukkit.persistence.PersistentDataType.STRING, 
            hint
        );
        
        professor.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(plugin, "hint_number"), 
            org.bukkit.persistence.PersistentDataType.INTEGER, 
            hintNumber
        );
        
        // Spawn particles and play sound effects
        spawnProfessorEffects(location, true);
        
        // Schedule removal after 60 seconds (in case player doesn't interact)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (professor.isValid()) {
                spawnProfessorEffects(professor.getLocation(), false);
                professor.remove();
            }
        }, 1200L); // 60 seconds
    }
    
    /**
     * Handle professor villager interaction to show hint
     * @param player The player who clicked the professor
     * @param professor The professor villager
     */
    public void handleProfessorInteraction(Player player, org.bukkit.entity.Villager professor) {
        // Verify this is a SQL professor
        String ownerUUID = professor.getPersistentDataContainer().get(
            new org.bukkit.NamespacedKey(plugin, "sql_professor"), 
            org.bukkit.persistence.PersistentDataType.STRING
        );
        
        if (ownerUUID == null || !ownerUUID.equals(player.getUniqueId().toString())) {
            player.sendMessage(Component.text("❌ Este profesor no fue invocado por ti", NamedTextColor.RED));
            return;
        }
        
        // Get hint from professor
        String hint = professor.getPersistentDataContainer().get(
            new org.bukkit.NamespacedKey(plugin, "professor_hint"), 
            org.bukkit.persistence.PersistentDataType.STRING
        );
        
        Integer hintNumber = professor.getPersistentDataContainer().get(
            new org.bukkit.NamespacedKey(plugin, "hint_number"), 
            org.bukkit.persistence.PersistentDataType.INTEGER
        );
        
        if (hint == null || hintNumber == null) {
            player.sendMessage(Component.text("❌ Error al obtener la pista del profesor", NamedTextColor.RED));
            return;
        }
        
        // Show hint in temporary hologram
        showHintHologram(player, professor.getLocation(), hint, hintNumber);
        
        // Remove professor with effects
        spawnProfessorEffects(professor.getLocation(), false);
        professor.remove();
    }
    
    /**
     * Spawn visual and sound effects for professor appearance/disappearance
     * @param location The location for effects
     * @param isAppearing true for appearance, false for disappearance
     */
    private void spawnProfessorEffects(Location location, boolean isAppearing) {
        org.bukkit.World world = location.getWorld();
        if (world == null) return;
        
        if (isAppearing) {
            // Appearance effects
            world.spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE, location.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
            world.spawnParticle(org.bukkit.Particle.PORTAL, location.clone().add(0, 0.5, 0), 20, 0.3, 0.3, 0.3, 0.1);
            world.playSound(location, org.bukkit.Sound.ENTITY_VILLAGER_AMBIENT, 1.0f, 1.2f);
            world.playSound(location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
        } else {
            // Disappearance effects
            world.spawnParticle(org.bukkit.Particle.END_ROD, location.clone().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.1);
            world.spawnParticle(org.bukkit.Particle.FIREWORKS_SPARK, location.clone().add(0, 0.5, 0), 15, 0.2, 0.2, 0.2, 0.1);
            world.playSound(location, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.3f);
            world.playSound(location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2.0f);
        }
    }
    
    /**
     * Show hint in a temporary hologram above the professor location
     * @param player The player receiving the hint
     * @param location The location where the professor was
     * @param hint The hint text to display
     * @param hintNumber The hint number (1, 2, or 3)
     */
    private void showHintHologram(Player player, Location location, String hint, int hintNumber) {
        org.bukkit.World world = location.getWorld();
        if (world == null) return;
        
        // Create hologram location (2 blocks above the professor location)
        Location hologramLoc = location.clone().add(0, 2.5, 0);
        
        // Create armor stand as hologram base
        org.bukkit.entity.ArmorStand hologram = world.spawn(hologramLoc, org.bukkit.entity.ArmorStand.class);
        
        // Configure armor stand as invisible hologram
        hologram.setVisible(false);
        hologram.setGravity(false);
        hologram.setCanPickupItems(false);
        hologram.setCustomNameVisible(true);
        hologram.setMarker(true); // Makes it non-collidable
        hologram.setInvulnerable(true);
        
        // Set hint text with formatting
        String formattedHint = String.format("§6💡 Pista %d: §e%s", hintNumber, hint);
        hologram.setCustomName(formattedHint);
        
        // Send personalized message to player
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        player.sendMessage(Component.text("🎓 El profesor te ha dado una pista:", NamedTextColor.AQUA));
        player.sendMessage(Component.text("💡 Pista " + hintNumber + ": " + hint, NamedTextColor.YELLOW));
        player.sendMessage(Component.text("", NamedTextColor.WHITE));
        
        // Play hint sound
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
        
        // Spawn hint particles around the hologram
        spawnHintParticles(hologramLoc);
        
        // Schedule hologram removal after 15 seconds with fade effect
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (hologram.isValid()) {
                // Fade effect before removal
                fadeHologram(hologram, hologramLoc);
            }
        }, 300L); // 15 seconds
    }
    
    /**
     * Spawn particles around the hint hologram
     * @param location The hologram location
     */
    private void spawnHintParticles(Location location) {
        org.bukkit.World world = location.getWorld();
        if (world == null) return;
        
        // Initial burst of particles
        world.spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, location, 20, 0.5, 0.3, 0.5, 0.1);
        world.spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE, location, 15, 0.3, 0.2, 0.3, 0.1);
        
        // Continuous particles for the first few seconds
        for (int i = 1; i <= 5; i++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (world != null) {
                    world.spawnParticle(org.bukkit.Particle.END_ROD, location, 5, 0.2, 0.1, 0.2, 0.05);
                }
            }, i * 20L); // Every second for 5 seconds
        }
    }
    
    /**
     * Fade the hologram with sparkle effects before removal
     * @param hologram The armor stand hologram
     * @param location The hologram location
     */
    private void fadeHologram(org.bukkit.entity.ArmorStand hologram, Location location) {
        org.bukkit.World world = location.getWorld();
        if (world == null) return;
        
        // Fade effect with particles
        world.spawnParticle(org.bukkit.Particle.FIREWORKS_SPARK, location, 30, 0.4, 0.4, 0.4, 0.1);
        world.spawnParticle(org.bukkit.Particle.END_ROD, location, 20, 0.3, 0.3, 0.3, 0.05);
        world.spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE, location, 15, 0.2, 0.2, 0.2, 0.1);
        
        // Play fade sound
        world.playSound(location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 0.8f);
        world.playSound(location, org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.2f);
        
        // Remove hologram after particles
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (hologram.isValid()) {
                hologram.remove();
            }
        }, 20L); // 1 second delay for particles to finish
    }
}