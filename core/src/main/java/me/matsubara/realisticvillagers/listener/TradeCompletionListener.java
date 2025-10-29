package me.matsubara.realisticvillagers.listener;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.WorkHungerConfig;
import me.matsubara.realisticvillagers.trading.InventoryTradeFilter;
import me.matsubara.realisticvillagers.util.InventoryCleanupUtil;
import me.matsubara.realisticvillagers.util.WorkHungerIntegration;
import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public final class TradeCompletionListener implements Listener {

    private final RealisticVillagers plugin;

    public TradeCompletionListener(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResultClick(@NotNull InventoryClickEvent event) {
        if (!(event.getInventory() instanceof MerchantInventory merchantInventory)) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (!(merchantInventory.getMerchant() instanceof Villager villager)) return;
        if (!plugin.getTradingConfig().isEnabled()) return;

        Optional<IVillagerNPC> npcOpt = plugin.getConverter().getNPC(villager);
        if (npcOpt.isEmpty()) return;

        MerchantRecipe recipe = merchantInventory.getSelectedRecipe();
        if (recipe == null) return;

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) return;

        ItemStack ingredientOne = merchantInventory.getItem(0);
        ItemStack ingredientTwo = merchantInventory.getItem(1);

        if (!canCompleteTrade(recipe, ingredientOne, ingredientTwo)) return;

        plugin.getFoliaLib().getScheduler().runAtEntityLater(villager, () -> {
            processTrade(npcOpt.get(), villager, recipe, ingredientOne, ingredientTwo);

            // Block vanilla restocking if work-item-generation is enabled
            // Rule: work items enabled = vanilla restock disabled
            if (WorkHungerIntegration.shouldBlockVanillaRestock(npcOpt.get())) {
                WorkHungerIntegration.blockVanillaRestock(villager);
            } else {
                plugin.getTradeFilter().refreshTrades(villager);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMerchantClose(@NotNull InventoryCloseEvent event) {
        if (!(event.getInventory() instanceof MerchantInventory merchantInventory)) return;

        Merchant merchant = merchantInventory.getMerchant();
        if (!(merchant instanceof Villager villager)) return;
        if (!plugin.getTradingConfig().isEnabled()) return;

        Optional<IVillagerNPC> npcOpt = plugin.getConverter().getNPC(villager);
        if (npcOpt.isEmpty()) return;

        plugin.getFoliaLib().getScheduler().runAtEntityLater(villager, () -> {
            // Block vanilla restocking if work-item-generation is enabled
            // Rule: work items enabled = vanilla restock disabled
            if (WorkHungerIntegration.shouldBlockVanillaRestock(npcOpt.get())) {
                WorkHungerIntegration.blockVanillaRestock(villager);
            } else {
                plugin.getTradeFilter().refreshTrades(villager);
            }
        }, 1L);
    }

    private void processTrade(@NotNull IVillagerNPC npc,
                              @NotNull Villager villager,
                              @NotNull MerchantRecipe recipe,
                              ItemStack ingredientOne,
                              ItemStack ingredientTwo) {
        // Only apply realistic trade inventory if enabled
        if (!WorkHungerConfig.REALISTIC_TRADE_INVENTORY_ENABLED.asBool()) {
            return;
        }

        Inventory inventory = villager.getInventory();

        // Remove items villager is giving away (the result)
        removeFromInventory(inventory, recipe.getResult(), plugin.getTradingConfig().isCheckExactItem());

        // Add items villager is receiving (the ingredients/inputs)
        handleIncomingItem(villager, inventory, ingredientOne);
        handleIncomingItem(villager, inventory, ingredientTwo);

        if (InventoryCleanupUtil.isCleanupEnabled(plugin)) {
            InventoryCleanupUtil.cleanupVillagerInventory(npc, plugin);
        }
    }

    private void handleIncomingItem(@NotNull Villager villager,
                                    @NotNull Inventory inventory,
                                    ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;

        if (!villagerNeedsItem(villager, item)) return;

        ItemStack clone = item.clone();
        HashMap<Integer, ItemStack> leftover = inventory.addItem(clone);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(stack ->
                    villager.getWorld().dropItemNaturally(villager.getLocation(), stack));
        }
    }

    private boolean villagerNeedsItem(@NotNull Villager villager, @NotNull ItemStack item) {
        Material type = item.getType();
        for (MerchantRecipe recipe : villager.getRecipes()) {
            for (ItemStack ingredient : recipe.getIngredients()) {
                if (ingredient != null && ingredient.getType() == type) {
                    return true;
                }
            }
        }
        return false;
    }

    private void removeFromInventory(@NotNull Inventory inventory,
                                     @NotNull ItemStack target,
                                     boolean exactMatch) {
        int remaining = target.getAmount();

        for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing == null || existing.getType() == Material.AIR) continue;

            if (!matches(existing, target, exactMatch)) continue;

            int take = Math.min(existing.getAmount(), remaining);
            existing.setAmount(existing.getAmount() - take);
            if (existing.getAmount() <= 0) {
                inventory.clear(slot);
            }
            remaining -= take;
        }
    }

    private boolean canCompleteTrade(@NotNull MerchantRecipe recipe, ItemStack first, ItemStack second) {
        List<ItemStack> required = recipe.getIngredients();
        if (required.isEmpty()) return false;

        if (!matchesIngredient(required.get(0), first)) return false;
        if (required.size() > 1 && !matchesIngredient(required.get(1), second)) return false;
        return true;
    }

    private boolean matchesIngredient(ItemStack required, ItemStack provided) {
        if (required == null) return provided == null || provided.getType() == Material.AIR;
        if (provided == null || provided.getType() == Material.AIR) return false;

        if (plugin.getTradingConfig().isCheckExactItem()) {
            if (!provided.isSimilar(required)) return false;
        } else if (provided.getType() != required.getType()) {
            return false;
        }

        return provided.getAmount() >= required.getAmount();
    }

    private boolean matches(@NotNull ItemStack stack, @NotNull ItemStack target, boolean exact) {
        if (stack.getType() != target.getType()) return false;
        return !exact || stack.isSimilar(target);
    }
}
