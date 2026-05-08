package com.seminario.plugin.model;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Villager;

/**
 * Model class representing a Harry NPC with teaching dialogue
 */
public class HarryNPC {
    private String id;
    private String name;
    private Location location;
    private String worldName;
    private List<String> lines;
    private int currentLineIndex;
    private boolean isVisible;
    private Villager villager; // The actual villager NPC
    private ArmorStand hologramStand; // For displaying current line

    public HarryNPC(String id, String name, Location location) {
        this.id = id;
        this.name = name;
        this.location = location.clone();
        this.worldName = location.getWorld() != null ? location.getWorld().getName() : null;
        this.lines = new ArrayList<>();
        this.currentLineIndex = 0;
        this.isVisible = true;
        this.villager = null;
        this.hologramStand = null;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Location getLocation() {
        return location.clone();
    }

    public void setLocation(Location location) {
        this.location = location.clone();
        if (location.getWorld() != null) {
            this.worldName = location.getWorld().getName();
        }
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public List<String> getLines() {
        return new ArrayList<>(lines);
    }

    public void setLines(List<String> lines) {
        this.lines = new ArrayList<>(lines);
    }

    public void addLine(String line) {
        this.lines.add(line);
    }

    public boolean editLine(int index, String newText) {
        if (index < 0 || index >= lines.size()) {
            return false;
        }
        lines.set(index, newText);
        return true;
    }

    public String getCurrentLine() {
        if (currentLineIndex >= 0 && currentLineIndex < lines.size()) {
            return lines.get(currentLineIndex);
        }
        return null;
    }

    public boolean hasNextLine() {
        return currentLineIndex < lines.size() - 1;
    }

    public boolean advanceLine() {
        if (hasNextLine()) {
            currentLineIndex++;
            return true;
        }
        return false;
    }

    public void resetLines() {
        currentLineIndex = 0;
    }

    public void markAsComplete() {
        currentLineIndex = lines.size();
    }

    public int getCurrentLineIndex() {
        return currentLineIndex;
    }

    public int getTotalLines() {
        return lines.size();
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void setVisible(boolean visible) {
        this.isVisible = visible;
    }

    public Villager getVillager() {
        return villager;
    }

    public void setVillager(Villager villager) {
        this.villager = villager;
    }

    public ArmorStand getHologramStand() {
        return hologramStand;
    }

    public void setHologramStand(ArmorStand hologramStand) {
        this.hologramStand = hologramStand;
    }

    public boolean isComplete() {
        return currentLineIndex >= lines.size();
    }

    @Override
    public String toString() {
        return "HarryNPC{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", location=" + (worldName != null ? worldName : "unknown") + ":" + 
                location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ() +
                ", lines=" + lines.size() +
                ", currentLine=" + currentLineIndex +
                ", visible=" + isVisible +
                '}';
    }
}