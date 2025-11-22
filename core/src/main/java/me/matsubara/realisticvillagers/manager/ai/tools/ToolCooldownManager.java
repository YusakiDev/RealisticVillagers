package me.matsubara.realisticvillagers.manager.ai.tools;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cooldown manager for AI tools.
 * Tracks: Villager UUID -> Tool Name -> Player UUID -> Last Use Timestamp
 * <p>
 * This class is designed to be Folia-compatible and thread-safe.
 */
public class ToolCooldownManager {

    // Villager -> Tool -> Player -> Timestamp
    private final Map<UUID, Map<String, Map<UUID, Long>>> cooldowns = new ConcurrentHashMap<>();

    /**
     * Checks if a tool is on cooldown for a specific villager-player pair.
     *
     * @param villagerUUID    the villager's UUID
     * @param toolName        the tool name
     * @param playerUUID      the player's UUID
     * @param cooldownSeconds the cooldown duration in seconds
     * @return true if on cooldown, false otherwise
     */
    public boolean isOnCooldown(@NotNull UUID villagerUUID,
                                @NotNull String toolName,
                                @NotNull UUID playerUUID,
                                int cooldownSeconds) {
        if (cooldownSeconds <= 0) return false;

        Map<String, Map<UUID, Long>> villagerCooldowns = cooldowns.get(villagerUUID);
        if (villagerCooldowns == null) return false;

        Map<UUID, Long> toolCooldowns = villagerCooldowns.get(toolName);
        if (toolCooldowns == null) return false;

        Long lastUse = toolCooldowns.get(playerUUID);
        if (lastUse == null) return false;

        long now = System.currentTimeMillis();
        long cooldownMillis = cooldownSeconds * 1000L;

        return (now - lastUse) < cooldownMillis;
    }

    /**
     * Sets a cooldown for a tool.
     *
     * @param villagerUUID the villager's UUID
     * @param toolName     the tool name
     * @param playerUUID   the player's UUID
     */
    public void setCooldown(@NotNull UUID villagerUUID,
                           @NotNull String toolName,
                           @NotNull UUID playerUUID) {
        cooldowns
                .computeIfAbsent(villagerUUID, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(toolName, k -> new ConcurrentHashMap<>())
                .put(playerUUID, System.currentTimeMillis());
    }

    /**
     * Gets the remaining cooldown in seconds.
     *
     * @param villagerUUID    the villager's UUID
     * @param toolName        the tool name
     * @param playerUUID      the player's UUID
     * @param cooldownSeconds the cooldown duration in seconds
     * @return remaining seconds, or 0 if not on cooldown
     */
    public int getRemainingCooldown(@NotNull UUID villagerUUID,
                                    @NotNull String toolName,
                                    @NotNull UUID playerUUID,
                                    int cooldownSeconds) {
        if (cooldownSeconds <= 0) return 0;

        Map<String, Map<UUID, Long>> villagerCooldowns = cooldowns.get(villagerUUID);
        if (villagerCooldowns == null) return 0;

        Map<UUID, Long> toolCooldowns = villagerCooldowns.get(toolName);
        if (toolCooldowns == null) return 0;

        Long lastUse = toolCooldowns.get(playerUUID);
        if (lastUse == null) return 0;

        long now = System.currentTimeMillis();
        long cooldownMillis = cooldownSeconds * 1000L;
        long remaining = cooldownMillis - (now - lastUse);

        return Math.max(0, (int) (remaining / 1000));
    }

    /**
     * Clears all cooldowns for a specific villager.
     * Useful when a villager is removed or dies.
     *
     * @param villagerUUID the villager's UUID
     */
    public void clearVillagerCooldowns(@NotNull UUID villagerUUID) {
        cooldowns.remove(villagerUUID);
    }

    /**
     * Clears all cooldowns (useful for plugin reload).
     */
    public void clearAllCooldowns() {
        cooldowns.clear();
    }
}
