# Folia Thread Safety Fix - Periodic Hunger Check

## Problem
The periodic hunger check system was causing a Folia threading error:
```
Thread failed main thread check: Accessing entity state off owning region's thread
```

This error occurred in `WorkHungerIntegration.findNearbyVillagers()` when it was called from the wrong thread context.

## Root Cause
The original implementation had a flawed approach:

1. **Main timer task** collected all villagers from all worlds
2. Each villager was checked on its **own entity thread** and added to a shared list
3. After collecting all villagers, `WorkHungerIntegration.periodicHungerCheck()` was called
4. This method then called `findNearbyVillagers()` which used `world.getEntitiesByClass(Villager.class)`
5. **PROBLEM**: This accessed entities from different regions while not on the correct thread

In Folia's multi-threaded environment, each region has its own thread. Accessing entities from different regions without proper thread synchronization causes the "thread failed main thread check" error.

## Solution

### 1. Changed Scheduling Approach (RealisticVillagers.java)
**Before**: Collected all villagers, then processed them together
```java
// Collect all villagers from all entity threads
List<IVillagerNPC> allVillagers = Collections.synchronizedList(new ArrayList<>());
// ... collect villagers ...
// Then process them all at once (WRONG - not on entity thread!)
WorkHungerIntegration.periodicHungerCheck(allVillagers, this);
```

**After**: Process each villager individually on its own entity thread
```java
// Process each villager on its own entity thread
scheduler.runAtEntity(villager, checkTask -> {
    // Check if this villager needs food
    if (npc.getFoodLevel() < threshold) {
        // Perform hunger check for THIS villager (on its entity thread)
        WorkHungerIntegration.periodicHungerCheck(
            Collections.singletonList(npc), 
            this
        );
    }
});
```

### 2. Changed Entity Finding Method (WorkHungerIntegration.java)
**Before**: Used `world.getEntitiesByClass()` which accesses entities across all regions
```java
for (Villager bukkitVillager : world.getEntitiesByClass(Villager.class)) {
    // This tries to access entities in other regions!
    pluginInstance.getConverter().getNPC(bukkitVillager).ifPresent(nearbyVillagers::add);
}
```

**After**: Used `entity.getNearbyEntities()` which is region-thread-safe
```java
bukkit.getNearbyEntities(nearbyVillagerRange, nearbyVillagerRange, nearbyVillagerRange)
    .stream()
    .filter(entity -> entity instanceof Villager)
    .map(entity -> (Villager) entity)
    .filter(nearbyVillager -> !nearbyVillager.equals(bukkit))
    .forEach(nearbyVillager -> {
        pluginInstance.getConverter().getNPC(nearbyVillager).ifPresent(nearbyVillagers::add);
    });
```

## Key Improvements

1. **Thread Safety**: Each villager is processed entirely on its own entity thread
2. **Region-Aware**: Uses `getNearbyEntities()` which only accesses entities safely accessible from the current region
3. **Efficiency**: Only checks villagers that actually need food (below threshold)
4. **Error Handling**: Added try-catch to gracefully handle any unexpected errors

## Files Modified

1. **core/src/main/java/me/matsubara/realisticvillagers/RealisticVillagers.java**
   - Modified `schedulePeriodicHungerChecks()` method
   - Changed from batch processing to per-villager processing on entity threads

2. **core/src/main/java/me/matsubara/realisticvillagers/util/WorkHungerIntegration.java**
   - Modified `findNearbyVillagers()` method
   - Changed from `world.getEntitiesByClass()` to `entity.getNearbyEntities()`
   - Added error handling and better documentation

## Testing Recommendations

1. Test on Folia server with multiple villagers in different regions
2. Verify no threading errors appear in console
3. Confirm hungry villagers can still request food from nearby villagers
4. Check that the periodic hunger check interval works as configured

## Folia Compatibility Notes

When developing for Folia, remember:
- Each region has its own thread
- Entity operations must be performed on the entity's owning thread
- Use `scheduler.runAtEntity()` to ensure correct thread context
- Prefer `entity.getNearbyEntities()` over `world.getEntitiesByClass()`
- Never access entities from other regions without proper scheduling
