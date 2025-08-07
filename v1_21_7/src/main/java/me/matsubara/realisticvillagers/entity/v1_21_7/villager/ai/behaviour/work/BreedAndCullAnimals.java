package me.matsubara.realisticvillagers.entity.v1_21_7.villager.ai.behaviour.work;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_7.villager.VillagerNPC;
import me.matsubara.realisticvillagers.files.WorkHungerConfig;
import me.matsubara.realisticvillagers.util.AntiEnslavementUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class BreedAndCullAnimals extends Behavior<Villager> {

    private final me.matsubara.realisticvillagers.RealisticVillagers plugin = 
        org.bukkit.plugin.java.JavaPlugin.getPlugin(me.matsubara.realisticvillagers.RealisticVillagers.class);

    private int timeWorkedSoFar;
    private long nextOkStartTime;
    private @Nullable BlockPos smokerPos;
    private @Nullable Villager villager;
    private final Map<EntityType<?>, Long> lastBreedingTime = new HashMap<>();
    private final Map<EntityType<?>, Long> lastCullingTime = new HashMap<>();
    
    private static final int DEFAULT_WORK_DURATION = 200;
    private static final double DEFAULT_SEARCH_RANGE = 16.0;
    
    // Animal type mappings
    private static final Map<EntityType<?>, String> ANIMAL_CONFIG_NAMES = ImmutableMap.<EntityType<?>, String>builder()
            .put(EntityType.COW, "COW")
            .put(EntityType.PIG, "PIG")
            .put(EntityType.CHICKEN, "CHICKEN")
            .put(EntityType.SHEEP, "SHEEP")
            .put(EntityType.RABBIT, "RABBIT")
            .build();

    public BreedAndCullAnimals() {
        super(ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT));
    }

    @Override
    public boolean checkExtraStartConditions(@NotNull ServerLevel level, Villager villager) {
        if (!(villager instanceof VillagerNPC npc)) return false;
        
        // Check if feature is enabled
        if (!WorkHungerConfig.BUTCHER_ANIMAL_MANAGEMENT_ENABLED.asBool()) return false;
        
        // Check if villager is butcher
        if (!villager.getVillagerData().profession().is(VillagerProfession.BUTCHER)) return false;
        
        // Check if villager is confined (anti-enslavement protection)
        if (AntiEnslavementUtil.isVillagerConfined(npc)) return false;
        
        // Check if villager is doing nothing
        if (!npc.isDoingNothing(true)) return false;
        
        // Find smoker (job site)
        net.minecraft.core.GlobalPos globalJobSite = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (globalJobSite == null) return false;
        
        BlockPos jobSite = globalJobSite.pos();
        
        // Verify it's actually a smoker
        if (!level.getBlockState(jobSite).is(Blocks.SMOKER)) return false;
        
        smokerPos = jobSite;
        return true;
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        this.villager = villager;
        if (time <= nextOkStartTime || smokerPos == null) return;
        
        // Set target to smoker
        BehaviorUtils.setWalkAndLookTargetMemories(villager, smokerPos, 0.5f, 1);
    }

    @Override
    public void stop(ServerLevel level, @NotNull Villager villager, long time) {
        if (villager instanceof VillagerNPC npc) {
            npc.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        timeWorkedSoFar = 0;
        nextOkStartTime = time + 40L;
    }

    @Override
    public void tick(ServerLevel level, Villager villager, long time) {
        if (smokerPos == null) return;
        
        // Must be near smoker to work
        if (!smokerPos.closerToCenterThan(villager.position(), 2.0)) {
            timeWorkedSoFar++;
            return;
        }
        
        if (time <= nextOkStartTime) {
            timeWorkedSoFar++;
            return;
        }
        
        double searchRange = WorkHungerConfig.BUTCHER_ANIMAL_MANAGEMENT_SEARCH_RANGE.asDouble();
        
        // Process each animal type
        for (Map.Entry<EntityType<?>, String> entry : ANIMAL_CONFIG_NAMES.entrySet()) {
            EntityType<?> animalType = entry.getKey();
            String configName = entry.getValue();
            
            if (!isAnimalEnabled(configName)) continue;
            
            processAnimalType(level, villager, time, animalType, configName, searchRange);
        }
        
        timeWorkedSoFar++;
    }

    private void processAnimalType(ServerLevel level, Villager villager, long time, EntityType<?> animalType, String configName, double searchRange) {
        // Find animals of this type near smoker
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, 
                villager.getBoundingBox().inflate(searchRange),
                entity -> entity.getType() == animalType && entity.isAlive());
        
        if (animals.isEmpty()) return;
        
        int animalCount = animals.size();
        int minToBreed = getAnimalConfig(configName, "min-to-breed", 2);
        int targetPopulation = getAnimalConfig(configName, "target-population", 4);
        int maxBeforeCull = getAnimalConfig(configName, "max-before-cull", 8);
        long breedingCooldown = getAnimalConfig(configName, "breeding-cooldown", 100);
        
        // Check if we should breed
        if (animalCount >= minToBreed && animalCount < targetPopulation) {
            if (canBreed(animalType, time, breedingCooldown)) {
                attemptBreeding(level, villager, animals, configName, time);
            }
        }
        
        // Check if we should cull
        if (animalCount > maxBeforeCull && WorkHungerConfig.BUTCHER_CULLING_ENABLED.asBool()) {
            long cullCooldown = WorkHungerConfig.BUTCHER_CULLING_CULL_COOLDOWN.asLong();
            if (canCull(animalType, time, cullCooldown)) {
                attemptCulling(level, villager, animals, time);
            }
        }
    }

    private void attemptBreeding(ServerLevel level, Villager villager, List<Animal> animals, String configName, long time) {
        // Find two adults that can breed
        List<Animal> breedableAnimals = new ArrayList<>();
        for (Animal animal : animals) {
            if (!animal.isBaby() && animal.canFallInLove() && !animal.isInLove()) {
                breedableAnimals.add(animal);
            }
        }
        
        if (breedableAnimals.size() < 2) return;
        
        // Get breeding item from config
        String breedingItemName = getAnimalBreedingItem(configName);
        ItemStack breedingItem = getItemStackFromName(breedingItemName);
        
        if (breedingItem.isEmpty()) return;
        
        // Spawn breeding item in hand
        villager.setItemInHand(InteractionHand.MAIN_HAND, breedingItem.copy());
        
        // Get first two available animals
        Animal animal1 = breedableAnimals.get(0);
        Animal animal2 = breedableAnimals.get(1);
        
        // Move villager towards the animals and use the item
        BehaviorUtils.setWalkAndLookTargetMemories(villager, animal1.blockPosition(), 0.5f, 1);
        
        // Schedule the actual breeding after villager gets close using FoliaLib with entity region
        plugin.getFoliaLib().getScheduler().runAtEntity(villager.getBukkitEntity(), (task) -> {
            scheduleDelayedBreeding(level, villager, animal1, animal2, breedingItem, time);
        });
    }
    
    private void scheduleDelayedBreeding(ServerLevel level, Villager villager, Animal animal1, Animal animal2, ItemStack breedingItem, long time) {
        // Check if villager is close enough to animals
        double distance1 = villager.distanceToSqr(animal1);
        double distance2 = villager.distanceToSqr(animal2);
        
        if (distance1 <= 9.0 && distance2 <= 9.0) { // Within 3 blocks
            // Perform hand swing animation to show villager is "using" the item
            villager.swing(InteractionHand.MAIN_HAND);
            
            // Actually breed the animals using the breeding item logic
            ItemStack currentItem = villager.getItemInHand(InteractionHand.MAIN_HAND);
            if (!currentItem.isEmpty() && animal1.isFood(currentItem) && animal2.isFood(currentItem)) {
                // Feed the animals to make them breed
                animal1.setInLove(null);
                animal2.setInLove(null);
            }
            
            // Update last breeding time
            lastBreedingTime.put(animal1.getType(), time);
            
            // Remove breeding item after use using FoliaLib with entity region
            plugin.getFoliaLib().getScheduler().runAtEntity(villager.getBukkitEntity(), (task) -> {
                villager.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            });
        } else {
            // If not close enough, try again later using FoliaLib with entity region
            plugin.getFoliaLib().getScheduler().runAtEntityLater(villager.getBukkitEntity(), () -> {
                scheduleDelayedBreeding(level, villager, animal1, animal2, breedingItem, time);
            }, 20L); // 1 second delay
        }
    }

    private void attemptCulling(ServerLevel level, Villager villager, List<Animal> animals, long time) {
        boolean preferAdults = WorkHungerConfig.BUTCHER_CULLING_PREFER_ADULTS.asBool();
        
        // Choose animal to cull
        Animal targetAnimal = null;
        
        if (preferAdults) {
            // Find an adult to cull first
            for (Animal animal : animals) {
                if (!animal.isBaby()) {
                    targetAnimal = animal;
                    break;
                }
            }
        }
        
        // If no adult found or prefer adults is false, cull any animal
        if (targetAnimal == null && !animals.isEmpty()) {
            targetAnimal = animals.get(level.getRandom().nextInt(animals.size()));
        }
        
        if (targetAnimal == null) return;
        
        // Spawn random axe from config
        List<String> axeTypes = WorkHungerConfig.BUTCHER_CULLING_AXE_TYPES.asStringList();
        if (axeTypes.isEmpty()) return;
        
        String axeType = axeTypes.get(level.getRandom().nextInt(axeTypes.size()));
        ItemStack axe = getItemStackFromName(axeType);
        
        if (axe.isEmpty()) return;
        
        // Equip axe
        villager.setItemInHand(InteractionHand.MAIN_HAND, axe.copy());
        
        // Move villager towards the target animal
        BehaviorUtils.setWalkAndLookTargetMemories(villager, targetAnimal.blockPosition(), 0.5f, 1);
        
        // Schedule the actual culling after villager gets close using FoliaLib with entity region
        Animal finalTarget = targetAnimal;
        plugin.getFoliaLib().getScheduler().runAtEntity(villager.getBukkitEntity(), (task) -> {
            scheduleDelayedCulling(level, villager, finalTarget, axe, time);
        });
    }
    
    private void scheduleDelayedCulling(ServerLevel level, Villager villager, Animal targetAnimal, ItemStack axe, long time) {
        // Check if villager is close enough to animal
        double distance = villager.distanceToSqr(targetAnimal);
        
        if (distance <= 4.0) { // Within 2 blocks
            // Perform attack animation and damage
            villager.swing(InteractionHand.MAIN_HAND);
            
            // Apply damage with some delay for realism using FoliaLib with entity region
            plugin.getFoliaLib().getScheduler().runAtEntity(villager.getBukkitEntity(), (task) -> {
                if (targetAnimal.isAlive()) {
                    targetAnimal.hurt(level.damageSources().mobAttack(villager), Float.MAX_VALUE);
                }
                
                // Update last culling time
                lastCullingTime.put(targetAnimal.getType(), time);
                
                // Remove axe after use
                villager.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            });
        } else {
            // If not close enough, try again later using FoliaLib with entity region
            plugin.getFoliaLib().getScheduler().runAtEntityLater(villager.getBukkitEntity(), () -> {
                if (targetAnimal.isAlive()) {
                    scheduleDelayedCulling(level, villager, targetAnimal, axe, time);
                }
            }, 20L); // 1 second delay
        }
    }

    private boolean canBreed(EntityType<?> animalType, long currentTime, long cooldown) {
        Long lastTime = lastBreedingTime.get(animalType);
        return lastTime == null || (currentTime - lastTime) >= cooldown;
    }

    private boolean canCull(EntityType<?> animalType, long currentTime, long cooldown) {
        Long lastTime = lastCullingTime.get(animalType);
        return lastTime == null || (currentTime - lastTime) >= cooldown;
    }

    private boolean isAnimalEnabled(String configName) {
        try {
            VillagerNPC npc = (VillagerNPC) villager;
            String path = "butcher-animal-management.animals." + configName + ".enabled";
            return npc.getPlugin().getWorkHungerConfig().getBoolean(path, true);
        } catch (Exception e) {
            return true;
        }
    }

    private int getAnimalConfig(String configName, String setting, int defaultValue) {
        try {
            VillagerNPC npc = (VillagerNPC) villager;
            String path = "butcher-animal-management.animals." + configName + "." + setting;
            return npc.getPlugin().getWorkHungerConfig().getInt(path, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String getAnimalBreedingItem(String configName) {
        try {
            VillagerNPC npc = (VillagerNPC) villager;
            String path = "butcher-animal-management.animals." + configName + ".breeding-item";
            return npc.getPlugin().getWorkHungerConfig().getString(path, "WHEAT");
        } catch (Exception e) {
            return "WHEAT";
        }
    }

    private ItemStack getItemStackFromName(String itemName) {
        try {
            return switch (itemName.toUpperCase()) {
                case "WHEAT" -> new ItemStack(Items.WHEAT);
                case "CARROT" -> new ItemStack(Items.CARROT);
                case "WHEAT_SEEDS" -> new ItemStack(Items.WHEAT_SEEDS);
                case "IRON_AXE" -> new ItemStack(Items.IRON_AXE);
                case "STONE_AXE" -> new ItemStack(Items.STONE_AXE);
                case "WOODEN_AXE" -> new ItemStack(Items.WOODEN_AXE);
                case "DIAMOND_AXE" -> new ItemStack(Items.DIAMOND_AXE);
                default -> ItemStack.EMPTY;
            };
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long time) {
        int workDuration = WorkHungerConfig.BUTCHER_ANIMAL_MANAGEMENT_WORK_DURATION.asInt();
        if (workDuration <= 0) workDuration = DEFAULT_WORK_DURATION;
        return timeWorkedSoFar < workDuration;
    }
}