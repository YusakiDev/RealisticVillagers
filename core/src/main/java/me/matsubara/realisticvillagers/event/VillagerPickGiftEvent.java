package me.matsubara.realisticvillagers.event;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class VillagerPickGiftEvent extends VillagerEvent implements Cancellable {

    private final Player gifter;
    private final ItemStack gift;
    private boolean cancelled = false;

    private static final HandlerList handlers = new HandlerList();

    public VillagerPickGiftEvent(IVillagerNPC npc, Player gifter, ItemStack gift) {
        super(npc);
        this.gift = gift;
        this.gifter = gifter;
    }

    public ItemStack getGift() {
        return gift;
    }

    public Player getGifter() {
        return gifter;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    @SuppressWarnings("unused")
    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}