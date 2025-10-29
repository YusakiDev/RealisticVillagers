package me.matsubara.realisticvillagers.manager.ai.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Parses AI responses that may contain tool calls in JSON format.
 * Handles both structured JSON responses and fallback to plain text.
 */
public class AIResponseParser {

    /**
     * Parses an AI response that may contain JSON with tool calls.
     * Expected format: {"text": "response", "tools": [{"name": "tool_name", "args": {...}}]}
     *
     * @param responseBody the raw response from the AI
     * @return parsed response with text and tool calls
     */
    public static ParsedResponse parseResponse(@NotNull String responseBody) {
        try {
            // Try to parse as JSON
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();

            // Extract the assistant's message
            String text = extractText(root);
            List<ToolCall> toolCalls = extractToolCalls(root);

            return new ParsedResponse(text, toolCalls);

        } catch (Exception e) {
            // If JSON parsing fails, treat entire response as plain text
            return new ParsedResponse(responseBody, Collections.emptyList());
        }
    }

    /**
     * Extracts text from various JSON response formats.
     */
    private static String extractText(JsonObject root) {
        // Direct text field
        if (root.has("text") && root.get("text").isJsonPrimitive()) {
            return root.get("text").getAsString();
        }

        // Groq format: choices[0].message.content
        if (root.has("choices") && root.get("choices").isJsonArray()) {
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices.size() > 0) {
                JsonObject firstChoice = choices.get(0).getAsJsonObject();
                if (firstChoice.has("message")) {
                    JsonObject message = firstChoice.getAsJsonObject("message");
                    if (message.has("content")) {
                        String content = message.get("content").getAsString();

                        // Try to parse content as JSON if it looks like JSON
                        if (content.trim().startsWith("{")) {
                            try {
                                JsonObject contentObj = JsonParser.parseString(content).getAsJsonObject();
                                if (contentObj.has("text")) {
                                    return contentObj.get("text").getAsString();
                                }
                            } catch (Exception ignored) {
                                // If content is not valid JSON, return as-is
                            }
                        }

                        return content;
                    }
                }
            }
        }

        // Fallback: convert entire object to string
        return root.toString();
    }

    /**
     * Extracts tool calls from various JSON response formats.
     */
    private static List<ToolCall> extractToolCalls(JsonObject root) {
        List<ToolCall> toolCalls = new ArrayList<>();

        // Direct tools array (legacy custom format)
        if (root.has("tools") && root.get("tools").isJsonArray()) {
            JsonArray tools = root.getAsJsonArray("tools");
            for (JsonElement toolElement : tools) {
                if (toolElement.isJsonObject()) {
                    ToolCall toolCall = parseToolCall(toolElement.getAsJsonObject());
                    if (toolCall != null) {
                        toolCalls.add(toolCall);
                    }
                }
            }
            return toolCalls;
        }

        // Check for native tool calling format (OpenAI/Groq)
        if (root.has("choices") && root.get("choices").isJsonArray()) {
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices.size() > 0) {
                JsonObject firstChoice = choices.get(0).getAsJsonObject();
                if (firstChoice.has("message")) {
                    JsonObject message = firstChoice.getAsJsonObject("message");

                    // NATIVE FORMAT: Check for tool_calls array
                    if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
                        JsonArray nativeToolCalls = message.getAsJsonArray("tool_calls");
                        for (JsonElement toolCallElement : nativeToolCalls) {
                            if (toolCallElement.isJsonObject()) {
                                ToolCall toolCall = parseNativeToolCall(toolCallElement.getAsJsonObject());
                                if (toolCall != null) {
                                    toolCalls.add(toolCall);
                                }
                            }
                        }
                        return toolCalls;
                    }

                    // Fallback: check if content is JSON with tools (legacy custom format)
                    if (message.has("content")) {
                        String content = message.get("content").getAsString();

                        // Try to parse content as JSON
                        if (content != null && content.trim().startsWith("{")) {
                            try {
                                JsonObject contentObj = JsonParser.parseString(content).getAsJsonObject();
                                if (contentObj.has("tools") && contentObj.get("tools").isJsonArray()) {
                                    JsonArray tools = contentObj.getAsJsonArray("tools");
                                    for (JsonElement toolElement : tools) {
                                        if (toolElement.isJsonObject()) {
                                            ToolCall toolCall = parseToolCall(toolElement.getAsJsonObject());
                                            if (toolCall != null) {
                                                toolCalls.add(toolCall);
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {
                                // Content is not valid JSON
                            }
                        }
                    }
                }
            }
        }

        return toolCalls;
    }

    /**
     * Parses a single tool call from JSON (custom format).
     */
    private static ToolCall parseToolCall(JsonObject toolObj) {
        if (!toolObj.has("name")) {
            return null;
        }

        String name = toolObj.get("name").getAsString();
        Map<String, Object> args = new HashMap<>();

        if (toolObj.has("args") && toolObj.get("args").isJsonObject()) {
            JsonObject argsObj = toolObj.getAsJsonObject("args");
            for (String key : argsObj.keySet()) {
                JsonElement value = argsObj.get(key);
                args.put(key, jsonElementToObject(value));
            }
        }

        return new ToolCall(name, args);
    }

    /**
     * Parses a native tool call from OpenAI/Groq format.
     * Format: {"id": "call_abc", "type": "function", "function": {"name": "...", "arguments": "{...}"}}
     */
    private static ToolCall parseNativeToolCall(JsonObject toolCallObj) {
        if (!toolCallObj.has("function")) {
            return null;
        }

        JsonObject function = toolCallObj.getAsJsonObject("function");
        if (!function.has("name")) {
            return null;
        }

        String name = function.get("name").getAsString();
        Map<String, Object> args = new HashMap<>();

        // Parse arguments (they come as a JSON string)
        if (function.has("arguments")) {
            String argumentsStr = function.get("arguments").getAsString();
            if (argumentsStr != null && !argumentsStr.trim().isEmpty()) {
                try {
                    JsonObject argsObj = JsonParser.parseString(argumentsStr).getAsJsonObject();
                    for (String key : argsObj.keySet()) {
                        JsonElement value = argsObj.get(key);
                        args.put(key, jsonElementToObject(value));
                    }
                } catch (Exception e) {
                    // If arguments parsing fails, continue with empty args
                }
            }
        }

        return new ToolCall(name, args);
    }

    /**
     * Converts a JsonElement to a Java object.
     */
    private static Object jsonElementToObject(JsonElement element) {
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isBoolean()) {
                return element.getAsBoolean();
            } else if (element.getAsJsonPrimitive().isNumber()) {
                // Try to return as integer if possible, otherwise double
                double value = element.getAsDouble();
                if (value == Math.floor(value)) {
                    return (int) value;
                }
                return value;
            } else {
                return element.getAsString();
            }
        } else if (element.isJsonNull()) {
            return null;
        } else if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) {
                list.add(jsonElementToObject(item));
            }
            return list;
        } else if (element.isJsonObject()) {
            Map<String, Object> map = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                map.put(entry.getKey(), jsonElementToObject(entry.getValue()));
            }
            return map;
        }
        return element.toString();
    }

    /**
     * Represents a parsed AI response.
     */
    public static class ParsedResponse {
        private final String text;
        private final List<ToolCall> toolCalls;

        public ParsedResponse(@NotNull String text, @NotNull List<ToolCall> toolCalls) {
            this.text = text;
            this.toolCalls = Collections.unmodifiableList(toolCalls);
        }

        /**
         * @return the text content of the response
         */
        @NotNull
        public String getText() {
            return text;
        }

        /**
         * @return the list of tool calls
         */
        @NotNull
        public List<ToolCall> getToolCalls() {
            return toolCalls;
        }

        /**
         * @return true if this response contains tool calls
         */
        public boolean hasToolCalls() {
            return !toolCalls.isEmpty();
        }
    }

    /**
     * Represents a single tool call.
     */
    public static class ToolCall {
        private final String name;
        private final Map<String, Object> arguments;

        public ToolCall(@NotNull String name, @NotNull Map<String, Object> arguments) {
            this.name = name;
            this.arguments = Collections.unmodifiableMap(arguments);
        }

        /**
         * @return the name of the tool to call
         */
        @NotNull
        public String getName() {
            return name;
        }

        /**
         * @return the arguments for the tool
         */
        @NotNull
        public Map<String, Object> getArguments() {
            return arguments;
        }

        @Override
        public String toString() {
            return name + "(" + arguments + ")";
        }
    }
}
