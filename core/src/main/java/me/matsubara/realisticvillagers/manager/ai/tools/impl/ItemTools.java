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

import java.util.HashMap;
import java.util.Map;

/**
 * Item-related AI tools for villagers.
 */
public class ItemTools {

    private static final RealisticVillagers PLUGIN = JavaPlugin.getPlugin(RealisticVillagers.class);

    /**
     * Tool that allows the villager to give an item to the player.
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

            // Remove from villager inventory and give to player
            int remaining = quantity;
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack stack = contents[i];
                if (stack != null && stack.getType() == material) {
                    int toRemove = Math.min(remaining, stack.getAmount());
                    stack.setAmount(stack.getAmount() - toRemove);
                    remaining -= toRemove;

                    if (stack.getAmount() <= 0) {
                        contents[i] = null;
                    }
                }
            }

            inventory.setContents(contents);

            // Give to player
            ItemStack giveStack = new ItemStack(material, quantity);
            player.getInventory().addItem(giveStack);

            return AIToolResult.success("Gave " + quantity + " " + itemName + " to " + player.getName());
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
            return "Activate gift system to receive items from the player";
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
