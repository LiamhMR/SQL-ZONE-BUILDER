package com.seminario.plugin.listener;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.seminario.plugin.manager.AuthManager;
import com.seminario.plugin.manager.HarryNPCManager;
import com.seminario.plugin.manager.LobbyManager;
import com.seminario.plugin.manager.SpawnpointManager;
import com.seminario.plugin.util.SpawnpointEffects;

/**
 * Handles player join events to teleport to spawnpoint with welcome effects
 */
public class PlayerJoinListener implements Listener {
    
    private final SpawnpointManager spawnpointManager;
    private final LobbyManager lobbyManager;
    private final HarryNPCManager harryNPCManager;
    private final AuthManager authManager;
    private final JavaPlugin plugin;
    private boolean firstJoinRecoveryExecuted;
    
    public PlayerJoinListener(SpawnpointManager spawnpointManager, LobbyManager lobbyManager, HarryNPCManager harryNPCManager, AuthManager authManager, JavaPlugin plugin) {
        this.spawnpointManager = spawnpointManager;
        this.lobbyManager = lobbyManager;
        this.harryNPCManager = harryNPCManager;
        this.authManager = authManager;
        this.plugin = plugin;
        this.firstJoinRecoveryExecuted = false;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        authManager.handleJoin(player);

        if (!firstJoinRecoveryExecuted) {
            firstJoinRecoveryExecuted = true;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getLogger().info("PlayerJoinListener: executing automatic Harry NPC reset after first player joined.");
                harryNPCManager.resetAllNPCs(plugin.getServer().getConsoleSender());
            }, 40L);
        }
        
        // Teleport to spawnpoint after a short delay to ensure player is fully loaded
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // ALWAYS clear inventory completely (clean slate for everyone)
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR));
            
            // ALWAYS teleport to spawnpoint (without immediate Adventure mode)
            plugin.getLogger().info("PlayerJoinListener: Teleporting " + player.getName() + " to spawn");
            spawnpointManager.teleportToSpawnpoint(player, false, false);
            
            // Send welcome message
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "✨ ¡Bienvenido " + player.getName() + "! ✨");
            player.sendMessage(ChatColor.YELLOW + "Has sido teletransportado al spawn del servidor.");
            player.sendMessage("");
            //INVITACION DE DISCORD
            player.sendMessage(ChatColor.AQUA + "Únete a nuestro Discord para info y soporte.");
            player.sendMessage(ChatColor.BLUE + "https://discord.gg/Xuq8v3RP5");
            player.sendMessage("");
            //Introducción explicatoria de que este servidor está orientado a la prueba de SQL por motivo de proyecto de título
            player.sendMessage(ChatColor.GREEN + "Este servidor está desarrollado por motivo de nuestro Proyecto de título.");
            player.sendMessage(ChatColor.GREEN + "Es ideal que respondas tanto el pretest como el postest a consciencia para que nuestro proyecto tenga mejores resultados.");
            player.sendMessage(ChatColor.GREEN + "Cualquier duda o comentario, no dudes en contactarnos en nuestro Discord.");
            player.sendMessage("");
            
            // ALWAYS give lobby inventory after teleport with longer delay
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getLogger().info("PlayerJoinListener: Checking world for " + player.getName() + " - Current world: " + player.getWorld().getName());

                if (!lobbyManager.isLobbyWorld(player.getWorld())) {
                    plugin.getLogger().info("PlayerJoinListener: Skipping lobby inventory for " + player.getName() + " (not in lobby world)");
                    return;
                }
                
                // Check if spawn location exists
                if (spawnpointManager.getSpawnpointLocation() != null) {
                    if (authManager.isAuthenticated(player)) {
                        plugin.getLogger().info("PlayerJoinListener: Spawn is configured, giving lobby inventory to " + player.getName());
                        lobbyManager.giveLobbyInventoryToPlayer(player, true); // Always set Adventure mode in spawn
                    } else {
                        plugin.getLogger().info("PlayerJoinListener: Waiting for authentication before giving lobby inventory to " + player.getName());
                        authManager.sendAuthReminder(player);
                    }
                } else {
                    plugin.getLogger().warning("PlayerJoinListener: Spawn location is NULL! Cannot give lobby inventory to " + player.getName());
                }
            }, 20L); // 1 second delay (increased from 0.5)
            
            // Play welcome music after inventory is given
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (authManager.isAuthenticated(player)) {
                    SpawnpointEffects.playWelcomeMelody(player, plugin);
                }
            }, 30L); // 1.5 seconds delay
            
        }, 5L); // 0.25 seconds delay to ensure player is loaded
    }
}