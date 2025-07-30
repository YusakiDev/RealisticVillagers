package me.matsubara.realisticvillagers.ai.tools;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Interface for AI-callable tools that villagers can use during conversations.
 * EXPERIMENTAL FEATURE - Subject to change
 */
public interface AITool {
    
    /**
     * Gets the unique name/identifier for this tool
     * @return tool name (e.g., "follow_player", "start_trading")
     */
    @NotNull String getName();
    
    /**
     * Gets a description of what this tool does
     * @return human-readable description
     */
    @NotNull String getDescription();
    
    /**
     * Gets the required parameters for this tool
     * @return map of parameter names to their descriptions
     */
    @NotNull Map<String, String> getParameters();
    
    /**
     * Checks if this tool can be used by the given villager with the player
     * @param villager the villager attempting to use the tool
     * @param player the player involved in the interaction
     * @param args the arguments provided to the tool
     * @return true if the tool can be executed
     */
    boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args);
    
    /**
     * Executes the tool with the given parameters
     * @param villager the villager using the tool
     * @param player the player involved in the interaction
     * @param args the arguments provided to the tool
     * @return result of the tool execution (null if no result needed)
     */
    @Nullable AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args);
    
    /**
     * Gets the maximum number of times this tool can be used per conversation
     * @return max uses per conversation (0 = unlimited)
     */
    default int getMaxUsesPerConversation() {
        return 0; // unlimited by default
    }
    
    /**
     * Gets the cooldown in seconds between uses of this tool
     * @return cooldown in seconds (0 = no cooldown)
     */
    default int getCooldownSeconds() {
        return 0; // no cooldown by default
    }
    
}