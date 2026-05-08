package com.seminario.plugin.commands;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.seminario.plugin.manager.AuthManager;
import com.seminario.plugin.manager.LobbyManager;
import com.seminario.plugin.manager.SpawnpointManager;

public class AuthCommand implements CommandExecutor, TabCompleter {

    private final AuthManager authManager;
    private final SpawnpointManager spawnpointManager;
    private final LobbyManager lobbyManager;
    private final JavaPlugin plugin;

    public AuthCommand(AuthManager authManager, SpawnpointManager spawnpointManager, LobbyManager lobbyManager, JavaPlugin plugin) {
        this.authManager = authManager;
        this.spawnpointManager = spawnpointManager;
        this.lobbyManager = lobbyManager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando solo puede ser usado por jugadores.");
            return true;
        }

        String commandName = command.getName().toLowerCase();
        if (commandName.equals("register")) {
            if (args.length != 3) {
                player.sendMessage("§cUso: /register <invitation_key> <password> <confirm_password>");
                return true;
            }

            if (authManager.register(player, args[0], args[1], args[2])) {
                completeAuthentication(player);
            }
            return true;
        }

        if (commandName.equals("login")) {
            if (args.length != 1) {
                player.sendMessage("§cUso: /login <password>");
                return true;
            }

            if (authManager.login(player, args[0])) {
                completeAuthentication(player);
            }
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }

    private void completeAuthentication(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (spawnpointManager.getSpawnpointLocation() != null && lobbyManager.isLobbyWorld(player.getWorld())) {
                lobbyManager.giveLobbyInventoryToPlayer(player, true);
            }
            player.sendMessage("§aAutenticación completada. Ya puedes usar el servidor.");
        });
    }
}