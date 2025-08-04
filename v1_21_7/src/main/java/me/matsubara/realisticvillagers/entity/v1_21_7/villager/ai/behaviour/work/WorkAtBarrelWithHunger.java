package me.matsubara.realisticvillagers.entity.v1_21_7.villager.ai.behaviour.work;

import me.matsubara.realisticvillagers.entity.v1_21_7.villager.VillagerNPC;
import me.matsubara.realisticvillagers.util.WorkHungerIntegration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.NotNull;

/**
 * WorkAtBarrel with hunger integration for fishermen
 * Combines custom fish storage logic with hunger system
 */
public class WorkAtBarrelWithHunger extends WorkAtBarrel {

    @Override
    public void useWorkstation(ServerLevel level, @NotNull Villager villager) {
        // Call the original custom work logic (storing fish in barrels)
        super.useWorkstation(level, villager);
        
        // Add hunger integration after work
        if (villager instanceof VillagerNPC npc) {
            WorkHungerIntegration.onVillagerWorkWithPlugin(npc, npc.getPlugin());
        }
    }
}