package com.seminario.plugin.gui;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.seminario.plugin.config.ConfigManager;
import com.seminario.plugin.manager.QuestManager;
import com.seminario.plugin.model.MenuZone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Handles CHESTPORT menu GUI and teleportation logic
 */
public class ChestportGUI {
    
    private static final Map<UUID, MenuZone> playerZones = new HashMap<>();
    private static final Random random = new Random();
    private static QuestManager questManager = null;
    private static ConfigManager configManager = null;

    public static void setQuestManager(QuestManager manager) {
        questManager = manager;
    }

    /**
     * Set the config manager for world player-limit checks
     */
    public static void setConfigManager(ConfigManager manager) {
        configManager = manager;
    }
    
    /**
     * Open the chestport confirmation GUI for a player
     * @param player The player
     * @param zone The menu zone with teleport data
     */
    public static void openChestportGUI(Player player, MenuZone zone) {
        if (!zone.hasTeleportLocation()) {
            player.sendMessage(Component.text("Esta zona no tiene una ubicación de teleport configurada.", NamedTextColor.RED));
            return;
        }
        
        // Store the player's zone for later reference
        playerZones.put(player.getUniqueId(), zone);
        
        // Create inventory GUI
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("¿Quieres ser transportado?"));
        
        // Fill with glass panes for decoration
        ItemStack grayGlass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", "");
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, grayGlass);
        }
        
        // YES button (Green)
        ItemStack yesButton = createItem(Material.GREEN_CONCRETE, 
            "§a§lSÍ", 
            "§7Ser transportado al destino",
            "§8Ubicación: " + formatLocation(zone.getTeleportLocation()),
            "§8Mundo: " + zone.getTeleportWorldName());
        gui.setItem(11, yesButton);
        
        // NO button (Red)
        ItemStack noButton = createItem(Material.RED_CONCRETE,
            "§c§lNO",
            "§7Rechazar el teleport",
            "§6¡Serás lanzado por los aires!");
        gui.setItem(15, noButton);
        
        // Info item
        ItemStack infoItem = createItem(Material.ENDER_PEARL,
            "§6Portal de Transporte",
            "§7Elige tu destino:",
            "§a➤ SÍ: Teleport al destino",
            "§c➤ NO: Ser lanzado al aire");
        gui.setItem(4, infoItem);
        
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
    }
    
    /**
     * Handle GUI click events
     * @param player The player who clicked
     * @param slot The slot that was clicked
     * @return true if the click was handled
     */
    public static boolean handleGUIClick(Player player, int slot) {
        MenuZone zone = playerZones.get(player.getUniqueId());
        if (zone == null) {
            return false;
        }
        
        player.closeInventory();
        
        if (slot == 11) { // YES button
            handleYesChoice(player, zone);
        } else if (slot == 15) { // NO button
            handleNoChoice(player);
        }
        
        // Clean up
        playerZones.remove(player.getUniqueId());
        return true;
    }
    
    /**
     * Handle YES choice - teleport with effects (after validating quest requirements)
     * @param player The player
     * @param zone The menu zone
     */
    private static void handleYesChoice(Player player, MenuZone zone) {
        // Check quest requirement
        if (zone.getQuestRequirement() != null && !zone.getQuestRequirement().isEmpty()) {
            if (questManager == null || !questManager.playerHasQuestAttempt(player.getUniqueId(), zone.getQuestRequirement())) {
                // Requirement not met
                handleRequirementFailed(player, zone);
                return;
            }
        }

        // Check world player limit
        if (configManager != null && zone.hasTeleportLocation()) {
            String destWorldName = zone.getTeleportWorldName();
            int limit = configManager.getWorldPlayerLimit(destWorldName);
            if (limit > 0) {
                World destWorld = Bukkit.getWorld(destWorldName);
                int current = (destWorld != null) ? destWorld.getPlayers().size() : 0;
                if (current >= limit) {
                    player.sendMessage(Component.text("¡Arena llena! (" + current + "/" + limit + " jugadores)", NamedTextColor.RED));
                    player.showTitle(net.kyori.adventure.title.Title.title(
                        Component.text("Arena Llena", NamedTextColor.RED),
                        Component.text(current + "/" + limit + " jugadores", NamedTextColor.GOLD)
                    ));
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
                    return;
                }
            }
        }

        // Proceed with teleportation
        doTeleport(player, zone);
    }

    /**
     * Execute the actual teleportation sequence
     */
    private static void doTeleport(Player player, MenuZone zone) {
        Location teleportLoc = zone.getTeleportLocation();
        if (teleportLoc == null) {
            player.sendMessage(Component.text("Error: Ubicación de teleport no válida.", NamedTextColor.RED));
            return;
        }
        
        // Validate world still exists
        World world = Bukkit.getWorld(zone.getTeleportWorldName());
        if (world == null) {
            player.sendMessage(Component.text("Error: El mundo de destino no existe.", NamedTextColor.RED));
            return;
        }
        
        player.sendMessage(Component.text("¡Preparándote para el viaje!", NamedTextColor.GREEN));
        
        // Launch player up 10 blocks with no fall damage
        Vector upVector = new Vector(0, 1.2, 0); // Velocity to reach ~10 blocks height
        player.setVelocity(upVector);
        
        // Give no fall damage effect
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0)); 
        
        // Play sound
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
        
        // Schedule firework and teleport after delay
        Bukkit.getScheduler().runTaskLater(
            org.bukkit.plugin.java.JavaPlugin.getPlugin(com.seminario.plugin.App.class),
            () -> {
                // Spawn firework at player's location
                spawnTeleportFirework(player.getLocation());
                
                // Teleport after firework
                Bukkit.getScheduler().runTaskLater(
                    org.bukkit.plugin.java.JavaPlugin.getPlugin(com.seminario.plugin.App.class),
                    () -> {
                        player.teleport(teleportLoc);
                        player.playSound(teleportLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
                        player.sendMessage(Component.text("¡Has sido transportado exitosamente!", NamedTextColor.GOLD));
                        
                        // Spawn arrival firework
                        spawnTeleportFirework(teleportLoc);
                    }, 10L // 1 second after firework
                );
            }, 20L // 1 seconds to reach peak height
        );
    }

    /**
     * Handle quest requirement failure - reject teleport with custom message
     * @param player The player
     * @param zone The menu zone
     */
    private static void handleRequirementFailed(Player player, MenuZone zone) {
        String questName = zone.getQuestRequirement();
        String failureText = zone.getQuestFailureText() != null && !zone.getQuestFailureText().isEmpty()
                ? zone.getQuestFailureText()
                : "Debes completar el quest " + questName + " antes de usar este portal.";

        // Show title with custom failure message
        player.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("Acceso Denegado", NamedTextColor.RED),
                Component.text(failureText, NamedTextColor.GOLD)
        ));

        // Play rejection sound
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);

        // Launch player with firework
        player.sendMessage(Component.text("¡Debes completar el quest requerido!", NamedTextColor.RED));
        handleNoChoice(player);
    }
    
    /**
     * Handle NO choice - launch player randomly
     * @param player The player
     */
    public static void handleNoChoice(Player player) {
        player.sendMessage(Component.text("¡Has rechazado el teleport! ¡Prepárate para volar!", NamedTextColor.YELLOW));
        
        // Random direction (horizontal)
        double angle = random.nextDouble() * 2 * Math.PI;
        double strength = 1.5 + random.nextDouble() * 0.5; // 1.5-2.0 strength
        
        Vector launchVector = new Vector(
            Math.cos(angle) * strength,
            1.8, // High vertical component for 15+ blocks
            Math.sin(angle) * strength
        );
        
        player.setVelocity(launchVector);
        
        // Give no fall damage effect
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 150, 0)); // 7.5 seconds
        
        // Play dramatic sound
        player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f, 1.5f);
        
        // Spawn dramatic firework
        Bukkit.getScheduler().runTaskLater(
            org.bukkit.plugin.java.JavaPlugin.getPlugin(com.seminario.plugin.App.class),
            () -> spawnRejectionFirework(player.getLocation()),
            10L // 0.5 seconds
        );
        
        player.sendMessage(Component.text("¡Wheeeee! 🚀", NamedTextColor.GOLD));
    }
    
    /**
     * Spawn a firework for teleportation
     * @param location The location to spawn the firework
     */
    private static void spawnTeleportFirework(Location location) {
        Firework firework = location.getWorld().spawn(location, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        
        FireworkEffect effect = FireworkEffect.builder()
            .with(FireworkEffect.Type.STAR)
            .withColor(Color.BLUE, Color.WHITE, Color.AQUA)
            .withFade(Color.GRAY)
            .flicker(true)
            .trail(true)
            .build();
            
        meta.addEffect(effect);
        meta.setPower(1);
        firework.setFireworkMeta(meta);
    }
    
    /**
     * Spawn a firework for rejection/launch
     * @param location The location to spawn the firework
     */
    private static void spawnRejectionFirework(Location location) {
        Firework firework = location.getWorld().spawn(location, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        
        FireworkEffect effect = FireworkEffect.builder()
            .with(FireworkEffect.Type.BURST)
            .withColor(Color.RED, Color.ORANGE, Color.YELLOW)
            .withFade(Color.BLACK)
            .flicker(false)
            .trail(true)
            .build();
            
        meta.addEffect(effect);
        meta.setPower(2);
        firework.setFireworkMeta(meta);
    }
    
    /**
     * Create an item with display name and lore
     * @param material The material
     * @param displayName The display name
     * @param lore The lore lines
     * @return The created ItemStack
     */
    private static ItemStack createItem(Material material, String displayName, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(displayName));
            if (lore.length > 0) {
                meta.lore(Arrays.stream(lore)
                    .map(Component::text)
                    .toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * Format a location for display
     * @param location The location
     * @return Formatted string
     */
    private static String formatLocation(Location location) {
        return String.format("%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ());
    }
    
    /**
     * Check if a player has an active chestport GUI session
     * @param player The player
     * @return true if has active session
     */
    public static boolean hasActiveSession(Player player) {
        return playerZones.containsKey(player.getUniqueId());
    }
    
    /**
     * Clean up player session (on disconnect/leave zone)
     * @param player The player
     */
    public static void cleanupSession(Player player) {
        playerZones.remove(player.getUniqueId());
    }
}