package me.matsubara.realisticvillagers.util;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles physical item delivery between villagers
 * Villagers walk to each other and drop items that are marked for specific recipients
 */
public class PhysicalItemDelivery {
    
    private static RealisticVillagers plugin;
    private static boolean physicalInteractionEnabled = true;
    private static double maxDeliveryDistance = 15.0;
    private static int itemClaimDuration = 30; // seconds
    private static double deliveryWalkSpeed = 1.2;
    
    // Track ongoing deliveries to prevent duplicate requests
    private static final Map<UUID, UUID> activeDeliveries = new HashMap<>(); // provider -> requester
    
    // Metadata keys for dropped items
    private static final String INTENDED_RECIPIENT_KEY = "IntendedRecipient";
    private static final String PROVIDER_KEY = "ItemProvider";
    
    /**
     * Initialize the physical delivery system
     */
    public static void initialize(@NotNull RealisticVillagers pluginInstance) {
        plugin = pluginInstance;
        loadConfiguration();
    }
    
    /**
     * Load configuration from config.yml
     */
    private static void loadConfiguration() {
        if (plugin == null) return;
        
        var config = plugin.getWorkHungerConfig();
        physicalInteractionEnabled = config.getBoolean("villager-requests.physical-interaction.enabled", true);
        maxDeliveryDistance = config.getDouble("villager-requests.physical-interaction.max-delivery-distance", 32.0);
        itemClaimDuration = config.getInt("villager-requests.physical-interaction.item-claim-duration", 30);
        deliveryWalkSpeed = config.getDouble("villager-requests.physical-interaction.delivery-walk-speed", 1.0);
        
        plugin.getLogger().info(String.format("Physical item delivery loaded: enabled=%b, max-distance=%.1f, claim-duration=%ds", 
                physicalInteractionEnabled, maxDeliveryDistance, itemClaimDuration));
    }
    
    /**
     * Check if physical interaction is enabled
     */
    public static boolean isPhysicalInteractionEnabled() {
        return physicalInteractionEnabled;
    }
    
    /**
     * Start a physical item delivery between two villagers
     * @param provider The villager who will deliver the item
     * @param requester The villager who requested the item  
     * @param item The material to deliver
     * @param quantity The amount to deliver
     * @return true if delivery was started successfully
     */
    public static boolean startPhysicalDelivery(@NotNull IVillagerNPC provider,
                                              @NotNull IVillagerNPC requester,
                                              @NotNull Material item,
                                              int quantity) {
        if (!physicalInteractionEnabled) {
            if (plugin != null) {
                plugin.getLogger().info("Physical delivery failed: disabled in config");
            }
            return false;
        }
        
        if (plugin == null) {
            return false;
        }
        
        // Check if provider is already delivering to someone
        UUID providerUUID = provider.bukkit().getUniqueId();
        if (activeDeliveries.containsKey(providerUUID)) {
            plugin.getLogger().fine(String.format("Physical delivery failed: %s already delivering to someone", provider.getVillagerName()));
            return false; // Already busy delivering
        }
        
        // Check distance
        Location providerLoc = provider.bukkit().getLocation();
        Location requesterLoc = requester.bukkit().getLocation();
        double distance = providerLoc.distance(requesterLoc);
        
        if (distance > maxDeliveryDistance) {
            plugin.getLogger().fine(String.format("Physical delivery failed: distance %.2f > max %.2f (%s -> %s)", 
                    distance, maxDeliveryDistance, provider.getVillagerName(), requester.getVillagerName()));
            return false; // Too far to deliver
        }
        
        // Check if provider has the items
        if (!canProviderGiveItems(provider, item, quantity)) {
            plugin.getLogger().fine(String.format("Physical delivery failed: %s cannot provide %dx %s", 
                    provider.getVillagerName(), quantity, item.name()));
            return false;
        }
        
        plugin.getLogger().fine(String.format("Physical delivery started: %s delivering %dx %s to %s (distance: %.2f)", 
                provider.getVillagerName(), quantity, item.name(), requester.getVillagerName(), distance));
        
        // Mark as active delivery
        activeDeliveries.put(providerUUID, requester.bukkit().getUniqueId());
        
        // Start the delivery process
        DeliveryTask task = new DeliveryTask(provider, requester, item, quantity);
        task.start();
        
        return true;
    }
    
    /**
     * Check if a dropped item is intended for a specific villager
     */
    public static boolean isItemForVillager(@NotNull Item droppedItem, @NotNull IVillagerNPC villager) {
        if (!droppedItem.hasMetadata(INTENDED_RECIPIENT_KEY)) {
            return false;
        }
        
        String intendedRecipient = droppedItem.getMetadata(INTENDED_RECIPIENT_KEY).get(0).asString();
        return villager.bukkit().getUniqueId().toString().equals(intendedRecipient);
    }
    
    /**
     * Get the provider UUID of a dropped item
     */
    public static UUID getItemProvider(@NotNull Item droppedItem) {
        if (!droppedItem.hasMetadata(PROVIDER_KEY)) {
            return null;
        }
        
        String providerString = droppedItem.getMetadata(PROVIDER_KEY).get(0).asString();
        try {
            return UUID.fromString(providerString);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    /**
     * Check if provider can give items (same logic as SimpleItemRequest)
     */
    private static boolean canProviderGiveItems(@NotNull IVillagerNPC provider, @NotNull Material item, int quantity) {
        // Use the same logic from SimpleItemRequest
        return SimpleItemRequest.canProviderGiveItems(provider, item, quantity);
    }
    
    /**
     * Remove items from provider's inventory
     */
    private static ItemStack removeItemsFromProvider(@NotNull IVillagerNPC provider, @NotNull Material item, int quantity) {
        if (!(provider.bukkit() instanceof InventoryHolder holder)) {
            return null;
        }
        
        Inventory inventory = holder.getInventory();
        int removed = 0;
        
        for (int i = 0; i < inventory.getSize() && removed < quantity; i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot != null && slot.getType() == item) {
                int availableAmount = slot.getAmount();
                int toRemove = Math.min(availableAmount, quantity - removed);
                
                if (availableAmount == toRemove) {
                    inventory.setItem(i, null);
                } else {
                    slot.setAmount(availableAmount - toRemove);
                }
                
                removed += toRemove;
            }
        }
        
        return removed > 0 ? new ItemStack(item, removed) : null;
    }
    
    /**
     * Task that handles the physical delivery process
     */
    private static class DeliveryTask {
        private final IVillagerNPC provider;
        private final IVillagerNPC requester;
        private final Material item;
        private final int quantity;
        private boolean hasStartedWalking = false;
        private boolean hasDroppedItem = false;
        private int ticksRunning = 0;
        private final int maxTicks = 20 * 60; // 60 seconds max
        private com.tcoded.folialib.wrapper.task.WrappedTask task;
        
        public DeliveryTask(@NotNull IVillagerNPC provider, @NotNull IVillagerNPC requester, 
                           @NotNull Material item, int quantity) {
            this.provider = provider;
            this.requester = requester;
            this.item = item;
            this.quantity = quantity;
        }
        
        public void start() {
            // Run the task at the provider's location for Folia compatibility
            task = plugin.getFoliaLib().getImpl().runAtEntityTimer(provider.bukkit(), this::run, 0L, 5L);
        }
        
        public void run() {
            ticksRunning++;
            
            // Timeout check
            if (ticksRunning > maxTicks) {
                cleanup();
                return;
            }
            
            // Check if villagers are still valid
            if (!provider.bukkit().isValid() || !requester.bukkit().isValid()) {
                cleanup();
                return;
            }
            
            Location providerLoc = provider.bukkit().getLocation();
            Location requesterLoc = requester.bukkit().getLocation();
            double distance = providerLoc.distance(requesterLoc);
            
            if (!hasStartedWalking) {
                // Start walking to the requester
                startWalkingToRequester();
                hasStartedWalking = true;
                
                plugin.getLogger().fine(String.format("Villager %s started walking to deliver %s to %s", 
                        provider.getVillagerName(), item.name(), requester.getVillagerName()));
            }
            
            // Check if close enough to drop item
            if (!hasDroppedItem && distance <= 3.0) {
                dropItemForRequester();
                hasDroppedItem = true;
                cleanup();
            }
        }
        
        private void startWalkingToRequester() {
            // Set walking target to the requester's location
            Location requesterLoc = requester.bukkit().getLocation();
            
            // Use the provider's method to set walk target
            provider.setWalkTarget(requesterLoc, deliveryWalkSpeed, 2);
            
            // Also make them look at each other
            provider.setLookTarget(requester.bukkit());
            requester.setLookTarget(provider.bukkit());
            
            plugin.getLogger().fine(String.format("Villager %s walking to deliver items to %s", 
                    provider.getVillagerName(), requester.getVillagerName()));
        }
        
        private void dropItemForRequester() {
            // Remove items from provider's inventory
            ItemStack itemsToDrop = removeItemsFromProvider(provider, item, quantity);
            if (itemsToDrop == null) {
                plugin.getLogger().warning(String.format("Could not remove items from provider %s", provider.getVillagerName()));
                return;
            }
            
            // Drop the item at provider's location
            Location dropLocation = provider.bukkit().getLocation();
            Item droppedItem = dropLocation.getWorld().dropItemNaturally(dropLocation, itemsToDrop);
            
            // Mark the dropped item with metadata
            String recipientUUID = requester.bukkit().getUniqueId().toString();
            String providerUUID = provider.bukkit().getUniqueId().toString();
            
            droppedItem.setMetadata(INTENDED_RECIPIENT_KEY, 
                    new FixedMetadataValue(plugin, recipientUUID));
            droppedItem.setMetadata(PROVIDER_KEY, 
                    new FixedMetadataValue(plugin, providerUUID));
            
            // No pickup delay needed since metadata prevents wrong villager from picking up
            droppedItem.setPickupDelay(0);
            
            // Schedule removal of metadata after claim duration
            plugin.getFoliaLib().getImpl().runAtEntityLater(droppedItem, () -> {
                if (droppedItem.isValid()) {
                    droppedItem.removeMetadata(INTENDED_RECIPIENT_KEY, plugin);
                    droppedItem.removeMetadata(PROVIDER_KEY, plugin);
                }
            }, itemClaimDuration * 20L);
            
            plugin.getLogger().fine(String.format("Villager %s dropped %dx %s for %s", 
                    provider.getVillagerName(), quantity, item.name(), requester.getVillagerName()));
        }
        
        private void cleanup() {
            // Remove from active deliveries
            activeDeliveries.remove(provider.bukkit().getUniqueId());
            
            // Cancel the task
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
    }
}