package me.matsubara.realisticvillagers.util;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.WorkHungerConfig;
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
 * Villagers walk to each other and drop items marked for specific recipients
 * (Optional feature controlled by config)
 */
public class PhysicalItemDelivery {

    private static RealisticVillagers plugin;
    private static boolean physicalInteractionEnabled = false;
    private static double maxDeliveryDistance = 32.0;
    private static int itemClaimDuration = 30; // seconds
    private static double deliveryWalkSpeed = 1.0;

    // Track ongoing deliveries
    private static final Map<UUID, UUID> activeDeliveries = new HashMap<>();

    private static final String INTENDED_RECIPIENT_KEY = "IntendedRecipient";
    private static final String PROVIDER_KEY = "ItemProvider";
    private static final String CLAIM_EXPIRY_KEY = "ClaimExpiry";

    /**
     * Initialize the physical delivery system
     */
    public static void initialize(@NotNull RealisticVillagers pluginInstance) {
        plugin = pluginInstance;
        loadConfiguration();
    }

    /**
     * Load configuration
     */
    private static void loadConfiguration() {
        if (plugin == null) return;

        physicalInteractionEnabled = WorkHungerConfig.PHYSICAL_INTERACTION_ENABLED.asBool();
        maxDeliveryDistance = WorkHungerConfig.PHYSICAL_INTERACTION_MAX_DELIVERY_DISTANCE.asDouble();
        itemClaimDuration = WorkHungerConfig.PHYSICAL_INTERACTION_ITEM_CLAIM_DURATION.asInt();
        deliveryWalkSpeed = WorkHungerConfig.PHYSICAL_INTERACTION_DELIVERY_WALK_SPEED.asDouble();

        plugin.getLogger().info(String.format("Physical item delivery loaded: enabled=%b, max-distance=%.1f, claim-duration=%ds, walk-speed=%.1f",
                physicalInteractionEnabled, maxDeliveryDistance, itemClaimDuration, deliveryWalkSpeed));
    }

    /**
     * Check if physical interaction is enabled
     */
    public static boolean isPhysicalInteractionEnabled() {
        return physicalInteractionEnabled && SimpleItemRequest.isEnabled();
    }

    /**
     * Start a physical item delivery between two villagers
     */
    public static boolean startPhysicalDelivery(@NotNull IVillagerNPC provider,
                                               @NotNull IVillagerNPC requester,
                                               @NotNull Material item,
                                               int quantity) {
        if (!isPhysicalInteractionEnabled() || plugin == null) {
            return false;
        }

        // Check if provider is already busy
        UUID providerUUID = provider.bukkit().getUniqueId();
        if (activeDeliveries.containsKey(providerUUID)) {
            return false;
        }

        // Check distance
        Location providerLoc = provider.bukkit().getLocation();
        Location requesterLoc = requester.bukkit().getLocation();
        if (providerLoc == null || requesterLoc == null) {
            return false;
        }

        double distance = providerLoc.distance(requesterLoc);
        if (distance > maxDeliveryDistance) {
            return false;
        }

        // Check if provider has items
        if (!SimpleItemRequest.canProviderGiveItems(provider, item, quantity)) {
            return false;
        }

        plugin.getLogger().fine(String.format("Physical delivery started: %s -> %s (%dx %s, dist: %.1f)",
                provider.getVillagerName(), requester.getVillagerName(), quantity, item.name(), distance));

        // Mark as active delivery
        activeDeliveries.put(providerUUID, requester.bukkit().getUniqueId());

        // Remove items from provider
        removeItemsFromProvider(provider, item, quantity);

        // Drop items at provider location
        dropItemsForRecipient(provider, requester, item, quantity);

        return true;
    }

    /**
     * Check if dropped item is marked for a specific villager
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

        try {
            String providerString = droppedItem.getMetadata(PROVIDER_KEY).get(0).asString();
            return UUID.fromString(providerString);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Remove items from provider's inventory
     */
    private static void removeItemsFromProvider(@NotNull IVillagerNPC provider,
                                              @NotNull Material item,
                                              int quantity) {
        if (!(provider.bukkit() instanceof InventoryHolder holder)) {
            return;
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
    }

    /**
     * Drop items for recipient at provider location
     */
    private static void dropItemsForRecipient(@NotNull IVillagerNPC provider,
                                             @NotNull IVillagerNPC requester,
                                             @NotNull Material item,
                                             int quantity) {
        Location dropLocation = provider.bukkit().getLocation();
        if (dropLocation == null || plugin == null) {
            return;
        }

        ItemStack stack = new ItemStack(item, quantity);

        // Mark with metadata
        if (plugin.getServer() != null) {
            stack.setItemMeta(plugin.getServer().getItemFactory().getItemMeta(item));
        }

        Item dropped = dropLocation.getWorld().dropItem(dropLocation, stack);

        // Add metadata
        dropped.setMetadata(INTENDED_RECIPIENT_KEY,
                new FixedMetadataValue(plugin, requester.bukkit().getUniqueId().toString()));
        dropped.setMetadata(PROVIDER_KEY,
                new FixedMetadataValue(plugin, provider.bukkit().getUniqueId().toString()));

        // Mark claim expiry time
        long expiryTime = System.currentTimeMillis() + (itemClaimDuration * 1000L);
        dropped.setMetadata(CLAIM_EXPIRY_KEY,
                new FixedMetadataValue(plugin, expiryTime));

        plugin.getLogger().fine(String.format("Dropped %dx %s for %s at %.1f, %.1f, %.1f (expires in %ds)",
                quantity, item.name(), requester.getVillagerName(),
                dropLocation.getX(), dropLocation.getY(), dropLocation.getZ(), itemClaimDuration));
    }

    /**
     * Check if an item's claim has expired
     */
    public static boolean isClaimExpired(@NotNull Item droppedItem) {
        if (!droppedItem.hasMetadata(CLAIM_EXPIRY_KEY)) {
            return false;
        }

        try {
            long expiryTime = droppedItem.getMetadata(CLAIM_EXPIRY_KEY).get(0).asLong();
            return System.currentTimeMillis() > expiryTime;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the delivery walk speed multiplier
     */
    public static double getDeliveryWalkSpeed() {
        return deliveryWalkSpeed;
    }

    /**
     * Get the item claim duration in seconds
     */
    public static int getItemClaimDuration() {
        return itemClaimDuration;
    }

    /**
     * Mark delivery as complete
     */
    public static void completeDelivery(@NotNull IVillagerNPC provider) {
        activeDeliveries.remove(provider.bukkit().getUniqueId());
    }
}
