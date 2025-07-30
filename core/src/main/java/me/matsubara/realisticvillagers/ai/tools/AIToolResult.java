package me.matsubara.realisticvillagers.ai.tools;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the result of an AI tool execution.
 * EXPERIMENTAL FEATURE - Subject to change
 */
@Getter
public class AIToolResult {
    
    private final boolean success;
    private final String message;
    private final Object data;
    
    private AIToolResult(boolean success, @Nullable String message, @Nullable Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }
    
    /**
     * Creates a successful tool result
     * @return success result
     */
    @NotNull
    public static AIToolResult success() {
        return new AIToolResult(true, null, null);
    }
    
    /**
     * Creates a successful tool result with a message
     * @param message success message
     * @return success result with message
     */
    @NotNull
    public static AIToolResult success(@NotNull String message) {
        return new AIToolResult(true, message, null);
    }
    
    /**
     * Creates a successful tool result with data
     * @param message success message
     * @param data result data
     * @return success result with data
     */
    @NotNull
    public static AIToolResult success(@NotNull String message, @Nullable Object data) {
        return new AIToolResult(true, message, data);
    }
    
    /**
     * Creates a failed tool result
     * @param message error message
     * @return failure result
     */
    @NotNull
    public static AIToolResult failure(@NotNull String message) {
        return new AIToolResult(false, message, null);
    }
    
    /**
     * Creates a failed tool result with data
     * @param message error message
     * @param data error data
     * @return failure result with data
     */
    @NotNull
    public static AIToolResult failure(@NotNull String message, @Nullable Object data) {
        return new AIToolResult(false, message, data);
    }
    
    @Override
    public String toString() {
        return String.format("AIToolResult{success=%s, message='%s', data=%s}", 
            success, message, data);
    }
}