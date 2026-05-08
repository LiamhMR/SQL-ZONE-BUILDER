package com.seminario.plugin.manager;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.Plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.seminario.plugin.model.HarryNPC;
import com.seminario.plugin.util.HarryNPCAdapter;

/**
 * Manager for Harry NPCs - teacher NPCs that help orient players
 */
public class HarryNPCManager {
    
    private static final Logger LOGGER = Logger.getLogger(HarryNPCManager.class.getName());
    private final Plugin plugin;
    private final Map<String, HarryNPC> npcs;
    private final File dataFile;
    private final Gson gson;

    public HarryNPCManager(Plugin plugin) {
        this.plugin = plugin;
        this.npcs = new HashMap<>();
        this.dataFile = new File(plugin.getDataFolder(), "harry_npcs.json");
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(HarryNPC.class, new HarryNPCAdapter())
                .create();
        
        // Ensure data folder exists
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        loadNPCs();
    }

    /**
     * Create a new Harry NPC at the specified location
     */
    public boolean createNPC(String npcId, String name, Location location, Player creator) {
        ensureNPCsLoaded();

        if (npcs.containsKey(npcId)) {
            return false; // NPC with this ID already exists
        }

        HarryNPC npc = new HarryNPC(npcId, name, location);
        npcs.put(npcId, npc);
        
        spawnNPCEntity(npc);
        saveNPCs();
        
        creator.sendMessage("§a✓ Harry NPC '" + npcId + "' creado exitosamente como '" + name + "'");
        return true;
    }

    /**
     * Add a line to an NPC's dialogue
     */
    public boolean addLine(String npcId, String text, Player player) {
        HarryNPC npc = npcs.get(npcId);
        if (npc == null) {
            player.sendMessage("§cNPC Harry '" + npcId + "' no encontrado.");
            return false;
        }

        // Format color codes before storing
        String formattedText = formatColorCodes(text);
        npc.addLine(formattedText);
        saveNPCs();
        
        player.sendMessage("§a✓ Línea agregada a Harry '" + npcId + "'. Total: " + npc.getTotalLines() + " líneas");
        player.sendMessage("§7Vista previa: " + formattedText);
        return true;
    }

    /**
     * Edit a specific line of an NPC's dialogue
     */
    public boolean editLine(String npcId, int lineNumber, String newText, Player player) {
        HarryNPC npc = npcs.get(npcId);
        if (npc == null) {
            player.sendMessage("§cNPC Harry '" + npcId + "' no encontrado.");
            return false;
        }

        // Convert to 0-based index
        int index = lineNumber - 1;
        String formattedText = formatColorCodes(newText);
        if (!npc.editLine(index, formattedText)) {
            player.sendMessage("§cNúmero de línea inválido. Use 1-" + npc.getTotalLines());
            return false;
        }

        saveNPCs();
        player.sendMessage("§a✓ Línea " + lineNumber + " editada para Harry '" + npcId + "'");
        player.sendMessage("§7Vista previa: " + formattedText);
        return true;
    }

    /**
     * Display all lines of an NPC
     */
    public boolean showLines(String npcId, Player player) {
        HarryNPC npc = npcs.get(npcId);
        if (npc == null) {
            player.sendMessage("§cNPC Harry '" + npcId + "' no encontrado.");
            return false;
        }

        List<String> lines = npc.getLines();
        if (lines.isEmpty()) {
            player.sendMessage("§eHarry '" + npcId + "' no tiene líneas de diálogo.");
            return true;
        }

        player.sendMessage("§6=== Líneas de Harry '" + npcId + "' ===");
        for (int i = 0; i < lines.size(); i++) {
            player.sendMessage("§f" + (i + 1) + ". §7" + lines.get(i));
        }
        
        return true;
    }

    /**
     * Handle interaction with an NPC
     */
    public void handleNPCInteraction(Villager villager, Player player) {
        HarryNPC npc = getNPCByVillager(villager);
        if (npc == null || !npc.isVisible()) {
            return;
        }

        if (npc.getTotalLines() == 0) {
            player.sendMessage("§eHarry no tiene nada que decir todavía...");
            return;
        }

        // Clear any existing hologram first
        clearHologram(npc);

        // Check if we're already past all lines
        if (npc.isComplete()) {
            // Trigger completion sequence
            triggerCompletionSequence(npc);
            return;
        }

        String currentLine = npc.getCurrentLine();
        if (currentLine != null) {
            // Show current line as hologram
            showLineHologram(npc, currentLine);
            
            // Check if this is the last line
            if (!npc.hasNextLine()) {
                // This is the last line, mark as complete for next interaction
                npc.markAsComplete(); // Move past all lines
            } else {
                // Advance to next line
                npc.advanceLine();
            }
        }
    }

    /**
     * Show a line as a hologram above the NPC
     */
    private void showLineHologram(HarryNPC npc, String line) {
        clearHologram(npc);
        
        Location hologramLoc = npc.getLocation().clone().add(0, 2.5, 0);
        ArmorStand hologram = (ArmorStand) hologramLoc.getWorld().spawnEntity(hologramLoc, EntityType.ARMOR_STAND);
        
        hologram.setVisible(false);
        hologram.setGravity(false);
        hologram.setCanPickupItems(false);
        
        // Format text with color codes and make it more visible
        String formattedLine = formatColorCodes(line);
        hologram.customName(net.kyori.adventure.text.Component.text(formattedLine));
        hologram.setCustomNameVisible(true);
        hologram.setMarker(true);
        
        npc.setHologramStand(hologram);
        
        // Don't auto-remove hologram - it will be cleared on next interaction
    }

    /**
     * Format color codes in text (& to §)
     */
    private String formatColorCodes(String text) {
        if (text == null) return "";
        // Replace & color codes with § color codes manually
        return text.replace("&0", "§0")
                  .replace("&1", "§1")
                  .replace("&2", "§2")
                  .replace("&3", "§3")
                  .replace("&4", "§4")
                  .replace("&5", "§5")
                  .replace("&6", "§6")
                  .replace("&7", "§7")
                  .replace("&8", "§8")
                  .replace("&9", "§9")
                  .replace("&a", "§a")
                  .replace("&b", "§b")
                  .replace("&c", "§c")
                  .replace("&d", "§d")
                  .replace("&e", "§e")
                  .replace("&f", "§f")
                  .replace("&k", "§k")
                  .replace("&l", "§l")
                  .replace("&m", "§m")
                  .replace("&n", "§n")
                  .replace("&o", "§o")
                  .replace("&r", "§r");
    }

    /**
     * Clear any existing hologram for the NPC
     */
    private void clearHologram(HarryNPC npc) {
        ArmorStand hologram = npc.getHologramStand();
        if (hologram != null && hologram.isValid()) {
            hologram.remove();
        }
        npc.setHologramStand(null);
    }

    /**
     * Trigger the completion sequence (firework, disappear, respawn)
     */
    private void triggerCompletionSequence(HarryNPC npc) {
        // First firework and disappear
        spawnWhiteFirework(npc.getLocation());
        despawnNPCEntity(npc);
        npc.setVisible(false);
        
        // Respawn after 5 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spawnWhiteFirework(npc.getLocation());
            npc.resetLines();
            npc.setVisible(true);
            spawnNPCEntity(npc);
        }, 100L); // 5 seconds
    }

    /**
     * Spawn a white firework at the specified location
     */
    private void spawnWhiteFirework(Location location) {
        Firework firework = location.getWorld().spawn(location, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        
        FireworkEffect effect = FireworkEffect.builder()
            .withColor(Color.WHITE)
            .with(FireworkEffect.Type.BURST)
            .trail(true)
            .flicker(true)
            .build();
        
        meta.addEffect(effect);
        meta.setPower(1);
        firework.setFireworkMeta(meta);
        firework.setSilent(true);
        
        // Detonate immediately
        Bukkit.getScheduler().runTaskLater(plugin, firework::detonate, 1L);
    }

    /**
     * Spawn the actual Villager entity for an NPC with glasses
     */
    private void spawnNPCEntity(HarryNPC npc) {
        if (npc.getVillager() != null && npc.getVillager().isValid()) {
            return; // Already spawned
        }

        Location loc = npc.getLocation();
        Villager villager = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        
        // Configure villager appearance - make him look like a teacher
        villager.setProfession(Villager.Profession.LIBRARIAN); // Librarian = teacher-like
        villager.setVillagerType(Villager.Type.PLAINS);
        villager.setVillagerLevel(5); // Max level for more distinguished look
        villager.setCanPickupItems(false);
        villager.setAI(false); // Prevent movement
        villager.setSilent(false); // Allow some sounds
        villager.customName(net.kyori.adventure.text.Component.text("§6§l" + npc.getName() + " §7(Profesor Harry)"));
        villager.setCustomNameVisible(true);
        
        // Make villager wear glasses (helmet)
        ItemStack glasses = new ItemStack(Material.LEATHER_HELMET);
        var glassesMeta = glasses.getItemMeta();
        if (glassesMeta != null) {
            glassesMeta.displayName(net.kyori.adventure.text.Component.text("§fLentes de Profesor"));
            // Set color to make it look like glasses frames
            if (glassesMeta instanceof org.bukkit.inventory.meta.LeatherArmorMeta) {
                ((org.bukkit.inventory.meta.LeatherArmorMeta) glassesMeta).setColor(org.bukkit.Color.fromRGB(139, 69, 19)); // Brown color for frames
            }
            glasses.setItemMeta(glassesMeta);
        }
        villager.getEquipment().setHelmet(glasses);
        villager.getEquipment().setHelmetDropChance(0.0f); // Never drop
        
        npc.setVillager(villager);
    }

    /**
     * Remove the Villager entity for an NPC
     */
    private void despawnNPCEntity(HarryNPC npc) {
        Villager villager = npc.getVillager();
        if (villager != null && villager.isValid()) {
            villager.remove();
        }
        npc.setVillager(null);
        
        clearHologram(npc);
    }

    /**
     * Find NPC by its Villager entity
     */
    private HarryNPC getNPCByVillager(Villager villager) {
        for (HarryNPC npc : npcs.values()) {
            if (npc.getVillager() != null && npc.getVillager().equals(villager)) {
                return npc;
            }
        }
        return null;
    }

    /**
     * Get all NPCs
     */
    public Map<String, HarryNPC> getAllNPCs() {
        ensureNPCsLoaded();
        resolveNPCWorlds();
        return new HashMap<>(npcs);
    }

    /**
     * Get NPC by ID
     */
    public HarryNPC getNPC(String npcId) {
        return npcs.get(npcId);
    }

    /**
     * Remove an NPC
     */
    public boolean removeNPC(String npcId, Player player) {
        HarryNPC npc = npcs.remove(npcId);
        if (npc == null) {
            player.sendMessage("§cNPC Harry '" + npcId + "' no encontrado.");
            return false;
        }

        despawnNPCEntity(npc);
        saveNPCs();
        
        player.sendMessage("§a✓ NPC Harry '" + npcId + "' eliminado exitosamente.");
        return true;
    }

    /**
     * Reset an NPC - despawn, clean holograms, and respawn fresh
     */
    public boolean resetNPC(String npcId, Player player) {
        HarryNPC npc = npcs.get(npcId);
        if (npc == null) {
            player.sendMessage("§cNPC Harry '" + npcId + "' no encontrado.");
            return false;
        }

        player.sendMessage("§e⟳ Reseteando Harry '" + npcId + "'...");
        
        // Step 1: Clean ALL duplicates at this location (villagers AND holograms)
        cleanupAllEntitiesAtLocation(npc.getLocation(), npc.getName());
        
        // Step 2: Reset NPC internal state
        npc.resetLines();
        npc.setVisible(true);
        npc.setVillager(null); // Clear any stale villager reference
        npc.setHologramStand(null); // Clear any stale hologram reference
        
        // Step 3: Spawn exactly ONE fresh NPC
        spawnNPCEntity(npc);
        
        player.sendMessage("§a✓ Harry '" + npcId + "' reseteado exitosamente.");
        player.sendMessage("§7Estado: duplicados eliminados, estado limpio, una entidad nueva spawneada.");
        return true;
    }

    public boolean resetAllNPCs(CommandSender sender) {
        ensureNPCsLoaded();
        resolveNPCWorlds();

        if (npcs.isEmpty()) {
            sender.sendMessage("§eNo hay Harry NPCs cargados para resetear.");
            return true;
        }

        int resetCount = 0;
        int pendingCount = 0;

        sender.sendMessage("§e⟳ Reseteando todos los Harry NPCs...");

        for (HarryNPC npc : npcs.values()) {
            Location location = npc.getLocation();
            if (location.getWorld() == null) {
                pendingCount++;
                continue;
            }

            cleanupAllEntitiesAtLocation(location, npc.getName());
            npc.resetLines();
            npc.setVisible(true);
            npc.setVillager(null);
            npc.setHologramStand(null);
            spawnNPCEntity(npc);
            resetCount++;
        }

        sender.sendMessage("§a✓ Harry NPCs reseteados: " + resetCount);
        if (pendingCount > 0) {
            sender.sendMessage("§eHarry NPCs pendientes por mundo no cargado: " + pendingCount);
        }

        return true;
    }

    /**
     * Clean up ALL entities (villagers and holograms) at a specific location
     */
    private void cleanupAllEntitiesAtLocation(Location location, String npcName) {
        if (location.getWorld() == null) return;
        
        java.util.concurrent.atomic.AtomicInteger villagersCleaned = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger hologramsCleaned = new java.util.concurrent.atomic.AtomicInteger(0);
        
        // Look for ALL entities in a 3x4x3 area around the location
        location.getWorld().getNearbyEntities(location, 3.0, 4.0, 3.0).forEach(entity -> {
            if (entity instanceof org.bukkit.entity.Villager) {
                org.bukkit.entity.Villager villager = (org.bukkit.entity.Villager) entity;
                // Check if it's THIS Harry NPC by name pattern
                if (villager.getCustomName() != null) {
                    String customName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(villager.customName());
                    if (customName.contains("Profesor Harry") || customName.contains(npcName)) {
                        villager.remove();
                        villagersCleaned.incrementAndGet();
                    }
                }
            } else if (entity instanceof org.bukkit.entity.ArmorStand) {
                org.bukkit.entity.ArmorStand armorStand = (org.bukkit.entity.ArmorStand) entity;
                // Check if it's a hologram (invisible armor stand with custom name)
                if (!armorStand.isVisible() && 
                    armorStand.getCustomName() != null && 
                    armorStand.isMarker() && 
                    !armorStand.hasGravity()) {
                    armorStand.remove();
                    hologramsCleaned.incrementAndGet();
                }
            }
        });
        
        if (villagersCleaned.get() > 0 || hologramsCleaned.get() > 0) {
            LOGGER.info("Reset cleanup: removed " + villagersCleaned.get() + " villagers and " + hologramsCleaned.get() + " holograms for Harry: " + npcName);
        }
    }

    /**
     * Clean up any holograms around a specific location
     */
    private void cleanupHologramsAroundLocation(Location location) {
        if (location.getWorld() == null) return;
        
        // Look for holograms in a 5x5x5 area around the location
        location.getWorld().getNearbyEntities(location, 5.0, 5.0, 5.0).forEach(entity -> {
            if (entity instanceof org.bukkit.entity.ArmorStand) {
                org.bukkit.entity.ArmorStand armorStand = (org.bukkit.entity.ArmorStand) entity;
                // Check if it's a hologram (invisible armor stand with custom name)
                if (!armorStand.isVisible() && 
                    armorStand.getCustomName() != null && 
                    armorStand.isMarker() && 
                    !armorStand.hasGravity()) {
                    LOGGER.info("Cleaning residual hologram at: " + armorStand.getLocation());
                    armorStand.remove();
                }
            }
        });
    }

    /**
     * Spawn all NPCs when plugin starts or worlds load
     */
    public void spawnAllNPCs() {
        ensureNPCsLoaded();
        resolveNPCWorlds();

        // Clear existing Harry NPCs in the world before spawning new ones
        cleanupExistingHarryNPCs();
        
        // Also clean any orphaned holograms across all worlds
        cleanupOrphanedHolograms();
        
        for (HarryNPC npc : npcs.values()) {
            if (npc.isVisible() && npc.getLocation().getWorld() != null) {
                spawnNPCEntity(npc);
            }
        }
    }

    /**
     * Clean up existing Harry NPCs in the world to prevent duplicates
     */
    private void cleanupExistingHarryNPCs() {
        LOGGER.info("Cleaning up existing Harry NPCs to prevent duplicates...");
        
        for (HarryNPC npc : npcs.values()) {
            Location loc = npc.getLocation();
            if (loc != null && loc.getWorld() != null) {
                // Remove any existing villagers and holograms at this location
                loc.getWorld().getNearbyEntities(loc, 3.0, 4.0, 3.0).forEach(entity -> {
                    if (entity instanceof org.bukkit.entity.Villager) {
                        org.bukkit.entity.Villager villager = (org.bukkit.entity.Villager) entity;
                        // Check if it's a Harry NPC by name pattern
                        if (villager.getCustomName() != null) {
                            String customName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(villager.customName());
                            if (customName.contains("Profesor Harry") || customName.contains(npc.getName())) {
                                LOGGER.info("Removing existing Harry NPC: " + customName);
                                villager.remove();
                            }
                        }
                    } else if (entity instanceof org.bukkit.entity.ArmorStand) {
                        org.bukkit.entity.ArmorStand armorStand = (org.bukkit.entity.ArmorStand) entity;
                        // Check if it's a hologram (invisible armor stand with custom name)
                        if (!armorStand.isVisible() && armorStand.getCustomName() != null && 
                            armorStand.isMarker() && !armorStand.hasGravity()) {
                            LOGGER.info("Removing stuck Harry message hologram");
                            armorStand.remove();
                        }
                    }
                });
            }
        }
    }

    /**
     * Clean up orphaned Harry message holograms across all worlds
     */
    private void cleanupOrphanedHolograms() {
        LOGGER.info("Cleaning up orphaned Harry message holograms...");
        
        // Check all loaded worlds for orphaned Harry holograms specifically
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            world.getEntitiesByClass(org.bukkit.entity.ArmorStand.class).forEach(armorStand -> {
                // Only target Harry message holograms by checking:
                // 1. Basic hologram characteristics
                // 2. Location near known Harry NPC positions (within 4 blocks vertically)
                // 3. NOT attached to an existing Harry NPC
                if (!armorStand.isVisible() && 
                    armorStand.getCustomName() != null && 
                    armorStand.isMarker() && 
                    !armorStand.hasGravity()) {
                    
                    // Check if this hologram is near any Harry NPC location
                    boolean isNearHarryLocation = false;
                    for (HarryNPC npc : npcs.values()) {
                        if (npc.getLocation().getWorld() != null && npc.getLocation().getWorld().equals(world)) {
                            double distance = npc.getLocation().distance(armorStand.getLocation());
                            // Only consider holograms within 4 blocks of a Harry NPC location
                            if (distance <= 4.0) {
                                isNearHarryLocation = true;
                                break;
                            }
                        }
                    }
                    
                    // Only remove if it's near a Harry location AND not attached to current NPC
                    if (isNearHarryLocation) {
                        boolean isAttachedToNPC = false;
                        for (HarryNPC npc : npcs.values()) {
                            if (npc.getHologramStand() != null && npc.getHologramStand().equals(armorStand)) {
                                isAttachedToNPC = true;
                                break;
                            }
                        }
                        
                        if (!isAttachedToNPC) {
                            LOGGER.info("Removing orphaned Harry message hologram at: " + armorStand.getLocation());
                            armorStand.remove();
                        }
                    }
                }
            });
        }
    }

    /**
     * Despawn all NPCs when plugin shuts down
     */
    public void despawnAllNPCs() {
        for (HarryNPC npc : npcs.values()) {
            despawnNPCEntity(npc);
        }
    }

    /**
     * Save NPCs to JSON file
     */
    private void saveNPCs() {
        try (FileWriter writer = new FileWriter(dataFile)) {
            gson.toJson(npcs, writer);
        } catch (IOException e) {
            LOGGER.severe("Failed to save Harry NPCs: " + e.getMessage());
        }
    }

    /**
     * Load NPCs from JSON file
     */
    private void loadNPCs() {
        if (!dataFile.exists()) {
            return; // No file to load from
        }

        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, HarryNPC>>(){}.getType();
            Map<String, HarryNPC> loadedNPCs = gson.fromJson(reader, type);
            
            if (loadedNPCs != null) {
                npcs.clear();
                for (Map.Entry<String, HarryNPC> entry : loadedNPCs.entrySet()) {
                    npcs.put(entry.getKey(), entry.getValue());
                }
                LOGGER.info("Loaded " + npcs.size() + " Harry NPCs");
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to load Harry NPCs: " + e.getMessage());
        }
    }

    private void ensureNPCsLoaded() {
        if (!npcs.isEmpty() || !dataFile.exists()) {
            return;
        }

        loadNPCs();
        resolveNPCWorlds();
    }

    private void resolveNPCWorlds() {
        for (HarryNPC npc : npcs.values()) {
            if (npc.getLocation().getWorld() != null || npc.getWorldName() == null) {
                continue;
            }

            org.bukkit.World world = Bukkit.getWorld(npc.getWorldName());
            if (world == null) {
                continue;
            }

            Location location = npc.getLocation();
            npc.setLocation(new Location(
                world,
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
            ));
        }
    }
}