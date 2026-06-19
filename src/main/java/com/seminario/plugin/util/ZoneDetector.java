package com.seminario.plugin.util;

import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.seminario.plugin.model.MenuZone;

/**
 * Utility class for detecting when players are within menu zones.
 * Detection uses the real 3D bounding box of the zone.
 * For flat zones (pos1.y == pos2.y), a +2 block upward margin is added
 * so that a player standing on the floor block (minY) is still detected.
 */
public class ZoneDetector {
    
    private static final Logger LOGGER = Logger.getLogger(ZoneDetector.class.getName());

    // Extra upward headroom added on top of maxY so a player standing on
    // the top block of the zone (or on a flat zone's single Y level) is detected.
    // 2 covers the full player model height (1.8 blocks, rounded up to 2).
    private static final double PLAYER_HEIGHT_MARGIN = 2.0;
    
    /**
     * Check if a player is within a menu zone (including vertical tolerance)
     * @param player The player to check
     * @param zone The menu zone
     * @return true if player is within the zone area (with vertical tolerance)
     */
    public static boolean isPlayerInZone(Player player, MenuZone zone) {
        Location playerLoc = player.getLocation();
        return isLocationInZone(playerLoc, zone);
    }
    
    /**
     * Check if a location is within a menu zone.
     * Uses the real 3D bounding box: [minX,maxX] x [minY, maxY+PLAYER_HEIGHT_MARGIN] x [minZ,maxZ].
     * The upward margin ensures a player standing on the top/floor block is always detected,
     * without requiring artificial Y offsets.
     *
     * @param location The location to check
     * @param zone The menu zone
     * @return true if location is within the zone volume
     */
    public static boolean isLocationInZone(Location location, MenuZone zone) {
        // Check if in same world
        if (!location.getWorld().equals(zone.getWorld())) {
            return false;
        }
        
        // Get zone boundaries
        double minX = Math.min(zone.getPos1().getX(), zone.getPos2().getX());
        double maxX = Math.max(zone.getPos1().getX(), zone.getPos2().getX());
        double minZ = Math.min(zone.getPos1().getZ(), zone.getPos2().getZ());
        double maxZ = Math.max(zone.getPos1().getZ(), zone.getPos2().getZ());
        double minY = Math.min(zone.getPos1().getY(), zone.getPos2().getY());
        double maxY = Math.max(zone.getPos1().getY(), zone.getPos2().getY());

        double playerX = location.getX();
        double playerY = location.getY();
        double playerZ = location.getZ();

        // Check X and Z boundaries
        boolean inHorizontalBounds = playerX >= minX && playerX <= maxX &&
                                     playerZ >= minZ && playerZ <= maxZ;
        if (!inHorizontalBounds) {
            return false;
        }

        // Check vertical bounds: player must be inside the box or standing on its top face.
        // +PLAYER_HEIGHT_MARGIN extends the ceiling so standing on maxY is included.
        boolean inVerticalRange = playerY >= minY && playerY <= (maxY + PLAYER_HEIGHT_MARGIN);

        return inVerticalRange;
    }
    
    /**
     * Get the vertical distance between a player and a zone
     * @param player The player
     * @param zone The menu zone  
     * @return Vertical distance (positive if player is above zone)
     */
    public static double getVerticalDistance(Player player, MenuZone zone) {
        Location playerLoc = player.getLocation();
        double maxZoneY = Math.max(zone.getPos1().getY(), zone.getPos2().getY());
        return playerLoc.getY() - maxZoneY;
    }
    
    /**
     * Check if player is horizontally within zone boundaries
     * @param player The player
     * @param zone The menu zone
     * @return true if player is within X/Z boundaries
     */
    public static boolean isPlayerInHorizontalBounds(Player player, MenuZone zone) {
        Location playerLoc = player.getLocation();
        
        if (!playerLoc.getWorld().equals(zone.getWorld())) {
            return false;
        }
        
        double minX = Math.min(zone.getPos1().getX(), zone.getPos2().getX());
        double maxX = Math.max(zone.getPos1().getX(), zone.getPos2().getX());
        double minZ = Math.min(zone.getPos1().getZ(), zone.getPos2().getZ());
        double maxZ = Math.max(zone.getPos1().getZ(), zone.getPos2().getZ());
        
        double playerX = playerLoc.getX();
        double playerZ = playerLoc.getZ();
        
        return playerX >= minX && playerX <= maxX && playerZ >= minZ && playerZ <= maxZ;
    }
    
    /**
     * Get debug information about player position relative to zone
     * @param player The player
     * @param zone The menu zone
     * @return Debug string with position information
     */
    public static String getDebugInfo(Player player, MenuZone zone) {
        Location playerLoc = player.getLocation();
        
        boolean inHorizontal = isPlayerInHorizontalBounds(player, zone);
        double verticalDistance = getVerticalDistance(player, zone);
        boolean inZone = isPlayerInZone(player, zone);
        
        return String.format(
            "Player: %s | Zone: %s | Horizontal: %s | Vertical Distance: %.2f | In Zone: %s | Player Y: %.1f | Zone Y: %.1f-%.1f",
            player.getName(), zone.getName(), inHorizontal, verticalDistance, inZone,
            playerLoc.getY(), zone.getPos1().getY(), zone.getPos2().getY()
        );
    }
}