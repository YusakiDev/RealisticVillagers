package me.matsubara.realisticvillagers.nms;

import me.matsubara.realisticvillagers.RealisticVillagers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/**
 * Centralized manager for safe entity data access with runtime field discovery and validation.
 * This class provides a robust abstraction layer over Minecraft's entity data system to prevent
 * field mapping errors and client disconnections.
 */
public class EntityDataManager {
    
    private final RealisticVillagers plugin;
    private final ConcurrentMap<String, FieldMapping> fieldMappings = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, Boolean> discoveredClasses = new ConcurrentHashMap<>();
    
    // Performance metrics
    private long fieldDiscoveryTime = 0;
    private int successfulAccesses = 0;
    private int failedAccesses = 0;
    
    public EntityDataManager(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Represents a discovered entity data field mapping
     */
    private static class FieldMapping {
        final String fieldName;
        final Class<?> entityClass;
        final Class<?> fieldType;
        final Object fieldAccessor;
        final boolean isAccessible;
        final long discoveredAt;
        
        FieldMapping(String fieldName, Class<?> entityClass, Class<?> fieldType, 
                    Object fieldAccessor, boolean isAccessible) {
            this.fieldName = fieldName;
            this.entityClass = entityClass;
            this.fieldType = fieldType;
            this.fieldAccessor = fieldAccessor;
            this.isAccessible = isAccessible;
            this.discoveredAt = System.currentTimeMillis();
        }
        
        String getKey() {
            return entityClass.getSimpleName() + "." + fieldName;
        }
    }
    
    /**
     * Discovers and validates entity data fields for a given entity class
     * @param entityClass The entity class to scan
     * @return true if discovery was successful
     */
    public boolean discoverFields(@NotNull Class<?> entityClass) {
        if (discoveredClasses.containsKey(entityClass)) {
            return discoveredClasses.get(entityClass);
        }
        
        long startTime = System.currentTimeMillis();
        boolean success = false;
        
        try {
            plugin.getLogger().fine("Discovering entity data fields for " + entityClass.getSimpleName());
            
            // Scan all declared fields in this class
            Field[] fields = entityClass.getDeclaredFields();
            int discoveredCount = 0;
            
            for (Field field : fields) {
                if (isEntityDataField(field)) {
                    try {
                        FieldMapping mapping = createFieldMapping(field, entityClass);
                        if (mapping != null) {
                            fieldMappings.put(mapping.getKey(), mapping);
                            discoveredCount++;
                            plugin.getLogger().fine("Discovered field: " + mapping.getKey() + " (type: " + mapping.fieldType.getSimpleName() + ")");
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to map field " + field.getName() + " in " + entityClass.getSimpleName() + ": " + e.getMessage());
                    }
                }
            }
            
            // Also scan superclass fields
            Class<?> superClass = entityClass.getSuperclass();
            if (superClass != null && !superClass.equals(Object.class)) {
                discoverFields(superClass);
            }
            
            success = discoveredCount > 0;
            plugin.getLogger().info("Field discovery for " + entityClass.getSimpleName() + " completed: " + 
                                  discoveredCount + " fields discovered in " + 
                                  (System.currentTimeMillis() - startTime) + "ms");
                                  
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Field discovery failed for " + entityClass.getSimpleName(), e);
        } finally {
            fieldDiscoveryTime += (System.currentTimeMillis() - startTime);
            discoveredClasses.put(entityClass, success);
        }
        
        return success;
    }
    
    /**
     * Checks if a field is an entity data field (EntityDataAccessor)
     */
    private boolean isEntityDataField(@NotNull Field field) {
        Class<?> fieldType = field.getType();
        
        // Check if it's an EntityDataAccessor (in any NMS version)
        String typeName = fieldType.getName();
        return typeName.contains("EntityDataAccessor") || 
               typeName.contains("DataWatcher") ||
               field.getName().startsWith("DATA_");
    }
    
    /**
     * Creates a field mapping from a discovered field
     */
    @Nullable
    private FieldMapping createFieldMapping(@NotNull Field field, @NotNull Class<?> entityClass) {
        try {
            field.setAccessible(true);
            Object accessor = field.get(null); // Static field
            
            if (accessor == null) {
                plugin.getLogger().warning("Field " + field.getName() + " in " + entityClass.getSimpleName() + " is null");
                return null;
            }
            
            // Try to determine the field's data type
            Class<?> dataType = determineFieldDataType(field, accessor);
            
            return new FieldMapping(field.getName(), entityClass, dataType, accessor, true);
            
        } catch (IllegalAccessException e) {
            plugin.getLogger().warning("Cannot access field " + field.getName() + " in " + entityClass.getSimpleName() + ": " + e.getMessage());
            return new FieldMapping(field.getName(), entityClass, Object.class, null, false);
        } catch (Exception e) {
            plugin.getLogger().warning("Error creating field mapping for " + field.getName() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Attempts to determine the data type of an EntityDataAccessor
     */
    @NotNull
    private Class<?> determineFieldDataType(@NotNull Field field, @NotNull Object accessor) {
        // Try to get generic type information
        java.lang.reflect.Type genericType = field.getGenericType();
        if (genericType instanceof java.lang.reflect.ParameterizedType paramType) {
            java.lang.reflect.Type[] typeArgs = paramType.getActualTypeArguments();
            if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?> dataType) {
                return dataType;
            }
        }
        
        // Fallback: try to guess from field name
        String fieldName = field.getName().toLowerCase();
        if (fieldName.contains("flag") || fieldName.contains("byte")) {
            return Byte.class;
        } else if (fieldName.contains("int") || fieldName.contains("count")) {
            return Integer.class;
        } else if (fieldName.contains("float") || fieldName.contains("health")) {
            return Float.class;
        } else if (fieldName.contains("boolean") || fieldName.contains("visible")) {
            return Boolean.class;
        }
        
        return Object.class; // Unknown type
    }
    
    /**
     * Gets a field mapping by entity class and field name
     */
    @Nullable
    public FieldMapping getFieldMapping(@NotNull Class<?> entityClass, @NotNull String fieldName) {
        String key = entityClass.getSimpleName() + "." + fieldName;
        FieldMapping mapping = fieldMappings.get(key);
        
        if (mapping == null) {
            // Try to discover fields for this class if not done already
            discoverFields(entityClass);
            mapping = fieldMappings.get(key);
        }
        
        return mapping;
    }
    
    /**
     * Validates that a field mapping is safe to use
     */
    public boolean validateFieldMapping(@NotNull Class<?> entityClass, @NotNull String fieldName, @NotNull Class<?> expectedType) {
        FieldMapping mapping = getFieldMapping(entityClass, fieldName);
        
        if (mapping == null) {
            plugin.getLogger().warning("Field mapping not found: " + entityClass.getSimpleName() + "." + fieldName);
            return false;
        }
        
        if (!mapping.isAccessible) {
            plugin.getLogger().warning("Field is not accessible: " + mapping.getKey());
            return false;
        }
        
        if (!expectedType.isAssignableFrom(mapping.fieldType)) {
            plugin.getLogger().warning("Field type mismatch for " + mapping.getKey() + 
                                     ": expected " + expectedType.getSimpleName() + 
                                     ", found " + mapping.fieldType.getSimpleName());
            return false;
        }
        
        return true;
    }
    
    /**
     * Gets performance statistics
     */
    public String getStatistics() {
        return String.format("EntityDataManager Stats - Discovered: %d fields, Discovery Time: %dms, " +
                           "Successful Accesses: %d, Failed Accesses: %d",
                           fieldMappings.size(), fieldDiscoveryTime, successfulAccesses, failedAccesses);
    }
    
    /**
     * Generates a diagnostic report of all discovered field mappings
     */
    public String getDiagnosticReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Entity Data Field Mappings ===\n");
        
        fieldMappings.values().stream()
            .sorted((a, b) -> a.getKey().compareTo(b.getKey()))
            .forEach(mapping -> {
                report.append(String.format("%-40s %-15s %s %s\n",
                    mapping.getKey(),
                    mapping.fieldType.getSimpleName(),
                    mapping.isAccessible ? "✓" : "✗",
                    mapping.fieldAccessor != null ? "OK" : "NULL"
                ));
            });
        
        report.append("\n").append(getStatistics());
        return report.toString();
    }
    
    /**
     * Records a successful field access for metrics
     */
    void recordSuccess() {
        successfulAccesses++;
    }
    
    /**
     * Records a failed field access for metrics  
     */
    void recordFailure() {
        failedAccesses++;
    }
    
    /**
     * Clears all cached mappings (for testing or reloading)
     */
    public void clearCache() {
        fieldMappings.clear();
        discoveredClasses.clear();
        plugin.getLogger().info("EntityDataManager cache cleared");
    }
}