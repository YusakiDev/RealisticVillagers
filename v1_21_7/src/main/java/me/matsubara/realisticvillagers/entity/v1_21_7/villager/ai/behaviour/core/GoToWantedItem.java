package me.matsubara.realisticvillagers.entity.v1_21_7.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_7.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.NotNull;

public class GoToWantedItem extends Behavior<Villager> {

    private final float speedModifier;
    private final int maxDistToWalk;

    public GoToWantedItem(float speedModifier, int maxDistToWalk) {
        super(ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                VillagerNPC.NEAREST_WANTED_ITEM, MemoryStatus.VALUE_PRESENT));
        this.speedModifier = speedModifier;
        this.maxDistToWalk = maxDistToWalk;
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (!(villager instanceof VillagerNPC npc)) return false;

        // If item is a gift or has been fished by this villager, go to the item regardless of distance and cooldown.
        ItemEntity closest = getClosestLovedItem(villager);
        boolean isForced = force(npc, closest);
        
        if (isForced) {
            return true;
        }

        return closest.closerThan(villager, maxDistToWalk)
                && !isOnPickupCooldown(villager)
                && !villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET);
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return villager instanceof VillagerNPC npc
                && villager.getBrain().hasMemoryValue(VillagerNPC.NEAREST_WANTED_ITEM)
                && force(npc, getClosestLovedItem(villager));
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        tick(level, villager, time);
    }

    @Override
    public void tick(ServerLevel level, Villager villager, long time) {
        if (villager instanceof VillagerNPC npc) {
            ItemEntity item = getClosestLovedItem(villager);
            double distance = villager.distanceTo(item);
            
            // If very close (under 1.5 blocks) and item is intended for this villager, manually trigger pickup
            if (distance < 1.5) {
                org.bukkit.entity.Item bukkitItem = (org.bukkit.entity.Item) item.getBukkitEntity();
                if (bukkitItem.hasMetadata("IntendedRecipient")) {
                    String intendedRecipient = bukkitItem.getMetadata("IntendedRecipient").get(0).asString();
                    if (npc.bukkit().getUniqueId().toString().equals(intendedRecipient)) {
                        // Check if item still exists (prevent double pickup)
                        if (item.isAlive()) {
                            npc.pickUpItem(level, item);
                            // Clear the memory to stop this behavior and prevent conflicts
                            villager.getBrain().eraseMemory(VillagerNPC.NEAREST_WANTED_ITEM);
                        } else {
                            villager.getBrain().eraseMemory(VillagerNPC.NEAREST_WANTED_ITEM);
                        }
                        return; // Don't continue walking
                    }
                }
            }
        }
        BehaviorUtils.setWalkAndLookTargetMemories(villager, getClosestLovedItem(villager), speedModifier, 0);
    }

    private boolean force(@NotNull VillagerNPC npc, @NotNull ItemEntity closest) {
        // Force pickup for fished items and expected gifts
        boolean isFished = npc.fished(closest.getItem());
        boolean isExpectedGift = closest.thrower != null && npc.isExpectingGiftFrom(closest.thrower.getUUID());
        
        // Force pickup for items specifically intended for this villager (physical delivery)
        boolean isIntendedForThisVillager = false;
        org.bukkit.entity.Item bukkitItem = (org.bukkit.entity.Item) closest.getBukkitEntity();
        if (bukkitItem.hasMetadata("IntendedRecipient")) {
            String intendedRecipient = bukkitItem.getMetadata("IntendedRecipient").get(0).asString();
            isIntendedForThisVillager = npc.bukkit().getUniqueId().toString().equals(intendedRecipient);
        }
        
        return isFished || isExpectedGift || isIntendedForThisVillager;
    }

    private boolean isOnPickupCooldown(@NotNull Villager level) {
        return level.getBrain().checkMemory(MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS, MemoryStatus.VALUE_PRESENT);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private @NotNull ItemEntity getClosestLovedItem(@NotNull Villager level) {
        return level.getBrain().getMemory(VillagerNPC.NEAREST_WANTED_ITEM).get();
    }
}