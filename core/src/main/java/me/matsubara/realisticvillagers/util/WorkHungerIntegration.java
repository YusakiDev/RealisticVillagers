package me.matsubara.realisticvillagers.util;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.WorkHungerConfig;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;



/**
 * Integration helper for work behaviors to handle hunger and food requests
 * Supports configurable triggers for when to check hunger and request food
 */
public class WorkHungerIntegration {
    
    private static me.matsubara.realisticvillagers.RealisticVillagers plugin;
    
    // Default values (can be overridden by config)
    private static int hungerDecreasePerWork = 2; // Decrease hunger by 2 each work action
    private static int requestFoodThreshold = 5; // Request food when hunger < 5
    private static int minHungerToWork = 5; // Minimum hunger level required to work
    private static double nearbyVillagerRange = 20.0; // Range to search for food providers
    
    // Trigger configuration (only periodic check works)
    private static boolean periodicCheckEnabled = false;
    private static int periodicCheckInterval = 300; // seconds
    
    /**
     * Initialize with plugin configuration
     */
    public static void initialize(@NotNull me.matsubara.realisticvillagers.RealisticVillagers pluginInstance) {
        plugin = pluginInstance;
        
        // Load settings from dedicated work-hunger config file
        hungerDecreasePerWork = WorkHungerConfig.HUNGER_DECREASE_PER_WORK.asInt();
        requestFoodThreshold = WorkHungerConfig.REQUEST_FOOD_THRESHOLD.asInt();
        minHungerToWork = WorkHungerConfig.MIN_HUNGER_TO_WORK.asInt();
        nearbyVillagerRange = WorkHungerConfig.NEARBY_VILLAGER_RANGE.asDouble();
        
        // Load trigger configuration
        periodicCheckEnabled = WorkHungerConfig.CHECK_TRIGGERS_PERIODIC_ENABLED.asBool();
        periodicCheckInterval = WorkHungerConfig.CHECK_TRIGGERS_PERIODIC_INTERVAL_SECONDS.asInt();
        
        plugin.getLogger().info(String.format("Work-Hunger integration loaded: decrease=%d, threshold=%d, minWork=%d, range=%.1f", 
                hungerDecreasePerWork, requestFoodThreshold, minHungerToWork, nearbyVillagerRange));
        plugin.getLogger().info(String.format("Check triggers: periodic=%b(every %ds)", 
                periodicCheckEnabled, periodicCheckInterval));
    }
    
    /**
     * Check if the work-hunger system is enabled
     */
    public static boolean isEnabled(@NotNull me.matsubara.realisticvillagers.RealisticVillagers plugin) {
        return WorkHungerConfig.ENABLED.asBool();
    }
    
    /**
     * Call this method every time a villager performs a work action
     * This will decrease hunger and potentially request food based on configuration
     * 
     * @param workingVillager The villager who is working
     */
    public static void onVillagerWork(@NotNull IVillagerNPC workingVillager) {
        if (!SimpleItemRequest.isEnabled()) {
            return; // System disabled
        }
        
        // Decrease hunger when working
        int currentHunger = workingVillager.getFoodLevel();
        int newHunger = Math.max(0, currentHunger - hungerDecreasePerWork);
        
        // Set the new hunger level
        setVillagerHunger(workingVillager, newHunger);
        
        // Note: Only periodic check works for food requests
        // Immediate after-work requests don't work reliably
    }
    
    /**
     * Sets the villager's hunger level
     * Uses the villager's setFoodLevel method which updates their VillagerFoodData
     */
    private static void setVillagerHunger(@NotNull IVillagerNPC villager, int newHunger) {
        // Try multiple approaches to set villager hunger
        try {
            // Try reflection on the IVillagerNPC implementation (VillagerNPC has setFoodLevel)
            try {
                java.lang.reflect.Method setFoodLevelMethod = villager.getClass().getMethod("setFoodLevel", int.class);
                setFoodLevelMethod.invoke(villager, newHunger);
                if (plugin != null) {
                    plugin.getLogger().fine(String.format("Villager %s worked hard! Hunger: %d -> %d (via IVillagerNPC reflection)", 
                            villager.getVillagerName(), villager.getFoodLevel() + hungerDecreasePerWork, newHunger));
                }
                return;
            } catch (Exception e) {
                if (plugin != null) {
                    plugin.getLogger().warning(String.format("IVillagerNPC reflection setFoodLevel failed for %s: %s", 
                            villager.getVillagerName(), e.getMessage()));
                }
            }
            
            // Try reflection on bukkit villager as fallback
            try {
                Object villagerEntity = villager.bukkit();
                java.lang.reflect.Method setFoodLevelMethod = villagerEntity.getClass().getMethod("setFoodLevel", int.class);
                setFoodLevelMethod.invoke(villagerEntity, newHunger);
                if (plugin != null) {
                    plugin.getLogger().fine(String.format("Villager %s worked hard! Hunger: %d -> %d (via bukkit reflection)", 
                            villager.getVillagerName(), villager.getFoodLevel() + hungerDecreasePerWork, newHunger));
                }
                return;
            } catch (Exception e) {
                if (plugin != null) {
                    plugin.getLogger().warning(String.format("Bukkit reflection setFoodLevel failed for %s: %s", 
                            villager.getVillagerName(), e.getMessage()));
                }
            }
            
            // Log failure
            if (plugin != null) {
                plugin.getLogger().warning(String.format("Could not set hunger for %s - no working method found", 
                        villager.getVillagerName()));
            }
            
        } catch (Exception e) {
            if (plugin != null) {
                plugin.getLogger().warning(String.format("Error setting hunger for %s: %s", 
                        villager.getVillagerName(), e.getMessage()));
            }
        }
    }
    
    /**
     * Attempts to request food from nearby villagers
     * @param hungryVillager The villager who needs food
     */
    private static void requestFoodFromNearbyVillagers(@NotNull IVillagerNPC hungryVillager) {
        // Find nearby villagers who might have food
        List<IVillagerNPC> nearbyVillagers = findNearbyVillagers(hungryVillager);
        
        if (nearbyVillagers.isEmpty()) {
            if (plugin != null) {
                plugin.getLogger().info(String.format("Villager %s is hungry (food: %d) but no other villagers are nearby to help", 
                        hungryVillager.getVillagerName(), hungryVillager.getFoodLevel()));
            }
            return;
        }
        
        // Try to get food from nearby villagers
        boolean gotFood = SimpleItemRequest.requestFoodFromNearby(
                hungryVillager, 
                nearbyVillagers.toArray(new IVillagerNPC[0])
        );
        
        if (gotFood) {
            if (plugin != null) {
                plugin.getLogger().info(String.format("Villager %s successfully got food from a neighbor after working hard!", 
                        hungryVillager.getVillagerName()));
            }
        } else {
            if (plugin != null) {
                plugin.getLogger().info(String.format("Villager %s is hungry (food: %d) but couldn't get food from %d nearby villagers", 
                        hungryVillager.getVillagerName(), hungryVillager.getFoodLevel(), nearbyVillagers.size()));
            }
        }
    }
    
    /**
     * Finds nearby villagers who might be able to provide food
     * @param villager The villager to search around
     * @return List of nearby villagers
     */
    private static List<IVillagerNPC> findNearbyVillagers(@NotNull IVillagerNPC villager) {
        List<IVillagerNPC> nearbyVillagers = new ArrayList<>();
        
        World world = villager.bukkit().getWorld();
        if (world == null) return nearbyVillagers;
        
        var location = villager.bukkit().getLocation();
        
        // Find all villagers in the world within range
        for (Villager bukkitVillager : world.getEntitiesByClass(Villager.class)) {
            if (bukkitVillager.equals(villager.bukkit())) continue; // Skip self
            
            if (location.distance(bukkitVillager.getLocation()) <= nearbyVillagerRange) {
                // Convert bukkit villager to IVillagerNPC using the plugin's converter
                if (plugin != null) {
                    plugin.getConverter().getNPC(bukkitVillager).ifPresent(nearbyVillagers::add);
                }
            }
        }
        
        return nearbyVillagers;
    }
    
    /**
     * Alternative method for when you have access to the plugin instance
     * This version can properly convert bukkit villagers to IVillagerNPC
     */
    public static void onVillagerWorkWithPlugin(@NotNull IVillagerNPC workingVillager, 
                                              @NotNull me.matsubara.realisticvillagers.RealisticVillagers plugin) {
        
        // Check if work-hunger system is enabled
        if (!WorkHungerConfig.ENABLED.asBool()) {
            return; // System disabled
        }
        
        // Check if villager has enough hunger to work
        int currentHunger = workingVillager.getFoodLevel();
        if (currentHunger < minHungerToWork) {
            plugin.getLogger().info(String.format("Villager %s is too hungry to work! Hunger: %d (min required: %d)", 
                    workingVillager.getVillagerName(), currentHunger, minHungerToWork));
            return; // Too hungry to work
        }
        
        // Decrease hunger when working (independent of food request system)
        int newHunger = Math.max(0, currentHunger - hungerDecreasePerWork);
        
        plugin.getLogger().fine(String.format("onVillagerWorkWithPlugin called for %s: currentHunger=%d, newHunger=%d, minWork=%d", 
                workingVillager.getVillagerName(), currentHunger, newHunger, minHungerToWork));
        
        // Set the new hunger level
        setVillagerHunger(workingVillager, newHunger);
        
        // Generate items from work if enabled (independent check)
        if (WorkHungerConfig.WORK_ITEM_GENERATION_ENABLED.asBool()) {
            generateWorkItems(workingVillager, plugin);
        }
        
        // Note: Only periodic check works for food requests
        // Immediate after-work requests don't work reliably  
        plugin.getLogger().fine(String.format("Work completed for %s. Hunger: %d (minWork: %d)", 
                workingVillager.getVillagerName(), newHunger, minHungerToWork));
    }
    
    /**
     * Requests food using the plugin's converter system
     */
    private static void requestFoodWithPlugin(@NotNull IVillagerNPC hungryVillager, 
                                            @NotNull me.matsubara.realisticvillagers.RealisticVillagers plugin) {
        World world = hungryVillager.bukkit().getWorld();
        if (world == null) return;
        
        List<IVillagerNPC> nearbyVillagers = new ArrayList<>();
        var location = hungryVillager.bukkit().getLocation();
        
        // Find nearby villagers using the plugin's converter
        for (Villager bukkitVillager : world.getEntitiesByClass(Villager.class)) {
            if (bukkitVillager.equals(hungryVillager.bukkit())) continue; // Skip self
            
            if (location.distance(bukkitVillager.getLocation()) <= nearbyVillagerRange) {
                plugin.getConverter().getNPC(bukkitVillager).ifPresent(nearbyVillagers::add);
            }
        }
        
        if (nearbyVillagers.isEmpty()) {
            plugin.getLogger().fine(String.format("Villager %s is hungry but no other villagers are nearby to help", 
                    hungryVillager.getVillagerName()));
            return;
        }
        
        // Try to get food from nearby villagers
        boolean gotFood = SimpleItemRequest.requestFoodFromNearby(
                hungryVillager, 
                nearbyVillagers.toArray(new IVillagerNPC[0])
        );
        
        if (gotFood) {
            plugin.getLogger().info(String.format("Villager %s successfully got food from a neighbor after working hard!", 
                    hungryVillager.getVillagerName()));
        } else {
            plugin.getLogger().fine(String.format("Villager %s is hungry but couldn't get food from nearby villagers", 
                    hungryVillager.getVillagerName()));
        }
    }
    
    // Removed onVillagerRest - doesn't work reliably
    
    // Removed onVillagerInteraction - doesn't work reliably
    
    /**
     * Periodic hunger check (call this from a scheduled task)
     */
    public static void periodicHungerCheck(@NotNull List<IVillagerNPC> villagers, @NotNull me.matsubara.realisticvillagers.RealisticVillagers plugin) {
        if (!SimpleItemRequest.isEnabled() || !periodicCheckEnabled) {
            return;
        }
        
        for (IVillagerNPC villager : villagers) {
            if (villager.getFoodLevel() < requestFoodThreshold) {
                requestFoodWithPlugin(villager, plugin);
            }
        }
    }
    
    /**
     * Configuration getters
     */
    public static int getHungerDecreasePerWork() {
        return hungerDecreasePerWork;
    }
    
    public static int getRequestFoodThreshold() {
        return requestFoodThreshold;
    }
    
    public static double getNearbyVillagerRange() {
        return nearbyVillagerRange;
    }
    
    public static int getMinHungerToWork() {
        return minHungerToWork;
    }
    
    // Removed unused check trigger getters
    
    public static boolean isPeriodicCheckEnabled() {
        return periodicCheckEnabled;
    }
    
    public static int getPeriodicCheckInterval() {
        return periodicCheckInterval;
    }
    
    /**
     * Generates profession-specific items and adds them to villager's inventory
     * This simulates the villager "creating" trade goods through their work
     */
    private static void generateWorkItems(@NotNull IVillagerNPC workingVillager, 
                                        @NotNull me.matsubara.realisticvillagers.RealisticVillagers plugin) {
        
        if (!(workingVillager.bukkit() instanceof Villager bukkit)) {
            return;
        }
        
        Villager.Profession profession = bukkit.getProfession();
        if (profession == Villager.Profession.NONE || profession == Villager.Profession.NITWIT) {
            return; // No work items for unemployed villagers
        }
        
        // Generate one random item with quantity and max limit checking
        generateRandomItem(workingVillager, bukkit, plugin);
    }
    
    /**
     * Generates one random item with quantity and max limit checking
     */
    private static void generateRandomItem(@NotNull IVillagerNPC workingVillager, @NotNull Villager bukkit, 
                                         @NotNull me.matsubara.realisticvillagers.RealisticVillagers plugin) {
        
        List<String> possibleItems;
        
        // Use actual trades if enabled, otherwise use profession defaults
        if (WorkHungerConfig.WORK_ITEM_GENERATION_CHECK_ACTUAL_TRADES.asBool()) {
            possibleItems = getItemsFromActualTrades(bukkit);
        } else {
            possibleItems = getProfessionTradeItems(bukkit.getProfession());
        }
        
        if (possibleItems.isEmpty()) {
            plugin.getLogger().fine(String.format("No items to generate for %s", 
                    workingVillager.getVillagerName()));
            return;
        }
        
        // Pick one random item to generate
        String randomItem = possibleItems.get(ThreadLocalRandom.current().nextInt(possibleItems.size()));
        
        // Parse "ITEM_NAME:quantity:max_limit" format
        String[] parts = randomItem.split(":");
        if (parts.length != 3) {
            plugin.getLogger().warning(String.format("Invalid item format: %s (expected ITEM:quantity:max_limit)", randomItem));
            return;
        }
        
        try {
            Material material = Material.valueOf(parts[0]);
            int quantityPerWork = Integer.parseInt(parts[1]);
            int maxLimit = Integer.parseInt(parts[2]);
            
            // Check current amount in inventory
            int currentAmount = countItemInInventory(bukkit, material);
            
            // Don't generate if already at or above max
            if (currentAmount >= maxLimit) {
                plugin.getLogger().fine(String.format("Villager %s has %d %s (max: %d), not generating more", 
                        workingVillager.getVillagerName(), currentAmount, material.name(), maxLimit));
                return;
            }
            
            // Don't exceed max limit
            int actualQuantity = Math.min(quantityPerWork, maxLimit - currentAmount);
            
            if (actualQuantity > 0) {
                // Check if villager has enough inventory space for this specific material
                if (!hasInventorySpace(bukkit, material, actualQuantity)) {
                    plugin.getLogger().warning(String.format("Villager %s inventory full! Cannot generate %dx %s (maxStack: %d, total would be: %d/%d)", 
                            workingVillager.getVillagerName(), actualQuantity, material.name(), 
                            material.getMaxStackSize(), currentAmount + actualQuantity, maxLimit));
                    return; // Don't generate items if inventory is full
                }
                
                ItemStack item = new ItemStack(material, actualQuantity);
                HashMap<Integer, ItemStack> leftover = bukkit.getInventory().addItem(item);
                
                if (leftover.isEmpty()) {
                    plugin.getLogger().fine(String.format("Villager %s generated %dx %s from work (total: %d/%d)", 
                            workingVillager.getVillagerName(), actualQuantity, material.name(), 
                            currentAmount + actualQuantity, maxLimit));
                } else {
                    // Log partial success and what was lost
                    int lostAmount = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
                    plugin.getLogger().warning(String.format("Villager %s inventory partially full! Generated %dx %s, lost %dx %s", 
                            workingVillager.getVillagerName(), actualQuantity - lostAmount, material.name(), 
                            lostAmount, material.name()));
                }
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(String.format("Invalid item: %s (%s)", randomItem, e.getMessage()));
        }
    }
    
    /**
     * Counts how many of a specific item are in the villager's inventory
     */
    private static int countItemInInventory(@NotNull Villager villager, @NotNull Material material) {
        int count = 0;
        for (ItemStack item : villager.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }
    
    /**
     * Checks if villager has enough inventory space for the specified material and quantity
     */
    private static boolean hasInventorySpace(@NotNull Villager villager, @NotNull Material material, int itemsToAdd) {
        Inventory inventory = villager.getInventory();
        int maxStackSize = material.getMaxStackSize();
        int availableSpace = 0;
        
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType().isAir()) {
                // Empty slot can fit a full stack of this material
                availableSpace += maxStackSize;
            } else if (item.getType() == material && item.getAmount() < maxStackSize) {
                // Partial stack of same material - can add more
                availableSpace += (maxStackSize - item.getAmount());
            }
            // Different material stacks don't help us
        }
        
        return availableSpace >= itemsToAdd;
    }
    
    /**
     * Checks if villager has enough inventory space for any items (general purpose)
     */
    private static boolean hasInventorySpace(@NotNull Villager villager, int itemsToAdd) {
        Inventory inventory = villager.getInventory();
        int emptySlots = 0;
        
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType().isAir()) {
                emptySlots++;
            }
        }
        
        // For general check, assume worst case (non-stackable items = 1 per slot)
        return emptySlots >= itemsToAdd;
    }
    
    /**
     * Gets the list of trade items a profession can generate through work
     * Returns items in "MATERIAL:quantity:max_limit" format
     * Empty list means the profession can naturally generate their own trade items
     */
    private static List<String> getProfessionTradeItems(Villager.Profession profession) {
        // Try config first, fall back to hardcoded defaults
        List<String> configItems = WorkHungerConfig.WORK_ITEM_GENERATION_PROFESSION_ITEMS.asStringList();
        
        // Check if we have profession-specific config
        if (!configItems.isEmpty()) {
            // Config format is different - this is handled by the config system
            // Return empty for now since generateRandomItem reads from config directly
            return Collections.emptyList();
        }
        
        // Hardcoded defaults with 3-part format "ITEM:quantity:max_limit"
        if (profession == Villager.Profession.ARMORER) {
            return Arrays.asList(
                "EMERALD:2:64",
                "IRON_HELMET:1:4", "IRON_CHESTPLATE:1:4", "IRON_LEGGINGS:1:4", "IRON_BOOTS:1:4",
                "CHAINMAIL_HELMET:1:2", "CHAINMAIL_CHESTPLATE:1:2", "CHAINMAIL_LEGGINGS:1:2", "CHAINMAIL_BOOTS:1:2",
                "DIAMOND_HELMET:1:2", "DIAMOND_CHESTPLATE:1:2", "DIAMOND_LEGGINGS:1:2", "DIAMOND_BOOTS:1:2",
                "SHIELD:1:3", "BELL:1:2"
            );
        } else if (profession == Villager.Profession.BUTCHER) {
            return Arrays.asList(
                "EMERALD:2:64",
                "RABBIT_STEW:1:5", "COOKED_PORKCHOP:3:15", "COOKED_CHICKEN:3:15"
            );
        } else if (profession == Villager.Profession.CARTOGRAPHER) {
            return Arrays.asList(
                "EMERALD:2:64",
                "MAP:1:8", "ITEM_FRAME:2:10", "WHITE_BANNER:1:5"
            );
        } else if (profession == Villager.Profession.CLERIC) {
            return Arrays.asList(
                "EMERALD:2:64",
                "REDSTONE:4:32", "LAPIS_LAZULI:3:24", "GLOWSTONE:2:16"
            );
        } else if (profession == Villager.Profession.FARMER) {
            return Collections.emptyList();       // Can farm/cook naturally
        } else if (profession == Villager.Profession.FISHERMAN) {
            return Collections.emptyList();    // Can fish/cook naturally
        } else if (profession == Villager.Profession.FLETCHER) {
            return Arrays.asList(
                "EMERALD:2:64",
                "ARROW:16:128", "BOW:1:3", "CROSSBOW:1:3", "TIPPED_ARROW:5:40"
            );
        } else if (profession == Villager.Profession.LEATHERWORKER) {
            return Arrays.asList(
                "EMERALD:2:64",
                "LEATHER_HELMET:1:4", "LEATHER_CHESTPLATE:1:4", "LEATHER_LEGGINGS:1:4", "LEATHER_BOOTS:1:4",
                "LEATHER_HORSE_ARMOR:1:2", "SADDLE:1:2"
            );
        } else if (profession == Villager.Profession.LIBRARIAN) {
            return Arrays.asList(
                "EMERALD:2:64",
                "ENCHANTED_BOOK:1:5", "BOOKSHELF:1:8", "LANTERN:1:6", "CLOCK:1:3", "COMPASS:1:3", "NAME_TAG:1:5"
            );
        } else if (profession == Villager.Profession.MASON) {
            return Arrays.asList(
                "EMERALD:2:64",
                "BRICK:10:80", "CHISELED_STONE_BRICKS:4:32", "POLISHED_ANDESITE:4:32", 
                "POLISHED_GRANITE:4:32", "POLISHED_DIORITE:4:32", "TERRACOTTA:8:64"
            );
        } else if (profession == Villager.Profession.SHEPHERD) {
            return Arrays.asList(
                "EMERALD:2:64",
                "WHITE_WOOL:3:24", "BLACK_WOOL:3:24", "GRAY_WOOL:3:24", "LIGHT_GRAY_WOOL:3:24", "BROWN_WOOL:3:24"
            );
        } else if (profession == Villager.Profession.TOOLSMITH) {
            return Arrays.asList(
                "EMERALD:2:64",
                "STONE_AXE:1:3", "STONE_SHOVEL:1:3", "STONE_PICKAXE:1:3", "STONE_HOE:1:3",
                "IRON_AXE:1:3", "IRON_SHOVEL:1:3", "IRON_PICKAXE:1:3", "IRON_HOE:1:3",
                "DIAMOND_AXE:1:2", "DIAMOND_SHOVEL:1:2", "DIAMOND_PICKAXE:1:2", "DIAMOND_HOE:1:2"
            );
        } else if (profession == Villager.Profession.WEAPONSMITH) {
            return Arrays.asList(
                "EMERALD:2:64",
                "IRON_AXE:1:3", "IRON_SWORD:1:3", "DIAMOND_SWORD:1:2", "DIAMOND_AXE:1:2", "BELL:1:2"
            );
        } else {
            return Collections.emptyList();
        }
    }
    
    
    
    /**
     * Gets items from villager's actual trade recipes in 3-part format
     */
    private static List<String> getItemsFromActualTrades(@NotNull Villager villager) {
        List<String> items = new ArrayList<>();
        
        for (MerchantRecipe recipe : villager.getRecipes()) {
            ItemStack result = recipe.getResult();
            if (result != null && !result.getType().isAir()) {
                // Don't skip emeralds anymore since we use unified format
                if (!isSpecialTradeItem(result.getType())) {
                    // Use 3-part format with reasonable defaults for max limits
                    int quantity = result.getAmount();
                    int maxLimit = calculateReasonableMaxLimit(result.getType(), quantity);
                    items.add(result.getType().name() + ":" + quantity + ":" + maxLimit);
                }
            }
        }
        
        return items;
    }
    
    /**
     * Calculates a reasonable max limit for items based on their type and quantity
     */
    private static int calculateReasonableMaxLimit(@NotNull Material material, int baseQuantity) {
        return switch (material) {
            case EMERALD -> 64; // Standard max emeralds
            case ARROW, TIPPED_ARROW -> Math.max(baseQuantity * 8, 128); // Arrows can be stacked high
            case BREAD, COOKED_PORKCHOP, COOKED_CHICKEN, COOKED_BEEF, RABBIT_STEW -> Math.max(baseQuantity * 5, 20); // Food items
            case WHITE_WOOL, BLACK_WOOL, GRAY_WOOL, LIGHT_GRAY_WOOL, BROWN_WOOL, RED_WOOL, ORANGE_WOOL, YELLOW_WOOL, LIME_WOOL, GREEN_WOOL, CYAN_WOOL, LIGHT_BLUE_WOOL, BLUE_WOOL, PURPLE_WOOL, MAGENTA_WOOL, PINK_WOOL -> Math.max(baseQuantity * 8, 24); // Wool variants
            case BRICK, TERRACOTTA -> Math.max(baseQuantity * 8, 80); // Building blocks
            case REDSTONE, LAPIS_LAZULI, GLOWSTONE -> Math.max(baseQuantity * 8, 32); // Materials
            case ENCHANTED_BOOK, BOOKSHELF -> Math.max(baseQuantity * 5, 8); // Books
            default -> {
                // For armor, tools, and other items: base * 4 or minimum 4, whichever is higher
                if (isArmorOrTool(material)) {
                    yield Math.max(baseQuantity * 4, 4);
                } else {
                    yield Math.max(baseQuantity * 6, 10); // General items
                }
            }
        };
    }
    
    /**
     * Checks if a material is armor or a tool
     */
    private static boolean isArmorOrTool(@NotNull Material material) {
        String name = material.name();
        return name.contains("_HELMET") || name.contains("_CHESTPLATE") || 
               name.contains("_LEGGINGS") || name.contains("_BOOTS") ||
               name.contains("_SWORD") || name.contains("_AXE") || 
               name.contains("_PICKAXE") || name.contains("_SHOVEL") || 
               name.contains("_HOE") || name.equals("BOW") || name.equals("CROSSBOW") ||
               name.equals("SHIELD");
    }
    
    /**
     * Checks if an item is a special trade item that shouldn't be generated
     */
    private static boolean isSpecialTradeItem(Material material) {
        return switch (material) {
            case FILLED_MAP, ENDER_PEARL, EXPERIENCE_BOTTLE -> true;
            default -> false;
        };
    }
}