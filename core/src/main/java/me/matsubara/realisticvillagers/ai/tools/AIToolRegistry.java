package me.matsubara.realisticvillagers.ai.tools;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Registry for managing AI tools and handling their execution.
 * EXPERIMENTAL FEATURE - Subject to change
 */
public class AIToolRegistry {
    
    private final RealisticVillagers plugin;
    private final Map<String, AITool> tools = new ConcurrentHashMap<>();
    private final Map<String, Long> toolCooldowns = new ConcurrentHashMap<>();
    
    public AIToolRegistry(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Registers a tool in the registry
     * @param tool the tool to register
     */
    public void registerTool(@NotNull AITool tool) {
        tools.put(tool.getName().toLowerCase(), tool);
        plugin.getLogger().info("AI Tool registered: " + tool.getName());
    }
    
    /**
     * Unregisters a tool from the registry
     * @param toolName the name of the tool to unregister
     */
    public void unregisterTool(@NotNull String toolName) {
        tools.remove(toolName.toLowerCase());
        plugin.getLogger().info("AI Tool unregistered: " + toolName);
    }
    
    /**
     * Gets a tool by name
     * @param toolName the name of the tool
     * @return the tool, or null if not found
     */
    @Nullable
    public AITool getTool(@NotNull String toolName) {
        return tools.get(toolName.toLowerCase());
    }
    
    /**
     * Gets all registered tools
     * @return collection of all tools
     */
    @NotNull
    public Collection<AITool> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }
    
    /**
     * Gets the names of all registered tools
     * @return set of tool names
     */
    @NotNull
    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }
    
    /**
     * Executes a tool with validation and rate limiting
     * @param toolName the name of the tool to execute
     * @param villager the villager using the tool
     * @param player the player involved in the interaction
     * @param args the arguments for the tool
     * @return the result of the tool execution
     */
    @NotNull
    public AIToolResult executeTool(@NotNull String toolName, 
                                    @NotNull IVillagerNPC villager, 
                                    @NotNull Player player, 
                                    @NotNull Map<String, Object> args) {
        return executeTool(toolName, villager, player, args, true);
    }
    
    /**
     * Executes a tool with optional cooldown checking
     * @param toolName the name of the tool to execute
     * @param villager the villager using the tool
     * @param player the player involved in the interaction
     * @param args the arguments for the tool
     * @param checkCooldown whether to check cooldowns before execution
     * @return the result of the tool execution
     */
    @NotNull
    public AIToolResult executeTool(@NotNull String toolName, 
                                    @NotNull IVillagerNPC villager, 
                                    @NotNull Player player, 
                                    @NotNull Map<String, Object> args,
                                    boolean checkCooldown) {
        
        // Get the tool
        AITool tool = getTool(toolName);
        if (tool == null) {
            return AIToolResult.failure("Unknown tool: " + toolName);
        }
        
        // Check cooldown only if requested
        String cooldownKey = getCooldownKey(tool, villager, player);
        if (checkCooldown && isOnCooldown(tool, cooldownKey)) {
            long remainingCooldown = getRemainingCooldown(tool, cooldownKey);
            return AIToolResult.failure("Tool is on cooldown for " + remainingCooldown + " seconds");
        }
        
        try {
            // For Folia compatibility: Check if we need to execute on the entity's thread
            AIToolResult result;
            
            try {
                // Check if we're on the correct thread for entity access
                java.lang.reflect.Method isOwnedMethod = org.bukkit.Bukkit.class.getMethod("isOwnedByCurrentRegion", org.bukkit.entity.Entity.class);
                Boolean isOwned = (Boolean) isOwnedMethod.invoke(null, villager.bukkit());
                
                if (!isOwned) {
                    // We're on the wrong thread - need to execute on entity thread
                    // Use a CompletableFuture to synchronously wait for execution on the correct thread
                    java.util.concurrent.CompletableFuture<AIToolResult> future = new java.util.concurrent.CompletableFuture<>();
                    
                    plugin.getFoliaLib().getImpl().runAtEntity(villager.bukkit(), task -> {
                        try {
                            // Check reputation-based tool permissions on entity thread
                            if (!hasReputationForTool(toolName, villager, player)) {
                                future.complete(AIToolResult.failure("Insufficient reputation for this action"));
                                return;
                            }
                            
                            // Check if tool can be executed on entity thread
                            if (!tool.canExecute(villager, player, args)) {
                                future.complete(AIToolResult.failure("Tool cannot be executed with current parameters"));
                                return;
                            }
                            
                            AIToolResult entityResult = tool.execute(villager, player, args);
                            future.complete(entityResult);
                        } catch (Exception e) {
                            future.completeExceptionally(e);
                        }
                    });
                    
                    // Wait for the result (with timeout to prevent hanging)
                    result = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
                } else {
                    // We're on the correct thread, execute directly with all checks
                    
                    // Check reputation-based tool permissions
                    if (!hasReputationForTool(toolName, villager, player)) {
                        return AIToolResult.failure("Insufficient reputation for this action");
                    }
                    
                    // Check if tool can be executed
                    if (!tool.canExecute(villager, player, args)) {
                        return AIToolResult.failure("Tool cannot be executed with current parameters");
                    }
                    
                    result = tool.execute(villager, player, args);
                }
            } catch (NoSuchMethodException e) {
                // Not on Folia, execute directly with all checks
                
                // Check reputation-based tool permissions
                if (!hasReputationForTool(toolName, villager, player)) {
                    return AIToolResult.failure("Insufficient reputation for this action");
                }
                
                // Check if tool can be executed
                if (!tool.canExecute(villager, player, args)) {
                    return AIToolResult.failure("Tool cannot be executed with current parameters");
                }
                
                result = tool.execute(villager, player, args);
            }
            
            // Always update cooldown if successful (even in batch mode)
            // This prevents abuse but allows consecutive execution within a response
            if (result != null && result.isSuccess()) {
                updateCooldown(tool, cooldownKey);
                
                plugin.getLogger().info(String.format("AI Tool executed: %s by villager %s for player %s", 
                    toolName, villager.getVillagerName(), player.getName()));
            }
            
            return result != null ? result : AIToolResult.success();
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error executing AI tool: " + toolName, e);
            return AIToolResult.failure("Internal error executing tool");
        }
    }
    
    
    /**
     * Clears expired cooldowns
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        
        // Clear expired cooldowns
        toolCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
    
    private String getCooldownKey(@NotNull AITool tool, @NotNull IVillagerNPC villager, @NotNull Player player) {
        return villager.getUniqueId() + "_" + player.getUniqueId() + "_" + tool.getName() + "_cooldown";
    }
    
    
    private boolean isOnCooldown(@NotNull AITool tool, @NotNull String cooldownKey) {
        if (tool.getCooldownSeconds() <= 0) return false;
        
        Long lastUse = toolCooldowns.get(cooldownKey);
        if (lastUse == null) return false;
        
        return System.currentTimeMillis() - lastUse < (tool.getCooldownSeconds() * 1000L);
    }
    
    private long getRemainingCooldown(@NotNull AITool tool, @NotNull String cooldownKey) {
        Long lastUse = toolCooldowns.get(cooldownKey);
        if (lastUse == null) return 0;
        
        long elapsed = System.currentTimeMillis() - lastUse;
        long totalCooldown = tool.getCooldownSeconds() * 1000L;
        return Math.max(0, (totalCooldown - elapsed) / 1000);
    }
    
    
    private void updateCooldown(@NotNull AITool tool, @NotNull String cooldownKey) {
        if (tool.getCooldownSeconds() > 0) {
            toolCooldowns.put(cooldownKey, System.currentTimeMillis());
        }
    }
    
    /**
     * Check if a player has sufficient reputation to use a tool
     */
    private boolean hasReputationForTool(@NotNull String toolName, @NotNull IVillagerNPC villager, @NotNull Player player) {
        try {
            // Get AI service and its config
            var aiService = plugin.getAiService();
            if (aiService == null) return true;
            
            var aiConfig = aiService.getAIConfig();
            if (aiConfig == null) return true;
            
            // Get minimum reputation requirement for this tool
            int minReputation = aiConfig.getToolMinReputation(toolName);
            
            // Get current reputation
            var reputationManager = plugin.getReputationManager();
            int currentReputation;
            if (reputationManager != null && reputationManager.hasActiveProviders()) {
                currentReputation = reputationManager.getTotalReputation(villager, player);
            } else {
                currentReputation = villager.getReputation(player.getUniqueId());
            }
            
            return currentReputation >= minReputation;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error checking reputation for tool " + toolName, e);
            return true; // Default to allowing if check fails
        }
    }
    
}