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

    /** Custom categories that match modern Minecraft creative tabs more usefully. */
    private enum Category {
        FOOD, BREWING, COMBAT, TOOLS, REDSTONE,
        TRANSPORTATION, ORES, WOOD, STONE_NATURAL, MISC
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
     * Categorizes a material into sensible Minecraft-like groups.
     * Order of checks matters: more specific categories are tested first.
     */
    private Category getCategory(Material material) {
        String name = material.name();

        // ── Food ──────────────────────────────────────────────────────────────
        if (material.isEdible()
                || name.contains("_APPLE")
                || name.contains("_STEW")
                || name.contains("_SOUP")
                || name.equals("BREAD")
                || name.equals("CAKE")
                || name.equals("PUMPKIN_PIE")
                || name.equals("COOKIE")
                || name.equals("SUGAR")
                || name.equals("EGG")
                || name.equals("HONEY_BOTTLE")
                || name.equals("HONEYCOMB")
                || name.equals("SWEET_BERRIES")
                || name.equals("GLOW_BERRIES")
                || name.equals("CHORUS_FRUIT")
                || name.equals("DRIED_KELP")
                || name.equals("BEETROOT")
                || name.equals("CARROT")
                || name.equals("POTATO")
                || name.equals("POISONOUS_POTATO")
                || name.equals("MELON_SLICE")
                || name.equals("GLISTERING_MELON_SLICE")
                || name.equals("SPIDER_EYE")
                || name.equals("FERMENTED_SPIDER_EYE")
                || name.equals("ROTTEN_FLESH")
                || name.equals("BONE_MEAL")
                || name.contains("MUSHROOM") && !name.contains("BLOCK")) {
            return Category.FOOD;
        }

        // ── Brewing ───────────────────────────────────────────────────────────
        if (name.contains("POTION")
                || name.contains("GLASS_BOTTLE")
                || name.contains("WATER_BOTTLE")
                || name.equals("BLAZE_POWDER")
                || name.equals("MAGMA_CREAM")
                || name.equals("GHAST_TEAR")
                || name.equals("NETHER_WART")
                || name.equals("GLOWSTONE_DUST")
                || name.equals("REDSTONE")
                || name.equals("SUGAR")
                || name.equals("GLISTERING_MELON_SLICE")
                || name.equals("SPIDER_EYE")
                || name.equals("FERMENTED_SPIDER_EYE")
                || name.equals("RABBIT_FOOT")
                || name.equals("PHANTOM_MEMBRANE")
                || name.equals("TURTLE_HELMET")
                || name.equals("DRAGON_BREATH")
                || name.equals("GUNPOWDER")) {
            return Category.BREWING;
        }

        // ── Combat ────────────────────────────────────────────────────────────
        if (name.endsWith("_SWORD")
                || name.endsWith("_AXE") && !name.equals("STONE_AXE") && !name.equals("WOODEN_AXE") && !name.equals("GOLDEN_AXE") && !name.equals("IRON_AXE") && !name.equals("DIAMOND_AXE") && !name.equals("NETHERITE_AXE")
                || name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS")
                || name.equals("BOW")
                || name.equals("CROSSBOW")
                || name.equals("TRIDENT")
                || name.equals("SHIELD")
                || name.equals("TOTEM_OF_UNDYING")
                || name.equals("ARROW")
                || name.equals("SPECTRAL_ARROW")
                || name.contains("TIPPED_ARROW")
                || name.equals("WIND_CHARGE")
                || name.equals("MACE")
                || name.equals("BREEZE_ROD")) {
            return Category.COMBAT;
        }

        // ── Tools ─────────────────────────────────────────────────────────────
        if (name.endsWith("_PICKAXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE")
                || name.equals("SHEARS")
                || name.equals("FLINT_AND_STEEL")
                || name.equals("FISHING_ROD")
                || name.equals("CARROT_ON_A_STICK")
                || name.equals("WARPED_FUNGUS_ON_A_STICK")
                || name.equals("BRUSH")
                || name.equals("COMPASS")
                || name.equals("CLOCK")
                || name.equals("SPYGLASS")
                || name.equals("LEAD")
                || name.equals("NAME_TAG")
                || name.equals("SADDLE")
                || name.equals("BUCKET")
                || name.contains("_BUCKET")
                || name.equals("WATER_BUCKET")
                || name.equals("LAVA_BUCKET")
                || name.equals("POWDER_SNOW_BUCKET")
                || name.equals("MILK_BUCKET")
                || name.equals("TADPOLE_BUCKET")
                || name.equals("AXOLOTL_BUCKET")
                || name.equals("PUFFERFISH_BUCKET")
                || name.equals("SALMON_BUCKET")
                || name.equals("COD_BUCKET")
                || name.equals("TROPICAL_FISH_BUCKET")
                || name.equals("BOOK")
                || name.equals("WRITABLE_BOOK")
                || name.equals("WRITTEN_BOOK")
                || name.equals("MAP")
                || name.contains("_MAP")
                || name.equals("ENDER_PEARL")
                || name.equals("ENDER_EYE")
                || name.equals("FIRE_CHARGE")
                || name.equals("GO_HAMMER") // common custom item placeholder
                || name.equals("WOODEN_AXE")
                || name.equals("STONE_AXE")
                || name.equals("IRON_AXE")
                || name.equals("GOLDEN_AXE")
                || name.equals("DIAMOND_AXE")
                || name.equals("NETHERITE_AXE")) {
            return Category.TOOLS;
        }

        // ── Redstone ──────────────────────────────────────────────────────────
        if (name.equals("REDSTONE")
                || name.contains("REDSTONE_")
                || name.contains("_COMPARATOR")
                || name.contains("_REPEATER")
                || name.contains("PISTON")
                || name.contains("OBSERVER")
                || name.contains("DISPENSER")
                || name.contains("DROPPER")
                || name.contains("HOPPER")
                || name.contains("DAYLIGHT_DETECTOR")
                || name.contains("REDSTONE_LAMP")
                || name.contains("NOTE_BLOCK")
                || name.contains("TRIPWIRE")
                || name.equals("TARGET")
                || name.contains("SCULK_SENSOR")
                || name.contains("CALIBRATED_SKULK")
                || name.contains("LECTERN")
                || name.contains("LEVER")
                || name.contains("STONE_BUTTON")
                || name.contains("POLISHED_BLACKSTONE_BUTTON")
                || name.contains("WOODEN_BUTTON")
                || name.contains("STONE_PRESSURE_PLATE")
                || name.contains("POLISHED_BLACKSTONE_PRESSURE_PLATE")
                || name.contains("LIGHT_WEIGHTED_PRESSURE_PLATE")
                || name.contains("HEAVY_WEIGHTED_PRESSURE_PLATE")
                || name.contains("WOODEN_PRESSURE_PLATE")
                || name.contains("BAMBOO_PRESSURE_PLATE")
                || name.contains("CHERRY_PRESSURE_PLATE")
                || name.contains("MANGROVE_PRESSURE_PLATE")
                || name.contains("WARPED_PRESSURE_PLATE")
                || name.contains("CRIMSON_PRESSURE_PLATE")
                || name.contains("DARK_OAK_PRESSURE_PLATE")
                || name.contains("ACACIA_PRESSURE_PLATE")
                || name.contains("JUNGLE_PRESSURE_PLATE")
                || name.contains("BIRCH_PRESSURE_PLATE")
                || name.contains("SPRUCE_PRESSURE_PLATE")
                || name.contains("OAK_PRESSURE_PLATE")) {
            return Category.REDSTONE;
        }

        // ── Transportation ────────────────────────────────────────────────────
        if (name.contains("_BOAT")
                || name.contains("_CHEST_BOAT")
                || name.contains("MINECART")
                || name.contains("RAIL")
                || name.equals("SADDLE")
                || name.equals("CARROT_ON_A_STICK")
                || name.equals("WARPED_FUNGUS_ON_A_STICK")
                || name.equals("ELYTRA")) {
            return Category.TRANSPORTATION;
        }

        // ── Ores & minerals ───────────────────────────────────────────────────
        if (name.endsWith("_ORE")
                || name.endsWith("_INGOT")
                || name.endsWith("_NUGGET")
                || name.startsWith("RAW_")
                || name.equals("COAL")
                || name.equals("CHARCOAL")
                || name.equals("LAPIS_LAZULI")
                || name.equals("DIAMOND")
                || name.equals("EMERALD")
                || name.equals("QUARTZ")
                || name.equals("AMETHYST_SHARD")
                || name.equals("AMETHYST_CLUSTER")
                || name.equals("FLINT")
                || name.equals("NETHERITE_SCRAP")
                || name.equals("NETHERITE_INGOT")
                || name.equals("COPPER_BLOCK")
                || name.contains("CUT_COPPER")
                || name.contains("EXPOSED_COPPER")
                || name.contains("WEATHERED_COPPER")
                || name.contains("OXIDIZED_COPPER")
                || name.contains("WAXED_COPPER")) {
            return Category.ORES;
        }

        // ── Wood & wooden items ───────────────────────────────────────────────
        if ((name.contains("_LOG") || name.contains("_WOOD"))
                && !name.contains("STripped") // stripped is still wood, keep
                || name.contains("_PLANKS")
                || name.contains("_SAPLING")
                || name.contains("_LEAVES")
                || name.contains("_BUTTON") && !name.contains("STONE") && !name.contains("POLISHED")
                || name.contains("_DOOR") && !name.contains("IRON")
                || name.contains("_TRAPDOOR") && !name.contains("IRON")
                || name.contains("_FENCE") && !name.contains("NETHER_BRICK")
                || name.contains("_GATE")
                || name.contains("_SIGN")
                || name.contains("_HANGING_SIGN")
                || name.equals("STICK")
                || name.equals("BOWL")
                || name.equals("MANGROVE_ROOTS")
                || name.equals("BAMBOO")
                || name.equals("SUGAR_CANE")
                || name.equals("PAPER")
                || name.equals("OAK_SLAB") || name.equals("SPRUCE_SLAB") || name.equals("BIRCH_SLAB")
                || name.equals("JUNGLE_SLAB") || name.equals("ACACIA_SLAB") || name.equals("DARK_OAK_SLAB")
                || name.equals("MANGROVE_SLAB") || name.equals("CHERRY_SLAB") || name.equals("BAMBOO_SLAB")
                || name.equals("BAMBOO_MOSAIC_SLAB") || name.equals("CRIMSON_SLAB") || name.equals("WARPED_SLAB")
                || name.equals("OAK_STAIRS") || name.equals("SPRUCE_STAIRS") || name.equals("BIRCH_STAIRS")
                || name.equals("JUNGLE_STAIRS") || name.equals("ACACIA_STAIRS") || name.equals("DARK_OAK_STAIRS")
                || name.equals("MANGROVE_STAIRS") || name.equals("CHERRY_STAIRS") || name.equals("BAMBOO_STAIRS")
                || name.equals("BAMBOO_MOSAIC_STAIRS") || name.equals("CRIMSON_STAIRS") || name.equals("WARPED_STAIRS")) {
            return Category.WOOD;
        }

        // ── Stone & natural blocks ────────────────────────────────────────────
        if (name.contains("STONE")
                || name.contains("COBBLE")
                || name.contains("ANDESITE")
                || name.contains("DIORITE")
                || name.contains("GRANITE")
                || name.contains("DEEPSLATE")
                || name.contains("TUFF")
                || name.contains("CALCITE")
                || name.contains("DRIPSTONE")
                || name.contains("SANDSTONE")
                || name.contains("RED_SANDSTONE")
                || name.contains("TERRACOTTA")
                || name.contains("CONCRETE")
                || name.contains("BRICK")
                || name.contains("BLACKSTONE")
                || name.contains("BASALT")
                || name.contains("OBSIDIAN")
                || name.contains("NETHERRACK")
                || name.contains("END_STONE")
                || name.contains("PRISMARINE")
                || name.contains("QUARTZ_BLOCK")
                || name.contains("SMOOTH_QUARTZ")
                || name.equals("POINTED_DRIPSTONE")
                || name.equals("CLAY")
                || name.equals("GRAVEL")
                || name.equals("SAND")
                || name.equals("RED_SAND")
                || name.equals("DIRT")
                || name.equals("COARSE_DIRT")
                || name.equals("ROOTED_DIRT")
                || name.equals("PODZOL")
                || name.equals("MYCELIUM")
                || name.equals("GRASS_BLOCK")
                || name.equals("MOSS_BLOCK")
                || name.equals("MOSS_CARPET")
                || name.equals("FARMLAND")
                || name.equals("SNOW")
                || name.equals("SNOW_BLOCK")
                || name.equals("ICE")
                || name.equals("PACKED_ICE")
                || name.equals("BLUE_ICE")
                || name.equals("FROSTED_ICE")
                || name.equals("SOUL_SAND")
                || name.equals("SOUL_SOIL")
                || name.equals("MAGMA_BLOCK")
                || name.equals("BONE_BLOCK")
                || name.equals("HONEYCOMB_BLOCK")
                || name.equals("SLIME_BLOCK")
                || name.equals("HAY_BLOCK")
                || name.equals("MELON")
                || name.equals("PUMPKIN")
                || name.equals("CARVED_PUMPKIN")
                || name.equals("JACK_O_LANTERN")
                || name.contains("_AMETHYST_BUD")
                || name.equals("SMALL_AMETHYST_BUD")
                || name.equals("MEDIUM_AMETHYST_BUD")
                || name.equals("LARGE_AMETHYST_BUD")
                || name.equals("AMETHYST_CLUSTER")) {
            return Category.STONE_NATURAL;
        }

        return Category.MISC;
    }
}
