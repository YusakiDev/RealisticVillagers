package me.matsubara.realisticvillagers.ai;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerAIChatEvent;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.AIConfig;
import me.matsubara.realisticvillagers.ai.tools.AIToolRegistry;
import me.matsubara.realisticvillagers.ai.tools.AIToolResult;
import me.matsubara.realisticvillagers.ai.tools.AIResponseParser;
import me.matsubara.realisticvillagers.ai.tools.impl.MovementTools;
import me.matsubara.realisticvillagers.ai.tools.impl.InteractionTools;
import me.matsubara.realisticvillagers.ai.tools.impl.ItemTools;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Service for managing AI-powered conversations with villagers.
 * EXPERIMENTAL FEATURE - Subject to change
 */
public class AIConversationService {
    
    private final RealisticVillagers plugin;
    private final AIConfig aiConfig;
    private final AnthropicAPIClient apiClient;
    private final AIToolRegistry toolRegistry;
    private final Map<String, ConversationContext> activeConversations;
    private final Map<UUID, Long> rateLimitMap;
    private final Map<UUID, AIChatSession> activeSessions;
    
    public AIConversationService(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
        this.aiConfig = new AIConfig(plugin);
        this.toolRegistry = new AIToolRegistry(plugin);
        this.activeConversations = new ConcurrentHashMap<>();
        this.rateLimitMap = new ConcurrentHashMap<>();
        this.activeSessions = new ConcurrentHashMap<>();
        
        // Register all available tools
        registerTools();
        
        if (aiConfig.isEnabled() && !aiConfig.getApiKey().isEmpty()) {
            this.apiClient = new AnthropicAPIClient(
                aiConfig.getApiKey(), 
                aiConfig.getModel(), 
                aiConfig.getTemperature(), 
                aiConfig.getMaxTokens(), 
                plugin.getLogger()
            );
            testConnection();
        } else {
            this.apiClient = null;
            if (aiConfig.isEnabled()) {
                plugin.getLogger().warning("AI Chat is enabled but no API key is configured!");
            }
        }
        
        // Clean up old conversations periodically
        if (aiConfig.isEnabled()) {
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupOldConversations, 
                20L * 60 * 5, 20L * 60 * 5); // Every 5 minutes
        }
    }
    
    public boolean isEnabled() {
        return aiConfig.isEnabled() && apiClient != null;
    }
    
    private void testConnection() {
        if (apiClient == null) return;
        
        apiClient.testConnection().thenAccept(success -> {
            if (success) {
                plugin.getLogger().info("AI Chat: Successfully connected to Anthropic API");
            } else {
                plugin.getLogger().severe("AI Chat: Failed to connect to Anthropic API. Please check your API key.");
            }
        });
    }
    
    /**
     * Checks if a player is rate limited
     */
    public boolean isRateLimited(@NotNull Player player) {
        Long lastUse = rateLimitMap.get(player.getUniqueId());
        if (lastUse == null) return false;
        
        long rateLimitCooldown = aiConfig.getRateLimitSeconds() * 1000;
        return System.currentTimeMillis() - lastUse < rateLimitCooldown;
    }
    
    /**
     * Gets the remaining cooldown time in seconds
     */
    public int getRemainingCooldown(@NotNull Player player) {
        Long lastUse = rateLimitMap.get(player.getUniqueId());
        if (lastUse == null) return 0;
        
        long rateLimitCooldown = aiConfig.getRateLimitSeconds() * 1000;
        long remaining = rateLimitCooldown - (System.currentTimeMillis() - lastUse);
        return Math.max(0, (int) (remaining / 1000));
    }
    
    /**
     * Sends a message to the AI and gets a response
     */
    public CompletableFuture<String> sendMessage(@NotNull IVillagerNPC npc, @NotNull Player player, @NotNull String message) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        
        // Update rate limit
        rateLimitMap.put(player.getUniqueId(), System.currentTimeMillis());
        
        // Get or create conversation context
        String conversationKey = npc.getUniqueId() + "_" + player.getUniqueId();
        ConversationContext context = activeConversations.computeIfAbsent(conversationKey, 
            k -> ConversationContext.fromGameState(npc, player));
        
        // Get villager persona
        Villager villager = (Villager) npc.bukkit();
        AIConfig.ProfessionPersonality personality = aiConfig.getPersonality(villager.getProfession());
        
        // Fallback to default if not found in config
        String personaPrompt = (personality != null) ? personality.getSystemPrompt() : 
            VillagerPersona.getPersona(villager.getProfession()).getSystemPrompt();
        
        // Build system prompt
        String fullSystemPrompt = buildSystemPrompt(personaPrompt, context);
        
        // Send request to AI
        return apiClient.sendChatRequest(fullSystemPrompt, context.getConversationHistory(), message)
            .thenApply(response -> {
                if (response != null) {
                    // Parse the AI response for both text and tool calls
                    AIResponseParser.ParsedResponse parsedResponse = AIResponseParser.parseResponse(response);
                    
                    // Execute any tool calls  
                    String finalResponse = processResponseWithTools(parsedResponse, npc, player, null, message);
                    
                    // Add to conversation history (use original response for context)
                    context.addMessage("user", message);
                    context.addMessage("assistant", response);
                    
                    // Update context timestamp
                    activeConversations.put(conversationKey, context);
                    
                    return finalResponse;
                } else {
                    plugin.getLogger().warning("AI Chat: Failed to get response from API");
                    return getErrorMessage();
                }
            })
            .exceptionally(throwable -> {
                plugin.getLogger().log(Level.SEVERE, "AI Chat: Error during conversation", throwable);
                return getErrorMessage();
            });
    }
    
    private String buildSystemPrompt(@NotNull String personaPrompt, @NotNull ConversationContext context) {
        StringBuilder prompt = new StringBuilder();
        
        // Add Minecraft world context
        String serverVersion = plugin.getServer().getBukkitVersion(); // e.g. "1.21.7-R0.1-SNAPSHOT"
        String minecraftVersion = extractMinecraftVersion(serverVersion);
        
        prompt.append(aiConfig.getWorldContextPrompt(minecraftVersion)).append("\n\n");
        prompt.append(personaPrompt).append("\n\n");
        prompt.append(context.getContextPrompt()).append("\n\n");
        
        // Add Minecraft knowledge if enabled
        if (aiConfig.isMinecraftKnowledgeEnabled()) {
            prompt.append(aiConfig.getMinecraftKnowledgePrompt(minecraftVersion)).append("\n\n");
        }
        
        // Add tool instructions if tools are enabled
        if (aiConfig.isToolsEnabled()) {
            prompt.append(aiConfig.getToolInstructionsPrompt()).append("\n\n");
        }
        
        prompt.append("CRITICAL RULES:\n");
        
        // Language rules based on config
        if ("auto".equalsIgnoreCase(aiConfig.getLanguageMode())) {
            prompt.append("- RESPOND IN THE SAME LANGUAGE the player used to speak to you\n");
        } else {
            prompt.append(aiConfig.getLanguageOverridePrompt(aiConfig.getLanguageMode())).append("\n");
        }
        
        prompt.append(aiConfig.getBehaviorRulesPrompt());
        
        return prompt.toString();
    }
    
    /**
     * Registers all available AI tools
     */
    private void registerTools() {
        // Movement tools
        toolRegistry.registerTool(new MovementTools.FollowPlayerTool());
        toolRegistry.registerTool(new MovementTools.StayHereTool());
        toolRegistry.registerTool(new MovementTools.StopMovementTool());
        
        // Interaction tools
        toolRegistry.registerTool(new InteractionTools.ShakeHeadTool());
        toolRegistry.registerTool(new InteractionTools.StopInteractionTool());
        toolRegistry.registerTool(new InteractionTools.ToggleFishingTool());
        
        // Item tools
        toolRegistry.registerTool(new ItemTools.GiveItemTool());
        toolRegistry.registerTool(new ItemTools.CheckInventoryTool());
        toolRegistry.registerTool(new ItemTools.PrepareForGiftTool());
        
        plugin.getLogger().info("AI Tools: Registered " + toolRegistry.getAllTools().size() + " tools");
    }
    
    /**
     * Processes an AI response with tool calls and returns the final response text
     * @param parsedResponse the parsed AI response
     * @param npc the villager
     * @param player the player
     * @return the final response text to display to the player
     */
    @NotNull
    private String processResponseWithTools(@NotNull AIResponseParser.ParsedResponse parsedResponse, 
                                            @NotNull IVillagerNPC npc, 
                                            @NotNull Player player, 
                                            @Nullable AIChatSession session, 
                                            @NotNull String originalMessage) {
        StringBuilder finalResponse = new StringBuilder();
        
        // Add the text response first
        if (parsedResponse.hasText()) {
            finalResponse.append(parsedResponse.getText());
        }
        
        // Execute tool calls if any and collect results for AI context
        StringBuilder toolResults = new StringBuilder();
        if (parsedResponse.hasToolCalls()) {
            for (AIResponseParser.ToolCall toolCall : parsedResponse.getToolCalls()) {
                try {
                    plugin.getLogger().info(String.format("AI Tool Call: %s by villager %s for player %s with args %s", 
                        toolCall.getName(), npc.getVillagerName(), player.getName(), toolCall.getArguments()));
                    
                    AIToolResult result = toolRegistry.executeTool(
                        toolCall.getName(), 
                        npc, 
                        player, 
                        toolCall.getArguments()
                    );
                    
                    // Log tool result
                    if (result.isSuccess()) {
                        plugin.getLogger().info("AI Tool Success: " + toolCall.getName() + " - " + result.getMessage());
                        // Add result to context for potential follow-up tools
                        toolResults.append(toolCall.getName()).append(" result: ").append(result.getMessage()).append("; ");
                    } else {
                        plugin.getLogger().warning("AI Tool Failed: " + toolCall.getName() + " - " + result.getMessage());
                        toolResults.append(toolCall.getName()).append(" failed: ").append(result.getMessage()).append("; ");
                    }
                    
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error executing AI tool: " + toolCall.getName(), e);
                }
            }
            
            // If we have tool results and the response seems incomplete, ask AI for follow-up
            if (toolResults.length() > 0 && shouldProcessFollowUp(parsedResponse, toolResults.toString())) {
                return processFollowUpResponse(npc, player, originalMessage, toolResults.toString(), finalResponse.toString());
            }
        }
        
        // Return the text response (tool effects are visible in-game, not in text)
        return finalResponse.toString().isEmpty() ? "..." : finalResponse.toString();
    }
    
    /**
     * Determines if AI should get follow-up call based on tool results
     */
    private boolean shouldProcessFollowUp(AIResponseParser.ParsedResponse response, String toolResults) {
        // Only follow up if we have check_inventory results (suggests AI might want to act on them)
        return toolResults.contains("check_inventory result:") && 
               !toolResults.contains("Empty inventory") &&
               response.getToolCalls().size() == 1; // Only had 1 tool call (the check)
    }
    
    /**
     * Processes follow-up AI call with tool results as context
     */
    private String processFollowUpResponse(IVillagerNPC npc, Player player,
                                         String originalMessage, String toolResults, String previousResponse) {
        try {
            String followUpPrompt = String.format(
                "Based on your tool results: %s\n" +
                "The player's original request was: \"%s\"\n" +
                "Your previous response was: \"%s\"\n" +
                "Do you need to use any additional tools to help the player? If so, respond with JSON including tools. If not, just respond with text.",
                toolResults, originalMessage, previousResponse
            );
            
            // Get conversation context from existing one (don't create new one from async thread)
            String conversationKey = player.getUniqueId() + ":" + npc.getUniqueId();
            ConversationContext context = activeConversations.get(conversationKey);
            if (context == null) {
                // Can't create new context from async thread - skip follow-up
                plugin.getLogger().warning("No conversation context available for follow-up, skipping");
                return previousResponse;
            }
            
            // Build system prompt like the main method
            String personaPrompt = VillagerPersona.getPersona(((Villager) npc.bukkit()).getProfession()).getSystemPrompt();
            String systemPrompt = buildSystemPrompt(personaPrompt, context);
            
            CompletableFuture<String> future = apiClient.sendChatRequest(systemPrompt, context.getConversationHistory(), followUpPrompt);
            String followUpResponse = future.get(10, TimeUnit.SECONDS);
            
            // Process the follow-up response (might contain more tool calls)
            AIResponseParser.ParsedResponse followUpParsed = AIResponseParser.parseResponse(followUpResponse);
            StringBuilder finalFollowUp = new StringBuilder();
            
            if (followUpParsed.hasText()) {
                finalFollowUp.append(followUpParsed.getText());
            }
            
            // Execute any additional tool calls
            if (followUpParsed.hasToolCalls()) {
                for (AIResponseParser.ToolCall toolCall : followUpParsed.getToolCalls()) {
                    try {
                        plugin.getLogger().info(String.format("AI Follow-up Tool Call: %s by villager %s for player %s", 
                            toolCall.getName(), npc.getVillagerName(), player.getName()));
                        
                        AIToolResult result = toolRegistry.executeTool(
                            toolCall.getName(), 
                            npc, 
                            player, 
                            toolCall.getArguments()
                        );
                        
                        if (result.isSuccess()) {
                            plugin.getLogger().info("AI Follow-up Tool Success: " + toolCall.getName() + " - " + result.getMessage());
                        } else {
                            plugin.getLogger().warning("AI Follow-up Tool Failed: " + toolCall.getName() + " - " + result.getMessage());
                        }
                        
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "Error executing follow-up AI tool: " + toolCall.getName(), e);
                    }
                }
            }
            
            return finalFollowUp.toString().isEmpty() ? previousResponse : finalFollowUp.toString();
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to process follow-up AI response", e);
            return previousResponse; // Fallback to original response
        }
    }
    
    /**
     * Extracts Minecraft version from server version string
     * e.g. "1.21.7-R0.1-SNAPSHOT" -> "1.21.7"
     */
    private String extractMinecraftVersion(String serverVersion) {
        if (serverVersion == null) return "Unknown";
        
        // Extract version number before the first hyphen
        int hyphenIndex = serverVersion.indexOf('-');
        if (hyphenIndex > 0) {
            return serverVersion.substring(0, hyphenIndex);
        }
        
        return serverVersion;
    }
    
    private String getErrorMessage() {
        return "I'm sorry, I seem to be having trouble thinking right now. Perhaps we can talk later?";
    }
    
    /**
     * Clears the conversation history for a specific villager-player pair
     */
    public void clearConversation(@NotNull IVillagerNPC npc, @NotNull Player player) {
        String conversationKey = npc.getUniqueId() + "_" + player.getUniqueId();
        activeConversations.remove(conversationKey);
        
        // Clear tool usage for this conversation
        toolRegistry.clearConversationUsage(npc, player);
    }
    
    /**
     * Removes conversations older than 30 minutes
     */
    private void cleanupOldConversations() {
        long thirtyMinutesAgo = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30);
        
        activeConversations.entrySet().removeIf(entry -> {
            ConversationContext context = entry.getValue();
            return context.getTimestamp().toInstant(java.time.ZoneOffset.UTC).toEpochMilli() < thirtyMinutesAgo;
        });
        
        // Also clean up old rate limit entries
        rateLimitMap.entrySet().removeIf(entry -> entry.getValue() < thirtyMinutesAgo);
        
        // Clean up expired chat sessions
        activeSessions.entrySet().removeIf(entry -> entry.getValue().isExpired(30));
        
        // Clean up tool registry
        toolRegistry.cleanup();
    }
    
    /**
     * Starts an AI chat session for a player with a villager
     */
    public void startChatSession(@NotNull IVillagerNPC npc, @NotNull Player player) {
        plugin.getLogger().info("AI Chat: startChatSession called for player " + player.getName() + " with villager " + npc.getVillagerName());
        
        if (!isEnabled()) {
            plugin.getLogger().warning("AI Chat: Service not enabled, isEnabled=" + isEnabled() + ", apiClient=" + (apiClient != null));
            player.sendMessage("§cAI Chat is not enabled or configured properly!");
            return;
        }
        
        AIChatSession session = new AIChatSession(player, npc);
        activeSessions.put(player.getUniqueId(), session);
        
        plugin.getLogger().info("AI Chat: Session created successfully for " + player.getName());
        player.sendMessage("§a§l[AI CHAT] §7Started conversation with §a" + npc.getVillagerName());
        player.sendMessage("§7Type your messages in chat. Type §c'exit' §7to end the conversation.");
        player.sendMessage("§c§lWARNING: §7This is an experimental AI feature!");
    }
    
    /**
     * Ends an AI chat session for a player
     */
    public void endChatSession(@NotNull Player player) {
        AIChatSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            player.sendMessage("§a§l[AI CHAT] §7Conversation with §a" + session.getVillagerName() + " §7ended.");
            
            // Clear conversation history
            String conversationKey = session.getVillagerUUID() + "_" + session.getPlayerUUID();
            activeConversations.remove(conversationKey);
            
            // Find the villager and clear tool usage
            for (org.bukkit.World world : plugin.getServer().getWorlds()) {
                for (org.bukkit.entity.Villager villager : world.getEntitiesByClass(org.bukkit.entity.Villager.class)) {
                    if (villager.getUniqueId().equals(session.getVillagerUUID())) {
                        IVillagerNPC npc = plugin.getConverter().getNPC(villager).orElse(null);
                        if (npc != null) {
                            toolRegistry.clearConversationUsage(npc, player);
                        }
                        break;
                    }
                }
            }
        }
    }
    
    /**
     * Checks if a player is in an AI chat session
     */
    public boolean isInChatSession(@NotNull Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }
    
    /**
     * Gets the active chat session for a player
     */
    @Nullable
    public AIChatSession getChatSession(@NotNull Player player) {
        return activeSessions.get(player.getUniqueId());
    }
    
    /**
     * Handles a chat message from a player in an AI session
     */
    public void handleChatMessage(@NotNull Player player, @NotNull String message) {
        AIChatSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;
        
        // Check for exit command
        if (message.trim().equalsIgnoreCase("exit")) {
            endChatSession(player);
            return;
        }
        
        // Check rate limit
        if (isRateLimited(player)) {
            int cooldown = getRemainingCooldown(player);
            player.sendMessage("§c§l[AI CHAT] §cPlease wait " + cooldown + " seconds before sending another message.");
            return;
        }
        
        // Show player's message immediately (this is safe in async)
        player.sendMessage("§7[§eYou§7] §f" + message);
        
        // Find the villager on the main thread (entity access must be synchronous)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            IVillagerNPC npc = null;
            for (org.bukkit.World world : plugin.getServer().getWorlds()) {
                for (org.bukkit.entity.Villager villager : world.getEntitiesByClass(org.bukkit.entity.Villager.class)) {
                    if (villager.getUniqueId().equals(session.getVillagerUUID())) {
                        npc = plugin.getConverter().getNPC(villager).orElse(null);
                        break;
                    }
                }
                if (npc != null) break;
            }
            
            if (npc == null) {
                player.sendMessage("§c§l[AI CHAT] §cCouldn't find the villager. Ending conversation.");
                endChatSession(player);
                return;
            }
            
            // Send to AI (this can be async)
            IVillagerNPC finalNpc = npc;
            sendMessage(npc, player, message).thenAccept(response -> {
                if (response != null) {
                    // Fire event on main thread
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        VillagerAIChatEvent event = new VillagerAIChatEvent(finalNpc, player, message, response);
                        plugin.getServer().getPluginManager().callEvent(event);
                        
                        if (!event.isCancelled()) {
                            player.sendMessage("§7[§a" + session.getVillagerName() + "§7] §f" + event.getResponse());
                        }
                    });
                } else {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c§l[AI CHAT] §c" + session.getVillagerName() + " seems unable to respond right now.");
                    });
                }
            });
        });
    }
    
    public void shutdown() {
        activeConversations.clear();
        rateLimitMap.clear();
        activeSessions.clear();
        
        // Clean up tool registry
        toolRegistry.cleanup();
    }
}