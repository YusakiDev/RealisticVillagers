package me.matsubara.realisticvillagers.listener;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerPickGiftEvent;
import me.matsubara.realisticvillagers.manager.TalkModeManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
// Paper API support - try to import Paper's AsyncChatEvent if available
import java.lang.reflect.Method;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.bukkit.entity.Villager.Profession.*;

/**
 * Handles chat events for AI chat sessions.
 * EXPERIMENTAL FEATURE - Subject to change
 */
public class AIChatListeners implements Listener {
    
    private final RealisticVillagers plugin;
    
    // Track active conversations to manage behavior properly
    private final Map<UUID, UUID> activeConversations = new ConcurrentHashMap<>(); // villager UUID -> player UUID
    
    public AIChatListeners(RealisticVillagers plugin) {
        this.plugin = plugin;
    }
    
    // Early cancellation handler - runs first to prevent other plugins from processing AI chat
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChatEarlyCancel(@NotNull AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        
        // Cancel early if this looks like AI chat to prevent other plugins from processing it
        if (shouldHandleAsAIChat(player, message)) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(@NotNull AsyncPlayerChatEvent event) {
        // Skip if already cancelled by another plugin (but not by us)
        if (event.isCancelled() && !shouldHandleAsAIChat(event.getPlayer(), event.getMessage())) {
            return;
        }
        Player player = event.getPlayer();
        String message = event.getMessage();
        
        // For Folia compatibility, we must defer ALL processing to main thread
        // since AsyncPlayerChatEvent runs on async chat thread and cannot access worlds/entities
        
        // Check if player is in talk mode
        if (plugin.getTalkModeManager() != null && plugin.getTalkModeManager().isInTalkMode(player)) {
            TalkModeManager.TalkSession session = plugin.getTalkModeManager().getTalkSession(player);
            if (session != null && plugin.getAiService().isNaturalChatEnabled()) {
                // Cancel the chat event
                event.setCancelled(true);
                
                // Update activity
                plugin.getTalkModeManager().updateActivity(player);
                
                // Find the villager they're talking to - defer all processing to main thread
                UUID villagerId = session.getVillagerId();
                
                plugin.getFoliaLib().getImpl().runNextTick(task -> {
                    // Find the villager entity on main thread  
                    org.bukkit.entity.Villager villagerEntity = null;
                    for (org.bukkit.World world : plugin.getServer().getWorlds()) {
                        for (org.bukkit.entity.Villager bukkitVillager : world.getEntitiesByClass(org.bukkit.entity.Villager.class)) {
                            if (bukkitVillager.getUniqueId().equals(villagerId)) {
                                villagerEntity = bukkitVillager;
                                break;
                            }
                        }
                        if (villagerEntity != null) break;
                    }
                    
                    if (villagerEntity != null) {
                        // Schedule on the villager's thread for safe entity access
                        final org.bukkit.entity.Villager finalVillager = villagerEntity;
                        plugin.getFoliaLib().getImpl().runAtEntity(finalVillager, entityTask -> {
                            IVillagerNPC targetVillager = findVillagerById(villagerId);
                            if (targetVillager != null) {
                                // Send message to the villager using natural chat
                                handleNaturalChat(player, targetVillager, message);
                            } else {
                                player.sendMessage("§cThe villager you were talking to is no longer available.");
                                plugin.getTalkModeManager().endTalkMode(player, false);
                            }
                        });
                    } else {
                        // Villager not found
                        player.sendMessage("§cThe villager you were talking to is no longer available.");
                        plugin.getTalkModeManager().endTalkMode(player, false);
                    }
                });
                return;
            }
        }
        
        // Check for @Name prefix pattern for natural chat
        if (plugin.getAiService().isNaturalChatEnabled() && message.startsWith("@") && message.length() > 1) {
            String[] parts = message.split("\\s+", 2);
            if (parts.length >= 1) {
                String targetName = parts[0].substring(1); // Remove @ prefix
                String chatMessage = parts.length > 1 ? parts[1] : ""; // Message content after name
                
                // Cancel the chat event immediately
                event.setCancelled(true);
                
                // Find nearest villager by name - defer all processing to main thread
                plugin.getFoliaLib().getImpl().runNextTick(task -> {
                    final double MAX_RANGE = plugin.getAiService().getNaturalChatTriggerRange();
                    org.bukkit.entity.Villager targetEntity = null;
                    double closestDistance = MAX_RANGE + 1;
                    
                    // Search in player's world only
                    org.bukkit.World playerWorld = player.getWorld();
                    for (org.bukkit.entity.Villager bukkitVillager : playerWorld.getEntitiesByClass(org.bukkit.entity.Villager.class)) {
                        // Basic distance check
                        double distance = player.getLocation().distance(bukkitVillager.getLocation());
                        if (distance <= MAX_RANGE && distance < closestDistance) {
                            targetEntity = bukkitVillager;
                            closestDistance = distance;
                        }
                    }
                    
                    if (targetEntity != null) {
                        // Schedule on the target villager's thread for safe entity data access
                        final org.bukkit.entity.Villager finalTarget = targetEntity;
                        plugin.getFoliaLib().getImpl().runAtEntity(finalTarget, entityTask -> {
                            // Now safely check the name and get the NPC
                            var npcOpt = plugin.getConverter().getNPC(finalTarget);
                            if (npcOpt.isPresent()) {
                                IVillagerNPC npc = npcOpt.get();
                                if (npc.getVillagerName().equalsIgnoreCase(targetName)) {
                                    // Found matching villager - handle natural chat with AI
                                    handleNaturalChat(player, npc, chatMessage.isEmpty() ? "hello" : chatMessage);
                                    return;
                                }
                            }
                            // Name didn't match or NPC not found
                            player.sendMessage("§7No villager named '§e" + targetName + "§7' is nearby.");
                        });
                    } else {
                        // No villager found in range
                        player.sendMessage("§7No villager named '§e" + targetName + "§7' is nearby.");
                    }
                });
            }
        }
        
        // Check for auto-chat villagers (villagers that respond to any nearby chat)
        if (plugin.getAiService().isEnabled() && !event.isCancelled()) {
            // Check if there are any auto-chat villagers enabled at all (quick check)
            if (plugin.getAiService().hasAnyAutoChat()) {
                // Cancel the chat event and defer all processing to main thread
                event.setCancelled(true);
                
                // Defer all processing to main thread for Folia compatibility
                plugin.getFoliaLib().getImpl().runNextTick(task -> {
                    if (hasNearbyAutoChatVillagers(player)) {
                        // There are auto-chat villagers nearby, handle them
                        handleAutoChatVillagers(player, message);
                    }
                    // If no auto-chat villagers nearby, the event stays cancelled - no message is sent
                });
            }
            // If no auto-chat villagers enabled at all, let normal chat proceed
        }
    }
    
    /**
     * Quick check if this chat message should be handled as AI chat
     * This is used for early cancellation to prevent other plugins from processing it
     */
    private boolean shouldHandleAsAIChat(@NotNull Player player, @NotNull String message) {
        // Check if AI service is enabled
        if (!plugin.getAiService().isEnabled()) {
            return false;
        }
        
        // Check if player is in talk mode
        if (plugin.getTalkModeManager() != null && plugin.getTalkModeManager().isInTalkMode(player)) {
            if (plugin.getAiService().isNaturalChatEnabled()) {
                return true;
            }
        }
        
        // Check for @Name prefix pattern
        if (plugin.getAiService().isNaturalChatEnabled() && message.startsWith("@") && message.length() > 1) {
            return true;
        }
        
        // Check if there are any auto-chat villagers enabled at all
        if (plugin.getAiService().hasAnyAutoChat()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if there are any nearby villagers with auto-chat enabled
     * Note: This is called from runNextTick so it's safe to access entities
     */
    private boolean hasNearbyAutoChatVillagers(@NotNull Player player) {
        final double AUTO_CHAT_RANGE = plugin.getAiService().getNaturalChatTriggerRange();
        
        // Check synchronously - this method is called from runNextTick so entity access is safe
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
     * Note: This is called from runNextTick so entity access is safe
     */
    private void handleAutoChatVillagers(@NotNull Player player, @NotNull String message) {
        final double AUTO_CHAT_RANGE = plugin.getAiService().getNaturalChatTriggerRange();
        
        // Find nearby villagers with auto-chat enabled - this method is called from runNextTick so entity access is safe
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.Villager bukkitVillager : world.getEntitiesByClass(org.bukkit.entity.Villager.class)) {
                // Check if this villager has auto-chat enabled
                if (plugin.getAiService().hasAutoChat(bukkitVillager.getUniqueId())) {
                    // Check if villager is within range
                    double distance = player.getLocation().distance(bukkitVillager.getLocation());
                    if (distance <= AUTO_CHAT_RANGE) {
                        // Schedule on the villager's thread for safe entity access
                        plugin.getFoliaLib().getImpl().runAtEntity(bukkitVillager, task -> {
                            // Get villager NPC safely on its thread
                            plugin.getConverter().getNPC(bukkitVillager).ifPresent(npc -> {
                                // Check rate limit for this player
                                if (!plugin.getAiService().isRateLimited(player)) {
                                    // Start conversation behavior for auto-chat
                                    startConversationBehavior(npc, player);
                                    
                                    // Format message with villager name for context
                                    String contextMessage = message;
                                    
                                    // Show the player's message to nearby players
                                    showPlayerMessageToAutoChatVillager(player, npc, message);
                                    
                                    // Send to AI for response
                                    plugin.getAiService().sendNaturalMessage(npc, player, contextMessage).thenAccept(response -> {
                                        if (response != null && !response.isEmpty()) {
                                            // Show villager's response to all nearby players
                                            plugin.getFoliaLib().getImpl().runAtEntity(npc.bukkit(), responseTask -> {
                                                showVillagerResponseToNearby(npc, response);
                                            });
                                        } else {
                                            // No response received, but keep conversation behavior active for manual control
                                            // User can manually end conversation when needed
                                        }
                                    });
                                }
                            });
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
                        plugin.getFoliaLib().getImpl().runAtEntity(villager.bukkit(), (task) -> {
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
                plugin.getFoliaLib().getImpl().runAtEntity(villager.bukkit(), (task) -> {
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
        
        switch (giftType) {
            case LOVED:
                return "Oh wonderful! " + itemName + "! Thank you so much!";
            case LIKED:
                return "Thank you for the " + itemName + "! Very thoughtful.";
            case NEUTRAL:
                return "Thank you for the " + itemName + ".";
            case DISLIKED:
                return "Um... " + itemName + "? Well, thank you I suppose.";
            case HATED:
                return "Oh... " + itemName + ". That's... interesting. Thanks.";
            default:
                return "Thank you for the " + itemName + ".";
        }
    }
    
    private GiftType categorizeGift(Material material, Villager.Profession profession) {
        // Profession-specific preferences
        if (profession == FARMER) {
            if (material == Material.DIAMOND || material == Material.EMERALD) return GiftType.LOVED;
            if (material == Material.WHEAT_SEEDS || material == Material.BONE_MEAL) return GiftType.LIKED;
            if (material == Material.BREAD || material == Material.WHEAT) return GiftType.LIKED;
        } else if (profession == LIBRARIAN) {
            if (material == Material.BOOK || material == Material.ENCHANTED_BOOK) return GiftType.LOVED;
            if (material == Material.PAPER || material == Material.FEATHER) return GiftType.LIKED;
        } else if (profession == ARMORER) {
            if (material == Material.IRON_INGOT || material == Material.DIAMOND) return GiftType.LOVED;
            if (material.name().contains("ARMOR")) return GiftType.LIKED;
        } else if (profession == TOOLSMITH) {
            if (material == Material.IRON_INGOT || material == Material.DIAMOND) return GiftType.LOVED;
            if (material.name().contains("_PICKAXE") || material.name().contains("_AXE")) return GiftType.LIKED;
        } else if (profession == BUTCHER) {
            if (material.name().startsWith("COOKED_") || material == Material.BREAD) return GiftType.LIKED;
        } else if (profession == FISHERMAN) {
            if (material.name().contains("FISH") || material == Material.FISHING_ROD) return GiftType.LOVED;
        } else if (profession == WEAPONSMITH) {
            if (material == Material.IRON_INGOT || material == Material.DIAMOND) return GiftType.LOVED;
            if (material.name().contains("_SWORD") || material.name().contains("_AXE")) return GiftType.LIKED;
        } else if (profession == LEATHERWORKER) {
            if (material == Material.LEATHER || material == Material.RABBIT_HIDE) return GiftType.LOVED;
            if (material.name().contains("LEATHER")) return GiftType.LIKED;
        } else if (profession == FLETCHER) {
            if (material == Material.STICK || material == Material.FLINT) return GiftType.LOVED;
            if (material == Material.BOW || material == Material.ARROW) return GiftType.LIKED;
        } else if (profession == SHEPHERD) {
            if (material.name().contains("WOOL") || material == Material.SHEARS) return GiftType.LOVED;
            if (material.name().contains("DYE")) return GiftType.LIKED;
        } else if (profession == MASON) {
            if (material.name().contains("STONE") || material.name().contains("BRICK")) return GiftType.LOVED;
            if (material == Material.CLAY_BALL || material.name().contains("TERRACOTTA")) return GiftType.LIKED;
        } else if (profession == CARTOGRAPHER) {
            if (material == Material.MAP || material == Material.COMPASS) return GiftType.LOVED;
            if (material == Material.PAPER || material == Material.GLASS_PANE) return GiftType.LIKED;
        } else if (profession == CLERIC) {
            if (material == Material.REDSTONE || material == Material.GLOWSTONE_DUST) return GiftType.LOVED;
            if (material.name().contains("POTION") || material == Material.BREWING_STAND) return GiftType.LIKED;
        }
        // NITWIT and NONE (unemployed) have no specific preferences
        
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
     * Finds a villager by its UUID
     */
    @Nullable
    private IVillagerNPC findVillagerById(@NotNull UUID villagerId) {
        // Search all worlds for the villager
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.Villager bukkitVillager : world.getEntitiesByClass(org.bukkit.entity.Villager.class)) {
                if (bukkitVillager.getUniqueId().equals(villagerId)) {
                    var npcOpt = plugin.getConverter().getNPC(bukkitVillager);
                    return npcOpt.orElse(null);
                }
            }
        }
        return null;
    }
    
    /**
     * Finds the nearest villager with the given name within range
     */
    private IVillagerNPC findNearestVillagerByName(@NotNull Player player, @NotNull String name) {
        final double MAX_RANGE = plugin.getAiService().getNaturalChatTriggerRange(); // Maximum range for natural chat from config
        IVillagerNPC closestVillager = null;
        double closestDistance = MAX_RANGE + 1; // Start with a value larger than max range
        
        // Only search in the player's world for performance and range logic
        org.bukkit.World playerWorld = player.getWorld();
        for (org.bukkit.entity.Villager bukkitVillager : playerWorld.getEntitiesByClass(org.bukkit.entity.Villager.class)) {
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
        
        // Start conversation behavior - make villager look at player and stop normal movement
        startConversationBehavior(villager, player);
        
        // Show the player's message to nearby players (like normal chat but formatted)
        showPlayerMessageToNearby(player, villager, message);
        
        // Send to AI for response
        plugin.getAiService().sendNaturalMessage(villager, player, message).thenAccept(response -> {
            if (response != null && !response.isEmpty()) {
                // Show villager's response to all nearby players
                plugin.getFoliaLib().getImpl().runAtEntity(villager.bukkit(), (task) -> {
                    showVillagerResponseToNearby(villager, response);
                });
            } else {
                // No response received, but keep conversation behavior active for manual control
                // User can manually end conversation when needed
            }
        });
    }
    
    /**
     * Starts conversation behavior - villager looks at player and stops normal movement
     */
    private void startConversationBehavior(@NotNull IVillagerNPC villager, @NotNull Player player) {
        try {
            UUID villagerUUID = villager.getUniqueId();
            UUID playerUUID = player.getUniqueId();
            
            // Track this conversation
            activeConversations.put(villagerUUID, playerUUID);
            
            // Make villager look at the player
            villager.setLookTarget(player);
            
            // Stop villager's current movement by setting walk target to current location
            org.bukkit.Location currentLocation = villager.bukkit().getLocation();
            villager.setWalkTarget(currentLocation, 0.0, 0);
            
            plugin.getLogger().fine(String.format("Started conversation behavior for villager %s with player %s", 
                    villager.getVillagerName(), player.getName()));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to start conversation behavior: " + e.getMessage());
        }
    }
    
    /**
     * Ends conversation behavior - villager returns to normal AI behavior
     * This is for manual control only
     */
    private void endConversationBehavior(@NotNull IVillagerNPC villager) {
        try {
            UUID villagerUUID = villager.getUniqueId();
            
            // Remove from tracking
            activeConversations.remove(villagerUUID);
            
            // Clear the look target and resume normal behavior
            villager.resetActivity();
            
            plugin.getLogger().fine(String.format("Ended conversation behavior for villager %s", 
                    villager.getVillagerName()));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to end conversation behavior: " + e.getMessage());
        }
    }
    
    /**
     * Public method to manually end conversation behavior for a villager
     */
    public void endConversation(@NotNull IVillagerNPC villager) {
        if (activeConversations.containsKey(villager.getUniqueId())) {
            endConversationBehavior(villager);
        }
    }
    
    /**
     * Check if a villager is currently in conversation
     */
    public boolean isInConversation(@NotNull IVillagerNPC villager) {
        return activeConversations.containsKey(villager.getUniqueId());
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