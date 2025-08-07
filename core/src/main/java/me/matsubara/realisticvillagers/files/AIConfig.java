package me.matsubara.realisticvillagers.files;

import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration handler for AI chat features.
 * EXPERIMENTAL FEATURE - Subject to change
 */
public class AIConfig {
    
    private final RealisticVillagers plugin;
    private final File configFile;
    private FileConfiguration config;
    
    // Cached values for performance
    private boolean enabled;
    private String apiKey;
    private String model;
    private double temperature;
    private int maxTokens;
    private long rateLimitSeconds;
    private String languageMode;
    private boolean minecraftKnowledge;
    private boolean toolsEnabled;
    private int maxToolsPerResponse;
    private long toolRateLimitSeconds;
    
    // Natural chat settings
    private boolean naturalChatEnabled;
    private int naturalChatTriggerRange;
    private int naturalChatHearingRange;
    private int conversationMemoryMinutes;
    
    // Cached prompts
    private String worldContextPrompt;
    private String minecraftKnowledgePrompt;
    private String behaviorRulesPrompt;
    private String languageOverridePrompt;
    private String toolInstructionsPrompt;
    
    // Reputation settings
    private boolean reputationEnabled;
    private Map<String, String> reputationTonePrompts;
    
    // Cached personalities
    private final Map<Villager.Profession, ProfessionPersonality> personalities = new HashMap<>();
    
    public AIConfig(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "ai-config.yml");
        
        // Create config file if it doesn't exist
        if (!configFile.exists()) {
            plugin.saveResource("ai-config.yml", false);
        }
        
        reload();
    }
    
    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Load API settings
        enabled = config.getBoolean("api.enabled", false);
        apiKey = config.getString("api.api-key", "");
        model = config.getString("api.model", "claude-3-5-haiku-latest");
        temperature = config.getDouble("api.temperature", 0.7);
        maxTokens = config.getInt("api.max-tokens", 150);
        rateLimitSeconds = config.getLong("api.rate-limit-seconds", 3);
        
        // Load language settings
        languageMode = config.getString("language.mode", "auto");
        
        // Load knowledge settings
        minecraftKnowledge = config.getBoolean("knowledge.minecraft-enabled", true);
        
        // Load natural chat settings
        naturalChatEnabled = config.getBoolean("natural-chat.enabled", true);
        naturalChatTriggerRange = config.getInt("natural-chat.trigger-range", 8);
        naturalChatHearingRange = config.getInt("natural-chat.hearing-range", 10);
        conversationMemoryMinutes = config.getInt("natural-chat.conversation-memory-minutes", 30);
        
        // Load reputation settings
        reputationEnabled = config.getBoolean("reputation.enabled", true);
        reputationTonePrompts = new HashMap<>();
        ConfigurationSection toneSection = config.getConfigurationSection("reputation.tone-prompts");
        if (toneSection != null) {
            for (String key : toneSection.getKeys(false)) {
                reputationTonePrompts.put(key, toneSection.getString(key, ""));
            }
        }
        
        // Load tool settings
        toolsEnabled = config.getBoolean("tools.enabled", false);
        maxToolsPerResponse = config.getInt("tools.max-tools-per-response", 3);
        toolRateLimitSeconds = config.getLong("tools.rate-limit-seconds", 2);
        
        // Load prompts
        worldContextPrompt = config.getString("prompts.world-context", "");
        minecraftKnowledgePrompt = config.getString("prompts.minecraft-knowledge", "");
        behaviorRulesPrompt = config.getString("prompts.behavior-rules", "");
        languageOverridePrompt = config.getString("prompts.language-override", "");
        toolInstructionsPrompt = config.getString("prompts.tool-instructions", "");
        
        // Load personalities
        loadPersonalities();
    }
    
    private void loadPersonalities() {
        personalities.clear();
        ConfigurationSection personalitiesSection = config.getConfigurationSection("personalities");
        if (personalitiesSection == null) return;
        
        for (String professionName : personalitiesSection.getKeys(false)) {
            try {
                Villager.Profession profession = Villager.Profession.valueOf(professionName.toUpperCase());
                ConfigurationSection profSection = personalitiesSection.getConfigurationSection(professionName);
                if (profSection != null) {
                    ProfessionPersonality personality = new ProfessionPersonality(
                        profession,
                        profSection.getString("personality", ""),
                        profSection.getString("speaking-style", ""),
                        profSection.getString("knowledge", "")
                    );
                    personalities.put(profession, personality);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown profession in ai-config.yml: " + professionName);
            }
        }
    }
    
    // Getters for API settings
    public boolean isEnabled() { return enabled; }
    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public double getTemperature() { return temperature; }
    public int getMaxTokens() { return maxTokens; }
    public long getRateLimitSeconds() { return rateLimitSeconds; }
    
    // Getters for language settings
    public String getLanguageMode() { return languageMode; }
    
    // Getters for knowledge settings
    public boolean isMinecraftKnowledgeEnabled() { return minecraftKnowledge; }
    
    // Getters for natural chat settings
    public boolean isNaturalChatEnabled() { return naturalChatEnabled; }
    public int getNaturalChatTriggerRange() { return naturalChatTriggerRange; }
    public int getNaturalChatHearingRange() { return naturalChatHearingRange; }
    public int getConversationMemoryMinutes() { return conversationMemoryMinutes; }
    
    // Getters for tool settings
    public boolean isToolsEnabled() { return toolsEnabled; }
    public int getMaxToolsPerResponse() { return maxToolsPerResponse; }
    public long getToolRateLimitSeconds() { return toolRateLimitSeconds; }
    
    // Getters for prompts (with version replacement)
    public String getWorldContextPrompt(String version) {
        return worldContextPrompt.replace("{version}", version);
    }
    
    public String getMinecraftKnowledgePrompt(String version) {
        return minecraftKnowledgePrompt.replace("{version}", version);
    }
    
    public String getBehaviorRulesPrompt() {
        return behaviorRulesPrompt;
    }
    
    public String getLanguageOverridePrompt(String language) {
        return languageOverridePrompt.replace("{language}", language.toUpperCase());
    }
    
    public String getToolInstructionsPrompt() {
        return toolInstructionsPrompt;
    }
    
    // Get personality for profession
    @Nullable
    public ProfessionPersonality getPersonality(@NotNull Villager.Profession profession) {
        return personalities.get(profession);
    }
    
    // Reputation methods
    public boolean isReputationEnabled() {
        return reputationEnabled;
    }
    
    @Nullable
    public String getReputationTonePrompt(String level) {
        return reputationTonePrompts.get(level);
    }
    
    /**
     * Get the minimum reputation required for a specific tool
     * @param toolName the name of the tool
     * @return the minimum reputation required, or -200 if not configured
     */
    public int getToolMinReputation(@NotNull String toolName) {
        ConfigurationSection toolsSection = config.getConfigurationSection("tools.available-tools");
        if (toolsSection == null) return -200; // Default minimum reputation
        
        ConfigurationSection toolSection = toolsSection.getConfigurationSection(toolName);
        if (toolSection == null) return -200; // No specific config for this tool
        
        return toolSection.getInt("min-reputation", -200);
    }
    
    /**
     * Represents a profession's personality configuration
     */
    public static class ProfessionPersonality {
        private final Villager.Profession profession;
        private final String personality;
        private final String speakingStyle;
        private final String knowledge;
        
        public ProfessionPersonality(Villager.Profession profession, String personality, 
                                   String speakingStyle, String knowledge) {
            this.profession = profession;
            this.personality = personality;
            this.speakingStyle = speakingStyle;
            this.knowledge = knowledge;
        }
        
        public Villager.Profession getProfession() { return profession; }
        public String getPersonality() { return personality; }
        public String getSpeakingStyle() { return speakingStyle; }
        public String getKnowledge() { return knowledge; }
        
        public String getSystemPrompt() {
            return String.format(
                "You are a minecraft villager with the %s profession. " +
                "Personality: %s. " +
                "Speaking style: %s. " +
                "Respond as this villager in VERY SHORT phrases (3-8 words max). " +
                "Think casual conversation, not explanations. " +
                "Examples: 'Hey there!', 'Busy day today', 'What's up?', 'Nice to see you'",
                profession.name().toLowerCase(),
                personality,
                speakingStyle
            );
        }
    }
}