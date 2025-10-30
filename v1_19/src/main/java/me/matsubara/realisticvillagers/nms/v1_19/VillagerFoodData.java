package me.matsubara.realisticvillagers.nms.v1_19;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.entity.v1_19.villager.VillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerExhaustionEvent;
import me.matsubara.realisticvillagers.event.VillagerFoodLevelChangeEvent;
import me.matsubara.realisticvillagers.files.WorkHungerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_19_R3.inventory.CraftItemStack;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
public class VillagerFoodData {

    private final VillagerNPC npc;

    private int foodLevel;
    private int tickTimer;
    private int lastFoodLevel;
    private float saturationLevel;
    private float exhaustionLevel;

    private static final int SATURATED_REGEN_RATE = 10;
    private static final int UNSATURATED_REGEN_RATE = 80;
    private static final int STARVATION_RATE = 80;

    public VillagerFoodData(VillagerNPC npc) {
        this.npc = npc;
        this.foodLevel = getMaxFoodLevel();
        this.saturationLevel = 5.0f;
        this.lastFoodLevel = getMaxFoodLevel();
    }

    /**
     * Gets the maximum food level from config
     * @return The configured max food level (default: 20)
     */
    private static int getMaxFoodLevel() {
        try {
            return Math.max(1, WorkHungerConfig.MAX_FOOD_LEVEL.asInt());
        } catch (Exception e) {
            return 20; // Fallback to vanilla default
        }
    }

    /**
     * Gets the minimum hunger level required for slow healing
     * @return The configured min hunger to heal (default: 5)
     */
    private static int getMinHungerToHeal() {
        try {
            return Math.max(0, WorkHungerConfig.MIN_HUNGER_TO_HEAL.asInt());
        } catch (Exception e) {
            return 5; // Fallback
        }
    }

    /**
     * Gets the minimum hunger level required for saturated (fast) healing
     * @return The configured min hunger for saturated heal (default: 20)
     */
    private static int getMinHungerToSaturatedHeal() {
        try {
            return Math.max(1, WorkHungerConfig.MIN_HUNGER_TO_SATURATED_HEAL.asInt());
        } catch (Exception e) {
            return 20; // Fallback
        }
    }

    /**
     * Gets the hunger level at which villagers stop eating
     * @return The configured stop eating threshold (default: 20)
     */
    private static int getStopEatingAtHunger() {
        try {
            return Math.max(1, WorkHungerConfig.STOP_EATING_AT_HUNGER.asInt());
        } catch (Exception e) {
            return 20; // Fallback
        }
    }

    public void eat(int foodLevel, float saturationModifier) {
        this.foodLevel = Math.min(foodLevel + this.foodLevel, getMaxFoodLevel());
        this.saturationLevel = Math.min(this.saturationLevel + (float) foodLevel * saturationModifier * 2.0f, (float) this.foodLevel);
    }

    public void eat(@NotNull Item item, ItemStack stack) {
        FoodProperties properties = item.getFoodProperties();
        if (properties == null) return;

        int oldFoodLevel = foodLevel;

        VillagerFoodLevelChangeEvent event = callEvent(properties.getNutrition() + oldFoodLevel, stack);
        if (!event.isCancelled()) eat(event.getFoodLevel() - oldFoodLevel, properties.getSaturationModifier());
    }

    public void tick() {
        Difficulty difficulty = npc.level.getDifficulty();
        lastFoodLevel = foodLevel;

        if (exhaustionLevel > 4.0f) {
            exhaustionLevel -= 4.0f;
            if (saturationLevel > 0.0f) {
                saturationLevel = Math.max(saturationLevel - 1.0f, 0.0f);
            } else if (difficulty != Difficulty.PEACEFUL) {
                VillagerFoodLevelChangeEvent event = callEvent(Math.max(foodLevel - 1, 0), null);
                if (!event.isCancelled()) foodLevel = event.getFoodLevel();
            }
        }

        boolean flag = npc.level.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
        if (flag && saturationLevel > 0.0f && npc.isHurt() && foodLevel >= getMinHungerToSaturatedHeal()) {
            // Fast saturation-based healing (when hunger is full)
            ++tickTimer;
            if (tickTimer >= SATURATED_REGEN_RATE) {
                float amount = Math.min(saturationLevel, 6.0f);
                npc.heal(amount / 6.0f, RegainReason.SATIATED);
                npc.causeFoodExhaustion(amount, VillagerExhaustionEvent.ExhaustionReason.REGEN);
                tickTimer = 0;
            }
        } else if (flag && foodLevel >= getMinHungerToHeal() && npc.isHurt()) {
            // Slow hunger-based healing (configurable minimum hunger)
            ++tickTimer;
            if (tickTimer >= UNSATURATED_REGEN_RATE) {
                npc.heal(1.0f, RegainReason.SATIATED);
                npc.causeFoodExhaustion(npc.level.spigotConfig.regenExhaustion, VillagerExhaustionEvent.ExhaustionReason.REGEN);
                tickTimer = 0;
            }
        } else if (foodLevel <= 0) {
            ++tickTimer;
            if (tickTimer >= STARVATION_RATE) {
                if (npc.getHealth() > 10.0f || difficulty == Difficulty.HARD || npc.getHealth() > 1.0f && difficulty == Difficulty.NORMAL) {
                    npc.hurt(npc.damageSources().starve(), 1.0f);
                }
                tickTimer = 0;
            }
        } else {
            tickTimer = 0;
        }
    }

    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        if (!tag.contains("foodLevel", 99)) return;
        foodLevel = tag.getInt("foodLevel");
        tickTimer = tag.getInt("foodTickTimer");
        saturationLevel = tag.getFloat("foodSaturationLevel");
        exhaustionLevel = tag.getFloat("foodExhaustionLevel");
    }

    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        tag.putInt("foodLevel", foodLevel);
        tag.putInt("foodTickTimer", tickTimer);
        tag.putFloat("foodSaturationLevel", saturationLevel);
        tag.putFloat("foodExhaustionLevel", exhaustionLevel);
    }

    public boolean needsFood() {
        return foodLevel < getStopEatingAtHunger();
    }

    public void addExhaustion(float exhaustionLevel) {
        this.exhaustionLevel = Math.min(this.exhaustionLevel + exhaustionLevel, 40.0f);
    }

    private @NotNull VillagerFoodLevelChangeEvent callEvent(int level, @Nullable ItemStack item) {
        VillagerFoodLevelChangeEvent event = new VillagerFoodLevelChangeEvent(
                npc,
                level,
                item != null ? CraftItemStack.asBukkitCopy(item) : null);
        Bukkit.getServer().getPluginManager().callEvent(event);
        return event;
    }
}