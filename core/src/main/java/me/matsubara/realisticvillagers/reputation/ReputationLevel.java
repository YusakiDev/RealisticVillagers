package me.matsubara.realisticvillagers.reputation;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public class ReputationLevel {
    
    private final String name;
    private final int minReputation;
    private final int maxReputation;
    private final List<BlockedAction> blockedActions;
    private final double priceModifier;
    private final double giftChance;
    private final double fleeDistance;
    private final boolean alertOthers;
    private final boolean defendPlayer;
    private final boolean shareGossip;
    
    public enum BlockedAction {
        TRADE,
        FOLLOW,
        GIFT_ACCEPT,
        PROCREATE,
        TALK,
        HIRE
    }
    
    public ReputationLevel(String name, ConfigurationSection config) {
        this.name = name;
        this.minReputation = config.getInt("min", 0);
        this.maxReputation = config.getInt("max", 0);
        
        // Load blocked actions
        this.blockedActions = new ArrayList<>();
        List<String> blockedList = config.getStringList("blocked-actions");
        for (String action : blockedList) {
            try {
                blockedActions.add(BlockedAction.valueOf(action));
            } catch (IllegalArgumentException ignored) {
                // Invalid action name, skip
            }
        }
        
        // Load modifiers and chances
        this.priceModifier = config.getDouble("price-modifier", 1.0);
        this.giftChance = config.getDouble("gift-chance", 0.0);
        this.fleeDistance = config.getDouble("flee-distance", 0.0);
        this.alertOthers = config.getBoolean("alert-others", false);
        this.defendPlayer = config.getBoolean("defend-player", false);
        this.shareGossip = config.getBoolean("share-gossip", false);
    }
    
    public boolean isInRange(int reputation) {
        return reputation >= minReputation && reputation <= maxReputation;
    }
    
    public boolean isActionBlocked(BlockedAction action) {
        return blockedActions.contains(action);
    }
    
    // Getters
    public String getName() {
        return name;
    }
    
    public int getMinReputation() {
        return minReputation;
    }
    
    public int getMaxReputation() {
        return maxReputation;
    }
    
    public double getPriceModifier() {
        return priceModifier;
    }
    
    public double getGiftChance() {
        return giftChance;
    }
    
    public double getFleeDistance() {
        return fleeDistance;
    }
    
    public boolean shouldAlertOthers() {
        return alertOthers;
    }
    
    public boolean shouldDefendPlayer() {
        return defendPlayer;
    }
    
    public boolean shouldShareGossip() {
        return shareGossip;
    }
    
    /**
     * Get a descriptive tone for AI chat based on reputation level
     */
    public String getChatTone() {
        switch (name.toLowerCase()) {
            case "hostile":
                return "cold, dismissive, and hostile. Speak curtly and show distrust. May refuse to talk or tell the player to leave.";
            case "unfriendly":
                return "wary and unfriendly. Be brief and unhelpful. Show reluctance to engage.";
            case "neutral":
                return "polite but reserved. Be professional and courteous without being overly warm.";
            case "friendly":
                return "warm and friendly. Be helpful and engaging. Show genuine interest in conversation.";
            case "beloved":
                return "extremely warm and affectionate. Treat the player as a dear friend. Be very helpful and may offer extra assistance or gifts.";
            default:
                return "neutral and professional.";
        }
    }
    
    /**
     * Get behavioral hints for AI
     */
    public String getBehaviorHints() {
        StringBuilder hints = new StringBuilder();
        
        if (alertOthers) {
            hints.append("You should warn other villagers about this player. ");
        }
        
        if (fleeDistance > 0) {
            hints.append("You are afraid and want to keep distance from this player. ");
        }
        
        if (defendPlayer) {
            hints.append("You would defend this player if they were attacked. ");
        }
        
        if (shareGossip) {
            hints.append("You trust them enough to share gossip about other players. ");
        }
        
        if (isActionBlocked(BlockedAction.TRADE)) {
            hints.append("You refuse to trade with this player. ");
        }
        
        if (isActionBlocked(BlockedAction.FOLLOW)) {
            hints.append("You won't follow this player. ");
        }
        
        return hints.toString();
    }
}