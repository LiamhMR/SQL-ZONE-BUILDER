package com.seminario.plugin.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import com.seminario.plugin.config.ConfigManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Manages persistent count holograms that display the current/max player count
 * for a given world. Holograms are ArmorStand entities stored in menuzones.yml
 * and respawned on every plugin startup.
 */
public class CountHologramManager {

    /** PersistentDataContainer key used to identify hologram ArmorStands. */
    public static final NamespacedKey HOLOGRAM_KEY = new NamespacedKey("seminario", "counthologram_name");

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Logger logger;

    /** In-memory map: hologram name -> live ArmorStand UUID */
    private final Map<String, UUID> activeEntities = new HashMap<>();

    public CountHologramManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.logger = plugin.getLogger();
    }

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────

    /**
     * Remove leftover hologram ArmorStands from previous sessions and
     * respawn all configured holograms for currently-loaded worlds.
     * Call this after plugin enable.
     */
    public void initializeAll() {
        // Kill any surviving hologram stands from the previous session
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (entity.getPersistentDataContainer().has(HOLOGRAM_KEY, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }

        // Respawn from config
        int spawned = 0;
        for (Map.Entry<String, ConfigManager.CountHologramData> entry : configManager.getAllCountHolograms().entrySet()) {
            if (spawnFromData(entry.getKey(), entry.getValue())) spawned++;
        }
        logger.info("Count holograms initialized: " + spawned + "/" + configManager.getAllCountHolograms().size());
    }

    // ─────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────

    private boolean spawnFromData(String name, ConfigManager.CountHologramData data) {
        World world = Bukkit.getWorld(data.spawnWorld);
        if (world == null) {
            logger.warning("CountHologram '" + name + "': spawn world '" + data.spawnWorld + "' is not loaded. Will retry on next refresh.");
            return false;
        }

        // Kill any tracked entity for this name (UUID may be stale but entity may still exist)
        UUID oldUUID = activeEntities.remove(name);
        if (oldUUID != null) {
            Entity old = Bukkit.getEntity(oldUUID);
            if (old != null) old.remove();
        }

        // Kill any surviving ArmorStand tagged with this name across ALL worlds (defensive cleanup)
        for (World w : Bukkit.getWorlds()) {
            for (Entity entity : w.getEntitiesByClass(ArmorStand.class)) {
                String tag = entity.getPersistentDataContainer().get(HOLOGRAM_KEY, PersistentDataType.STRING);
                if (name.equals(tag)) {
                    entity.remove();
                }
            }
        }

        Location loc = new Location(world, data.x, data.y, data.z);

        ArmorStand stand = world.spawn(loc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setCustomNameVisible(true);
            as.setInvulnerable(true);
            as.setCollidable(false);
            as.getPersistentDataContainer().set(HOLOGRAM_KEY, PersistentDataType.STRING, name);
        });

        activeEntities.put(name, stand.getUniqueId());
        updateStandText(stand, data.worldName);
        logger.info("Count hologram '" + name + "' spawned for world '" + data.worldName + "'.");
        return true;
    }

    private void updateStandText(ArmorStand stand, String worldName) {
        int max = configManager.getWorldPlayerLimit(worldName);
        World world = Bukkit.getWorld(worldName);
        int current = (world != null) ? world.getPlayers().size() : 0;
        stand.customName(buildText(current, max));
    }

    private Component buildText(int current, int max) {
        Component curr = Component.text(current)
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false);
        Component sep = Component.text("/")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
        Component maxComp = (max <= 0)
                ? Component.text("∞").color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)
                : Component.text(max).color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false);

        return Component.text("👥 ")
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false)
                .append(curr).append(sep).append(maxComp);
    }

    private ArmorStand findStand(UUID id) {
        if (id == null) return null;
        Entity e = Bukkit.getEntity(id);
        if (e instanceof ArmorStand as && !as.isDead()) return as;
        return null;
    }

    // ─────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────

    /**
     * Refresh the display text of all holograms that track the given world.
     * Also attempts to (re)spawn holograms whose spawn world matches (not yet loaded at startup).
     */
    public void refreshWorld(String worldName) {
        for (Map.Entry<String, ConfigManager.CountHologramData> entry : configManager.getAllCountHolograms().entrySet()) {
            ConfigManager.CountHologramData data = entry.getValue();
            // Only update holograms that COUNT players in this world
            if (!data.worldName.equalsIgnoreCase(worldName)) continue;

            String name = entry.getKey();
            ArmorStand stand = findStand(activeEntities.get(name));
            if (stand == null) {
                // Try to (re)spawn if spawn world is now loaded
                spawnFromData(name, data);
            } else {
                updateStandText(stand, data.worldName);
            }
        }
    }

    /**
     * Create a new count hologram, persist it and spawn it immediately.
     *
     * @return true if created; false if name already exists
     */
    public boolean createHologram(String name, String worldName, Location location) {
        if (configManager.getAllCountHolograms().containsKey(name)) return false;

        configManager.addCountHologram(name, worldName, location);
        ConfigManager.CountHologramData data = configManager.getAllCountHolograms().get(name);
        spawnFromData(name, data);
        return true;
    }

    /**
     * Remove a hologram by name (kills entity + removes from config).
     *
     * @return true if it existed and was removed
     */
    public boolean removeHologram(String name) {
        ArmorStand stand = findStand(activeEntities.remove(name));
        if (stand != null) stand.remove();
        return configManager.removeCountHologram(name);
    }

    /**
     * Move an existing hologram to a new location.
     * Updates config and teleports (or respawns) the entity.
     */
    public void moveHologram(String name, Location newLocation) {
        // Update config with new coordinates
        ConfigManager.CountHologramData old = configManager.getAllCountHolograms().get(name);
        if (old == null) return;
        // Re-persist with same world but new coords
        configManager.addCountHologram(name, old.worldName, newLocation);

        // Move live entity or respawn it
        ArmorStand stand = findStand(activeEntities.get(name));
        if (stand != null) {
            stand.teleport(newLocation);
        } else {
            // Not alive yet — respawn
            ConfigManager.CountHologramData data = configManager.getAllCountHolograms().get(name);
            if (data != null) spawnFromData(name, data);
        }
    }

    public boolean hologramExists(String name) {
        return configManager.getAllCountHolograms().containsKey(name);
    }
}
