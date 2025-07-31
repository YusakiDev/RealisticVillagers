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

import java.util.List;
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
    private final Map<UUID, Boolean> autoChatVillagers; // Tracks which villagers have auto-chat enabled
    
    public AIConversationService(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
        this.aiConfig = new AIConfig(plugin);
        this.toolRegistry = new AIToolRegistry(plugin);
        this.activeConversations = new ConcurrentHashMap<>();
        this.rateLimitMap = new ConcurrentHashMap<>();
        this.activeSessions = new ConcurrentHashMap<>();
        this.autoChatVillagers = new ConcurrentHashMap<>();
        
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
    
    public boolean isNaturalChatEnabled() {
        return isEnabled() && aiConfig.isNaturalChatEnabled();
    }
    
    public int getNaturalChatTriggerRange() {
        return aiConfig.getNaturalChatTriggerRange();
    }
    
    public int getNaturalChatHearingRange() {
        return aiConfig.getNaturalChatHearingRange();
    }
    
    public int getConversationMemoryMinutes() {
        return aiConfig.getConversationMemoryMinutes();
    }
    
    /**
     * Toggles auto-chat mode for a villager
     * @param villager the villager to toggle
     * @return true if auto-chat is now enabled, false if disabled
     */
    public boolean toggleAutoChat(@NotNull IVillagerNPC villager) {
        UUID villagerUUID = villager.bukkit().getUniqueId();
        boolean currentState = autoChatVillagers.getOrDefault(villagerUUID, false);
        boolean newState = !currentState;
        
        if (newState) {
            autoChatVillagers.put(villagerUUID, true);
        } else {
            autoChatVillagers.remove(villagerUUID);
        }
        
        return newState;
    }
    
    /**
     * Checks if a villager has auto-chat enabled
     * @param villager the villager to check
     * @return true if auto-chat is enabled for this villager
     */
    public boolean hasAutoChat(@NotNull IVillagerNPC villager) {
        return autoChatVillagers.getOrDefault(villager.bukkit().getUniqueId(), false);
    }
    
    /**
     * Checks if a villager has auto-chat enabled by UUID
     * @param villagerUUID the UUID of the villager
     * @return true if auto-chat is enabled for this villager
     */
    public boolean hasAutoChat(@NotNull UUID villagerUUID) {
        return autoChatVillagers.getOrDefault(villagerUUID, false);
    }
    
    /**
     * Disables auto-chat for all villagers
     * Used to make the /rv aichat command exclusive
     */
    public void disableAllAutoChat() {
        autoChatVillagers.clear();
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
     * Sends a natural chat message to the AI (no session required)
     */
    public CompletableFuture<String> sendNaturalMessage(@NotNull IVillagerNPC npc, @NotNull Player player, @NotNull String message) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        
        // Update rate limit
        rateLimitMap.put(player.getUniqueId(), System.currentTimeMillis());
        
        // Get or create conversation context for this villager (global, not per-player)
        String conversationKey = npc.getUniqueId().toString(); // Global conversation per villager
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
                    
                    // Add user message to history with player name for multi-player context
                    context.addMessage("user", "[" + player.getName() + "]: " + message);
                    
                    // Execute any tool calls  
                    ToolProcessingResult result = processResponseWithTools(parsedResponse, npc, player, context, message);
                    
                    // If we executed tools and got results, send them back to AI for follow-up
                    if (!result.toolResults.isEmpty() && parsedResponse.hasToolCalls()) {
                        // Add the initial response to context first
                        String initialMessage = parsedResponse.hasText() ? parsedResponse.getText() : response;
                        context.addMessage("assistant", initialMessage + "\n\n[Tool Results: " + result.toolResults + "]");
                        
                        // Send tool results back to AI for a follow-up response
                        String followUpPrompt = "Based on the tool results above, please provide a natural response about what you discovered.";
                        
                        try {
                            String followUpResponse = apiClient.sendChatRequest(fullSystemPrompt, context.getConversationHistory(), followUpPrompt).get();
                            if (followUpResponse != null && !followUpResponse.isEmpty()) {
                                // Parse the follow-up response (should just be text, no more tools)
                                AIResponseParser.ParsedResponse followUpParsed = AIResponseParser.parseResponse(followUpResponse);
                                String finalResponse = followUpParsed.hasText() ? followUpParsed.getText() : followUpResponse;
                                
                                // Clean any tool results that might be in the follow-up
                                finalResponse = finalResponse.replaceAll("\\[Tool Results:.*?\\]", "").trim();
                                
                                // Add follow-up to context
                                context.addMessage("assistant", finalResponse);
                                
                                // Update context timestamp
                                activeConversations.put(conversationKey, context);
                                
                                return finalResponse;
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to get follow-up response from AI: " + e.getMessage());
                            // Fall through to return original response
                        }
                    }
                    
                    // No tools or follow-up failed - return original response
                    // Add assistant response to history (with tool results if any)
                    String assistantMessage = parsedResponse.hasText() ? parsedResponse.getText() : response;
                    if (!result.toolResults.isEmpty()) {
                        // Append tool results to the assistant message for AI context
                        assistantMessage += "\n\n[Tool Results: " + result.toolResults + "]";
                    }
                    context.addMessage("assistant", assistantMessage);
                    
                    // Update context timestamp
                    activeConversations.put(conversationKey, context);
                    
                    // Always return the parsed text, never the raw JSON
                    String returnValue = parsedResponse.hasText() ? parsedResponse.getText() : 
                           (result.responseText.isEmpty() ? "..." : result.responseText);
                    
                    // Clean any tool results that the AI might have included in the response
                    returnValue = returnValue.replaceAll("\\[Tool Results:.*?\\]", "").trim();
                    
                    return returnValue;
                } else {
                    plugin.getLogger().warning("AI Chat: Failed to get response from API");
                    return getErrorMessage();
                }
            })
            .exceptionally(throwable -> {
                plugin.getLogger().log(Level.SEVERE, "AI Chat: Error during natural conversation", throwable);
                return getErrorMessage();
            });
    }
    
    /**
     * Sends a message to the AI and gets a response (legacy session-based method)
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
                    
                    // Add user message to history first
                    context.addMessage("user", message);
                    
                    // Execute any tool calls  
                    ToolProcessingResult result = processResponseWithTools(parsedResponse, npc, player, context, message);
                    
                    // If we executed tools and got results, send them back to AI for follow-up
                    if (!result.toolResults.isEmpty() && parsedResponse.hasToolCalls()) {
                        // Add the initial response to context first
                        String initialMessage = parsedResponse.hasText() ? parsedResponse.getText() : response;
                        context.addMessage("assistant", initialMessage + "\n\n[Tool Results: " + result.toolResults + "]");
                        
                        // Send tool results back to AI for a follow-up response
                        String followUpPrompt = "Based on the tool results above, please provide a natural response about what you discovered.";
                        
                        try {
                            String followUpResponse = apiClient.sendChatRequest(fullSystemPrompt, context.getConversationHistory(), followUpPrompt).get();
                            if (followUpResponse != null && !followUpResponse.isEmpty()) {
                                // Parse the follow-up response (should just be text, no more tools)
                                AIResponseParser.ParsedResponse followUpParsed = AIResponseParser.parseResponse(followUpResponse);
                                String finalResponse = followUpParsed.hasText() ? followUpParsed.getText() : followUpResponse;
                                
                                // Clean any tool results that might be in the follow-up
                                finalResponse = finalResponse.replaceAll("\\[Tool Results:.*?\\]", "").trim();
                                
                                // Add follow-up to context
                                context.addMessage("assistant", finalResponse);
                                
                                // Update context timestamp
                                activeConversations.put(conversationKey, context);
                                
                                return finalResponse;
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to get follow-up response from AI: " + e.getMessage());
                            // Fall through to return original response
                        }
                    }
                    
                    // No tools or follow-up failed - return original response
                    // Add assistant response to history (with tool results if any)
                    String assistantMessage = parsedResponse.hasText() ? parsedResponse.getText() : response;
                    if (!result.toolResults.isEmpty()) {
                        // Append tool results to the assistant message for AI context
                        assistantMessage += "\n\n[Tool Results: " + result.toolResults + "]";
                    }
                    context.addMessage("assistant", assistantMessage);
                    
                    // Update context timestamp
                    activeConversations.put(conversationKey, context);
                    
                    // Always return the parsed text, never the raw JSON
                    String returnValue = parsedResponse.hasText() ? parsedResponse.getText() : 
                           (result.responseText.isEmpty() ? "..." : result.responseText);
                    
                    // Clean any tool results that the AI might have included in the response
                    returnValue = returnValue.replaceAll("\\[Tool Results:.*?\\]", "").trim();
                    
                    return returnValue;
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
        toolRegistry.registerTool(new ItemTools.CheckPlayerItemTool());
        
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
    private ToolProcessingResult processResponseWithTools(@NotNull AIResponseParser.ParsedResponse parsedResponse, 
                                            @NotNull IVillagerNPC npc, 
                                            @NotNull Player player, 
                                            @NotNull ConversationContext context, 
                                            @NotNull String originalMessage) {
        StringBuilder finalResponse = new StringBuilder();
        
        // Add the text response first
        if (parsedResponse.hasText()) {
            finalResponse.append(parsedResponse.getText());
        }
        
        // Execute tool calls if any and collect results for AI context
        StringBuilder toolResults = new StringBuilder();
        if (parsedResponse.hasToolCalls()) {
            int maxTools = aiConfig.getMaxToolsPerResponse();
            int toolsExecuted = 0;
            
            for (AIResponseParser.ToolCall toolCall : parsedResponse.getToolCalls()) {
                // Respect max tools per response limit
                if (maxTools > 0 && toolsExecuted >= maxTools) {
                    plugin.getLogger().warning(String.format("AI Tool limit reached (%d/%d), skipping tool: %s", 
                        toolsExecuted, maxTools, toolCall.getName()));
                    break;
                }
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
                        toolResults.append(toolCall.getName()).append(" result: ").append(result.getMessage()).append("; ");
                    } else {
                        plugin.getLogger().warning("AI Tool Failed: " + toolCall.getName() + " - " + result.getMessage());
                        toolResults.append(toolCall.getName()).append(" failed: ").append(result.getMessage()).append("; ");
                    }
                    
                    // Tool results will be included in the assistant message later
                    
                    toolsExecuted++;
                    
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error executing AI tool: " + toolCall.getName(), e);
                }
            }
        }
        
        // Clean up the response text by removing any tool result artifacts
        String cleanedText = finalResponse.toString();
        
        // Remove any [Tool Results: ...] text that the AI might have included
        cleanedText = cleanedText.replaceAll("\\[Tool Results:.*?\\]", "").trim();
        
        // If there's no text but we executed tools, require the AI to provide dialogue
        // This prevents responses that are only tool calls without spoken content
        if (cleanedText.isEmpty() && parsedResponse.hasToolCalls()) {
            // Generate a fallback response based on the tools used
            cleanedText = generateFallbackDialogue(parsedResponse.getToolCalls());
        } else if (cleanedText.isEmpty()) {
            cleanedText = "..."; // Fallback for completely empty responses
        }
        
        String responseText = cleanedText;
            
        return new ToolProcessingResult(responseText, toolResults.toString());
    }
    
    /**
     * Generates appropriate fallback dialogue when the AI uses tools but provides no spoken text
     */
    @NotNull
    private String generateFallbackDialogue(@NotNull List<AIResponseParser.ToolCall> toolCalls) {
        if (toolCalls.isEmpty()) {
            return "...";
        }
        
        // Generate contextual responses based on the tools used
        for (AIResponseParser.ToolCall toolCall : toolCalls) {
            switch (toolCall.getName()) {
                case "prepare_for_gift":
                    return "Let me get ready for that.";
                case "give_item":
                    return "Here, take this.";
                case "check_inventory":
                    return "Let me see what I have.";
                case "check_player_item":
                    return "What's that you have there?";
                case "follow_player":
                    return "I'll come with you.";
                case "stay_here":
                    return "I'll wait here.";
                case "stop_movement":
                    return "Alright, I'll stop.";
                case "shake_head":
                    return "No, I don't think so.";
                case "toggle_fishing":
                    return "Time to fish.";
                default:
                    return "There we go.";
            }
        }
        
        return "There we go.";
    }
    
    // Simple result class to hold both response text and tool results
    private static class ToolProcessingResult {
        final String responseText;
        final String toolResults;
        
        ToolProcessingResult(String responseText, String toolResults) {
            this.responseText = responseText;
            this.toolResults = toolResults;
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
     * Clears the conversation history for a specific villager-player pair (legacy mode)
     */
    public void clearConversation(@NotNull IVillagerNPC npc, @NotNull Player player) {
        String conversationKey = npc.getUniqueId() + "_" + player.getUniqueId();
        activeConversations.remove(conversationKey);
        
    }
    
    /**
     * Clears the global conversation history for a villager (affects all players)
     */
    public void clearNaturalConversation(@NotNull IVillagerNPC npc) {
        String conversationKey = npc.getUniqueId().toString();
        activeConversations.remove(conversationKey);
        
        // Note: We don't clear tool usage here since it's per-player-villager pair
        // and natural conversations don't track per-player tool usage separately
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
                        
                        if (!event.isCancelled() && !event.getResponse().isEmpty()) {
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
        autoChatVillagers.clear();
        
        // Clean up tool registry
        toolRegistry.cleanup();
    }
}