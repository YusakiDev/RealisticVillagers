package me.matsubara.realisticvillagers.nms;

import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/**
 * Type-safe wrapper API for entity data operations that prevents client disconnections
 * and provides graceful fallback mechanisms when field access fails.
 */
public class SafeEntityDataAPI {
    
    private final RealisticVillagers plugin;
    private final EntityDataManager dataManager;
    
    // Cache for reflection methods to improve performance
    private final ConcurrentMap<String, Method> methodCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> fieldAccessorCache = new ConcurrentHashMap<>();
    
    public SafeEntityDataAPI(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
        this.dataManager = new EntityDataManager(plugin);
    }
    
    /**
     * Safely sets a LivingEntity flag bit without causing client disconnections
     * @param entity The entity to modify
     * @param flag The flag bit to set (0-7)
     * @param value The value to set
     * @return true if operation was successful
     */
    public boolean setLivingEntityFlag(@NotNull LivingEntity entity, int flag, boolean value) {
        if (flag < 0 || flag > 7) {
            plugin.getLogger().warning("Invalid flag bit: " + flag + " (valid range: 0-7)");
            return false;
        }
        
        try {
            // Get the NMS entity
            Object nmsEntity = getNMSEntity(entity);
            if (nmsEntity == null) {
                plugin.getLogger().warning("Could not get NMS entity for " + entity.getType());
                dataManager.recordFailure();
                return false;
            }
            
            // Try to use the safe method approach
            if (setFlagUsingSafeMethod(nmsEntity, flag, value)) {
                dataManager.recordSuccess();
                return true;
            }
            
            // Fallback: try direct entity data manipulation
            if (setFlagUsingDirectAccess(nmsEntity, flag, value)) {
                dataManager.recordSuccess();
                return true;
            }
            
            plugin.getLogger().warning("All methods failed to set living entity flag " + flag + " for " + entity.getType());
            dataManager.recordFailure();
            return false;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error setting living entity flag " + flag + " for " + entity.getType(), e);
            dataManager.recordFailure();
            return false;
        }
    }
    
    /**
     * Safely gets the LivingEntity flags byte without causing client disconnections
     * @param entity The entity to read from
     * @return The flags byte, or 0 if access failed
     */
    public byte getLivingEntityFlags(@NotNull LivingEntity entity) {
        try {
            Object nmsEntity = getNMSEntity(entity);
            if (nmsEntity == null) {
                dataManager.recordFailure();
                return 0;
            }
            
            // Try to get flags using safe method
            Byte flags = getFlagsUsingSafeMethod(nmsEntity);
            if (flags != null) {
                dataManager.recordSuccess();
                return flags;
            }
            
            // Fallback: try direct access
            flags = getFlagsUsingDirectAccess(nmsEntity);
            if (flags != null) {
                dataManager.recordSuccess();
                return flags;
            }
            
            plugin.getLogger().fine("Could not read living entity flags for " + entity.getType());
            dataManager.recordFailure();
            return 0;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Error reading living entity flags for " + entity.getType(), e);
            dataManager.recordFailure();
            return 0;
        }
    }
    
    /**
     * Validates that entity data field mapping is correct and safe to use
     */
    public boolean validateFieldMapping(@NotNull Class<?> entityClass, @NotNull String fieldName, @NotNull Class<?> expectedType) {
        return dataManager.validateFieldMapping(entityClass, fieldName, expectedType);
    }
    
    /**
     * Initializes field discovery for commonly used entity classes
     */
    public void initializeFieldMappings() {
        plugin.getLogger().info("Initializing entity data field mappings...");
        
        try {
            // Discover fields for common entity classes
            String[] commonEntities = {
                "net.minecraft.world.entity.LivingEntity",
                "net.minecraft.world.entity.npc.Villager", 
                "net.minecraft.world.entity.npc.AbstractVillager",
                "net.minecraft.world.entity.npc.WanderingTrader",
                "net.minecraft.world.entity.Entity"
            };
            
            for (String className : commonEntities) {
                try {
                    Class<?> clazz = Class.forName(className);
                    dataManager.discoverFields(clazz);
                } catch (ClassNotFoundException e) {
                    plugin.getLogger().fine("Entity class not found: " + className + " (this may be normal for this server version)");
                }
            }
            
            plugin.getLogger().info("Field mapping initialization completed. " + dataManager.getStatistics());
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error during field mapping initialization", e);
        }
    }
    
    /**
     * Gets a diagnostic report of all field mappings
     */
    public String getDiagnosticReport() {
        return dataManager.getDiagnosticReport();
    }
    
    // === Private Helper Methods ===
    
    /**
     * Gets the NMS entity from a Bukkit entity
     */
    @Nullable
    private Object getNMSEntity(@NotNull LivingEntity entity) {
        try {
            // Try CraftEntity.getHandle() method
            Method getHandle = entity.getClass().getMethod("getHandle");
            return getHandle.invoke(entity);
        } catch (Exception e) {
            plugin.getLogger().fine("Could not get NMS entity handle: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Attempts to set flag using the standard setLivingEntityFlag method
     */
    private boolean setFlagUsingSafeMethod(@NotNull Object nmsEntity, int flag, boolean value) {
        try {
            String methodKey = nmsEntity.getClass().getName() + ".setLivingEntityFlag";
            Method method = methodCache.get(methodKey);
            
            if (method == null) {
                // Try to find the method
                method = nmsEntity.getClass().getMethod("setLivingEntityFlag", int.class, boolean.class);
                methodCache.put(methodKey, method);
            }
            
            // Call the method
            method.invoke(nmsEntity, flag, value);
            plugin.getLogger().fine("Successfully set living entity flag " + flag + " = " + value + " using standard method");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().fine("Standard method failed for setLivingEntityFlag: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Attempts to set flag using direct entity data manipulation
     */
    private boolean setFlagUsingDirectAccess(@NotNull Object nmsEntity, int flag, boolean value) {
        try {
            // Get the EntityData object
            Method getEntityData = nmsEntity.getClass().getMethod("getEntityData");
            Object entityData = getEntityData.invoke(nmsEntity);
            
            // Find the DATA_LIVING_ENTITY_FLAGS accessor
            Object flagsAccessor = getFlagsAccessor(nmsEntity.getClass());
            if (flagsAccessor == null) {
                return false;
            }
            
            // Get current flags
            Method get = entityData.getClass().getMethod("get", Class.forName("net.minecraft.network.syncher.EntityDataAccessor"));
            Byte currentFlags = (Byte) get.invoke(entityData, flagsAccessor);
            
            // Calculate new flags value
            byte newFlags = currentFlags;
            if (value) {
                newFlags |= (1 << flag); // Set bit
            } else {
                newFlags &= ~(1 << flag); // Clear bit  
            }
            
            // Set new flags if changed
            if (newFlags != currentFlags) {
                Method set = entityData.getClass().getMethod("set", 
                    Class.forName("net.minecraft.network.syncher.EntityDataAccessor"), Object.class);
                set.invoke(entityData, flagsAccessor, newFlags);
                plugin.getLogger().fine("Successfully set living entity flag " + flag + " = " + value + " using direct access");
            }
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().fine("Direct access failed for setLivingEntityFlag: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets flags using the standard method
     */
    @Nullable
    private Byte getFlagsUsingSafeMethod(@NotNull Object nmsEntity) {
        try {
            // Try to call a getter method that reads the flags
            String[] possibleMethods = {"isUsingItem", "getSharedFlag"};
            
            for (String methodName : possibleMethods) {
                try {
                    Method method = nmsEntity.getClass().getMethod(methodName);
                    // This won't give us the raw flags, but at least we know the field is accessible
                    method.invoke(nmsEntity);
                    // If we got here, field access is working, so try direct access
                    return getFlagsUsingDirectAccess(nmsEntity);
                } catch (Exception ignored) {
                    // Try next method
                }
            }
            
            return null;
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Gets flags using direct entity data access
     */
    @Nullable
    private Byte getFlagsUsingDirectAccess(@NotNull Object nmsEntity) {
        try {
            // Get the EntityData object
            Method getEntityData = nmsEntity.getClass().getMethod("getEntityData");
            Object entityData = getEntityData.invoke(nmsEntity);
            
            // Find the DATA_LIVING_ENTITY_FLAGS accessor
            Object flagsAccessor = getFlagsAccessor(nmsEntity.getClass());
            if (flagsAccessor == null) {
                return null;
            }
            
            // Get current flags
            Method get = entityData.getClass().getMethod("get", Class.forName("net.minecraft.network.syncher.EntityDataAccessor"));
            return (Byte) get.invoke(entityData, flagsAccessor);
            
        } catch (Exception e) {
            plugin.getLogger().fine("Direct flags access failed: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets the DATA_LIVING_ENTITY_FLAGS accessor for an entity class
     */
    @Nullable
    private Object getFlagsAccessor(@NotNull Class<?> entityClass) {
        String cacheKey = entityClass.getName() + ".DATA_LIVING_ENTITY_FLAGS";
        Object cached = fieldAccessorCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        try {
            // Try to find the DATA_LIVING_ENTITY_FLAGS field
            Class<?> livingEntityClass = Class.forName("net.minecraft.world.entity.LivingEntity");
            java.lang.reflect.Field flagsField = livingEntityClass.getDeclaredField("DATA_LIVING_ENTITY_FLAGS");
            flagsField.setAccessible(true);
            Object accessor = flagsField.get(null);
            
            fieldAccessorCache.put(cacheKey, accessor);
            return accessor;
            
        } catch (Exception e) {
            plugin.getLogger().fine("Could not find DATA_LIVING_ENTITY_FLAGS accessor: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Clears all internal caches
     */
    public void clearCaches() {
        methodCache.clear();
        fieldAccessorCache.clear();
        dataManager.clearCache();
        plugin.getLogger().info("SafeEntityDataAPI caches cleared");
    }
}