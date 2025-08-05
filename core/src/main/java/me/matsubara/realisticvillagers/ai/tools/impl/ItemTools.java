package me.matsubara.realisticvillagers.ai.tools.impl;

import me.matsubara.realisticvillagers.ai.tools.AITool;
import me.matsubara.realisticvillagers.ai.tools.AIToolResult;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Collection of item-related AI tools for villagers.
 * EXPERIMENTAL FEATURE - Subject to change
 */
public class ItemTools {
    
    /**
     * Tool that makes the villager give an item to the player
     */
    public static class GiveItemTool implements AITool {
        
        @Override
        @NotNull
        public String getName() {
            return "give_item";
        }
        
        @Override
        @NotNull
        public String getDescription() {
            return "Makes the villager give an item to the player from their inventory";
        }
        
        @Override
        @NotNull
        public Map<String, String> getParameters() {
            return Map.of(
                "item", "The type of item to give (e.g., 'bread', 'wheat', 'emerald')",
                "quantity", "The number of items to give (optional, defaults to 1)"
            );
        }
        
        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            // Check if villager is in a state that prevents giving items
            if (villager.isFighting() || villager.isProcreating()) {
                return false;
            }
            
            // Get item type from arguments
            String itemName = (String) args.get("item");
            if (itemName == null || itemName.isEmpty()) {
                return false;
            }
            
            // Parse material
            Material material;
            try {
                material = Material.valueOf(itemName.toUpperCase());
            } catch (IllegalArgumentException e) {
                return false; // Invalid material
            }
            
            // Check if villager has the item
            if (!(villager.bukkit() instanceof Villager bukkit)) {
                return false;
            }
            
            Inventory inventory = bukkit.getInventory();
            int quantity = getQuantity(args);
            
            // Check if villager has enough of the item (inventory + equipment)
            int availableAmount = 0;
            
            // Check inventory
            for (ItemStack item : inventory.getContents()) {
                if (item != null && item.getType() == material) {
                    availableAmount += item.getAmount();
                }
            }
            
            // Also check equipped items (main hand, off hand)
            if (bukkit.getEquipment() != null) {
                ItemStack mainHand = bukkit.getEquipment().getItemInMainHand();
                if (mainHand != null && mainHand.getType() == material) {
                    availableAmount += mainHand.getAmount();
                }
                
                ItemStack offHand = bukkit.getEquipment().getItemInOffHand();
                if (offHand != null && offHand.getType() == material) {
                    availableAmount += offHand.getAmount();
                }
            }
            
            return availableAmount >= quantity;
        }
        
        @Override
        @NotNull
        public AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            try {
                String itemName = (String) args.get("item");
                Material material = Material.valueOf(itemName.toUpperCase());
                int quantity = getQuantity(args);
                
                if (!(villager.bukkit() instanceof Villager bukkit)) {
                    return AIToolResult.failure("Villager is not available");
                }
                
                Inventory inventory = bukkit.getInventory();
                
                // Remove items from villager's inventory and equipment
                int remainingToRemove = quantity;
                
                // First try inventory
                for (int i = 0; i < inventory.getSize() && remainingToRemove > 0; i++) {
                    ItemStack item = inventory.getItem(i);
                    if (item != null && item.getType() == material) {
                        int amountToTake = Math.min(item.getAmount(), remainingToRemove);
                        
                        if (amountToTake == item.getAmount()) {
                            inventory.setItem(i, null);
                        } else {
                            item.setAmount(item.getAmount() - amountToTake);
                        }
                        
                        remainingToRemove -= amountToTake;
                    }
                }
                
                // If still need more, check equipped items
                if (remainingToRemove > 0 && bukkit.getEquipment() != null) {
                    // Check main hand
                    ItemStack mainHand = bukkit.getEquipment().getItemInMainHand();
                    if (mainHand != null && mainHand.getType() == material && remainingToRemove > 0) {
                        int amountToTake = Math.min(mainHand.getAmount(), remainingToRemove);
                        
                        if (amountToTake == mainHand.getAmount()) {
                            bukkit.getEquipment().setItemInMainHand(null);
                        } else {
                            mainHand.setAmount(mainHand.getAmount() - amountToTake);
                        }
                        
                        remainingToRemove -= amountToTake;
                    }
                    
                    // Check off hand
                    ItemStack offHand = bukkit.getEquipment().getItemInOffHand();
                    if (offHand != null && offHand.getType() == material && remainingToRemove > 0) {
                        int amountToTake = Math.min(offHand.getAmount(), remainingToRemove);
                        
                        if (amountToTake == offHand.getAmount()) {
                            bukkit.getEquipment().setItemInOffHand(null);
                        } else {
                            offHand.setAmount(offHand.getAmount() - amountToTake);
                        }
                        
                        remainingToRemove -= amountToTake;
                    }
                }
                
                // Create item to drop - must be done on main thread
                ItemStack itemToDrop = new ItemStack(material, quantity - remainingToRemove);
                int finalQuantityGiven = quantity - remainingToRemove;
                
                me.matsubara.realisticvillagers.RealisticVillagers plugin = 
                    (me.matsubara.realisticvillagers.RealisticVillagers) org.bukkit.Bukkit.getPluginManager().getPlugin("RealisticVillagers");
                if (plugin != null) {
                    plugin.getFoliaLib().getImpl().runAtEntity(villager.bukkit(), task -> {
                        try {
                            // Make villager walk to player first
                            org.bukkit.entity.Villager bukkitVillager = (org.bukkit.entity.Villager) villager.bukkit();
                            double distance = bukkitVillager.getLocation().distance(player.getLocation());
                            
                            if (distance > 3.0) {
                                // Too far - make villager follow player using RealisticVillagers system
                                villager.setInteractingWithAndType(player.getUniqueId(), me.matsubara.realisticvillagers.data.InteractType.FOLLOW_ME);
                                
                                // Keep checking until villager reaches player
                                waitForVillagerToReachPlayer(villager, player, itemToDrop, 0);
                            } else {
                                // Close enough - drop immediately
                                dropItemNearPlayer(player, itemToDrop);
                            }
                            
                        } catch (Exception e) {
                            // Fallback: just drop at player location
                            dropItemNearPlayer(player, itemToDrop);
                        }
                    });
                }
                
                String itemDisplayName = material.name().toLowerCase().replace('_', ' ');
                return AIToolResult.success("Gave " + finalQuantityGiven + " " + itemDisplayName);
                
            } catch (Exception e) {
                return AIToolResult.failure("Failed to give item: " + e.getMessage());
            }
        }
        
        
        @Override
        public int getCooldownSeconds() {
            return 3; // 3 second cooldown between item giving
        }
        
        private int getQuantity(@NotNull Map<String, Object> args) {
            Object quantityObj = args.get("quantity");
            if (quantityObj instanceof Number) {
                return Math.max(1, Math.min(64, ((Number) quantityObj).intValue()));
            }
            if (quantityObj instanceof String) {
                try {
                    return Math.max(1, Math.min(64, Integer.parseInt((String) quantityObj)));
                } catch (NumberFormatException e) {
                    return 1;
                }
            }
            return 1; // Default quantity
        }
        
        private void waitForVillagerToReachPlayer(IVillagerNPC villager, Player player, ItemStack item, int attempts) {
            // Prevent infinite waiting - give up after 10 seconds (20 attempts * 0.5 seconds)
            if (attempts >= 20) {
                dropItemNearPlayer(player, item);
                return;
            }
            
            me.matsubara.realisticvillagers.RealisticVillagers plugin = 
                (me.matsubara.realisticvillagers.RealisticVillagers) org.bukkit.Bukkit.getPluginManager().getPlugin("RealisticVillagers");
            if (plugin != null) {
                plugin.getFoliaLib().getImpl().runAtEntityLater(villager.bukkit(), task -> {
                    try {
                        org.bukkit.entity.Villager bukkitVillager = (org.bukkit.entity.Villager) villager.bukkit();
                        double distance = bukkitVillager.getLocation().distance(player.getLocation());
                        
                        if (distance <= 3.0) {
                            // Villager reached player - stop following and drop the item
                            villager.stopInteracting();
                            dropItemNearPlayer(player, item);
                        } else {
                            // Still too far - keep following
                            villager.setInteractingWithAndType(player.getUniqueId(), me.matsubara.realisticvillagers.data.InteractType.FOLLOW_ME);
                            waitForVillagerToReachPlayer(villager, player, item, attempts + 1);
                        }
                    } catch (Exception e) {
                        // Something went wrong - just drop the item
                        dropItemNearPlayer(player, item);
                    }
                }, 10L); // Check every 0.5 seconds
            }
        }
        
        private void dropItemNearPlayer(Player player, ItemStack item) {
            try {
                // Drop item near player with slight randomization
                org.bukkit.Location dropLocation = player.getLocation().add(
                    (Math.random() - 0.5) * 2, // Random X offset (-1 to 1)
                    0.1, // Slight Y offset so it doesn't drop in ground
                    (Math.random() - 0.5) * 2  // Random Z offset (-1 to 1)
                );
                player.getWorld().dropItemNaturally(dropLocation, item);
            } catch (Exception e) {
                // Fallback: drop at exact player location
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
    }
    
    /**
     * Tool that prepares the villager to receive a gift from the player
     */
    public static class PrepareForGiftTool implements AITool {
        
        @Override
        @NotNull
        public String getName() {
            return "prepare_for_gift";
        }
        
        @Override
        @NotNull
        public String getDescription() {
            return "Uses the native RealisticVillagers gift system to prepare for receiving gifts";
        }
        
        @Override
        @NotNull
        public Map<String, String> getParameters() {
            return Map.of(); // No parameters needed - just prepares to receive
        }
        
        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            // Check if villager is in a state that prevents receiving gifts
            if (villager.isFighting() || villager.isProcreating()) {
                return false;
            }
            
            // Always allow preparing to receive gifts - no specific item validation needed
            return true;
        }
        
        @Override
        @NotNull 
        public AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            try {
                // Use the native RealisticVillagers gift system exactly as the core plugin does
                // This integrates with existing ExpectingManager, GiftManager, etc.
                
                // Step 1: Set villager to expect a gift (same as clicking gift option in GUI)
                villager.startExpectingFrom(me.matsubara.realisticvillagers.data.ExpectingType.GIFT, player.getUniqueId(), 600);
                
                // Step 2: Register with ExpectingManager for PlayerDropItemEvent handling
                me.matsubara.realisticvillagers.RealisticVillagers plugin = 
                    (me.matsubara.realisticvillagers.RealisticVillagers) org.bukkit.Bukkit.getPluginManager().getPlugin("RealisticVillagers");
                if (plugin != null) {
                    plugin.getFoliaLib().getImpl().runAtEntity(villager.bukkit(), task -> {
                        try {
                            if (plugin.getExpectingManager() != null) {
                                // This enables automatic gift key tagging when player drops items
                                plugin.getExpectingManager().expect(player.getUniqueId(), villager);
                            }
                        } catch (Exception e) {
                            // Silently handle registration errors - core system still works
                        }
                    });
                }
                
                return AIToolResult.success("Ready to receive gifts - drop items nearby!");
                
            } catch (Exception e) {
                return AIToolResult.failure("Failed to prepare for gift: " + e.getMessage());
            }
        }
        
        
        
        @Override
        public int getCooldownSeconds() {
            return 2; // Short cooldown
        }
    }
    
    /**
     * Tool that lets the villager check their inventory and equipment
     */
    public static class CheckInventoryTool implements AITool {
        
        @Override
        @NotNull
        public String getName() {
            return "check_inventory";
        }
        
        @Override
        @NotNull
        public String getDescription() {
            return "Check the villager's inventory and equipment (armor, hand items)";
        }
        
        @Override
        @NotNull
        public Map<String, String> getParameters() {
            return Map.of(); // No parameters needed
        }
        
        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            // Can always check inventory unless in very specific states
            return !(villager.isFighting() || villager.isProcreating());
        }
        
        @Override
        @NotNull
        public AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            try {
                if (!(villager.bukkit() instanceof Villager bukkit)) {
                    return AIToolResult.failure("Not a valid villager");
                }
                
                // Thread safety is now handled at the AIToolRegistry level
                
                StringBuilder inventory = new StringBuilder();
                
                // Check main inventory
                org.bukkit.inventory.Inventory inv = bukkit.getInventory();
                java.util.Map<org.bukkit.Material, Integer> itemCounts = new java.util.HashMap<>();
                
                for (ItemStack item : inv.getContents()) {
                    if (item != null && item.getType() != org.bukkit.Material.AIR) {
                        itemCounts.merge(item.getType(), item.getAmount(), Integer::sum);
                    }
                }
                
                // Check equipment
                org.bukkit.inventory.EntityEquipment equipment = bukkit.getEquipment();
                StringBuilder gear = new StringBuilder();
                
                if (equipment != null) {
                    if (equipment.getItemInMainHand().getType() != org.bukkit.Material.AIR) {
                        gear.append("Main hand: ").append(formatItem(equipment.getItemInMainHand())).append("; ");
                    }
                    if (equipment.getItemInOffHand().getType() != org.bukkit.Material.AIR) {
                        gear.append("Off hand: ").append(formatItem(equipment.getItemInOffHand())).append("; ");
                    }
                    if (equipment.getHelmet() != null && equipment.getHelmet().getType() != org.bukkit.Material.AIR) {
                        gear.append("Helmet: ").append(formatItem(equipment.getHelmet())).append("; ");
                    }
                    if (equipment.getChestplate() != null && equipment.getChestplate().getType() != org.bukkit.Material.AIR) {
                        gear.append("Chestplate: ").append(formatItem(equipment.getChestplate())).append("; ");
                    }
                    if (equipment.getLeggings() != null && equipment.getLeggings().getType() != org.bukkit.Material.AIR) {
                        gear.append("Leggings: ").append(formatItem(equipment.getLeggings())).append("; ");
                    }
                    if (equipment.getBoots() != null && equipment.getBoots().getType() != org.bukkit.Material.AIR) {
                        gear.append("Boots: ").append(formatItem(equipment.getBoots())).append("; ");
                    }
                }
                
                // Format full inventory for AI processing
                if (!itemCounts.isEmpty()) {
                    inventory.append("Inventory: ");
                    itemCounts.forEach((material, count) -> {
                        String itemName = material.name().toLowerCase().replace('_', ' ');
                        inventory.append(count).append(" ").append(itemName).append(", ");
                    });
                    inventory.setLength(inventory.length() - 2); // Remove last ", "
                }
                
                if (gear.length() > 0) {
                    if (inventory.length() > 0) inventory.append("; ");
                    inventory.append(gear);
                    if (gear.toString().endsWith("; ")) {
                        inventory.setLength(inventory.length() - 2); // Remove trailing "; "
                    }
                }
                
                if (inventory.length() == 0) {
                    inventory.append("Empty inventory, no equipment");
                }
                
                // Return full inventory data for AI decision making
                // The AI will naturally summarize this when speaking to the player
                return AIToolResult.success(inventory.toString());
                
            } catch (Exception e) {
                return AIToolResult.failure("Failed to check inventory: " + e.getMessage());
            }
        }
        
        private String formatItem(ItemStack item) {
            return item.getAmount() + " " + item.getType().name().toLowerCase().replace('_', ' ');
        }
        
        
        @Override
        public int getCooldownSeconds() {
            return 1; // Short cooldown
        }
        
        private int getQuantity(@NotNull Map<String, Object> args) {
            Object quantityObj = args.get("quantity");
            if (quantityObj instanceof Number) {
                return Math.max(1, Math.min(64, ((Number) quantityObj).intValue()));
            }
            if (quantityObj instanceof String) {
                try {
                    return Math.max(1, Math.min(64, Integer.parseInt((String) quantityObj)));
                } catch (NumberFormatException e) {
                    return 1;
                }
            }
            return 1;
        }
    }
    
    /**
     * Tool that lets the villager check what the player is holding
     */
    public static class CheckPlayerItemTool implements AITool {
        
        @Override
        @NotNull
        public String getName() {
            return "check_player_item";
        }
        
        @Override
        @NotNull
        public String getDescription() {
            return "Check what item the player is currently holding in their hand";
        }
        
        @Override
        @NotNull
        public Map<String, String> getParameters() {
            return Map.of(); // No parameters needed
        }
        
        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            // Can always check what player is holding
            return true;
        }
        
        @Override
        @NotNull
        public AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            try {
                StringBuilder result = new StringBuilder();
                
                // Check main hand
                ItemStack mainHand = player.getInventory().getItemInMainHand();
                if (mainHand != null && mainHand.getType() != Material.AIR) {
                    result.append("Main hand: ").append(formatPlayerItem(mainHand));
                } else {
                    result.append("Main hand: empty");
                }
                
                // Check off hand
                ItemStack offHand = player.getInventory().getItemInOffHand();
                if (offHand != null && offHand.getType() != Material.AIR) {
                    result.append("; Off hand: ").append(formatPlayerItem(offHand));
                } else {
                    result.append("; Off hand: empty");
                }
                
                return AIToolResult.success(result.toString());
                
            } catch (Exception e) {
                return AIToolResult.failure("Failed to check player's held items: " + e.getMessage());
            }
        }
        
        private String formatPlayerItem(ItemStack item) {
            String itemName = item.getType().name().toLowerCase().replace('_', ' ');
            if (item.getAmount() == 1) {
                return itemName;
            } else {
                return item.getAmount() + " " + itemName;
            }
        }
        
        
        @Override
        public int getCooldownSeconds() {
            return 1; // Short cooldown
        }
    }
}