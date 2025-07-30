package me.matsubara.realisticvillagers.listener;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerPickGiftEvent;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

/**
 * Handles chat events for AI chat sessions.
 * EXPERIMENTAL FEATURE - Subject to change
 */
public class AIChatListeners implements Listener {
    
    private final RealisticVillagers plugin;
    
    public AIChatListeners(RealisticVillagers plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(@NotNull AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        
        // Check if player is in an AI chat session
        if (plugin.getAiService().isInChatSession(player)) {
            event.setCancelled(true); // Cancel the normal chat
            
            String message = event.getMessage();
            
            // Handle the AI chat message
            plugin.getAiService().handleChatMessage(player, message);
        }
    }
    
    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // End AI chat session when player leaves
        if (plugin.getAiService().isInChatSession(player)) {
            plugin.getAiService().endChatSession(player);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH) // Run BEFORE core message processing
    public void onVillagerPickGift(@NotNull VillagerPickGiftEvent event) {
        // This is the AI enhancement layer
        
        if (!plugin.getAiService().isEnabled()) {
            // AI disabled - let core system handle everything normally
            return;
        }
        
        IVillagerNPC villager = event.getNPC();
        Player gifter = event.getGifter();
        ItemStack gift = event.getGift();
        
        // Check if we should handle this with AI (either in chat or enhanced mode)
        if (shouldHandleWithAI(villager, gifter)) {
            // Cancel the event to prevent native message, then handle with AI
            event.setCancelled(true);
            handleAIGiftReaction(villager, gifter, gift);
        }
        // If we don't handle with AI, let the native system process normally
    }
    
    private boolean shouldHandleWithAI(@NotNull IVillagerNPC villager, @NotNull Player gifter) {
        // Handle with AI if player is in AI chat session with this villager
        if (plugin.getAiService().isInChatSession(gifter)) {
            var session = plugin.getAiService().getChatSession(gifter);
            if (session != null && session.getVillagerUUID().equals(villager.bukkit().getUniqueId())) {
                return true; // AI chat mode
            }
        }
        
        // For now, only handle with AI if in active chat session
        // Could be extended to always use enhanced reactions when AI is enabled
        return false;
    }
    
    
    private void handleAIGiftReaction(@NotNull IVillagerNPC villager, @NotNull Player gifter, @NotNull ItemStack gift) {
        String itemName = gift.getType().name().toLowerCase().replace('_', ' ');
        int quantity = gift.getAmount();
        
        // Check if player is in AI chat session with this specific villager
        if (plugin.getAiService().isInChatSession(gifter)) {
            var session = plugin.getAiService().getChatSession(gifter);
            if (session != null && session.getVillagerUUID().equals(villager.bukkit().getUniqueId())) {
                // AI chat mode: AI generates natural response and adds to conversation
                String giftMessage = "I just received " + quantity + " " + itemName + " as a gift from you.";
                
                // Send to AI for natural response within conversation context
                plugin.getAiService().sendMessage(villager, gifter, giftMessage).thenAccept(response -> {
                    if (response != null) {
                        // Send AI's contextual reaction within the conversation
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            gifter.sendMessage("§7[§a" + villager.getVillagerName() + "§7] §f" + response);
                        });
                    }
                });
                return;
            }
        }
        
        // Fallback AI mode: Not in active chat, but AI is enabled
        String reaction = getEnhancedGiftReaction(villager, gift.getType(), gift.getAmount());
        if (reaction != null) {
            gifter.sendMessage("§a" + villager.getVillagerName() + "§7: §f" + reaction);
        }
    }
    
    private String getEnhancedGiftReaction(IVillagerNPC villager, Material material, int quantity) {
        // Enhanced AI gift reactions when AI is enabled but not in active chat
        // These are more sophisticated than core reactions but simpler than full AI
        return getBasicGiftReaction(villager, material, quantity);
    }
    
    private String getBasicGiftReaction(IVillagerNPC villager, Material material, int quantity) {
        // Get villager profession for preferences
        Villager.Profession profession = Villager.Profession.NONE;
        if (villager.bukkit() instanceof Villager bukkit) {
            profession = bukkit.getProfession();
        }
        
        // Determine gift value and appropriateness
        GiftType giftType = categorizeGift(material, profession);
        String itemName = material.name().toLowerCase().replace('_', ' ');
        
        return switch (giftType) {
            case LOVED -> "Oh wonderful! " + itemName + "! Thank you so much!";
            case LIKED -> "Thank you for the " + itemName + "! Very thoughtful.";
            case NEUTRAL -> "Thank you for the " + itemName + ".";
            case DISLIKED -> "Um... " + itemName + "? Well, thank you I suppose.";
            case HATED -> "Oh... " + itemName + ". That's... interesting. Thanks.";
        };
    }
    
    private GiftType categorizeGift(Material material, Villager.Profession profession) {
        // Profession-specific preferences
        switch (profession) {
            case FARMER -> {
                if (material == Material.DIAMOND || material == Material.EMERALD) return GiftType.LOVED;
                if (material == Material.WHEAT_SEEDS || material == Material.BONE_MEAL) return GiftType.LIKED;
                if (material == Material.BREAD || material == Material.WHEAT) return GiftType.LIKED;
            }
            case LIBRARIAN -> {
                if (material == Material.BOOK || material == Material.ENCHANTED_BOOK) return GiftType.LOVED;
                if (material == Material.PAPER || material == Material.FEATHER) return GiftType.LIKED;
            }
            case ARMORER -> {
                if (material == Material.IRON_INGOT || material == Material.DIAMOND) return GiftType.LOVED;
                if (material.name().contains("ARMOR")) return GiftType.LIKED;
            }
            case TOOLSMITH -> {
                if (material == Material.IRON_INGOT || material == Material.DIAMOND) return GiftType.LOVED;
                if (material.name().contains("_PICKAXE") || material.name().contains("_AXE")) return GiftType.LIKED;
            }
            case BUTCHER -> {
                if (material.name().startsWith("COOKED_") || material == Material.BREAD) return GiftType.LIKED;
            }
            case FISHERMAN -> {
                if (material.name().contains("FISH") || material == Material.FISHING_ROD) return GiftType.LOVED;
            }
        }
        
        // Universal preferences
        if (material == Material.DIAMOND || material == Material.EMERALD) return GiftType.LOVED;
        if (material == Material.GOLD_INGOT) return GiftType.LIKED;
        if (material.name().contains("FLOWER") || material == Material.CAKE) return GiftType.LIKED;
        if (material == Material.BREAD || material.name().startsWith("COOKED_")) return GiftType.NEUTRAL;
        if (material == Material.ROTTEN_FLESH || material == Material.POISONOUS_POTATO) return GiftType.HATED;
        
        return GiftType.NEUTRAL;
    }
    
    private enum GiftType {
        LOVED, LIKED, NEUTRAL, DISLIKED, HATED
    }
}