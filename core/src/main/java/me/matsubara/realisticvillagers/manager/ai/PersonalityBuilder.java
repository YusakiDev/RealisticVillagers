package me.matsubara.realisticvillagers.manager.ai;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Builds personality prompts for villagers based on their profession and individual traits.
 */
public class PersonalityBuilder {

    private final ConfigurationSection config;
    private final Random random = new Random();

    public PersonalityBuilder(@NotNull ConfigurationSection config) {
        this.config = config;
    }

    /**
     * Builds a personality description for the given villager.
     *
     * @param npc The villager NPC
     * @return A string describing the villager's personality
     */
    public @NotNull String buildPersonality(@NotNull IVillagerNPC npc) {
        String profession = getProfessionName(npc);
        ConfigurationSection personalitySection = config.getConfigurationSection("personalities." + profession);

        if (personalitySection == null) {
            personalitySection = config.getConfigurationSection("personalities.default");
        }

        if (personalitySection == null) {
            return "You are a friendly villager who enjoys talking with visitors.";
        }

        List<String> traits = personalitySection.getStringList("traits");
        String knowledge = personalitySection.getString("knowledge", "general topics");

        // Get or assign individual trait
        String individualTrait = getIndividualTrait(npc);

        StringBuilder personality = new StringBuilder();
        personality.append("You are a ").append(profession).append(" villager named ").append(npc.getVillagerName()).append(". ");

        if (!traits.isEmpty()) {
            personality.append("You are ");
            for (int i = 0; i < traits.size(); i++) {
                personality.append(traits.get(i));
                if (i < traits.size() - 2) {
                    personality.append(", ");
                } else if (i == traits.size() - 2) {
                    personality.append(" and ");
                }
            }
            personality.append(". ");
        }

        if (individualTrait != null && !individualTrait.isEmpty()) {
            personality.append("Your personality is especially ").append(individualTrait).append(". ");
        }

        personality.append("You are knowledgeable about ").append(knowledge).append(". ");

        return personality.toString();
    }

    /**
     * Gets a relationship-based tone modifier.
     *
     * @param relationshipLevel The relationship level (enemy, neutral, friend, family)
     * @return A tone description
     */
    public @NotNull String getRelationshipTone(@NotNull String relationshipLevel) {
        ConfigurationSection relationshipSection = config.getConfigurationSection("relationship." + relationshipLevel);
        if (relationshipSection != null) {
            return relationshipSection.getString("tone", "polite");
        }
        return "polite";
    }

    /**
     * Gets the profession name from the villager.
     *
     * @param npc The villager NPC
     * @return The profession name in lowercase
     */
    private @NotNull String getProfessionName(@NotNull IVillagerNPC npc) {
        if (npc.bukkit() instanceof Villager villager) {
            return villager.getProfession().name().toLowerCase();
        }
        return "none";
    }

    /**
     * Gets or randomly assigns an individual trait for the villager.
     * In a full implementation, this would be stored in the villager's persistent data.
     *
     * @param npc The villager NPC
     * @return An individual trait
     */
    private String getIndividualTrait(@NotNull IVillagerNPC npc) {
        // Try to get stored trait first (would be implemented with PDC or custom storage)
        // For now, we'll deterministically select based on UUID hash
        List<String> availableTraits = config.getStringList("individual-traits");
        if (availableTraits.isEmpty()) {
            return null;
        }

        // Use UUID hashcode for deterministic but unique selection
        int index = Math.abs(npc.getUniqueId().hashCode() % availableTraits.size());
        return availableTraits.get(index);
    }

    /**
     * Determines the relationship level based on reputation.
     *
     * @param reputation The reputation value
     * @param isFamily Whether the player is family
     * @param isPartner Whether the player is a partner
     * @return The relationship level (enemy, neutral, friend, family)
     */
    public @NotNull String getRelationshipLevel(int reputation, boolean isFamily, boolean isPartner) {
        if (isFamily || isPartner) {
            return "family";
        } else if (reputation >= 10) {
            return "friend";
        } else if (reputation < 0) {
            return "enemy";
        } else {
            return "neutral";
        }
    }
}
