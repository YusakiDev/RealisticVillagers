package me.matsubara.realisticvillagers.trading;

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

/**
 * Handles loading and exposing configuration options for the realistic trading system.
 */
public final class TradingConfig {

    private static final String ROOT_PATH = "inventory-based-trading.";

    private final RealisticVillagers plugin;
    private FileConfiguration config;
    private File configFile;

    private boolean enabled;
    private boolean checkExactItem;
    private double requiredStockMultiplier;
    private boolean showDisabledReason;
    private String outOfStockMessage;
    private boolean checkInputItems;
    private List<String> keepItems = List.of();
    private Set<String> keepCategories = Set.of();
    private Set<Villager.Profession> exemptProfessions = Set.of();

    public TradingConfig(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "trading-config.yml");
        }

        if (!configFile.exists()) {
            plugin.saveResource("trading-config.yml");
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        InputStream defaultStream = plugin.getResource("trading-config.yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
            config.setDefaults(defaults);
        }

        loadValues();
    }

    public void save() {
        if (config == null || configFile == null) return;

        try {
            config.save(configFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save trading-config.yml", exception);
        }
    }

    private void loadValues() {
        enabled = config.getBoolean(ROOT_PATH + "enabled", true);
        checkExactItem = config.getBoolean(ROOT_PATH + "trade-validation.check-exact-item", false);
        requiredStockMultiplier = Math.max(0.0d, config.getDouble(ROOT_PATH + "trade-validation.required-stock-multiplier", 1.0d));
        showDisabledReason = config.getBoolean(ROOT_PATH + "display.show-disabled-reason", true);
        outOfStockMessage = config.getString(ROOT_PATH + "display.out-of-stock-message", "&cOut of Stock");
        checkInputItems = config.getBoolean(ROOT_PATH + "trade-validation.check-input-items", false);

        // Cleanup is controlled by list content: enabled if either list has items, disabled if both empty
        keepItems = new ArrayList<>(config.getStringList(ROOT_PATH + "inventory-cleanup.keep-items"));
        keepCategories = new HashSet<>(config.getStringList(ROOT_PATH + "inventory-cleanup.keep-categories"));

        List<String> exempt = config.getStringList(ROOT_PATH + "exempt-professions");
        Set<Villager.Profession> professions = new HashSet<>();
        for (String entry : exempt) {
            try {
                professions.add(Villager.Profession.valueOf(entry.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Unknown profession in trading-config.yml: " + entry);
            }
        }
        exemptProfessions = professions;
    }

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

    @NotNull
    public List<String> getKeepItems() {
        return new ArrayList<>(keepItems);
    }

    @NotNull
    public Set<String> getKeepCategories() {
        return new HashSet<>(keepCategories);
    }

    public boolean isProfessionExempt(@NotNull Villager.Profession profession) {
        return exemptProfessions.contains(profession);
    }
}
