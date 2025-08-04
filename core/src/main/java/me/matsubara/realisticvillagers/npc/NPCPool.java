package me.matsubara.realisticvillagers.npc;

import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.files.Config;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NPCPool implements Listener {

    private final @Getter RealisticVillagers plugin;
    private final Map<Integer, NPC> npcMap = new ConcurrentHashMap<>();

    private static final double BUKKIT_VIEW_DISTANCE = Math.pow(Bukkit.getViewDistance() << 4, 2);

    public NPCPool(RealisticVillagers plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
        tick();
    }

    protected void tick() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                for (NPC npc : npcMap.values()) {
                    LivingEntity bukkit = npc.getNpc().bukkit();
                    if (bukkit == null) continue;

                    Location npcLocation = bukkit.getLocation();
                    Location playerLocation = player.getLocation();

                    World npcWorld = npcLocation.getWorld();
                    if (npcWorld == null) continue;

                    if (!npcWorld.equals(playerLocation.getWorld())
                            || !npcWorld.isChunkLoaded(npcLocation.getBlockX() >> 4, npcLocation.getBlockZ() >> 4)) {
                        // Hide NPC if the NPC isn't in the same world of the player or the NPC isn't on a loaded chunk.
                        if (npc.isShownFor(player)) npc.hide(player);
                        continue;
                    }

                    int renderDistance = Config.RENDER_DISTANCE.asInt();
                    boolean inRange = npcLocation.distanceSquared(playerLocation) <= Math.min(renderDistance * renderDistance, BUKKIT_VIEW_DISTANCE);

                    if (!inRange && npc.isShownFor(player)) {
                        npc.hide(player);
                    } else if (inRange && !npc.isShownFor(player)) {
                        npc.show(player);
                    } else if (npc.isShownFor(player)) {
                        // NPC is shown, check nametag distance separately
                        int nametagRenderDistance = Config.NAMETAG_RENDER_DISTANCE.asInt();
                        double distanceSquared = npcLocation.distanceSquared(playerLocation);
                        boolean nametagInRange = distanceSquared <= Math.min(nametagRenderDistance * nametagRenderDistance, BUKKIT_VIEW_DISTANCE);
                        
                        if (!nametagInRange) {
                            // Player moved beyond nametag range - hide nametags only
                            npc.hideNametags(player);
                        }
                        // Note: nametags will reappear when NPC is next refreshed or re-shown
                    }
                }
            }
        }, 10L, 10L); // Faster tick rate to reduce ghost nametag window
    }

    protected void takeCareOf(NPC npc) {
        npcMap.put(npc.getEntityId(), npc);
    }

    public Optional<NPC> getNPC(int entityId) {
        return Optional.ofNullable(npcMap.get(entityId));
    }

    public Optional<NPC> getNPC(UUID uniqueId) {
        return npcMap.values().stream().filter(npc -> npc.getProfile().getUUID().equals(uniqueId)).findFirst();
    }

    public void removeNPC(int entityId) {
        getNPC(entityId).ifPresent(npc -> {
            // Ensure comprehensive cleanup to prevent ghost nametags
            Collection<Player> seeingPlayers = new ArrayList<>(npc.getSeeingPlayers());
            
            // Hide for all seeing players with explicit nametag cleanup
            for (Player player : seeingPlayers) {
                npc.hide(player);
            }
            
            // Reset nametag entity IDs to prevent ghost references
            if (npc.getNpc() instanceof me.matsubara.realisticvillagers.entity.Nameable nameable) {
                nameable.setNametagEntity(-1);
                nameable.setNametagItemEntity(-1);
            }
            
            // Remove from map after cleanup
            npcMap.remove(entityId);
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void handleJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Multiple attempts to ensure NPCs are shown, especially after server restart
        // Try at 1s, 2s, and 4s to handle various loading scenarios
        int[] delays = {20, 40, 80};
        
        for (int delay : delays) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Only process if player is still online
                if (!player.isOnline()) return;
                
                // If NPC pool is empty, try to spawn NPCs from existing villagers
                if (npcMap.isEmpty()) {
                    for (World world : plugin.getServer().getWorlds()) {
                        for (LivingEntity entity : world.getLivingEntities()) {
                            if (entity instanceof org.bukkit.entity.Villager villager) {
                                if (!plugin.getTracker().isInvalid(villager) && !plugin.getTracker().hasNPC(villager.getEntityId())) {
                                    plugin.getTracker().spawnNPC(villager);
                                }
                            }
                        }
                    }
                }
                
                for (NPC npc : npcMap.values()) {
                    // Skip if already shown to this player
                    if (npc.isShownFor(player)) continue;
                    
                    LivingEntity bukkit = npc.getNpc().bukkit();
                    if (bukkit == null) continue;
                    
                    Location npcLocation = bukkit.getLocation();
                    Location playerLocation = player.getLocation();
                    
                    World npcWorld = npcLocation.getWorld();
                    if (npcWorld == null || !npcWorld.equals(playerLocation.getWorld())) continue;
                    
                    // Check if chunk is loaded
                    if (!npcWorld.isChunkLoaded(npcLocation.getBlockX() >> 4, npcLocation.getBlockZ() >> 4)) continue;
                    
                    // Check if NPC should be visible to this player
                    int renderDistance = Config.RENDER_DISTANCE.asInt();
                    boolean inRange = npcLocation.distanceSquared(playerLocation) <= Math.min(renderDistance * renderDistance, BUKKIT_VIEW_DISTANCE);
                    
                    if (inRange) {
                        npc.show(player);
                    }
                }
            }, delay);
        }
    }

    @EventHandler
    public void handleRespawn(@NotNull PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        npcMap.values().stream()
                .filter(npc -> npc.isShownFor(player))
                .forEach(npc -> npc.hide(player));
    }

    @EventHandler
    public void handleQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        npcMap.values().stream()
                .filter(npc -> npc.isShownFor(player))
                .forEach(npc -> npc.removeSeeingPlayer(player));
    }
}