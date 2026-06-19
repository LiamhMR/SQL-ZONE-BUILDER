package com.seminario.plugin.model;

/**
 * Represents the different types of menus available in menu zones
 */
public enum MenuType {
    
    /**
     * Slide menu type - displays a sliding interface
     */
    SLIDE("slide"),
    
    /**
     * Fixed slide menu type - displays a permanent sliding presentation at a fixed location
     * Shares slides with a SLIDE zone but renders independently
     */
    FIXSLIDE("fixslide"),
    
    /**
     * Chest menu type - displays a chest-like inventory interface
     */
    CHEST("chest"),
    
    /**
     * Chest teleport menu type - displays teleport confirmation GUI
     */
    CHESTPORT("chestport"),
    
    /**
     * Laboratory menu type - enables SQL experimentation and learning
     */
    LABORATORY("laboratory"),

    /**
     * Laboratory2 menu type - enables SELECT-only SQL experimentation using the SQLBattle schema
     * (jugador, tipos_item, almacen, inventario, tipos_enemigo, enemigos)
     */
    LABORATORY2("laboratory2");
    
    private final String name;
    
    MenuType(String name) {
        this.name = name;
    }
    
    /**
     * Get the string representation of the menu type
     * @return The name of the menu type
     */
    public String getName() {
        return name;
    }
    
    /**
     * Get MenuType from string name
     * @param name The name to parse
     * @return The MenuType or null if not found
     */
    public static MenuType fromString(String name) {
        if (name == null) {
            return null;
        }
        
        for (MenuType type : MenuType.values()) {
            if (type.name.equalsIgnoreCase(name.trim())) {
                return type;
            }
        }
        return null;
    }
    
    /**
     * Check if a string represents a valid menu type
     * @param name The name to check
     * @return true if valid
     */
    public static boolean isValidType(String name) {
        return fromString(name) != null;
    }
    
    /**
     * Get all available menu type names
     * @return Array of available type names
     */
    public static String[] getAvailableTypes() {
        MenuType[] types = MenuType.values();
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            names[i] = types[i].getName();
        }
        return names;
    }
    
    @Override
    public String toString() {
        return name;
    }
}