package me.matsubara.realisticvillagers.files;

import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class GUIConfig {
    
    private final RealisticVillagers plugin;
    private FileConfiguration config;
    private File configFile;
    
    public enum ClickAction {
        TRADE,
        MAIN_GUI,
        TALK,
        NONE,
        DEFAULT
    }
    
    public GUIConfig(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    public void loadConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "gui-config.yml");
        }
        
        if (!configFile.exists()) {
            plugin.saveResource("gui-config.yml", false);
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Load defaults from jar
        try (InputStream defConfigStream = plugin.getResource("gui-config.yml")) {
            if (defConfigStream != null) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
                config.setDefaults(defConfig);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not load default gui-config.yml", e);
        }
    }
    
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save gui-config.yml", e);
        }
    }
    
    public void reloadConfig() {
        loadConfig();
    }
    
    // Main GUI settings
    public boolean isMainGUIEnabled() {
        return config.getBoolean("main-gui.enabled", true);
    }
    
    // Click action mappings
    @NotNull
    public ClickAction getRightClickAction() {
        String action = config.getString("click-actions.right-click.action", "TRADE");
        try {
            return ClickAction.valueOf(action);
        } catch (IllegalArgumentException e) {
            return ClickAction.TRADE;
        }
    }
    
    @NotNull
    public ClickAction getShiftRightClickAction() {
        String action = config.getString("click-actions.shift-right-click.action", "TALK");
        try {
            return ClickAction.valueOf(action);
        } catch (IllegalArgumentException e) {
            return ClickAction.TALK;
        }
    }
    
    @NotNull
    public ClickAction getLeftClickAction() {
        String action = config.getString("click-actions.left-click.action", "DEFAULT");
        try {
            return ClickAction.valueOf(action);
        } catch (IllegalArgumentException e) {
            return ClickAction.DEFAULT;
        }
    }
    
    @NotNull
    public ClickAction getShiftLeftClickAction() {
        String action = config.getString("click-actions.shift-left-click.action", "DEFAULT");
        try {
            return ClickAction.valueOf(action);
        } catch (IllegalArgumentException e) {
            return ClickAction.DEFAULT;
        }
    }
    
    // Talk mode settings
    public boolean isTalkModeEnabled() {
        return config.getBoolean("talk-mode.enabled", true);
    }
    
    public double getTalkModeMaxDistance() {
        return config.getDouble("talk-mode.max-distance", 10.0);
    }
    
    public boolean showTalkModeIndicators() {
        return config.getBoolean("talk-mode.show-indicators", true);
    }
    
    @Nullable
    public Particle getTalkModeEntryParticle() {
        String particle = config.getString("talk-mode.entry-particle");
        if (particle == null) return null;
        try {
            return Particle.valueOf(particle);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    @Nullable
    public Sound getTalkModeEntrySound() {
        String sound = config.getString("talk-mode.entry-sound");
        if (sound == null) return null;
        try {
            return Sound.valueOf(sound);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    @Nullable
    public Sound getTalkModeExitSound() {
        String sound = config.getString("talk-mode.exit-sound");
        if (sound == null) return null;
        try {
            return Sound.valueOf(sound);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    @NotNull
    public String getTalkModeChatPrefix() {
        return config.getString("talk-mode.chat-prefix", "&7[&aTalking to %villager%&7]&r ");
    }
    
    public boolean allowMultipleTalkers() {
        return config.getBoolean("talk-mode.allow-multiple-talkers", false);
    }
    
    public int getTalkModeIdleTimeout() {
        return config.getInt("talk-mode.idle-timeout", 300);
    }
    
    // Trade GUI settings
    public boolean overrideVanillaTrade() {
        return config.getBoolean("trade-gui.override-vanilla", false);
    }
    
    @NotNull
    public String getCustomTradeTitle() {
        return config.getString("trade-gui.custom-title", "&2%villager%'s Shop");
    }
    
    // Animation settings
    public boolean areAnimationsEnabled() {
        return config.getBoolean("animations.enabled", true);
    }
    
    public int getAnimationSpeed() {
        return config.getInt("animations.speed", 200);
    }
    
    @Nullable
    public Sound getGUIOpenSound() {
        String sound = config.getString("animations.sounds.gui-open");
        if (sound == null) return null;
        try {
            return Sound.valueOf(sound);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    @Nullable
    public Sound getGUICloseSound() {
        String sound = config.getString("animations.sounds.gui-close");
        if (sound == null) return null;
        try {
            return Sound.valueOf(sound);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    @Nullable
    public Sound getButtonClickSound() {
        String sound = config.getString("animations.sounds.button-click");
        if (sound == null) return null;
        try {
            return Sound.valueOf(sound);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    // Permission settings
    @NotNull
    public String getForceGUIPermission() {
        return config.getString("permissions.force-gui", "realisticvillagers.gui.force");
    }
    
    @NotNull
    public String getTalkModePermission() {
        return config.getString("permissions.talk-mode", "realisticvillagers.talk");
    }
    
    @NotNull
    public String getTradePermission() {
        return config.getString("permissions.trade", "realisticvillagers.trade");
    }
    
    // Debug settings
    public boolean isDebugEnabled() {
        return config.getBoolean("debug.enabled", false);
    }
    
    public boolean logClicks() {
        return config.getBoolean("debug.log-clicks", false);
    }
}