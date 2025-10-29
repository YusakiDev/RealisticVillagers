package me.matsubara.realisticvillagers.manager.ai.tools.impl;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.ExpectingType;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.manager.ExpectingManager;
import me.matsubara.realisticvillagers.manager.ai.tools.AITool;
import me.matsubara.realisticvillagers.manager.ai.tools.AIToolResult;
import me.matsubara.realisticvillagers.manager.ai.tools.ToolCategory;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Item-related AI tools for villagers.
 */
public class ItemTools {

    private static final RealisticVillagers PLUGIN = JavaPlugin.getPlugin(RealisticVillagers.class);

    /**
     * Tool that allows the villager to give an item to the player.
     * Villager walks to player and drops the item physically.
     */
    public static class GiveItemTool implements AITool {

        @Override
        public @NotNull String getName() {
            return "give_item";
        }

        @Override
        public @NotNull String getDescription() {
            return "Give an item from inventory to the player (MUST check inventory first!)";
        }

        @Override
        public @NotNull ToolCategory getCategory() {
            return ToolCategory.ITEMS;
        }

        @Override
        public @NotNull Map<String, String> getParameters() {
            Map<String, String> params = new HashMap<>();
            params.put("item", "The material name of the item to give (e.g., 'BREAD', 'WHEAT')");
            params.put("quantity", "The number of items to give (default: 1)");
            return params;
        }

        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            // Can't give items while fighting or in raid
            if (villager.isFighting()) return false;
            if (villager.isInsideRaid()) return false;

            // Must have item parameter
            return args.containsKey("item");
        }

        @Override
        public @NotNull AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            String itemName = (String) args.get("item");
            int quantity = 1;

            if (args.containsKey("quantity")) {
                Object quantityObj = args.get("quantity");
                if (quantityObj instanceof Number) {
                    quantity = ((Number) quantityObj).intValue();
                }
            }

            // Parse material
            Material material;
            try {
                material = Material.valueOf(itemName.toUpperCase());
            } catch (IllegalArgumentException e) {
                return AIToolResult.failure("Invalid item type: " + itemName);
            }

            // Check villager inventory for the item
            Inventory inventory = getVillagerInventory(villager);
            if (inventory == null) {
                return AIToolResult.failure("Cannot access villager inventory.");
            }

            ItemStack[] contents = inventory.getContents();
            int available = 0;
            for (ItemStack stack : contents) {
                if (stack != null && stack.getType() == material) {
                    available += stack.getAmount();
                }
            }

            if (available < quantity) {
                return AIToolResult.failure("Don't have enough " + itemName + " (have " + available + ", need " + quantity + ")");
            }

            // Remove from villager inventory
            int remaining = quantity;
            List<ItemStack> itemsToGive = new ArrayList<>();
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack stack = contents[i];
                if (stack != null && stack.getType() == material) {
                    int toRemove = Math.min(remaining, stack.getAmount());
                    ItemStack removedStack = stack.clone();
                    removedStack.setAmount(toRemove);
                    itemsToGive.add(removedStack);
                    stack.setAmount(stack.getAmount() - toRemove);
                    remaining -= toRemove;

                    if (stack.getAmount() <= 0) {
                        contents[i] = null;
                    }
                }
            }

            inventory.setContents(contents);

            // Schedule walk and drop on entity scheduler (Folia safe)
            scheduleWalkAndDrop(villager, player, itemsToGive);

            return AIToolResult.success("Walking over to give " + quantity + " " + itemName + " to " + player.getName());
        }

        /**
         * Schedules the villager to walk towards the player and drop the item.
         * Uses native Minecraft brain system with distance monitoring.
         * Folia-compatible: runs on entity scheduler.
         */
        private void scheduleWalkAndDrop(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull List<ItemStack> itemsToDrop) {
            org.bukkit.entity.Entity bukkitEntity = villager.bukkit();
            if (bukkitEntity == null || !bukkitEntity.isValid()) {
                return;
            }

            // Schedule on entity scheduler (Folia safe)
            PLUGIN.getFoliaLib().getScheduler().runAtEntity(bukkitEntity, task -> {
                // Start walking towards player using native Minecraft brain system
                villager.setWalkTargetToEntity(player, 1);

                // Monitor distance and drop when close enough or timeout
                monitorDistanceAndDrop(villager, player, itemsToDrop);
            });
        }

        /**
         * Monitors the distance between villager and player, dropping the item when close enough or timeout occurs.
         * Drop threshold: 1.5 blocks
         * Timeout: 20 seconds (400 ticks)
         */
        private void monitorDistanceAndDrop(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull List<ItemStack> itemsToDrop) {
            org.bukkit.entity.Entity entity = villager.bukkit();
            if (entity == null || !entity.isValid()) {
                return;
            }

            final double CLOSE_DISTANCE = 1.5;
            final long MAX_TICKS = 400L; // 20 seconds

            // Use an array to store mutable tick counter
            final long[] tickCounter = {0};

            PLUGIN.getFoliaLib().getScheduler().runAtEntityTimer(entity, task -> {
                if (!entity.isValid() || !player.isOnline()) {
                    task.cancel();
                    return;
                }

                double distance = entity.getLocation().distance(player.getLocation());

                // If close enough or timeout reached, drop the item
                if (distance < CLOSE_DISTANCE || tickCounter[0] >= MAX_TICKS) {
                    task.cancel();
                    dropItemForPlayer(villager, itemsToDrop);
                    // Play happy effect
                    entity.playEffect(org.bukkit.EntityEffect.VILLAGER_HAPPY);
                    return;
                }

                tickCounter[0]++;
            }, 0L, 1L); // Run every tick
        }

        /**
         * Drops the item for the player.
         * Uses the same method as hardcoded gifting, with identifier to prevent villager from picking it up.
         */
        private void dropItemForPlayer(@NotNull IVillagerNPC villager, @NotNull List<ItemStack> itemsToDrop) {
            // Drop using the villager's drop method with ignoreItemKey to mark it
            for (ItemStack stack : itemsToDrop) {
                if (stack == null || stack.getAmount() <= 0) {
                    continue;
                }
                villager.drop(stack.clone(), PLUGIN.getIgnoreItemKey());
            }
        }
    }

    /**
     * Tool that checks what items the villager has in inventory.
     */
    public static class CheckInventoryTool implements AITool {

        @Override
        public @NotNull String getName() {
            return "check_inventory";
        }

        @Override
        public @NotNull String getDescription() {
            return "Check what items you have in your inventory";
        }

        @Override
        public @NotNull ToolCategory getCategory() {
            return ToolCategory.ITEMS;
        }

        @Override
        public @NotNull Map<String, String> getParameters() {
            return java.util.Collections.emptyMap();
        }

        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            return true; // Can always check inventory
        }

        @Override
        public @NotNull AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            Inventory inventory = getVillagerInventory(villager);
            if (inventory == null) {
                return AIToolResult.failure("Cannot access villager inventory.");
            }

            ItemStack[] contents = inventory.getContents();
            Map<Material, Integer> itemCounts = new HashMap<>();

            for (ItemStack stack : contents) {
                if (stack != null && stack.getType() != Material.AIR) {
                    itemCounts.merge(stack.getType(), stack.getAmount(), Integer::sum);
                }
            }

            if (itemCounts.isEmpty()) {
                return AIToolResult.success("Your inventory is empty");
            }

            StringBuilder message = new StringBuilder("You have: ");
            itemCounts.forEach((material, count) -> {
                message.append(count).append("x ").append(material.name()).append(", ");
            });

            // Remove trailing comma and space
            if (message.length() > 10) {
                message.setLength(message.length() - 2);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("items", itemCounts.toString());

            return AIToolResult.success(message.toString(), data);
        }
    }

    /**
     * Tool that prepares the villager to receive gifts from the player.
     */
    public static class PrepareForGiftTool implements AITool {

        @Override
        public @NotNull String getName() {
            return "prepare_for_gift";
        }

        @Override
        public @NotNull String getDescription() {
            return "Activate gift system to receive items from the player (requires DROP gift mode)";
        }

        @Override
        public @NotNull ToolCategory getCategory() {
            return ToolCategory.ITEMS;
        }

        @Override
        public @NotNull Map<String, String> getParameters() {
            return java.util.Collections.emptyMap();
        }

        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            // Can't receive gifts while fighting or in raid
            if (villager.isFighting()) return false;
            if (villager.isInsideRaid()) return false;

            // Can't if already expecting something
            if (villager.isExpecting()) return false;

            // Check if gift mode is compatible with AI tool (only support drop mode)
            ExpectingManager expectingManager = PLUGIN.getExpectingManager();
            if (!expectingManager.getGiftModeFromConfig().drop()) {
                return false;
            }

            // Another villager already expecting from this player?
            IVillagerNPC other = expectingManager.get(player.getUniqueId());
            if (other != null && other.isExpecting() && other != villager) {
                return false;
            }

            // Gift cooldown check (if villager entity available)
            if (villager.bukkit() instanceof org.bukkit.entity.Villager bukkitVillager) {
                if (!PLUGIN.getCooldownManager().canInteract(player, bukkitVillager, "gift")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public @NotNull AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            ExpectingManager expectingManager = PLUGIN.getExpectingManager();
            int expectSeconds = Config.TIME_TO_EXPECT.asInt();

            villager.startExpectingFrom(ExpectingType.GIFT, player.getUniqueId(), expectSeconds);
            expectingManager.expect(player.getUniqueId(), villager);

            return AIToolResult.success("Ready to receive gifts from " + player.getName() + " (drop items near me for "
                    + expectSeconds + " seconds)");
        }
    }

    /**
     * Tool that checks what item the player is currently holding.
     */
    public static class CheckPlayerItemTool implements AITool {

        @Override
        public @NotNull String getName() {
            return "check_player_item";
        }

        @Override
        public @NotNull String getDescription() {
            return "Check what item the player is currently holding";
        }

        @Override
        public @NotNull ToolCategory getCategory() {
            return ToolCategory.INFORMATION;
        }

        @Override
        public @NotNull Map<String, String> getParameters() {
            return java.util.Collections.emptyMap();
        }

        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            return true; // Can always check
        }

        @Override
        public @NotNull AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();

            StringBuilder message = new StringBuilder();
            Map<String, Object> data = new HashMap<>();

            if (mainHand.getType() != Material.AIR) {
                message.append("Main hand: ").append(mainHand.getAmount()).append("x ").append(mainHand.getType().name());
                data.put("mainHand", mainHand.getType().name());
                data.put("mainHandAmount", mainHand.getAmount());
            }

            if (offHand.getType() != Material.AIR) {
                if (message.length() > 0) message.append(", ");
                message.append("Off hand: ").append(offHand.getAmount()).append("x ").append(offHand.getType().name());
                data.put("offHand", offHand.getType().name());
                data.put("offHandAmount", offHand.getAmount());
            }

            if (message.length() == 0) {
                return AIToolResult.success(player.getName() + " is not holding anything");
            }

            return AIToolResult.success(player.getName() + " is holding: " + message, data);
        }
    }

    private static Inventory getVillagerInventory(@NotNull IVillagerNPC villager) {
        return villager.bukkit() instanceof InventoryHolder holder ? holder.getInventory() : null;
    }
}
