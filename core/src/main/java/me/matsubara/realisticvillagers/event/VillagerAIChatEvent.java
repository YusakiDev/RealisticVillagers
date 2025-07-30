package me.matsubara.realisticvillagers.event;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a player uses the experimental AI chat feature with a villager.
 * EXPERIMENTAL FEATURE - Subject to change
 */
@Getter
@Setter
public class VillagerAIChatEvent extends VillagerEvent implements Cancellable {
    
    private static final HandlerList handlers = new HandlerList();
    
    private final Player player;
    private final String message;
    private String response;
    private boolean cancelled;
    
    public VillagerAIChatEvent(@NotNull IVillagerNPC npc, @NotNull Player player, 
                              @NotNull String message, @NotNull String response) {
        super(npc);
        this.player = player;
        this.message = message;
        this.response = response;
        this.cancelled = false;
    }
    
    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }
    
    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}