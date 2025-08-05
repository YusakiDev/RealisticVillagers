package me.matsubara.realisticvillagers.files;

import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class TradingConfig {
    
    private final RealisticVillagers plugin;
    private FileConfiguration config;
    private File configFile;
    
    // Cached values
    private boolean enabled;
    private boolean checkExactItem;
    private double requiredStockMultiplier;
    private boolean showDisabledReason;
    private String outOfStockMessage;
    private boolean checkInputItems;
    private boolean cleanupUselessItems;
    private List<String> keepItems;
    private Set<String> keepCategories;
    private Set<Villager.Profession> exemptProfessions;
    
    public TradingConfig(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
        this.exemptProfessions = new HashSet<>();
        this.keepCategories = new HashSet<>();
        reload();
    }
    
    public void reload() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "trading-config.yml");
        }
        
        // Create file if it doesn't exist
        if (!configFile.exists()) {
            plugin.saveResource("trading-config.yml", false);
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Load defaults from jar
        InputStream defaultStream = plugin.getResource("trading-config.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream)
            );
            config.setDefaults(defaultConfig);
        }
        
        // Load cached values
        loadValues();
    }
    
    private void loadValues() {
        String basePath = "inventory-based-trading.";
        
        enabled = config.getBoolean(basePath + "enabled", true);
        checkExactItem = config.getBoolean(basePath + "check-exact-item", false);
        requiredStockMultiplier = config.getDouble(basePath + "required-stock-multiplier", 1.0);
        showDisabledReason = config.getBoolean(basePath + "show-disabled-reason", true);
        outOfStockMessage = config.getString(basePath + "out-of-stock-message", "&cOut of Stock");
        checkInputItems = config.getBoolean(basePath + "check-input-items", false);
        cleanupUselessItems = config.getBoolean(basePath + "cleanup-useless-items", true);
        
        // Load keep items
        keepItems = config.getStringList(basePath + "cleanup.keep-items");
        
        // Load keep categories
        keepCategories.clear();
        keepCategories.addAll(config.getStringList(basePath + "cleanup.keep-categories"));
        
        // Load exempt professions
        exemptProfessions.clear();
        List<String> professionList = config.getStringList(basePath + "exempt-professions");
        for (String profName : professionList) {
            try {
                Villager.Profession profession = Villager.Profession.valueOf(profName.toUpperCase());
                exemptProfessions.add(profession);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid profession in trading-config.yml: " + profName);
            }
        }
    }
    
    public void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save trading-config.yml", e);
        }
    }
    
    // Getters
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public boolean isCheckExactItem() {
        return checkExactItem;
    }
    
    public double getRequiredStockMultiplier() {
        return requiredStockMultiplier;
    }
    
    public boolean isShowDisabledReason() {
        return showDisabledReason;
    }
    
    @NotNull
    public String getOutOfStockMessage() {
        return outOfStockMessage;
    }
    
    public boolean isCheckInputItems() {
        return checkInputItems;
    }
    
    public boolean isProfessionExempt(@NotNull Villager.Profession profession) {
        return exemptProfessions.contains(profession);
    }
    
    @NotNull
    public Set<Villager.Profession> getExemptProfessions() {
        return new HashSet<>(exemptProfessions);
    }
    
    public boolean isCleanupUselessItems() {
        return cleanupUselessItems;
    }
    
    @NotNull
    public List<String> getKeepItems() {
        return keepItems != null ? new ArrayList<>(keepItems) : new ArrayList<>();
    }
    
    @NotNull
    public Set<String> getKeepCategories() {
        return new HashSet<>(keepCategories);
    }
}