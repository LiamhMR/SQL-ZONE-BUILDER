package com.seminario.plugin;

import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.java.JavaPlugin;

import com.seminario.plugin.commands.AuthCommand;
import com.seminario.plugin.commands.FullNameCommand;
import com.seminario.plugin.commands.SeminarioCommand;
import com.seminario.plugin.config.ConfigManager;
import com.seminario.plugin.listener.AuthListener;
import com.seminario.plugin.listener.ChestportGUIListener;
import com.seminario.plugin.listener.FireworkTriggerListener;
import com.seminario.plugin.listener.HarryNPCListener;
import com.seminario.plugin.listener.LobbyPlayerListener;
import com.seminario.plugin.listener.PlayerEventListener;
import com.seminario.plugin.listener.QuestListener;
import com.seminario.plugin.listener.SQLBattlePreparationListener;
import com.seminario.plugin.listener.SQLBattleWaveListener;
import com.seminario.plugin.listener.SQLEntryListener;
import com.seminario.plugin.manager.AuthManager;
import com.seminario.plugin.manager.CountHologramManager;
import com.seminario.plugin.manager.FireworkManager;
import com.seminario.plugin.manager.FixSlideManager;
import com.seminario.plugin.manager.HarryNPCManager;
import com.seminario.plugin.manager.LobbyManager;
import com.seminario.plugin.manager.QuestManager;
import com.seminario.plugin.manager.SQLBattleManager;
import com.seminario.plugin.manager.SQLDungeonManager;
import com.seminario.plugin.manager.SlideManager;
import com.seminario.plugin.manager.SlideShowManager;
import com.seminario.plugin.manager.SpawnpointManager;
import com.seminario.plugin.manager.SurveyManager;
import com.seminario.plugin.manager.TutorialSQLPresentationManager;
import com.seminario.plugin.model.MenuZone;
import com.seminario.plugin.model.SQLBattleWorld;
import com.seminario.plugin.model.SQLDungeonWorld;
import com.seminario.plugin.model.SQLLevel;
import com.seminario.plugin.model.Slide;
import com.seminario.plugin.model.Survey;

/**
 * Main plugin class for the Seminario Plugin
 * Minecraft 1.20.1 Paper/Bukkit Plugin with WorldEdit integration
 */
public class App extends JavaPlugin {
    
    private ConfigManager configManager;
    private SlideManager slideManager;
    private SlideShowManager slideShowManager;
    private FixSlideManager fixSlideManager;
    private SQLBattleManager sqlBattleManager;
    private SQLDungeonManager sqlDungeonManager;
    private SpawnpointManager spawnpointManager;
    private LobbyManager lobbyManager;
    private SurveyManager surveyManager;
    private QuestManager questManager;
    private FireworkManager fireworkManager;
    private AuthManager authManager;
    private HarryNPCManager harryNPCManager;
    private CountHologramManager countHologramManager;
    private TutorialSQLPresentationManager tutorialSQLPresentationManager;
    private PlayerEventListener playerEventListener;
    private com.seminario.plugin.listener.PlayerJoinListener playerJoinListener;
    
    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("Seminario Plugin has been enabled!");
        
        // Check for WorldEdit dependency
        if (getServer().getPluginManager().getPlugin("WorldEdit") == null) {
            getLogger().warning("WorldEdit not found! Some features may not work properly.");
        } else {
            getLogger().info("WorldEdit integration enabled!");
        }
        
        // Register serializable classes for YAML storage
        ConfigurationSerialization.registerClass(MenuZone.class);
        ConfigurationSerialization.registerClass(Slide.class);
        ConfigurationSerialization.registerClass(SQLBattleWorld.class);
        ConfigurationSerialization.registerClass(SQLDungeonWorld.class);
        ConfigurationSerialization.registerClass(SQLLevel.class);
        ConfigurationSerialization.registerClass(Survey.class);
        
        // Initialize managers
        configManager = new ConfigManager(this);
        slideManager = new SlideManager(this);
        slideShowManager = new SlideShowManager(this, configManager, slideManager);
        fixSlideManager = new FixSlideManager(this, configManager, slideManager);
        sqlBattleManager = new SQLBattleManager(this, configManager);
        sqlDungeonManager = new SQLDungeonManager(this, configManager);
        spawnpointManager = new SpawnpointManager(this, configManager);
        surveyManager = new SurveyManager(this);
        questManager = new QuestManager(this);
        lobbyManager = new LobbyManager(this, configManager, spawnpointManager, surveyManager);
        lobbyManager.setSQLBattleManager(sqlBattleManager);
        lobbyManager.setQuestManager(questManager);
        fireworkManager = new FireworkManager(this);
        authManager = new AuthManager(this);
        harryNPCManager = new HarryNPCManager(this);
        countHologramManager = new CountHologramManager(this, configManager);
        tutorialSQLPresentationManager = new TutorialSQLPresentationManager(this);
        
        // Clear cached slides with old format (12x9) to force regeneration to new format (16x11)
        getLogger().info("Clearing slide cache to force regeneration for 16x11 format...");
        slideManager.clearAllSlideCache();
        getLogger().info("Slide cache cleared. Slides will regenerate in new 16x11 format on first view.");
        
        // Initialize FIXSLIDE presentations
        getLogger().info("Initializing FIXSLIDE presentations...");
        fixSlideManager.initializeAllFixSlides();
        
        // Initialize SQL Dungeon system
        if (!sqlDungeonManager.initialize()) {
            getLogger().warning("SQL Dungeon system failed to initialize - some features may not work");
        }
        
        // Spawn all Harry NPCs after worlds are loaded
        getLogger().info("Spawning Harry NPCs...");
        harryNPCManager.spawnAllNPCs();

        // Initialize count holograms (must be after worlds/entities are loaded)
        com.seminario.plugin.gui.ChestportGUI.setConfigManager(configManager);
        countHologramManager.initializeAll();
        
        // Register event listeners
        playerEventListener = new PlayerEventListener(configManager, slideShowManager, fixSlideManager, this);
        getServer().getPluginManager().registerEvents(playerEventListener, this);
        
        // Register chestport GUI listener
        getServer().getPluginManager().registerEvents(new ChestportGUIListener(), this);
        
        // Register SQL entry listener
        getServer().getPluginManager().registerEvents(new SQLEntryListener(sqlDungeonManager), this);

        // Register SQL Battle preparation listener for chat-based query gameplay
        getServer().getPluginManager().registerEvents(new SQLBattlePreparationListener(sqlBattleManager, this), this);

        // Register SQL Battle wave listener for stage progression and wave completion
        getServer().getPluginManager().registerEvents(new SQLBattleWaveListener(sqlBattleManager, this), this);

        // Register SQL Battle inventory restriction listener
        getServer().getPluginManager().registerEvents(
            new com.seminario.plugin.listener.BattleInventoryListener(sqlBattleManager), this);
        
        // Register SQL world listener for auto-start functionality
        getServer().getPluginManager().registerEvents(new com.seminario.plugin.listeners.SQLWorldListener(sqlDungeonManager), this);
        
        // Register SQL death listener for checkpoint respawn and heart loss on death
        getServer().getPluginManager().registerEvents(new com.seminario.plugin.listener.SQLDeathListener(sqlDungeonManager, this), this);

        // Register SQL player listener for SuperJump and exit item handling
        getServer().getPluginManager().registerEvents(new com.seminario.plugin.listener.SQLPlayerListener(sqlDungeonManager, spawnpointManager), this);
        
        // Register laboratory listener for SQL experimentation
        com.seminario.plugin.listener.LaboratoryListener laboratoryListener = new com.seminario.plugin.listener.LaboratoryListener(configManager, sqlDungeonManager);

        // Initialize persistent SQLBattle lab database for LABORATORY2 zones
        com.seminario.plugin.sql.battle.BattleSQLDatabase battleLabDatabase =
                new com.seminario.plugin.sql.battle.BattleSQLDatabase(getLogger(), "laboratory2_lab");
        if (battleLabDatabase.initialize()) {
            try {
                battleLabDatabase.loadWave(1);
                laboratoryListener.setBattleLabDatabase(battleLabDatabase);
                getLogger().info("[SeminarioPlugin] Battle Lab database inicializada para zonas LABORATORY2");
            } catch (Exception e) {
                getLogger().warning("[SeminarioPlugin] No se pudo cargar la oleada del Battle Lab: " + e.getMessage());
            }
        } else {
            getLogger().warning("[SeminarioPlugin] No se pudo inicializar la base de datos del Battle Lab (LABORATORY2 sin datos)");
        }

        getServer().getPluginManager().registerEvents(laboratoryListener, this);
        
        // Connect laboratory listener with player event listener
        playerEventListener.setLaboratoryListener(laboratoryListener);
        
        // Register player join listener for spawnpoint welcome (always teleports to spawn)
        playerJoinListener = new com.seminario.plugin.listener.PlayerJoinListener(spawnpointManager, lobbyManager, harryNPCManager, authManager, this);
        getServer().getPluginManager().registerEvents(playerJoinListener, this);
        
        // Register world change listener for lobby inventory on world switch
        getServer().getPluginManager().registerEvents(new com.seminario.plugin.listener.WorldChangeListener(this, lobbyManager), this);
        
        // Register lobby player listener for lobby item interactions
        getServer().getPluginManager().registerEvents(new LobbyPlayerListener(lobbyManager, spawnpointManager, tutorialSQLPresentationManager, this), this);
        
        // Register survey listener for survey interactions
        getServer().getPluginManager().registerEvents(new com.seminario.plugin.listener.SurveyListener(surveyManager), this);

        // Register quest listener for inventory questionnaires
        getServer().getPluginManager().registerEvents(new QuestListener(questManager), this);
        
        // Register post-test listener for post-test item interactions
        getServer().getPluginManager().registerEvents(new com.seminario.plugin.listener.PostTestListener(surveyManager), this);
        
        // Register firework trigger listener for firework activation
        getServer().getPluginManager().registerEvents(new FireworkTriggerListener(fireworkManager), this);
        
        // Register Harry NPC listener for NPC interactions
        getServer().getPluginManager().registerEvents(new HarryNPCListener(harryNPCManager), this);

        // Register authentication listener
        getServer().getPluginManager().registerEvents(new AuthListener(authManager), this);
        
        // Register commands
        SeminarioCommand seminarioCommand = new SeminarioCommand(configManager, slideManager, sqlDungeonManager, sqlBattleManager, spawnpointManager, lobbyManager, surveyManager, questManager, fireworkManager, harryNPCManager, authManager);
        seminarioCommand.setFixSlideManager(fixSlideManager); // Connect FixSlideManager to commands
        seminarioCommand.setCountHologramManager(countHologramManager);
        var smCommand = getCommand("sm");
        if (smCommand != null) {
            smCommand.setExecutor(seminarioCommand);
            smCommand.setTabCompleter(seminarioCommand);
        } else {
            getLogger().severe("Failed to register /sm command!");
        }

        // Inject QuestManager into ChestportGUI for requirement validation
        com.seminario.plugin.gui.ChestportGUI.setQuestManager(questManager);

        // Wire PlayerEventListener with CountHologramManager for live hologram updates
        playerEventListener.setCountHologramManager(countHologramManager);
        AuthCommand authCommand = new AuthCommand(authManager, spawnpointManager, lobbyManager, this);
        var registerCommand = getCommand("register");
        if (registerCommand != null) {
            registerCommand.setExecutor(authCommand);
            registerCommand.setTabCompleter(authCommand);
        } else {
            getLogger().severe("Failed to register /register command!");
        }

        var loginCommand = getCommand("login");
        if (loginCommand != null) {
            loginCommand.setExecutor(authCommand);
            loginCommand.setTabCompleter(authCommand);
        } else {
            getLogger().severe("Failed to register /login command!");
        }

        // Register name mapping commands: /pucvname, /impucv, /pucvlist
        FullNameCommand fullNameCommand = new FullNameCommand(this);
        var pucvnameCmd = getCommand("pucvname");
        if (pucvnameCmd != null) {
            pucvnameCmd.setExecutor(fullNameCommand);
        } else {
            getLogger().severe("Failed to register /pucvname command!");
        }

        var impucvCmd = getCommand("impucv");
        if (impucvCmd != null) {
            impucvCmd.setExecutor(fullNameCommand);
        } else {
            getLogger().severe("Failed to register /impucv command!");
        }

        var pucvlistCmd = getCommand("pucvlist");
        if (pucvlistCmd != null) {
            pucvlistCmd.setExecutor(fullNameCommand);
        } else {
            getLogger().severe("Failed to register /pucvlist command!");
        }

        
        getLogger().info("Menu zones and slideshow system initialized successfully!");
    }
    
    public SpawnpointManager getSpawnpointManager() {
        return spawnpointManager;
    }
    
    public LobbyManager getLobbyManager() {
        return lobbyManager;
    }
    
    public SurveyManager getSurveyManager() {
        return surveyManager;
    }
    
    public FixSlideManager getFixSlideManager() {
        return fixSlideManager;
    }
    
    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("Seminario Plugin has been disabled!");
        
        // Despawn all Harry NPCs before shutdown
        if (harryNPCManager != null) {
            getLogger().info("Despawning Harry NPCs...");
            harryNPCManager.despawnAllNPCs();
        }
        
        // Save configuration before shutdown
        if (configManager != null) {
            configManager.saveConfig();
        }
        
        if (slideManager != null) {
            slideManager.saveSlides();
        }
        
        // Clean up active slideshow sessions and screens
        if (slideShowManager != null) {
            getLogger().info("Cleaned up " + slideShowManager.getActiveSessionCount() + " active slideshow sessions");
        }
        
        // Clean up FIXSLIDE presentations
        if (fixSlideManager != null) {
            fixSlideManager.cleanupAll();
            getLogger().info("Cleaned up " + fixSlideManager.getActiveFixSlideCount() + " FIXSLIDE presentations");
        }
        
        // Clean up SQL Dungeon system
        if (sqlDungeonManager != null) {
            sqlDungeonManager.shutdown();
        }

        if (sqlBattleManager != null) {
            sqlBattleManager.shutdown();
        }
        
        // Clean up Lobby system
        if (lobbyManager != null) {
            lobbyManager.shutdown();
        }
        
        // Clean up Survey system
        if (surveyManager != null) {
            surveyManager.shutdown();
        }

        if (questManager != null) {
            questManager.shutdown();
        }
        
        // Clean up all slide screens
        com.seminario.plugin.util.SlideScreenRenderer.cleanupAllScreens();
    }
    
    /**
     * Get the configuration manager instance
     * @return ConfigManager instance
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    /**
     * Get the slide manager instance
     * @return SlideManager instance
     */
    public SlideManager getSlideManager() {
        return slideManager;
    }

    public SQLBattleManager getSQLBattleManager() {
        return sqlBattleManager;
    }
    
    /**
     * Get the slideshow manager instance
     * @return SlideShowManager instance
     */
    public SlideShowManager getSlideShowManager() {
        return slideShowManager;
    }
    
    /**
     * Get the player event listener instance
     * @return PlayerEventListener instance
     */
    public PlayerEventListener getPlayerEventListener() {
        return playerEventListener;
    }

    public CountHologramManager getCountHologramManager() {
        return countHologramManager;
    }
}
