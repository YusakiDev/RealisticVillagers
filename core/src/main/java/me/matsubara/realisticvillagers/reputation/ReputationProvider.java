package me.matsubara.realisticvillagers.reputation;

import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.util.Map;

public interface ReputationProvider {
    
    /**
     * Check if this reputation provider is enabled and functional
     */
    boolean isEnabled();
    
    /**
     * Get the reputation value for a player with a specific villager profession
     * @param player The player to get reputation for
     * @param profession The villager's profession (for profession-specific multipliers)
     * @return The reputation value
     */
    int getReputation(Player player, Villager.Profession profession);
    
    /**
     * Modify the reputation value for a player
     * @param player The player to modify reputation for
     * @param profession The villager's profession
     * @param amount The amount to modify (positive or negative)
     */
    void modifyReputation(Player player, Villager.Profession profession, int amount);
    
    /**
     * Get the mode of operation ("additive" or "replace")
     * - additive: Adds to the existing vanilla reputation
     * - replace: Completely replaces the vanilla reputation
     */
    String getMode();
    
    /**
     * Reload configuration for this provider
     */
    void reload();
    
    /**
     * Get the name of this provider for logging/debugging
     */
    String getProviderName();
    
    /**
     * Get debug information about this provider for a specific player
     */
    Map<String, Object> getDebugInfo(Player player);
}