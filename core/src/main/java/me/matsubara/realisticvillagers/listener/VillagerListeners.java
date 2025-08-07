package me.matsubara.realisticvillagers.listener;


import com.cryptomorin.xseries.reflection.XReflection;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.ExpectingType;
import me.matsubara.realisticvillagers.data.InteractType;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.GUIConfig;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import me.matsubara.realisticvillagers.gui.types.MainGUI;
import me.matsubara.realisticvillagers.manager.TalkModeManager;
import me.matsubara.realisticvillagers.npc.NPC;
import me.matsubara.realisticvillagers.tracker.VillagerTracker;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import me.matsubara.realisticvillagers.util.PluginUtils;
import me.matsubara.realisticvillagers.util.Reflection;
import org.apache.commons.lang3.Validate;
import org.bukkit.ChatColor;
import org.bukkit.GameEvent;
import org.bukkit.Material;
import org.bukkit.Raid;
import org.bukkit.entity.*;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.util.*;

public final class VillagerListeners extends SimplePacketListenerAbstract implements Listener {

    private final RealisticVillagers plugin;

    private static final MethodHandle MODIFIERS = Reflection.getFieldGetter(EntityDamageEvent.class, "modifiers");

    public VillagerListeners(RealisticVillagers plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    @Override
    public void onPacketPlayReceive(@NotNull PacketPlayReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);

        WrapperPlayClientInteractEntity.InteractAction action = wrapper.getAction();
        if (action == WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        int id = wrapper.getEntityId();

        Optional<NPC> npc;
        if ((npc = plugin.getTracker().getNPC(id)).isEmpty()) {
            return;
        }

        // Store necessary data from packet thread
        Player player = (Player) event.getPlayer();
        Entity entity = npc.get().getNpc().bukkit();
        
        // Don't cancel packets for wandering traders - let them use vanilla trading
        if (entity.getType() == EntityType.WANDERING_TRADER) {
            return;
        }
        
        EquipmentSlot slot = wrapper.getHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
        
        // Cancel the packet immediately to prevent client freezing
        event.setCancelled(true);
        
        // Schedule the interaction handling on the entity's thread
        plugin.getFoliaLib().getImpl().runAtEntity(entity, task -> {
            // Now we're on the correct thread and can safely access entity state
            if (handleInteract(player, slot, action, entity)) {
                // Already cancelled the packet event above
            }
        });
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGenericGameEvent(@NotNull GenericGameEvent event) {
        GameEvent gameEvent = event.getEvent();

        // RING_BELL is deprecated and shouldn't be used, but due to having the same key as BLOCK_CHANCE,
        // RING BELL is called because of the map replacing the duplicated key.
        if (gameEvent != GameEvent.BLOCK_CHANGE && gameEvent != GameEvent.RING_BELL) return;

        if (event.getLocation().getBlock().getType() != Material.BELL) return;

        // Play swing hand animation when ringing a bell.
        if (event.getEntity() instanceof Villager villager) {
            villager.swingMainHand();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!Config.ARROWS_PASS_THROUGH_OTHER_VILLAGERS.asBool()) return;

        // It's a custom villager since vanilla ones can't shoot arrows.
        if (!(event.getEntity().getShooter() instanceof Villager)) return;
        if (!(event.getHitEntity() instanceof Villager)) return;

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerCareerChange(@NotNull VillagerCareerChangeEvent event) {
        Villager villager = event.getEntity();

        VillagerTracker tracker = plugin.getTracker();
        if (tracker.isInvalid(villager)) return;

        // Update villager skin when changing a job after 1 tick since this event is called before changing a job.
        // Respawn NPC with the new profession texture.
        plugin.getFoliaLib().getImpl().runAtEntity(villager, task -> tracker.refreshNPCSkin(villager, true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTargetLivingEntity(@NotNull EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof IronGolem)) return;
        if (!(event.getTarget() instanceof Villager)) return;

        // Prevent iron golem attacking villagers (they might hit them by accident with a bow/crossbow).
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(@NotNull EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;

        Optional<IVillagerNPC> npcOptional = plugin.getConverter().getNPC(villager);
        
        // Handle player kills villager for retaliation system
        if (npcOptional.isPresent() && event.getEntity().getKiller() instanceof Player) {
            Player killer = (Player) event.getEntity().getKiller();
            handlePlayerVillagerViolence(npcOptional.get(), killer, true); // true = this is a kill
        }

        // Handle villager death alerts
        if (npcOptional.isPresent() && Config.ALERT_ON_VILLAGER_DEATH.asBool()) {
            me.matsubara.realisticvillagers.util.EquipmentManager.onVillagerDies(npcOptional.get());
        }
        
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Inventory open = player.getOpenInventory().getTopInventory();
            if (!(open.getHolder() instanceof InteractGUI interact)) continue;

            IVillagerNPC npc = interact.getNPC();
            if (npc != null && npc.bukkit().equals(villager)) player.closeInventory();
        }

        if (!Config.DROP_WHOLE_INVENTORY.asBool()) return;
        if (plugin.getTracker().isInvalid(villager, true)) return;

        List<ItemStack> drops = event.getDrops();
        drops.clear();

        Collections.addAll(drops, villager.getInventory().getContents());

        EntityEquipment equipment = villager.getEquipment();
        if (equipment != null) {
            Collections.addAll(drops, equipment.getItemInMainHand(), equipment.getItemInOffHand());
            Collections.addAll(drops, equipment.getArmorContents());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(@NotNull EntityInteractEvent event) {
        if (event.getEntityType() != EntityType.VILLAGER) return;
        if (event.getBlock().getType() != Material.FARMLAND) return;

        Villager villager = (Villager) event.getEntity();
        if (villager.getProfession() != Villager.Profession.FARMER) return;

        // Prevent farmer villager trampling farmlands.
        if (!plugin.getTracker().isInvalid(villager, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(@NotNull EntityChangeBlockEvent event) {
        Material type = event.getBlock().getType();
        if (!type.isAir() && type != Material.COMPOSTER) return;

        // Play swing hand animation when removing crop or using composter.
        if (event.getEntity() instanceof Villager villager) villager.swingMainHand();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        preventChangeSkinItemUse(event, event.getItemInHand());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        preventChangeSkinItemUse(event, event.getItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerArmorStandManipulate(@NotNull PlayerArmorStandManipulateEvent event) {
        preventChangeSkinItemUse(event, event.getPlayerItem());
    }

    // Changed the priority to LOW to support VTL.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerInteractEntity(@NotNull PlayerInteractEntityEvent event) {
        if (handleInteract(event.getPlayer(), event.getHand(), null, event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    private boolean handleInteract(@NotNull Player player, EquipmentSlot hand, @Nullable WrapperPlayClientInteractEntity.InteractAction action, Entity entity) {
        ItemStack item = player.getInventory().getItem(hand);
        boolean cancel = preventChangeSkinItemUse(null, item);

        // Special handling for wandering traders - don't cancel their events
        if (entity.getType() == EntityType.WANDERING_TRADER) {
            return false; // Never cancel wandering trader interactions
        }

        if (!(entity instanceof Villager villager)) return cancel;

        VillagerTracker tracker = plugin.getTracker();

        if (Config.DISABLE_INTERACTIONS.asBool()) return cancel;
        if (tracker.isInvalid(villager, true)) return cancel;

        Optional<IVillagerNPC> optional = plugin.getConverter().getNPC(villager);

        IVillagerNPC npc = optional.orElse(null);
        if (npc == null) return cancel;

        if (hand != EquipmentSlot.HAND) return true;
        if (action != null && action != WrapperPlayClientInteractEntity.InteractAction.INTERACT) return true;

        plugin.getFoliaLib().getImpl().runAtEntity(villager, (task -> {
            Messages messages = plugin.getMessages();

            // Don't open GUI if using the whistle.
            ItemMeta meta;
            if (item != null && (meta = item.getItemMeta()) != null) {
                PersistentDataContainer container = meta.getPersistentDataContainer();
                if (container.has(plugin.getIsWhistleKey(), PersistentDataType.INTEGER)) return;

                if (container.has(plugin.getSkinDataKey(), PersistentDataType.STRING)) {
                    handleChangeSkinItem(player, npc, item);
                    return;
                }

                if (item.getType() == Material.NAME_TAG && meta.hasDisplayName()) {
                    handleRename(player, npc, item);
                    return;
                }

                if (item.getType() == Material.LEAD) {
                    if (npc.isInteracting() && npc.getInteractingWith().equals(player.getUniqueId()) && npc.isFollowing()) {
                        messages.send(player, npc, Messages.Message.FOLLOW_ME_STOP);
                        npc.stopInteracting();
                    } else {
                        plugin.getInventoryListeners().handleFollorOrStay(npc, player, InteractType.FOLLOW_ME, true);
                    }
                    return;
                }
            }

            // Prevent interacting with villager if it's fighting.
            if (npc.isFighting() || npc.isInsideRaid()) {
                messages.send(player, Messages.Message.INTERACT_FAIL_FIGHTING_OR_RAID);
                return;
            }

            if (npc.isProcreating()) {
                messages.send(player, Messages.Message.INTERACT_FAIL_PROCREATING);
                return;
            }

            if (isExpecting(player, npc, ExpectingType.GIFT)) return;
            if (isExpecting(player, npc, ExpectingType.BED)) return;

            if (npc.isInteracting()) {
                if (!npc.getInteractingWith().equals(player.getUniqueId())) {
                    messages.send(player, Messages.Message.INTERACT_FAIL_INTERACTING);
                    return;
                } else if (npc.isFollowing()) {
                    messages.send(player, npc, Messages.Message.FOLLOW_ME_STOP);
                    npc.stopInteracting();
                    return;
                } else if (npc.isStayingInPlace()) {
                    messages.send(player, npc, Messages.Message.STAY_HERE_STOP);
                    npc.stopInteracting();
                    npc.stopStayingInPlace();
                    return;
                } else if (npc.getInteractType() != null && npc.getInteractType().isTalking()) {
                    // Player is in talk mode with this villager - allow other interactions
                    // Don't return, continue to process the click action
                } else {
                    // Otherwise, is in GUI mode, do nothing
                    return;
                }
            }

            if (villager.isTrading()) {
                messages.send(player, Messages.Message.INTERACT_FAIL_TRADING);
                return;
            }

            // Get GUI configuration
            GUIConfig guiConfig = plugin.getGuiConfig();
            
            // Determine which action to perform based on click type
            GUIConfig.ClickAction clickAction;
            if (player.isSneaking()) {
                clickAction = guiConfig.getShiftRightClickAction();
            } else {
                clickAction = guiConfig.getRightClickAction();
            }
            
            // Handle the configured action
            switch (clickAction) {
                case MAIN_GUI:
                    // Check if main GUI is enabled
                    if (!guiConfig.isMainGUIEnabled()) {
                        // GUI is disabled, do nothing
                        return;
                    }
                    // Open custom GUI
                    new MainGUI(plugin, npc, player);
                    // Set interacting with id
                    npc.setInteractingWithAndType(player.getUniqueId(), InteractType.GUI);
                    break;
                    
                case TRADE:
                    // Open vanilla trade GUI
                    if (!player.hasPermission(guiConfig.getTradePermission())) {
                        player.sendMessage(ChatColor.RED + "You don't have permission to trade!");
                        return;
                    }
                    // Use filtered trading system if enabled
                    if (plugin.getTradingConfig().isEnabled()) {
                        plugin.getTradeWrapper().openFilteredTrading(npc, player);
                    } else {
                        // Use the native trading system
                        player.openMerchant(villager, true);
                    }
                    break;
                    
                case TALK:
                    // Toggle talk mode - if already talking to this villager, stop; otherwise start
                    if (plugin.getTalkModeManager().isInTalkMode(player)) {
                        TalkModeManager.TalkSession session = plugin.getTalkModeManager().getTalkSession(player);
                        if (session != null && session.getVillagerId().equals(npc.getUniqueId())) {
                            // Already talking to this villager, so stop talk mode
                            plugin.getTalkModeManager().endTalkMode(player, true);
                        } else {
                            // Talking to a different villager, switch to this one
                            plugin.getTalkModeManager().endTalkMode(player, false);
                            if (plugin.getTalkModeManager().startTalkMode(player, npc)) {
                                npc.setInteractingWithAndType(player.getUniqueId(), InteractType.TALKING);
                            }
                        }
                    } else {
                        // Not in talk mode, start it
                        if (plugin.getTalkModeManager().startTalkMode(player, npc)) {
                            npc.setInteractingWithAndType(player.getUniqueId(), InteractType.TALKING);
                        }
                    }
                    break;
                    
                case NONE:
                    // Do nothing
                    break;
                    
                default:
                    // DEFAULT - not applicable for right click
                    break;
            }
        }));

        return true;
    }

    // All this is checked in the invoker method.
    private void handleRename(Player player, IVillagerNPC npc, ItemStack item) {
        if (plugin.getInventoryListeners().notAllowedToModifyInventoryOrName(player, npc, Config.WHO_CAN_MODIFY_VILLAGER_NAME, "realisticvillagers.bypass.rename")) {
            plugin.getMessages().send(player, Messages.Message.INTERACT_FAIL_RENAME_NOT_ALLOWED);
            return;
        }

        @SuppressWarnings("DataFlowIssue") String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        if (name.length() < 3) return;
        
        // Prevent extremely long names that cause NBT serialization issues
        if (name.length() > 64) {
            plugin.getLogger().warning(String.format(
                "Player %s attempted to set villager name that's too long (%d chars), truncating: %s...",
                player.getName(), name.length(), name.substring(0, Math.min(20, name.length()))
            ));
            name = name.substring(0, 64);
        }

        npc.setVillagerName(name);

        // Refresh skin.
        plugin.getTracker().refreshNPCSkin(npc.bukkit(), false);

        player.getInventory().removeItem(new ItemBuilder(item.clone())
                .setAmount(1)
                .build());
    }

    private boolean isExpecting(Player player, @NotNull IVillagerNPC npc, ExpectingType checkType) {
        if (!npc.isExpecting()) return false;

        ExpectingType expecting = npc.getExpectingType();
        if (expecting != checkType) return false;

        Messages messages = plugin.getMessages();

        if (!npc.getExpectingFrom().equals(player.getUniqueId())) {
            messages.send(player, Messages.Message.valueOf("INTERACT_FAIL_EXPECTING_" + expecting + "_FROM_SOMEONE"));
            return true;
        }

        if (!player.isSneaking()) {
            messages.send(player, Messages.Message.valueOf("INTERACT_FAIL_EXPECTING_" + expecting + "_FROM_YOU"));
            return true;
        }

        messages.send(player, npc, Messages.Message.valueOf((expecting.isGift() ? "GIFT_EXPECTING" : "SET_HOME") + "_FAIL"));
        npc.stopExpecting();
        plugin.getCooldownManager().removeCooldown(player, checkType.name().toLowerCase(Locale.ROOT));
        return true;
    }

    private boolean preventChangeSkinItemUse(@Nullable Cancellable cancellable, ItemStack item) {
        ItemMeta meta;
        if (item == null || (meta = item.getItemMeta()) == null) return false;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(plugin.getSkinDataKey(), PersistentDataType.STRING)) {
            if (cancellable != null) cancellable.setCancelled(true);
            return true;
        }

        return false;
    }

    private void handleChangeSkinItem(Player player, @NotNull IVillagerNPC npc, @NotNull ItemStack handItem) {
        ItemMeta meta = handItem.getItemMeta();
        Validate.notNull(meta);

        Messages messages = plugin.getMessages();
        VillagerTracker tracker = plugin.getTracker();
        LivingEntity living = npc.bukkit();

        VillagerTracker.SkinRelatedData relatedData = tracker.getRelatedData(living, "none");
        TextureProperty property = relatedData.property();

        if (property != null && property.getName().equals("error")) {
            messages.send(player, Messages.Message.SKIN_ERROR);
            plugin.getLogger().severe(property.getValue());
            return;
        }

        String skinData = meta.getPersistentDataContainer().get(plugin.getSkinDataKey(), PersistentDataType.STRING);
        if (skinData == null || skinData.isEmpty()) return;

        String[] data = skinData.split(":");
        if (data.length != 2) return;

        String sex = data[0];
        boolean isMale = sex.equals("male");
        String sexFormatted = (isMale ? Config.MALE : Config.FEMALE).asString();

        if (!sex.equalsIgnoreCase(npc.getSex())) {
            messages.send(player, Messages.Message.SKIN_DIFFERENT_SEX, string -> string.replace("%sex%", sexFormatted));
            return;
        }

        int id = Integer.parseInt(data[1]);
        if (id == npc.getSkinTextureId()) {
            messages.send(player, Messages.Message.SKIN_VILLAGER_SAME_SKIN);
            return;
        }

        boolean isAdult = !(living instanceof Villager villager) || villager.isAdult(), forBabies = relatedData.storage().getBoolean("none." + id + ".for-babies");
        if ((isAdult && forBabies) || (!isAdult && !forBabies)) {
            messages.send(player, Messages.Message.SKIN_DIFFERENT_AGE_STAGE, string -> string.replace("%age-stage%", (forBabies ? Config.KID : Config.ADULT).asString()));
            return;
        }

        // Here, we change the id of the villager, so then we can check if the skin exists.
        npc.setSkinTextureId(id);

        int skinId = tracker.getRelatedData(living, "none", false).id();
        if (skinId == -1) {
            messages.send(player, Messages.Message.SKIN_TEXTURE_NOT_FOUND);
            return;
        }

        messages.send(player, Messages.Message.SKIN_DISGUISED, string -> string
                .replace("%id%", String.valueOf(skinId))
                .replace("%sex%", sexFormatted)
                .replace("%profession%", plugin.getProfessionFormatted(PluginUtils.getProfessionOrType(living), isMale))
                .replace("%age-stage%", isAdult ? Config.ADULT.asString() : Config.KID.asString()));

        tracker.refreshNPCSkin(living, false);

        player.getInventory().removeItem(new ItemBuilder(handItem.clone())
                .setAmount(1)
                .build());
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(@NotNull EntityDamageEvent event) {
        tryToDefendPlayer(event);

        if (!(event.getEntity() instanceof AbstractVillager villager)) return;
        if (plugin.getTracker().isInvalid(villager, true)) return;

        Optional<IVillagerNPC> optional = plugin.getConverter().getNPC(villager);

        IVillagerNPC npc = optional.orElse(null);
        if (npc == null) return;
        if (npc.isFishing()) npc.toggleFishing();
        
        // Handle player violence against villagers for alert system
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player player) {
            handlePlayerVillagerViolence(npc, player, event.getFinalDamage() >= villager.getHealth());
        }

        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            if ((XReflection.MINOR_NUMBER != 20 && XReflection.PATCH_NUMBER != 5)
                    && event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION
                    && !villager.isAdult()
                    && !Config.DISABLE_SKINS.asBool()
                    && !Config.INCREASE_BABY_SCALE.asBool()
                    && !villager.getLocation().getBlock().getType().isSolid()) {
                // Prevent baby villagers suffocating when their hitbox remain small with enabled skins.
                event.setCancelled(true);
            }
            return;
        }

        if (byEntity.getDamager() instanceof Firework firework
                && firework.getShooter() instanceof Villager
                && !Config.VILLAGER_CROSSBOW_FIREWORK_DAMAGES_OTHER_VILLAGERS.asBool()) {
            event.setCancelled(true);
            return;
        }

        boolean alive = villager.getHealth() - event.getFinalDamage() > 0.0d;

        // Don't send messages if villager died.
        if (villager.getTarget() == null && byEntity.getDamager() instanceof Player player && alive) {
            plugin.getMessages().send(player, npc, Messages.Message.ON_HIT);
        }

        if (!npc.isDamageSourceBlocked()) return;

        try {
            EntityDamageEvent.DamageModifier modifier = EntityDamageEvent.DamageModifier.BLOCKING;
            double base = event.getDamage(EntityDamageEvent.DamageModifier.BASE);
            ((Map<EntityDamageEvent.DamageModifier, Double>) MODIFIERS.invoke(event)).put(modifier, base);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    private void tryToDefendPlayer(@NotNull EntityDamageEvent event) {
        if (!Config.VILLAGER_DEFEND_ATTACK_PLAYERS.asBool()) return;

        if (!(event.getEntity() instanceof Player player)
                || !(event instanceof EntityDamageByEntityEvent byEntity)
                || !(byEntity.getDamager() instanceof Player damager)) return;

        for (Entity nearby : player.getNearbyEntities(16.0d, 16.0d, 16.0d)) {
            if (!(nearby instanceof Villager villager)) continue;

            Optional<IVillagerNPC> optional = plugin.getConverter().getNPC(villager);

            // If the NPC can't attack or the damager is part of the family of the NPC, continue.
            IVillagerNPC npc = optional.orElse(null);
            if (npc == null
                    || !npc.canAttack()
                    || npc.isFamily(damager.getUniqueId(), true)) return;

            if (npc.isFamily(player.getUniqueId(), true) && Config.VILLAGER_DEFEND_FAMILY_MEMBER.asBool()) {
                npc.attack(damager);
                continue;
            }

            Raid raid;
            if (player.hasPotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE)
                    || ((raid = player.getWorld().locateNearestRaid(player.getLocation(), 5)) != null
                    && raid.getHeroes().contains(player.getUniqueId()))
                    && Config.VILLAGER_DEFEND_HERO_OF_THE_VILLAGE.asBool()) {
                npc.attack(damager);
                continue;
            }

            if (player.getUniqueId().equals(npc.getInteractingWith()) && Config.VILLAGER_DEFEND_FOLLOWING_PLAYER.asBool()) {
                npc.attack(damager);
            }
        }
    }
    
    /**
     * Handles player violence against villagers for the alert system.
     * 
     * @param npc The villager NPC that was attacked
     * @param player The player who attacked the villager
     * @param willKill Whether this damage will kill the villager
     */
    private void handlePlayerVillagerViolence(@NotNull IVillagerNPC npc, @NotNull Player player, boolean willKill) {
        // Only trigger alerts when threat-based equipment is enabled
        if (!Config.THREAT_BASED_EQUIPMENT.asBool()) return;
        
        if (willKill && Config.ALERT_ON_PLAYER_KILL_VILLAGER.asBool()) {
            // Player is killing a villager - HIGH intensity village-wide alert
            me.matsubara.realisticvillagers.util.EquipmentManager.onPlayerKillsVillager(npc, player);
        } else if (Config.ALERT_ON_PLAYER_DAMAGE_VILLAGER.asBool()) {
            // Player is damaging a villager - MEDIUM intensity local alert
            me.matsubara.realisticvillagers.util.EquipmentManager.onPlayerDamagesVillager(npc, player);
        }
    }
}