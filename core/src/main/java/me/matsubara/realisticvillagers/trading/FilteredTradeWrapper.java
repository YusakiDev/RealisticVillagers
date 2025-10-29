package me.matsubara.realisticvillagers.trading;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.MerchantRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Applies filtered trades when a player opens the merchant GUI, then restores the originals.
 */
public final class FilteredTradeWrapper {

    private final RealisticVillagers plugin;

    public FilteredTradeWrapper(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    public void openFilteredTrading(@NotNull IVillagerNPC npc, @NotNull Player player) {
        Villager villager = npc.bukkit() instanceof Villager bukkit ? bukkit : null;
        if (villager == null) {
            npc.startTrading(player);
            return;
        }

        if (!plugin.getTradingConfig().isEnabled()) {
            npc.startTrading(player);
            return;
        }

        List<MerchantRecipe> originalRecipes = villager.getRecipes();
        List<MerchantRecipe> filtered = plugin.getTradeFilter().buildFilteredRecipes(npc);

        villager.setRecipes(filtered);
        npc.startTrading(player);

        plugin.getFoliaLib().getScheduler().runAtEntityLater(villager, () -> villager.setRecipes(originalRecipes), 5L);
    }
}
