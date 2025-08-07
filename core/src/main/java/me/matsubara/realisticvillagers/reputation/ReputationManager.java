package me.matsubara.realisticvillagers.reputation;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class ReputationManager {
    
    private final RealisticVillagers plugin;
    private final List<ReputationProvider> providers = new ArrayList<>();
    private final Map<String, ReputationLevel> reputationLevels = new LinkedHashMap<>();
    private boolean debugMode = false;
    
    public ReputationManager(RealisticVillagers plugin) {
        this.plugin = plugin;
        
        // Delay provider loading to allow other plugins to enable
        plugin.getFoliaLib().getImpl().runLater((task) -> loadProviders(), 20L); // 1 second delay
    }
    
    private void loadProviders() {
        loadProviders(false);
    }
    
    private void loadProviders(boolean isRetry) {
        providers.clear();
        
        plugin.getLogger().info("Loading reputation providers" + (isRetry ? " (retry)" : "") + "...");
        
        // Load LamCore provider
        try {
            LamCoreReputationProvider lamCoreProvider = new LamCoreReputationProvider(plugin);
            providers.add(lamCoreProvider); // Always add it so we can see debug info
            
            if (lamCoreProvider.isEnabled()) {
                plugin.getLogger().info("Loaded reputation provider: " + lamCoreProvider.getProviderName());
            } else {
                plugin.getLogger().info("LamCore reputation provider loaded but disabled.");
                
                // If this is the first try and LamCore exists but isn't enabled yet, retry later
                if (!isRetry) {
                    Plugin lamCore = plugin.getServer().getPluginManager().getPlugin("LamCore");
                    if (lamCore != null && !lamCore.isEnabled()) {
                        plugin.getLogger().info("LamCore found but not enabled yet. Will retry in 3 seconds...");
                        plugin.getFoliaLib().getImpl().runLater((task) -> loadProviders(true), 60L); // 3 seconds later
                        return; // Don't log final status yet
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize LamCore reputation provider", e);
        }
        
        // Future providers can be added here
        // providers.add(new TownyReputationProvider(plugin));
        // providers.add(new FactionsReputationProvider(plugin));
        
        debugMode = plugin.getConfig().getBoolean("reputation.debug", false);
        
        int activeCount = getActiveProviderNames().size();
        if (activeCount > 0) {
            plugin.getLogger().info("Reputation system initialized with " + activeCount + " active providers: " + String.join(", ", getActiveProviderNames()));
        } else {
            plugin.getLogger().info("Reputation system initialized with 0 active providers (using vanilla reputation only)");
        }
    }
    
    /**
     * Get the total reputation for a player with a villager
     * This combines vanilla reputation with all provider reputations
     */
    public int getTotalReputation(IVillagerNPC villager, Player player) {
        // Start with vanilla reputation
        int reputation = villager.getReputation(player.getUniqueId());
        
        if (debugMode) {
            plugin.getLogger().info("Base vanilla reputation for " + player.getName() + ": " + reputation);
        }
        
        // Get villager profession
        Villager.Profession profession = Villager.Profession.NONE;
        if (villager.bukkit() instanceof Villager v) {
            profession = v.getProfession();
        }
        
        // Apply reputation from providers
        for (ReputationProvider provider : providers) {
            if (!provider.isEnabled()) continue;
            
            int providerRep = provider.getReputation(player, profession);
            
            if ("replace".equalsIgnoreCase(provider.getMode())) {
                // Replace mode: use provider reputation instead of vanilla
                reputation = providerRep;
                if (debugMode) {
                    plugin.getLogger().info(provider.getProviderName() + " replaced reputation with: " + providerRep);
                }
            } else {
                // Additive mode: add to existing reputation
                reputation += providerRep;
                if (debugMode) {
                    plugin.getLogger().info(provider.getProviderName() + " added reputation: " + providerRep);
                }
            }
        }
        
        if (debugMode) {
            plugin.getLogger().info("Final reputation for " + player.getName() + ": " + reputation);
        }
        
        return reputation;
    }
    
    /**
     * Modify reputation through all applicable providers
     */
    public void modifyReputation(IVillagerNPC villager, Player player, int amount) {
        // Always modify vanilla reputation
        if (amount > 0) {
            villager.addMinorPositive(player.getUniqueId(), amount);
        } else if (amount < 0) {
            villager.addMinorNegative(player.getUniqueId(), Math.abs(amount));
        }
        
        // Get villager profession
        Villager.Profession profession = Villager.Profession.NONE;
        if (villager.bukkit() instanceof Villager v) {
            profession = v.getProfession();
        }
        
        // Also notify providers (they might want to track this)
        for (ReputationProvider provider : providers) {
            if (!provider.isEnabled()) continue;
            
            try {
                provider.modifyReputation(player, profession, amount);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, 
                    "Error modifying reputation with provider " + provider.getProviderName(), e);
            }
        }
    }
    
    /**
     * Get debug information for all providers
     */
    public Map<String, Object> getDebugInfo(Player player) {
        Map<String, Object> info = new HashMap<>();
        info.put("debug_mode", debugMode);
        info.put("provider_count", providers.size());
        
        List<Map<String, Object>> providerInfo = new ArrayList<>();
        for (ReputationProvider provider : providers) {
            providerInfo.add(provider.getDebugInfo(player));
        }
        info.put("providers", providerInfo);
        
        return info;
    }
    
    /**
     * Reload all reputation providers
     */
    public void reload() {
        loadProviders();
    }
    
    /**
     * Check if any external reputation providers are active
     */
    public boolean hasActiveProviders() {
        return providers.stream().anyMatch(ReputationProvider::isEnabled);
    }
    
    /**
     * Get list of active provider names
     */
    public List<String> getActiveProviderNames() {
        List<String> names = new ArrayList<>();
        for (ReputationProvider provider : providers) {
            if (provider.isEnabled()) {
                names.add(provider.getProviderName());
            }
        }
        return names;
    }
}