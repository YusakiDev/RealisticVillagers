package me.matsubara.realisticvillagers.ai.tools.impl;

import me.matsubara.realisticvillagers.ai.tools.AITool;
import me.matsubara.realisticvillagers.ai.tools.AIToolResult;
import me.matsubara.realisticvillagers.data.InteractType;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Collection of movement-related AI tools for villagers.
 * EXPERIMENTAL FEATURE - Subject to change
 */
public class MovementTools {
    
    /**
     * Tool that makes the villager follow the player
     */
    public static class FollowPlayerTool implements AITool {
        
        @Override
        @NotNull
        public String getName() {
            return "follow_player";
        }
        
        @Override
        @NotNull
        public String getDescription() {
            return "Makes the villager follow the player";
        }
        
        @Override
        @NotNull
        public Map<String, String> getParameters() {
            return Map.of(
                "player", "The name of the player to follow (optional, defaults to current player)"
            );
        }
        
        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            // Check if villager is already following the same player
            if (villager.isFollowing() && villager.getInteractingWith() != null && villager.getInteractingWith().equals(player.getUniqueId())) {
                return false;
            }
            
            // Don't prevent following if villager is staying - following should override staying
            
            // Check if villager is in an interaction that prevents following
            if (villager.isFighting() || villager.isProcreating()) {
                return false;
            }
            
            return true;
        }
        
        @Override
        @NotNull
        public AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            try {
                // For now, we'll always follow the current player
                // In the future, we could support following other players by name
                villager.setInteractingWithAndType(player.getUniqueId(), InteractType.FOLLOW_ME);
                
                return AIToolResult.success("Now following " + player.getName());
            } catch (Exception e) {
                return AIToolResult.failure("Failed to start following: " + e.getMessage());
            }
        }
        
        @Override
        public int getCooldownSeconds() {
            return 3; // 3 second cooldown between follow commands
        }
        
        @Override
        public boolean requiresPlayerConsent() {
            return true; // Following requires player consent
        }
    }
    
    /**
     * Tool that makes the villager stay in their current location
     */
    public static class StayHereTool implements AITool {
        
        @Override
        @NotNull
        public String getName() {
            return "stay_here";
        }
        
        @Override
        @NotNull
        public String getDescription() {
            return "Makes the villager stay in their current location";
        }
        
        @Override
        @NotNull
        public Map<String, String> getParameters() {
            return Map.of(); // No parameters needed
        }
        
        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            // Check if villager is already staying in place
            if (villager.isStayingInPlace()) {
                return false;
            }
            
            // Check if villager is in an interaction that prevents staying
            if (villager.isFighting() || villager.isProcreating()) {
                return false;
            }
            
            return true;
        }
        
        @Override
        @NotNull
        public AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            try {
                villager.setInteractingWithAndType(player.getUniqueId(), InteractType.STAY_HERE);
                villager.stayInPlace();
                
                return AIToolResult.success("Now staying in place");
            } catch (Exception e) {
                return AIToolResult.failure("Failed to stay in place: " + e.getMessage());
            }
        }
        
        @Override
        public int getCooldownSeconds() {
            return 2; // 2 second cooldown
        }
    }
    
    /**
     * Tool that stops the villager's current movement behavior (following or staying)
     */
    public static class StopMovementTool implements AITool {
        
        @Override
        @NotNull
        public String getName() {
            return "stop_movement";
        }
        
        @Override
        @NotNull
        public String getDescription() {
            return "Stops the villager's current movement behavior (following or staying)";
        }
        
        @Override
        @NotNull
        public Map<String, String> getParameters() {
            return Map.of(); // No parameters needed
        }
        
        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            // Can only stop movement if currently following or staying
            return villager.isFollowing() || villager.isStayingInPlace();
        }
        
        @Override
        @NotNull
        public AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            try {
                String previousBehavior = "";
                
                if (villager.isFollowing()) {
                    previousBehavior = "following";
                } else if (villager.isStayingInPlace()) {
                    previousBehavior = "staying in place";
                    villager.stopStayingInPlace();
                }
                
                villager.stopInteracting();
                
                return AIToolResult.success("Stopped " + previousBehavior);
            } catch (Exception e) {
                return AIToolResult.failure("Failed to stop movement: " + e.getMessage());
            }
        }
        
        @Override
        public int getCooldownSeconds() {
            return 1; // 1 second cooldown
        }
    }
}