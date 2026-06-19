package com.seminario.plugin.listener;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import com.seminario.plugin.manager.SQLBattleManager;

/**
 * Enforces SQLBattle inventory restrictions:
 * <ul>
 *   <li>Players cannot pick up dropped items.</li>
 *   <li>Mobs killed inside a SQLBattle arena drop nothing.</li>
 *   <li>Slots 7 and 8 are system-reserved (prewaveStart / prewaveLeave items).</li>
 *   <li>Slots 9-35 (main inventory bag) are fully blocked.</li>
 *   <li>Offhand swap (F key) is blocked during battle.</li>
 * </ul>
 */
public class BattleInventoryListener implements Listener {

    // Bukkit slot constants for the player inventory view
    /** First "bag" row slot (not hotbar, not armour). */
    private static final int MAIN_INV_MIN = 9;
    /** Last "bag" row slot. */
    private static final int MAIN_INV_MAX = 35;
    /** Hotbar slots reserved for system items (start-wave / leave). */
    private static final int SYSTEM_SLOT_1 = 7;
    private static final int SYSTEM_SLOT_2 = 8;

    private final SQLBattleManager sqlBattleManager;

    public BattleInventoryListener(SQLBattleManager sqlBattleManager) {
        this.sqlBattleManager = sqlBattleManager;
    }

    // -------------------------------------------------------------------------
    // Item pickup – belt-and-suspenders guard (setCanPickupItems already false)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;
        if (sqlBattleManager.isPlayerInBattleArena(player)) {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Entity death – suppress drops in battle arenas
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        // Only suppress drops for non-player mobs that carry the SQL Battle role tag
        if (entity instanceof Player) return;

        String role = entity.getPersistentDataContainer()
            .get(new org.bukkit.NamespacedKey("seminario", "sql_battle_role"),
                PersistentDataType.STRING);
        if ("enemy".equalsIgnoreCase(role)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    // -------------------------------------------------------------------------
    // Inventory click – protect reserved / blocked slots
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!sqlBattleManager.isPlayerInBattleArena(player)) return;

        int rawSlot = event.getRawSlot();
        int slot    = event.getSlot();
        InventoryType.SlotType slotType = event.getSlotType();

        // Always allow crafting output and outside clicks to pass through
        if (slotType == InventoryType.SlotType.OUTSIDE) return;

        // Block any interaction with system-reserved hotbar slots (7 and 8)
        if (slot == SYSTEM_SLOT_1 || slot == SYSTEM_SLOT_2) {
            event.setCancelled(true);
            return;
        }

        // Block the main inventory bag (slots 9-35) entirely
        if (rawSlot >= MAIN_INV_MIN && rawSlot <= MAIN_INV_MAX) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Solo puedes usar la barra de acceso rápido (hotbar) en el SQL Battle.");
            return;
        }

        // If clicking in the player's own inventory (not a chest/container), also guard bag slots
        if (event.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
            if (slot >= MAIN_INV_MIN && slot <= MAIN_INV_MAX) {
                event.setCancelled(true);
                return;
            }
        }

        // Shift-click into bag: cancel
        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            ItemStack cursor = event.getCurrentItem();
            if (cursor != null && cursor.getType() != Material.AIR) {
                // If the target would land in the bag, cancel
                // We cancel all shift-clicks during battle to be safe
                event.setCancelled(true);
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // F key (offhand swap) – block during battle to keep hotbar items in place
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (sqlBattleManager.isPlayerInBattleArena(player)) {
            // Allow swapping only if the offhand slot (slot 40) holds a shield (battle item).
            // In practice, just block all swaps to keep inventory sync simple.
            event.setCancelled(true);
        }
    }
}
