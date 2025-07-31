package me.matsubara.realisticvillagers.trading;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.MerchantRecipe;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;

/**
 * Wrapper to handle filtered trading by temporarily replacing
 * the villager's trades before opening the merchant GUI.
 */
public class FilteredTradeWrapper {
    
    private final RealisticVillagers plugin;
    
    public FilteredTradeWrapper(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Opens trading with filtered recipes based on inventory.
     * 
     * @param npc The villager NPC
     * @param player The player opening trade
     */
    public void openFilteredTrading(@NotNull IVillagerNPC npc, @NotNull Player player) {
        if (!(npc.bukkit() instanceof Villager villager)) {
            npc.startTrading(player);
            return;
        }
        
        // Get original recipes
        List<MerchantRecipe> originalRecipes = villager.getRecipes();
        
        try {
            // Get filtered recipes
            List<MerchantRecipe> filteredRecipes = plugin.getTradeFilter().filterTrades(npc);
            
            // Temporarily set filtered recipes
            villager.setRecipes(filteredRecipes);
            
            // Open trading GUI
            npc.startTrading(player);
            
            // Restore original recipes after a short delay
            // This ensures the GUI has opened with filtered recipes
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                villager.setRecipes(originalRecipes);
            }, 5L); // 0.25 seconds should be enough
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to apply trade filter, using original trades", e);
            // Fallback to normal trading
            npc.startTrading(player);
        }
    }
}