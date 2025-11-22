package me.matsubara.realisticvillagers.util;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

/**
 * Helper class to identify combat equipment for sharing during alerts
 * Used by SimpleItemRequest to determine generosity rules
 */
public class EquipmentRequestQueue {

    /**
     * Check if a material is combat equipment
     */
    public static boolean isCombatEquipment(@NotNull Material item) {
        String name = item.name();

        // Swords and axes (melee combat)
        if (name.contains("_SWORD") || name.contains("_AXE")) {
            return true;
        }

        // Armor (protection)
        if (name.contains("_HELMET") || name.contains("_CHESTPLATE") ||
            name.contains("_LEGGINGS") || name.contains("_BOOTS")) {
            return true;
        }

        // Ranged combat
        if (name.equals("BOW") || name.equals("CROSSBOW") || name.equals("ARROW") ||
            name.equals("TIPPED_ARROW") || name.equals("TRIDENT")) {
            return true;
        }

        // Shield (defensive)
        if (name.equals("SHIELD")) {
            return true;
        }

        return false;
    }

    /**
     * Check if a material is food
     */
    public static boolean isFood(@NotNull Material item) {
        return item.isEdible();
    }

    /**
     * Check if a material is a tool
     */
    public static boolean isTool(@NotNull Material item) {
        String name = item.name();
        return name.contains("_PICKAXE") || name.contains("_SHOVEL") ||
               name.contains("_HOE") || name.equals("FISHING_ROD");
    }
}
