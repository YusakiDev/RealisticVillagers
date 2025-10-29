package me.matsubara.realisticvillagers.manager.ai.tools;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * Result of an AI tool execution.
 * Contains success status, message, and optional data.
 */
public class AIToolResult {

    private final boolean success;
    private final String message;
    private final Map<String, Object> data;

    private AIToolResult(boolean success, @NotNull String message, @Nullable Map<String, Object> data) {
        this.success = success;
        this.message = message;
        this.data = data != null ? Map.copyOf(data) : Collections.emptyMap();
    }

    /**
     * Creates a successful result with a message.
     *
     * @param message the success message
     * @return success result
     */
    public static AIToolResult success(@NotNull String message) {
        return new AIToolResult(true, message, null);
    }

    /**
     * Creates a successful result with a message and additional data.
     *
     * @param message the success message
     * @param data    additional data to include
     * @return success result
     */
    public static AIToolResult success(@NotNull String message, @NotNull Map<String, Object> data) {
        return new AIToolResult(true, message, data);
    }

    /**
     * Creates a failure result with an error message.
     *
     * @param message the error message
     * @return failure result
     */
    public static AIToolResult failure(@NotNull String message) {
        return new AIToolResult(false, message, null);
    }

    /**
     * @return true if the tool executed successfully
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * @return the result message
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return additional data from the tool execution
     */
    public Map<String, Object> getData() {
        return data;
    }

    /**
     * Formats this result for sending to the AI.
     *
     * @return formatted string for AI consumption
     */
    public String formatForAI() {
        StringBuilder sb = new StringBuilder();
        sb.append(success ? "SUCCESS: " : "FAILED: ");
        sb.append(message);

        if (!data.isEmpty()) {
            sb.append("\nData: ");
            data.forEach((key, value) -> sb.append(key).append("=").append(value).append(", "));
            // Remove trailing comma and space
            if (sb.length() > 2) {
                sb.setLength(sb.length() - 2);
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return formatForAI();
    }
}
