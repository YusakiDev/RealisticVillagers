package me.matsubara.realisticvillagers.files;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Configuration enum for work and hunger related settings.
 * Separated from main config for better organization.
 */
public enum WorkHungerConfig {
    // Main work-hunger settings
    ENABLED("enabled"),
    HUNGER_DECREASE_PER_WORK("hunger-decrease-per-work"),
    REQUEST_FOOD_THRESHOLD("request-food-threshold"),
    MIN_HUNGER_TO_WORK("min-hunger-to-work"),
    MAX_FOOD_LEVEL("max-food-level"),
    
    // Healing thresholds
    MIN_HUNGER_TO_HEAL("min-hunger-to-heal"),
    MIN_HUNGER_TO_SATURATED_HEAL("min-hunger-to-saturated-heal"),
    STOP_EATING_AT_HUNGER("stop-eating-at-hunger"),

    // Anti-enslavement settings (dynamic walkable area detection)
    ANTI_ENSLAVEMENT_ENABLED("anti-enslavement.enabled"),
    ANTI_ENSLAVEMENT_MINIMUM_WALKABLE_AREA("anti-enslavement.minimum-walkable-area"),
    ANTI_ENSLAVEMENT_MAX_AREA_SCAN("anti-enslavement.max-area-scan"),

    // Periodic check settings
    PERIODIC_CHECK_ENABLED("periodic-check.enabled"),
    PERIODIC_CHECK_INTERVAL_SECONDS("periodic-check.interval-seconds"),

    // Villager request system settings
    VILLAGER_REQUESTS_ENABLED("villager-requests.enabled"),
    VILLAGER_REQUESTS_NEARBY_VILLAGER_RANGE("villager-requests.nearby-villager-range"),
    VILLAGER_REQUESTS_MIN_KEEP_FOOD("villager-requests.min-keep-food"),
    VILLAGER_REQUESTS_GENEROSITY_FACTOR("villager-requests.generosity-factor"),

    // Physical interaction settings
    PHYSICAL_INTERACTION_ENABLED("villager-requests.physical-interaction.enabled"),
    PHYSICAL_INTERACTION_MAX_DELIVERY_DISTANCE("villager-requests.physical-interaction.max-delivery-distance"),
    PHYSICAL_INTERACTION_ITEM_CLAIM_DURATION("villager-requests.physical-interaction.item-claim-duration"),
    PHYSICAL_INTERACTION_DELIVERY_WALK_SPEED("villager-requests.physical-interaction.delivery-walk-speed"),

    // Work-based item generation settings
    // NOTE: When WORK_ITEM_GENERATION_ENABLED is true, vanilla restocking is automatically disabled
    WORK_ITEM_GENERATION_ENABLED("work-item-generation.enabled"),
    WORK_ITEM_GENERATION_CHECK_ACTUAL_TRADES("work-item-generation.check-actual-trades"),
    WORK_ITEM_GENERATION_PROFESSION_ITEMS("work-item-generation.profession-items"),

    // Realistic trade inventory settings
    REALISTIC_TRADE_INVENTORY_ENABLED("realistic-trade-inventory.enabled");

    private final String path;
    private final RealisticVillagers plugin = JavaPlugin.getPlugin(RealisticVillagers.class);

    WorkHungerConfig(String path) {
        this.path = path;
    }

    public boolean asBool() {
        return plugin.getWorkHungerConfig().getBoolean(path);
    }

    public int asInt() {
        return plugin.getWorkHungerConfig().getInt(path);
    }

    public long asLong() {
        return plugin.getWorkHungerConfig().getLong(path);
    }

    public double asDouble() {
        return plugin.getWorkHungerConfig().getDouble(path);
    }

    public float asFloat() {
        return (float) plugin.getWorkHungerConfig().getDouble(path);
    }

    public String asString() {
        return plugin.getWorkHungerConfig().getString(path);
    }

    public String asString(String defaultValue) {
        return plugin.getWorkHungerConfig().getString(path, defaultValue);
    }

    public @NotNull String asStringTranslated() {
        return PluginUtils.translate(asString());
    }

    public List<String> asStringList() {
        return plugin.getWorkHungerConfig().getStringList(path);
    }

    public String getPath() {
        return path;
    }
}
