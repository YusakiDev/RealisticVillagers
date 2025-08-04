package me.matsubara.realisticvillagers.entity.v1_21_7.villager.ai.sensing;

import com.google.common.collect.ImmutableSet;
import me.matsubara.realisticvillagers.entity.v1_21_7.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class NearestItemSensor extends Sensor<Mob> {

    private static final long ITEM_RANGE = 32L;
    private static final long TNT_RANGE = 8L;

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(VillagerNPC.NEAREST_WANTED_ITEM, VillagerNPC.NEAREST_PRIMED_TNT);
    }

    @Override
    public void doTick(@NotNull ServerLevel level, Mob mob) {
        provideNearest(mob,
                level.getEntitiesOfClass(
                        ItemEntity.class,
                        mob.getBoundingBox().inflate(ITEM_RANGE, (double) ITEM_RANGE / 2, ITEM_RANGE)),
                ITEM_RANGE,
                VillagerNPC.NEAREST_WANTED_ITEM,
                (entity) -> {
                    if (!(entity instanceof ItemEntity item)) return false;
                    
                    // Check if this item has delivery metadata
                    org.bukkit.entity.Item bukkitItem = (org.bukkit.entity.Item) item.getBukkitEntity();
                    
                    // FIRST: Check if this villager was the provider who dropped this item
                    if (bukkitItem.hasMetadata("ItemProvider")) {
                        String providerUUID = bukkitItem.getMetadata("ItemProvider").get(0).asString();
                        if (mob.getUUID().toString().equals(providerUUID)) {
                            // This villager dropped this item - don't want to pick it up
                            return false;
                        }
                    }
                    
                    // SECOND: If the item is marked for delivery, check if this villager is the intended recipient
                    if (bukkitItem.hasMetadata("IntendedRecipient")) {
                        // Check if this villager is the intended recipient
                        String intendedRecipient = bukkitItem.getMetadata("IntendedRecipient").get(0).asString();
                        if (!mob.getUUID().toString().equals(intendedRecipient)) {
                            // This item is for someone else - don't want it
                            return false;
                        }
                        // This item is for this villager - definitely want it!
                        return true;
                    }
                    
                    // THIRD: For all other items, check if they're wanted and not ignored
                    if (mob instanceof VillagerNPC npc) {
                        // Check if item is marked to be ignored
                        if (item.getBukkitEntity().getPersistentDataContainer().has(npc.getPlugin().getIgnoreItemKey(), PersistentDataType.INTEGER)) {
                            return false;
                        }
                    }
                    
                    // Finally, check if the villager wants to pick up this item
                    return mob.wantsToPickUp(level, item.getItem());
                });

        provideNearest(mob,
                level.getEntitiesOfClass(
                        PrimedTnt.class,
                        mob.getBoundingBox().inflate(TNT_RANGE)),
                TNT_RANGE,
                VillagerNPC.NEAREST_PRIMED_TNT,
                entity -> true);
    }

    private <T extends Entity> void provideNearest(Mob mob, List<T> entities, double closerThan, MemoryModuleType<T> memory, Predicate<Entity> filter) {
        if (entities == null) return;

        entities.sort(Comparator.comparingDouble(mob::distanceToSqr));
        Stream<T> stream = entities.stream().filter(filter).filter((entity) -> entity.closerThan(mob, closerThan));
        mob.getBrain().setMemory(memory, stream.findFirst());
    }
}