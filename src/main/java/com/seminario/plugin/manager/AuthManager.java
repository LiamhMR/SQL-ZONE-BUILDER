package com.seminario.plugin.manager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class AuthManager {

    private static final String INVITATIONS_SECTION = "codes";
    private static final String DEFAULT_INVITATION_PREFIX = "ADMIN-";
    private static final String INVITATION_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final JavaPlugin plugin;
    private final File usersFolder;
    private final File invitationsFile;
    private final YamlConfiguration invitationsConfig;
    private final Set<UUID> authenticatedPlayers;

    public AuthManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.usersFolder = new File(plugin.getDataFolder(), "users");
        this.invitationsFile = new File(plugin.getDataFolder(), "invitations.yml");
        this.invitationsConfig = new YamlConfiguration();
        this.authenticatedPlayers = new HashSet<>();

        ensureStorage();
        loadInvitations();
        ensureDefaultInvitationExists();
    }

    public void handleJoin(Player player) {
        logout(player);
        sendAuthReminder(player);
    }

    public void logout(Player player) {
        authenticatedPlayers.remove(player.getUniqueId());
    }

    public boolean isAuthenticated(Player player) {
        return authenticatedPlayers.contains(player.getUniqueId());
    }

    public boolean isRegistered(Player player) {
        return isRegistered(player.getName());
    }

    public boolean isRegistered(String username) {
        return getUserFile(username).exists();
    }

    public void sendAuthReminder(Player player) {
        player.sendMessage("");
        if (isRegistered(player)) {
            player.sendMessage("§eDebes autenticarte para usar el servidor.");
            player.sendMessage("§7Usa: §f/login <password>");
        } else {
            player.sendMessage("§eDebes registrarte para usar el servidor.");
            player.sendMessage("§7Usa: §f/register <invitation_key> <password> <confirm_password>");
        }
        player.sendMessage("§7Hasta autenticarte no podrás usar comandos.");
        player.sendMessage("");
    }

    public boolean register(Player player, String invitationKey, String password, String confirmPassword) {
        if (isRegistered(player)) {
            player.sendMessage("§cEse usuario ya está registrado. Usa /login <password>.");
            return false;
        }

        if (!password.equals(confirmPassword)) {
            player.sendMessage("§cLas contraseñas no coinciden.");
            return false;
        }

        if (password.isBlank()) {
            player.sendMessage("§cLa contraseña no puede estar vacía.");
            return false;
        }

        String storedInvitationKey = findInvitationKey(invitationKey);
        if (storedInvitationKey == null) {
            player.sendMessage("§cLa invitation key no es válida.");
            return false;
        }

        File userFile = getUserFile(player.getName());
        YamlConfiguration userConfig = new YamlConfiguration();
        userConfig.set("username", player.getName());
        userConfig.set("passwordSha256", sha256(password));
        userConfig.set("registeredAt", Instant.now().toString());
        userConfig.set("lastLoginAt", Instant.now().toString());

        try {
            userConfig.save(userFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save user file for " + player.getName() + ": " + e.getMessage());
            player.sendMessage("§cNo se pudo completar el registro.");
            return false;
        }

        consumeInvitation(storedInvitationKey);
        authenticatedPlayers.add(player.getUniqueId());
        player.sendMessage("§aRegistro completado correctamente.");
        return true;
    }

    public boolean login(Player player, String password) {
        if (!isRegistered(player)) {
            player.sendMessage("§cEse usuario no está registrado. Usa /register primero.");
            return false;
        }

        File userFile = getUserFile(player.getName());
        YamlConfiguration userConfig = YamlConfiguration.loadConfiguration(userFile);
        String expectedHash = userConfig.getString("passwordSha256", "");
        if (!expectedHash.equals(sha256(password))) {
            player.sendMessage("§cContraseña incorrecta.");
            return false;
        }

        userConfig.set("lastLoginAt", Instant.now().toString());
        try {
            userConfig.save(userFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not update last login for " + player.getName() + ": " + e.getMessage());
        }

        authenticatedPlayers.add(player.getUniqueId());
        player.sendMessage("§aLogin correcto.");
        return true;
    }

    public boolean canUseCommandBeforeAuth(String message) {
        String trimmed = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("/login ")
                || trimmed.equals("/login")
                || trimmed.startsWith("/register ")
                || trimmed.equals("/register");
    }

    public boolean createInvitation(String key, int uses) {
        if (findInvitationKey(key) != null) {
            return false;
        }

        invitationsConfig.set(INVITATIONS_SECTION + "." + key + ".uses", uses);
        invitationsConfig.set(INVITATIONS_SECTION + "." + key + ".createdAt", Instant.now().toString());
        saveInvitations();
        return true;
    }

    public boolean removeInvitation(String key) {
        String storedKey = findInvitationKey(key);
        if (storedKey == null) {
            return false;
        }

        invitationsConfig.set(INVITATIONS_SECTION + "." + storedKey, null);
        saveInvitations();
        ensureDefaultInvitationExists();
        return true;
    }

    public Map<String, Integer> listInvitations() {
        ConfigurationSection section = invitationsConfig.getConfigurationSection(INVITATIONS_SECTION);
        if (section == null) {
            return Map.of();
        }

        return section.getKeys(false).stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toMap(
                        key -> key,
                        key -> section.getInt(key + ".uses", 0),
                        (left, right) -> left,
                        HashMap::new));
    }

    public String ensureDefaultInvitationExists() {
        Map<String, Integer> invitations = listInvitations();
        if (!invitations.isEmpty()) {
            return null;
        }

        String generated = DEFAULT_INVITATION_PREFIX + generateCodeSuffix(8);
        invitationsConfig.set(INVITATIONS_SECTION + "." + generated + ".uses", 1);
        invitationsConfig.set(INVITATIONS_SECTION + "." + generated + ".createdAt", Instant.now().toString());
        saveInvitations();
        plugin.getLogger().warning("No invitation keys existed. Generated bootstrap invitation key: " + generated);
        return generated;
    }

    private void ensureStorage() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        if (!usersFolder.exists()) {
            usersFolder.mkdirs();
        }
        if (!invitationsFile.exists()) {
            try {
                invitationsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create invitations.yml: " + e.getMessage());
            }
        }
    }

    private void loadInvitations() {
        try {
            invitationsConfig.load(invitationsFile);
        } catch (Exception e) {
            plugin.getLogger().severe("Could not load invitations.yml: " + e.getMessage());
        }
    }

    private void saveInvitations() {
        try {
            invitationsConfig.save(invitationsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save invitations.yml: " + e.getMessage());
        }
    }

    private String findInvitationKey(String providedKey) {
        ConfigurationSection section = invitationsConfig.getConfigurationSection(INVITATIONS_SECTION);
        if (section == null) {
            return null;
        }

        for (String key : section.getKeys(false)) {
            if (key.equalsIgnoreCase(providedKey)) {
                return key;
            }
        }
        return null;
    }

    private void consumeInvitation(String key) {
        int uses = invitationsConfig.getInt(INVITATIONS_SECTION + "." + key + ".uses", 0);
        if (uses <= 1) {
            invitationsConfig.set(INVITATIONS_SECTION + "." + key, null);
        } else {
            invitationsConfig.set(INVITATIONS_SECTION + "." + key + ".uses", uses - 1);
        }
        saveInvitations();
        ensureDefaultInvitationExists();
    }

    private File getUserFile(String username) {
        return new File(usersFolder, username.toLowerCase(Locale.ROOT) + ".yml");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte current : hashed) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }

    private String generateCodeSuffix(int length) {
        byte[] randomBytes = new byte[length];
        new java.security.SecureRandom().nextBytes(randomBytes);
        StringBuilder builder = new StringBuilder(length);
        for (byte current : randomBytes) {
            int index = Byte.toUnsignedInt(current) % INVITATION_ALPHABET.length();
            builder.append(INVITATION_ALPHABET.charAt(index));
        }
        return builder.toString();
    }
}