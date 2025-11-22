package me.matsubara.realisticvillagers.manager.ai.tools;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Interface for AI-callable tools that villagers can use during conversations.
 * Tools allow villagers to perform actions based on AI decisions.
 */
public interface AITool {

    /**
     * Gets the unique identifier for this tool.
     *
     * @return tool name (e.g., "follow_player")
     */
    @NotNull
    String getName();

    /**
     * Gets a human-readable description of what this tool does.
     *
     * @return description
     */
    @NotNull
    String getDescription();

    /**
     * Gets the category of this tool.
     *
     * @return tool category
     */
    @NotNull
    ToolCategory getCategory();

    /**
     * Gets parameter descriptions for this tool.
     *
     * @return map of parameter name to description
     */
    @NotNull
    Map<String, String> getParameters();

    /**
     * Checks if this tool can be executed in the current context.
     *
     * @param villager the villager NPC
     * @param player   the player interacting with the villager
     * @param args     the arguments provided to the tool
     * @return true if can execute, false otherwise
     */
    boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args);

    /**
     * Executes the tool.
     * IMPORTANT: This is called on the main thread (Folia entity scheduler).
     *
     * @param villager the villager NPC
     * @param player   the player interacting with the villager
     * @param args     the arguments provided to the tool
     * @return execution result
     */
    @NotNull
    AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args);

    /**
     * Whether this tool requires execution on the main thread.
     * Default: true (most tools modify game state).
     *
     * @return true if requires main thread
     */
    default boolean requiresMainThread() {
        return true;
    }
}
