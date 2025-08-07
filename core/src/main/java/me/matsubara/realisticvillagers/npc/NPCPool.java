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
    
    // Cache for nametag status to detect changes
    private final Map<Integer, String> nametagCache = new ConcurrentHashMap<>();
    
    // Cache for tracking which players are currently focused on which villagers (for focus-only nametags)
    private final Map<String, Integer> playerFocusCache = new ConcurrentHashMap<>(); // playerUUID -> villager entityId

    private static final double BUKKIT_VIEW_DISTANCE = Math.pow(Bukkit.getViewDistance() << 4, 2);

    public NPCPool(RealisticVillagers plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
        tick();
    }

    protected void tick() {
        plugin.getFoliaLib().getImpl().runTimer(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                for (NPC npc : npcMap.values()) {
                    LivingEntity bukkitEntity = npc.getNpc().bukkit();
                    if (bukkitEntity == null) continue;

                    // For Folia compatibility: Schedule entity access on the entity's thread
                    plugin.getFoliaLib().getImpl().runAtEntity(bukkitEntity, (task) -> {
                        // Re-check if entity is still valid after scheduling
                        if (bukkitEntity.isDead() || !player.isOnline()) {
                            return;
                        }

                        Location npcLocation = bukkitEntity.getLocation();
                        Location playerLocation = player.getLocation();

                        World npcWorld = npcLocation.getWorld();
                        if (npcWorld == null) return;

                        if (!npcWorld.equals(playerLocation.getWorld())
                                || !npcWorld.isChunkLoaded(npcLocation.getBlockX() >> 4, npcLocation.getBlockZ() >> 4)) {
                            // Hide NPC if the NPC isn't in the same world of the player or the NPC isn't on a loaded chunk.
                            if (npc.isShownFor(player)) npc.hide(player);
                            return;
                        }

                        int renderDistance = Config.RENDER_DISTANCE.asInt();
                        boolean inRange = npcLocation.distanceSquared(playerLocation) <= Math.min(renderDistance * renderDistance, BUKKIT_VIEW_DISTANCE);

                        if (!inRange && npc.isShownFor(player)) {
                            npc.hide(player);
                        } else if (inRange && !npc.isShownFor(player)) {
                            npc.show(player);
                        } else if (npc.isShownFor(player)) {
                            // NPC is shown, check nametag visibility
                            int nametagRenderDistance = Config.NAMETAG_RENDER_DISTANCE.asInt();
                            double distanceSquared = npcLocation.distanceSquared(playerLocation);
                            boolean nametagInRange = distanceSquared <= Math.min(nametagRenderDistance * nametagRenderDistance, BUKKIT_VIEW_DISTANCE);
                            
                            if (!nametagInRange) {
                                // Player moved beyond nametag range - hide nametags only
                                npc.hideNametags(player);
                                // Remove from focus cache if out of range
                                playerFocusCache.remove(player.getUniqueId().toString());
                            } else {
                                // Within range - check focus-only nametag setting
                                boolean focusOnly = Config.FOCUS_ONLY_NAMETAGS.asBool();
                                String playerUUID = player.getUniqueId().toString();
                                Integer currentFocus = playerFocusCache.get(playerUUID);
                                
                                if (focusOnly) {
                                    // Focus-only mode: check if player is looking at this villager
                                    boolean isLookingAt = isPlayerLookingAtVillager(player, npcLocation);
                                    
                                    if (isLookingAt) {
                                        // Player is looking at this villager
                                        if (currentFocus == null || !currentFocus.equals(npc.getEntityId())) {
                                            // Focus changed - hide previous nametag and show current
                                            if (currentFocus != null) {
                                                getNPC(currentFocus).ifPresent(previousNPC -> previousNPC.hideNametags(player));
                                            }
                                            npc.refreshNametags(player);
                                            playerFocusCache.put(playerUUID, npc.getEntityId());
                                        }
                                    } else if (currentFocus != null && currentFocus.equals(npc.getEntityId())) {
                                        // Player was looking at this villager but no longer is
                                        npc.hideNametags(player);
                                        playerFocusCache.remove(playerUUID);
                                    }
                                } else {
                                    // Normal mode: always show nametags when in range (legacy behavior)
                                    // This ensures nametags reappear when NPC is refreshed or re-shown
                                    playerFocusCache.remove(playerUUID); // Clear focus cache in normal mode
                                }
                            }
                        }
                    });
                }
            }
        }, 10L, 10L); // Faster tick rate to reduce ghost nametag window
        
        // Additional slower tick for nametag status updates (hunger/confinement)
        // For Folia compatibility, schedule individual checks per NPC on their region threads
        plugin.getFoliaLib().getImpl().runTimer(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                for (NPC npc : npcMap.values()) {
                    if (npc.isShownFor(player)) {
                        // Schedule the nametag check on the villager's region thread for Folia compatibility
                        if (npc.getNpc().bukkit() != null) {
                            plugin.getFoliaLib().getImpl().runAtEntity(npc.getNpc().bukkit(), (task) -> {
                                // Check if nametag content needs updating (hunger/confinement status changed)
                                if (shouldRefreshNametag(npc, player)) {
                                    npc.refreshNametags(player);
                                }
                            });
                        }
                    }
                }
            }
        }, 40L, 40L); // Every 2 seconds for status updates
        
        // Additional periodic check to catch missed villagers - runs every 30 seconds
        plugin.getFoliaLib().getImpl().runTimer(() -> {
            for (World world : plugin.getServer().getWorlds()) {
                plugin.getFoliaLib().getImpl().runAtLocation(world.getSpawnLocation(), (task) -> {
                    for (LivingEntity entity : world.getLivingEntities()) {
                        if (entity instanceof org.bukkit.entity.Villager villager) {
                            // Schedule entity access on correct thread for Folia compatibility
                            plugin.getFoliaLib().getImpl().runAtEntity(villager, (entityTask) -> {
                                // Check if this villager should be tracked but isn't yet (now safe on entity thread)
                                if (!plugin.getTracker().isInvalid(villager) && !plugin.getTracker().hasNPC(villager.getEntityId())) {
                                    plugin.getLogger().info(String.format("Found untracked villager %s (ID: %d), spawning NPC", 
                                            villager.getCustomName() != null ? villager.getCustomName() : "Unnamed", villager.getEntityId()));
                                    plugin.getTracker().spawnNPC(villager);
                                }
                            });
                        }
                    }
                });
            }
        }, 600L, 600L); // Every 30 seconds
    }

    protected void takeCareOf(NPC npc) {
        npcMap.put(npc.getEntityId(), npc);
        
        // Initialize nametag cache when NPC is first added
        int entityId = npc.getEntityId();
        String initialStatus = getNametagStatus(npc.getNpc());
        nametagCache.put(entityId, initialStatus);
        
        plugin.getLogger().fine(String.format("NPC %s (ID: %d) added to pool with initial status: %s", 
                npc.getNpc().getVillagerName(), entityId, initialStatus));
    }
    
    /**
     * Checks if a nametag should be refreshed due to status changes
     */
    private boolean shouldRefreshNametag(NPC npc, Player player) {
        int entityId = npc.getEntityId();
        
        // Generate current status string (hunger + confinement)
        me.matsubara.realisticvillagers.entity.IVillagerNPC villagerNPC = npc.getNpc();
        String currentStatus = getNametagStatus(villagerNPC);
        
        // Compare with cached status
        String cachedStatus = nametagCache.get(entityId);
        
        if (!currentStatus.equals(cachedStatus)) {
            // Status changed - update cache and return true
            nametagCache.put(entityId, currentStatus);
            
            // Debug logging to help troubleshoot nametag refresh issues
            plugin.getLogger().fine(String.format("Nametag refresh triggered for villager %s (ID: %d): %s -> %s", 
                    villagerNPC.getVillagerName(), entityId, cachedStatus, currentStatus));
            
            return true;
        }
        
        return false; // No change
    }
    
    /**
     * Generates a status string for nametag comparison that matches actual nametag content
     */
    private String getNametagStatus(me.matsubara.realisticvillagers.entity.IVillagerNPC npc) {
        try {
            // Generate the actual hunger and confinement status strings that appear in nametags
            String hungerStatus = getHungerStatusString(npc);
            String confinementStatus = getConfinementStatusString(npc);
            
            // Also include other dynamic content that might change
            String villagerName = npc.getVillagerName();
            
            // For villagers, include profession and level which can change
            String professionInfo = "";
            if (npc.bukkit() instanceof org.bukkit.entity.Villager villager) {
                try {
                    professionInfo = villager.getProfession().name() + ":" + villager.getVillagerLevel();
                } catch (IllegalStateException e) {
                    // Thread access violation in Folia - use cached info
                    professionInfo = "thread_error";
                }
            }
            
            // Create status string that includes all dynamic nametag content for comparison
            return String.format("name:%s,profession:%s,hunger:%s,confined:%s", 
                    villagerName, professionInfo, hungerStatus, confinementStatus);
        } catch (Exception e) {
            return "error";
        }
    }
    
    /**
     * Gets the hunger status string that matches NPC.getHungerStatus()
     */
    private String getHungerStatusString(me.matsubara.realisticvillagers.entity.IVillagerNPC npc) {
        int foodLevel = npc.getFoodLevel();
        int minWorkHunger = plugin.getWorkHungerConfig().getInt("min-work-hunger", 5);
        
        if (foodLevel < minWorkHunger) {
            return "hungry"; // Simplified for comparison
        } else {
            return ""; // Empty when not hungry
        }
    }
    
    /**
     * Gets the confinement status string that matches NPC.getConfinementStatus()
     */
    private String getConfinementStatusString(me.matsubara.realisticvillagers.entity.IVillagerNPC npc) {
        try {
            boolean isConfined = me.matsubara.realisticvillagers.util.AntiEnslavementUtil.isVillagerConfined(npc);
            
            if (isConfined) {
                return "confined"; // Simplified for comparison
            } else {
                return ""; // Empty when free
            }
        } catch (Exception e) {
            return "error"; // Error state
        }
    }

    public Optional<NPC> getNPC(int entityId) {
        return Optional.ofNullable(npcMap.get(entityId));
    }

    public Optional<NPC> getNPC(UUID uniqueId) {
        return npcMap.values().stream().filter(npc -> npc.getProfile().getUUID().equals(uniqueId)).findFirst();
    }

    public java.util.Collection<NPC> getNPCs() {
        return npcMap.values();
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
            
            // Clean up nametag status cache to prevent memory leaks
            nametagCache.remove(entityId);
            
            // Remove from map after cleanup
            npcMap.remove(entityId);
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void handleJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Enhanced multiple attempts to ensure NPCs are shown, especially after server restart
        // Try at 1s, 2s, 4s, 8s, and 16s to handle various loading scenarios including chunk loading
        int[] delays = {20, 40, 80, 160, 320};
        
        for (int delay : delays) {
            plugin.getFoliaLib().getImpl().runLater(() -> {
                // Only process if player is still online
                if (!player.isOnline()) return;
                
                // Try to spawn NPCs from existing villagers that aren't tracked yet
                for (World world : plugin.getServer().getWorlds()) {
                    // Process each world in its own region context for Folia compatibility
                    plugin.getFoliaLib().getImpl().runAtLocation(world.getSpawnLocation(), (task) -> {
                        for (LivingEntity entity : world.getLivingEntities()) {
                            if (entity instanceof org.bukkit.entity.Villager villager) {
                                // For Folia compatibility: Check and spawn each villager on its own thread
                                plugin.getFoliaLib().getImpl().runAtEntity(villager, (entityTask) -> {
                                    if (!plugin.getTracker().isInvalid(villager) && !plugin.getTracker().hasNPC(villager.getEntityId())) {
                                        plugin.getTracker().spawnNPC(villager);
                                    }
                                });
                            }
                        }
                    });
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
                        // Extra safety: schedule the NPC show with another small delay to ensure stability
                        plugin.getFoliaLib().getImpl().runAtEntityLater(npc.getNpc().bukkit(), () -> {
                            if (player.isOnline() && !npc.isShownFor(player)) {
                                npc.show(player);
                            }
                        }, 5L);
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
        
        // Clear focus cache for respawning player
        playerFocusCache.remove(player.getUniqueId().toString());
    }

    @EventHandler
    public void handleQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        npcMap.values().stream()
                .filter(npc -> npc.isShownFor(player))
                .forEach(npc -> npc.removeSeeingPlayer(player));
        
        // Clear focus cache for quitting player
        playerFocusCache.remove(player.getUniqueId().toString());
    }
    
    /**
     * Checks if a player is looking at a villager within a reasonable cone of view
     * @param player The player looking
     * @param villagerLocation The location of the villager
     * @return true if the player is looking at the villager
     */
    private boolean isPlayerLookingAtVillager(Player player, Location villagerLocation) {
        Location playerEye = player.getEyeLocation();
        
        // Calculate vector from player to villager
        org.bukkit.util.Vector toVillager = villagerLocation.toVector().subtract(playerEye.toVector()).normalize();
        
        // Get player's look direction
        org.bukkit.util.Vector playerLook = playerEye.getDirection().normalize();
        
        // Calculate dot product to determine angle
        double dotProduct = playerLook.dot(toVillager);
        
        // Convert to angle in degrees - dot product of 1 = 0 degrees, 0 = 90 degrees, -1 = 180 degrees
        // We want to show nametags when player is looking within about 45 degrees (cos(45°) ≈ 0.707)
        return dotProduct > 0.707; // About 45-degree cone
    }
}