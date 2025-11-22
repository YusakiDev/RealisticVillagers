# Testing Plan for Folia Thread Safety Fix

## Prerequisites
- Folia server (version 1.21.8 or compatible)
- RealisticVillagers plugin with the fix applied
- Multiple villagers spawned in different regions

## Test Scenarios

### 1. Basic Functionality Test
**Goal**: Verify the periodic hunger check still works correctly

**Steps**:
1. Start the Folia server with the plugin
2. Create several villagers in the world
3. Configure `work-hunger-config.yml`:
   ```yaml
   periodic-check-enabled: true
   periodic-check-interval-seconds: 30
   request-food-threshold: 15
   ```
4. Monitor the server console for 2-3 minutes

**Expected Results**:
- No threading errors in console
- No "Thread failed main thread check" errors
- Periodic hunger checks run every 30 seconds (check logs)

### 2. Multi-Region Test
**Goal**: Verify the fix works when villagers are in different regions

**Steps**:
1. Create villagers in different chunks/regions (e.g., 1000 blocks apart)
2. Set some villagers to low hunger (below 15)
3. Place food in nearby villagers' inventories
4. Wait for the periodic hunger check to run

**Expected Results**:
- No threading errors even with villagers in different regions
- Hungry villagers request food from nearby villagers
- System logs show successful food requests

### 3. High Load Test
**Goal**: Verify the fix works under high load

**Steps**:
1. Spawn 50+ villagers across the world
2. Set `periodic-check-interval-seconds: 10` (faster checks)
3. Let the server run for 5 minutes

**Expected Results**:
- No threading errors
- No performance degradation
- All villagers are processed correctly

### 4. Edge Cases

#### Test 4a: Villager in unloaded chunk
**Steps**:
1. Create a villager
2. Move far away so the chunk unloads
3. Wait for periodic check

**Expected**: No errors, villager is skipped gracefully

#### Test 4b: Villager removed during check
**Steps**:
1. Create villagers
2. During a periodic check, remove a villager with `/kill @e[type=villager,limit=1]`

**Expected**: No errors, removed villager is handled gracefully

#### Test 4c: No nearby villagers
**Steps**:
1. Create a single isolated villager with low hunger
2. Wait for periodic check

**Expected**: Log shows "no nearby villagers found", no errors

## Console Log Verification

### What to Look For (Good Signs)
```
[RealisticVillagers] Periodic hunger check scheduled (every X seconds)
[RealisticVillagers] Villager {name} got food from a neighbor!
[RealisticVillagers] Villager {name} is hungry (food: X) but no nearby villagers
```

### What to Avoid (Bad Signs)
```
[ERROR] Thread failed main thread check: Accessing entity state off owning region's thread
[WARN] Error during periodic hunger check
java.lang.IllegalStateException: Accessing entity state off thread
```

## Configuration Testing

Test with different configurations:

1. **Disabled periodic checks**:
   ```yaml
   periodic-check-enabled: false
   ```
   Expected: No periodic checks run, no errors

2. **Different intervals**:
   ```yaml
   periodic-check-interval-seconds: 60  # 1 minute
   periodic-check-interval-seconds: 300 # 5 minutes
   ```
   Expected: Checks run at correct intervals

3. **Different thresholds**:
   ```yaml
   request-food-threshold: 5
   request-food-threshold: 20
   ```
   Expected: Villagers request food at correct hunger levels

## Performance Monitoring

Monitor these metrics:
- Server TPS (should remain stable)
- Memory usage (should not increase significantly)
- Thread usage (should not spike during periodic checks)

Use commands:
- `/timings report` (if available)
- `/spark profiler start` (if Spark is installed)

## Regression Testing

Verify these features still work:
1. Villagers lose hunger when working
2. Villagers can't work when too hungry
3. Work item generation still functions
4. Food requests between villagers work
5. Physical item delivery works

## Success Criteria

The fix is successful if:
1. ✅ No "Thread failed main thread check" errors appear
2. ✅ Periodic hunger checks run at configured intervals
3. ✅ Hungry villagers successfully request food from nearby villagers
4. ✅ No performance degradation
5. ✅ All edge cases are handled gracefully
6. ✅ No regression in existing functionality

## Failure Handling

If tests fail:
1. Capture full error logs
2. Note the exact scenario that caused the failure
3. Check Folia version compatibility
4. Verify the fix was applied correctly
5. Report to developer with logs and steps to reproduce
