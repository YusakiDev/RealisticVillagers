package me.matsubara.realisticvillagers.manager;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.GUIConfig;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TalkModeManager {
    
    private final RealisticVillagers plugin;
    private final Map<UUID, TalkSession> activeSessions;
    private final Map<UUID, WrappedTask> distanceCheckers;
    private final Map<UUID, Long> lastActivity;
    
    public TalkModeManager(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
        this.activeSessions = new ConcurrentHashMap<>();
        this.distanceCheckers = new HashMap<>();
        this.lastActivity = new ConcurrentHashMap<>();
        
        // Start idle timeout checker
        startIdleChecker();
    }
    
    public static class TalkSession {
        private final UUID playerId;
        private final UUID villagerId;
        private final String villagerName;
        private final long startTime;
        
        public TalkSession(@NotNull UUID playerId, @NotNull UUID villagerId, @NotNull String villagerName) {
            this.playerId = playerId;
            this.villagerId = villagerId;
            this.villagerName = villagerName;
            this.startTime = System.currentTimeMillis();
        }
        
        public UUID getPlayerId() {
            return playerId;
        }
        
        public UUID getVillagerId() {
            return villagerId;
        }
        
        public String getVillagerName() {
            return villagerName;
        }
        
        public long getStartTime() {
            return startTime;
        }
    }
    
    public boolean startTalkMode(@NotNull Player player, @NotNull IVillagerNPC villager) {
        GUIConfig guiConfig = plugin.getGuiConfig();
        
        if (!guiConfig.isTalkModeEnabled()) {
            return false;
        }
        
        // Check permission
        String permission = guiConfig.getTalkModePermission();
        if (!permission.isEmpty() && !player.hasPermission(permission)) {
            player.sendMessage(ChatColor.RED + "You don't have permission to talk with villagers!");
            return false;
        }
        
        UUID playerId = player.getUniqueId();
        UUID villagerId = villager.getUniqueId();
        
        // Check if player is already in talk mode
        if (activeSessions.containsKey(playerId)) {
            endTalkMode(player, false);
        }
        
        // Check if villager is already being talked to (if multiple talkers not allowed)
        if (!guiConfig.allowMultipleTalkers()) {
            for (TalkSession session : activeSessions.values()) {
                if (session.getVillagerId().equals(villagerId)) {
                    player.sendMessage(ChatColor.RED + "Someone is already talking to this villager!");
                    return false;
                }
            }
        }
        
        // Create new session
        String villagerName = villager.getVillagerName();
        TalkSession session = new TalkSession(playerId, villagerId, villagerName);
        activeSessions.put(playerId, session);
        lastActivity.put(playerId, System.currentTimeMillis());
        
        // Send entry message
        String prefix = guiConfig.getTalkModeChatPrefix()
            .replace("%villager%", villagerName)
            .replace("&", "§");
        player.sendMessage(ChatColor.GREEN + "You are now talking to " + villagerName + "!");
        player.sendMessage(ChatColor.GRAY + "Type messages normally or use @" + villagerName + " to talk.");
        player.sendMessage(ChatColor.GRAY + "Walk away, sneak, or right-click the villager again to exit talk mode.");
        
        // Play entry effects
        Location villagerLoc = villager.bukkit().getLocation();
        
        Particle particle = guiConfig.getTalkModeEntryParticle();
        if (particle != null && guiConfig.showTalkModeIndicators()) {
            villagerLoc.getWorld().spawnParticle(particle, 
                villagerLoc.add(0, 2, 0), 10, 0.3, 0.3, 0.3, 0);
        }
        
        Sound sound = guiConfig.getTalkModeEntrySound();
        if (sound != null) {
            player.playSound(villagerLoc, sound, 1.0f, 1.0f);
        }
        
        // Start distance checker
        startDistanceChecker(player, villager);
        
        // Talk mode just makes it easier to chat with this villager
        // No private AI sessions - use public @tagging system
        
        return true;
    }
    
    public void endTalkMode(@NotNull Player player, boolean playEffects) {
        UUID playerId = player.getUniqueId();
        TalkSession session = activeSessions.remove(playerId);
        
        if (session == null) {
            return;
        }
        
        // Cancel distance checker
        WrappedTask task = distanceCheckers.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        
        // Remove last activity
        lastActivity.remove(playerId);
        
        // Clear villager's interaction state
        UUID villagerId = session.getVillagerId();
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.Villager bukkitVillager : world.getEntitiesByClass(org.bukkit.entity.Villager.class)) {
                if (bukkitVillager.getUniqueId().equals(villagerId)) {
                    plugin.getConverter().getNPC(bukkitVillager).ifPresent(npc -> {
                        if (npc.isInteracting() && npc.getInteractingWith().equals(playerId)) {
                            npc.stopInteracting();
                        }
                    });
                    break;
                }
            }
        }
        
        // Send exit message
        player.sendMessage(ChatColor.YELLOW + "You stopped talking to " + session.getVillagerName() + ".");
        
        // Play exit effects
        if (playEffects) {
            GUIConfig guiConfig = plugin.getGuiConfig();
            Sound sound = guiConfig.getTalkModeExitSound();
            if (sound != null) {
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            }
        }
        
        // No AI session cleanup needed - we use public chat only
    }
    
    private void startDistanceChecker(@NotNull Player player, @NotNull IVillagerNPC villager) {
        GUIConfig guiConfig = plugin.getGuiConfig();
        double maxDistance = guiConfig.getTalkModeMaxDistance();
        
        WrappedTask task = plugin.getFoliaLib().getImpl().runAtEntityTimer(villager.bukkit(), () -> {
            // Check if player is still online
            if (!player.isOnline()) {
                endTalkMode(player, false);
                return;
            }
            
            // Check if villager still exists
            if (!villager.bukkit().isValid()) {
                endTalkMode(player, true);
                return;
            }
            
            // Check if player is sneaking (exit condition)
            if (player.isSneaking()) {
                endTalkMode(player, true);
                return;
            }
            
            // Check distance
            Location playerLoc = player.getLocation();
            Location villagerLoc = villager.bukkit().getLocation();
            
            if (!playerLoc.getWorld().equals(villagerLoc.getWorld()) ||
                playerLoc.distance(villagerLoc) > maxDistance) {
                endTalkMode(player, true);
            }
        }, 20L, 20L); // Check every second
        
        distanceCheckers.put(player.getUniqueId(), task);
    }
    
    private void startIdleChecker() {
        GUIConfig guiConfig = plugin.getGuiConfig();
        int idleTimeout = guiConfig.getTalkModeIdleTimeout();
        
        if (idleTimeout <= 0) {
            return;
        }
        
        plugin.getFoliaLib().getImpl().runTimer(() -> {
            long currentTime = System.currentTimeMillis();
            long timeoutMillis = idleTimeout * 1000L;
            
            for (Map.Entry<UUID, Long> entry : new HashMap<>(lastActivity).entrySet()) {
                UUID playerId = entry.getKey();
                Long lastActive = entry.getValue();
                
                if (currentTime - lastActive > timeoutMillis) {
                    Player player = plugin.getServer().getPlayer(playerId);
                    if (player != null) {
                        player.sendMessage(ChatColor.GRAY + "Talk mode ended due to inactivity.");
                        endTalkMode(player, true);
                    }
                }
            }
        }, 20L * 60, 20L * 60); // Check every minute
    }
    
    public void updateActivity(@NotNull Player player) {
        if (isInTalkMode(player)) {
            lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }
    
    public boolean isInTalkMode(@NotNull Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }
    
    @Nullable
    public TalkSession getTalkSession(@NotNull Player player) {
        return activeSessions.get(player.getUniqueId());
    }
    
    @Nullable
    public TalkSession getTalkSessionByVillager(@NotNull UUID villagerId) {
        for (TalkSession session : activeSessions.values()) {
            if (session.getVillagerId().equals(villagerId)) {
                return session;
            }
        }
        return null;
    }
    
    public void cleanup() {
        // End all active sessions
        for (UUID playerId : new HashMap<>(activeSessions).keySet()) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                endTalkMode(player, false);
            }
        }
        
        // Cancel all tasks
        for (WrappedTask task : distanceCheckers.values()) {
            task.cancel();
        }
        distanceCheckers.clear();
        activeSessions.clear();
        lastActivity.clear();
    }
}