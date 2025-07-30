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
        String message = event.getMessage();
        
        // Check if player is in an AI chat session (legacy mode)
        if (plugin.getAiService().isInChatSession(player)) {
            event.setCancelled(true); // Cancel the normal chat
            
            // Handle the AI chat message (legacy mode)
            plugin.getAiService().handleChatMessage(player, message);
            return;
        }
        
        // Check for @Name prefix pattern for natural chat
        if (plugin.getAiService().isNaturalChatEnabled() && message.startsWith("@") && message.length() > 1) {
            String[] parts = message.split("\\s+", 2);
            if (parts.length >= 1) {
                String targetName = parts[0].substring(1); // Remove @ prefix
                String chatMessage = parts.length > 1 ? parts[1] : ""; // Message content after name
                
                // Cancel the chat event immediately
                event.setCancelled(true);
                
                // Find villager on main thread (entity access must be synchronous)
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    IVillagerNPC targetVillager = findNearestVillagerByName(player, targetName);
                    
                    if (targetVillager != null) {
                        // Found a villager - handle natural chat with AI
                        handleNaturalChat(player, targetVillager, chatMessage.isEmpty() ? "hello" : chatMessage);
                    } else {
                        // No villager found in range
                        player.sendMessage("§7No villager named '§e" + targetName + "§7' is nearby.");
                    }
                });
            }
        }
        
        // Check for auto-chat villagers (villagers that respond to any nearby chat)
        if (plugin.getAiService().isEnabled() && !event.isCancelled()) {
            // Cancel the chat event and check for auto-chat villagers on main thread
            event.setCancelled(true);
            
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (hasNearbyAutoChatVillagers(player)) {
                    // There are auto-chat villagers nearby, handle them
                    handleAutoChatVillagers(player, message);
                } else {
                    // No auto-chat villagers nearby, re-broadcast the original message
                    plugin.getServer().broadcastMessage("<" + player.getName() + "> " + message);
                }
            });
        }
    }
    
    /**
     * Checks if there are any nearby villagers with auto-chat enabled
     */
    private boolean hasNearbyAutoChatVillagers(@NotNull Player player) {
        final double AUTO_CHAT_RANGE = plugin.getAiService().getNaturalChatTriggerRange();
        
        // Check synchronously to avoid cancelling chat unnecessarily
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.Villager bukkitVillager : world.getEntitiesByClass(org.bukkit.entity.Villager.class)) {
                // Check if this villager has auto-chat enabled
                if (plugin.getAiService().hasAutoChat(bukkitVillager.getUniqueId())) {
                    // Check if villager is within range
                    double distance = player.getLocation().distance(bukkitVillager.getLocation());
                    if (distance <= AUTO_CHAT_RANGE) {
                        return true; // Found at least one auto-chat villager nearby
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Handles automatic chat responses from nearby villagers with auto-chat enabled
     */
    private void handleAutoChatVillagers(@NotNull Player player, @NotNull String message) {
        final double AUTO_CHAT_RANGE = plugin.getAiService().getNaturalChatTriggerRange();
        
        // Find nearby villagers with auto-chat enabled (already on main thread)
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.Villager bukkitVillager : world.getEntitiesByClass(org.bukkit.entity.Villager.class)) {
                // Check if this villager has auto-chat enabled
                if (plugin.getAiService().hasAutoChat(bukkitVillager.getUniqueId())) {
                    // Check if villager is within range
                    double distance = player.getLocation().distance(bukkitVillager.getLocation());
                    if (distance <= AUTO_CHAT_RANGE) {
                        // Get villager NPC
                        plugin.getConverter().getNPC(bukkitVillager).ifPresent(npc -> {
                            // Check rate limit for this player
                            if (!plugin.getAiService().isRateLimited(player)) {
                                // Format message with villager name for context
                                String contextMessage = message;
                                
                                // Show the player's message to nearby players
                                showPlayerMessageToAutoChatVillager(player, npc, message);
                                
                                // Send to AI for response
                                plugin.getAiService().sendNaturalMessage(npc, player, contextMessage).thenAccept(response -> {
                                    if (response != null && !response.isEmpty()) {
                                        // Show villager's response to all nearby players
                                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                                            showVillagerResponseToNearby(npc, response);
                                        });
                                    }
                                });
                            }
                        });
                    }
                }
            }
        }
    }
    
    /**
     * Shows the player's message to nearby players when talking to an auto-chat villager
     */
    private void showPlayerMessageToAutoChatVillager(@NotNull Player speaker, @NotNull IVillagerNPC villager, @NotNull String message) {
        final double HEARING_RANGE = plugin.getAiService().getNaturalChatHearingRange();
        String formattedMessage = "§7[§b" + speaker.getName() + " → " + villager.getVillagerName() + "§7] §f" + message;
        
        org.bukkit.entity.Villager bukkitVillager = (org.bukkit.entity.Villager) villager.bukkit();
        
        for (Player nearbyPlayer : bukkitVillager.getWorld().getPlayers()) {
            if (nearbyPlayer.getLocation().distance(bukkitVillager.getLocation()) <= HEARING_RANGE) {
                nearbyPlayer.sendMessage(formattedMessage);
            }
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
        // Only use AI if AI service is enabled
        if (!plugin.getAiService().isEnabled()) {
            return false;
        }
        
        // Handle with AI if player is in AI chat session with this villager
        if (plugin.getAiService().isInChatSession(gifter)) {
            var session = plugin.getAiService().getChatSession(gifter);
            if (session != null && session.getVillagerUUID().equals(villager.bukkit().getUniqueId())) {
                return true; // AI chat mode
            }
        }
        
        // Also handle with AI if this villager has auto-chat enabled (proximity chat)
        if (plugin.getAiService().hasAutoChat(villager.bukkit().getUniqueId())) {
            return true; // Auto-chat villager should use AI reactions
        }
        
        // For @name targeted chat and general AI enhancement, always use AI when enabled
        return true;
    }
    
    
    private void handleAIGiftReaction(@NotNull IVillagerNPC villager, @NotNull Player gifter, @NotNull ItemStack gift) {
        String itemName = gift.getType().name().toLowerCase().replace('_', ' ');
        int quantity = gift.getAmount();
        
        // Check if player is in AI chat session with this specific villager
        if (plugin.getAiService().isInChatSession(gifter)) {
            var session = plugin.getAiService().getChatSession(gifter);
            if (session != null && session.getVillagerUUID().equals(villager.bukkit().getUniqueId())) {
                // AI chat mode: AI generates natural response and adds to conversation
                String giftMessage = "The player just gave you " + quantity + " " + itemName + " as a gift. Please respond naturally to receiving this gift from them.";
                
                // Send to AI for natural response within conversation context
                plugin.getAiService().sendMessage(villager, gifter, giftMessage).thenAccept(response -> {
                    if (response != null && !response.isEmpty()) {
                        // Show AI's contextual reaction to all nearby players (like natural chat)
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            showVillagerResponseToNearby(villager, response);
                        });
                    }
                });
                return;
            }
        }
        
        // For all other AI modes (auto-chat, @name targeted, and general AI enhancement)
        // Generate AI reaction and show to everyone in range
        String giftMessage = "The player " + gifter.getName() + " just gave you " + quantity + " " + itemName + " as a gift. Please respond naturally and briefly to receiving this gift.";
        
        // Send to AI for natural response (works for all AI modes)
        plugin.getAiService().sendNaturalMessage(villager, gifter, giftMessage).thenAccept(response -> {
            if (response != null && !response.isEmpty()) {
                // Show AI's reaction to all nearby players (like natural chat)
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    showVillagerResponseToNearby(villager, response);
                });
            }
        });
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
    
    /**
     * Finds the nearest villager with the given name within range
     */
    private IVillagerNPC findNearestVillagerByName(@NotNull Player player, @NotNull String name) {
        final double MAX_RANGE = plugin.getAiService().getNaturalChatTriggerRange(); // Maximum range for natural chat from config
        IVillagerNPC closestVillager = null;
        double closestDistance = MAX_RANGE + 1; // Start with a value larger than max range
        
        // Search through all worlds for villagers
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.Villager bukkitVillager : world.getEntitiesByClass(org.bukkit.entity.Villager.class)) {
                // Get the RealisticVillagers NPC wrapper
                var npcOpt = plugin.getConverter().getNPC(bukkitVillager);
                if (npcOpt.isPresent()) {
                    IVillagerNPC npc = npcOpt.get();
                    // Check if name matches (case-insensitive)
                    if (npc.getVillagerName().equalsIgnoreCase(name)) {
                        // Check distance to player
                        double distance = player.getLocation().distance(bukkitVillager.getLocation());
                        if (distance <= MAX_RANGE && distance < closestDistance) {
                            // This villager is closer and within range
                            closestDistance = distance;
                            closestVillager = npc;
                        }
                    }
                }
            }
        }
        
        return closestVillager;
    }
    
    /**
     * Handles natural chat with AI (no session required)
     */
    private void handleNaturalChat(@NotNull Player player, @NotNull IVillagerNPC villager, @NotNull String message) {
        if (!plugin.getAiService().isEnabled()) {
            player.sendMessage("§cAI Chat is not enabled.");
            return;
        }
        
        // Check rate limit
        if (plugin.getAiService().isRateLimited(player)) {
            int cooldown = plugin.getAiService().getRemainingCooldown(player);
            player.sendMessage("§7Please wait " + cooldown + " seconds before talking to villagers again.");
            return;
        }
        
        // Show the player's message to nearby players (like normal chat but formatted)
        showPlayerMessageToNearby(player, villager, message);
        
        // Send to AI for response
        plugin.getAiService().sendNaturalMessage(villager, player, message).thenAccept(response -> {
            if (response != null && !response.isEmpty()) {
                // Show villager's response to all nearby players
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    showVillagerResponseToNearby(villager, response);
                });
            }
        });
    }
    
    /**
     * Shows the player's message to nearby players
     */
    private void showPlayerMessageToNearby(@NotNull Player speaker, @NotNull IVillagerNPC villager, @NotNull String message) {
        final double HEARING_RANGE = plugin.getAiService().getNaturalChatHearingRange();
        String formattedMessage = "§7[§b" + speaker.getName() + " → " + villager.getVillagerName() + "§7] §f" + message;
        
        org.bukkit.entity.Villager bukkitVillager = (org.bukkit.entity.Villager) villager.bukkit();
        
        for (Player nearbyPlayer : bukkitVillager.getWorld().getPlayers()) {
            if (nearbyPlayer.getLocation().distance(bukkitVillager.getLocation()) <= HEARING_RANGE) {
                nearbyPlayer.sendMessage(formattedMessage);
            }
        }
    }
    
    /**
     * Shows the villager's response to nearby players
     */
    private void showVillagerResponseToNearby(@NotNull IVillagerNPC villager, @NotNull String response) {
        final double HEARING_RANGE = plugin.getAiService().getNaturalChatHearingRange();
        String formattedMessage = "§a[" + villager.getVillagerName() + "]§7: §f" + response;
        
        org.bukkit.entity.Villager bukkitVillager = (org.bukkit.entity.Villager) villager.bukkit();
        
        for (Player nearbyPlayer : bukkitVillager.getWorld().getPlayers()) {
            if (nearbyPlayer.getLocation().distance(bukkitVillager.getLocation()) <= HEARING_RANGE) {
                nearbyPlayer.sendMessage(formattedMessage);
            }
        }
    }
}