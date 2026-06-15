package com.example.invsort;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InvSortPlugin extends JavaPlugin implements Listener {

    // Player inventory slot ranges (within PlayerInventory)
    private static final int HOTBAR_FIRST = 0;
    private static final int HOTBAR_LAST  = 8;
    private static final int MAIN_FIRST   = 9;
    private static final int MAIN_LAST    = 35;

    /** Minecraft creative-mode categories used to group sorted items. */
    private enum Category {
        BUILDING_BLOCKS, DECORATIONS, REDSTONE, TRANSPORTATION,
        MISC, FOOD, TOOLS, COMBAT, BREWING
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("InvSort enabled.");
        getLogger().info("  • Double-click a hotbar slot        → sort hotbar only");
        getLogger().info("  • Double-click a main inventory slot → sort main inventory only");
        getLogger().info("  • Double-click inside a chest        → sort that container");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getClick() != ClickType.DOUBLE_CLICK) return;

        // If the player is holding an item on the cursor, bail out to avoid duplication issues
        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) return;

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) return;

        // ── Case 1: Click happened inside the player's own inventory ────────────
        if (clickedInv instanceof PlayerInventory) {
            event.setCancelled(true);
            int slot = event.getSlot();

            if (slot >= HOTBAR_FIRST && slot <= HOTBAR_LAST) {
                // Clicked in hotbar → sort hotbar only
                sortRange(player.getInventory(), HOTBAR_FIRST, HOTBAR_LAST);
            } else if (slot >= MAIN_FIRST && slot <= MAIN_LAST) {
                // Clicked in main inventory → sort main inventory only
                sortRange(player.getInventory(), MAIN_FIRST, MAIN_LAST);
            }
            return;
        }

        // ── Case 2: Middle-click inside a chest / external container ────────────
        event.setCancelled(true);
        sortInventoryFull(clickedInv);
    }

    // ── Sorting helpers ──────────────────────────────────────────────────────────

    /**
     * Sorts a contiguous range of slots [first, last] within the given inventory.
     */
    private void sortRange(Inventory inv, int first, int last) {
        // 1. Collect all non-empty items in the range
        List<ItemStack> items = new ArrayList<>();
        for (int i = first; i <= last; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                items.add(item);
            }
        }

        // 2. Merge identical stacks
        List<ItemStack> sorted = mergeAndSort(items);

        // 3. Clear range and place sorted items back
        for (int i = first; i <= last; i++) {
            inv.setItem(i, null);
        }
        for (int i = 0; i < sorted.size() && (first + i) <= last; i++) {
            inv.setItem(first + i, sorted.get(i));
        }
    }

    /**
     * Sorts all slots of an external inventory (e.g. a chest).
     */
    private void sortInventoryFull(Inventory inv) {
        int size = inv.getSize();

        // 1. Collect non-empty items
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                items.add(item);
            }
        }

        // 2. Merge and sort
        List<ItemStack> sorted = mergeAndSort(items);

        // 3. Clear and refill
        for (int i = 0; i < size; i++) {
            inv.setItem(i, null);
        }
        for (int i = 0; i < sorted.size() && i < size; i++) {
            inv.setItem(i, sorted.get(i));
        }
    }

    /**
     * Merges identical stacks, splits back to max-stack-size, and sorts
     * alphabetically by material name then by amount (largest first).
     */
    private List<ItemStack> mergeAndSort(List<ItemStack> items) {
        // Merge
        Map<String, ItemStack> merged = new LinkedHashMap<>();
        for (ItemStack item : items) {
            String key = itemKey(item);
            ItemStack existing = merged.get(key);
            if (existing != null) {
                existing.setAmount(existing.getAmount() + item.getAmount());
            } else {
                merged.put(key, item.clone());
            }
        }

        // Split back into legal stacks
        List<ItemStack> finalItems = new ArrayList<>();
        for (ItemStack item : merged.values()) {
            int maxStack = item.getMaxStackSize();
            int amount = item.getAmount();
            while (amount > 0) {
                int stackAmount = Math.min(amount, maxStack);
                ItemStack stack = item.clone();
                stack.setAmount(stackAmount);
                finalItems.add(stack);
                amount -= stackAmount;
            }
        }

        // Sort: category first, then alphabetical by type, then largest stacks first
        finalItems.sort(
                Comparator.comparing((ItemStack i) -> getCategory(i.getType()))
                          .thenComparing((ItemStack i) -> i.getType().name())
                          .thenComparing(Comparator.comparingInt(ItemStack::getAmount).reversed())
        );

        return finalItems;
    }

    /**
     * Builds a key that groups items only when they are truly identical
     * (same material + same item meta including enchantments, name, lore, etc.).
     */
    private String itemKey(ItemStack item) {
        StringBuilder sb = new StringBuilder(item.getType().name());
        if (item.hasItemMeta()) {
            sb.append('|').append(item.getItemMeta());
        }
        return sb.toString();
    }

    /**
     * Categorizes a material using Minecraft's built-in creative-mode category.
     * Falls back to MISC for materials without a category.
     */
    @SuppressWarnings({"deprecation", "removal"})
    private Category getCategory(Material material) {
        org.bukkit.inventory.CreativeCategory mcCategory = material.getCreativeCategory();
        if (mcCategory == null) {
            return Category.MISC;
        }
        return switch (mcCategory) {
            case BUILDING_BLOCKS -> Category.BUILDING_BLOCKS;
            case DECORATIONS -> Category.DECORATIONS;
            case REDSTONE -> Category.REDSTONE;
            case TRANSPORTATION -> Category.TRANSPORTATION;
            case MISC -> Category.MISC;
            case FOOD -> Category.FOOD;
            case TOOLS -> Category.TOOLS;
            case COMBAT -> Category.COMBAT;
            case BREWING -> Category.BREWING;
        };
    }
}
