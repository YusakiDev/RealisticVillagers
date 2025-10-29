package me.matsubara.realisticvillagers.util;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.WorkHungerConfig;
import org.bukkit.Location;
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
 * Integration helper for work behaviors to handle hunger and item generation.
 * Supports:
 * - Hunger decrease when villagers work
 * - Work-based item generation
 * - Configurable profession-specific trade items
 */
public class WorkHungerIntegration {

    private static RealisticVillagers plugin;

    // Default values (overridden by config)
    private static int hungerDecreasePerWork = 1;
    private static int requestFoodThreshold = 15;
    private static int minHungerToWork = 5;
    private static double nearbyVillagerRange = 40.0;

    /**
     * Initialize with plugin configuration
     */
    public static void initialize(@NotNull RealisticVillagers pluginInstance) {
        plugin = pluginInstance;

        // Load settings from work-hunger config file
        hungerDecreasePerWork = WorkHungerConfig.HUNGER_DECREASE_PER_WORK.asInt();
        requestFoodThreshold = WorkHungerConfig.REQUEST_FOOD_THRESHOLD.asInt();
        minHungerToWork = WorkHungerConfig.MIN_HUNGER_TO_WORK.asInt();
        nearbyVillagerRange = WorkHungerConfig.VILLAGER_REQUESTS_NEARBY_VILLAGER_RANGE.asDouble();

        // Initialize sub-systems
        SimpleItemRequest.initialize(pluginInstance);
        PhysicalItemDelivery.initialize(pluginInstance);

        plugin.getLogger().info(String.format("Work-Hunger integration loaded: decrease=%d, threshold=%d, minWork=%d, range=%.1f",
                hungerDecreasePerWork, requestFoodThreshold, minHungerToWork, nearbyVillagerRange));
    }

    /**
     * Check if the work-hunger system is enabled
     */
    public static boolean isEnabled() {
        return WorkHungerConfig.ENABLED.asBool();
    }

    /**
     * Call this method every time a villager performs a work action.
     * This will decrease hunger and optionally generate work items.
     *
     * @param workingVillager The villager who is working
     * @param plugin The plugin instance
     */
    public static void onVillagerWorkWithPlugin(@NotNull IVillagerNPC workingVillager,
                                              @NotNull RealisticVillagers plugin) {

        // Check if work-hunger system is enabled
        if (!WorkHungerConfig.ENABLED.asBool()) {
            return; // System disabled
        }

        // Check if villager has enough hunger to work
        int currentHunger = workingVillager.getFoodLevel();
        if (currentHunger < minHungerToWork) {
            plugin.getLogger().fine(String.format("Villager %s is too hungry to work! Hunger: %d (min required: %d)",
                    workingVillager.getVillagerName(), currentHunger, minHungerToWork));
            return; // Too hungry to work
        }

        // Decrease hunger when working
        int newHunger = Math.max(0, currentHunger - hungerDecreasePerWork);

        plugin.getLogger().fine(String.format("Villager %s working: currentHunger=%d, newHunger=%d, minWork=%d",
                workingVillager.getVillagerName(), currentHunger, newHunger, minHungerToWork));

        // Set the new hunger level
        setVillagerHunger(workingVillager, newHunger, plugin);

        // Generate items from work if enabled
        if (WorkHungerConfig.WORK_ITEM_GENERATION_ENABLED.asBool()) {
            generateWorkItems(workingVillager, plugin);
        }

        plugin.getLogger().fine(String.format("Work completed for %s. Hunger: %d (minWork: %d)",
                workingVillager.getVillagerName(), newHunger, minHungerToWork));
    }

    /**
     * Sets the villager's hunger level using the IVillagerNPC setFoodLevel method
     */
    private static void setVillagerHunger(@NotNull IVillagerNPC villager, int newHunger, @NotNull RealisticVillagers plugin) {
        try {
            // IVillagerNPC has setFoodLevel method which updates VillagerFoodData
            java.lang.reflect.Method setFoodLevelMethod = villager.getClass().getMethod("setFoodLevel", int.class);
            setFoodLevelMethod.invoke(villager, newHunger);
            plugin.getLogger().fine(String.format("Villager %s worked hard! Hunger: %d -> %d",
                    villager.getVillagerName(), villager.getFoodLevel() + hungerDecreasePerWork, newHunger));
        } catch (Exception e) {
            plugin.getLogger().warning(String.format("Could not set hunger for %s: %s",
                    villager.getVillagerName(), e.getMessage()));
        }
    }

    /**
     * Generates profession-specific items and adds them to villager's inventory.
     * This simulates the villager "creating" trade goods through their work.
     */
    private static void generateWorkItems(@NotNull IVillagerNPC workingVillager,
                                        @NotNull RealisticVillagers plugin) {

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
                                         @NotNull RealisticVillagers plugin) {

        List<String> possibleItems;

        // Use actual trades if enabled, otherwise use profession defaults
        if (WorkHungerConfig.WORK_ITEM_GENERATION_CHECK_ACTUAL_TRADES.asBool()) {
            possibleItems = getItemsFromActualTrades(bukkit);
        } else {
            possibleItems = getProfessionTradeItemsFromConfig(bukkit.getProfession(), plugin);
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
                    plugin.getLogger().fine(String.format("Villager %s inventory full! Cannot generate %dx %s",
                            workingVillager.getVillagerName(), actualQuantity, material.name()));
                    return; // Don't generate items if inventory is full
                }

                ItemStack item = new ItemStack(material, actualQuantity);
                HashMap<Integer, ItemStack> leftover = bukkit.getInventory().addItem(item);

                int generated = actualQuantity;
                if (leftover.isEmpty()) {
                    plugin.getLogger().fine(String.format("Villager %s generated %dx %s from work (total: %d/%d)",
                            workingVillager.getVillagerName(), actualQuantity, material.name(),
                            currentAmount + actualQuantity, maxLimit));
                } else {
                    // Log partial success
                    int lostAmount = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
                    generated -= lostAmount;
                    plugin.getLogger().warning(String.format("Villager %s inventory partially full! Generated %dx %s, lost %dx %s",
                            workingVillager.getVillagerName(), actualQuantity - lostAmount, material.name(),
                            lostAmount, material.name()));
                }

                if (generated > 0 && plugin.getTradingConfig().isEnabled()) {
                    plugin.getTradeFilter().refreshTrades(bukkit);
                }
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(String.format("Invalid item: %s (%s)", randomItem, e.getMessage()));
        }
    }

    /**
     * Gets profession trade items from config file
     */
    private static List<String> getProfessionTradeItemsFromConfig(Villager.Profession profession, @NotNull RealisticVillagers plugin) {
        List<String> items = new ArrayList<>();

        try {
            // Access config section for this profession
            String professionKey = "work-item-generation.profession-items." + profession.name();
            List<String> configItems = plugin.getWorkHungerConfig().getStringList(professionKey);

            if (configItems != null && !configItems.isEmpty()) {
                items.addAll(configItems);
            }
        } catch (Exception e) {
            plugin.getLogger().warning(String.format("Could not load profession items for %s: %s",
                    profession.name(), e.getMessage()));
        }

        return items;
    }

    /**
     * Gets items from villager's actual trade recipes in 3-part format
     */
    private static List<String> getItemsFromActualTrades(@NotNull Villager villager) {
        List<String> items = new ArrayList<>();

        for (MerchantRecipe recipe : villager.getRecipes()) {
            ItemStack result = recipe.getResult();
            if (result != null && !result.getType().isAir()) {
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
            case EMERALD -> 64;
            case ARROW, TIPPED_ARROW -> Math.max(baseQuantity * 8, 128);
            case BREAD, COOKED_PORKCHOP, COOKED_CHICKEN, COOKED_BEEF, RABBIT_STEW -> Math.max(baseQuantity * 5, 20);
            case BRICK, TERRACOTTA -> Math.max(baseQuantity * 8, 80);
            case REDSTONE, LAPIS_LAZULI, GLOWSTONE -> Math.max(baseQuantity * 8, 32);
            case ENCHANTED_BOOK, BOOKSHELF -> Math.max(baseQuantity * 5, 8);
            default -> {
                if (isArmorOrTool(material)) {
                    yield Math.max(baseQuantity * 4, 4);
                } else if (isWool(material)) {
                    yield Math.max(baseQuantity * 8, 24);
                } else {
                    yield Math.max(baseQuantity * 6, 10);
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
               name.equals("SHIELD") || name.equals("TRIDENT");
    }

    /**
     * Checks if a material is wool
     */
    private static boolean isWool(@NotNull Material material) {
        return material.name().contains("_WOOL");
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
        }

        return availableSpace >= itemsToAdd;
    }

    public static int getMinHungerToWork() {
        return minHungerToWork;
    }

    /**
     * Check if vanilla restocking should be prevented for a villager
     *
     * RULE: If work-item-generation is enabled, vanilla restocking is ALWAYS disabled
     * This ensures villagers only restock through work-based item generation
     */
    public static boolean shouldBlockVanillaRestock(@NotNull IVillagerNPC villager) {
        // Simple rule: work items enabled = vanilla restock disabled
        return WorkHungerConfig.WORK_ITEM_GENERATION_ENABLED.asBool();
    }

    /**
     * Prevent vanilla restocking by setting all trades to max uses
     * Should be called when vanilla restock should be blocked
     */
    public static void blockVanillaRestock(@NotNull Villager villager) {
        for (MerchantRecipe recipe : villager.getRecipes()) {
            recipe.setUses(recipe.getMaxUses());
        }
    }

    /**
     * Periodic hunger check - call from scheduled task
     * Finds hungry villagers and attempts to get food from nearby villagers
     */
    public static void periodicHungerCheck(@NotNull List<IVillagerNPC> villagers, @NotNull RealisticVillagers pluginInstance) {
        if (!SimpleItemRequest.isEnabled() || !WorkHungerConfig.ENABLED.asBool()) {
            return;
        }

        for (IVillagerNPC villager : villagers) {
            if (villager.getFoodLevel() < requestFoodThreshold) {
                requestFoodFromNearbyVillagers(villager, pluginInstance);
            }
        }
    }

    /**
     * Request food from nearby villagers
     */
    private static void requestFoodFromNearbyVillagers(@NotNull IVillagerNPC hungryVillager,
                                                       @NotNull RealisticVillagers pluginInstance) {
        List<IVillagerNPC> nearbyVillagers = findNearbyVillagers(hungryVillager, pluginInstance);

        if (nearbyVillagers.isEmpty()) {
            pluginInstance.getLogger().fine(String.format("Villager %s is hungry (food: %d) but no nearby villagers",
                    hungryVillager.getVillagerName(), hungryVillager.getFoodLevel()));
            return;
        }

        boolean gotFood = SimpleItemRequest.requestFoodFromNearby(
                hungryVillager,
                nearbyVillagers.toArray(new IVillagerNPC[0])
        );

        if (gotFood) {
            pluginInstance.getLogger().info(String.format("Villager %s got food from a neighbor!",
                    hungryVillager.getVillagerName()));
        } else {
            pluginInstance.getLogger().fine(String.format("Villager %s couldn't get food from %d nearby villagers",
                    hungryVillager.getVillagerName(), nearbyVillagers.size()));
        }
    }

    /**
     * Find nearby villagers within range
     */
    private static List<IVillagerNPC> findNearbyVillagers(@NotNull IVillagerNPC villager,
                                                          @NotNull RealisticVillagers pluginInstance) {
        List<IVillagerNPC> nearbyVillagers = new ArrayList<>();

        World world = villager.bukkit().getWorld();
        if (world == null) {
            return nearbyVillagers;
        }

        Location location = villager.bukkit().getLocation();
        if (location == null) {
            return nearbyVillagers;
        }

        // Find all villagers in range
        for (Villager bukkitVillager : world.getEntitiesByClass(Villager.class)) {
            if (bukkitVillager.equals(villager.bukkit())) {
                continue; // Skip self
            }

            if (location.distance(bukkitVillager.getLocation()) <= nearbyVillagerRange) {
                // Convert bukkit villager to IVillagerNPC
                pluginInstance.getConverter().getNPC(bukkitVillager).ifPresent(nearbyVillagers::add);
            }
        }

        return nearbyVillagers;
    }
}
