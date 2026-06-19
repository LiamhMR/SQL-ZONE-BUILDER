package com.seminario.plugin.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.seminario.plugin.model.MenuType;
import com.seminario.plugin.model.MenuZone;
import com.seminario.plugin.model.SQLBattleWorld;
import com.seminario.plugin.model.SQLDungeonWorld;

/**
 * Manages the storage and retrieval of menu zones in YAML format
 */
public class ConfigManager {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private final File configFile;
    private final File sqlBattlesFile;
    private final File sqlDungeonsFile;
    private FileConfiguration config;
    private FileConfiguration sqlBattlesConfig;
    private FileConfiguration sqlDungeonsConfig;
    private Map<String, MenuZone> menuZones;
    private final Set<String> pendingMenuZones;
    private Map<String, SQLBattleWorld> sqlBattles;
    private final Set<String> pendingSQLBattles;
    private Map<String, SQLDungeonWorld> sqlDungeons;
    private final Set<String> pendingSQLDungeons;
    
    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configFile = new File(plugin.getDataFolder(), "menuzones.yml");
        this.sqlBattlesFile = new File(plugin.getDataFolder(), "sqlbattle.yml");
        this.sqlDungeonsFile = new File(plugin.getDataFolder(), "sqldungeons.yml");
        this.menuZones = new HashMap<>();
        this.pendingMenuZones = new HashSet<>();
        this.sqlBattles = new HashMap<>();
        this.pendingSQLBattles = new HashSet<>();
        this.sqlDungeons = new HashMap<>();
        this.pendingSQLDungeons = new HashSet<>();
        
        loadConfig();
        loadSQLBattles();
        loadSQLDungeons();
    }
    
    /**
     * Load configuration from file
     */
    private void loadConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
                logger.info("Created new menuzones.yml file");
            } catch (IOException e) {
                logger.severe("Could not create menuzones.yml file: " + e.getMessage());
                return;
            }
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        loadMenuZones();
    }
    
    /**
     * Load all menu zones from configuration
     */
    private void loadMenuZones() {
        menuZones.clear();
        pendingMenuZones.clear();
        
        ConfigurationSection zonesSection = config.getConfigurationSection("menuzones");
        if (zonesSection == null) {
            logger.info("No menu zones found in configuration");
            return;
        }
        
        for (String zoneName : zonesSection.getKeys(false)) {
            ConfigurationSection zoneSection = zonesSection.getConfigurationSection(zoneName);
            if (zoneSection != null) {
                loadSingleMenuZone(zoneName, zoneSection, true);
            }
        }
        
        logger.info("Loaded " + menuZones.size() + " menu zones");
        if (!pendingMenuZones.isEmpty()) {
            logger.warning("Deferred loading for " + pendingMenuZones.size() + " menu zones because their worlds are not loaded yet");
        }
    }

    private void loadSingleMenuZone(String zoneName, ConfigurationSection zoneSection, boolean allowPending) {
        try {
            Map<String, Object> zoneData = new HashMap<>();
            for (String key : zoneSection.getKeys(true)) {
                zoneData.put(key, zoneSection.get(key));
            }
            zoneData.put("name", zoneName);

            MenuZone zone = MenuZone.deserialize(zoneData);
            menuZones.put(zoneName, zone);
            pendingMenuZones.remove(zoneName);
            logger.info("Loaded menu zone: " + zoneName);
        } catch (Exception e) {
            if (allowPending && shouldDeferMenuZoneLoad(e)) {
                pendingMenuZones.add(zoneName);
                logger.warning("Deferred menu zone '" + zoneName + "' until world is loaded: " + e.getMessage());
                return;
            }
            logger.warning("Failed to load menu zone '" + zoneName + "': " + e.getMessage());
        }
    }

    private boolean shouldDeferMenuZoneLoad(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("world") && normalized.contains("not found");
    }

    private void retryPendingMenuZones() {
        if (pendingMenuZones.isEmpty()) {
            return;
        }

        ConfigurationSection zonesSection = config.getConfigurationSection("menuzones");
        if (zonesSection == null) {
            pendingMenuZones.clear();
            return;
        }

        Set<String> pendingCopy = new HashSet<>(pendingMenuZones);
        for (String zoneName : pendingCopy) {
            try {
                ConfigurationSection zoneSection = zonesSection.getConfigurationSection(zoneName);
                if (zoneSection != null) {
                    loadSingleMenuZone(zoneName, zoneSection, false);
                }
            } catch (Exception e) {
                logger.warning("Failed deferred menu zone load for '" + zoneName + "': " + e.getMessage());
            }
        }
    }
    
    /**
     * Save configuration to file
     */
    public void saveConfig() {
        try {
            retryPendingMenuZones();

            ConfigurationSection existingZones = config.getConfigurationSection("menuzones");
            boolean hasExistingZones = existingZones != null && !existingZones.getKeys(false).isEmpty();

            // Safety guard: never wipe persisted zones if memory cache is unexpectedly empty.
            if (menuZones.isEmpty() && pendingMenuZones.isEmpty() && hasExistingZones) {
                logger.warning("Skipping menuzones save to prevent data loss: in-memory cache is empty but file has zones");
                return;
            }

            Map<String, Map<String, Object>> preservedPendingZoneData = new HashMap<>();
            if (existingZones != null) {
                for (String pendingZone : pendingMenuZones) {
                    ConfigurationSection pendingSection = existingZones.getConfigurationSection(pendingZone);
                    if (pendingSection == null) {
                        continue;
                    }

                    Map<String, Object> pendingData = new HashMap<>();
                    for (String key : pendingSection.getKeys(true)) {
                        pendingData.put(key, pendingSection.get(key));
                    }
                    preservedPendingZoneData.put(pendingZone, pendingData);
                }
            }

            // Clear existing zones section
            config.set("menuzones", null);
            
            // Save all menu zones
            for (MenuZone zone : menuZones.values()) {
                String path = "menuzones." + zone.getName();
                Map<String, Object> zoneData = zone.serialize();
                
                for (Map.Entry<String, Object> entry : zoneData.entrySet()) {
                    if (!entry.getKey().equals("name")) { // Don't save name as it's the key
                        config.set(path + "." + entry.getKey(), entry.getValue());
                    }
                }
            }

            // Preserve zones still pending because their worlds are not loaded yet.
            for (Map.Entry<String, Map<String, Object>> pendingEntry : preservedPendingZoneData.entrySet()) {
                String pendingZoneName = pendingEntry.getKey();
                for (Map.Entry<String, Object> dataEntry : pendingEntry.getValue().entrySet()) {
                    config.set("menuzones." + pendingZoneName + "." + dataEntry.getKey(), dataEntry.getValue());
                }
            }
            
            config.save(configFile);
            logger.info("Saved " + menuZones.size() + " menu zones to configuration");
        } catch (IOException e) {
            logger.severe("Could not save menuzones.yml: " + e.getMessage());
        }
    }
    
    /**
     * Add a new menu zone
     * @param zone The menu zone to add
     * @return true if added successfully, false if name already exists
     */
    public boolean addMenuZone(MenuZone zone) {
        if (menuZones.containsKey(zone.getName())) {
            return false;
        }
        
        menuZones.put(zone.getName(), zone);
        saveConfig();
        logger.info("Added new menu zone: " + zone.getName());
        return true;
    }
    
    /**
     * Remove a menu zone
     * @param name The name of the zone to remove
     * @return true if removed successfully, false if not found
     */
    public boolean removeMenuZone(String name) {
        MenuZone removed = menuZones.remove(name);
        if (removed != null) {
            saveConfig();
            logger.info("Removed menu zone: " + name);
            return true;
        }
        return false;
    }
    
    /**
     * Get a menu zone by name
     * @param name The name of the zone
     * @return The menu zone or null if not found
     */
    public MenuZone getMenuZone(String name) {
        retryPendingMenuZones();
        return menuZones.get(name);
    }
    
    /**
     * Get all menu zones
     * @return Map of all menu zones
     */
    public Map<String, MenuZone> getAllMenuZones() {
        retryPendingMenuZones();
        return new HashMap<>(menuZones);
    }
    
    /**
     * Check if a zone name exists
     * @param name The name to check
     * @return true if exists
     */
    public boolean hasMenuZone(String name) {
        retryPendingMenuZones();
        return menuZones.containsKey(name);
    }
    
    /**
     * Update the menu type of an existing zone
     * @param zoneName The name of the zone to update
     * @param menuType The new menu type
     * @return true if updated successfully, false if zone not found
     */
    public boolean updateMenuType(String zoneName, MenuType menuType) {
        MenuZone zone = menuZones.get(zoneName);
        if (zone == null) {
            return false;
        }
        
        zone.setMenuType(menuType);
        saveConfig();
        logger.info("Updated menu type for zone '" + zoneName + "' to: " + 
            (menuType != null ? menuType.getName() : "none"));
        return true;
    }
    
    /**
     * Reload configuration from file
     */
    public void reload() {
        loadConfig();
        loadSQLBattles();
        loadSQLDungeons();
    }

    // ===== SQL BATTLE MANAGEMENT =====

    private void loadSQLBattles() {
        sqlBattles.clear();
        pendingSQLBattles.clear();

        if (!sqlBattlesFile.exists()) {
            try {
                sqlBattlesFile.createNewFile();
                logger.info("Created new sqlbattle.yml file");
            } catch (IOException e) {
                logger.warning("Could not create sqlbattle.yml file: " + e.getMessage());
                return;
            }
        }

        sanitizeLegacySQLBattlesFile();

        try {
            sqlBattlesConfig = YamlConfiguration.loadConfiguration(sqlBattlesFile);
        } catch (Exception e) {
            sqlBattlesConfig = new YamlConfiguration();
            logger.warning("Failed to parse sqlbattle.yml; SQL battles will be skipped this boot: " + e.getMessage());
            return;
        }

        ConfigurationSection battlesSection = sqlBattlesConfig.getConfigurationSection("sqlbattles");
        if (battlesSection == null) {
            logger.info("No SQL battles found in configuration");
            return;
        }

        for (String worldName : battlesSection.getKeys(false)) {
            ConfigurationSection battleSection = battlesSection.getConfigurationSection(worldName);
            if (battleSection != null) {
                loadSingleSQLBattle(worldName, battleSection, true);
            }
        }

        logger.info("Loaded " + sqlBattles.size() + " SQL battles");
        if (!pendingSQLBattles.isEmpty()) {
            logger.warning("Deferred loading for " + pendingSQLBattles.size() + " SQL battles because their worlds are not loaded yet");
        }
    }

    private void sanitizeLegacySQLBattlesFile() {
        try {
            String original = Files.readString(sqlBattlesFile.toPath(), StandardCharsets.UTF_8);
            String sanitized = original
                .replaceAll("(?m)^\\s*==:\\s*org\\.bukkit\\.Location\\s*\\R", "")
                .replaceAll("(?m)^\\s*==:\\s*com\\.seminario\\.plugin\\.model\\.(SQLBattleWorld)\\s*\\R", "");

            if (!original.equals(sanitized)) {
                Files.writeString(sqlBattlesFile.toPath(), sanitized, StandardCharsets.UTF_8);
                logger.warning("Migrated legacy sqlbattle.yml format by removing legacy type markers");
            }
        } catch (IOException e) {
            logger.warning("Could not sanitize sqlbattle.yml before loading: " + e.getMessage());
        }
    }

    private void loadSingleSQLBattle(String worldName, ConfigurationSection battleSection, boolean allowPending) {
        try {
            Map<String, Object> battleData = convertConfigurationSectionToMap(battleSection);
            if (allowPending && containsUnloadedWorldReference(battleData)) {
                pendingSQLBattles.add(worldName);
                logger.warning("Deferred SQL battle '" + worldName + "' until world is loaded");
                return;
            }

            SQLBattleWorld battleWorld = SQLBattleWorld.deserialize(battleData);
            sqlBattles.put(worldName, battleWorld);
            pendingSQLBattles.remove(worldName);
            logger.info("Loaded SQL battle: " + worldName);
        } catch (Exception e) {
            logger.warning("Failed to load SQL battle '" + worldName + "': " + e.getMessage());
        }
    }

    private void retryPendingSQLBattles() {
        if (pendingSQLBattles.isEmpty()) {
            return;
        }

        ConfigurationSection battlesSection = sqlBattlesConfig.getConfigurationSection("sqlbattles");
        if (battlesSection == null) {
            pendingSQLBattles.clear();
            return;
        }

        Set<String> pendingCopy = new HashSet<>(pendingSQLBattles);
        for (String worldName : pendingCopy) {
            ConfigurationSection battleSection = battlesSection.getConfigurationSection(worldName);
            if (battleSection != null) {
                loadSingleSQLBattle(worldName, battleSection, false);
            }
        }
    }

    public void saveSQLBattles() {
        try {
            retryPendingSQLBattles();

            ConfigurationSection existingBattles = sqlBattlesConfig.getConfigurationSection("sqlbattles");
            boolean hasExistingBattles = existingBattles != null && !existingBattles.getKeys(false).isEmpty();

            // Safety guard: avoid wiping persisted SQL battles if runtime cache is unexpectedly empty.
            if (sqlBattles.isEmpty() && pendingSQLBattles.isEmpty() && hasExistingBattles) {
                logger.warning("Skipping sqlbattle save to prevent data loss: in-memory cache is empty but file has battles");
                return;
            }

            Map<String, Map<String, Object>> preservedPendingBattleData = new HashMap<>();
            if (existingBattles != null) {
                for (String pendingBattle : pendingSQLBattles) {
                    ConfigurationSection pendingSection = existingBattles.getConfigurationSection(pendingBattle);
                    if (pendingSection == null) {
                        continue;
                    }

                    Map<String, Object> pendingData = convertConfigurationSectionToMap(pendingSection);
                    preservedPendingBattleData.put(pendingBattle, pendingData);
                }
            }

            sqlBattlesConfig.set("sqlbattles", null);

            for (Map.Entry<String, SQLBattleWorld> entry : sqlBattles.entrySet()) {
                String worldName = entry.getKey();
                SQLBattleWorld battleWorld = entry.getValue();

                Map<String, Object> serializedData = battleWorld.serialize();
                for (Map.Entry<String, Object> dataEntry : serializedData.entrySet()) {
                    sqlBattlesConfig.set("sqlbattles." + worldName + "." + dataEntry.getKey(), dataEntry.getValue());
                }
            }

            // Preserve battles still pending because their worlds are not loaded yet.
            for (Map.Entry<String, Map<String, Object>> pendingEntry : preservedPendingBattleData.entrySet()) {
                String pendingBattleName = pendingEntry.getKey();
                for (Map.Entry<String, Object> dataEntry : pendingEntry.getValue().entrySet()) {
                    sqlBattlesConfig.set("sqlbattles." + pendingBattleName + "." + dataEntry.getKey(), dataEntry.getValue());
                }
            }

            sqlBattlesConfig.save(sqlBattlesFile);
            logger.info("Saved " + sqlBattles.size() + " SQL battles to configuration");
        } catch (IOException e) {
            logger.severe("Could not save sqlbattle.yml: " + e.getMessage());
        }
    }

    public boolean addSQLBattle(SQLBattleWorld battleWorld) {
        if (sqlBattles.containsKey(battleWorld.getWorldName())) {
            return false;
        }

        sqlBattles.put(battleWorld.getWorldName(), battleWorld);
        saveSQLBattles();
        logger.info("Added new SQL battle: " + battleWorld.getWorldName());
        return true;
    }

    public boolean removeSQLBattle(String worldName) {
        if (sqlBattles.remove(worldName) != null) {
            saveSQLBattles();
            logger.info("Removed SQL battle: " + worldName);
            return true;
        }
        return false;
    }

    public SQLBattleWorld getSQLBattle(String worldName) {
        retryPendingSQLBattles();
        return sqlBattles.get(worldName);
    }

    public Map<String, SQLBattleWorld> getAllSQLBattles() {
        retryPendingSQLBattles();
        return new HashMap<>(sqlBattles);
    }

    public boolean isSQLBattle(String worldName) {
        retryPendingSQLBattles();
        return sqlBattles.containsKey(worldName);
    }

    public void updateSQLBattle(SQLBattleWorld battleWorld) {
        sqlBattles.put(battleWorld.getWorldName(), battleWorld);
        saveSQLBattles();
    }
    
    // ===== SQL DUNGEONS MANAGEMENT =====
    
    /**
     * Load SQL dungeons from configuration
     */
    private void loadSQLDungeons() {
        sqlDungeons.clear();
        pendingSQLDungeons.clear();
        
        if (!sqlDungeonsFile.exists()) {
            try {
                sqlDungeonsFile.createNewFile();
                logger.info("Created new sqldungeons.yml file");
            } catch (IOException e) {
                logger.warning("Could not create sqldungeons.yml file: " + e.getMessage());
                return;
            }
        }

        sanitizeLegacySQLDungeonsFile();
        
        try {
            sqlDungeonsConfig = YamlConfiguration.loadConfiguration(sqlDungeonsFile);
        } catch (Exception e) {
            sqlDungeonsConfig = new YamlConfiguration();
            logger.warning("Failed to parse sqldungeons.yml; SQL dungeons will be skipped this boot: " + e.getMessage());
            return;
        }
        
        ConfigurationSection dungeonsSection = sqlDungeonsConfig.getConfigurationSection("sqldungeons");
        if (dungeonsSection == null) {
            logger.info("No SQL dungeons found in configuration");
            return;
        }
        
        for (String worldName : dungeonsSection.getKeys(false)) {
            ConfigurationSection dungeonSection = dungeonsSection.getConfigurationSection(worldName);
            if (dungeonSection != null) {
                loadSingleSQLDungeon(worldName, dungeonSection, true);
            }
        }
        
        logger.info("Loaded " + sqlDungeons.size() + " SQL dungeons");
        if (!pendingSQLDungeons.isEmpty()) {
            logger.warning("Deferred loading for " + pendingSQLDungeons.size() + " SQL dungeons because their worlds are not loaded yet");
        }
    }

    private void sanitizeLegacySQLDungeonsFile() {
        try {
            String original = Files.readString(sqlDungeonsFile.toPath(), StandardCharsets.UTF_8);
            String sanitized = original
                .replaceAll("(?m)^\\s*==:\\s*org\\.bukkit\\.Location\\s*\\R", "")
                .replaceAll("(?m)^\\s*==:\\s*com\\.seminario\\.plugin\\.model\\.(SQLDungeonWorld|SQLLevel)\\s*\\R", "");

            if (!original.equals(sanitized)) {
                Files.writeString(sqlDungeonsFile.toPath(), sanitized, StandardCharsets.UTF_8);
                logger.warning("Migrated legacy sqldungeons.yml format by removing legacy type markers");
            }
        } catch (IOException e) {
            logger.warning("Could not sanitize sqldungeons.yml before loading: " + e.getMessage());
        }
    }

    private void loadSingleSQLDungeon(String worldName, ConfigurationSection dungeonSection, boolean allowPending) {
        try {
            Map<String, Object> dungeonData = convertConfigurationSectionToMap(dungeonSection);
            if (allowPending && containsUnloadedWorldReference(dungeonData)) {
                pendingSQLDungeons.add(worldName);
                logger.warning("Deferred SQL dungeon '" + worldName + "' until world is loaded");
                return;
            }

            SQLDungeonWorld sqlWorld = SQLDungeonWorld.deserialize(dungeonData);
            sqlDungeons.put(worldName, sqlWorld);
            pendingSQLDungeons.remove(worldName);
            logger.info("Loaded SQL dungeon: " + worldName);
        } catch (Exception e) {
            logger.warning("Failed to load SQL dungeon '" + worldName + "': " + e.getMessage());
        }
    }

    private void retryPendingSQLDungeons() {
        if (pendingSQLDungeons.isEmpty()) {
            return;
        }

        ConfigurationSection dungeonsSection = sqlDungeonsConfig.getConfigurationSection("sqldungeons");
        if (dungeonsSection == null) {
            pendingSQLDungeons.clear();
            return;
        }

        Set<String> pendingCopy = new HashSet<>(pendingSQLDungeons);
        for (String worldName : pendingCopy) {
            ConfigurationSection dungeonSection = dungeonsSection.getConfigurationSection(worldName);
            if (dungeonSection != null) {
                loadSingleSQLDungeon(worldName, dungeonSection, false);
            }
        }
    }
    
    /**
     * Save SQL dungeons to configuration
     */
    public void saveSQLDungeons() {
        try {
            retryPendingSQLDungeons();

            ConfigurationSection existingDungeons = sqlDungeonsConfig.getConfigurationSection("sqldungeons");
            boolean hasExistingDungeons = existingDungeons != null && !existingDungeons.getKeys(false).isEmpty();

            // Safety guard: avoid wiping persisted SQL dungeons if runtime cache is unexpectedly empty.
            if (sqlDungeons.isEmpty() && pendingSQLDungeons.isEmpty() && hasExistingDungeons) {
                logger.warning("Skipping sqldungeons save to prevent data loss: in-memory cache is empty but file has dungeons");
                return;
            }

            Map<String, Map<String, Object>> preservedPendingDungeonData = new HashMap<>();
            if (existingDungeons != null) {
                for (String pendingDungeon : pendingSQLDungeons) {
                    ConfigurationSection pendingSection = existingDungeons.getConfigurationSection(pendingDungeon);
                    if (pendingSection == null) {
                        continue;
                    }

                    Map<String, Object> pendingData = convertConfigurationSectionToMap(pendingSection);
                    preservedPendingDungeonData.put(pendingDungeon, pendingData);
                }
            }

            // Clear existing dungeons section
            sqlDungeonsConfig.set("sqldungeons", null);
            
            // Save all SQL dungeons
            for (Map.Entry<String, SQLDungeonWorld> entry : sqlDungeons.entrySet()) {
                String worldName = entry.getKey();
                SQLDungeonWorld sqlWorld = entry.getValue();
                
                Map<String, Object> serializedData = sqlWorld.serialize();
                for (Map.Entry<String, Object> dataEntry : serializedData.entrySet()) {
                    sqlDungeonsConfig.set("sqldungeons." + worldName + "." + dataEntry.getKey(), dataEntry.getValue());
                }
            }

            // Preserve dungeons still pending because their worlds are not loaded yet.
            for (Map.Entry<String, Map<String, Object>> pendingEntry : preservedPendingDungeonData.entrySet()) {
                String pendingDungeonName = pendingEntry.getKey();
                for (Map.Entry<String, Object> dataEntry : pendingEntry.getValue().entrySet()) {
                    sqlDungeonsConfig.set("sqldungeons." + pendingDungeonName + "." + dataEntry.getKey(), dataEntry.getValue());
                }
            }
            
            sqlDungeonsConfig.save(sqlDungeonsFile);
            logger.info("Saved " + sqlDungeons.size() + " SQL dungeons to configuration");
        } catch (IOException e) {
            logger.severe("Could not save sqldungeons.yml: " + e.getMessage());
        }
    }
    
    /**
     * Add a new SQL dungeon
     * @param sqlWorld The SQL dungeon world to add
     * @return true if added successfully
     */
    public boolean addSQLDungeon(SQLDungeonWorld sqlWorld) {
        if (sqlDungeons.containsKey(sqlWorld.getWorldName())) {
            return false;
        }
        
        sqlDungeons.put(sqlWorld.getWorldName(), sqlWorld);
        saveSQLDungeons();
        logger.info("Added new SQL dungeon: " + sqlWorld.getWorldName());
        return true;
    }
    
    /**
     * Remove a SQL dungeon
     * @param worldName The world name
     * @return true if removed successfully
     */
    public boolean removeSQLDungeon(String worldName) {
        if (sqlDungeons.remove(worldName) != null) {
            saveSQLDungeons();
            logger.info("Removed SQL dungeon: " + worldName);
            return true;
        }
        return false;
    }
    
    /**
     * Get a SQL dungeon by world name
     * @param worldName The world name
     * @return SQLDungeonWorld or null if not found
     */
    public SQLDungeonWorld getSQLDungeon(String worldName) {
        retryPendingSQLDungeons();
        return sqlDungeons.get(worldName);
    }
    
    /**
     * Get all SQL dungeons
     * @return Map of world names to SQL dungeon worlds
     */
    public Map<String, SQLDungeonWorld> getAllSQLDungeons() {
        retryPendingSQLDungeons();
        return new HashMap<>(sqlDungeons);
    }
    
    /**
     * Check if a world is a SQL dungeon
     * @param worldName The world name
     * @return true if it's a SQL dungeon
     */
    public boolean isSQLDungeon(String worldName) {
        retryPendingSQLDungeons();
        return sqlDungeons.containsKey(worldName);
    }
    
    /**
     * Update a SQL dungeon (save changes)
     * @param sqlWorld The updated SQL dungeon world  
     */
    public void updateSQLDungeon(SQLDungeonWorld sqlWorld) {
        sqlDungeons.put(sqlWorld.getWorldName(), sqlWorld);
        saveSQLDungeons();
    }
    
    /**
     * Convert a ConfigurationSection to a Map, handling nested sections properly
     * @param section The ConfigurationSection to convert
     * @return Map representation of the section
     */
    private Map<String, Object> convertConfigurationSectionToMap(ConfigurationSection section) {
        Map<String, Object> map = new HashMap<>();
        
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            
            if (value instanceof ConfigurationSection) {
                // Recursively convert nested sections
                map.put(key, convertConfigurationSectionToMap((ConfigurationSection) value));
            } else {
                map.put(key, value);
            }
        }
        
        return map;
    }

    private boolean containsUnloadedWorldReference(Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if ("world".equalsIgnoreCase(key) && value instanceof String) {
                String worldName = (String) value;
                if (!worldName.isEmpty() && Bukkit.getWorld(worldName) == null) {
                    return true;
                }
            }

            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                if (containsUnloadedWorldReference(nestedMap)) {
                    return true;
                }
            }
        }

        return false;
    }

    public int getSQLBattleGlobalPoints(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        if (sqlBattlesConfig == null) {
            loadSQLBattles();
        }
        return sqlBattlesConfig.getInt("globalPoints." + playerId.toString(), 0);
    }

    public int addSQLBattleGlobalPoints(UUID playerId, int pointsToAdd) {
        if (playerId == null) {
            return 0;
        }
        if (pointsToAdd == 0) {
            return getSQLBattleGlobalPoints(playerId);
        }
        if (sqlBattlesConfig == null) {
            loadSQLBattles();
        }

        int current = sqlBattlesConfig.getInt("globalPoints." + playerId.toString(), 0);
        int updated = Math.max(0, current + pointsToAdd);
        sqlBattlesConfig.set("globalPoints." + playerId.toString(), updated);
        saveSQLBattles();
        return updated;
    }

    public Map<UUID, Integer> getSQLBattleGlobalPointsMap() {
        Map<UUID, Integer> result = new HashMap<>();
        if (sqlBattlesConfig == null) {
            loadSQLBattles();
        }

        ConfigurationSection pointsSection = sqlBattlesConfig.getConfigurationSection("globalPoints");
        if (pointsSection == null) {
            return result;
        }

        for (String rawKey : pointsSection.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(rawKey);
                int points = pointsSection.getInt(rawKey, 0);
                result.put(playerId, points);
            } catch (IllegalArgumentException ignored) {
            }
        }

        return result;
    }
    
    /**
     * Set the server spawnpoint
     * @param location The location to set as spawnpoint
     */
    public void setServerSpawnpoint(Location location) {
        if (location == null || location.getWorld() == null) {
            logger.warning("Attempted to set server spawnpoint with null location or world");
            return;
        }
        
        try {
            // Save spawnpoint data
            config.set("server.spawnpoint.world", location.getWorld().getName());
            config.set("server.spawnpoint.x", location.getX());
            config.set("server.spawnpoint.y", location.getY());
            config.set("server.spawnpoint.z", location.getZ());
            config.set("server.spawnpoint.yaw", location.getYaw());
            config.set("server.spawnpoint.pitch", location.getPitch());
            
            config.save(configFile);
            logger.info("Server spawnpoint saved to configuration");
        } catch (IOException e) {
            logger.severe("Could not save server spawnpoint: " + e.getMessage());
        }
    }
    
    /**
     * Get the server spawnpoint
     * @return The server spawnpoint location, or null if not set or world not found
     */
    public Location getServerSpawnpoint() {
        if (!config.contains("server.spawnpoint")) {
            return null;
        }
        
        try {
            String worldName = config.getString("server.spawnpoint.world");
            double x = config.getDouble("server.spawnpoint.x");
            double y = config.getDouble("server.spawnpoint.y");
            double z = config.getDouble("server.spawnpoint.z");
            float yaw = (float) config.getDouble("server.spawnpoint.yaw");
            float pitch = (float) config.getDouble("server.spawnpoint.pitch");
            
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                logger.warning("Server spawnpoint world '" + worldName + "' not found");
                return null;
            }
            
            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception e) {
            logger.warning("Error loading server spawnpoint: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if server spawnpoint is set
     * @return true if spawnpoint is configured
     */
    public boolean hasServerSpawnpoint() {
        return config.contains("server.spawnpoint.world");
    }

    // ===== WORLD PLAYER LIMITS =====

    /**
     * Set the maximum number of players allowed in a world.
     * A value <= 0 removes the limit.
     */
    public void setWorldPlayerLimit(String worldName, int limit) {
        if (limit <= 0) {
            config.set("worldlimits." + worldName, null);
            logger.info("Removed player limit for world '" + worldName + "'.");
        } else {
            config.set("worldlimits." + worldName, limit);
            logger.info("Set player limit for world '" + worldName + "' to " + limit + ".");
        }
        try {
            config.save(configFile);
        } catch (IOException e) {
            logger.severe("Could not save world player limit: " + e.getMessage());
        }
    }

    /**
     * Get the maximum number of players allowed in a world.
     * @return the limit, or -1 if no limit is configured
     */
    public int getWorldPlayerLimit(String worldName) {
        if (!config.contains("worldlimits." + worldName)) return -1;
        return config.getInt("worldlimits." + worldName, -1);
    }

    /**
     * Check whether a world has a player limit configured.
     */
    public boolean hasWorldPlayerLimit(String worldName) {
        return getWorldPlayerLimit(worldName) > 0;
    }

    // ===== COUNT HOLOGRAMS =====

    /**
     * Immutable data bag for a count hologram entry.
     */
    public static class CountHologramData {
        /** World whose player count is displayed on the hologram. */
        public final String worldName;
        /** World where the ArmorStand entity is physically spawned. */
        public final String spawnWorld;
        public final double x;
        public final double y;
        public final double z;

        public CountHologramData(String worldName, String spawnWorld, double x, double y, double z) {
            this.worldName = worldName;
            this.spawnWorld = spawnWorld;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /**
     * Return all count holograms stored in config.
     */
    public Map<String, CountHologramData> getAllCountHolograms() {
        Map<String, CountHologramData> result = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("countholograms");
        if (section == null) return result;

        for (String name : section.getKeys(false)) {
            String world = section.getString(name + ".world");
            // spawn_world added in v2; fall back to world for old entries
            String spawnWorld = section.getString(name + ".spawn_world", world);
            double x = section.getDouble(name + ".x");
            double y = section.getDouble(name + ".y");
            double z = section.getDouble(name + ".z");
            if (world != null) {
                result.put(name, new CountHologramData(world, spawnWorld, x, y, z));
            }
        }
        return result;
    }

    /**
     * Persist a new count hologram entry.
     * @param trackWorldName  world whose player count will be shown
     * @param location        where the ArmorStand will be placed (world + coords)
     */
    public void addCountHologram(String name, String trackWorldName, Location location) {
        String path = "countholograms." + name;
        config.set(path + ".world", trackWorldName);
        config.set(path + ".spawn_world", location.getWorld().getName());
        config.set(path + ".x", location.getX());
        config.set(path + ".y", location.getY());
        config.set(path + ".z", location.getZ());
        try {
            config.save(configFile);
            logger.info("Saved count hologram '" + name + "' tracking world '" + trackWorldName + "' at " + location.getWorld().getName() + ".");
        } catch (IOException e) {
            logger.severe("Could not save count hologram '" + name + "': " + e.getMessage());
        }
    }

    /**
     * Remove a count hologram entry from config.
     * @return true if it existed
     */
    public boolean removeCountHologram(String name) {
        if (!config.contains("countholograms." + name)) return false;
        config.set("countholograms." + name, null);
        try {
            config.save(configFile);
            logger.info("Removed count hologram '" + name + "'.");
        } catch (IOException e) {
            logger.severe("Could not save after removing count hologram: " + e.getMessage());
        }
        return true;
    }
}