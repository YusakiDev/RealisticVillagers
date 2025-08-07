package me.matsubara.realisticvillagers.handler.protocol;

import com.cryptomorin.xseries.particles.XParticle;
import com.cryptomorin.xseries.reflection.XReflection;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.handler.npc.NPCHandler;
import me.matsubara.realisticvillagers.nms.INMSConverter;
import me.matsubara.realisticvillagers.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Raid;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public class VillagerHandler extends SimplePacketListenerAbstract {

    private final RealisticVillagers plugin;
    private final @Getter Set<UUID> allowSpawn = ConcurrentHashMap.newKeySet();
    private final List<PacketType.Play.Server> listenTo;

    /* MINECRAFT 1.21.7 METADATA MAPPINGS
    VILLAGER METADATA
    ID = 0-14  | Entity + LivingEntity base fields
    ID = 15    | DATA_UNHAPPY_COUNTER (AbstractVillager) | Integer
    ID = 16    | DATA_VILLAGER_DATA (Villager) | VillagerData
    PLAYER METADATA  
    ID = 0-14  | Entity + LivingEntity base fields
    ID = 15    | DATA_PLAYER_ABSORPTION_ID | Float 
    ID = 16    | DATA_SCORE_ID | Integer
    ID = 17    | DATA_PLAYER_MODE_CUSTOMISATION (Skin Parts) | Byte 
    ID = 18    | DATA_PLAYER_MAIN_HAND | Byte
    ID = 19    | DATA_SHOULDER_LEFT | CompoundTag
    ID = 20    | DATA_SHOULDER_RIGHT | CompoundTag
    */
    private static final Predicate<EntityData> REMOVE_METADATA = data -> {
        // Data between 0-14 is the same for players and villagers.
        int index = data.getIndex();
        if (index <= 14) return false;


        // Use 1.21.4 logic but adapted for 1.21.7 field numbers:
        
        // 15 & 16 is unnecessary.
        if (index == 15 || index == 16) return true;

        // 17: Keep skin state (over head shake timer).
        if (index == 17 && data.getType() != EntityDataTypes.BYTE) return true;

        // 19 & 20 only exists for players, they shouldn't collide with anything.
        return data.getType() == EntityDataTypes.VILLAGER_DATA;
    };

    private static final Set<PacketType.Play.Server> MOVEMENT_PACKETS = Sets.newHashSet(
            PacketType.Play.Server.ENTITY_ROTATION,
            PacketType.Play.Server.ENTITY_HEAD_LOOK,
            PacketType.Play.Server.ENTITY_TELEPORT,
            PacketType.Play.Server.ENTITY_VELOCITY,
            PacketType.Play.Server.ENTITY_RELATIVE_MOVE,
            PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION);

    public VillagerHandler(RealisticVillagers plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
        this.listenTo = ImmutableList.builder()
                .addAll(MOVEMENT_PACKETS)
                .add(
                        PacketType.Play.Server.SPAWN_ENTITY,
                        PacketType.Play.Server.SPAWN_LIVING_ENTITY,
                        PacketType.Play.Server.ENTITY_STATUS,
                        PacketType.Play.Server.ENTITY_METADATA)
                .build()
                .stream()
                .map(object -> (PacketType.Play.Server) object)
                .toList();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onPacketPlaySend(@NotNull PacketPlaySendEvent event) {
        if (event.isCancelled()
                || !listenTo.contains(event.getPacketType())
                || !(event.getPlayer() instanceof Player player)) return;

        PacketType.Play.Server type = event.getPacketType();
        boolean isMetadata = type == PacketType.Play.Server.ENTITY_METADATA;

        World world;
        PacketWrapper<?> metadataWrapper;
        int id;
        Entity entity;
        try {
            world = player.getWorld();
            if (isMetadata) {
                metadataWrapper = new PacketWrapper<>(event, false);
                id = metadataWrapper.readVarInt();
            } else {
                metadataWrapper = null;
                id = getEntityIdFromPacket(event);
            }
            entity = id != -1 ? SpigotReflectionUtil.getEntityById(world, id) : null;
        } catch (Throwable ex) {
            if (isMetadata) event.setCancelled(true);
            return;
        }

        if (!(entity instanceof AbstractVillager villager)) return;
        
        // ALWAYS block villager spawn packets - clients should only see player NPCs
        if (isCancellableSpawnPacket(event)) {
            event.setCancelled(true);
            return;
        }

        // For non-spawn packets, check if this is a RealisticVillagers NPC
        Optional<NPC> npc = plugin.getTracker().getNPC(id);
        
        INMSConverter converter = plugin.getConverter();

        if (type == PacketType.Play.Server.ENTITY_STATUS) {
            WrapperPlayServerEntityStatus status = new WrapperPlayServerEntityStatus(event);
            // Schedule entity access on the correct thread for Folia compatibility
            plugin.getFoliaLib().getScheduler().runAtEntity(villager, (task) -> {
                converter.getNPC(villager).ifPresent(temp -> handleStatus(temp, (byte) status.getStatus()));
            });
            return;
        }

        if (isMetadata) {
            // Cancel metadata packets for players using 1.7 (or lower).
            if (plugin.getCompatibilityManager().shouldCancelMetadata(player)) {
                event.setCancelled(true);
                return;
            }

            // Fix issues with ViaVersion - filter metadata instead of blocking completely
            ServerVersion version = PacketEvents.getAPI().getServerManager().getVersion();
            if (!version.isNewerThanOrEquals(ServerVersion.V_1_20_4)) return;

            try {
                List<EntityData> metadata = metadataWrapper.readEntityMetadata();
                if (!metadata.removeIf(REMOVE_METADATA)) return;

                event.setCancelled(true);

                // Cancel the packet and send a new one with filtered metadata
                WrapperPlayServerEntityMetadata wrapper = new WrapperPlayServerEntityMetadata(id, metadata);
                Object channel = SpigotReflectionUtil.getChannel(player);
                PacketEvents.getAPI().getProtocolManager().sendPacket(channel, wrapper);

                // Cannot adapt scale here - would violate Folia thread safety
                // Scale adaptation should be done when the NPC is spawned, not in packet handler
            } catch (Exception ignored) {
                event.setCancelled(true);
                return;
            }

            return;
        }

        if (npc.isEmpty() || !MOVEMENT_PACKETS.contains(type)) return;

        // Cannot check reviving status here - would violate Folia thread safety
        // Just handle the rotation for all movement packets
        rotateBody(event, villager);
    }

    private int getEntityIdFromPacket(@NotNull PacketPlaySendEvent event) {
        PacketType.Play.Server type = event.getPacketType();
        if (type == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity wrapper = new WrapperPlayServerSpawnEntity(event);
            return wrapper.getEntityId();
        } else if (type == PacketType.Play.Server.SPAWN_LIVING_ENTITY) {
            WrapperPlayServerSpawnLivingEntity wrapper = new WrapperPlayServerSpawnLivingEntity(event);
            return wrapper.getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_STATUS) {
            WrapperPlayServerEntityStatus wrapper = new WrapperPlayServerEntityStatus(event);
            return wrapper.getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_ROTATION) {
            WrapperPlayServerEntityRotation wrapper = new WrapperPlayServerEntityRotation(event);
            return wrapper.getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_HEAD_LOOK) {
            WrapperPlayServerEntityHeadLook wrapper = new WrapperPlayServerEntityHeadLook(event);
            return wrapper.getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_TELEPORT) {
            WrapperPlayServerEntityTeleport wrapper = new WrapperPlayServerEntityTeleport(event);
            return wrapper.getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_VELOCITY) {
            WrapperPlayServerEntityVelocity wrapper = new WrapperPlayServerEntityVelocity(event);
            return wrapper.getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
            WrapperPlayServerEntityRelativeMove wrapper = new WrapperPlayServerEntityRelativeMove(event);
            return wrapper.getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
            WrapperPlayServerEntityRelativeMoveAndRotation wrapper = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
            return wrapper.getEntityId();
        }
        return -1;
    }

    private void rotateBody(@NotNull PacketPlaySendEvent event, @NotNull AbstractVillager villager) {
        PacketType.Play.Server type = event.getPacketType();
        if (type != PacketType.Play.Server.ENTITY_HEAD_LOOK) return;

        WrapperPlayServerEntityHeadLook headLook = new WrapperPlayServerEntityHeadLook(event);

        // Schedule entity access on the correct thread for Folia compatibility
        plugin.getFoliaLib().getScheduler().runAtEntity(villager, (task) -> {
            Location location = villager.getLocation();
            float pitch = location.getPitch();

            // Rotate the body with the head.
            if (plugin.getConverter().getNPC(villager)
                    .map(IVillagerNPC::isShakingHead)
                    .orElse(false)) return;

            WrapperPlayServerEntityRelativeMoveAndRotation rotation = new WrapperPlayServerEntityRelativeMoveAndRotation(
                    villager.getEntityId(),
                    0.0d,
                    0.0d,
                    0.0d,
                    headLook.getHeadYaw(),
                    pitch,
                    false);

            PacketEvents.getAPI().getProtocolManager().sendPacket(event.getChannel(), rotation);
        });
    }

    private void handleStatus(@NotNull IVillagerNPC npc, byte status) {
        // Schedule entity access on the correct thread for Folia compatibility
        plugin.getFoliaLib().getScheduler().runAtEntity(npc.bukkit(), (task) -> {
            LivingEntity bukkit = npc.bukkit();

            XParticle particle;
            switch (status) {
                case 12 -> particle = XParticle.HEART;
                case 13 -> particle = XParticle.ANGRY_VILLAGER;
                case 14 -> particle = XParticle.HAPPY_VILLAGER;
                case 42 -> {
                    Raid raid = plugin.getConverter().getRaidAt(bukkit.getLocation());
                    particle = raid != null && raid.getStatus() == Raid.RaidStatus.ONGOING ? null : XParticle.SPLASH;
                }
                default -> particle = null;
            }
            if (particle == null) return;

            Location location = bukkit.getLocation();
            BoundingBox box = bukkit.getBoundingBox();

            ThreadLocalRandom random = ThreadLocalRandom.current();
            double x = location.getX() + box.getWidthX() * ((2.0d * random.nextDouble() - 1.0d) * 1.05d);
            double y = location.getY() + box.getHeight() * random.nextDouble() + 1.15d;
            double z = location.getZ() + box.getWidthZ() * ((2.0d * random.nextDouble() - 1.0d) * 1.05d);

            bukkit.getWorld().spawnParticle(
                    particle.get(),
                    x,
                    y,
                    z,
                    1,
                    random.nextGaussian() * 0.02d,
                    random.nextGaussian() * 0.02d,
                    random.nextGaussian() * 0.02d);
        });
    }

    private boolean isCancellableSpawnPacket(@NotNull PacketPlaySendEvent event) {
        PacketType.Play.Server type = event.getPacketType();
        if (type == PacketType.Play.Server.SPAWN_LIVING_ENTITY) return true;

        if (!XReflection.supports(19) || type != PacketType.Play.Server.SPAWN_ENTITY) return false;

        WrapperPlayServerSpawnEntity wrapper = new WrapperPlayServerSpawnEntity(event);
        EntityType entityType = SpigotConversionUtil.toBukkitEntityType(wrapper.getEntityType());

        return PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_20_2)
                || entityType == EntityType.VILLAGER
                || entityType == EntityType.WANDERING_TRADER;
    }
}