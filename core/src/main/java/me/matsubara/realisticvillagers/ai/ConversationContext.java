package me.matsubara.realisticvillagers.ai;

import lombok.Builder;
import lombok.Getter;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Captures the context of a conversation including world state and relationships.
 * EXPERIMENTAL FEATURE - Subject to change
 */
@Getter
@Builder
public class ConversationContext {
    
    private final UUID villagerUUID;
    private final UUID playerUUID;
    private final String villagerName;
    private final String playerName;
    private final int reputation;
    private final boolean isMarried;
    private final boolean isFamily;
    private final String worldTime;
    private final String weather;
    private final String location;
    private final List<String> nearbyEntities;
    private final String villagerActivity;
    private final String combatState;
    private final Villager.Profession profession;
    private final int villagerLevel;
    private final String gender;
    private final LocalDateTime timestamp;
    
    @Builder.Default
    private final List<Message> conversationHistory = new ArrayList<>();
    
    /**
     * Creates a context from the current game state
     */
    public static ConversationContext fromGameState(@NotNull IVillagerNPC npc, @NotNull Player player) {
        Villager villager = (Villager) npc.bukkit();
        Location loc = villager.getLocation();
        World world = loc.getWorld();
        
        // Get reputation from reputation manager if available, otherwise use vanilla
        int reputation = npc.getReputation(player.getUniqueId());
        try {
            // Try to use ReputationManager if available
            Object plugin = npc.getClass().getMethod("getPlugin").invoke(npc);
            if (plugin != null) {
                Object repManager = plugin.getClass().getMethod("getReputationManager").invoke(plugin);
                if (repManager != null) {
                    reputation = (int) repManager.getClass()
                        .getMethod("getTotalReputation", IVillagerNPC.class, Player.class)
                        .invoke(repManager, npc, player);
                }
            }
        } catch (Exception ignored) {
            // Fall back to vanilla reputation if ReputationManager fails
        }
        
        return ConversationContext.builder()
            .villagerUUID(villager.getUniqueId())
            .playerUUID(player.getUniqueId())
            .villagerName(npc.getVillagerName())
            .playerName(player.getName())
            .reputation(reputation)
            .isMarried(npc.isPartner(player.getUniqueId()))
            .isFamily(npc.isFamily(player.getUniqueId(), true))
            .gender(npc.isFemale() ? "female" : "male")
            .worldTime(getTimeDescription(world.getTime()))
            .weather(getWeatherDescription(world))
            .location(getLocationDescription(loc))
            .nearbyEntities(getNearbyEntitiesDescription(villager))
            .villagerActivity(npc.getActivityName("idle"))
            .combatState(getCombatState(npc))
            .profession(villager.getProfession())
            .villagerLevel(villager.getVillagerLevel())
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    private static String getTimeDescription(long time) {
        // Convert Minecraft time to readable format
        int hours = (int) ((time / 1000 + 6) % 24);
        int minutes = (int) ((time % 1000) * 60 / 1000);
        
        String period;
        if (hours >= 6 && hours < 12) {
            period = "morning";
        } else if (hours >= 12 && hours < 17) {
            period = "afternoon";
        } else if (hours >= 17 && hours < 20) {
            period = "evening";
        } else {
            period = "night";
        }
        
        return String.format("%02d:%02d (%s)", hours, minutes, period);
    }
    
    private static String getWeatherDescription(@NotNull World world) {
        if (world.isThundering()) {
            return "thunderstorm";
        } else if (world.hasStorm()) {
            return "raining";
        } else {
            return "clear";
        }
    }
    
    private static String getLocationDescription(@NotNull Location loc) {
        return String.format("%s at %d, %d, %d",
            loc.getWorld().getName(),
            loc.getBlockX(),
            loc.getBlockY(),
            loc.getBlockZ()
        );
    }
    
    private static List<String> getNearbyEntitiesDescription(@NotNull Entity center) {
        List<String> descriptions = new ArrayList<>();
        List<Entity> nearby = center.getNearbyEntities(10, 10, 10);
        
        int villagerCount = 0;
        int playerCount = 0;
        int animalCount = 0;
        int monsterCount = 0;
        
        for (Entity entity : nearby) {
            if (entity instanceof Villager) villagerCount++;
            else if (entity instanceof Player) playerCount++;
            else if (entity.getType().name().contains("ZOMBIE") || 
                     entity.getType().name().contains("SKELETON") ||
                     entity.getType().name().contains("CREEPER")) monsterCount++;
            else if (entity.getType().isAlive()) animalCount++;
        }
        
        if (villagerCount > 0) descriptions.add(villagerCount + " other villagers");
        if (playerCount > 0) descriptions.add(playerCount + " players");
        if (animalCount > 0) descriptions.add(animalCount + " animals");
        if (monsterCount > 0) descriptions.add(monsterCount + " hostile mobs");
        
        return descriptions;
    }
    
    /**
     * Gets the current combat state of the villager
     */
    private static String getCombatState(@NotNull IVillagerNPC npc) {
        if (npc.isFighting()) {
            if (npc.canAttack()) {
                return "fighting (armed and dangerous)";
            } else {
                return "fighting (but unarmed, likely fleeing)";
            }
        }
        
        // Check if villager is on alert due to threat-based equipment system
        if (me.matsubara.realisticvillagers.util.EquipmentManager.isAlerted(npc)) {
            if (npc.canAttack()) {
                return "on high alert (armed and ready for combat)";
            } else {
                return "on alert (nervous but unarmed)";
            }
        }
        
        // Check if villager is in panic state
        try {
            java.lang.reflect.Method isPanicking = npc.getClass().getMethod("isPanicking");
            if ((Boolean) isPanicking.invoke(npc)) {
                return "panicking (scared and trying to escape)";
            }
        } catch (Exception ignored) {
            // Reflection failed, ignore
        }
        
        return "peaceful";
    }
    
    public void addMessage(String role, String content) {
        // Limit conversation history to prevent NBT overflow
        final int MAX_HISTORY_SIZE = 20; // Keep only recent messages
        final int MAX_MESSAGE_LENGTH = 1000; // Limit individual message size
        
        // Truncate content if too long
        String truncatedContent = content;
        if (content.length() > MAX_MESSAGE_LENGTH) {
            truncatedContent = content.substring(0, MAX_MESSAGE_LENGTH) + "... [truncated]";
        }
        
        conversationHistory.add(new Message(role, truncatedContent, LocalDateTime.now()));
        
        // Remove oldest messages if history gets too large
        while (conversationHistory.size() > MAX_HISTORY_SIZE) {
            conversationHistory.remove(0);
        }
    }
    
    public String getContextPrompt() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Current context:\n");
        prompt.append("- You are a ").append(gender).append(" villager named ").append(villagerName).append("\n");
        prompt.append("- Time: ").append(worldTime).append("\n");
        prompt.append("- Weather: ").append(weather).append("\n");
        prompt.append("- Location: ").append(location).append("\n");
        prompt.append("- Current activity: ").append(villagerActivity).append("\n");
        prompt.append("- Combat state: ").append(combatState).append("\n");
        
        if (!nearbyEntities.isEmpty()) {
            prompt.append("- Nearby: ").append(String.join(", ", nearbyEntities)).append("\n");
        }
        
        // Note: In natural chat mode, multiple players may be talking to you
        // The conversation history will show who said what with [PlayerName (rep:X)]: message format
        prompt.append("\nYou may be talking to multiple players at once. ");
        prompt.append("Pay attention to who is speaking and their current reputation by looking at [PlayerName (rep:X)] in messages.\n");
        
        // Show relationship info for the original player who created this context (marriage/family status)
        prompt.append("\nSpecial relationships:\n");
        if (isMarried) {
            prompt.append("- You are married to ").append(playerName).append("\n");
        }
        if (isFamily) {
            prompt.append("- ").append(playerName).append(" is part of your family\n");
        }
        if (!isMarried && !isFamily) {
            prompt.append("- No special relationships currently\n");
        }
        
        return prompt.toString();
    }
    
    @Getter
    public static class Message {
        private final String role;
        private final String content;
        private final LocalDateTime timestamp;
        
        public Message(String role, String content, LocalDateTime timestamp) {
            this.role = role;
            this.content = content;
            this.timestamp = timestamp;
        }
    }
}