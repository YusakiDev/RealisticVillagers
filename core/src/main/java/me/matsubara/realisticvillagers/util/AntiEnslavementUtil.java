package me.matsubara.realisticvillagers.util;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Utility class for detecting villager confinement/enslavement and preventing work in confined spaces.
 */
public class AntiEnslavementUtil {
    
    private static me.matsubara.realisticvillagers.RealisticVillagers plugin;
    
    // Cache for walkable area calculations to improve performance
    private static final Map<UUID, CachedAreaResult> areaCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 30000L; // 30 seconds cache
    
    /**
     * Cached result for walkable area calculations
     */
    private static class CachedAreaResult {
        final int walkableArea;
        final long timestamp;
        final Location cachedLocation;
        
        CachedAreaResult(int walkableArea, Location location) {
            this.walkableArea = walkableArea;
            this.timestamp = System.currentTimeMillis();
            this.cachedLocation = location.clone();
        }
        
        boolean isValid(Location currentLocation) {
            // Cache is valid if it's recent and villager hasn't moved much
            boolean timeValid = (System.currentTimeMillis() - timestamp) < CACHE_DURATION;
            boolean locationValid = cachedLocation.distance(currentLocation) < 5.0; // 5 block tolerance
            return timeValid && locationValid;
        }
    }
    
    /**
     * Initialize the anti-enslavement system
     */
    public static void initialize(@NotNull me.matsubara.realisticvillagers.RealisticVillagers pluginInstance) {
        plugin = pluginInstance;
    }
    
    /**
     * Checks if anti-enslavement protection is enabled in configuration
     */
    public static boolean isAntiEnslavementEnabled() {
        if (plugin == null) return false;
        
        try {
            var config = plugin.getWorkHungerConfig();
            return config.getBoolean("anti-enslavement.enabled", true);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Checks if a villager is confined and should refuse to work
     * 
     * @param villager The villager to check
     * @return true if the villager is confined and should not work
     */
    public static boolean isVillagerConfined(@NotNull IVillagerNPC villager) {
        if (!isAntiEnslavementEnabled()) {
            return false;
        }
        
        LivingEntity bukkitVillager = villager.bukkit();
        if (bukkitVillager == null || bukkitVillager.getWorld() == null) {
            return false;
        }
        
        Location currentLocation = bukkitVillager.getLocation();
        UUID villagerId = villager.getUniqueId();
        
        // Check cache first
        CachedAreaResult cached = areaCache.get(villagerId);
        int walkableArea;
        
        if (cached != null && cached.isValid(currentLocation)) {
            walkableArea = cached.walkableArea;
        } else {
            // Calculate new walkable area
            walkableArea = calculateWalkableArea(villager);
            areaCache.put(villagerId, new CachedAreaResult(walkableArea, currentLocation));
        }
        
        int minimumArea = getMinimumWalkableArea();
        boolean isConfined = walkableArea < minimumArea;
        
        // Log confinement results only at FINE level
        if (plugin != null && isConfined && plugin.getLogger().isLoggable(Level.FINE)) {
            plugin.getLogger().fine(String.format("Villager %s refuses to work: confined to %d blocks (minimum: %d)", 
                villager.getVillagerName(), walkableArea, minimumArea));
        }
        
        return isConfined;
    }
    
    /**
     * Calculates the walkable area around a villager using villager-sized flood-fill algorithm
     * This simulates actual villager movement (2 blocks tall) rather than theoretical walkable blocks
     * 
     * @param villager The villager to check
     * @return The number of positions where a villager can actually stand
     */
    private static int calculateWalkableArea(@NotNull IVillagerNPC villager) {
        LivingEntity bukkitVillager = villager.bukkit();
        if (bukkitVillager == null || bukkitVillager.getWorld() == null) {
            return 0;
        }
        
        Location startLocation = bukkitVillager.getLocation();
        World world = startLocation.getWorld();
        
        // Use villager-sized flood-fill algorithm to count standing positions
        Set<String> visited = new HashSet<>();
        Queue<Location> toCheck = new LinkedList<>();
        
        // Find a good starting point where a villager can actually stand
        Location validStart = findValidVillagerStandingLocation(world, startLocation, villager);
        
        if (validStart == null) {
            // No valid standing position found
            if (plugin != null) {
                plugin.getLogger().fine(String.format("No valid standing location found for %s at %s", 
                    villager.getVillagerName(), startLocation.toString()));
            }
            return 0;
        }
        
        // Debug the chosen starting location (FINE level)
        if (plugin != null && plugin.getLogger().isLoggable(Level.FINE)) {
            Block floor = world.getBlockAt(validStart.clone().add(0, -1, 0));
            Block feet = world.getBlockAt(validStart);
            Block head = world.getBlockAt(validStart.clone().add(0, 1, 0));
            
            plugin.getLogger().fine(String.format("Checking %s at %s - Floor: %s, Feet: %s, Head: %s", 
                villager.getVillagerName(), 
                validStart.toString(),
                floor.getType().name(),
                feet.getType().name(),
                head.getType().name()));
        }
        
        toCheck.add(validStart);
        
        int villagerPositions = 0;
        int maxChecks = getMaxAreaScan();
        int checks = 0;
        
        while (!toCheck.isEmpty() && checks < maxChecks) {
            checks++;
            Location current = toCheck.poll();
            String key = current.getBlockX() + "," + current.getBlockY() + "," + current.getBlockZ();
            
            if (visited.contains(key)) {
                continue;
            }
            visited.add(key);
            
            // Check if a villager can actually stand here (2 blocks tall)
            if (canVillagerStandHere(world, current)) {
                // Distance check - don't count positions too far from start
                double distanceFromStart = current.distance(validStart);
                if (distanceFromStart > 6.0) { // Max 6 blocks from starting position
                    continue; // Skip this position but keep exploring
                }
                
                villagerPositions++;
                
                // Debug: log first few standing positions (FINE level only)
                if (plugin != null && plugin.getLogger().isLoggable(Level.FINE) && villagerPositions <= 3) {
                    Block floor = world.getBlockAt(current.clone().add(0, -1, 0));
                    plugin.getLogger().fine(String.format("  Position #%d at %s: floor=%s (distance: %.1f)", 
                        villagerPositions, current.toString(), floor.getType().name(), distanceFromStart));
                }
                
                // Only add adjacent positions if we're not too far from start
                if (distanceFromStart < 5.0) { // Stop exploring beyond 5 blocks
                    addVillagerWalkablePositions(current, toCheck, visited);
                }
            }
        }
        
        // Debug logging for area analysis (FINE level only)
        if (plugin != null && plugin.getLogger().isLoggable(Level.FINE) && villagerPositions > 0) {
            // Analyze the actual standing positions found
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            
            // Go through the visited set to find boundaries of standing positions
            for (String key : visited) {
                String[] coords = key.split(",");
                if (coords.length == 3) {
                    try {
                        int x = Integer.parseInt(coords[0]);
                        int y = Integer.parseInt(coords[1]);
                        int z = Integer.parseInt(coords[2]);
                        
                        // Only count actual villager standing positions for boundaries
                        Location testLoc = new Location(world, x, y, z);
                        if (canVillagerStandHere(world, testLoc)) {
                            minX = Math.min(minX, x);
                            maxX = Math.max(maxX, x);
                            minY = Math.min(minY, y);
                            maxY = Math.max(maxY, y);
                            minZ = Math.min(minZ, z);
                            maxZ = Math.max(maxZ, z);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
            
            if (minX != Integer.MAX_VALUE) {
                plugin.getLogger().fine(String.format("Villager area bounds for %s: X(%d to %d), Y(%d to %d), Z(%d to %d) = %dx%dx%d box with %d standing positions", 
                    villager.getVillagerName(), minX, maxX, minY, maxY, minZ, maxZ,
                    (maxX - minX + 1), (maxY - minY + 1), (maxZ - minZ + 1), villagerPositions));
            }
        }
        
        return villagerPositions;
    }
    
    /**
     * Finds a valid starting location where a villager can actually stand (2 blocks tall).
     * Checks the villager's exact position and nearby blocks to find a standing spot.
     */
    @Nullable
    private static Location findValidVillagerStandingLocation(@NotNull World world, @NotNull Location villagerLocation, @NotNull IVillagerNPC villager) {
        // Convert to block coordinates
        Location blockLocation = villagerLocation.getBlock().getLocation();
        
        // First, try the exact position where the villager is
        if (canVillagerStandHere(world, blockLocation)) {
            return blockLocation;
        }
        
        // If that doesn't work, try one block down (villager might be floating slightly)
        Location oneDown = blockLocation.clone().add(0, -1, 0);
        if (canVillagerStandHere(world, oneDown)) {
            return oneDown;
        }
        
        // Try adjacent horizontal positions (villager might be on the edge)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue; // Skip center (already checked)
                
                Location adjacent = blockLocation.clone().add(dx, 0, dz);
                if (canVillagerStandHere(world, adjacent)) {
                    return adjacent;
                }
                
                // Also try one level down for each adjacent position
                Location adjacentDown = adjacent.clone().add(0, -1, 0);
                if (canVillagerStandHere(world, adjacentDown)) {
                    return adjacentDown;
                }
            }
        }
        
        // If we still haven't found anything, the villager is in a very confined space
        if (plugin != null) {
            plugin.getLogger().fine(String.format("Villager %s is in an impossible location - no villager standing positions nearby at %s", 
                villager.getVillagerName(), villagerLocation.toString()));
        }
        
        return null;
    }
    
    /**
     * Adds adjacent positions a villager could walk to (simulating actual movement)
     */
    private static void addVillagerWalkablePositions(@NotNull Location current, @NotNull Queue<Location> toCheck, @NotNull Set<String> visited) {
        World world = current.getWorld();
        
        // Villagers can move in 8 horizontal directions  
        int[] dx = {-1, -1, -1,  0,  0,  1,  1,  1};
        int[] dz = {-1,  0,  1, -1,  1, -1,  0,  1};
        
        for (int i = 0; i < 8; i++) {
            // For diagonal movement, check if corner is blocked
            if (dx[i] != 0 && dz[i] != 0) { // This is diagonal movement
                // Check if we can actually move diagonally through the corner
                Location horizontalBlock = current.clone().add(dx[i], 0, 0);
                Location verticalBlock = current.clone().add(0, 0, dz[i]);
                
                // If either the horizontal or vertical path is blocked, can't go diagonal
                if (!canVillagerStandHere(world, horizontalBlock) || !canVillagerStandHere(world, verticalBlock)) {
                    continue; // Skip this diagonal movement
                }
            }
            
            // Check horizontal movement (same Y level)
            Location next = current.clone().add(dx[i], 0, dz[i]);
            String key = next.getBlockX() + "," + next.getBlockY() + "," + next.getBlockZ();
            
            if (!visited.contains(key)) {
                toCheck.add(next);
            }
            
            // Check one level down (villagers can step down)
            Location down = next.clone().add(0, -1, 0);
            String downKey = down.getBlockX() + "," + down.getBlockY() + "," + down.getBlockZ();
            
            if (!visited.contains(downKey)) {
                // Same corner check for downward diagonal movement
                if (dx[i] != 0 && dz[i] != 0) { // Diagonal down movement
                    Location horizontalBlockDown = current.clone().add(dx[i], -1, 0);
                    Location verticalBlockDown = current.clone().add(0, -1, dz[i]);
                    
                    if (!canVillagerStandHere(world, horizontalBlockDown) || !canVillagerStandHere(world, verticalBlockDown)) {
                        continue; // Skip this diagonal down movement
                    }
                }
                toCheck.add(down);
            }
            
            // Check one level up (villagers can step up 1 block)
            Location up = next.clone().add(0, 1, 0);
            String upKey = up.getBlockX() + "," + up.getBlockY() + "," + up.getBlockZ();
            
            if (!visited.contains(upKey)) {
                // Same corner check for upward diagonal movement
                if (dx[i] != 0 && dz[i] != 0) { // Diagonal up movement
                    Location horizontalBlockUp = current.clone().add(dx[i], 1, 0);
                    Location verticalBlockUp = current.clone().add(0, 1, dz[i]);
                    
                    if (!canVillagerStandHere(world, horizontalBlockUp) || !canVillagerStandHere(world, verticalBlockUp)) {
                        continue; // Skip this diagonal up movement
                    }
                }
                toCheck.add(up);
            }
        }
    }
    
    /**
     * Checks if a villager (2 blocks tall) can stand at this position
     */
    private static boolean canVillagerStandHere(@NotNull World world, @NotNull Location location) {
        Block floor = world.getBlockAt(location.clone().add(0, -1, 0));    // Block below feet
        Block feet = world.getBlockAt(location);                           // Feet level  
        Block head = world.getBlockAt(location.clone().add(0, 1, 0));      // Head level
        
        // Need solid floor (but not dangerous) + 2 blocks of air clearance
        Material floorType = floor.getType();
        if (!floorType.isSolid() || isDangerousBlock(floorType)) {
            // Special case: standing on farmland, path, or other special blocks
            if (floorType != Material.FARMLAND && 
                floorType != Material.DIRT_PATH && 
                !floorType.name().contains("SLAB") &&
                !floorType.name().contains("STAIRS")) {
                return false;
            }
        }
        
        // Both foot level AND head level must be air or passable plants
        return isPassableForVillager(feet.getType()) && isPassableForVillager(head.getType());
    }
    
    
    /**
     * Checks if a block type is passable for villagers (air or plants they can walk through)
     */
    private static boolean isPassableForVillager(@NotNull Material type) {
        // Air is always passable
        if (type == Material.AIR) {
            return true;
        }
        
        // Grass and plant materials that villagers can walk through
        return type == Material.SHORT_GRASS ||           // Short grass
               type == Material.TALL_GRASS ||      // Tall grass
               type == Material.FERN ||             // Fern
               type == Material.LARGE_FERN ||       // Large fern
               type == Material.DEAD_BUSH ||        // Dead bush
               type == Material.DANDELION ||        // Flowers
               type == Material.POPPY ||
               type == Material.BLUE_ORCHID ||
               type == Material.ALLIUM ||
               type == Material.AZURE_BLUET ||
               type == Material.RED_TULIP ||
               type == Material.ORANGE_TULIP ||
               type == Material.WHITE_TULIP ||
               type == Material.PINK_TULIP ||
               type == Material.OXEYE_DAISY ||
               type == Material.CORNFLOWER ||
               type == Material.LILY_OF_THE_VALLEY ||
               type == Material.SUNFLOWER ||
               type == Material.LILAC ||
               type == Material.ROSE_BUSH ||
               type == Material.PEONY ||
               type.name().contains("SAPLING") ||   // Tree saplings
               type == Material.WHEAT ||            // Crops
               type == Material.CARROTS ||
               type == Material.POTATOES ||
               type == Material.BEETROOTS ||
               type == Material.SUGAR_CANE ||
               type == Material.KELP ||
               type == Material.SEAGRASS ||
               type == Material.TALL_SEAGRASS;
    }
    
    /**
     * Checks if a block type is dangerous for villagers
     */
    private static boolean isDangerousBlock(@NotNull Material type) {
        return type == Material.LAVA ||
               type == Material.FIRE ||
               type == Material.SOUL_FIRE ||
               type.name().contains("MAGMA") ||
               type == Material.CACTUS ||
               type.name().contains("WITHER_ROSE") ||
               type == Material.SWEET_BERRY_BUSH;
    }
    
    /**
     * Gets the minimum walkable area from configuration
     */
    private static int getMinimumWalkableArea() {
        if (plugin == null) return 25; // Default fallback
        
        try {
            var config = plugin.getWorkHungerConfig();
            return config.getInt("anti-enslavement.minimum-walkable-area", 25);
        } catch (Exception e) {
            return 25;
        }
    }
    
    /**
     * Gets the maximum area scan limit from configuration
     */
    private static int getMaxAreaScan() {
        if (plugin == null) return 500; // Default fallback
        
        try {
            var config = plugin.getWorkHungerConfig();
            return config.getInt("anti-enslavement.max-area-scan", 500);
        } catch (Exception e) {
            return 500;
        }
    }
    
    /**
     * Clears the area cache for a specific villager (useful when villager moves significantly)
     */
    public static void clearCache(@NotNull UUID villagerId) {
        areaCache.remove(villagerId);
    }
    
    /**
     * Clears all cached area calculations (useful for cleanup)
     */
    public static void clearAllCache() {
        areaCache.clear();
    }
    
    /**
     * Scheduled cleanup task to remove expired cache entries
     */
    public static void cleanupExpiredCache() {
        long currentTime = System.currentTimeMillis();
        areaCache.entrySet().removeIf(entry -> 
            (currentTime - entry.getValue().timestamp) > CACHE_DURATION);
    }
}