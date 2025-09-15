package me.matsubara.realisticvillagers.files;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Configuration enum for work and hunger related settings
 * Separated from main config for better organization
 */
public enum WorkHungerConfig {
    // Main work-hunger settings
    ENABLED("enabled"),
    HUNGER_DECREASE_PER_WORK("hunger-decrease-per-work"),
    REQUEST_FOOD_THRESHOLD("request-food-threshold"),
    MIN_HUNGER_TO_WORK("min-hunger-to-work"),
    NEARBY_VILLAGER_RANGE("nearby-villager-range"),
    MAX_FOOD_LEVEL("max-food-level"),
    
    // Vanilla restock controls
    PREVENT_VANILLA_RESTOCK_WHEN_HUNGRY("prevent-vanilla-restock-when-hungry"),
    
    // Check trigger settings (only periodic check works)
    
    // Periodic check settings
    CHECK_TRIGGERS_PERIODIC_ENABLED("check-triggers.periodic-check.enabled"),
    CHECK_TRIGGERS_PERIODIC_INTERVAL_SECONDS("check-triggers.periodic-check.interval-seconds"),
    
    // Villager request system settings (moved from main config)
    VILLAGER_REQUESTS_ENABLED("villager-requests.enabled"),
    VILLAGER_REQUESTS_MIN_KEEP_FOOD("villager-requests.min-keep-food"),
    VILLAGER_REQUESTS_GENEROSITY_FACTOR("villager-requests.generosity-factor"),
    
    // Physical interaction settings
    PHYSICAL_INTERACTION_ENABLED("villager-requests.physical-interaction.enabled"),
    PHYSICAL_INTERACTION_MAX_DELIVERY_DISTANCE("villager-requests.physical-interaction.max-delivery-distance"),
    PHYSICAL_INTERACTION_ITEM_CLAIM_DURATION("villager-requests.physical-interaction.item-claim-duration"),
    PHYSICAL_INTERACTION_DELIVERY_WALK_SPEED("villager-requests.physical-interaction.delivery-walk-speed"),
    
    // Work-based item generation settings
    WORK_ITEM_GENERATION_ENABLED("work-item-generation.enabled"),
    WORK_ITEM_GENERATION_DISABLE_VANILLA_RESTOCK("work-item-generation.disable-vanilla-restock"),
    WORK_ITEM_GENERATION_CHECK_ACTUAL_TRADES("work-item-generation.check-actual-trades"),
    WORK_ITEM_GENERATION_PROFESSION_ITEMS("work-item-generation.profession-items"),
    
    // Realistic trade inventory settings
    REALISTIC_TRADE_INVENTORY_ENABLED("realistic-trade-inventory.enabled"),
    
    // Equipment request system settings
    EQUIPMENT_REQUESTS_ENABLED("equipment-requests.enabled"),
    EQUIPMENT_REQUESTS_RANGE("equipment-requests.request-range"),
    EQUIPMENT_REQUESTS_MAX_DELIVERY_TIME_MINUTES("equipment-requests.max-delivery-time-minutes"),
    
    // Equipment sharing rules
    EQUIPMENT_SHARING_MIN_KEEP_WEAPONS("equipment-requests.sharing-rules.min-keep-weapons"),
    EQUIPMENT_SHARING_MIN_KEEP_ARMOR_PIECES("equipment-requests.sharing-rules.min-keep-armor-pieces"),
    EQUIPMENT_SHARING_ALLOW_SHARING_EQUIPPED("equipment-requests.sharing-rules.allow-sharing-equipped"),
    EQUIPMENT_SHARING_BETTER_THAN_EQUIPPED("equipment-requests.sharing-rules.share-better-than-equipped"),
    
    // Butcher animal management settings
    BUTCHER_ANIMAL_MANAGEMENT_ENABLED("butcher-animal-management.enabled"),
    BUTCHER_ANIMAL_MANAGEMENT_SEARCH_RANGE("butcher-animal-management.search-range"),
    BUTCHER_ANIMAL_MANAGEMENT_WORK_DURATION("butcher-animal-management.work-duration"),
    
    // Culling settings
    BUTCHER_CULLING_ENABLED("butcher-animal-management.culling.enabled"),
    BUTCHER_CULLING_AXE_TYPES("butcher-animal-management.culling.axe-types"),
    BUTCHER_CULLING_CULL_COOLDOWN("butcher-animal-management.culling.cull-cooldown"),
    BUTCHER_CULLING_PREFER_ADULTS("butcher-animal-management.culling.prefer-adults");

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
