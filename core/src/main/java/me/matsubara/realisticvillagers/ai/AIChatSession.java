package me.matsubara.realisticvillagers.ai;

import lombok.Getter;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Represents an active AI chat session between a player and villager.
 * EXPERIMENTAL FEATURE - Subject to change
 */
@Getter
public class AIChatSession {
    
    private final UUID playerUUID;
    private final UUID villagerUUID;
    private final String playerName;
    private final String villagerName;
    private final long startTime;
    
    public AIChatSession(Player player, IVillagerNPC npc) {
        this.playerUUID = player.getUniqueId();
        this.villagerUUID = npc.getUniqueId();
        this.playerName = player.getName();
        this.villagerName = npc.getVillagerName();
        this.startTime = System.currentTimeMillis();
    }
    
    public boolean isExpired(long timeoutMinutes) {
        return System.currentTimeMillis() - startTime > (timeoutMinutes * 60 * 1000);
    }
}