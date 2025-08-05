package me.matsubara.realisticvillagers.ai.tools;

import com.google.gson.*;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Parses AI responses that may contain both text and tool calls.
 * Supports both plain text responses and JSON responses with tool calls.
 * EXPERIMENTAL FEATURE - Subject to change
 */
public class AIResponseParser {
    
    private static final Gson gson = new GsonBuilder().create();
    
    /**
     * Parses an AI response into text and tool calls
     * @param response the raw AI response
     * @return parsed response containing text and tool calls
     */
    @NotNull
    public static ParsedResponse parseResponse(@NotNull String response) {
        String trimmed = response.trim();
        
        // Always expect JSON format now
        try {
            JsonObject jsonResponse = gson.fromJson(trimmed, JsonObject.class);
            return parseJsonResponse(jsonResponse);
        } catch (JsonSyntaxException e) {
            // JSON parsing failed - this means AI didn't follow the format
            System.err.println("AI Response Parser: Failed to parse JSON response: " + e.getMessage());
            System.err.println("Raw response: " + trimmed);
            
            // Try to extract text from malformed JSON as fallback
            String extractedText = extractTextFromMalformedJson(trimmed);
            if (!extractedText.isEmpty()) {
                System.err.println("AI Response Parser: Extracted text from malformed JSON: " + extractedText);
                return new ParsedResponse(extractedText, Collections.emptyList());
            }
            
            // Ultimate fallback - treat entire response as text
            System.err.println("AI Response Parser: Using entire response as fallback text");
            return new ParsedResponse(trimmed, Collections.emptyList());
        }
    }
    
    @NotNull
    private static ParsedResponse parseJsonResponse(@NotNull JsonObject jsonResponse) {
        // Extract text
        String text = "";
        if (jsonResponse.has("text")) {
            JsonElement textElement = jsonResponse.get("text");
            if (textElement.isJsonPrimitive()) {
                text = cleanText(textElement.getAsString());
            }
        }
        
        // Extract tool calls
        List<ToolCall> toolCalls = new ArrayList<>();
        if (jsonResponse.has("tools")) {
            JsonElement toolsElement = jsonResponse.get("tools");
            if (toolsElement.isJsonArray()) {
                toolCalls = parseToolCalls(toolsElement.getAsJsonArray());
            }
        }
        
        return new ParsedResponse(text, toolCalls);
    }
    
    @NotNull
    private static List<ToolCall> parseToolCalls(@NotNull JsonArray toolsArray) {
        List<ToolCall> toolCalls = new ArrayList<>();
        
        for (JsonElement toolElement : toolsArray) {
            if (!toolElement.isJsonObject()) continue;
            
            JsonObject toolObject = toolElement.getAsJsonObject();
            
            // Extract tool name
            if (!toolObject.has("name")) continue;
            String toolName = toolObject.get("name").getAsString();
            
            // Extract arguments
            Map<String, Object> args = new HashMap<>();
            if (toolObject.has("args") && toolObject.get("args").isJsonObject()) {
                JsonObject argsObject = toolObject.get("args").getAsJsonObject();
                args = parseArguments(argsObject);
            }
            
            toolCalls.add(new ToolCall(toolName, args));
        }
        
        return toolCalls;
    }
    
    @NotNull
    private static Map<String, Object> parseArguments(@NotNull JsonObject argsObject) {
        Map<String, Object> args = new HashMap<>();
        
        for (Map.Entry<String, JsonElement> entry : argsObject.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            
            // Convert JsonElement to appropriate Java type
            args.put(key, convertJsonElement(value));
        }
        
        return args;
    }
    
    @Nullable
    private static Object convertJsonElement(@NotNull JsonElement element) {
        if (element.isJsonNull()) {
            return null;
        } else if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            } else if (primitive.isNumber()) {
                // Try to determine if it's an integer or double
                Number number = primitive.getAsNumber();
                if (number.doubleValue() == number.intValue()) {
                    return number.intValue();
                } else {
                    return number.doubleValue();
                }
            } else {
                return primitive.getAsString();
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            List<Object> list = new ArrayList<>();
            for (JsonElement arrayElement : array) {
                list.add(convertJsonElement(arrayElement));
            }
            return list;
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            Map<String, Object> map = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                map.put(entry.getKey(), convertJsonElement(entry.getValue()));
            }
            return map;
        }
        
        return null;
    }
    
    /**
     * Cleans and formats text from AI responses
     * Handles line breaks and ensures proper spacing
     */
    @NotNull
    private static String cleanText(@NotNull String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        
        // Replace newlines with spaces and clean up multiple spaces
        return text.trim()
                  .replaceAll("\\r?\\n", " ")  // Replace line breaks with spaces
                  .replaceAll("\\s+", " ")     // Replace multiple spaces with single space
                  .trim();
    }
    
    /**
     * Attempts to extract text from malformed JSON using regex
     * This is a fallback when proper JSON parsing fails
     */
    @NotNull
    private static String extractTextFromMalformedJson(@NotNull String malformedJson) {
        try {
            // Simple regex to extract text field value
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"text\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher matcher = pattern.matcher(malformedJson);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            // Ignore regex errors
        }
        return "";
    }
    
    /**
     * Represents a parsed AI response
     */
    @Getter
    public static class ParsedResponse {
        private final String text;
        private final List<ToolCall> toolCalls;
        
        public ParsedResponse(@NotNull String text, @NotNull List<ToolCall> toolCalls) {
            this.text = text;
            this.toolCalls = Collections.unmodifiableList(toolCalls);
        }
        
        /**
         * Checks if this response contains any tool calls
         * @return true if there are tool calls
         */
        public boolean hasToolCalls() {
            return !toolCalls.isEmpty();
        }
        
        /**
         * Checks if this response has text content
         * @return true if there is text content
         */
        public boolean hasText() {
            return !text.isEmpty();
        }
        
        @Override
        public String toString() {
            return String.format("ParsedResponse{text='%s', toolCalls=%s}", text, toolCalls);
        }
    }
    
    /**
     * Represents a single tool call
     */
    @Getter
    public static class ToolCall {
        private final String name;
        private final Map<String, Object> arguments;
        
        public ToolCall(@NotNull String name, @NotNull Map<String, Object> arguments) {
            this.name = name;
            this.arguments = Collections.unmodifiableMap(arguments);
        }
        
        /**
         * Gets an argument as a string
         * @param key the argument key
         * @return the argument value as string, or null if not found
         */
        @Nullable
        public String getStringArgument(@NotNull String key) {
            Object value = arguments.get(key);
            return value != null ? value.toString() : null;
        }
        
        /**
         * Gets an argument as an integer
         * @param key the argument key
         * @param defaultValue the default value if not found or not an integer
         * @return the argument value as integer
         */
        public int getIntArgument(@NotNull String key, int defaultValue) {
            Object value = arguments.get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException | NullPointerException e) {
                return defaultValue;
            }
        }
        
        /**
         * Gets an argument as a boolean
         * @param key the argument key
         * @param defaultValue the default value if not found or not a boolean
         * @return the argument value as boolean
         */
        public boolean getBooleanArgument(@NotNull String key, boolean defaultValue) {
            Object value = arguments.get(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            if (value != null) {
                return Boolean.parseBoolean(value.toString());
            }
            return defaultValue;
        }
        
        @Override
        public String toString() {
            return String.format("ToolCall{name='%s', arguments=%s}", name, arguments);
        }
    }
}