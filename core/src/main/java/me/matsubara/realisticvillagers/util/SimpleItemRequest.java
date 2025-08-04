package me.matsubara.realisticvillagers.util;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Simple utility class for handling basic item requests between villagers
 * No complex background processing or reputation systems - just direct item transfers
 */
public class SimpleItemRequest {
    
    private static RealisticVillagers plugin;
    
    // Default configuration values (overridden by config)
    private static int minKeepFood = 3; // Always keep at least 3 food items
    private static int minKeepTools = 1; // Always keep at least 1 tool of each type
    private static double generosityFactor = 0.6; // Will give up to 60% of excess items
    
    /**
     * Initialize the configuration from the plugin
     * @param pluginInstance The plugin instance
     */
    public static void initialize(@NotNull RealisticVillagers pluginInstance) {
        plugin = pluginInstance;
        loadConfiguration();
        PhysicalItemDelivery.initialize(pluginInstance);
    }
    
    /**
     * Loads configuration from config.yml
     */
    private static void loadConfiguration() {
        if (plugin == null) return;
        
        var config = plugin.getConfig();
        minKeepFood = config.getInt("villager-requests.min-keep-food", 3);
        minKeepTools = config.getInt("villager-requests.min-keep-tools", 1);
        generosityFactor = config.getDouble("villager-requests.generosity-factor", 0.6);
    }
    
    /**
     * Checks if the request system is enabled
     * @return true if enabled
     */
    public static boolean isEnabled() {
        return plugin != null && plugin.getConfig().getBoolean("villager-requests.enabled", true);
    }
    
    /**
     * Handles a simple item request between two villagers
     * @param requester The villager requesting items
     * @param provider The villager being asked to provide items
     * @param item The material being requested
     * @param quantity The amount being requested
     * @return true if the request was successful and items were transferred
     */
    public static boolean handleItemRequest(@NotNull IVillagerNPC requester, 
                                          @NotNull IVillagerNPC provider, 
                                          @NotNull Material item, 
                                          int quantity) {
        if (quantity <= 0 || !isEnabled()) return false;
        
        // Check if provider has the items and can spare them
        if (!canProviderGiveItems(provider, item, quantity)) {
            return false;
        }
        
        // Try physical delivery if enabled
        if (PhysicalItemDelivery.isPhysicalInteractionEnabled()) {
            boolean physicalSuccess = PhysicalItemDelivery.startPhysicalDelivery(provider, requester, item, quantity);
            return physicalSuccess; // Return success/failure of physical delivery only
        }
        
        // If physical delivery is disabled, use instant inventory transfer
        if (!hasInventorySpace(requester, item, quantity)) {
            return false;
        }
        
        return transferItems(provider, requester, item, quantity);
    }
    
    /**
     * Checks if a villager can give the requested items
     * @param provider The villager being asked to provide
     * @param item The material requested
     * @param quantity The amount requested
     * @return true if the provider can spare the items
     */
    public static boolean canProviderGiveItems(@NotNull IVillagerNPC provider, 
                                              @NotNull Material item, 
                                              int quantity) {
        if (!(provider.bukkit() instanceof InventoryHolder holder)) {
            return false; // Provider has no inventory
        }
        
        Inventory inventory = holder.getInventory();
        int totalItems = countItems(inventory, item);
        
        if (totalItems < quantity) {
            return false; // Not enough items available
        }
        
        // Determine how many items to keep based on item type
        int minKeep = getMinKeepAmount(item);
        int availableForSharing = totalItems - minKeep;
        
        // For combat equipment during alerts, be more generous
        int canSpare;
        if (shouldUseGenerousSharing(item)) {
            // For combat equipment, share all excess items (100% generosity)
            canSpare = availableForSharing;
        } else {
            // For regular items, use the generosity factor
            canSpare = Math.max(0, (int) (availableForSharing * generosityFactor));
        }
        
        
        return canSpare >= quantity;
    }
    
    /**
     * Checks if a villager has inventory space for items
     * @param villager The villager to check
     * @param item The material to add
     * @param quantity The amount to add
     * @return true if there's space
     */
    private static boolean hasInventorySpace(@NotNull IVillagerNPC villager, 
                                           @NotNull Material item, 
                                           int quantity) {
        if (!(villager.bukkit() instanceof InventoryHolder holder)) return false;
        
        Inventory inventory = holder.getInventory();
        
        // Check for empty slots
        int emptySlots = 0;
        for (ItemStack slot : inventory.getContents()) {
            if (slot == null || slot.getType() == Material.AIR) {
                emptySlots++;
            }
        }
        
        // Check for partial stacks of the same material
        int partialStackSpace = 0;
        for (ItemStack slot : inventory.getContents()) {
            if (slot != null && slot.getType() == item && slot.getAmount() < slot.getMaxStackSize()) {
                partialStackSpace += slot.getMaxStackSize() - slot.getAmount();
            }
        }
        
        int maxStackSize = item.getMaxStackSize();
        int totalSpace = (emptySlots * maxStackSize) + partialStackSpace;
        
        return totalSpace >= quantity;
    }
    
    /**
     * Transfers items from provider to requester
     * @param provider The villager giving items
     * @param requester The villager receiving items
     * @param item The material to transfer
     * @param quantity The amount to transfer
     * @return true if transfer was successful
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
        
        // Find and remove items from provider
        for (int i = 0; i < providerInventory.getSize() && transferred < quantity; i++) {
            ItemStack slot = providerInventory.getItem(i);
            if (slot != null && slot.getType() == item) {
                int availableAmount = slot.getAmount();
                int toTransfer = Math.min(availableAmount, quantity - transferred);
                
                // Remove from provider
                if (availableAmount == toTransfer) {
                    providerInventory.setItem(i, null);
                } else {
                    slot.setAmount(availableAmount - toTransfer);
                }
                
                transferred += toTransfer;
            }
        }
        
        if (transferred < quantity) {
            // Somehow we couldn't get enough items, this shouldn't happen if checks passed
            return false;
        }
        
        // Add items to requester
        ItemStack toAdd = new ItemStack(item, transferred);
        
        // Mark the item as intended for the requester (for villager-to-villager sharing)
        markItemForVillager(toAdd, requester, provider);
        
        requesterInventory.addItem(toAdd);
        
        return true;
    }
    
    /**
     * Counts how many of a specific item a villager has
     * @param inventory The inventory to check
     * @param item The material to count
     * @return The total count of the item
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
     * Gets the minimum amount of an item type a villager should keep
     * @param item The material to check
     * @return The minimum amount to keep
     */
    private static int getMinKeepAmount(@NotNull Material item) {
        if (item.isEdible()) {
            return minKeepFood;
        }
        
        // For combat equipment during alerts, use more generous sharing rules
        if (shouldUseGenerousSharing(item)) {
            // Allow sharing of combat equipment more freely during alerts
            // Only keep 1 of each combat item instead of using minKeepTools
            return 1;
        }
        
        // Tools and weapons (non-combat)
        if (isToolOrWeapon(item)) {
            return minKeepTools;
        }
        
        // For other materials, keep at least 1
        return 1;
    }
    
    /**
     * Checks if a material is a tool or weapon
     * @param item The material to check
     * @return true if it's a tool or weapon
     */
    private static boolean isToolOrWeapon(@NotNull Material item) {
        String name = item.name();
        return name.contains("_SWORD") || name.contains("_AXE") || name.contains("_PICKAXE") ||
               name.contains("_SHOVEL") || name.contains("_HOE") || name.contains("_HELMET") ||
               name.contains("_CHESTPLATE") || name.contains("_LEGGINGS") || name.contains("_BOOTS") ||
               name.equals("BOW") || name.equals("CROSSBOW") || name.equals("TRIDENT") ||
               name.equals("SHIELD") || name.equals("FISHING_ROD");
    }
    
    /**
     * Check if equipment sharing should use more generous rules during alerts
     * @param item The material to check
     * @return true if generous sharing should be used
     */
    private static boolean shouldUseGenerousSharing(@NotNull Material item) {
        // For combat equipment during alerts, be more generous with sharing
        return EquipmentRequestQueue.isCombatEquipment(item);
    }
    
    /**
     * Marks an ItemStack as intended for a specific villager
     * @param itemStack The ItemStack to mark
     * @param recipient The villager who should receive this item
     * @param provider The villager who provided this item
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
                
                // Mark who the item is intended for
                NamespacedKey intendedRecipientKey = new NamespacedKey(plugin, "IntendedRecipient");
                container.set(intendedRecipientKey, PersistentDataType.STRING, 
                             recipient.bukkit().getUniqueId().toString());
                
                // Mark who provided the item
                NamespacedKey providerKey = new NamespacedKey(plugin, "ItemProvider");
                container.set(providerKey, PersistentDataType.STRING, 
                             provider.bukkit().getUniqueId().toString());
                
                itemStack.setItemMeta(meta);
                
            }
        } catch (Exception e) {
            if (plugin != null) {
                plugin.getLogger().warning("Failed to mark item for villager delivery: " + e.getMessage());
            }
        }
    }
    
    /**
     * Simple method to request food when hungry
     * @param hungryVillager The villager who needs food
     * @param nearbyVillagers List of nearby villagers who might have food
     * @return true if food was obtained
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
                        return true; // Successfully got food!
                    }
                }
            }
        }
        
        return false; // No food available
    }
}