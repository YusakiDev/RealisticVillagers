package me.matsubara.realisticvillagers.reputation;

import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class LamCoreReputationProvider implements ReputationProvider {
    
    private final RealisticVillagers plugin;
    private Plugin lamCore;
    private Object vouchModule;
    private Method calculateVouchScoreMethod;
    
    private boolean enabled = false;
    private String mode = "additive"; // "additive" or "replace"
    private double globalMultiplier = 1.0;
    private double minReputation = -200.0;
    private double maxReputation = 200.0;
    
    public LamCoreReputationProvider(RealisticVillagers plugin) {
        this.plugin = plugin;
        loadConfiguration();
        initializeLamCore();
    }
    
    private void loadConfiguration() {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("reputation.lamcore");
        if (config == null) return;
        
        enabled = config.getBoolean("enabled", false);
        mode = config.getString("mode", "additive");
        globalMultiplier = config.getDouble("global-multiplier", 1.0);
        minReputation = config.getDouble("min-reputation", -200.0);
        maxReputation = config.getDouble("max-reputation", 200.0);
    }
    
    private void initializeLamCore() {
        if (!enabled) {
            plugin.getLogger().info("LamCore reputation integration is disabled in config.");
            return;
        }
        
        plugin.getLogger().info("Attempting to initialize LamCore reputation integration...");
        
        lamCore = Bukkit.getPluginManager().getPlugin("LamCore");
        if (lamCore == null) {
            plugin.getLogger().info("LamCore plugin not found. LamCore reputation integration disabled.");
            enabled = false;
            return;
        }
        
        if (!lamCore.isEnabled()) {
            plugin.getLogger().info("LamCore plugin found but not enabled. LamCore reputation integration disabled.");
            enabled = false;
            return;
        }
        
        plugin.getLogger().info("LamCore plugin found and enabled. Version: " + lamCore.getDescription().getVersion());
        
        try {
            // Get the VouchModule instance directly
            plugin.getLogger().info("Trying to get VouchModule from LamCore...");
            Method getVouchModuleMethod = lamCore.getClass().getMethod("getVouchModule");
            vouchModule = getVouchModuleMethod.invoke(lamCore);
            
            if (vouchModule == null) {
                plugin.getLogger().warning("LamCore VouchModule not found or disabled. Check if vouch module is enabled in LamCore config.");
                enabled = false;
                return;
            }
            
            plugin.getLogger().info("VouchModule found: " + vouchModule.getClass().getName());
            
            // Get the calculateVouchScore method
            plugin.getLogger().info("Trying to get calculateVouchScore method...");
            calculateVouchScoreMethod = vouchModule.getClass().getMethod("calculateVouchScore", String.class);
            
            plugin.getLogger().info("Successfully hooked into LamCore reputation system!");
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to hook into LamCore reputation system. Error: " + e.getMessage(), e);
            enabled = false;
        }
    }
    
    @Override
    public boolean isEnabled() {
        return enabled && lamCore != null && lamCore.isEnabled();
    }
    
    @Override
    public int getReputation(Player player, Villager.Profession profession) {
        if (!isEnabled()) return 0;
        
        try {
            // Get the vouch score from LamCore
            double vouchScore = (double) calculateVouchScoreMethod.invoke(vouchModule, player.getUniqueId().toString());
            
            // Apply global multiplier
            double reputation = vouchScore * globalMultiplier;
            
            // Clamp to min/max values
            reputation = Math.max(minReputation, Math.min(maxReputation, reputation));
            
            return (int) Math.round(reputation);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error getting LamCore reputation for " + player.getName(), e);
            return 0;
        }
    }
    
    @Override
    public void modifyReputation(Player player, Villager.Profession profession, int amount) {
        // LamCore reputation is read-only from our perspective
        // We don't modify it directly, only read from it
        plugin.getLogger().fine("LamCore reputation is read-only. Cannot modify reputation for " + player.getName());
    }
    
    @Override
    public String getMode() {
        return mode;
    }
    
    @Override
    public void reload() {
        loadConfiguration();
        initializeLamCore();
    }
    
    @Override
    public String getProviderName() {
        return "LamCore";
    }
    
    @Override
    public Map<String, Object> getDebugInfo(Player player) {
        Map<String, Object> info = new HashMap<>();
        info.put("provider", getProviderName());
        info.put("enabled", isEnabled());
        info.put("mode", mode);
        info.put("global_multiplier", globalMultiplier);
        
        if (isEnabled()) {
            try {
                double vouchScore = (double) calculateVouchScoreMethod.invoke(vouchModule, player.getUniqueId().toString());
                info.put("vouch_score", vouchScore);
                info.put("base_reputation", vouchScore * globalMultiplier);
            } catch (Exception e) {
                info.put("error", e.getMessage());
            }
        }
        
        return info;
    }
}