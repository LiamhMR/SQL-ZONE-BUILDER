package com.seminario.plugin.util;

import java.lang.reflect.Type;
import java.util.Arrays;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.seminario.plugin.model.HarryNPC;

/**
 * Custom JSON adapter for HarryNPC to handle serialization without ArmorStand references
 */
public class HarryNPCAdapter implements JsonSerializer<HarryNPC>, JsonDeserializer<HarryNPC> {

    @Override
    public JsonElement serialize(HarryNPC src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        
        obj.addProperty("id", src.getId());
        obj.addProperty("name", src.getName());
        
        // Serialize location manually
        Location loc = src.getLocation();
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : src.getWorldName();
        obj.addProperty("world", worldName);
        obj.addProperty("x", loc.getX());
        obj.addProperty("y", loc.getY());
        obj.addProperty("z", loc.getZ());
        obj.addProperty("yaw", loc.getYaw());
        obj.addProperty("pitch", loc.getPitch());
        
        // Serialize lines
        obj.add("lines", context.serialize(src.getLines()));
        
        obj.addProperty("currentLineIndex", src.getCurrentLineIndex());
        obj.addProperty("visible", src.isVisible());
        
        return obj;
    }

    @Override
    public HarryNPC deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        
        String id = obj.get("id").getAsString();
        String name = obj.get("name").getAsString();
        
        // Deserialize location
        String worldName = obj.get("world").getAsString();
        World world = Bukkit.getWorld(worldName);

        Location location = new Location(
            world,
            obj.get("x").getAsDouble(),
            obj.get("y").getAsDouble(),
            obj.get("z").getAsDouble(),
            obj.get("yaw").getAsFloat(),
            obj.get("pitch").getAsFloat()
        );
        
        HarryNPC npc = new HarryNPC(id, name, location);
        npc.setWorldName(worldName);
        
        // Deserialize lines
        String[] linesArray = context.deserialize(obj.get("lines"), String[].class);
        npc.setLines(Arrays.asList(linesArray));
        
        // Set other properties
        if (obj.has("currentLineIndex")) {
            // Set current line index via reflection or add setter
            try {
                var field = HarryNPC.class.getDeclaredField("currentLineIndex");
                field.setAccessible(true);
                field.setInt(npc, obj.get("currentLineIndex").getAsInt());
            } catch (Exception ignored) {
                // If reflection fails, just start from 0
            }
        }
        
        if (obj.has("visible")) {
            npc.setVisible(obj.get("visible").getAsBoolean());
        }
        
        return npc;
    }
}