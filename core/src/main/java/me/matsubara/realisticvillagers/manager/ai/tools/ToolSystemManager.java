package me.matsubara.realisticvillagers.manager.ai.tools;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.LastKnownPosition;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Manages tool execution with permission and cooldown checks.
 * Ensures all tool executions are Folia-safe by running on the entity or location scheduler.
 */
public class ToolSystemManager {

    private final RealisticVillagers plugin;
    private final AIToolRegistry registry;
    private final ToolCooldownManager cooldownManager;
    private final boolean debugEnabled;

    public ToolSystemManager(@NotNull RealisticVillagers plugin, @NotNull AIToolRegistry registry, boolean debugEnabled) {
        this.plugin = plugin;
        this.registry = registry;
        this.cooldownManager = new ToolCooldownManager();
        this.debugEnabled = debugEnabled;
    }

    /**
     * Executes a list of tool calls asynchronously on the appropriate Folia scheduler.
     *
     * @param toolCalls the parsed tool calls from the AI response
     * @param npc       the villager NPC (may be offline)
     * @param player    the player interacting with the villager
     * @return future with execution results
     */
    public CompletableFuture<List<AIToolResult>> executeTools(
            @NotNull List<AIResponseParser.ToolCall> toolCalls,
            @NotNull IVillagerNPC npc,
            @NotNull Player player) {

        CompletableFuture<List<AIToolResult>> future = new CompletableFuture<>();

        plugin.getFoliaLib().getScheduler().runNextTick((task1) -> {
            var entity = npc.bukkit();
            if (entity != null && entity.isValid()) {
                plugin.getFoliaLib().getScheduler().runAtEntity(entity, task ->
                        future.complete(executeToolBatch(toolCalls, npc, player)));
                return;
            }

            debug("Villager entity unavailable for tool execution: %s", npc.getVillagerName());

            Location fallbackLocation = resolveExecutionLocation(npc, player);
            if (fallbackLocation == null || fallbackLocation.getWorld() == null) {
                debug("No valid execution location for villager %s; failing tool execution.", npc.getVillagerName());
                future.complete(buildFailureResults(toolCalls, "Villager entity is unavailable."));
                return;
            }

            plugin.getFoliaLib().getScheduler().runAtLocation(fallbackLocation, task ->
                    future.complete(executeToolBatch(toolCalls, npc, player)));
        });

        return future;
    }

    /**
     * Executes a single tool with all necessary checks.
     * This method runs on the main thread (entity/location scheduler).
     */
    private AIToolResult executeSingleTool(
            @NotNull AIResponseParser.ToolCall toolCall,
            @NotNull IVillagerNPC npc,
            @NotNull Player player) {

        String toolName = toolCall.getName();

        // Check if tool exists
        AITool tool = registry.getTool(toolName);
        if (tool == null) {
            debug("Tool '%s' not registered.", toolName);
            return AIToolResult.failure("Unknown tool: " + toolName);
        }

        // Check if tool is enabled in config
        if (!registry.isToolEnabled(toolName)) {
            debug("Tool '%s' disabled in configuration.", toolName);
            return AIToolResult.failure("Tool is disabled: " + toolName);
        }

        // Check reputation requirement
        int minReputation = registry.getMinReputation(toolName);
        int currentReputation = npc.getReputation(player.getUniqueId());
        if (currentReputation < minReputation) {
            debug("Tool '%s' blocked due to reputation. Need=%d have=%d", toolName, minReputation, currentReputation);
            return AIToolResult.failure(
                    "Insufficient reputation for " + toolName +
                            " (need " + minReputation + ", have " + currentReputation + ")"
            );
        }

        // Check cooldown
        int cooldownSeconds = registry.getCooldownSeconds(toolName);
        if (cooldownManager.isOnCooldown(npc.getUniqueId(), toolName, player.getUniqueId(), cooldownSeconds)) {
            int remaining = cooldownManager.getRemainingCooldown(
                    npc.getUniqueId(), toolName, player.getUniqueId(), cooldownSeconds
            );
            debug("Tool '%s' on cooldown for %ds", toolName, remaining);
            return AIToolResult.failure("Tool on cooldown: " + toolName + " (wait " + remaining + "s)");
        }

        // Check if tool can execute in current context
        if (!tool.canExecute(npc, player, toolCall.getArguments())) {
            debug("Tool '%s' cannot execute in current context.", toolName);
            return AIToolResult.failure("Cannot execute " + toolName + " right now");
        }

        // Execute the tool
        try {
            AIToolResult result = tool.execute(npc, player, toolCall.getArguments());

            // Set cooldown only on success
            if (result.isSuccess()) {
                cooldownManager.setCooldown(npc.getUniqueId(), toolName, player.getUniqueId());
                debug("Cooldown set for tool '%s' for %ds", toolName, cooldownSeconds);
            }

            return result;

        } catch (Exception e) {
            plugin.getLogger().warning("Error executing tool " + toolName + ": " + e.getMessage());
            e.printStackTrace();
            return AIToolResult.failure("Error executing " + toolName + ": " + e.getMessage());
        }
    }

    /**
     * @return the tool registry
     */
    public AIToolRegistry getRegistry() {
        return registry;
    }

    /**
     * @return the cooldown manager
     */
    public ToolCooldownManager getCooldownManager() {
        return cooldownManager;
    }

    private void debug(@NotNull String message, Object... args) {
        if (!debugEnabled) {
            return;
        }
        plugin.getLogger().info("[AI Tool Debug] " + String.format(message, args));
    }

    private @NotNull List<AIToolResult> executeToolBatch(
            @NotNull List<AIResponseParser.ToolCall> toolCalls,
            @NotNull IVillagerNPC npc,
            @NotNull Player player) {

        List<AIToolResult> results = new ArrayList<>();

        debug("Executing %d tool call(s) for villager=%s player=%s", toolCalls.size(), npc.getVillagerName(), player.getName());
        for (AIResponseParser.ToolCall toolCall : toolCalls) {
            debug("Executing tool '%s' with args=%s", toolCall.getName(), toolCall.getArguments());
            AIToolResult result = executeSingleTool(toolCall, npc, player);
            debug("Result for '%s': %s - %s", toolCall.getName(), result.isSuccess() ? "SUCCESS" : "FAILURE", result.getMessage());
            results.add(result);
        }

        return results;
    }

    private @NotNull List<AIToolResult> buildFailureResults(
            @NotNull List<AIResponseParser.ToolCall> calls,
            @NotNull String reason) {

        List<AIToolResult> failures = new ArrayList<>(calls.size());
        for (AIResponseParser.ToolCall ignored : calls) {
            failures.add(AIToolResult.failure(reason));
        }
        return failures;
    }

    private @Nullable Location resolveExecutionLocation(@NotNull IVillagerNPC npc, @NotNull Player player) {
        LastKnownPosition position = npc.getLastKnownPosition();
        if (position != null) {
            Location location = position.asLocation();
            if (location != null && location.getWorld() != null) {
                return location;
            }
        }

        if (player.isOnline()) {
            return player.getLocation();
        }

        return null;
    }
}
