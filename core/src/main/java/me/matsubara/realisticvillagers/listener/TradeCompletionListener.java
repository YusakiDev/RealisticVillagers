package me.matsubara.realisticvillagers.listener;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.WorkHungerConfig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;

/**
 * Handles realistic trade inventory exchanges.
 * When trades complete, villagers lose the items they pay (emeralds)
 * and gain the items players sell to them (bread, etc.).
 */
public class TradeCompletionListener implements Listener {
    
    private final RealisticVillagers plugin;
    
    public TradeCompletionListener(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        // Only handle merchant inventory clicks
        if (!(event.getInventory() instanceof MerchantInventory merchantInventory)) {
            return;
        }
        
        // Only handle players
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        // Check if realistic trade inventory is enabled
        if (!WorkHungerConfig.REALISTIC_TRADE_INVENTORY_ENABLED.asBool()) {
            return;
        }
        
        // Get the villager
        if (!(merchantInventory.getHolder() instanceof Villager villager)) {
            return;
        }
        
        // Convert to IVillagerNPC
        IVillagerNPC npc = plugin.getConverter().getNPC(villager).orElse(null);
        if (npc == null) {
            return;
        }
        
        // Check if this is a trade completion (clicking result slot with valid trade)
        if (event.getSlot() != 2) { // Result slot is always slot 2 in merchant inventory
            return;
        }
        
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) {
            return;
        }
        
        // Get the selected recipe
        MerchantRecipe selectedRecipe = merchantInventory.getSelectedRecipe();
        if (selectedRecipe == null) {
            return;
        }
        
        // Verify the trade can actually happen (has ingredients)
        ItemStack ingredient1 = merchantInventory.getItem(0);
        ItemStack ingredient2 = merchantInventory.getItem(1);
        
        List<ItemStack> requiredIngredients = selectedRecipe.getIngredients();
        if (!canCompleteTrade(requiredIngredients, ingredient1, ingredient2)) {
            return;
        }
        
        // Schedule the inventory update for next tick (after trade completes)
        plugin.getFoliaLib().getImpl().runAtEntity(villager, task -> {
            processTradeInventoryExchange(npc, villager, selectedRecipe, ingredient1, ingredient2);
        });
    }
    
    /**
     * Checks if a trade can be completed with the current ingredients
     */
    private boolean canCompleteTrade(@NotNull List<ItemStack> required, ItemStack ingredient1, ItemStack ingredient2) {
        if (required.isEmpty()) return false;
        
        ItemStack required1 = required.get(0);
        ItemStack required2 = required.size() > 1 ? required.get(1) : null;
        
        // Check first ingredient
        if (!itemMatches(ingredient1, required1)) {
            return false;
        }
        
        // Check second ingredient if required
        if (required2 != null && !itemMatches(ingredient2, required2)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Checks if two items match for trading purposes
     */
    private boolean itemMatches(ItemStack provided, ItemStack required) {
        if (provided == null || required == null) return required == null;
        if (provided.getType() != required.getType()) return false;
        return provided.getAmount() >= required.getAmount();
    }
    
    /**
     * Processes the inventory exchange after a trade completes
     */
    private void processTradeInventoryExchange(@NotNull IVillagerNPC npc, @NotNull Villager villager, 
                                             @NotNull MerchantRecipe recipe, ItemStack actualIngredient1, ItemStack actualIngredient2) {
        
        Inventory villagerInventory = villager.getInventory();
        
        // Remove payment items from villager inventory (what villager pays to player)
        ItemStack tradeResult = recipe.getResult();
        removeItemFromInventory(villagerInventory, tradeResult);
        
        // Process received items - only keep items that the villager actually needs for their trades
        String ingredient1Status = "nothing";
        String ingredient2Status = "nothing";
        
        if (actualIngredient1 != null && !actualIngredient1.getType().isAir()) {
            if (villagerNeedsItem(villager, actualIngredient1)) {
                addItemToInventory(villagerInventory, actualIngredient1.clone());
                ingredient1Status = actualIngredient1.toString() + " (kept)";
            } else {
                ingredient1Status = actualIngredient1.toString() + " (discarded - not needed)";
            }
        }
        
        if (actualIngredient2 != null && !actualIngredient2.getType().isAir()) {
            if (villagerNeedsItem(villager, actualIngredient2)) {
                addItemToInventory(villagerInventory, actualIngredient2.clone());
                ingredient2Status = actualIngredient2.toString() + " (kept)";
            } else {
                ingredient2Status = actualIngredient2.toString() + " (discarded - not needed)";
            }
        }
        
        plugin.getLogger().info(String.format("Trade completed: Villager %s lost %dx %s, gained %s + %s", 
                npc.getVillagerName(), 
                tradeResult.getAmount(), 
                tradeResult.getType().name(),
                ingredient1Status,
                ingredient2Status));
    }
    
    /**
     * Removes items from villager inventory
     */
    private void removeItemFromInventory(@NotNull Inventory inventory, @NotNull ItemStack toRemove) {
        int amountToRemove = toRemove.getAmount();
        
        for (ItemStack item : inventory.getContents()) {
            if (item == null || !item.getType().equals(toRemove.getType())) {
                continue;
            }
            
            if (item.getAmount() > amountToRemove) {
                item.setAmount(item.getAmount() - amountToRemove);
                return;
            } else {
                amountToRemove -= item.getAmount();
                item.setAmount(0);
                item.setType(org.bukkit.Material.AIR);
                
                if (amountToRemove <= 0) {
                    return;
                }
            }
        }
    }
    
    /**
     * Adds items to villager inventory
     */
    private void addItemToInventory(@NotNull Inventory inventory, @NotNull ItemStack toAdd) {
        HashMap<Integer, ItemStack> leftover = inventory.addItem(toAdd);
        
        if (!leftover.isEmpty()) {
            plugin.getLogger().fine(String.format("Villager inventory full, couldn't add %dx %s", 
                    toAdd.getAmount(), toAdd.getType().name()));
        }
    }
    
    /**
     * Checks if a villager needs this item type for any of their trades.
     * Only items that the villager needs as ingredients for their own trades should be kept.
     * 
     * @param villager The villager to check
     * @param item The item to check
     * @return true if the villager needs this item for trading, false if it should be discarded
     */
    private boolean villagerNeedsItem(@NotNull Villager villager, @NotNull ItemStack item) {
        // Check all of the villager's trade recipes
        for (MerchantRecipe recipe : villager.getRecipes()) {
            // Check ingredients - these are what the villager needs to pay out
            for (ItemStack ingredient : recipe.getIngredients()) {
                if (ingredient != null && ingredient.getType() == item.getType()) {
                    return true; // Villager needs this item type for this trade
                }
            }
        }
        
        return false; // Villager doesn't need this item type for any of their trades
    }
}