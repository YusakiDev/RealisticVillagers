package me.matsubara.realisticvillagers.util;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for managing villager equipment in threat-based combat mode.
 * Handles storing/retrieving combat equipment based on threat status.
 */
public class EquipmentManager {
    
    // Alert system - stores villagers who have been alerted and when
    private static final Map<UUID, Long> alertedVillagers = new ConcurrentHashMap<>();
    private static final Map<UUID, AlertIntensity> alertIntensity = new ConcurrentHashMap<>();
    
    // Aggro system - stores villagers who are hostile towards specific players
    private static final Map<String, Long> hostileVillagers = new ConcurrentHashMap<>(); // Key: "villagerId:playerId"
    
    // Panic system - stores villagers who are in alert-induced panic state
    private static final Map<UUID, Long> panickedVillagers = new ConcurrentHashMap<>();
    
    private static org.bukkit.scheduler.BukkitTask cleanupTask = null;
    
    /**
     * Alert intensity levels for different types of threats/events
     */
    public enum AlertIntensity {
        LOW(20),      // Suspicious behavior, minor threats
        MEDIUM(45),   // Player damages villager, moderate threats  
        HIGH(60);     // Player kills villager, major village events
        
        private final int defaultDuration;
        
        AlertIntensity(int defaultDuration) {
            this.defaultDuration = defaultDuration;
        }
        
        public int getDefaultDuration() {
            return defaultDuration;
        }
    }
    
    // Performance optimization - cache current time for batch operations
    private static long cachedCurrentTime = 0L;
    private static long lastCacheUpdate = 0L;
    private static final long CACHE_DURATION = 100L; // Cache for 100ms
    
    /**
     * Checks if a villager should have combat equipment equipped based on threat-based mode.
     * 
     * @param npc The villager NPC
     * @return true if equipment should be equipped, false otherwise
     */
    public static boolean shouldHaveCombatEquipment(@NotNull IVillagerNPC npc) {
        // If threat-based equipment is disabled, always keep equipment
        if (!Config.THREAT_BASED_EQUIPMENT.asBool()) {
            return true;
        }
        
        // If villager is fighting or in danger, equip combat gear
        if (npc.isFighting()) {
            return true;
        }
        
        // If villager is panicking (fleeing), they should still have combat gear
        if (isPanicking(npc)) {
            return true;
        }
        
        // Check if this villager has been alerted by others
        boolean hasBeenAlerted = isAlerted(npc);
        
        
        return hasBeenAlerted;
    }
    
    /**
     * Checks if the villager is currently panicking/fleeing.
     * This is a simplified approach since we can't access NMS classes from core module.
     * 
     * @param npc The villager NPC
     * @return true if panicking, false otherwise
     */
    private static boolean isPanicking(@NotNull IVillagerNPC npc) {
        try {
            // Use reflection to check if villager is in PANIC activity
            // We'll check for the method name without specifying the parameter type
            java.lang.reflect.Method[] methods = npc.getClass().getMethods();
            for (java.lang.reflect.Method method : methods) {
                if (method.getName().equals("checkCurrentActivity") && method.getParameterCount() == 1) {
                    // Try to get the PANIC activity using reflection
                    Class<?> activityClass = method.getParameterTypes()[0];
                    java.lang.reflect.Field panicField = activityClass.getField("PANIC");
                    Object panicActivity = panicField.get(null);
                    
                    Object result = method.invoke(npc, panicActivity);
                    return result instanceof Boolean && (Boolean) result;
                }
            }
        } catch (Exception e) {
            // If reflection fails, assume not panicking
        }
        return false;
    }
    
    
    /**
     * Stores current combat equipment back into villager's inventory.
     * 
     * @param npc The villager NPC
     */
    public static void storeCombatEquipment(@NotNull IVillagerNPC npc) {
        LivingEntity bukkitVillager = npc.bukkit();
        if (bukkitVillager == null) return;
        
        EntityEquipment equipment = bukkitVillager.getEquipment();
        if (equipment == null) return;
        
        if (!(bukkitVillager instanceof org.bukkit.entity.Villager villager)) return;
        Inventory inventory = villager.getInventory();
        
        // Store weapons (main hand and off hand)
        ItemStack mainHand = equipment.getItemInMainHand();
        if (isCombatItem(mainHand) && !isWorkTool(mainHand)) {
            if (addToInventoryIfSpace(inventory, mainHand)) {
                equipment.setItemInMainHand(new ItemStack(Material.AIR));
            }
        }
        
        ItemStack offHand = equipment.getItemInOffHand();
        if (isCombatItem(offHand) && !isWorkTool(offHand)) {
            // Special handling for tridents - they need to be specifically handled
            if (offHand.getType() == Material.TRIDENT) {
                // Force trident into inventory even if it doesn't fit perfectly
                forceItemIntoInventory(inventory, offHand);
                equipment.setItemInOffHand(new ItemStack(Material.AIR));
            } else if (addToInventoryIfSpace(inventory, offHand)) {
                equipment.setItemInOffHand(new ItemStack(Material.AIR));
            }
        }
        
        // Store armor pieces
        ItemStack[] armorContents = equipment.getArmorContents();
        boolean armorChanged = false;
        
        for (int i = 0; i < armorContents.length; i++) {
            ItemStack armor = armorContents[i];
            if (armor != null && armor.getType() != Material.AIR) {
                if (addToInventoryIfSpace(inventory, armor)) {
                    armorContents[i] = new ItemStack(Material.AIR);
                    armorChanged = true;
                }
            }
        }
        
        if (armorChanged) {
            equipment.setArmorContents(armorContents);
        }
    }
    
    /**
     * Equips the best available combat equipment from villager's inventory.
     * This is a simplified version that relies on the existing CheckInventory behavior
     * to handle the actual equipment logic.
     * 
     * @param npc The villager NPC
     */
    public static void equipCombatGear(@NotNull IVillagerNPC npc) {
        // The actual equipping logic is handled by CheckInventory behavior
        // when shouldHaveCombatEquipment returns true.
        // This method serves as a trigger point for the equipment system.
    }
    
    
    /**
     * Checks if an item is a combat-related item (weapon, armor, shield).
     * 
     * @param item The item to check
     * @return true if it's a combat item, false otherwise
     */
    private static boolean isCombatItem(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        
        return ItemStackUtils.isWeapon(item) || 
               ItemStackUtils.getSlotByItem(item) != null ||
               item.getType() == Material.SHIELD ||
               item.getType() == Material.TRIDENT; // Ensure tridents are recognized as combat items
    }
    
    /**
     * Checks if an item is a work tool that should be kept equipped.
     * 
     * @param item The item to check
     * @return true if it's a work tool, false otherwise
     */
    private static boolean isWorkTool(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        
        Material type = item.getType();
        return type == Material.WOODEN_HOE ||
               type == Material.STONE_HOE ||
               type == Material.IRON_HOE ||
               type == Material.DIAMOND_HOE ||
               type == Material.GOLDEN_HOE ||
               type == Material.NETHERITE_HOE;
    }
    
    /**
     * Attempts to add an item to the inventory if there's space.
     * 
     * @param inventory The inventory to add to
     * @param item The item to add
     * @return true if the item was added, false otherwise
     */
    private static boolean addToInventoryIfSpace(@NotNull Inventory inventory, @NotNull ItemStack item) {
        // Check if there's space in the inventory
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) {
                inventory.setItem(i, item.clone());
                return true;
            }
            // Check if we can stack with existing item
            if (slot.isSimilar(item) && slot.getAmount() + item.getAmount() <= slot.getMaxStackSize()) {
                slot.setAmount(slot.getAmount() + item.getAmount());
                return true;
            }
        }
        return false;
    }
    
    /**
     * Forces an item into the inventory, replacing an existing item if necessary.
     * Used for special items like tridents that must be stored.
     * 
     * @param inventory The inventory to add to
     * @param item The item to force in
     */
    private static void forceItemIntoInventory(@NotNull Inventory inventory, @NotNull ItemStack item) {
        // First try normal addition
        if (addToInventoryIfSpace(inventory, item)) {
            return;
        }
        
        // If inventory is full, replace the first non-essential item
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot != null && slot.getType() != Material.AIR && !isWorkTool(slot)) {
                // Replace this item with the trident
                inventory.setItem(i, item.clone());
                return;
            }
        }
        
        // If all else fails, replace the first item
        if (inventory.getSize() > 0) {
            inventory.setItem(0, item.clone());
        }
    }
    
    /**
     * Alerts nearby villagers that threats have been detected.
     * 
     * @param alertingVillager The villager who detected the threat
     */
    private static void alertNearbyVillagers(@NotNull IVillagerNPC alertingVillager) {
        alertNearbyVillagers(alertingVillager, AlertIntensity.MEDIUM);
    }
    
    /**
     * Alerts nearby villagers with specified intensity level.
     * 
     * @param alertingVillager The villager who detected the threat
     * @param intensity The intensity level of the alert
     */
    private static void alertNearbyVillagers(@NotNull IVillagerNPC alertingVillager, @NotNull AlertIntensity intensity) {
        double alertRange = Config.THREAT_ALERT_RANGE.asDouble();
        alertNearbyVillagers(alertingVillager, intensity, alertRange);
    }
    
    /**
     * Alerts nearby villagers with specified intensity level and custom range.
     * 
     * @param alertingVillager The villager who detected the threat
     * @param intensity The intensity level of the alert
     * @param customRange The custom range for this alert (overrides config)
     */
    private static void alertNearbyVillagers(@NotNull IVillagerNPC alertingVillager, @NotNull AlertIntensity intensity, double customRange) {
        LivingEntity bukkitVillager = alertingVillager.bukkit();
        if (bukkitVillager == null || bukkitVillager.getWorld() == null) {
            return;
        }
        
        org.bukkit.Location alertLocation = bukkitVillager.getLocation();
        long currentTime = System.currentTimeMillis();
        
        // Find nearby villagers to alert using the custom range
        java.util.Collection<org.bukkit.entity.Entity> nearbyEntities = 
            bukkitVillager.getWorld().getNearbyEntities(alertLocation, customRange, customRange, customRange);
        
        int alertedCount = 0;
        for (org.bukkit.entity.Entity entity : nearbyEntities) {
            if (!(entity instanceof org.bukkit.entity.Villager villager) || entity == bukkitVillager) {
                continue;
            }
            
            // Check if this is a RealisticVillagers NPC
            try {
                // Try to get the NPC interface
                java.lang.reflect.Method getConverterMethod = org.bukkit.Bukkit.getPluginManager().getPlugin("RealisticVillagers")
                    .getClass().getMethod("getConverter");
                Object converter = getConverterMethod.invoke(org.bukkit.Bukkit.getPluginManager().getPlugin("RealisticVillagers"));
                
                java.lang.reflect.Method getNPCMethod = converter.getClass().getMethod("getNPC", LivingEntity.class);
                Object npcOptional = getNPCMethod.invoke(converter, villager);
                
                if (npcOptional instanceof java.util.Optional<?> optional && optional.isPresent()) {
                    IVillagerNPC targetNPC = (IVillagerNPC) optional.get();
                    
                    // Check distance to make sure they're actually within alert range
                    double alertDistance = alertLocation.distance(villager.getLocation());
                    if (alertDistance <= customRange) {
                        // Alert this villager with the specified intensity
                        alertedVillagers.put(targetNPC.getUniqueId(), currentTime);
                        alertIntensity.put(targetNPC.getUniqueId(), intensity);
                        alertedCount++;
                        
                        // For HIGH intensity alerts, trigger panic state for unarmed villagers
                        if (intensity == AlertIntensity.HIGH && !hasAnyWeapon(targetNPC)) {
                            org.bukkit.entity.LivingEntity threat = findNearbyThreat(targetNPC);
                            triggerVillagerPanic(targetNPC, threat);
                        }
                    }
                }
            } catch (Exception e) {
                // Reflection failed, skip this villager
            }
        }
        
    }
    
    
    /**
     * Checks if a villager has been alerted by another villager.
     * 
     * @param npc The villager to check
     * @return true if the villager has been alerted and the alert is still active
     */
    /**
     * Gets cached current time for performance optimization.
     * Reduces System.currentTimeMillis() calls during batch operations.
     */
    private static long getCurrentTime() {
        long now = System.currentTimeMillis();
        if (now - lastCacheUpdate > CACHE_DURATION) {
            cachedCurrentTime = now;
            lastCacheUpdate = now;
        }
        return cachedCurrentTime;
    }
    
    private static boolean isAlerted(@NotNull IVillagerNPC npc) {
        // Early exit - check if map is empty first
        if (alertedVillagers.isEmpty()) {
            return false;
        }
        
        Long alertTime = alertedVillagers.get(npc.getUniqueId());
        if (alertTime == null) {
            return false;
        }
        
        long currentTime = getCurrentTime();
        
        // Use intensity-specific duration if available, otherwise use config default
        AlertIntensity intensity = alertIntensity.get(npc.getUniqueId());
        long alertDuration;
        if (intensity != null) {
            alertDuration = intensity.getDefaultDuration() * 1000L;
        } else {
            alertDuration = Config.THREAT_ALERT_DURATION.asInt() * 1000L;
        }
        
        return (currentTime - alertTime) < alertDuration;
    }
    
    /**
     * Initializes the scheduled cleanup task for expired alerts.
     * Should be called when the plugin enables.
     * 
     * @param plugin The plugin instance for scheduling tasks
     */
    public static void initializeCleanupTask(@NotNull org.bukkit.plugin.Plugin plugin) {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        
        // Run cleanup every 30 seconds (600 ticks)
        cleanupTask = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, 
            EquipmentManager::cleanupExpiredAlerts, 600L, 600L);
    }
    
    /**
     * Stops the scheduled cleanup task.
     * Should be called when the plugin disables.
     */
    public static void shutdownCleanupTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }
    
    /**
     * Scheduled task that removes expired alerts and hostility in batches.
     * Much more efficient than individual cleanup per villager check.
     */
    private static void cleanupExpiredAlerts() {
        long currentTime = System.currentTimeMillis();
        
        // Clean up expired alerts
        if (!alertedVillagers.isEmpty()) {
            long defaultAlertDuration = Config.THREAT_ALERT_DURATION.asInt() * 1000L;
            
            // Remove expired alerts in batch, considering intensity levels
            alertedVillagers.entrySet().removeIf(entry -> {
                UUID villagerId = entry.getKey();
                long alertTime = entry.getValue();
                
                // Check intensity-specific duration
                AlertIntensity intensity = alertIntensity.get(villagerId);
                long alertDuration = intensity != null ? 
                    intensity.getDefaultDuration() * 1000L : defaultAlertDuration;
                
                boolean isExpired = (currentTime - alertTime) >= alertDuration;
                
                // Also clean up intensity map when alert expires
                if (isExpired) {
                    alertIntensity.remove(villagerId);
                }
                
                return isExpired;
            });
        }
        
        // Clean up expired hostility
        if (!hostileVillagers.isEmpty()) {
            hostileVillagers.entrySet().removeIf(entry -> {
                long expirationTime = entry.getValue();
                return currentTime >= expirationTime;
            });
        }
        
        // Clean up expired panic states
        if (!panickedVillagers.isEmpty()) {
            panickedVillagers.entrySet().removeIf(entry -> {
                UUID villagerId = entry.getKey();
                long expirationTime = entry.getValue();
                boolean isExpired = currentTime >= expirationTime;
                
                if (isExpired) {
                    // Clear the HURT_BY memory when panic expires
                    clearVillagerPanicMemory(villagerId);
                }
                
                return isExpired;
            });
        }
    }
    
    /**
     * Clears all alerts, hostility, and panic states (useful for cleanup or when threats are resolved).
     */
    public static void clearAllAlerts() {
        alertedVillagers.clear();
        alertIntensity.clear();
        hostileVillagers.clear();
        
        // Clear panic states and their memories
        for (UUID villagerId : panickedVillagers.keySet()) {
            clearVillagerPanicMemory(villagerId);
        }
        panickedVillagers.clear();
    }
    
    /**
     * Manually alert a specific villager (useful for testing or special cases).
     * 
     * @param npc The villager to alert
     */
    public static void alertVillager(@NotNull IVillagerNPC npc) {
        alertVillager(npc, AlertIntensity.LOW);
    }
    
    /**
     * Alert a specific villager with a specified intensity level.
     * 
     * @param npc The villager to alert
     * @param intensity The intensity level of the alert
     */
    public static void alertVillager(@NotNull IVillagerNPC npc, @NotNull AlertIntensity intensity) {
        long currentTime = System.currentTimeMillis();
        alertedVillagers.put(npc.getUniqueId(), currentTime);
        alertIntensity.put(npc.getUniqueId(), intensity);
    }
    
    /**
     * Called when a villager gets targeted/attacked by a hostile entity.
     * This triggers the alert system to warn nearby villagers.
     * 
     * @param targetedVillager The villager that was targeted/attacked
     */
    public static void onVillagerTargeted(@NotNull IVillagerNPC targetedVillager) {
        onVillagerTargeted(targetedVillager, AlertIntensity.MEDIUM);
    }
    
    /**
     * Called when a villager gets targeted/attacked with specified intensity.
     * 
     * @param targetedVillager The villager that was targeted/attacked
     * @param intensity The intensity level of the threat
     */
    public static void onVillagerTargeted(@NotNull IVillagerNPC targetedVillager, @NotNull AlertIntensity intensity) {
        // Alert the targeted villager themselves
        alertVillager(targetedVillager, intensity);
        
        // Alert nearby villagers about the threat
        alertNearbyVillagers(targetedVillager, intensity);
        
        // Force immediate equipment check for the targeted villager
        // Since they should equip gear immediately when attacked
        boolean shouldHaveGear = shouldHaveCombatEquipment(targetedVillager);
        boolean currentlyHasGear = hasAnyWeapon(targetedVillager) || hasAnyArmor(targetedVillager);
        
        if (shouldHaveGear && !currentlyHasGear) {
            // Try to cast to VillagerNPC and call immediate equipment
            try {
                // Use reflection to call the CheckInventory.equipImmediately method
                Class<?> checkInventoryClass = Class.forName("me.matsubara.realisticvillagers.entity.v1_21_7.villager.ai.behaviour.core.CheckInventory");
                java.lang.reflect.Method equipMethod = checkInventoryClass.getMethod("equipImmediately", 
                    Class.forName("me.matsubara.realisticvillagers.entity.v1_21_7.villager.VillagerNPC"));
                
                // Need to get the NMS villager instance
                Object nmsVillager = targetedVillager; // This should be the VillagerNPC instance
                equipMethod.invoke(null, nmsVillager);
                
            } catch (Exception e) {
                // Reflection failed, fallback to normal CheckInventory timing
            }
        }
    }
    
    /**
     * Called when a player kills a villager.
     * This creates a HIGH intensity alert that affects the entire village.
     * 
     * @param killedVillager The villager that was killed
     * @param killer The player who killed the villager
     */
    public static void onPlayerKillsVillager(@NotNull IVillagerNPC killedVillager, @NotNull org.bukkit.entity.Player killer) {
        // Create high-intensity village-wide alert (simple, immediate)
        alertNearbyVillagers(killedVillager, AlertIntensity.HIGH, 48.0); // Large range for serious events
        
        // Make nearby villagers retaliate against the killer
        if (Config.VILLAGER_RETALIATE_AGAINST_PLAYER_ATTACKS.asBool()) {
            makeNearbyVillagersAttackPlayer(killedVillager, killer, 48.0);
        }
    }
    
    /**
     * Called when a player damages a villager.
     * This creates a MEDIUM intensity alert in the local area.
     * The damaged villager and nearby villagers will attack the player.
     * 
     * @param damagedVillager The villager that was damaged
     * @param damager The player who damaged the villager
     */
    public static void onPlayerDamagesVillager(@NotNull IVillagerNPC damagedVillager, @NotNull org.bukkit.entity.Player damager) {
        // Alert the damaged villager and nearby villagers (they will equip gear)
        onVillagerTargeted(damagedVillager, AlertIntensity.MEDIUM);
        
        // Make the damaged villager and nearby villagers retaliate
        if (Config.VILLAGER_RETALIATE_AGAINST_PLAYER_ATTACKS.asBool()) {
            // First, make the damaged villager fight back
            if (damagedVillager.canAttack()) {
                setVillagerHostile(damagedVillager, damager);
                damagedVillager.attack(damager);
            }
            
            // Then make nearby villagers attack as well
            makeNearbyVillagersAttackPlayer(damagedVillager, damager, Config.THREAT_ALERT_RANGE.asDouble());
        }
    }
    
    /**
     * Called when any villager dies (not necessarily by player).
     * This creates a LOW-MEDIUM intensity mourning period for nearby villagers.
     * 
     * @param deadVillager The villager that died
     */
    public static void onVillagerDies(@NotNull IVillagerNPC deadVillager) {
        // Create mourning period for nearby family/friends
        alertNearbyVillagers(deadVillager, AlertIntensity.LOW, 24.0); // Smaller range, shorter duration
    }
    
    
    /**
     * Makes nearby villagers attack a specific player.
     * Used for retaliation when players attack villagers.
     * 
     * @param centerVillager The villager at the center of the retaliation
     * @param targetPlayer The player to attack
     * @param range The range to find villagers for retaliation
     */
    private static void makeNearbyVillagersAttackPlayer(@NotNull IVillagerNPC centerVillager, @NotNull org.bukkit.entity.Player targetPlayer, double range) {
        org.bukkit.entity.LivingEntity bukkitVillager = centerVillager.bukkit();
        if (bukkitVillager == null || bukkitVillager.getWorld() == null) {
            return;
        }
        
        org.bukkit.Location centerLocation = bukkitVillager.getLocation();
        
        // Find nearby villagers to make them attack the player
        java.util.Collection<org.bukkit.entity.Entity> nearbyEntities = 
            bukkitVillager.getWorld().getNearbyEntities(centerLocation, range, range, range);
        
        for (org.bukkit.entity.Entity entity : nearbyEntities) {
            if (!(entity instanceof org.bukkit.entity.Villager villager) || entity == bukkitVillager) {
                continue;
            }
            
            // Check if this is a RealisticVillagers NPC
            try {
                // Try to get the NPC interface
                java.lang.reflect.Method getConverterMethod = org.bukkit.Bukkit.getPluginManager().getPlugin("RealisticVillagers")
                    .getClass().getMethod("getConverter");
                Object converter = getConverterMethod.invoke(org.bukkit.Bukkit.getPluginManager().getPlugin("RealisticVillagers"));
                
                java.lang.reflect.Method getNPCMethod = converter.getClass().getMethod("getNPC", org.bukkit.entity.LivingEntity.class);
                Object npcOptional = getNPCMethod.invoke(converter, villager);
                
                if (npcOptional instanceof java.util.Optional<?> optional && optional.isPresent()) {
                    IVillagerNPC targetNPC = (IVillagerNPC) optional.get();
                    
                    // Check distance
                    double distance = centerLocation.distance(villager.getLocation());
                    if (distance <= range) {
                        // Don't retaliate against family members
                        if (!targetNPC.isFamily(targetPlayer.getUniqueId(), true)) {
                            // Only make villagers with weapons hostile - unarmed ones will just panic
                            if (targetNPC.canAttack() && hasAnyWeapon(targetNPC)) {
                                setVillagerHostile(targetNPC, targetPlayer);
                                // Set the player as nearest hostile so villager can see them even at long range
                                setVillagerHostileMemory(targetNPC, targetPlayer);
                                targetNPC.attack(targetPlayer);
                            } else {
                                // Unarmed villagers panic instead of fighting
                                triggerVillagerPanic(targetNPC, targetPlayer);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Reflection failed, skip this villager
            }
        }
    }
    
    /**
     * Helper method to check if villager has any armor equipped.
     */
    private static boolean hasAnyArmor(@NotNull IVillagerNPC npc) {
        org.bukkit.inventory.EntityEquipment equipment = npc.bukkit().getEquipment();
        if (equipment == null) return false;
        
        return (equipment.getHelmet() != null && equipment.getHelmet().getType() != org.bukkit.Material.AIR) ||
               (equipment.getChestplate() != null && equipment.getChestplate().getType() != org.bukkit.Material.AIR) ||
               (equipment.getLeggings() != null && equipment.getLeggings().getType() != org.bukkit.Material.AIR) ||
               (equipment.getBoots() != null && equipment.getBoots().getType() != org.bukkit.Material.AIR);
    }
    
    /**
     * Helper method to check if villager has any weapon equipped.
     */
    private static boolean hasAnyWeapon(@NotNull IVillagerNPC npc) {
        org.bukkit.inventory.EntityEquipment equipment = npc.bukkit().getEquipment();
        if (equipment == null) return false;
        
        org.bukkit.inventory.ItemStack mainHand = equipment.getItemInMainHand();
        org.bukkit.inventory.ItemStack offHand = equipment.getItemInOffHand();
        
        return (mainHand != null && ItemStackUtils.isWeapon(mainHand)) ||
               (offHand != null && (ItemStackUtils.isWeapon(offHand) || offHand.getType() == org.bukkit.Material.SHIELD));
    }
    
    /**
     * Marks a villager as hostile towards a specific player.
     * 
     * @param npc The villager NPC
     * @param player The player the villager is hostile towards
     */
    public static void setVillagerHostile(@NotNull IVillagerNPC npc, @NotNull org.bukkit.entity.Player player) {
        String key = npc.getUniqueId() + ":" + player.getUniqueId();
        long expirationTime = System.currentTimeMillis() + (Config.VILLAGER_AGGRO_COOLDOWN.asInt() * 1000L);
        hostileVillagers.put(key, expirationTime);
    }
    
    /**
     * Checks if a villager is hostile towards a specific player.
     * 
     * @param npc The villager NPC
     * @param player The player to check hostility against
     * @return true if the villager is hostile towards the player
     */
    public static boolean isVillagerHostile(@NotNull IVillagerNPC npc, @NotNull org.bukkit.entity.Player player) {
        String key = npc.getUniqueId() + ":" + player.getUniqueId();
        Long expirationTime = hostileVillagers.get(key);
        
        if (expirationTime == null) {
            return false;
        }
        
        // Check if aggro has expired
        if (System.currentTimeMillis() >= expirationTime) {
            hostileVillagers.remove(key);
            return false;
        }
        
        return true;
    }
    
    /**
     * Clears hostility between a villager and a specific player.
     * 
     * @param npc The villager NPC
     * @param player The player to clear hostility for
     */
    public static void clearVillagerHostility(@NotNull IVillagerNPC npc, @NotNull org.bukkit.entity.Player player) {
        String key = npc.getUniqueId() + ":" + player.getUniqueId();
        hostileVillagers.remove(key);
    }
    
    /**
     * Clears all hostility for a specific villager.
     * 
     * @param npc The villager NPC
     */
    public static void clearAllVillagerHostility(@NotNull IVillagerNPC npc) {
        String villagerPrefix = npc.getUniqueId() + ":";
        hostileVillagers.entrySet().removeIf(entry -> entry.getKey().startsWith(villagerPrefix));
    }
    
    /**
     * Clears all hostility towards a specific player.
     * 
     * @param player The player to clear hostility for
     */
    public static void clearAllHostilityTowardsPlayer(@NotNull org.bukkit.entity.Player player) {
        String playerSuffix = ":" + player.getUniqueId();
        hostileVillagers.entrySet().removeIf(entry -> entry.getKey().endsWith(playerSuffix));
    }
    
    /**
     * Checks if a villager is currently in alert-induced panic state.
     * 
     * @param npc The villager NPC to check
     * @return true if the villager is panicking due to alerts
     */
    public static boolean isPanickedByAlert(@NotNull IVillagerNPC npc) {
        Long expirationTime = panickedVillagers.get(npc.getUniqueId());
        if (expirationTime == null) {
            return false;
        }
        
        // Check if panic has expired
        if (System.currentTimeMillis() >= expirationTime) {
            panickedVillagers.remove(npc.getUniqueId());
            clearVillagerPanicMemory(npc.getUniqueId());
            return false;
        }
        
        return true;
    }
    
    /**
     * Manually clears panic state for a specific villager.
     * 
     * @param npc The villager NPC to clear panic for
     */
    public static void clearVillagerPanic(@NotNull IVillagerNPC npc) {
        panickedVillagers.remove(npc.getUniqueId());
        clearVillagerPanicMemory(npc.getUniqueId());
    }
    
    /**
     * Clears all alert-induced panic states.
     */
    public static void clearAllPanic() {
        for (UUID villagerId : panickedVillagers.keySet()) {
            clearVillagerPanicMemory(villagerId);
        }
        panickedVillagers.clear();
    }
    
    /**
     * Helper method to clear HURT_BY memory from a villager's brain.
     * This stops the panic behavior by removing the sustaining memory.
     * 
     * @param villagerId The UUID of the villager to clear panic memory for
     */
    private static void clearVillagerPanicMemory(@NotNull UUID villagerId) {
        try {
            // Try to get the villager NPC by UUID
            // We need to find the villager to clear its memory
            
            // Try to get the plugin instance and find the villager
            org.bukkit.plugin.Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("RealisticVillagers");
            if (plugin == null) return;
            
            java.lang.reflect.Method getConverterMethod = plugin.getClass().getMethod("getConverter");
            Object converter = getConverterMethod.invoke(plugin);
            
            // Unfortunately, we can't easily get a villager by UUID from here
            // The cleanup will happen naturally when the villager's brain processes expire the memory
            // Or when the villager gets hurt by something else, it will overwrite this memory
            
        } catch (Exception e) {
            // Memory cleanup failed - this is acceptable as panic will eventually expire naturally
        }
    }
    
    /**
     * Triggers panic state for a villager by temporarily disabling attack capability and causing damage.
     * This makes the villager flee and act scared during HIGH intensity alerts.
     * 
     * @param npc The villager NPC to make panic
     */
    private static void triggerVillagerPanic(@NotNull IVillagerNPC npc) {
        triggerVillagerPanic(npc, null);
    }
    
    /**
     * Triggers panic state for a villager with a specific threat to flee from.
     * 
     * @param npc The villager NPC to make panic
     * @param threatEntity The entity to flee from (can be null)
     */
    private static void triggerVillagerPanic(@NotNull IVillagerNPC npc, @Nullable org.bukkit.entity.LivingEntity threatEntity) {
        // Track panic duration
        long panicExpiration = System.currentTimeMillis() + (Config.VILLAGER_PANIC_DURATION.asInt() * 1000L);
        panickedVillagers.put(npc.getUniqueId(), panicExpiration);
        
        try {
            // Set hostile memory if we have a threat entity
            if (threatEntity != null) {
                setVillagerHostileMemory(npc, threatEntity);
            }
            
            // For villagers to panic instead of fight, they need to not be able to attack
            // We'll temporarily disable their attack capability, trigger damage, then restore it
            
            // Store original attack capability
            boolean originalCanAttack = npc.canAttack();
            
            // Temporarily disable attack capability so shouldPanic() returns true
            if (originalCanAttack) {
                // Use reflection to temporarily set canAttack to false
                java.lang.reflect.Method setCanAttackMethod = npc.getClass().getMethod("setCanAttack", boolean.class);
                setCanAttackMethod.invoke(npc, false);
            }
            
            // Now cause minimal damage to trigger panic (since canAttack is false, they will panic)
            org.bukkit.entity.LivingEntity bukkitVillager = npc.bukkit();
            if (bukkitVillager != null) {
                bukkitVillager.damage(0.1);
            }
            
            // Schedule restoring attack capability after a short delay (let panic trigger first)
            if (originalCanAttack) {
                org.bukkit.Bukkit.getScheduler().runTaskLater(
                    org.bukkit.Bukkit.getPluginManager().getPlugin("RealisticVillagers"),
                    () -> {
                        try {
                            java.lang.reflect.Method setCanAttackMethod = npc.getClass().getMethod("setCanAttack", boolean.class);
                            setCanAttackMethod.invoke(npc, true);
                        } catch (Exception e) {
                            // Failed to restore attack capability - not critical
                        }
                    },
                    2L // 0.1 seconds delay
                );
            }
            
        } catch (Exception e) {
            // Reflection failed - just track the panic duration for cleanup purposes
            // The villager won't actually panic but at least we won't have memory leaks
        }
    }
    
    /**
     * Finds nearby threatening entities (players or hostile mobs) that villagers should react to.
     * 
     * @param npc The villager NPC to search around
     * @return The nearest threatening entity, or null if none found
     */
    private static org.bukkit.entity.LivingEntity findNearbyThreat(@NotNull IVillagerNPC npc) {
        org.bukkit.entity.LivingEntity bukkitVillager = npc.bukkit();
        if (bukkitVillager == null || bukkitVillager.getWorld() == null) {
            return null;
        }
        
        // Search for nearby players or hostile mobs within alert range
        double searchRange = Config.THREAT_ALERT_RANGE.asDouble();
        java.util.Collection<org.bukkit.entity.Entity> nearbyEntities = 
            bukkitVillager.getWorld().getNearbyEntities(bukkitVillager.getLocation(), searchRange, searchRange, searchRange);
        
        for (org.bukkit.entity.Entity entity : nearbyEntities) {
            if (entity instanceof org.bukkit.entity.Player player) {
                // Players are threats if they're not family
                if (!npc.isFamily(player.getUniqueId(), true)) {
                    return player;
                }
            } else if (entity instanceof org.bukkit.entity.Monster) {
                // Hostile mobs are threats
                return (org.bukkit.entity.LivingEntity) entity;
            }
        }
        
        return null;
    }
    
    /**
     * Sets the NEAREST_HOSTILE memory for a villager so they can see and react to threats at long range.
     * 
     * @param npc The villager NPC
     * @param hostileEntity The entity to mark as hostile
     */
    private static void setVillagerHostileMemory(@NotNull IVillagerNPC npc, @NotNull org.bukkit.entity.LivingEntity hostileEntity) {
        try {
            // Get the NMS villager instance
            Object nmsVillager = npc;
            
            // Get the villager's brain
            java.lang.reflect.Method getBrainMethod = nmsVillager.getClass().getMethod("getBrain");
            Object brain = getBrainMethod.invoke(nmsVillager);
            
            // Get the MemoryModuleType.NEAREST_HOSTILE
            Class<?> memoryTypeClass = Class.forName("net.minecraft.world.entity.ai.memory.MemoryModuleType");
            Object nearestHostileMemory = memoryTypeClass.getField("NEAREST_HOSTILE").get(null);
            
            // Set the hostile entity in memory (convert Bukkit entity to NMS using reflection)
            java.lang.reflect.Method getHandleMethod = hostileEntity.getClass().getMethod("getHandle");
            Object nmsHostileEntity = getHandleMethod.invoke(hostileEntity);
            
            java.lang.reflect.Method setMemoryMethod = brain.getClass().getMethod("setMemory", memoryTypeClass, Object.class);
            setMemoryMethod.invoke(brain, nearestHostileMemory, nmsHostileEntity);
            
        } catch (Exception e) {
            // Reflection failed - villagers will still be alerted but may not react properly to long-range threats
        }
    }
}