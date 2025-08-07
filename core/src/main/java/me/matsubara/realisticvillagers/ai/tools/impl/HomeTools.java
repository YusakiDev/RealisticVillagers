package me.matsubara.realisticvillagers.ai.tools.impl;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.ai.tools.AITool;
import me.matsubara.realisticvillagers.ai.tools.AIToolResult;
import me.matsubara.realisticvillagers.data.ExpectingType;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Messages;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Collection of home-related AI tools for villagers.
 * EXPERIMENTAL FEATURE - Subject to change
 */
public class HomeTools {
    
    /**
     * Tool that triggers the existing bed selection mode
     */
    public static class SetHomeTool implements AITool {
        
        @Override
        @NotNull
        public String getName() {
            return "set_home";
        }
        
        @Override
        @NotNull
        public String getDescription() {
            return "Triggers bed selection mode for the player to set villager's home";
        }
        
        @Override
        @NotNull
        public Map<String, String> getParameters() {
            return Map.of();
        }
        
        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            // Check if villager is already expecting something
            if (villager.isExpectingGift() || villager.isExpectingBed()) {
                return false;
            }
            
            // Check if villager is in a state where they can set a bed
            if (villager.isFighting() || villager.isProcreating()) {
                return false;
            }
            
            return true;
        }
        
        @Override
        @NotNull
        public AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            try {
                // Get plugin instance
                RealisticVillagers plugin = JavaPlugin.getPlugin(RealisticVillagers.class);
                
                // Use the existing expecting system
                villager.startExpectingFrom(ExpectingType.BED, player.getUniqueId(), 600); // 30 seconds timeout
                plugin.getExpectingManager().expect(player.getUniqueId(), villager);
                
                // Send messages using the existing message system
                Messages messages = plugin.getMessages();
                messages.send(player, Messages.Message.SELECT_BED);
                messages.send(player, villager, Messages.Message.SET_HOME_EXPECTING);
                
                return AIToolResult.success("Please click on a bed to set as my home");
                
            } catch (Exception e) {
                return AIToolResult.failure("Failed to start home selection: " + e.getMessage());
            }
        }
        
        @Override
        public int getCooldownSeconds() {
            return 5;
        }
    }
}