package me.matsubara.realisticvillagers.npc;

import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.files.Config;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class NPCPool implements Listener, Runnable {

    private final @Getter RealisticVillagers plugin;
    private final Map<Integer, NPC> npcMap = new ConcurrentHashMap<>();
    private final Map<Integer, WrappedTask> npcTasks = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastPassengerRefresh = new ConcurrentHashMap<>();

    private static final double BUKKIT_VIEW_DISTANCE = Math.pow(Bukkit.getViewDistance() << 4, 2);
    private static final long PASSENGER_RESYNC_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(2500);

    public NPCPool(RealisticVillagers plugin) {
        this.plugin = plugin;
        Server server = this.plugin.getServer();
        server.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void run() {
        // Unused: visibility is handled by per-NPC region tasks started in takeCareOf().
    }

    protected void takeCareOf(NPC npc) {
        npcMap.put(npc.getEntityId(), npc);
        LivingEntity bukkit = npc.getNpc().bukkit();
        if (bukkit != null) {
            WrappedTask task = plugin.getFoliaLib().getScheduler().runAtEntityTimer(bukkit, () -> tickNPC(npc), 1L, 1L);
            npcTasks.put(npc.getEntityId(), task);
            lastPassengerRefresh.put(npc.getEntityId(), System.nanoTime());
        }
    }

    public Optional<NPC> getNPC(int entityId) {
        return Optional.ofNullable(npcMap.get(entityId));
    }


    public Optional<NPC> getNPC(UUID uniqueId) {
        return npcMap.values().stream().filter(npc -> npc.getProfile().getUUID().equals(uniqueId)).findFirst();
    }

    public void removeNPC(int entityId) {
        getNPC(entityId).ifPresent(npc -> {
            // Cancel the task immediately to stop ticking the NPC
            Optional.ofNullable(npcTasks.remove(entityId)).ifPresent(WrappedTask::cancel);

            LivingEntity bukkit = npc.getNpc().bukkit();
            if (bukkit != null) {
                plugin.getFoliaLib().getScheduler().runAtEntity(bukkit, task -> {
                    World world = bukkit.getWorld();
                    if (world != null) {
                        List.copyOf(world.getPlayers()).forEach(npc::hide);
                    } else {
                        npc.getSeeingPlayers().forEach(npc::hide);
                    }
                    // Remove from map AFTER hiding completes to allow DESTROY_ENTITIES packet handler to find NPC
                    npcMap.remove(entityId);
                    lastPassengerRefresh.remove(entityId);
                });
            } else {
                npc.getSeeingPlayers().forEach(npc::hide);
                // No async operation, safe to remove immediately
                npcMap.remove(entityId);
                lastPassengerRefresh.remove(entityId);
            }
        });
    }

    public void handleVisibility(@NotNull Player player, Location playerLocation, @NotNull NPC npc) {
        // Kept for compatibility; visibility is controlled by tickNPC() on-region. No-op here.
        tickNPC(npc);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerTeleport(@NotNull PlayerTeleportEvent event) {
        // If the player switched worlds, then we'll handle the visibility on onPlayerChangedWorld().
        Location to = event.getTo();
        if (to == null || !event.getPlayer().getWorld().equals(to.getWorld())) return;

        handleEventVisibility(event);
    }

    @EventHandler
    public void onPlayerChangedWorld(@NotNull PlayerChangedWorldEvent event) {
        handleEventVisibility(event);
    }

    @EventHandler
    public void handleRespawn(@NotNull PlayerRespawnEvent event) {
        handleEventVisibility(event);
    }

    private void handleEventVisibility(@NotNull PlayerEvent event) {
        // Ensure updates happen on the owning region of each NPC.
        for (NPC npc : npcMap.values()) {
            LivingEntity bukkit = npc.getNpc().bukkit();
            if (bukkit != null) {
                plugin.getFoliaLib().getScheduler().runAtEntity(bukkit, task -> tickNPC(npc));
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        handleRemoval(event.getEntity(), NPC::hide);
    }

    @EventHandler
    public void handleQuit(@NotNull PlayerQuitEvent event) {
        // No need to send remove packets, the player left.
        handleRemoval(event.getPlayer(), NPC::removeSeeingPlayer);
    }

    private void handleRemoval(Player player, BiConsumer<NPC, Player> action) {
        npcMap.values().stream()
                .filter(npc -> npc.isShownFor(player))
                .forEach(npc -> action.accept(npc, player));
    }

    private void tickNPC(@NotNull NPC npc) {
        LivingEntity bukkit = npc.getNpc().bukkit();
        if (bukkit == null || !bukkit.isValid()) return;

        Location npcLocation = bukkit.getLocation();
        World world = npcLocation.getWorld();
        if (world == null || !world.isChunkLoaded(npcLocation.getBlockX() >> 4, npcLocation.getBlockZ() >> 4)) {
            for (Player p : List.copyOf(npc.getSeeingPlayers())) {
                npc.hide(p);
            }
            return;
        }

        double renderDistanceSq = Math.min(Math.pow(Config.RENDER_DISTANCE.asInt(), 2), BUKKIT_VIEW_DISTANCE);
        double radius = Math.sqrt(renderDistanceSq);

        Set<Player> nearby = new HashSet<>();
        for (org.bukkit.entity.Entity e : bukkit.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player p && p.isValid() && p.getWorld().equals(world)) {
                nearby.add(p);
            }
        }

        // Show newly in range
        for (Player p : nearby) {
            if (!npc.isShownFor(p)) {
                npc.show(p, npcLocation);
            }
        }

        // Hide players that left range/world
        for (Player p : List.copyOf(npc.getSeeingPlayers())) {
            if (!nearby.contains(p)) {
                npc.hide(p);
            }
        }

        long now = System.nanoTime();
        long last = lastPassengerRefresh.getOrDefault(npc.getEntityId(), 0L);
        if (now - last >= PASSENGER_RESYNC_INTERVAL_NANOS) {
            for (Player p : npc.getSeeingPlayers()) {
                npc.sendPassengers(p);
            }
            lastPassengerRefresh.put(npc.getEntityId(), now);
        }
    }
}
