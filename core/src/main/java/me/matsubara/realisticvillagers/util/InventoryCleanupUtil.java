package me.matsubara.realisticvillagers.util;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Utility class for cleaning up villager inventories by removing ALL items they cannot use.
 * Items are ONLY kept if they are:
 * - Needed for their trades (what they sell) - always kept
 * - Specified in the keep-items configuration
 * - Match keep-categories (food, weapons, armor, tools) if configured
 * 
 * Everything else is removed completely.
 */
public class InventoryCleanupUtil {
    
    /**
     * Cleans up a villager's inventory by removing ALL items they cannot use.
     * Only keeps items needed for trades and items specified in configuration.
     * 
     * @param npc The villager NPC to clean
     * @param plugin The plugin instance
     * @return The number of item stacks removed
     */
    public static int cleanupVillagerInventory(@NotNull IVillagerNPC npc, @NotNull RealisticVillagers plugin) {
        Villager villager = (Villager) npc.bukkit();
        Inventory inventory = villager.getInventory();
        
        // Get all materials this villager should keep
        Set<Material> keepMaterials = new HashSet<>();
        
        // ALWAYS keep items needed for their trades (what they sell)
        for (MerchantRecipe recipe : villager.getRecipes()) {
            keepMaterials.add(recipe.getResult().getType());
        }
        
        // Add configured keep items
        addConfiguredKeepItems(plugin, keepMaterials);
        
        // Add items based on keep categories
        addCategoryItems(plugin, keepMaterials);
        
        int removedCount = 0;
        ItemStack[] contents = inventory.getContents();
        
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) continue;
            
            // Remove if NOT in keep list
            if (!keepMaterials.contains(item.getType())) {
                // Remove useless item completely
                inventory.setItem(i, null);
                removedCount++;
                
                if (plugin.getLogger().isLoggable(Level.FINE)) {
                    plugin.getLogger().fine(String.format("Removed %dx %s from %s's inventory (not in keep list)", 
                        item.getAmount(), item.getType(), npc.getVillagerName()));
                }
            }
        }
        
        return removedCount;
    }
    
    /**
     * Adds items from configuration that should always be kept.
     * 
     * @param plugin The plugin instance
     * @param materials The set to add materials to
     */
    private static void addConfiguredKeepItems(@NotNull RealisticVillagers plugin, @NotNull Set<Material> materials) {
        List<String> keepItems = plugin.getTradingConfig().getKeepItems();
        for (String itemName : keepItems) {
            try {
                Material material = Material.valueOf(itemName.toUpperCase());
                materials.add(material);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in keep-items: " + itemName);
            }
        }
    }
    
    /**
     * Adds items based on configured categories (food, weapons, armor, tools).
     * 
     * @param plugin The plugin instance
     * @param materials The set to add materials to
     */
    private static void addCategoryItems(@NotNull RealisticVillagers plugin, @NotNull Set<Material> materials) {
        Set<String> categories = plugin.getTradingConfig().getKeepCategories();
        
        for (String category : categories) {
            switch (category.toLowerCase()) {
                case "food" -> addFoodItems(materials);
                case "weapons" -> addWeaponItems(materials);
                case "armor" -> addArmorItems(materials);
                case "tools" -> addToolItems(materials);
                default -> plugin.getLogger().warning("Unknown keep-category: " + category);
            }
        }
    }
    
    private static void addFoodItems(@NotNull Set<Material> materials) {
        // All food items
        materials.add(Material.BREAD);
        materials.add(Material.APPLE);
        materials.add(Material.GOLDEN_APPLE);
        materials.add(Material.ENCHANTED_GOLDEN_APPLE);
        materials.add(Material.CARROT);
        materials.add(Material.GOLDEN_CARROT);
        materials.add(Material.POTATO);
        materials.add(Material.BAKED_POTATO);
        materials.add(Material.POISONOUS_POTATO);
        materials.add(Material.BEETROOT);
        materials.add(Material.BEETROOT_SOUP);
        materials.add(Material.MUSHROOM_STEW);
        materials.add(Material.COOKED_BEEF);
        materials.add(Material.BEEF);
        materials.add(Material.COOKED_PORKCHOP);
        materials.add(Material.PORKCHOP);
        materials.add(Material.COOKED_MUTTON);
        materials.add(Material.MUTTON);
        materials.add(Material.COOKED_CHICKEN);
        materials.add(Material.CHICKEN);
        materials.add(Material.COOKED_RABBIT);
        materials.add(Material.RABBIT);
        materials.add(Material.RABBIT_STEW);
        materials.add(Material.COOKED_COD);
        materials.add(Material.COD);
        materials.add(Material.COOKED_SALMON);
        materials.add(Material.SALMON);
        materials.add(Material.TROPICAL_FISH);
        materials.add(Material.PUFFERFISH);
        materials.add(Material.COOKIE);
        materials.add(Material.MELON_SLICE);
        materials.add(Material.PUMPKIN_PIE);
        materials.add(Material.CAKE);
        materials.add(Material.SWEET_BERRIES);
        materials.add(Material.GLOW_BERRIES);
        materials.add(Material.HONEY_BOTTLE);
        materials.add(Material.MILK_BUCKET);
    }
    
    private static void addWeaponItems(@NotNull Set<Material> materials) {
        // Swords
        materials.add(Material.WOODEN_SWORD);
        materials.add(Material.STONE_SWORD);
        materials.add(Material.IRON_SWORD);
        materials.add(Material.GOLDEN_SWORD);
        materials.add(Material.DIAMOND_SWORD);
        materials.add(Material.NETHERITE_SWORD);
        
        // Ranged
        materials.add(Material.BOW);
        materials.add(Material.CROSSBOW);
        materials.add(Material.ARROW);
        materials.add(Material.SPECTRAL_ARROW);
        materials.add(Material.TIPPED_ARROW);
        
        // Other
        materials.add(Material.TRIDENT);
        materials.add(Material.SHIELD);
    }
    
    private static void addArmorItems(@NotNull Set<Material> materials) {
        // Helmets
        materials.add(Material.LEATHER_HELMET);
        materials.add(Material.CHAINMAIL_HELMET);
        materials.add(Material.IRON_HELMET);
        materials.add(Material.GOLDEN_HELMET);
        materials.add(Material.DIAMOND_HELMET);
        materials.add(Material.NETHERITE_HELMET);
        materials.add(Material.TURTLE_HELMET);
        
        // Chestplates
        materials.add(Material.LEATHER_CHESTPLATE);
        materials.add(Material.CHAINMAIL_CHESTPLATE);
        materials.add(Material.IRON_CHESTPLATE);
        materials.add(Material.GOLDEN_CHESTPLATE);
        materials.add(Material.DIAMOND_CHESTPLATE);
        materials.add(Material.NETHERITE_CHESTPLATE);
        materials.add(Material.ELYTRA);
        
        // Leggings
        materials.add(Material.LEATHER_LEGGINGS);
        materials.add(Material.CHAINMAIL_LEGGINGS);
        materials.add(Material.IRON_LEGGINGS);
        materials.add(Material.GOLDEN_LEGGINGS);
        materials.add(Material.DIAMOND_LEGGINGS);
        materials.add(Material.NETHERITE_LEGGINGS);
        
        // Boots
        materials.add(Material.LEATHER_BOOTS);
        materials.add(Material.CHAINMAIL_BOOTS);
        materials.add(Material.IRON_BOOTS);
        materials.add(Material.GOLDEN_BOOTS);
        materials.add(Material.DIAMOND_BOOTS);
        materials.add(Material.NETHERITE_BOOTS);
    }
    
    private static void addToolItems(@NotNull Set<Material> materials) {
        // Pickaxes
        materials.add(Material.WOODEN_PICKAXE);
        materials.add(Material.STONE_PICKAXE);
        materials.add(Material.IRON_PICKAXE);
        materials.add(Material.GOLDEN_PICKAXE);
        materials.add(Material.DIAMOND_PICKAXE);
        materials.add(Material.NETHERITE_PICKAXE);
        
        // Axes
        materials.add(Material.WOODEN_AXE);
        materials.add(Material.STONE_AXE);
        materials.add(Material.IRON_AXE);
        materials.add(Material.GOLDEN_AXE);
        materials.add(Material.DIAMOND_AXE);
        materials.add(Material.NETHERITE_AXE);
        
        // Shovels
        materials.add(Material.WOODEN_SHOVEL);
        materials.add(Material.STONE_SHOVEL);
        materials.add(Material.IRON_SHOVEL);
        materials.add(Material.GOLDEN_SHOVEL);
        materials.add(Material.DIAMOND_SHOVEL);
        materials.add(Material.NETHERITE_SHOVEL);
        
        // Hoes
        materials.add(Material.WOODEN_HOE);
        materials.add(Material.STONE_HOE);
        materials.add(Material.IRON_HOE);
        materials.add(Material.GOLDEN_HOE);
        materials.add(Material.DIAMOND_HOE);
        materials.add(Material.NETHERITE_HOE);
        
        // Other tools
        materials.add(Material.FISHING_ROD);
        materials.add(Material.FLINT_AND_STEEL);
        materials.add(Material.SHEARS);
        materials.add(Material.COMPASS);
        materials.add(Material.CLOCK);
        materials.add(Material.LEAD);
        materials.add(Material.NAME_TAG);
        materials.add(Material.BUCKET);
        materials.add(Material.WATER_BUCKET);
        materials.add(Material.LAVA_BUCKET);
    }
    
    /**
     * Checks if inventory cleanup is enabled in the configuration.
     * 
     * @param plugin The plugin instance
     * @return true if cleanup is enabled
     */
    public static boolean isCleanupEnabled(@NotNull RealisticVillagers plugin) {
        return plugin.getTradingConfig().isEnabled() && 
               plugin.getTradingConfig().isCleanupUselessItems();
    }
}