package me.matsubara.realisticvillagers.util;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.trading.TradingConfig;
import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Utility to prune villager inventories so they only keep items relevant to trading.
 */
public final class InventoryCleanupUtil {

    private InventoryCleanupUtil() {
    }

    /**
     * Check if cleanup is enabled
     * Cleanup is enabled if trading is enabled AND either keep-items or keep-categories list has items
     * Cleanup is disabled if both lists are empty
     */
    public static boolean isCleanupEnabled(@NotNull RealisticVillagers plugin) {
        TradingConfig config = plugin.getTradingConfig();
        if (config == null || !config.isEnabled()) {
            return false;
        }

        // Cleanup enabled if either list has items
        return !config.getKeepItems().isEmpty() || !config.getKeepCategories().isEmpty();
    }

    /**
     * Removes any item stacks from the villager inventory that are not useful for trading.
     *
     * @return number of removed stacks
     */
    public static int cleanupVillagerInventory(@NotNull IVillagerNPC npc, @NotNull RealisticVillagers plugin) {
        TradingConfig config = plugin.getTradingConfig();
        if (config == null) return 0;

        Villager villager = npc.bukkit() instanceof Villager bukkit ? bukkit : null;
        if (villager == null) return 0;

        Inventory inventory = villager.getInventory();
        Set<Material> keep = gatherKeepMaterials(villager, config);

        int removed = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() == Material.AIR) continue;

            if (!keep.contains(stack.getType())) {
                inventory.setItem(slot, null);
                removed++;

                if (plugin.getLogger().isLoggable(Level.FINE)) {
                    plugin.getLogger().fine("Removed " + stack.getAmount() + "x " + stack.getType()
                            + " from " + npc.getVillagerName() + " (not needed for trading)");
                }
            }
        }
        return removed;
    }

    private static Set<Material> gatherKeepMaterials(@NotNull Villager villager, @NotNull TradingConfig config) {
        Set<Material> keep = EnumSet.noneOf(Material.class);

        for (MerchantRecipe recipe : villager.getRecipes()) {
            keep.add(recipe.getResult().getType());
        }

        addConfiguredMaterials(config.getKeepItems(), keep);
        addCategoryMaterials(config.getKeepCategories(), keep);

        return keep;
    }

    private static void addConfiguredMaterials(@NotNull List<String> entries, @NotNull Set<Material> keep) {
        for (String entry : entries) {
            try {
                keep.add(Material.valueOf(entry.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static void addCategoryMaterials(@NotNull Set<String> categories, @NotNull Set<Material> keep) {
        for (String category : categories) {
            switch (category.toLowerCase()) {
                case "food" -> keep.addAll(FoodMaterials.ALL);
                case "weapons" -> keep.addAll(WeaponMaterials.ALL);
                case "armor" -> keep.addAll(ArmorMaterials.ALL);
                case "tools" -> keep.addAll(ToolMaterials.ALL);
                default -> {
                }
            }
        }
    }

    private static final class FoodMaterials {
        private static final Set<Material> ALL = new HashSet<>();

        static {
            ALL.add(Material.BREAD);
            ALL.add(Material.APPLE);
            ALL.add(Material.GOLDEN_APPLE);
            ALL.add(Material.ENCHANTED_GOLDEN_APPLE);
            ALL.add(Material.CARROT);
            ALL.add(Material.GOLDEN_CARROT);
            ALL.add(Material.POTATO);
            ALL.add(Material.BAKED_POTATO);
            ALL.add(Material.BEETROOT);
            ALL.add(Material.BEETROOT_SOUP);
            ALL.add(Material.MUSHROOM_STEW);
            ALL.add(Material.COOKED_BEEF);
            ALL.add(Material.COOKED_PORKCHOP);
            ALL.add(Material.COOKED_CHICKEN);
            ALL.add(Material.COOKED_MUTTON);
            ALL.add(Material.COOKED_RABBIT);
            ALL.add(Material.COOKED_COD);
            ALL.add(Material.COOKED_SALMON);
            ALL.add(Material.RABBIT_STEW);
            ALL.add(Material.HONEY_BOTTLE);
            ALL.add(Material.CAKE);
            ALL.add(Material.COOKIE);
            ALL.add(Material.MELON_SLICE);
            ALL.add(Material.PUMPKIN_PIE);
            ALL.add(Material.SWEET_BERRIES);
            ALL.add(Material.GLOW_BERRIES);
            ALL.add(Material.MILK_BUCKET);
        }
    }

    private static final class WeaponMaterials {
        private static final Set<Material> ALL = new HashSet<>();

        static {
            ALL.add(Material.WOODEN_SWORD);
            ALL.add(Material.STONE_SWORD);
            ALL.add(Material.IRON_SWORD);
            ALL.add(Material.GOLDEN_SWORD);
            ALL.add(Material.DIAMOND_SWORD);
            ALL.add(Material.NETHERITE_SWORD);
            ALL.add(Material.BOW);
            ALL.add(Material.CROSSBOW);
            ALL.add(Material.ARROW);
            ALL.add(Material.SPECTRAL_ARROW);
            ALL.add(Material.TIPPED_ARROW);
            ALL.add(Material.TRIDENT);
            ALL.add(Material.SHIELD);
        }
    }

    private static final class ArmorMaterials {
        private static final Set<Material> ALL = new HashSet<>();

        static {
            ALL.add(Material.LEATHER_HELMET);
            ALL.add(Material.CHAINMAIL_HELMET);
            ALL.add(Material.IRON_HELMET);
            ALL.add(Material.GOLDEN_HELMET);
            ALL.add(Material.DIAMOND_HELMET);
            ALL.add(Material.NETHERITE_HELMET);
            ALL.add(Material.TURTLE_HELMET);

            ALL.add(Material.LEATHER_CHESTPLATE);
            ALL.add(Material.CHAINMAIL_CHESTPLATE);
            ALL.add(Material.IRON_CHESTPLATE);
            ALL.add(Material.GOLDEN_CHESTPLATE);
            ALL.add(Material.DIAMOND_CHESTPLATE);
            ALL.add(Material.NETHERITE_CHESTPLATE);
            ALL.add(Material.ELYTRA);

            ALL.add(Material.LEATHER_LEGGINGS);
            ALL.add(Material.CHAINMAIL_LEGGINGS);
            ALL.add(Material.IRON_LEGGINGS);
            ALL.add(Material.GOLDEN_LEGGINGS);
            ALL.add(Material.DIAMOND_LEGGINGS);
            ALL.add(Material.NETHERITE_LEGGINGS);

            ALL.add(Material.LEATHER_BOOTS);
            ALL.add(Material.CHAINMAIL_BOOTS);
            ALL.add(Material.IRON_BOOTS);
            ALL.add(Material.GOLDEN_BOOTS);
            ALL.add(Material.DIAMOND_BOOTS);
            ALL.add(Material.NETHERITE_BOOTS);
        }
    }

    private static final class ToolMaterials {
        private static final Set<Material> ALL = new HashSet<>();

        static {
            addToolSet(Material.WOODEN_PICKAXE, Material.WOODEN_AXE, Material.WOODEN_SHOVEL, Material.WOODEN_HOE);
            addToolSet(Material.STONE_PICKAXE, Material.STONE_AXE, Material.STONE_SHOVEL, Material.STONE_HOE);
            addToolSet(Material.IRON_PICKAXE, Material.IRON_AXE, Material.IRON_SHOVEL, Material.IRON_HOE);
            addToolSet(Material.GOLDEN_PICKAXE, Material.GOLDEN_AXE, Material.GOLDEN_SHOVEL, Material.GOLDEN_HOE);
            addToolSet(Material.DIAMOND_PICKAXE, Material.DIAMOND_AXE, Material.DIAMOND_SHOVEL, Material.DIAMOND_HOE);
            addToolSet(Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE, Material.NETHERITE_SHOVEL, Material.NETHERITE_HOE);
            ALL.add(Material.SHEARS);
            ALL.add(Material.FISHING_ROD);
            ALL.add(Material.FLINT_AND_STEEL);
        }

        private static void addToolSet(Material pickaxe, Material axe, Material shovel, Material hoe) {
            ALL.add(pickaxe);
            ALL.add(axe);
            ALL.add(shovel);
            ALL.add(hoe);
        }
    }
}
