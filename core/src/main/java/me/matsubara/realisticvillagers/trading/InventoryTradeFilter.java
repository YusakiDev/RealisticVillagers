package me.matsubara.realisticvillagers.trading;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.TradingConfig;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Filters villager trades based on their inventory contents.
 * This creates a more realistic trading system where villagers
 * can only trade items they actually possess.
 */
public class InventoryTradeFilter {
    
    private final RealisticVillagers plugin;
    private final TradingConfig config;
    
    public InventoryTradeFilter(@NotNull RealisticVillagers plugin, @NotNull TradingConfig config) {
        this.plugin = plugin;
        this.config = config;
    }
    
    /**
     * Filters merchant recipes based on villager's inventory.
     * Trades without sufficient stock are disabled (max uses = uses).
     * 
     * @param villager The villager NPC
     * @return List of filtered merchant recipes
     */
    @NotNull
    public List<MerchantRecipe> filterTrades(@NotNull IVillagerNPC villager) {
        if (!config.isEnabled()) {
            // System disabled, return original trades
            Villager bukkit = (Villager) villager.bukkit();
            return new ArrayList<>(bukkit.getRecipes());
        }
        
        Villager bukkitVillager = (Villager) villager.bukkit();
        
        // Check if profession is exempt
        if (config.isProfessionExempt(bukkitVillager.getProfession())) {
            return new ArrayList<>(bukkitVillager.getRecipes());
        }
        
        List<MerchantRecipe> filteredRecipes = new ArrayList<>();
        
        for (MerchantRecipe originalRecipe : bukkitVillager.getRecipes()) {
            MerchantRecipe filteredRecipe = copyRecipe(originalRecipe);
            
            // Check if villager has stock for this trade
            if (!hasStockForTrade(bukkitVillager, originalRecipe)) {
                // Disable the trade by setting uses = maxUses
                filteredRecipe = disableRecipe(filteredRecipe);
            }
            
            filteredRecipes.add(filteredRecipe);
        }
        
        return filteredRecipes;
    }
    
    /**
     * Checks if a villager has sufficient inventory stock for a trade.
     * 
     * @param villager The villager to check
     * @param recipe The trade recipe to validate
     * @return true if villager has enough items, false otherwise
     */
    private boolean hasStockForTrade(@NotNull Villager villager, @NotNull MerchantRecipe recipe) {
        ItemStack result = recipe.getResult();
        int requiredAmount = (int) Math.ceil(result.getAmount() * config.getRequiredStockMultiplier());
        
        // Count matching items in villager's inventory
        int availableAmount = 0;
        for (ItemStack item : villager.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            
            if (itemMatches(item, result)) {
                availableAmount += item.getAmount();
                if (availableAmount >= requiredAmount) {
                    return true; // Early exit when we have enough
                }
            }
        }
        
        return availableAmount >= requiredAmount;
    }
    
    /**
     * Checks if two items match based on configuration.
     * 
     * @param inventoryItem Item in villager's inventory
     * @param tradeItem Item from the trade recipe
     * @return true if items match according to config settings
     */
    private boolean itemMatches(@NotNull ItemStack inventoryItem, @NotNull ItemStack tradeItem) {
        // First check material type
        if (inventoryItem.getType() != tradeItem.getType()) {
            return false;
        }
        
        // If not checking exact items, material match is enough
        if (!config.isCheckExactItem()) {
            return true;
        }
        
        // For exact matching, compare item meta
        return inventoryItem.isSimilar(tradeItem);
    }
    
    /**
     * Creates a copy of a merchant recipe.
     * Bukkit's MerchantRecipe doesn't have a proper clone method.
     * 
     * @param original The recipe to copy
     * @return A new instance with the same properties
     */
    @NotNull
    private MerchantRecipe copyRecipe(@NotNull MerchantRecipe original) {
        MerchantRecipe copy = new MerchantRecipe(
            original.getResult(),
            original.getUses(),
            original.getMaxUses(),
            original.hasExperienceReward(),
            original.getVillagerExperience(),
            original.getPriceMultiplier(),
            original.getDemand(),
            original.getSpecialPrice()
        );
        
        // Copy ingredients
        List<ItemStack> ingredients = original.getIngredients();
        copy.setIngredients(ingredients);
        
        return copy;
    }
    
    /**
     * Disables a merchant recipe by setting uses = maxUses.
     * Optionally adds "Out of Stock" to the result item's lore.
     * 
     * @param recipe The recipe to disable
     * @return The disabled recipe
     */
    @NotNull
    private MerchantRecipe disableRecipe(@NotNull MerchantRecipe recipe) {
        // Create disabled recipe with uses = maxUses
        MerchantRecipe disabled = new MerchantRecipe(
            recipe.getResult(),
            recipe.getMaxUses(), // Set uses = maxUses to disable
            recipe.getMaxUses(),
            recipe.hasExperienceReward(),
            recipe.getVillagerExperience(),
            recipe.getPriceMultiplier(),
            recipe.getDemand(),
            recipe.getSpecialPrice()
        );
        
        disabled.setIngredients(recipe.getIngredients());
        
        // Add "Out of Stock" to lore if configured
        if (config.isShowDisabledReason()) {
            ItemStack result = disabled.getResult().clone();
            ItemMeta meta = result.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(PluginUtils.translate(config.getOutOfStockMessage()));
                meta.setLore(lore);
                result.setItemMeta(meta);
                
                // Create new recipe with modified result
                disabled = new MerchantRecipe(
                    result,
                    recipe.getMaxUses(),
                    recipe.getMaxUses(),
                    recipe.hasExperienceReward(),
                    recipe.getVillagerExperience(),
                    recipe.getPriceMultiplier(),
                    recipe.getDemand(),
                    recipe.getSpecialPrice()
                );
                disabled.setIngredients(recipe.getIngredients());
            }
        }
        
        return disabled;
    }
    
    /**
     * Gets a debug string showing villager's inventory contents.
     * Useful for troubleshooting why trades might be disabled.
     * 
     * @param villager The villager to inspect
     * @return String representation of inventory contents
     */
    @NotNull
    public String getInventoryDebugInfo(@NotNull Villager villager) {
        return java.util.Arrays.stream(villager.getInventory().getContents())
            .filter(item -> item != null && item.getType() != Material.AIR)
            .map(item -> item.getType().name() + " x" + item.getAmount())
            .collect(Collectors.joining(", "));
    }
}