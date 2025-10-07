package me.matsubara.realisticvillagers.manager.revive;

import com.google.common.collect.Sets;
import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.event.RealisticRemoveEvent;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.util.PluginUtils;
import me.matsubara.realisticvillagers.util.customblockdata.CustomBlockData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Getter
public class ReviveManager implements Listener {

    private final RealisticVillagers plugin;
    private final Map<Block, MonumentAnimation> runningTasks = new HashMap<>();
    private final Set<UUID> ignoreDead = new HashSet<>();

    private static final Set<EntityDamageEvent.DamageCause> CAN_NOT_REVIVE;

    static {
        Set<EntityDamageEvent.DamageCause> temp = new HashSet<>();
        temp.add(EntityDamageEvent.DamageCause.SUFFOCATION);
        temp.add(EntityDamageEvent.DamageCause.VOID);

        // Added in 1.20.
        EntityDamageEvent.DamageCause border = PluginUtils.getOrNull(EntityDamageEvent.DamageCause.class, "WORLD_BORDER");
        if (border != null) temp.add(border);

        CAN_NOT_REVIVE = Sets.immutableEnumSet(temp);
    }

    public static final BlockFace[] MONUMENT = {
            BlockFace.NORTH_EAST,
            BlockFace.SOUTH_EAST,
            BlockFace.SOUTH_WEST,
            BlockFace.NORTH_WEST};

    public ReviveManager(RealisticVillagers plugin) {
        this.plugin = plugin;
        if (Config.REVIVE_ENABLED.asBool()) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public boolean isDay(@NotNull World world) {
        long time = world.getTime();
        return time < 13000 || time > 23000;
    }

    private boolean handleMonument(Player player, Block block) {
        // Already running.
        if (runningTasks.containsKey(block)) return false;

        CustomBlockData data = new CustomBlockData(block, plugin);

        // Shouldn't be null at this point, just checking to remove warning.
        String tag = data.get(plugin.getNpcValuesKey(), PersistentDataType.STRING);
        if (tag == null) return false;

        IVillagerNPC npc = plugin.getConverter().getNPCFromTag(tag);
        if (npc == null) return false;

        // Under head, should be an emerald block.
        Block downBlock = block.getRelative(BlockFace.DOWN);
        if (downBlock.getType() != Material.EMERALD_BLOCK) return false;

        // Sides of monument should be 2 emerald blocks with fire at the top.
        for (BlockFace face : MONUMENT) {
            Block upBlock = block.getRelative(BlockFace.UP);
            if (upBlock.getRelative(face, 2).getType() != Material.FIRE) return false;
            if (block.getRelative(face, 2).getType() != Material.EMERALD_BLOCK) return false;
            if (downBlock.getRelative(face, 2).getType() != Material.EMERALD_BLOCK) return false;
        }

        // Villager already exists, cancel to prevent duplicated entity.
        for (IVillagerNPC offline : plugin.getTracker().getOfflineVillagers()) {
            if (offline.getUniqueId().equals(npc.getUniqueId())) {
                plugin.getMessages().send(player, Messages.Message.INTERACT_FAIL_ALREADY_ALIVE);
                return false;
            }
        }

        runningTasks.put(block, new MonumentAnimation(plugin, tag, block));
        return true;
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        for (MonumentAnimation animation : runningTasks.values()) {
            BossBar display = animation.getDisplay();
            if (display != null && display.getPlayers().contains(player)) {
                display.removePlayer(player);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTransform(@NotNull EntityTransformEvent event) {
        EntityTransformEvent.TransformReason reason = event.getTransformReason();
        if (reason != EntityTransformEvent.TransformReason.INFECTION
                && reason != EntityTransformEvent.TransformReason.LIGHTNING) return;

        if (!(event.getEntity() instanceof Villager villager)) return;

        Entity transformed = event.getTransformedEntity();
        if (!(transformed instanceof Witch)
                && !(transformed instanceof ZombieVillager)) return;

        // Ignore villagers converted to ZOMBIE_VILLAGER or WITCH.
        if (!plugin.getTracker().isInvalid(villager, true)) {
            ignoreDead.add(villager.getUniqueId());
        }
    }

    @EventHandler
    public void onVillagerRemove(@NotNull RealisticRemoveEvent event) {
        RealisticRemoveEvent.RemovalReason reason = event.getReason();
        if (reason != RealisticRemoveEvent.RemovalReason.KILLED
                && reason != RealisticRemoveEvent.RemovalReason.DISCARDED) return;

        LivingEntity bukkit = event.getNPC().bukkit();
        if (bukkit == null || !bukkit.isDead()) return;

        handleTombstone(bukkit);
    }

    private void handleTombstone(LivingEntity living) {
        Optional<IVillagerNPC> npc = plugin.getConverter().getNPC(living);
        if (npc.isEmpty()) return;

        EntityDamageEvent damage = living.getLastDamageCause();
        if (damage != null && CAN_NOT_REVIVE.contains(damage.getCause())) {
            return;
        }

        if (ignoreDead.remove(living.getUniqueId())) return;

        if (Config.REVIVE_ONLY_WITH_CROSS.asBool()
                && living instanceof InventoryHolder holder
                && !PluginUtils.hasAnyOf(holder, plugin.getIsCrossKey())) return;

        ItemStack head = createHeadItem(npc.get(), plugin.getConverter().getNPCTag(living, false));

        // Drop head at villager death location.
        living.getWorld().dropItemNaturally(
                living.getLocation(),
                head);
    }

    public ItemStack createHeadItem(IVillagerNPC npc, String tag) {
        return plugin.getItem("revive.head-item", npc)
                .replace("%villager-name%", npc.getVillagerName())
                .setData(plugin.getNpcValuesKey(), PersistentDataType.STRING, tag)
                .build();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(@NotNull BlockIgniteEvent event) {
        // We need to cancel block ignite in the last 2 strikes to prevent the villager burning.
        Entity source;
        if (event.getCause() == BlockIgniteEvent.IgniteCause.LIGHTNING
                && (source = event.getIgnitingEntity()) != null
                && source.hasMetadata("LastStages")) {
            event.setCancelled(true);
        }

        Block block = event.getBlock().getRelative(BlockFace.DOWN);
        if (block.getType() != Material.EMERALD_BLOCK
                || (Config.REVIVE_ONLY_AT_NIGHT.asBool()) && isDay(block.getWorld())) return;

        plugin.getFoliaLib().getScheduler().runNextTick(task -> checkForMonument(event.getPlayer(), block));
    }

    private void checkForMonument(Player player, @NotNull Block block) {
        if (block.getRelative(BlockFace.UP).getType() != Material.FIRE) return;

        // Look for the center first.
        for (BlockFace face : MONUMENT) {
            Block relative = block.getRelative(face, 2);

            CustomBlockData data = new CustomBlockData(relative, plugin);
            if (!data.has(plugin.getNpcValuesKey(), PersistentDataType.STRING)) continue;

            // Found the center (probably).
            if (handleMonument(player, relative)) break;
        }
    }

}
