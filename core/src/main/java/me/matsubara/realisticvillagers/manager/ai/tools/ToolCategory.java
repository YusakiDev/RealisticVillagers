package me.matsubara.realisticvillagers.manager.ai.tools;

/**
 * Categories of AI tools for organization and display.
 */
public enum ToolCategory {
    MOVEMENT("Movement", "Tools that control villager movement"),
    ITEMS("Items", "Tools for giving and checking items"),
    INTERACTION("Interaction", "Tools for villager interactions"),
    HOME("Home", "Tools related to villager homes"),
    INFORMATION("Information", "Tools that provide information");

    private final String displayName;
    private final String description;

    ToolCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * @return the display name of this category
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return the description of this category
     */
    public String getDescription() {
        return description;
    }
}
