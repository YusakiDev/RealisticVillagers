package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour.work;

import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import me.matsubara.realisticvillagers.util.AntiEnslavementUtil;
import me.matsubara.realisticvillagers.util.InventoryCleanupUtil;
import me.matsubara.realisticvillagers.util.WorkHungerIntegration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.WorkAtPoi;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.NotNull;

/**
 * Base WorkAtPoi class that adds hunger integration for all professions.
 * Ensures equal hunger loss across all villager professions.
 */
public class WorkAtPoiWithHunger extends WorkAtPoi {

    @Override
    protected void useWorkstation(ServerLevel level, @NotNull Villager villager) {
        // Check if villager is confined (anti-enslavement protection)
        if (villager instanceof VillagerNPC npc && AntiEnslavementUtil.isVillagerConfined(npc)) {
            // Villager refuses to work when confined - passive resistance
            return;
        }

        // Call the original vanilla work logic
        super.useWorkstation(level, villager);

        // Add hunger integration for ALL professions
        if (villager instanceof VillagerNPC npc) {
            WorkHungerIntegration.onVillagerWorkWithPlugin(npc, npc.getPlugin());
            if (InventoryCleanupUtil.isCleanupEnabled(npc.getPlugin())) {
                InventoryCleanupUtil.cleanupVillagerInventory(npc, npc.getPlugin());
            }
        }
    }
}
