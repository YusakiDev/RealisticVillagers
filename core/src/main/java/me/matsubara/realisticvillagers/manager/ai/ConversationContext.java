package me.matsubara.realisticvillagers.manager.ai;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.Location;
import org.bukkit.Raid;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds context information for AI conversations.
 */
public class ConversationContext {

    private final ConfigurationSection config;
    private final PersonalityBuilder personalityBuilder;

    public ConversationContext(@NotNull ConfigurationSection config, @NotNull PersonalityBuilder personalityBuilder) {
        this.config = config;
        this.personalityBuilder = personalityBuilder;
    }

    /**
     * Builds the system prompt for the AI conversation.
     *
     * @param player The player talking to the villager
     * @param npc The villager NPC
     * @return The system prompt for the AI
     */
    public @NotNull String buildSystemPrompt(@NotNull Player player, @NotNull IVillagerNPC npc) {
        StringBuilder prompt = new StringBuilder();

        // World context
        prompt.append("You are a villager character in the Minecraft world. ");
        prompt.append("You have full knowledge of Minecraft blocks, items, creatures, mechanics, and gameplay. ");
        prompt.append("However, you must ROLEPLAY as if this is your real world - never mention 'game', 'players', 'server', etc. ");
        prompt.append("Refer to players as 'travelers', 'visitors', or by their names. Speak as if you actually live here.\n\n");

        // Base personality
        prompt.append(personalityBuilder.buildPersonality(npc));

        // Current context
        prompt.append("\n\nCurrent context:");
        String gender = npc.isFemale() ? "female" : "male";
        prompt.append("\n- You are a ").append(gender).append(" villager named ").append(npc.getVillagerName());

        // Village state context
        if (config.getBoolean("context.include-village-state", true)) {
            appendVillageStateContext(prompt, npc);
        }

        // Villager's life context
        if (config.getBoolean("context.include-villager-life", true)) {
            appendVillagerLifeContext(prompt, npc);
        }

        // Relationship context
        if (config.getBoolean("context.include-reputation", true)) {
            appendReputationContext(prompt, player, npc);
        }

        // Behavioral instructions
        prompt.append("\n\nCRITICAL RULES:");
        prompt.append("\n- RESPOND IN THE SAME LANGUAGE the player used to speak to you");
        prompt.append("\n- Keep replies concise and conversational; one or two short sentences are fine when you need detail");
        prompt.append("\n- Speak like a real villager living in this world");
        prompt.append("\n- Mention a concrete detail each time (weather, work, nearby sights, sounds, or recent happenings) when it fits naturally");
        prompt.append("\n- Lean on your profession's knowledge, tools, and daily routines instead of generic phrases");
        prompt.append("\n- Vary how you address the traveler; reuse the same greeting only when it makes sense");
        prompt.append("\n- ABSOLUTELY FORBIDDEN: *actions*, *emotions*, *movements*, *gestures*, *looks*, *smiles*, *nods*");
        prompt.append("\n- ABSOLUTELY FORBIDDEN: Any text in *asterisks* or describing what you do");
        prompt.append("\n- ONLY SPEAK: What the villager would say out loud, nothing else");
        prompt.append("\n- Stay in direct speech; no stage directions, narration, or prose outside dialogue");

        // Note: With native tool calling, we don't need strict JSON instructions
        // The API provider handles tool calling automatically

        return prompt.toString();
    }

    /**
     * Appends reputation/relationship context to the prompt.
     */
    private void appendReputationContext(@NotNull StringBuilder prompt, @NotNull Player player, @NotNull IVillagerNPC npc) {
        UUID playerUUID = player.getUniqueId();
        int reputation = npc.getReputation(playerUUID);

        // Check family relationships
        boolean isPartner = npc.isPartner(playerUUID);
        // Check if player is a child of this villager
        boolean isChild = npc.getChildrens().stream().anyMatch(child -> child.getUniqueId().equals(playerUUID));
        // Check if player is a parent of this villager
        boolean isParent = (npc.getFather() != null && npc.getFather().getUniqueId().equals(playerUUID))
                || (npc.getMother() != null && npc.getMother().getUniqueId().equals(playerUUID));
        boolean isFamily = isPartner || isChild || isParent;

        String relationshipLevel = personalityBuilder.getRelationshipLevel(reputation, isFamily, isPartner);
        String tone = personalityBuilder.getRelationshipTone(relationshipLevel);

        prompt.append("\n\nYour relationship with ").append(player.getName()).append(": ");

        if (isPartner) {
            prompt.append("This is your spouse/partner. You love them deeply and speak with warmth and affection.");
        } else if (isChild) {
            prompt.append("This is your child. You are protective and caring towards them.");
        } else if (isParent) {
            prompt.append("This is your parent. You respect and care for them.");
        } else {
            prompt.append("You have met them before. ");
            prompt.append("Your reputation with them is ").append(reputation).append(". ");
            prompt.append("Speak in a tone that is ").append(tone).append(".");
        }
    }

    /**
     * Appends village state context to the prompt.
     */
    private void appendVillageStateContext(@NotNull StringBuilder prompt, @NotNull IVillagerNPC npc) {
        Location location = npc.bukkit().getLocation();
        World world = location.getWorld();
        if (world == null) return;

        // Time of day
        long time = world.getTime();
        String timeOfDay = getTimeDescription(time);
        prompt.append("\n- Time: ").append(timeOfDay);

        // Weather
        String weather = getWeatherDescription(world);
        prompt.append("\n- Weather: ").append(weather);

        // Location
        prompt.append("\n- Location: ").append(world.getName()).append(" at ")
                .append(location.getBlockX()).append(", ")
                .append(location.getBlockY()).append(", ")
                .append(location.getBlockZ());

        // Current activity
        String activity = npc.getActivityName("idle");
        prompt.append("\n- Current activity: ").append(activity);

        // Combat state
        String combatState = getCombatState(npc);
        prompt.append("\n- Combat state: ").append(combatState);

        // Raid status
        if (npc.isInsideRaid()) {
            prompt.append("\n- WARNING: The village is under attack by raiders! You are worried and frightened.");
        }
    }

    /**
     * Appends villager's personal life context to the prompt.
     */
    private void appendVillagerLifeContext(@NotNull StringBuilder prompt, @NotNull IVillagerNPC npc) {
        prompt.append("\n\nYour personal life:");

        // Family
        IVillagerNPC partner = npc.getPartner();
        if (partner != null) {
            prompt.append("\n- You are married to ").append(partner.getVillagerName());
        }

        List<IVillagerNPC> children = npc.getChildrens();
        if (!children.isEmpty()) {
            prompt.append("\n- You have ").append(children.size()).append(" child");
            if (children.size() > 1) prompt.append("ren");
        }

        // Pregnancy
        if (npc.isProcreating()) {
            prompt.append("\n- You are expecting a baby and feeling excited");
        }

        // Job
        if (npc.bukkit() instanceof Villager villager) {
            Villager.Profession profession = villager.getProfession();
            if (profession != Villager.Profession.NONE && profession != Villager.Profession.NITWIT) {
                prompt.append("\n- You work as a ").append(profession.name().toLowerCase());
            }
        }
    }

    /**
     * Gets a human-readable time of day description.
     */
    private @NotNull String getTimeDescription(long time) {
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

    /**
     * Gets a human-readable weather description.
     */
    private @NotNull String getWeatherDescription(@NotNull World world) {
        if (world.isThundering()) {
            return "thunderstorm";
        } else if (world.hasStorm()) {
            return "raining";
        } else {
            return "clear";
        }
    }

    /**
     * Gets the current combat state of the villager.
     */
    private @NotNull String getCombatState(@NotNull IVillagerNPC npc) {
        if (npc.isFighting()) {
            if (npc.canAttack()) {
                return "fighting (armed and dangerous)";
            } else {
                return "fighting (but unarmed, likely fleeing)";
            }
        }
        return "peaceful";
    }
}
