package me.matsubara.realisticvillagers.manager.ai.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for all available AI tools.
 * Thread-safe and config-driven.
 */
public class AIToolRegistry {

    private final RealisticVillagers plugin;
    private final Map<String, AITool> tools;
    private final Map<String, ToolConfig> toolConfigs;

    public AIToolRegistry(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
        this.tools = new ConcurrentHashMap<>();
        this.toolConfigs = new ConcurrentHashMap<>();
    }

    /**
     * Registers a tool in the registry.
     *
     * @param tool the tool to register
     */
    public void registerTool(@NotNull AITool tool) {
        tools.put(tool.getName(), tool);
        plugin.getLogger().info("Registered AI tool: " + tool.getName());
    }

    /**
     * Loads tool configurations from the config file.
     *
     * @param config the config section containing tool settings
     */
    public void loadToolConfigs(@NotNull ConfigurationSection config) {
        toolConfigs.clear();

        ConfigurationSection toolsSection = config.getConfigurationSection("tools.available-tools");
        if (toolsSection == null) {
            plugin.getLogger().warning("No tools configuration found in ai-config.yml");
            return;
        }

        for (String toolName : toolsSection.getKeys(false)) {
            ConfigurationSection toolSection = toolsSection.getConfigurationSection(toolName);
            if (toolSection == null) continue;

            boolean enabled = toolSection.getBoolean("enabled", true);
            int minReputation = toolSection.getInt("min-reputation", -200);
            int cooldownSeconds = toolSection.getInt("cooldown-seconds", 0);

            toolConfigs.put(toolName, new ToolConfig(enabled, minReputation, cooldownSeconds));
            plugin.getLogger().info("Loaded config for tool '" + toolName + "': enabled=" + enabled +
                    ", minRep=" + minReputation + ", cooldown=" + cooldownSeconds + "s");
        }
    }

    /**
     * Gets a tool by name.
     *
     * @param name the tool name
     * @return the tool, or null if not found
     */
    @Nullable
    public AITool getTool(@NotNull String name) {
        return tools.get(name);
    }

    /**
     * Gets all registered tools.
     *
     * @return unmodifiable collection of tools
     */
    public Collection<AITool> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    /**
     * Checks if a tool is enabled in the config.
     *
     * @param toolName the tool name
     * @return true if enabled, false otherwise (defaults to false if not configured)
     */
    public boolean isToolEnabled(@NotNull String toolName) {
        ToolConfig config = toolConfigs.get(toolName);
        return config != null && config.enabled;
    }

    /**
     * Gets the minimum reputation required for a tool.
     *
     * @param toolName the tool name
     * @return minimum reputation (defaults to -200 if not configured)
     */
    public int getMinReputation(@NotNull String toolName) {
        ToolConfig config = toolConfigs.get(toolName);
        return config != null ? config.minReputation : -200;
    }

    /**
     * Gets the cooldown for a tool in seconds.
     *
     * @param toolName the tool name
     * @return cooldown in seconds (defaults to 0 if not configured)
     */
    public int getCooldownSeconds(@NotNull String toolName) {
        ToolConfig config = toolConfigs.get(toolName);
        return config != null ? config.cooldownSeconds : 0;
    }

    /**
     * Generates tool instructions for inclusion in the system prompt.
     *
     * @return formatted tool instructions
     */
    public String generateToolInstructions() {
        StringBuilder sb = new StringBuilder();
        sb.append("Available tools:\n");

        // Group tools by category
        Map<ToolCategory, List<AITool>> byCategory = new EnumMap<>(ToolCategory.class);
        for (AITool tool : tools.values()) {
            if (isToolEnabled(tool.getName())) {
                byCategory.computeIfAbsent(tool.getCategory(), k -> new ArrayList<>()).add(tool);
            }
        }

        // Format by category
        byCategory.forEach((category, categoryTools) -> {
            sb.append("\n").append(category.getDisplayName()).append(":\n");
            for (AITool tool : categoryTools) {
                sb.append("- ").append(tool.getName()).append("(");

                Map<String, String> params = tool.getParameters();
                if (!params.isEmpty()) {
                    StringJoiner joiner = new StringJoiner(", ");
                    params.keySet().forEach(joiner::add);
                    sb.append(joiner);
                }

                sb.append("): ").append(tool.getDescription()).append("\n");
            }
        });

        return sb.toString();
    }

    /**
     * Builds native tool calling array for OpenAI/Groq API format.
     * This generates the tools array in the format expected by native function calling.
     *
     * @return JsonArray of tool definitions
     */
    public @NotNull JsonArray buildNativeToolsArray() {
        JsonArray toolsArray = new JsonArray();

        for (AITool tool : tools.values()) {
            if (!isToolEnabled(tool.getName())) {
                continue;
            }

            JsonObject toolDef = new JsonObject();
            toolDef.addProperty("type", "function");

            JsonObject function = new JsonObject();
            function.addProperty("name", tool.getName());
            function.addProperty("description", tool.getDescription());

            // Build parameters schema
            JsonObject parameters = new JsonObject();
            parameters.addProperty("type", "object");

            Map<String, String> toolParams = tool.getParameters();
            if (!toolParams.isEmpty()) {
                JsonObject properties = new JsonObject();
                JsonArray required = new JsonArray();

                for (Map.Entry<String, String> param : toolParams.entrySet()) {
                    JsonObject paramDef = new JsonObject();
                    paramDef.addProperty("type", "string"); // Default to string, tools will handle conversion
                    paramDef.addProperty("description", param.getValue());
                    properties.add(param.getKey(), paramDef);

                    // Mark all parameters as required for now
                    required.add(param.getKey());
                }

                parameters.add("properties", properties);
                if (required.size() > 0) {
                    parameters.add("required", required);
                }
            } else {
                // No parameters - empty properties object
                parameters.add("properties", new JsonObject());
            }

            function.add("parameters", parameters);
            toolDef.add("function", function);
            toolsArray.add(toolDef);
        }

        return toolsArray;
    }

    /**
     * Internal configuration for a tool.
     */
    private static class ToolConfig {
        final boolean enabled;
        final int minReputation;
        final int cooldownSeconds;

        ToolConfig(boolean enabled, int minReputation, int cooldownSeconds) {
            this.enabled = enabled;
            this.minReputation = minReputation;
            this.cooldownSeconds = cooldownSeconds;
        }
    }
}
