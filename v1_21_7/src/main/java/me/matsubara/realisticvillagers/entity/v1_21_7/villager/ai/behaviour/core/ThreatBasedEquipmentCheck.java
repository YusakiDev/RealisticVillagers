package me.matsubara.realisticvillagers.entity.v1_21_7.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_7.villager.VillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.util.EquipmentManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.Villager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Behavior that periodically checks if villagers should store or equip combat gear
 * based on the threat-based equipment system.
 */
public class ThreatBasedEquipmentCheck extends Behavior<Villager> {

    private int checkCooldown;
    private static final int CHECK_INTERVAL = 60; // Check every 3 seconds (60 ticks)
    
    // Cache for last logged state per villager to prevent spam
    private static final Map<java.util.UUID, String> lastLoggedState = new ConcurrentHashMap<>();
    private static final long LOG_COOLDOWN = 30000L; // 30 seconds between same-state logs
    private static final Map<java.util.UUID, Long> lastLogTime = new ConcurrentHashMap<>();

    public ThreatBasedEquipmentCheck() {
        super(ImmutableMap.of());
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        // Only run if threat-based equipment is enabled
        if (!Config.THREAT_BASED_EQUIPMENT.asBool()) {
            return false;
        }
        
        if (!(villager instanceof VillagerNPC npc) || npc.isSleeping()) {
            return false;
        }
        
        // Reduce cooldown or skip cooldown entirely during combat/alerts
        boolean inCombatOrAlert = npc.isFighting() || EquipmentManager.isAlerted(npc);
        
        if (!inCombatOrAlert && checkCooldown > 0) {
            checkCooldown--;
            return false;
        }
        
        // During alerts or combat, allow interrupting some activities for equipment checks
        if (inCombatOrAlert) {
            return true;
        }

        return npc.isDoingNothing(false); // Don't interrupt important activities during peaceful times
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        if (!(villager instanceof VillagerNPC npc)) return;

        // Check if villager should have combat equipment based on current threat status
        boolean shouldHaveCombatGear = EquipmentManager.shouldHaveCombatEquipment(npc);
        boolean currentlyHasCombatGear = npc.isHoldingWeapon() || hasAnyArmor(npc);
        boolean hasAdequateGear = EquipmentManager.hasAdequateCombatEquipment(npc);
        boolean isAlerted = EquipmentManager.isAlerted(npc);
        
        if (shouldHaveCombatGear && !hasAdequateGear) {
            // Villager needs equipment but doesn't have adequate gear
            // Check if this villager is on equipment request cooldown - if so, skip the request
            if (!EquipmentManager.isOnEquipmentCooldown(npc)) {
                // Request equipment from nearby villagers
                EquipmentManager.requestCombatEquipment(npc);
            }
        } else if (!shouldHaveCombatGear && currentlyHasCombatGear) {
            // Threats have passed, store combat gear back in inventory
            logStateChangeIfAllowed(npc, "storing", shouldHaveCombatGear, currentlyHasCombatGear, hasAdequateGear, isAlerted);
            EquipmentManager.storeCombatEquipment(npc);
        } else if (shouldHaveCombatGear && !currentlyHasCombatGear) {
            // New threats detected, equip combat gear
            logStateChangeIfAllowed(npc, "equipping", shouldHaveCombatGear, currentlyHasCombatGear, hasAdequateGear, isAlerted);
            EquipmentManager.equipCombatGear(npc);
        }

        // Reset cooldown
        checkCooldown = CHECK_INTERVAL;
    }
    
    /**
     * Checks if the villager has any armor equipped.
     */
    private boolean hasAnyArmor(VillagerNPC npc) {
        org.bukkit.inventory.EntityEquipment equipment = npc.bukkit().getEquipment();
        if (equipment == null) return false;
        
        return (equipment.getHelmet() != null && equipment.getHelmet().getType() != org.bukkit.Material.AIR) ||
               (equipment.getChestplate() != null && equipment.getChestplate().getType() != org.bukkit.Material.AIR) ||
               (equipment.getLeggings() != null && equipment.getLeggings().getType() != org.bukkit.Material.AIR) ||
               (equipment.getBoots() != null && equipment.getBoots().getType() != org.bukkit.Material.AIR);
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return false; // Single execution per start
    }
    
    /**
     * Logs state changes only if enough time has passed since the last similar log for this villager.
     * This prevents spam when villagers repeatedly try to equip/store gear.
     */
    private void logStateChangeIfAllowed(VillagerNPC npc, String action, boolean shouldHave, boolean currentlyHas, boolean hasAdequate, boolean isAlerted) {
        java.util.UUID villagerUUID = npc.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // Create a state signature to detect same-state spam
        String stateSignature = String.format("%s|%b|%b|%b|%b", action, shouldHave, currentlyHas, hasAdequate, isAlerted);
        
        String lastState = lastLoggedState.get(villagerUUID);
        Long lastTime = lastLogTime.get(villagerUUID);
        
        // Only log if state changed OR enough time has passed since last identical log
        boolean shouldLog = !stateSignature.equals(lastState) || 
                           (lastTime == null || (currentTime - lastTime) >= LOG_COOLDOWN);
        
        if (shouldLog) {
            npc.getPlugin().getLogger().info(String.format("%s %s combat gear (shouldHave=%b, has=%b, adequate=%b, alerted=%b)", 
                npc.getVillagerName(), action, shouldHave, currentlyHas, hasAdequate, isAlerted));
            
            lastLoggedState.put(villagerUUID, stateSignature);
            lastLogTime.put(villagerUUID, currentTime);
        }
    }
}