package me.matsubara.realisticvillagers.ai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client for interacting with the Anthropic API.
 * EXPERIMENTAL FEATURE - Subject to change
 */
public class AnthropicAPIClient {
    
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final HttpClient httpClient;
    private final Gson gson;
    private final Logger logger;
    
    public AnthropicAPIClient(@NotNull String apiKey, @NotNull String model, 
                            double temperature, int maxTokens, @NotNull Logger logger) {
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
        this.gson = new Gson();
    }
    
    /**
     * Sends a chat completion request to the Anthropic API
     */
    public CompletableFuture<String> sendChatRequest(@NotNull String systemPrompt, 
                                                    @NotNull List<ConversationContext.Message> messages,
                                                    @NotNull String userMessage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject requestBody = buildRequestBody(systemPrompt, messages, userMessage);
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .header("content-type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return parseResponse(response.body());
                } else {
                    logger.log(Level.WARNING, "Anthropic API error: " + response.statusCode() + " - " + response.body());
                    return null;
                }
            } catch (IOException | InterruptedException e) {
                logger.log(Level.SEVERE, "Error calling Anthropic API", e);
                return null;
            }
        });
    }
    
    private JsonObject buildRequestBody(@NotNull String systemPrompt, 
                                      @NotNull List<ConversationContext.Message> history,
                                      @NotNull String userMessage) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", maxTokens);
        body.addProperty("temperature", temperature);
        body.addProperty("system", systemPrompt);
        
        // Build messages array
        List<JsonObject> messages = new ArrayList<>();
        
        // Add conversation history
        for (ConversationContext.Message msg : history) {
            JsonObject message = new JsonObject();
            message.addProperty("role", msg.getRole());
            message.addProperty("content", msg.getContent());
            messages.add(message);
        }
        
        // Add current user message
        JsonObject currentMessage = new JsonObject();
        currentMessage.addProperty("role", "user");
        currentMessage.addProperty("content", userMessage);
        messages.add(currentMessage);
        
        body.add("messages", gson.toJsonTree(messages));
        
        return body;
    }
    
    @Nullable
    private String parseResponse(@NotNull String responseBody) {
        try {
            JsonObject response = gson.fromJson(responseBody, JsonObject.class);
            
            if (response.has("content") && response.getAsJsonArray("content").size() > 0) {
                JsonObject content = response.getAsJsonArray("content").get(0).getAsJsonObject();
                if (content.has("text")) {
                    return content.get("text").getAsString();
                }
            }
            
            logger.warning("Unexpected response format from Anthropic API");
            return null;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error parsing Anthropic API response", e);
            return null;
        }
    }
    
    /**
     * Tests if the API key is valid by making a minimal request
     */
    public CompletableFuture<Boolean> testConnection() {
        return sendChatRequest("You are a helpful assistant.", new ArrayList<>(), "Say 'Hello'")
            .thenApply(response -> response != null && !response.isEmpty());
    }
}