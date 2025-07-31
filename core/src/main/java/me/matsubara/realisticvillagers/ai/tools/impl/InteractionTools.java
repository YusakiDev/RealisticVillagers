package me.matsubara.realisticvillagers.ai.tools.impl;

import me.matsubara.realisticvillagers.ai.tools.AITool;
import me.matsubara.realisticvillagers.ai.tools.AIToolResult;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Collection of interaction-related AI tools for villagers.
 * EXPERIMENTAL FEATURE - Subject to change
 */
public class InteractionTools {
    
    /**
     * Tool that opens the trading interface with the player
     */
    public static class StartTradingTool implements AITool {
        
        @Override
        @NotNull
        public String getName() {
            return "start_trading";
        }
        
        @Override
        @NotNull
        public String getDescription() {
            return "Opens the trading interface with the player";
        }
        
        @Override
        @NotNull
        public Map<String, String> getParameters() {
            return Map.of(); // No parameters needed
        }
        
        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            // Check if villager is actually a villager (not wandering trader, etc.)
            if (!(villager.bukkit() instanceof Villager bukkit)) {
                return false;
            }
            
            // Check if villager is already trading
            if (bukkit.isTrading()) {
                return false;
            }
            
            // Check if villager has trades available
            if (bukkit.getRecipes().isEmpty()) {
                return false;
            }
            
            // Check if villager is in a state that prevents trading
            if (villager.isFighting() || villager.isProcreating()) {
                return false;
            }
            
            return true;
        }
        
        @Override
        @NotNull
        public AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            try {
                // Trading must be done on the main thread
                org.bukkit.Bukkit.getScheduler().runTask(
                    org.bukkit.Bukkit.getPluginManager().getPlugin("RealisticVillagers"), 
                    () -> {
                        try {
                            // Use filtered trade wrapper if available
                            me.matsubara.realisticvillagers.RealisticVillagers plugin = 
                                (me.matsubara.realisticvillagers.RealisticVillagers) org.bukkit.Bukkit.getPluginManager().getPlugin("RealisticVillagers");
                            if (plugin != null && plugin.getTradeWrapper() != null) {
                                plugin.getTradeWrapper().openFilteredTrading(villager, player);
                            } else {
                                villager.startTrading(player);
                            }
                        } catch (Exception e) {
                            player.sendMessage("§cCouldn't start trading right now.");
                        }
                    }
                );
                return AIToolResult.success("Trading interface opened");
            } catch (Exception e) {
                return AIToolResult.failure("Failed to start trading: " + e.getMessage());
            }
        }
        
        
        @Override
        public int getCooldownSeconds() {
            return 5; // 5 second cooldown between trade openings
        }
    }
    
    /**
     * Tool that makes the villager shake their head at the player (refusal gesture)
     */
    public static class ShakeHeadTool implements AITool {
        
        @Override
        @NotNull
        public String getName() {
            return "shake_head";
        }
        
        @Override
        @NotNull
        public String getDescription() {
            return "Makes the villager shake their head at the player (refusal gesture)";
        }
        
        @Override
        @NotNull
        public Map<String, String> getParameters() {
            return Map.of(); // No parameters needed
        }
        
        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            // Check if villager is already shaking head
            if (villager.isShakingHead()) {
                return false;
            }
            
            return true;
        }
        
        @Override
        @NotNull
        public AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            try {
                villager.shakeHead(player);
                return AIToolResult.success("Shook head at player");
            } catch (Exception e) {
                return AIToolResult.failure("Failed to shake head: " + e.getMessage());
            }
        }
        
        
        @Override
        public int getCooldownSeconds() {
            return 2; // 2 second cooldown
        }
    }
    
    /**
     * Tool that stops the villager's current interaction
     */
    public static class StopInteractionTool implements AITool {
        
        @Override
        @NotNull
        public String getName() {
            return "stop_interaction";
        }
        
        @Override
        @NotNull
        public String getDescription() {
            return "Stops the villager's current interaction";
        }
        
        @Override
        @NotNull
        public Map<String, String> getParameters() {
            return Map.of(); // No parameters needed
        }
        
        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            // Can only stop interaction if currently interacting
            return villager.isInteracting();
        }
        
        @Override
        @NotNull
        public AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            try {
                villager.stopInteracting();
                return AIToolResult.success("Stopped current interaction");
            } catch (Exception e) {
                return AIToolResult.failure("Failed to stop interaction: " + e.getMessage());
            }
        }
        
        @Override
        public int getCooldownSeconds() {
            return 1; // 1 second cooldown
        }
    }
    
    /**
     * Tool that toggles the villager's fishing behavior
     */
    public static class ToggleFishingTool implements AITool {
        
        @Override
        @NotNull
        public String getName() {
            return "toggle_fishing";
        }
        
        @Override
        @NotNull
        public String getDescription() {
            return "Toggles the villager's fishing behavior";
        }
        
        @Override
        @NotNull
        public Map<String, String> getParameters() {
            return Map.of(); // No parameters needed
        }
        
        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            // Check if villager is in a state that prevents fishing changes
            if (villager.isFighting() || villager.isProcreating()) {
                return false;
            }
            
            return true;
        }
        
        @Override
        @NotNull
        public AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            try {
                boolean wasFishing = villager.isFishing();
                villager.toggleFishing();
                
                String status = villager.isFishing() ? "started" : "stopped";
                return AIToolResult.success("Fishing " + status);
            } catch (Exception e) {
                return AIToolResult.failure("Failed to toggle fishing: " + e.getMessage());
            }
        }
        
        
        @Override
        public int getCooldownSeconds() {
            return 3; // 3 second cooldown
        }
    }
}