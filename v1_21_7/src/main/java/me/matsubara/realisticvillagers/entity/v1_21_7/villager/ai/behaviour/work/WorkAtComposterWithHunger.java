package me.matsubara.realisticvillagers.entity.v1_21_7.villager.ai.behaviour.work;

import me.matsubara.realisticvillagers.entity.v1_21_7.villager.VillagerNPC;
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
        // Call the original vanilla work logic (composting + bread making)
        super.useWorkstation(level, villager);
        
        // Add hunger integration after work
        if (villager instanceof VillagerNPC npc) {
            WorkHungerIntegration.onVillagerWorkWithPlugin(npc, npc.getPlugin());
        }
    }
}