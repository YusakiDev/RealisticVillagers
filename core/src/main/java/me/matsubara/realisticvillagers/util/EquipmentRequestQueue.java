package me.matsubara.realisticvillagers.util;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.WorkHungerConfig;
import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages equipment request queues during village alerts.
 * Ensures villagers can only deliver one item at a time and handles priority-based requests.
 */
public class EquipmentRequestQueue {
    
    // Track active deliveries - each villager can only deliver one item at a time
    private static final Map<UUID, DeliveryTask> activeDeliveries = new ConcurrentHashMap<>();
    
    // Priority queue for pending equipment requests
    private static final PriorityQueue<EquipmentRequest> pendingRequests = new PriorityQueue<>();
    
    // Equipment priority order - higher priority items are delivered first
    private static final Map<Material, Integer> EQUIPMENT_PRIORITY = new HashMap<>();
    
    static {
        // Weapons (highest priority)
        EQUIPMENT_PRIORITY.put(Material.NETHERITE_SWORD, 100);
        EQUIPMENT_PRIORITY.put(Material.DIAMOND_SWORD, 95);
        EQUIPMENT_PRIORITY.put(Material.IRON_SWORD, 90);
        EQUIPMENT_PRIORITY.put(Material.STONE_SWORD, 85);
        EQUIPMENT_PRIORITY.put(Material.WOODEN_SWORD, 80);
        EQUIPMENT_PRIORITY.put(Material.BOW, 75);
        EQUIPMENT_PRIORITY.put(Material.CROSSBOW, 70);
        EQUIPMENT_PRIORITY.put(Material.TRIDENT, 65);
        
        // Armor (medium priority)
        EQUIPMENT_PRIORITY.put(Material.NETHERITE_HELMET, 60);
        EQUIPMENT_PRIORITY.put(Material.DIAMOND_HELMET, 55);
        EQUIPMENT_PRIORITY.put(Material.IRON_HELMET, 50);
        EQUIPMENT_PRIORITY.put(Material.CHAINMAIL_HELMET, 45);
        EQUIPMENT_PRIORITY.put(Material.LEATHER_HELMET, 40);
        
        EQUIPMENT_PRIORITY.put(Material.NETHERITE_CHESTPLATE, 59);
        EQUIPMENT_PRIORITY.put(Material.DIAMOND_CHESTPLATE, 54);
        EQUIPMENT_PRIORITY.put(Material.IRON_CHESTPLATE, 49);
        EQUIPMENT_PRIORITY.put(Material.CHAINMAIL_CHESTPLATE, 44);
        EQUIPMENT_PRIORITY.put(Material.LEATHER_CHESTPLATE, 39);
        
        EQUIPMENT_PRIORITY.put(Material.NETHERITE_LEGGINGS, 58);
        EQUIPMENT_PRIORITY.put(Material.DIAMOND_LEGGINGS, 53);
        EQUIPMENT_PRIORITY.put(Material.IRON_LEGGINGS, 48);
        EQUIPMENT_PRIORITY.put(Material.CHAINMAIL_LEGGINGS, 43);
        EQUIPMENT_PRIORITY.put(Material.LEATHER_LEGGINGS, 38);
        
        EQUIPMENT_PRIORITY.put(Material.NETHERITE_BOOTS, 57);
        EQUIPMENT_PRIORITY.put(Material.DIAMOND_BOOTS, 52);
        EQUIPMENT_PRIORITY.put(Material.IRON_BOOTS, 47);
        EQUIPMENT_PRIORITY.put(Material.CHAINMAIL_BOOTS, 42);
        EQUIPMENT_PRIORITY.put(Material.LEATHER_BOOTS, 37);
        
        // Shield (lowest priority)
        EQUIPMENT_PRIORITY.put(Material.SHIELD, 30);
    }
    
    /**
     * Represents a request for equipment from a needy villager
     */
    public static class EquipmentRequest implements Comparable<EquipmentRequest> {
        private final UUID requesterId;
        private final String requesterName;
        private final Material equipment;
        private final int quantity;
        private final long timestamp;
        
        public EquipmentRequest(@NotNull IVillagerNPC requester, @NotNull Material equipment, int quantity) {
            this.requesterId = requester.getUniqueId();
            this.requesterName = requester.getVillagerName();
            this.equipment = equipment;
            this.quantity = quantity;
            this.timestamp = System.currentTimeMillis();
        }
        
        @Override
        public int compareTo(@NotNull EquipmentRequest other) {
            // Higher priority first, then older requests first
            int priorityDiff = getEquipmentPriority(other.equipment) - getEquipmentPriority(this.equipment);
            if (priorityDiff != 0) return priorityDiff;
            
            return Long.compare(this.timestamp, other.timestamp);
        }
        
        public UUID getRequesterId() { return requesterId; }
        public String getRequesterName() { return requesterName; }
        public Material getEquipment() { return equipment; }
        public int getQuantity() { return quantity; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Represents an active delivery task
     */
    public static class DeliveryTask {
        private final UUID providerId;
        private final UUID recipientId;
        private final Material equipment;
        private final int quantity;
        private final long startTime;
        
        public DeliveryTask(@NotNull IVillagerNPC provider, @NotNull IVillagerNPC recipient, 
                           @NotNull Material equipment, int quantity) {
            this.providerId = provider.getUniqueId();
            this.recipientId = recipient.getUniqueId();
            this.equipment = equipment;
            this.quantity = quantity;
            this.startTime = System.currentTimeMillis();
        }
        
        public UUID getProviderId() { return providerId; }
        public UUID getRecipientId() { return recipientId; }
        public Material getEquipment() { return equipment; }
        public int getQuantity() { return quantity; }
        public long getStartTime() { return startTime; }
    }
    
    /**
     * Checks if a villager can deliver equipment (not currently busy with another delivery)
     */
    public static boolean canVillagerDeliver(@NotNull IVillagerNPC villager) {
        return !activeDeliveries.containsKey(villager.getUniqueId());
    }
    
    /**
     * Adds an equipment request to the queue
     */
    public static void queueEquipmentRequest(@NotNull IVillagerNPC requester, @NotNull Material equipment, int quantity) {
        EquipmentRequest request = new EquipmentRequest(requester, equipment, quantity);
        synchronized (pendingRequests) {
            pendingRequests.offer(request);
        }
    }
    
    /**
     * Starts a delivery task for a villager
     */
    public static void startDelivery(@NotNull IVillagerNPC provider, @NotNull IVillagerNPC recipient, 
                                    @NotNull Material equipment, int quantity) {
        DeliveryTask task = new DeliveryTask(provider, recipient, equipment, quantity);
        activeDeliveries.put(provider.getUniqueId(), task);
    }
    
    /**
     * Completes a delivery task
     */
    public static void completeDelivery(@NotNull IVillagerNPC provider) {
        activeDeliveries.remove(provider.getUniqueId());
    }
    
    /**
     * Gets the current delivery task for a villager
     */
    @Nullable
    public static DeliveryTask getCurrentDelivery(@NotNull IVillagerNPC villager) {
        return activeDeliveries.get(villager.getUniqueId());
    }
    
    /**
     * Gets the next highest priority equipment request
     */
    @Nullable
    public static EquipmentRequest getNextRequest() {
        synchronized (pendingRequests) {
            return pendingRequests.poll();
        }
    }
    
    /**
     * Gets the current number of pending requests
     */
    public static int getPendingRequestCount() {
        synchronized (pendingRequests) {
            return pendingRequests.size();
        }
    }
    
    /**
     * Gets the number of active deliveries
     */
    public static int getActiveDeliveryCount() {
        return activeDeliveries.size();
    }
    
    /**
     * Gets the priority value for a specific equipment type
     */
    private static int getEquipmentPriority(@NotNull Material equipment) {
        // First try to get from configuration
        try {
            RealisticVillagers plugin = JavaPlugin.getPlugin(RealisticVillagers.class);
            String configPath = "equipment-requests.priority-order." + equipment.name();
            if (plugin.getWorkHungerConfig().contains(configPath)) {
                return plugin.getWorkHungerConfig().getInt(configPath, 0);
            }
        } catch (Exception e) {
            // Fall back to hardcoded values if config reading fails
        }
        
        // Fallback to hardcoded priorities
        return EQUIPMENT_PRIORITY.getOrDefault(equipment, 0);
    }
    
    /**
     * Checks if a material is considered combat equipment
     */
    public static boolean isCombatEquipment(@NotNull Material material) {
        return EQUIPMENT_PRIORITY.containsKey(material);
    }
    
    /**
     * Clears all queues and active deliveries (for cleanup)
     */
    public static void clearAll() {
        synchronized (pendingRequests) {
            pendingRequests.clear();
        }
        activeDeliveries.clear();
    }
    
    /**
     * Removes stale delivery tasks (in case they get stuck)
     */
    public static void cleanupStaleDeliveries(long maxAgeMillis) {
        long currentTime = System.currentTimeMillis();
        activeDeliveries.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().getStartTime() > maxAgeMillis);
    }
    
    /**
     * Removes stale delivery tasks using configuration value
     */
    public static void cleanupStaleDeliveries() {
        long maxAgeMillis = WorkHungerConfig.EQUIPMENT_REQUESTS_MAX_DELIVERY_TIME_MINUTES.asInt() * 60 * 1000L;
        cleanupStaleDeliveries(maxAgeMillis);
    }
}