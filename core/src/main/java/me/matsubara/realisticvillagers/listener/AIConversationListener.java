package me.matsubara.realisticvillagers.listener;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.manager.ai.AIConversationManager;
import me.matsubara.realisticvillagers.util.PluginUtils;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * Listens for chat events to handle AI conversations.
 */
public class AIConversationListener implements Listener {

    private final RealisticVillagers plugin;

    public AIConversationListener(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncPlayerChat(@NotNull AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        AIConversationManager aiManager = plugin.getAIConversationManager();
        if (aiManager == null || !aiManager.isInConversation(player)) {
            return;
        }

        // Cancel the event so the message doesn't broadcast to everyone
        event.setCancelled(true);

        String message = event.getMessage();
        plugin.getFoliaLib().getScheduler().runAtEntity(player, task -> handleConversationMessage(player, message));
    }

    private void handleConversationMessage(@NotNull Player player, @NotNull String message) {
        AIConversationManager aiManager = plugin.getAIConversationManager();
        if (aiManager == null || !aiManager.isInConversation(player)) {
            return;
        }

        UUID villagerUUID = aiManager.getConversationVillager(player);
        if (villagerUUID == null) {
            aiManager.endConversation(player);
            return;
        }

        IVillagerNPC npc = plugin.getTracker().getOfflineByUUID(villagerUUID);
        if (npc == null || !npc.bukkit().isValid()) {
            aiManager.endConversation(player);
            sendConfigMessage(player, aiManager, "messages.conversation-ended", "&cEnded conversation with %villager-name%.",
                    Map.of("%villager-name%", "the villager"));
            return;
        }

        // Show player's message to them in a formatted way
        String playerMessage = ChatColor.GRAY + "[You → " + ChatColor.WHITE + npc.getVillagerName() + ChatColor.GRAY + "] " + ChatColor.RESET + message;
        player.sendMessage(playerMessage);

        boolean showActionBar = aiManager.getConfig().getBoolean("conversation.show-actionbar", true);
        if (showActionBar) {
            sendActionBar(player, ChatColor.GRAY + npc.getVillagerName() + " is thinking...");
        }

        IVillagerNPC conversationNpc = npc;

        // Process the message asynchronously
        aiManager.processMessage(player, conversationNpc, message).thenAccept(response -> {
            // Run on main thread to send the response
            plugin.getFoliaLib().getScheduler().runAtEntity(player, (task) -> {
                AIConversationManager currentManager = plugin.getAIConversationManager();
                if (currentManager == null) {
                    return;
                }

                boolean currentShowActionBar = currentManager.getConfig().getBoolean("conversation.show-actionbar", true);
                if (!isConversationActive(currentManager, player, villagerUUID)) {
                    if (currentShowActionBar) {
                        clearActionBar(player);
                    }
                    return;
                }

                IVillagerNPC activeNpc = plugin.getTracker().getOfflineByUUID(villagerUUID);
                if (activeNpc == null || !activeNpc.bukkit().isValid()) {
                    currentManager.endConversation(player);
                    if (currentShowActionBar) {
                        clearActionBar(player);
                    }
                    sendConfigMessage(player, currentManager, "messages.conversation-ended",
                            "&cEnded conversation with %villager-name%.", Map.of("%villager-name%", "the villager"));
                    return;
                }

                if (response != null && !response.isEmpty()) {
                    String villagerMessage = ChatColor.GRAY + "[" + ChatColor.WHITE + activeNpc.getVillagerName() + ChatColor.GRAY + " → You] " + ChatColor.RESET + response;
                    player.sendMessage(villagerMessage);

                    if (currentShowActionBar) {
                        clearActionBar(player);
                    }
                } else {
                    sendConfigMessage(player, currentManager, "messages.api-error", "&cFailed to get response from AI. Please try again.", null);

                    if (currentShowActionBar) {
                        clearActionBar(player);
                    }
                }
            });
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("Error in AI conversation: " + throwable.getMessage());
            plugin.getFoliaLib().getScheduler().runAtEntity(player, (task) -> {
                AIConversationManager currentManager = plugin.getAIConversationManager();
                if (currentManager == null) {
                    return;
                }

                boolean actionBarEnabled = currentManager.getConfig().getBoolean("conversation.show-actionbar", true);
                if (!isConversationActive(currentManager, player, villagerUUID)) {
                    if (actionBarEnabled) {
                        clearActionBar(player);
                    }
                    return;
                }

                sendConfigMessage(player, currentManager, "messages.api-error", "&cFailed to get response from AI. Please try again.", null);

                if (actionBarEnabled) {
                    clearActionBar(player);
                }
            });
            return null;
        });
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        AIConversationManager aiManager = plugin.getAIConversationManager();

        if (aiManager != null && aiManager.isInConversation(player)) {
            aiManager.endConversation(player);
        }
    }

    private void sendConfigMessage(@NotNull Player player,
                                   @NotNull AIConversationManager manager,
                                   @NotNull String path,
                                   @NotNull String defaultValue,
                                   Map<String, String> replacements) {
        String message = manager.getConfig().getString(path);
        if (message == null || message.isBlank()) {
            message = defaultValue;
        }
        if (message == null || message.isBlank()) {
            return;
        }
        if (replacements != null && !replacements.isEmpty()) {
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                message = message.replace(entry.getKey(), entry.getValue());
            }
        }
        player.sendMessage(PluginUtils.translate(message));
    }

    private void sendActionBar(@NotNull Player player, @NotNull String legacyMessage) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(legacyMessage));
    }

    private void clearActionBar(@NotNull Player player) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
    }

    private boolean isConversationActive(@NotNull AIConversationManager manager, @NotNull Player player, @NotNull UUID villagerUUID) {
        if (!manager.isInConversation(player)) {
            return false;
        }
        UUID current = manager.getConversationVillager(player);
        return current != null && current.equals(villagerUUID);
    }
}
