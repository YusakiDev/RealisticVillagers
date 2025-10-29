package me.matsubara.realisticvillagers.util;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.WorkHungerConfig;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class for handling basic item requests between villagers
 * No complex background processing - just direct item transfers with configuration
 */
public class SimpleItemRequest {

    private static RealisticVillagers plugin;

    // Configuration values
    private static int minKeepFood = 3;
    private static double generosityFactor = 0.6;

    /**
     * Initialize the request system from config
     */
    public static void initialize(@NotNull RealisticVillagers pluginInstance) {
        plugin = pluginInstance;
        loadConfiguration();
    }

    /**
     * Load configuration from work-hunger-config
     */
    private static void loadConfiguration() {
        if (plugin == null) return;

        minKeepFood = WorkHungerConfig.VILLAGER_REQUESTS_MIN_KEEP_FOOD.asInt();
        generosityFactor = WorkHungerConfig.VILLAGER_REQUESTS_GENEROSITY_FACTOR.asDouble();

        plugin.getLogger().info(String.format("SimpleItemRequest loaded: min-keep-food=%d, generosity=%.1f%%",
                minKeepFood, generosityFactor * 100));
    }

    /**
     * Check if the request system is enabled
     */
    public static boolean isEnabled() {
        return plugin != null && WorkHungerConfig.VILLAGER_REQUESTS_ENABLED.asBool();
    }

    /**
     * Handle a simple item request between two villagers
     */
    public static boolean handleItemRequest(@NotNull IVillagerNPC requester,
                                           @NotNull IVillagerNPC provider,
                                           @NotNull Material item,
                                           int quantity) {
        if (quantity <= 0 || !isEnabled()) {
            return false;
        }

        // Check if provider can give items
        if (!canProviderGiveItems(provider, item, quantity)) {
            return false;
        }

        // Check if requester has inventory space
        if (!hasInventorySpace(requester, item, quantity)) {
            return false;
        }

        return transferItems(provider, requester, item, quantity);
    }

    /**
     * Check if a provider can give the requested items
     */
    public static boolean canProviderGiveItems(@NotNull IVillagerNPC provider,
                                               @NotNull Material item,
                                               int quantity) {
        if (!(provider.bukkit() instanceof InventoryHolder holder)) {
            return false;
        }

        Inventory inventory = holder.getInventory();
        int totalItems = countItems(inventory, item);

        if (totalItems < quantity) {
            return false;
        }

        // Get minimum to keep
        int minKeep = getMinKeepAmount(item);
        int availableForSharing = totalItems - minKeep;

        // Apply generosity factor
        int canSpare = Math.max(0, (int) (availableForSharing * generosityFactor));

        return canSpare >= quantity;
    }

    /**
     * Check if a villager has inventory space for items
     */
    private static boolean hasInventorySpace(@NotNull IVillagerNPC villager,
                                            @NotNull Material item,
                                            int quantity) {
        if (!(villager.bukkit() instanceof InventoryHolder holder)) {
            return false;
        }

        Inventory inventory = holder.getInventory();
        int emptySlots = 0;
        int partialStackSpace = 0;

        for (ItemStack slot : inventory.getContents()) {
            if (slot == null || slot.getType() == Material.AIR) {
                emptySlots++;
            } else if (slot.getType() == item && slot.getAmount() < slot.getMaxStackSize()) {
                partialStackSpace += slot.getMaxStackSize() - slot.getAmount();
            }
        }

        int maxStackSize = item.getMaxStackSize();
        int totalSpace = (emptySlots * maxStackSize) + partialStackSpace;

        return totalSpace >= quantity;
    }

    /**
     * Transfer items from provider to requester
     */
    private static boolean transferItems(@NotNull IVillagerNPC provider,
                                         @NotNull IVillagerNPC requester,
                                         @NotNull Material item,
                                         int quantity) {
        if (!(provider.bukkit() instanceof InventoryHolder providerHolder) ||
            !(requester.bukkit() instanceof InventoryHolder requesterHolder)) {
            return false;
        }

        Inventory providerInventory = providerHolder.getInventory();
        Inventory requesterInventory = requesterHolder.getInventory();

        int transferred = 0;

        // Remove from provider
        for (int i = 0; i < providerInventory.getSize() && transferred < quantity; i++) {
            ItemStack slot = providerInventory.getItem(i);
            if (slot != null && slot.getType() == item) {
                int availableAmount = slot.getAmount();
                int toTransfer = Math.min(availableAmount, quantity - transferred);

                if (availableAmount == toTransfer) {
                    providerInventory.setItem(i, null);
                } else {
                    slot.setAmount(availableAmount - toTransfer);
                }

                transferred += toTransfer;
            }
        }

        if (transferred < quantity) {
            return false;
        }

        // Add to requester
        ItemStack toAdd = new ItemStack(item, transferred);
        markItemForVillager(toAdd, requester, provider);
        requesterInventory.addItem(toAdd);

        if (plugin != null) {
            plugin.getLogger().fine(String.format("Villager %s received %dx %s from %s",
                    requester.getVillagerName(), transferred, item.name(), provider.getVillagerName()));
        }

        return true;
    }

    /**
     * Count items in an inventory
     */
    private static int countItems(@NotNull Inventory inventory, @NotNull Material item) {
        int count = 0;
        for (ItemStack slot : inventory.getContents()) {
            if (slot != null && slot.getType() == item) {
                count += slot.getAmount();
            }
        }
        return count;
    }

    /**
     * Get the minimum amount of an item type to keep
     */
    private static int getMinKeepAmount(@NotNull Material item) {
        return item.isEdible() ? minKeepFood : 1;
    }

    /**
     * Mark an ItemStack with metadata about its origin
     */
    private static void markItemForVillager(@NotNull ItemStack itemStack,
                                           @NotNull IVillagerNPC recipient,
                                           @NotNull IVillagerNPC provider) {
        if (plugin == null) return;

        try {
            ItemMeta meta = itemStack.getItemMeta();
            if (meta == null) {
                meta = plugin.getServer().getItemFactory().getItemMeta(itemStack.getType());
            }

            if (meta != null) {
                PersistentDataContainer container = meta.getPersistentDataContainer();

                NamespacedKey recipientKey = new NamespacedKey(plugin, "ItemRecipient");
                container.set(recipientKey, PersistentDataType.STRING,
                        recipient.bukkit().getUniqueId().toString());

                NamespacedKey providerKey = new NamespacedKey(plugin, "ItemProvider");
                container.set(providerKey, PersistentDataType.STRING,
                        provider.bukkit().getUniqueId().toString());

                itemStack.setItemMeta(meta);
            }
        } catch (Exception e) {
            if (plugin != null) {
                plugin.getLogger().warning("Failed to mark item metadata: " + e.getMessage());
            }
        }
    }

    /**
     * Request food from nearby villagers
     */
    public static boolean requestFoodFromNearby(@NotNull IVillagerNPC hungryVillager,
                                               @NotNull IVillagerNPC[] nearbyVillagers) {
        // Try common food items in order of preference
        Material[] foodItems = {
            Material.BREAD,
            Material.BAKED_POTATO,
            Material.COOKED_BEEF,
            Material.COOKED_PORKCHOP,
            Material.COOKED_CHICKEN,
            Material.CARROT,
            Material.POTATO,
            Material.BEETROOT
        };

        for (Material food : foodItems) {
            for (IVillagerNPC provider : nearbyVillagers) {
                if (provider != null && !provider.equals(hungryVillager)) {
                    if (handleItemRequest(hungryVillager, provider, food, 2)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
