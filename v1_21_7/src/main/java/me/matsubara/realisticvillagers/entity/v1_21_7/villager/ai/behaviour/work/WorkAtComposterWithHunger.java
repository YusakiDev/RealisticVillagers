package me.matsubara.realisticvillagers.entity.v1_21_7.villager.ai.behaviour.work;

import me.matsubara.realisticvillagers.entity.v1_21_7.villager.VillagerNPC;
import me.matsubara.realisticvillagers.util.AntiEnslavementUtil;
import me.matsubara.realisticvillagers.util.InventoryCleanupUtil;
import me.matsubara.realisticvillagers.util.WorkHungerIntegration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.WorkAtComposter;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.NotNull;

/**
 * WorkAtComposter with hunger integration for farmers
 * Combines vanilla composting/bread-making with hunger system
 */
public class WorkAtComposterWithHunger extends WorkAtComposter {

    @Override
    public void useWorkstation(ServerLevel level, @NotNull Villager villager) {
        // Check if villager is confined (anti-enslavement protection)
        if (villager instanceof VillagerNPC npc && AntiEnslavementUtil.isVillagerConfined(npc)) {
            // Villager refuses to work when confined - passive resistance
            return;
        }
        
        // Call the original vanilla work logic (composting + bread making)
        super.useWorkstation(level, villager);
        
        // Add hunger integration after work
        if (villager instanceof VillagerNPC npc) {
            WorkHungerIntegration.onVillagerWorkWithPlugin(npc, npc.getPlugin());
            
            // Clean up inventory periodically (remove items they can't use)
            if (InventoryCleanupUtil.isCleanupEnabled(npc.getPlugin())) {
                InventoryCleanupUtil.cleanupVillagerInventory(npc, npc.getPlugin());
            }
        }
    }
}