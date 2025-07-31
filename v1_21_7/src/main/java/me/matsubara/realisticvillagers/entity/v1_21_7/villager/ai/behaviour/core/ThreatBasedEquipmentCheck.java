package me.matsubara.realisticvillagers.entity.v1_21_7.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_7.villager.VillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.util.EquipmentManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.Villager;

/**
 * Behavior that periodically checks if villagers should store or equip combat gear
 * based on the threat-based equipment system.
 */
public class ThreatBasedEquipmentCheck extends Behavior<Villager> {

    private int checkCooldown;
    private static final int CHECK_INTERVAL = 60; // Check every 3 seconds (60 ticks)

    public ThreatBasedEquipmentCheck() {
        super(ImmutableMap.of());
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        // Only run if threat-based equipment is enabled
        if (!Config.THREAT_BASED_EQUIPMENT.asBool()) {
            return false;
        }
        
        if (checkCooldown > 0) {
            checkCooldown--;
            return false;
        }

        return villager instanceof VillagerNPC npc
                && !npc.isSleeping()
                && npc.isDoingNothing(false); // Don't interrupt important activities
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        if (!(villager instanceof VillagerNPC npc)) return;

        // Check if villager should have combat equipment based on current threat status
        boolean shouldHaveCombatGear = EquipmentManager.shouldHaveCombatEquipment(npc);
        boolean currentlyHasCombatGear = npc.isHoldingWeapon() || hasAnyArmor(npc);
        
        if (!shouldHaveCombatGear && currentlyHasCombatGear) {
            // Threats have passed, store combat gear back in inventory
            EquipmentManager.storeCombatEquipment(npc);
        } else if (shouldHaveCombatGear && !currentlyHasCombatGear) {
            // New threats detected, equip combat gear
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
}