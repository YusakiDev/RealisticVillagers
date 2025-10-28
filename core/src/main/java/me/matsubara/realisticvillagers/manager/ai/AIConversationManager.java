package me.matsubara.realisticvillagers.manager.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.manager.ai.tools.AIResponseParser;
import me.matsubara.realisticvillagers.manager.ai.tools.AIToolRegistry;
import me.matsubara.realisticvillagers.manager.ai.tools.AIToolResult;
import me.matsubara.realisticvillagers.manager.ai.tools.ToolSystemManager;
import me.matsubara.realisticvillagers.manager.ai.tools.impl.InteractionTools;
import me.matsubara.realisticvillagers.manager.ai.tools.impl.ItemTools;
import me.matsubara.realisticvillagers.manager.ai.tools.impl.MovementTools;
import me.matsubara.realisticvillagers.util.PluginUtils;
import okhttp3.*;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages AI conversations between players and villagers.
 */
public class AIConversationManager {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();
    private static final int MAX_TOOL_ITERATIONS = 3;

    private final RealisticVillagers plugin;
    private FileConfiguration config;
    private OkHttpClient httpClient;
    private PersonalityBuilder personalityBuilder;
    private ConversationContext conversationContext;
    private ToolSystemManager toolSystemManager;
    private AIToolRegistry toolRegistry;
    private boolean debugEnabled;
    private boolean toolDebugEnabled;

    // Player UUID -> Villager UUID
    private final Map<UUID, UUID> activeConversations = new ConcurrentHashMap<>();

    // Conversation UUID -> List of messages (for history)
    private final Map<UUID, List<ConversationMessage>> conversationHistory = new ConcurrentHashMap<>();

    // Last message time for timeout tracking
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private WrappedTask distanceCheckTask;
    private ProviderSettings providerSettings;

    public AIConversationManager(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
        loadConfig();
        initialize();
        startDistanceCheckTask();
    }

    /**
     * Loads the AI configuration file.
     */
    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "ai-config.yml");

        // Create config file if it doesn't exist
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                InputStream inputStream = plugin.getResource("ai-config.yml");
                if (inputStream != null) {
                    Files.copy(inputStream, configFile.toPath());
                }
            } catch (IOException exception) {
                plugin.getLogger().severe("Failed to create ai-config.yml: " + exception.getMessage());
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * Initializes the HTTP client and context builders.
     */
    private void initialize() {
        providerSettings = null;
        httpClient = null;
        personalityBuilder = null;
        conversationContext = null;
        toolSystemManager = null;
        toolRegistry = null;
        boolean defaultDebug = config.getBoolean("debug.enabled", false);
        debugEnabled = config.getBoolean("debug.ai", defaultDebug);
        toolDebugEnabled = config.getBoolean("debug.tools", defaultDebug);

        if (!isEnabled()) {
            plugin.getLogger().info("AI conversations are disabled in config.");
            return;
        }

        String providerId = config.getString("provider", ProviderType.OPENAI.configKey);
        ProviderType providerType = ProviderType.fromConfig(providerId);
        if (!providerType.configKey.equalsIgnoreCase(providerId)) {
            plugin.getLogger().warning("Unknown AI provider '" + providerId + "' in ai-config.yml. Falling back to " + ProviderType.OPENAI.displayName + ".");
        }
        ProviderSettings settings = loadProviderSettings(providerType);
        if (settings == null) {
            return;
        }

        try {
            httpClient = createHttpClient(settings);
            providerSettings = settings;
            personalityBuilder = new PersonalityBuilder(config);
            conversationContext = new ConversationContext(config, personalityBuilder);
            setupToolSystem();
            plugin.getLogger().info("AI conversation system initialized successfully using " + providerType.displayName + "!");
        } catch (Exception exception) {
            plugin.getLogger().severe("Failed to initialize " + providerType.displayName + " client: " + exception.getMessage());
        }
    }

    /**
     * Reloads the configuration.
     */
    public void reload() {
        loadConfig();
        shutdown();
        initialize();
        startDistanceCheckTask();
    }

    /**
     * Shuts down the HTTP client.
     */
    public void shutdown() {
        if (httpClient != null) {
            try {
                httpClient.dispatcher().executorService().shutdown();
                httpClient.connectionPool().evictAll();
            } catch (Exception ignored) {
            }
            httpClient = null;
        }
        cancelDistanceCheckTask();
        providerSettings = null;
        personalityBuilder = null;
        conversationContext = null;
        toolSystemManager = null;
        toolRegistry = null;
        activeConversations.clear();
        conversationHistory.clear();
        lastMessageTime.clear();
    }

    /**
     * Checks if AI conversations are enabled.
     */
    public boolean isEnabled() {
        return config.getBoolean("conversation.enabled", true);
    }

    /**
     * Checks if the system is properly configured.
     */
    public boolean isConfigured() {
        return providerSettings != null && httpClient != null;
    }

    /**
     * Starts a conversation between a player and villager.
     *
     * @param player The player
     * @param npc The villager NPC
     * @return true if conversation started, false if already in conversation
     */
    public boolean startConversation(@NotNull Player player, @NotNull IVillagerNPC npc) {
        UUID playerUUID = player.getUniqueId();
        UUID villagerUUID = npc.getUniqueId();

        if (activeConversations.containsKey(playerUUID)) {
            return false;
        }

        activeConversations.put(playerUUID, villagerUUID);
        conversationHistory.put(playerUUID, new ArrayList<>());
        lastMessageTime.put(playerUUID, System.currentTimeMillis());

        return true;
    }

    /**
     * Ends a conversation for a player.
     *
     * @param player The player
     */
    public void endConversation(@NotNull Player player) {
        UUID playerUUID = player.getUniqueId();
        removeConversation(playerUUID, null);
    }

    /**
     * Toggles conversation mode for a player with a villager.
     * If not in conversation, starts one. If already in conversation with this villager, ends it.
     *
     * @param player The player
     * @param npc The villager NPC
     */
    public void handleConversationToggle(@NotNull Player player, @NotNull IVillagerNPC npc) {
        if (!isEnabled()) {
            sendConfigMessage(player, "messages.ai-disabled", "AI conversations are disabled.", null);
            return;
        }

        if (!isConfigured()) {
            sendConfigMessage(player, "messages.no-api-key", "AI conversations are not configured.", null);
            return;
        }

        // Check permission
        if (!player.hasPermission(config.getString("permissions.use", "realisticvillagers.ai.use"))) {
            sendConfigMessage(player, "messages.no-permission", "You don't have permission.", null);
            return;
        }

        UUID playerUUID = player.getUniqueId();
        UUID villagerUUID = npc.getUniqueId();

        // Check if already in conversation
        if (isInConversation(player)) {
            UUID currentVillager = getConversationVillager(player);
            if (currentVillager != null && currentVillager.equals(villagerUUID)) {
                // End conversation with this villager
                endConversation(player);
                sendConfigMessage(player, "messages.conversation-ended", "&cEnded conversation with %villager-name%.",
                        Map.of("%villager-name%", npc.getVillagerName()));

                // Play sound if enabled
                if (config.getBoolean("conversation.play-sounds", true)) {
                    String soundName = config.getString("conversation.end-sound", "ENTITY_VILLAGER_NO");
                    try {
                        player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(soundName), 1.0f, 1.0f);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            } else {
                sendConfigMessage(player, "messages.already-in-conversation", "&cYou are already in a conversation with %villager-name%.",
                        Map.of("%villager-name%", npc.getVillagerName()));
            }
            return;
        }

        // Check if villager is busy
        if (npc.isFighting() || npc.isInsideRaid() || npc.isProcreating()) {
            sendConfigMessage(player, "messages.villager-busy", "&c%villager-name% is busy and cannot talk right now.",
                    Map.of("%villager-name%", npc.getVillagerName()));
            return;
        }

        // Start conversation
        if (startConversation(player, npc)) {
            sendConfigMessage(player, "messages.conversation-started",
                    "&aStarted conversation with %villager-name%. &7(Shift+Right-Click to end)",
                    Map.of("%villager-name%", npc.getVillagerName()));

            // Play sound if enabled
            if (config.getBoolean("conversation.play-sounds", true)) {
                String soundName = config.getString("conversation.start-sound", "ENTITY_VILLAGER_YES");
                try {
                    player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(soundName), 1.0f, 1.0f);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    /**
     * Checks if a player is in an active conversation.
     *
     * @param player The player
     * @return true if in conversation
     */
    public boolean isInConversation(@NotNull Player player) {
        return activeConversations.containsKey(player.getUniqueId());
    }

    /**
     * Gets the villager a player is currently talking to.
     *
     * @param player The player
     * @return The villager UUID, or null if not in conversation
     */
    public @Nullable UUID getConversationVillager(@NotNull Player player) {
        return activeConversations.get(player.getUniqueId());
    }

    /**
     * Processes a player's message and gets an AI response.
     *
     * @param player The player
     * @param npc The villager NPC
     * @param message The player's message
     * @return A CompletableFuture with the AI response
     */
    public @NotNull CompletableFuture<String> processMessage(@NotNull Player player, @NotNull IVillagerNPC npc, @NotNull String message) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(null);
        }

        if (conversationContext == null) {
            plugin.getLogger().warning("AI conversation context not initialized; skipping message handling.");
            return CompletableFuture.completedFuture(null);
        }

        ProviderSettings settings = this.providerSettings;
        if (settings == null) {
            return CompletableFuture.completedFuture(null);
        }

        UUID playerUUID = player.getUniqueId();
        UUID villagerUUID = npc.getUniqueId();

        CompletableFuture<String> systemPromptFuture = new CompletableFuture<>();
        FoliaLib folia = plugin.getFoliaLib();
        if (folia != null) {
            folia.getScheduler().runAtEntity(npc.bukkit(), task -> {
                try {
                    systemPromptFuture.complete(conversationContext.buildSystemPrompt(player, npc));
                } catch (Throwable throwable) {
                    systemPromptFuture.completeExceptionally(throwable);
                }
            });
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    systemPromptFuture.complete(conversationContext.buildSystemPrompt(player, npc));
                } catch (Throwable throwable) {
                    systemPromptFuture.completeExceptionally(throwable);
                }
            });
        }

        return systemPromptFuture.thenCompose(systemPrompt -> CompletableFuture.supplyAsync(() -> {
            try {
                List<ConversationMessage> history = conversationHistory.get(playerUUID);
                boolean historyExists = history != null;
                if (!historyExists) {
                    history = new ArrayList<>();
                }

                int historyLength = config.getInt("context.conversation-history-length", 3);

                AIResponseParser.ParsedResponse parsedResponse = requestAIResponse(systemPrompt, history, message, historyLength);
                if (parsedResponse == null) {
                    debug("AI response parsing failed for player %s and villager %s.", player.getName(), npc.getVillagerName());
                    return null;
                }

                debug("AI initial response for %s -> %s | tools=%d", player.getName(), npc.getVillagerName(), parsedResponse.getToolCalls().size());

                history.add(new ConversationMessage(true, message));
                trimHistory(history, historyLength);

                String finalText = handleAIResponsesWithTools(parsedResponse, history, systemPrompt, historyLength, npc, player);
                if (finalText == null) {
                    finalText = parsedResponse.getText();
                }

                if (finalText == null) {
                    finalText = "";
                }
                finalText = finalText.trim();

                debug("Final AI response for %s -> %s: \"%s\"", player.getName(), npc.getVillagerName(), finalText);

                UUID activeVillager = activeConversations.get(playerUUID);
                if (activeVillager == null || !activeVillager.equals(villagerUUID)) {
                    if (historyExists) {
                        conversationHistory.remove(playerUUID, history);
                    }
                    return finalText;
                }

                if (!historyExists) {
                    conversationHistory.put(playerUUID, history);
                }

                lastMessageTime.put(playerUUID, System.currentTimeMillis());

                return finalText;
            } catch (Exception exception) {
                plugin.getLogger().warning("Error processing AI message: " + exception.getMessage());
                exception.printStackTrace();
                return null;
            }
        }));
    }

    private @Nullable String handleAIResponsesWithTools(
            @NotNull AIResponseParser.ParsedResponse initialResponse,
            @NotNull List<ConversationMessage> history,
            @NotNull String systemPrompt,
            int historyLength,
            @NotNull IVillagerNPC npc,
            @NotNull Player player) {

        AIResponseParser.ParsedResponse currentResponse = initialResponse;
        String lastAssistantText = null;
        int iteration = 0;

        while (true) {
            String assistantText = Optional.ofNullable(currentResponse.getText()).orElse("");
            List<AIResponseParser.ToolCall> toolCalls = toolSystemManager != null
                    ? currentResponse.getToolCalls()
                    : Collections.emptyList();

            debug("Iteration %d assistant text: \"%s\" | toolCalls=%d", iteration, assistantText, toolCalls.size());

            if (toolSystemManager == null && !toolCalls.isEmpty()) {
                plugin.getLogger().warning("Received tool calls but tool system is disabled; ignoring tool execution request.");
            }

            boolean canExecuteTools = toolSystemManager != null
                    && !toolCalls.isEmpty()
                    && iteration < MAX_TOOL_ITERATIONS;

            if (!canExecuteTools) {
                if (toolSystemManager != null && !toolCalls.isEmpty() && iteration >= MAX_TOOL_ITERATIONS) {
                    plugin.getLogger().warning("Maximum tool execution iterations reached; returning response without further tool calls.");
                }

                history.add(new ConversationMessage(false, assistantText));
                trimHistory(history, historyLength);
                lastAssistantText = assistantText;
                break;
            }

            List<AIResponseParser.ToolCall> limitedCalls = limitToolCalls(toolCalls);
            if (limitedCalls.isEmpty()) {
                history.add(new ConversationMessage(false, assistantText));
                trimHistory(history, historyLength);
                lastAssistantText = assistantText;
                break;
            }

            debug("Executing %d tool calls for %s -> %s", limitedCalls.size(), player.getName(), npc.getVillagerName());

            history.add(new ConversationMessage(false, assistantText));
            trimHistory(history, historyLength);

            List<AIToolResult> toolResults = executeToolCalls(limitedCalls, npc, player);
            debug("Tool results: %s", formatToolResultsForDebug(limitedCalls, toolResults));
            String toolResultsMessage = buildToolResultsMessage(limitedCalls, toolResults);
            history.add(new ConversationMessage(true, toolResultsMessage));
            trimHistory(history, historyLength);

            iteration++;

            AIResponseParser.ParsedResponse followUp = requestAIResponse(systemPrompt, history, null, historyLength);
            if (followUp == null) {
                plugin.getLogger().warning("Failed to obtain follow-up AI response after executing tools.");
                lastAssistantText = assistantText;
                break;
            }

            debug("Follow-up AI response text=\"%s\" tools=%d", followUp.getText(), followUp.getToolCalls().size());
            currentResponse = followUp;
        }

        return lastAssistantText;
    }

    private @Nullable AIResponseParser.ParsedResponse requestAIResponse(
            @NotNull String systemPrompt,
            @NotNull List<ConversationMessage> history,
            @Nullable String pendingUserMessage,
            int historyLength) {

        ProviderSettings settings = this.providerSettings;
        if (settings == null) {
            return null;
        }

        debug("Preparing AI request. history=%d pendingUserMessage=%s", history.size(), pendingUserMessage != null ? "present" : "none");

        JsonObject request = new JsonObject();
        request.addProperty("model", settings.getModel());
        request.addProperty("temperature", settings.getTemperature());
        request.addProperty("max_tokens", settings.getMaxTokens());

        JsonArray messages = new JsonArray();

        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", systemPrompt);
        messages.add(systemMsg);

        int maxHistoryEntries = Math.max(0, historyLength * 2);
        int startIndex = maxHistoryEntries > 0 ? Math.max(0, history.size() - maxHistoryEntries) : 0;
        for (int i = startIndex; i < history.size(); i++) {
            ConversationMessage msg = history.get(i);
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("role", msg.isUser() ? "user" : "assistant");
            msgObj.addProperty("content", msg.getContent());
            messages.add(msgObj);
        }

        if (pendingUserMessage != null) {
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", pendingUserMessage);
            messages.add(userMsg);
        }

        request.add("messages", messages);

        RequestBody body = RequestBody.create(GSON.toJson(request), JSON);
        Request httpRequest = new Request.Builder()
                .url(settings.getBaseUrl() + "/chat/completions")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                plugin.getLogger().warning("API request failed: " + response.code() + " " + response.message());
                if (response.body() != null) {
                    plugin.getLogger().warning("Response body: " + response.body().string());
                }
                return null;
            }

            String responseBody = response.body() != null ? response.body().string() : null;
            if (responseBody == null || responseBody.isBlank()) {
                debug("Received empty response body from AI provider.");
                return null;
            }

            return AIResponseParser.parseResponse(responseBody);
        } catch (IOException exception) {
            plugin.getLogger().warning("Error calling AI provider: " + exception.getMessage());
            return null;
        }
    }

    private @NotNull List<AIToolResult> executeToolCalls(
            @NotNull List<AIResponseParser.ToolCall> toolCalls,
            @NotNull IVillagerNPC npc,
            @NotNull Player player) {

        if (toolSystemManager == null || toolCalls.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            debug("Dispatching %d tool(s) to ToolSystemManager", toolCalls.size());
            CompletableFuture<List<AIToolResult>> future = toolSystemManager.executeTools(toolCalls, npc, player);
            List<AIToolResult> results = future.get(5, TimeUnit.SECONDS);
            if (results == null) {
                debug("Tool execution returned null results.");
                return Collections.emptyList();
            }
            return results;
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("Tool execution interrupted: " + interruptedException.getMessage());
            return List.of(AIToolResult.failure("Tool execution interrupted."));
        } catch (Exception exception) {
            plugin.getLogger().warning("Error executing tools: " + exception.getMessage());
            return List.of(AIToolResult.failure("Error executing tools: " + exception.getMessage()));
        }
    }

    private @NotNull String buildToolResultsMessage(
            @NotNull List<AIResponseParser.ToolCall> toolCalls,
            @NotNull List<AIToolResult> toolResults) {

        StringBuilder builder = new StringBuilder("[Tool Results:\n");

        if (toolResults.isEmpty()) {
            builder.append("- No results (execution failed or timed out)\n");
        } else {
            for (int i = 0; i < toolResults.size(); i++) {
                AIToolResult result = toolResults.get(i);
                String toolName = i < toolCalls.size() ? toolCalls.get(i).getName() : "tool-" + (i + 1);
                builder.append("- ").append(toolName).append(": ").append(result.formatForAI()).append("\n");
            }
        }

        builder.append("]");
        return builder.toString();
    }

    private @NotNull List<AIResponseParser.ToolCall> limitToolCalls(@NotNull List<AIResponseParser.ToolCall> toolCalls) {
        int maxTools = Math.max(0, config.getInt("tools.max-tools-per-response", 3));
        if (maxTools <= 0) {
            plugin.getLogger().warning("Tool system enabled but max-tools-per-response <= 0; skipping tool execution.");
            return Collections.emptyList();
        }

        if (toolCalls.size() <= maxTools) {
            return new ArrayList<>(toolCalls);
        }

        plugin.getLogger().warning("Received " + toolCalls.size() + " tool calls; limiting execution to first " + maxTools + ".");
        return new ArrayList<>(toolCalls.subList(0, maxTools));
    }

    private void trimHistory(@NotNull List<ConversationMessage> history, int historyLength) {
        if (historyLength <= 0) {
            return;
        }

        int maxEntries = Math.max(0, historyLength * 2);
        if (maxEntries > 0 && history.size() > maxEntries) {
            history.subList(0, history.size() - maxEntries).clear();
        }
    }

    /**
     * Initializes the tool system when enabled in configuration.
     */
    private void setupToolSystem() {
        if (!config.getBoolean("tools.enabled", false)) {
            plugin.getLogger().info("AI tools are disabled in ai-config.yml.");
            return;
        }

        AIToolRegistry registry = new AIToolRegistry(plugin);
        registerDefaultTools(registry);
        registry.loadToolConfigs(config);

        toolRegistry = registry;
        toolSystemManager = new ToolSystemManager(plugin, registry, toolDebugEnabled);

        plugin.getLogger().info("AI tool system initialized with " + registry.getAllTools().size() + " tools.");
    }

    /**
     * Registers the base set of AI tools.
     */
    private void registerDefaultTools(@NotNull AIToolRegistry registry) {
        registry.registerTool(new MovementTools.FollowPlayerTool());
        registry.registerTool(new MovementTools.StayHereTool());
        registry.registerTool(new MovementTools.StopMovementTool());

        registry.registerTool(new InteractionTools.ShakeHeadTool());
        registry.registerTool(new InteractionTools.StopInteractionTool());
        registry.registerTool(new InteractionTools.ToggleFishingTool());

        registry.registerTool(new ItemTools.GiveItemTool());
        registry.registerTool(new ItemTools.CheckInventoryTool());
        registry.registerTool(new ItemTools.PrepareForGiftTool());
        registry.registerTool(new ItemTools.CheckPlayerItemTool());
    }

    private void debug(@NotNull String message, Object... args) {
        if (!debugEnabled) {
            return;
        }
        plugin.getLogger().info("[AI Debug] " + String.format(message, args));
    }

    private @NotNull String formatToolResultsForDebug(
            @NotNull List<AIResponseParser.ToolCall> toolCalls,
            @NotNull List<AIToolResult> toolResults) {

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < toolResults.size(); i++) {
            String toolName = i < toolCalls.size() ? toolCalls.get(i).getName() : "tool-" + (i + 1);
            AIToolResult result = toolResults.get(i);
            builder.append(toolName)
                    .append(": ")
                    .append(result.isSuccess() ? "SUCCESS" : "FAIL")
                    .append(" (")
                    .append(result.getMessage())
                    .append(")");
            if (i + 1 < toolResults.size()) {
                builder.append("; ");
            }
        }
        if (builder.length() == 0) {
            builder.append("No tool results.");
        }
        return builder.toString();
    }

    /**
     * Starts a repeating task to check distances and timeouts.
     */
    private void startDistanceCheckTask() {
        cancelDistanceCheckTask();

        if (!isEnabled() || providerSettings == null) {
            return;
        }

        int interval = Math.max(1, config.getInt("conversation.distance-check-interval", 20));
        FoliaLib folia = plugin.getFoliaLib();
        if (folia == null) {
            plugin.getLogger().warning("Folia scheduler unavailable; AI conversation distance checks disabled.");
            return;
        }

        distanceCheckTask = folia.getScheduler().runTimer(this::runDistanceAndTimeoutChecks, interval, interval);
    }

    private void runDistanceAndTimeoutChecks() {
        if (!isEnabled() || providerSettings == null) {
            return;
        }

        double maxDistance = config.getDouble("conversation.max-distance", 10.0);
        double maxDistanceSquared = maxDistance * maxDistance;

        long timeout = config.getLong("conversation.timeout", 300) * 1000; // Convert to milliseconds
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<UUID, UUID> entry : activeConversations.entrySet()) {
            UUID playerUUID = entry.getKey();
            UUID villagerUUID = entry.getValue();

            Player player = plugin.getServer().getPlayer(playerUUID);
            if (player == null || !player.isOnline()) {
                removeConversation(playerUUID, villagerUUID);
                continue;
            }

            IVillagerNPC npc = plugin.getTracker().getOfflineByUUID(villagerUUID);
            if (npc == null || !npc.bukkit().isValid()) {
                removeConversation(playerUUID, villagerUUID);
                sendConfigMessage(player, "messages.conversation-ended", "&cEnded conversation with %villager-name%.",
                        Map.of("%villager-name%", "the villager"));
                continue;
            }

            Location playerLoc = player.getLocation();
            Location villagerLoc = npc.bukkit().getLocation();

            if (!playerLoc.getWorld().equals(villagerLoc.getWorld())
                    || playerLoc.distanceSquared(villagerLoc) > maxDistanceSquared) {
                removeConversation(playerUUID, villagerUUID);
                sendConfigMessage(player, "messages.conversation-too-far", "&cYou moved too far from %villager-name%. Conversation ended.",
                        Map.of("%villager-name%", npc.getVillagerName()));
                continue;
            }

            if (timeout > 0) {
                Long lastTime = lastMessageTime.get(playerUUID);
                if (lastTime != null && (currentTime - lastTime) > timeout) {
                    removeConversation(playerUUID, villagerUUID);
                    sendConfigMessage(player, "messages.conversation-timeout",
                            "&cConversation with %villager-name% ended due to inactivity.",
                            Map.of("%villager-name%", npc.getVillagerName()));
                }
            }
        }
    }

    private void removeConversation(@NotNull UUID playerUUID, @Nullable UUID expectedVillagerUUID) {
        if (expectedVillagerUUID == null) {
            activeConversations.remove(playerUUID);
        } else {
            activeConversations.remove(playerUUID, expectedVillagerUUID);
        }
        conversationHistory.remove(playerUUID);
        lastMessageTime.remove(playerUUID);
    }

    private void cancelDistanceCheckTask() {
        if (distanceCheckTask != null) {
            distanceCheckTask.cancel();
            distanceCheckTask = null;
        }
    }

    private @Nullable ProviderSettings loadProviderSettings(@NotNull ProviderType providerType) {
        String sectionKey = providerType.configKey;
        ConfigurationSection section = config.getConfigurationSection(sectionKey);
        if (section == null) {
            plugin.getLogger().warning("Missing configuration section '" + sectionKey + "' in ai-config.yml. AI conversations will be disabled.");
            return null;
        }

        String rawKey = section.getString("api-key", "");
        if (rawKey == null) {
            rawKey = "";
        }
        String apiKey = rawKey.trim();
        if (apiKey.isEmpty() || isPlaceholderKey(apiKey)) {
            plugin.getLogger().warning(providerType.displayName + " API key not configured! AI conversations will not work.");
            plugin.getLogger().warning("Please set your API key in ai-config.yml");
            return null;
        }

        int timeoutSeconds = section.contains("timeout")
                ? section.getInt("timeout")
                : config.getInt("openai.timeout", 10);
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 10;
        }

        double temperature = section.contains("temperature")
                ? section.getDouble("temperature")
                : config.getDouble("openai.temperature", 0.8d);
        if (temperature < 0d) {
            temperature = 0d;
        } else if (temperature > 2d) {
            temperature = 2d;
        }

        int maxTokens = section.contains("max-tokens")
                ? section.getInt("max-tokens")
                : config.getInt("openai.max-tokens", 150);
        if (maxTokens <= 0) {
            maxTokens = 150;
        }

        String model = section.getString("model");
        if (model == null || model.isBlank()) {
            model = config.getString("openai.model", providerType.defaultModel);
        }
        if (model == null || model.isBlank()) {
            model = providerType.defaultModel;
        }
        model = model.trim();

        String baseUrl = section.getString("base-url", providerType.defaultBaseUrl);
        if (baseUrl != null) {
            baseUrl = baseUrl.trim();
        }

        return new ProviderSettings(providerType, apiKey, model, temperature, maxTokens, timeoutSeconds, baseUrl);
    }

    private @NotNull OkHttpClient createHttpClient(@NotNull ProviderSettings settings) {
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request request = chain.request().newBuilder()
                            .addHeader("Authorization", "Bearer " + settings.getApiKey())
                            .addHeader("Content-Type", "application/json")
                            .build();
                    return chain.proceed(request);
                })
                .connectTimeout(settings.getTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(settings.getTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(settings.getTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
    }

    private @NotNull String normalizeBaseUrl(@Nullable String candidate, @NotNull String fallback) {
        String value = (candidate == null || candidate.isBlank()) ? fallback : candidate.trim();
        // Remove trailing slash
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private boolean isPlaceholderKey(@NotNull String apiKey) {
        String upper = apiKey.toUpperCase(Locale.ROOT);
        return upper.contains("YOUR") && upper.contains("API");
    }

    private void sendConfigMessage(@NotNull Player player,
                                   @NotNull String path,
                                   @NotNull String defaultValue,
                                   @Nullable Map<String, String> replacements) {
        String message = config.getString(path);
        if (message == null || message.isBlank()) {
            message = defaultValue;
        }
        if (message == null || message.isBlank()) {
            return;
        }
        if (replacements != null && !replacements.isEmpty()) {
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                message = message.replace(entry.getKey(), entry.getValue());
            }
        }
        player.sendMessage(PluginUtils.translate(message));
    }

    public @NotNull FileConfiguration getConfig() {
        return config;
    }

    /**
     * Simple class to store conversation message history
     */
    private static final class ConversationMessage {
        private final boolean isUser;
        private final String content;

        private ConversationMessage(boolean isUser, String content) {
            this.isUser = isUser;
            this.content = content;
        }

        private boolean isUser() {
            return isUser;
        }

        private String getContent() {
            return content;
        }
    }

    private static final class ProviderSettings {
        private final ProviderType providerType;
        private final String apiKey;
        private final String model;
        private final double temperature;
        private final int maxTokens;
        private final int timeoutSeconds;
        private final String baseUrl;

        private ProviderSettings(
                @NotNull ProviderType providerType,
                @NotNull String apiKey,
                @NotNull String model,
                double temperature,
                int maxTokens,
                int timeoutSeconds,
                @NotNull String baseUrl
        ) {
            this.providerType = providerType;
            this.apiKey = apiKey;
            this.model = model;
            this.temperature = temperature;
            this.maxTokens = maxTokens;
            this.timeoutSeconds = timeoutSeconds;
            this.baseUrl = normalizeBaseUrl(baseUrl, providerType.defaultBaseUrl);
        }

        private @NotNull ProviderType getProviderType() {
            return providerType;
        }

        private @NotNull String getApiKey() {
            return apiKey;
        }

        private @NotNull String getModel() {
            return model;
        }

        private double getTemperature() {
            return temperature;
        }

        private int getMaxTokens() {
            return maxTokens;
        }

        private int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        private @NotNull String getBaseUrl() {
            return baseUrl;
        }

        private static @NotNull String normalizeBaseUrl(@Nullable String candidate, @NotNull String fallback) {
            String value = (candidate == null || candidate.isBlank()) ? fallback : candidate.trim();
            // Remove trailing slash
            if (value.endsWith("/")) {
                value = value.substring(0, value.length() - 1);
            }
            return value;
        }
    }

    private enum ProviderType {
        OPENAI("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
        GROQ("groq", "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile");

        private final String configKey;
        private final String displayName;
        private final String defaultBaseUrl;
        private final String defaultModel;

        ProviderType(String configKey, String displayName, String defaultBaseUrl, String defaultModel) {
            this.configKey = configKey;
            this.displayName = displayName;
            this.defaultBaseUrl = defaultBaseUrl;
            this.defaultModel = defaultModel;
        }

        private static ProviderType fromConfig(@Nullable String rawValue) {
            if (rawValue != null) {
                for (ProviderType value : values()) {
                    if (value.configKey.equalsIgnoreCase(rawValue.trim())) {
                        return value;
                    }
                }
            }
            return OPENAI;
        }
    }
}
