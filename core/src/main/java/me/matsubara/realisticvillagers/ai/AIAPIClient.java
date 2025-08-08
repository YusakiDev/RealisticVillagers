package me.matsubara.realisticvillagers.ai;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract interface for AI API clients.
 * EXPERIMENTAL FEATURE - Subject to change
 */
public interface AIAPIClient {
    
    /**
     * Sends a chat completion request to the AI API
     * 
     * @param systemPrompt The system prompt to set context
     * @param messages The conversation history
     * @param userMessage The current user message
     * @return CompletableFuture containing the AI response text, or null if failed
     */
    CompletableFuture<String> sendChatRequest(@NotNull String systemPrompt, 
                                             @NotNull List<ConversationContext.Message> messages,
                                             @NotNull String userMessage);
    
    /**
     * Tests if the API connection is working
     * 
     * @return CompletableFuture containing true if connection is successful
     */
    CompletableFuture<Boolean> testConnection();
    
    /**
     * Gets the provider name (e.g., "anthropic", "openai")
     * 
     * @return The provider name
     */
    String getProviderName();
}