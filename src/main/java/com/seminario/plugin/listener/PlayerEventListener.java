package com.seminario.plugin.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.seminario.plugin.config.ConfigManager;
import com.seminario.plugin.gui.ChestportGUI;
import com.seminario.plugin.manager.FixSlideManager;
import com.seminario.plugin.manager.SlideShowManager;
import com.seminario.plugin.model.MenuType;
import com.seminario.plugin.model.MenuZone;
import com.seminario.plugin.util.ZoneDetector;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Handles player movement and interaction events for menu zones
 * Manages automatic slideshow triggering and navigation
 */
public class PlayerEventListener implements Listener {
    
    private final ConfigManager configManager;
    private final SlideShowManager slideShowManager;
    private final FixSlideManager fixSlideManager;
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private LaboratoryListener laboratoryListener;
    private com.seminario.plugin.manager.CountHologramManager countHologramManager;
    
    // Track which zones players are currently in
    private final Map<UUID, String> playerCurrentZones;
    
    public PlayerEventListener(ConfigManager configManager, SlideShowManager slideShowManager, FixSlideManager fixSlideManager, org.bukkit.plugin.java.JavaPlugin plugin) {
        this.configManager = configManager;
        this.slideShowManager = slideShowManager;
        this.fixSlideManager = fixSlideManager;
        this.plugin = plugin;
        this.playerCurrentZones = new HashMap<>();
    }
    
    /**
     * Set the laboratory listener for zone management
     * @param laboratoryListener The laboratory listener
     */
    public void setLaboratoryListener(LaboratoryListener laboratoryListener) {
        this.laboratoryListener = laboratoryListener;
    }

    public void setCountHologramManager(com.seminario.plugin.manager.CountHologramManager manager) {
        this.countHologramManager = manager;
    }
    
    /**
     * Handle player movement to detect zone entry/exit
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location toLocation = event.getTo();
        Location fromLocation = event.getFrom();
        
        // Skip if player didn't actually move to a different block
        if (fromLocation.getBlockX() == toLocation.getBlockX() &&
             fromLocation.getBlockY() == toLocation.getBlockY() &&
             fromLocation.getBlockZ() == toLocation.getBlockZ() &&
             fromLocation.getWorld().equals(toLocation.getWorld())) {
            return;
        }
        
        String currentZone = playerCurrentZones.get(player.getUniqueId());
        String newZone = findZoneAtLocation(toLocation);
        
        // Hysteresis: if the player is already in a zone and still physically inside it,
        // keep them in that zone even if another zone also matches their location.
        // This prevents oscillation when two zones overlap (e.g. two adjacent labs).
        if (currentZone != null && newZone != null && !newZone.equals(currentZone)) {
            MenuZone currentMenuZone = configManager.getMenuZone(currentZone);
            if (currentMenuZone != null && ZoneDetector.isLocationInZone(toLocation, currentMenuZone)) {
                newZone = currentZone; // stay in current zone
            }
        }
        
        // Check if zone changed
        if (!java.util.Objects.equals(currentZone, newZone)) {
            // Player left a zone
            if (currentZone != null) {
                handlePlayerLeaveZone(player, currentZone);
            }
            
            // Player entered a zone
            if (newZone != null) {
                handlePlayerEnterZone(player, newZone);
            }
            
            // Update tracking
            if (newZone != null) {
                playerCurrentZones.put(player.getUniqueId(), newZone);
            } else {
                playerCurrentZones.remove(player.getUniqueId());
            }
        } else if (newZone != null && slideShowManager.hasActiveSession(player)) {
            // Player moved within the same zone and has an active slideshow
            // Reposition screen display
            com.seminario.plugin.util.SlideScreenRenderer.repositionScreen(player);
        }
    }
    
    /**
     * Handle player right-click interactions for slide navigation
     * DEPRECATED: Now using button-based navigation instead
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Esta funcionalidad ha sido reemplazada por los botones de navegación
        // Se mantiene el método para compatibilidad, pero ya no hace nada
    }
    
    /**
     * Handle interactions with item frames for slide navigation
     * Detects clicks on navigation buttons and slide screen frames
     * Handles both SLIDE (player-based) and FIXSLIDE (permanent) buttons
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        
        // Try FIXSLIDE button handling first (for any ItemFrame click)
        if (event.getRightClicked() instanceof org.bukkit.entity.ItemFrame) {
            org.bukkit.entity.ItemFrame frame = (org.bukkit.entity.ItemFrame) event.getRightClicked();
            
            com.seminario.plugin.manager.FixSlideManager fixSlideManager = 
                ((com.seminario.plugin.App) plugin).getFixSlideManager();
            
            // If FIXSLIDE button was clicked, handle and return
            if (fixSlideManager.handleButtonClick(frame, player)) {
                event.setCancelled(true);
                java.util.logging.Logger.getLogger("FixSlideNavigation").info(
                    "FIXSLIDE button clicked by " + player.getName()
                );
                return;
            }
        }
        
        // Check if player has an active slideshow session for SLIDE type
        if (!slideShowManager.hasActiveSession(player)) {
            return;
        }
        
        // Log the interaction for debugging
        java.util.logging.Logger.getLogger("SlideNavigation").info(
            "Player " + player.getName() + " interacted with entity: " + event.getRightClicked().getType()
        );
        
        // Check if it's a navigation button (SLIDE type)
        if (com.seminario.plugin.util.SlideScreenRenderer.isNavigationButton(event.getRightClicked())) {
            event.setCancelled(true);
            
            org.bukkit.entity.ItemFrame button = (org.bukkit.entity.ItemFrame) event.getRightClicked();
            
            // Determine which button was clicked
            if (com.seminario.plugin.util.SlideScreenRenderer.isPreviousButton(button)) {
                java.util.logging.Logger.getLogger("SlideNavigation").info(
                    "Previous button clicked by " + player.getName()
                );
                slideShowManager.previousSlide(player);
            } else if (com.seminario.plugin.util.SlideScreenRenderer.isNextButton(button)) {
                java.util.logging.Logger.getLogger("SlideNavigation").info(
                    "Next button clicked by " + player.getName()
                );
                slideShowManager.nextSlide(player);
            }
            return;
        }
        
        // Check if it's a slide screen frame (old behavior, just for compatibility)
        if (!com.seminario.plugin.util.SlideScreenRenderer.isSlideScreenFrame(event.getRightClicked())) {
            return;
        }
        
        // Verify the player owns this screen display
        java.util.UUID frameOwner = com.seminario.plugin.util.SlideScreenRenderer.getScreenOwner((org.bukkit.entity.ItemFrame) event.getRightClicked());
        if (!player.getUniqueId().equals(frameOwner)) {
            return;
        }
        
        // Cancel the interaction
        event.setCancelled(true);
    }
    
    /**
     * Protect slideshow frames from being broken
     */
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Check if it's a slide screen frame being damaged
        if (com.seminario.plugin.util.SlideScreenRenderer.isSlideScreenFrame(event.getEntity())) {
            // Cancel damage to protect the frame
            event.setCancelled(true);
        }
    }
    
    /**
     * Handle player joining the server
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Make all FIXSLIDE frames visible to the joining player
        // Delay to ensure chunks are loaded and player is fully initialized
        org.bukkit.Bukkit.getScheduler().runTaskLater(
            plugin,
            () -> fixSlideManager.showAllFramesToPlayer(player),
            40L // 2 second delay to ensure chunks are loaded
        );
        
        // Refresh count holograms for the world this player joined
        if (countHologramManager != null) {
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> countHologramManager.refreshWorld(player.getWorld().getName()),
                40L
            );
        }

        // Check if player is in any zone on join
        String zone = findZoneAtLocation(player.getLocation());
        if (zone != null) {
            playerCurrentZones.put(player.getUniqueId(), zone);
            // Small delay to ensure player is fully loaded
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                plugin, 
                () -> handlePlayerEnterZone(player, zone), 
                20L // 1 second delay
            );
        }
    }
    
    /**
     * Handle player leaving the server
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Clean up tracking
        String currentZone = playerCurrentZones.remove(player.getUniqueId());
        
        // Clean up any active sessions
        slideShowManager.cleanupPlayerSessions(player);
        
        // Log zone exit if applicable
        if (currentZone != null) {
            player.sendMessage(Component.text("Desconectado de la zona: " + currentZone, NamedTextColor.GRAY));
        }

        // Refresh count holograms for the world the player left
        if (countHologramManager != null) {
            final String worldName = player.getWorld().getName();
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> countHologramManager.refreshWorld(worldName),
                2L
            );
        }
    }

    @org.bukkit.event.EventHandler
    public void onPlayerChangedWorld(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        if (countHologramManager == null) return;
        String from = event.getFrom().getName();
        String to = event.getPlayer().getWorld().getName();
        org.bukkit.Bukkit.getScheduler().runTaskLater(
            plugin,
            () -> {
                countHologramManager.refreshWorld(from);
                countHologramManager.refreshWorld(to);
            },
            2L
        );
    }

    /**
     * Find which zone (if any) contains the given location
     * @param location The location to check
     * @return Zone name or null if not in any zone
     */
    private String findZoneAtLocation(Location location) {
        for (Map.Entry<String, MenuZone> entry : configManager.getAllMenuZones().entrySet()) {
            MenuZone zone = entry.getValue();
            if (ZoneDetector.isLocationInZone(location, zone)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * Handle player entering a zone
     * @param player The player
     * @param zoneName The zone name
     */
    private void handlePlayerEnterZone(Player player, String zoneName) {
        MenuZone zone = configManager.getMenuZone(zoneName);
        if (zone == null) {
            return;
        }
        
        // Check if zone is disabled
        if (zone.isDisabled()) {
            //player.sendMessage(Component.text("Esta zona está deshabilitada actualmente.", NamedTextColor.GRAY));
            return;
        }
        
        // Send zone enter message
        player.sendMessage(Component.text("Entraste en la zona: " + zoneName, NamedTextColor.AQUA));
        
        // Show zone info and handle zone type actions
        if (zone.hasMenuType()) {
            if (zone.getMenuType() == MenuType.SLIDE) {
                player.sendMessage(Component.text("Zona de presentaciones", NamedTextColor.YELLOW));
                // Trigger slideshow
                slideShowManager.onPlayerEnterZone(player, zoneName);
            } else if (zone.getMenuType() == MenuType.CHEST) {
                player.sendMessage(Component.text("Zona de inventario", NamedTextColor.YELLOW));
            } else if (zone.getMenuType() == MenuType.CHESTPORT) {
                player.sendMessage(Component.text("Portal de transporte", NamedTextColor.LIGHT_PURPLE));
                // Open chestport GUI
                ChestportGUI.openChestportGUI(player, zone);
            } else if (zone.getMenuType() == MenuType.LABORATORY) {
                player.sendMessage(Component.text("🧪 Laboratorio SQL", NamedTextColor.GOLD));
                // Add player to laboratory mode
                if (laboratoryListener != null) {
                    laboratoryListener.addPlayerToLaboratory(player);
                }
            } else if (zone.getMenuType() == MenuType.LABORATORY2) {
                player.sendMessage(Component.text("⚔ Laboratorio SQL Battle", NamedTextColor.GOLD));
                if (laboratoryListener != null) {
                    laboratoryListener.addPlayerToLab2(player);
                }
            }
        } else {
            player.sendMessage(Component.text("Zona sin configurar", NamedTextColor.GRAY));
            if (player.hasPermission("seminario.admin")) {
                player.sendMessage(Component.text("Usa /sm zone " + zoneName + " type <SLIDE|CHEST|CHESTPORT|LABORATORY|LABORATORY2> para configurar", NamedTextColor.GRAY));
            }
        }
    }
    
    /**
     * Handle player leaving a zone
     * @param player The player
     * @param zoneName The zone name
     */
    private void handlePlayerLeaveZone(Player player, String zoneName) {
        // Send zone exit message
        //player.sendMessage(Component.text("Saliste de la zona: " + zoneName, NamedTextColor.GRAY));
        
        // Stop slideshow if applicable
        slideShowManager.onPlayerLeaveZone(player, zoneName);
        
        // Clean up chestport GUI session if applicable
        ChestportGUI.cleanupSession(player);
        
        // Remove from laboratory only if the zone they are leaving is a lab zone
        if (laboratoryListener != null) {
            MenuZone leftZone = configManager.getAllMenuZones().get(zoneName);
            if (leftZone != null) {
                if (leftZone.getMenuType() == MenuType.LABORATORY) {
                    laboratoryListener.removePlayerFromLaboratory(player);
                } else if (leftZone.getMenuType() == MenuType.LABORATORY2) {
                    laboratoryListener.removePlayerFromLab2(player);
                }
            } else {
                // Zone no longer exists — clean up both to be safe
                laboratoryListener.removePlayerFromLaboratory(player);
                laboratoryListener.removePlayerFromLab2(player);
            }
        }
    }
    
    /**
     * Get the zone a player is currently in
     * @param player The player
     * @return Zone name or null if not in any zone
     */
    public String getPlayerCurrentZone(Player player) {
        return playerCurrentZones.get(player.getUniqueId());
    }
    
    /**
     * Check if a player is in any zone
     * @param player The player
     * @return true if player is in a zone
     */
    public boolean isPlayerInZone(Player player) {
        return playerCurrentZones.containsKey(player.getUniqueId());
    }
    
    /**
     * Force update a player's zone detection
     * @param player The player
     */
    public void updatePlayerZone(Player player) {
        String currentZone = playerCurrentZones.get(player.getUniqueId());
        String actualZone = findZoneAtLocation(player.getLocation());
        
        if (!java.util.Objects.equals(currentZone, actualZone)) {
            if (currentZone != null) {
                handlePlayerLeaveZone(player, currentZone);
            }
            
            if (actualZone != null) {
                handlePlayerEnterZone(player, actualZone);
                playerCurrentZones.put(player.getUniqueId(), actualZone);
            } else {
                playerCurrentZones.remove(player.getUniqueId());
            }
        }
    }
}