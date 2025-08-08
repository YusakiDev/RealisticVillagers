package me.matsubara.realisticvillagers.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
 * Client for interacting with the OpenAI API.
 * EXPERIMENTAL FEATURE - Subject to change
 */
public class OpenAIAPIClient implements AIAPIClient {
    
    private static final String DEFAULT_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final String organizationId;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final Gson gson;
    private final Logger logger;
    
    public OpenAIAPIClient(@NotNull String apiKey, @NotNull String model, 
                          double temperature, int maxTokens, 
                          @Nullable String organizationId, @Nullable String baseUrl,
                          @NotNull Logger logger) {
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.organizationId = organizationId;
        this.baseUrl = (baseUrl == null || baseUrl.trim().isEmpty()) ? DEFAULT_API_URL : baseUrl.trim();
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
        this.gson = new Gson();
    }
    
    /**
     * Sends a chat completion request to the OpenAI API
     */
    @Override
    public CompletableFuture<String> sendChatRequest(@NotNull String systemPrompt, 
                                                    @NotNull List<ConversationContext.Message> messages,
                                                    @NotNull String userMessage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject requestBody = buildRequestBody(systemPrompt, messages, userMessage);
                
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)));
                
                // Add organization header if provided
                if (organizationId != null && !organizationId.trim().isEmpty()) {
                    requestBuilder.header("OpenAI-Organization", organizationId);
                }
                
                HttpRequest request = requestBuilder.build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    String responseBody = response.body();
                    return parseResponse(responseBody);
                } else {
                    logger.log(Level.WARNING, "OpenAI API error: " + response.statusCode() + " - " + response.body());
                    return null;
                }
            } catch (IOException | InterruptedException e) {
                logger.log(Level.SEVERE, "Error calling OpenAI API", e);
                return null;
            }
        });
    }
    
    private JsonObject buildRequestBody(@NotNull String systemPrompt, 
                                      @NotNull List<ConversationContext.Message> history,
                                      @NotNull String userMessage) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_completion_tokens", maxTokens);
        
        // Only include temperature if it's 1.0 (default) - some models don't support custom temperature
        if (temperature == 1.0) {
            body.addProperty("temperature", temperature);
        }
        
        // Enable JSON response format - required for structured output
        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");
        body.add("response_format", responseFormat);
        
        // Build messages array
        JsonArray messages = new JsonArray();
        
        // Add system message
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);
        messages.add(systemMessage);
        
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
        
        body.add("messages", messages);
        
        return body;
    }
    
    @Nullable
    private String parseResponse(@NotNull String responseBody) {
        try {
            JsonObject response = gson.fromJson(responseBody, JsonObject.class);
            
            if (response.has("choices") && response.getAsJsonArray("choices").size() > 0) {
                JsonObject choice = response.getAsJsonArray("choices").get(0).getAsJsonObject();
                if (choice.has("message")) {
                    JsonObject message = choice.getAsJsonObject("message");
                    if (message.has("content")) {
                        String content = message.get("content").getAsString();
                        
                        // Check if content is empty (often happens when hitting token limit)
                        if (content == null || content.trim().isEmpty()) {
                            // Check finish_reason to provide better error message
                            if (choice.has("finish_reason")) {
                                String finishReason = choice.get("finish_reason").getAsString();
                                if ("length".equals(finishReason)) {
                                    logger.warning("OpenAI response was cut off due to token limit. Consider increasing max-tokens in config.");
                                    // Return a fallback response
                                    return "{\"text\": \"...\", \"tools\": []}";
                                } else {
                                    logger.warning("OpenAI returned empty content with finish_reason: " + finishReason);
                                }
                            }
                            return "{\"text\": \"...\", \"tools\": []}";
                        }
                        
                        // OpenAI returns the JSON as the content when using json_object response format
                        return content;
                    }
                }
            }
            
            logger.warning("Unexpected response format from OpenAI API");
            return null;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error parsing OpenAI API response", e);
            return null;
        }
    }
    
    /**
     * Tests if the API key is valid by making a minimal request
     */
    @Override
    public CompletableFuture<Boolean> testConnection() {
        return sendChatRequest("You are a helpful assistant that outputs JSON.", new ArrayList<>(), 
            "Say 'Hello' in JSON format with a 'text' field")
            .thenApply(response -> response != null && !response.isEmpty());
    }
    
    /**
     * Gets the provider name
     */
    @Override
    public String getProviderName() {
        return "openai";
    }
}