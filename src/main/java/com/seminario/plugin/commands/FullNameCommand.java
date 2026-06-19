package com.seminario.plugin.commands;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles /pucvname, /impucv and /pucvlist commands.
 * Stores a mapping playerNick -> full name in pucvnames.yml inside plugin data folder.
 */
public class FullNameCommand implements CommandExecutor {

	private final JavaPlugin plugin;
	private final File storageFile;

	public FullNameCommand(JavaPlugin plugin) {
		this.plugin = plugin;
		this.storageFile = new File(plugin.getDataFolder(), "pucvnames.yml");
		if (!storageFile.getParentFile().exists()) {
			storageFile.getParentFile().mkdirs();
		}
		try {
			if (!storageFile.exists()) {
				storageFile.createNewFile();
			}
		} catch (IOException e) {
			plugin.getLogger().warning("Could not create pucvnames.yml: " + e.getMessage());
		}
	}

	private FileConfiguration load() {
		return YamlConfiguration.loadConfiguration(storageFile);
	}

	private void save(FileConfiguration cfg) {
		try {
			cfg.save(storageFile);
		} catch (IOException e) {
			plugin.getLogger().warning("Failed to save pucvnames.yml: " + e.getMessage());
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		String cmd = command.getName().toLowerCase();

		if (cmd.equals("pucvname")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("Este comando solo puede ser usado por un jugador.");
				return true;
			}
			Player player = (Player) sender;
			if (args.length == 0) {
				player.sendMessage("Uso: /pucvname <nombre_completo>");
				return true;
			}
			String fullName = String.join(" ", args).trim();
			FileConfiguration cfg = load();
			cfg.set(player.getName(), fullName);
			save(cfg);
			player.sendMessage("✅ Nombre registrado: " + fullName);
			return true;
		}

		if (cmd.equals("impucv")) {
			FileConfiguration cfg = load();
			if (args.length >= 1) {
				String nick = args[0];
				String name = cfg.getString(nick, null);
				sender.sendMessage(name != null ? nick + " => " + name : nick + " no tiene nombre registrado.");
				return true;
			}

			if (!(sender instanceof Player)) {
				sender.sendMessage("Uso: /impucv <nick>  o ejecutalo como jugador para ver tu registro.");
				return true;
			}

			Player player = (Player) sender;
			String registered = cfg.getString(player.getName(), null);
			if (registered == null) {
				player.sendMessage("No tienes un nombre registrado. Usa /pucvname <nombre_completo>");
			} else {
				player.sendMessage("Tu nombre registrado es: " + registered);
			}
			return true;
		}

		if (cmd.equals("pucvlist")) {
			if (!sender.hasPermission("pucv.admin") && !sender.isOp()) {
				sender.sendMessage("No tienes permiso para usar este comando.");
				return true;
			}
			FileConfiguration cfg = load();
			Set<String> keys = cfg.getKeys(false);
			if (keys.isEmpty()) {
				sender.sendMessage("No hay nombres registrados.");
				return true;
			}
			sender.sendMessage("Lista de nombres registrados (") ;
			for (String nick : keys) {
				String name = cfg.getString(nick, "");
				sender.sendMessage("- " + nick + " => " + name);
			}
			return true;
		}

		return false;
	}
}
