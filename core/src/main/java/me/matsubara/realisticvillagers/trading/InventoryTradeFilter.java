package me.matsubara.realisticvillagers.trading;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Applies inventory-based availability rules on villager trades.
 */
public final class InventoryTradeFilter {

    private final RealisticVillagers plugin;
    private final TradingConfig config;

    public InventoryTradeFilter(@NotNull RealisticVillagers plugin, @NotNull TradingConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    /**
     * Builds a filtered copy of the villager's recipes that match their current inventory state.
     */
    public @NotNull List<MerchantRecipe> buildFilteredRecipes(@NotNull IVillagerNPC npc) {
        if (!isEnabled()) {
            return copyRecipes(npc);
        }

        Villager villager = asVillager(npc);
        if (villager == null || config.isProfessionExempt(villager.getProfession())) {
            return copyRecipes(npc);
        }

        List<MerchantRecipe> filtered = new ArrayList<>();
        for (MerchantRecipe original : villager.getRecipes()) {
            filtered.add(filterRecipe(villager, original));
        }
        return filtered;
    }

    /**
     * Re-applies availability constraints to the villager's current recipes.
     * Useful after their inventory changes (e.g., after trading or work).
     */
    public void refreshTrades(@NotNull Villager villager) {
        if (!isEnabled() || config.isProfessionExempt(villager.getProfession())) {
            return;
        }

        List<MerchantRecipe> originals = villager.getRecipes();
        Map<MerchantRecipe, MerchantRecipe> replacements = new IdentityHashMap<>();

        for (MerchantRecipe recipe : originals) {
            replacements.put(recipe, filterRecipe(villager, recipe));
        }

        for (int i = 0; i < originals.size(); i++) {
            villager.setRecipe(i, replacements.get(originals.get(i)));
        }
    }

    private @NotNull List<MerchantRecipe> copyRecipes(@NotNull IVillagerNPC npc) {
        Villager villager = asVillager(npc);
        if (villager == null) return List.of();

        List<MerchantRecipe> copy = new ArrayList<>();
        for (MerchantRecipe recipe : villager.getRecipes()) {
            copy.add(cloneRecipe(recipe));
        }
        return copy;
    }

    private MerchantRecipe filterRecipe(@NotNull Villager villager, @NotNull MerchantRecipe original) {
        boolean hasStock = hasStockForTrade(villager, original);
        boolean canAcceptInputs = !config.isCheckInputItems() || hasCapacityForInputs(villager, original);

        if (hasStock && canAcceptInputs) {
            return enableRecipe(original);
        }

        return disableRecipe(original);
    }

    private boolean hasStockForTrade(@NotNull Villager villager, @NotNull MerchantRecipe recipe) {
        ItemStack result = recipe.getResult();
        if (result == null || result.getType() == Material.AIR) {
            return true; // Nothing to pay out
        }

        int required = Math.max(1, (int) Math.ceil(result.getAmount() * config.getRequiredStockMultiplier()));
        return countMaterial(villager.getInventory(), result, config.isCheckExactItem()) >= required;
    }

    private boolean hasCapacityForInputs(@NotNull Villager villager, @NotNull MerchantRecipe recipe) {
        Inventory inventory = villager.getInventory();
        for (ItemStack ingredient : recipe.getIngredients()) {
            if (ingredient == null || ingredient.getType().isAir()) continue;

            int free = freeSpaceFor(inventory, ingredient);
            if (free < ingredient.getAmount()) {
                return false;
            }
        }
        return true;
    }

    private MerchantRecipe enableRecipe(@NotNull MerchantRecipe original) {
        MerchantRecipe enabled = cloneRecipe(original);
        enabled.setUses(0);
        enabled.setMaxUses(original.getMaxUses());

        // Remove any "Out of Stock" message from the lore when re-enabling
        if (config.isShowDisabledReason()) {
            ItemStack result = enabled.getResult().clone();
            ItemMeta meta = result.getItemMeta();
            if (meta != null && meta.hasLore()) {
                List<String> lore = new ArrayList<>(meta.getLore());
                String outOfStockMsg = PluginUtils.translate(config.getOutOfStockMessage());
                lore.removeIf(line -> line.equals(outOfStockMsg));

                if (lore.isEmpty()) {
                    meta.setLore(null);
                } else {
                    meta.setLore(lore);
                }
                result.setItemMeta(meta);

                MerchantRecipe cleaned = new MerchantRecipe(
                        result,
                        enabled.getUses(),
                        enabled.getMaxUses(),
                        enabled.hasExperienceReward(),
                        enabled.getVillagerExperience(),
                        enabled.getPriceMultiplier(),
                        enabled.getDemand(),
                        enabled.getSpecialPrice()
                );
                cleaned.setIngredients(enabled.getIngredients());
                return cleaned;
            }
        }

        return enabled;
    }

    private MerchantRecipe disableRecipe(@NotNull MerchantRecipe original) {
        MerchantRecipe disabled = cloneRecipe(original);
        disabled.setUses(disabled.getMaxUses());

        if (config.isShowDisabledReason()) {
            ItemStack result = disabled.getResult().clone();
            ItemMeta meta = result.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(PluginUtils.translate(config.getOutOfStockMessage()));
                meta.setLore(lore);
                result.setItemMeta(meta);

                MerchantRecipe decorated = new MerchantRecipe(
                        result,
                        disabled.getUses(),
                        disabled.getMaxUses(),
                        disabled.hasExperienceReward(),
                        disabled.getVillagerExperience(),
                        disabled.getPriceMultiplier(),
                        disabled.getDemand(),
                        disabled.getSpecialPrice()
                );
                decorated.setIngredients(disabled.getIngredients());
                return decorated;
            }
        }

        return disabled;
    }

    private MerchantRecipe cloneRecipe(@NotNull MerchantRecipe recipe) {
        MerchantRecipe clone = new MerchantRecipe(
                recipe.getResult().clone(),
                recipe.getUses(),
                recipe.getMaxUses(),
                recipe.hasExperienceReward(),
                recipe.getVillagerExperience(),
                recipe.getPriceMultiplier(),
                recipe.getDemand(),
                recipe.getSpecialPrice()
        );
        clone.setIngredients(recipe.getIngredients());
        return clone;
    }

    private int countMaterial(@NotNull Inventory inventory, @NotNull ItemStack target, boolean exact) {
        int count = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack == null || stack.getType() == Material.AIR) continue;
            if (matchesItem(stack, target, exact)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private int freeSpaceFor(@NotNull Inventory inventory, @NotNull ItemStack target) {
        int requiredStack = target.getMaxStackSize();
        int free = 0;

        for (ItemStack stack : inventory.getContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                free += requiredStack;
                continue;
            }

            if (matchesItem(stack, target, config.isCheckExactItem())) {
                free += Math.max(0, requiredStack - stack.getAmount());
            }
        }

        return free;
    }

    private boolean matchesItem(@NotNull ItemStack stack, @NotNull ItemStack target, boolean exact) {
        if (stack.getType() != target.getType()) return false;
        if (!exact) return true;
        return stack.isSimilar(target);
    }

    private Villager asVillager(@NotNull IVillagerNPC npc) {
        return npc.bukkit() instanceof Villager villager ? villager : null;
    }
}
