package me.matsubara.realisticvillagers.util;

/**
 * Example of how to integrate WorkHungerIntegration into existing work behaviors
 * This shows the pattern for adding hunger decrease and food requests to work actions
 */
public class WorkBehaviorExample {
    
    /**
     * Example integration for HarvestFarmland behavior
     * This would be added to the existing HarvestFarmland.tick() method
     */
    public void exampleHarvestFarmlandIntegration() {
        /*
        // This is how you would modify the existing HarvestFarmland.tick() method:
        
        @Override
        public void tick(ServerLevel level, Villager villager, long time) {
            // ... existing harvest logic ...
            
            // NEW: When villager successfully performs a work action, decrease hunger
            boolean didWork = false;
            
            // Check if villager destroyed a crop (work action)
            if (isValidCrop(aboveState) && !callEntityChangeBlockEvent(villager, aboveFarmlandPos, Blocks.AIR.defaultBlockState()).isCancelled()) {
                level.destroyBlock(aboveFarmlandPos, true, villager);
                didWork = true; // Harvesting crops is work!
            }
            
            // Check if villager planted seeds (work action)
            Triple<Integer, ItemStack, BlockState> wantedSeed = getWantedSeed(villager, aboveState, belowState, true);
            if (wantedSeed != null) {
                // ... existing planting logic ...
                didWork = true; // Planting seeds is work!
            }
            
            // Check if villager converted dirt to farmland (work action)
            if (isValidDirt(level, aboveFarmlandPos, aboveState, belowState)) {
                // ... existing conversion logic ...
                didWork = true; // Converting dirt is work!
            }
            
            // NEW: If villager did work, decrease hunger and potentially request food
            if (didWork && villager instanceof VillagerNPC npc) {
                WorkHungerIntegration.onVillagerWorkWithPlugin(npc, plugin);
            }
            
            // ... rest of existing logic ...
        }
        */
    }
    
    /**
     * Example integration for StartFishing behavior
     * This would be added to the existing StartFishing behavior
     */
    public void exampleStartFishingIntegration() {
        /*
        // This would be added to a fishing behavior when a fish is caught:
        
        // In the fishing success logic:
        if (fishCaught && villager instanceof VillagerNPC npc) {
            // Fishing is hard work! Decrease hunger.
            WorkHungerIntegration.onVillagerWorkWithPlugin(npc, plugin);
        }
        */
    }
    
    /**
     * Example integration for any work behavior
     * This is the general pattern to follow
     */
    public void generalWorkIntegrationPattern() {
        /*
        // General pattern for ANY work behavior:
        
        @Override
        public void tick(ServerLevel level, Villager villager, long time) {
            // ... existing work logic ...
            
            boolean performedWorkAction = false;
            
            // Check if villager did something that counts as "work"
            if (villager_did_something_productive) {
                // ... existing work logic ...
                performedWorkAction = true;
            }
            
            // If work was done, handle hunger decrease and food requests
            if (performedWorkAction && villager instanceof VillagerNPC npc) {
                WorkHungerIntegration.onVillagerWorkWithPlugin(npc, getPlugin());
            }
            
            // ... rest of existing logic ...
        }
        */
    }
    
    /**
     * What counts as "work" that should decrease hunger:
     * 
     * FARMER:
     * - Harvesting crops ✓
     * - Planting seeds ✓
     * - Converting dirt to farmland ✓
     * - Using bone meal ✓
     * 
     * FISHERMAN:
     * - Catching fish ✓
     * - Casting fishing line ✓
     * 
     * LIBRARIAN:
     * - Enchanting items ✓
     * - Reading/writing books ✓
     * 
     * TOOLSMITH/WEAPONSMITH/ARMORER:
     * - Crafting tools/weapons/armor ✓
     * - Using anvil ✓
     * - Using grindstone ✓
     * 
     * GENERAL:
     * - Any profession-specific productive activity ✓
     * - Moving heavy items ✓
     * - Crafting/processing materials ✓
     */
    
    /**
     * Different integration options based on trigger configuration:
     */
    public void triggerExamples() {
        /*
        // 1. AFTER WORK TRIGGER (immediate)
        // In work behaviors like HarvestFarmland.tick():
        if (didWork && villager instanceof VillagerNPC npc) {
            WorkHungerIntegration.onVillagerWorkWithPlugin(npc, plugin);
        }
        
        // 2. DURING REST TRIGGER (social)
        // In rest/idle behaviors:
        if (isResting && villager instanceof VillagerNPC npc) {
            WorkHungerIntegration.onVillagerRest(npc, plugin);
        }
        
        // 3. ON INTERACTION TRIGGER (social sharing)
        // When villagers meet/talk:
        if (villager1 instanceof VillagerNPC npc1 && villager2 instanceof VillagerNPC npc2) {
            WorkHungerIntegration.onVillagerInteraction(npc1, npc2, plugin);
        }
        
        // 4. PERIODIC CHECK TRIGGER (scheduled task)
        // In plugin's scheduled task (runs every X seconds):
        List<IVillagerNPC> allVillagers = getAllVillagers();
        WorkHungerIntegration.periodicHungerCheck(allVillagers, plugin);
        */
    }

    /**
     * Configuration options in config.yml:
     */
    public void configurationExample() {
        /*
        # Villager request system
        villager-requests:
          enabled: true
          min-keep-food: 3
          min-keep-tools: 1
          generosity-factor: 0.6
          # Physical interaction mode (walking and dropping items)
          physical-interaction:
            enabled: true                    # Villagers walk to each other and drop items
            max-delivery-distance: 15.0     # Max distance for physical delivery
            item-claim-duration: 30         # How long items stay marked (seconds)
            delivery-walk-speed: 1.2        # Walk speed multiplier during delivery
        
        # Work and hunger settings
        work-hunger:
          enabled: true
          hunger-decrease-per-work: 2
          request-food-threshold: 5
          nearby-villager-range: 20.0
          check-triggers:
            after-work: true          # Check right after work (immediate need)
            during-rest: true         # Check during rest time (social gathering)
            on-villager-interaction: false  # Check when villagers meet
            periodic-check:
              enabled: false          # Scheduled background checks
              interval-seconds: 300   # Every 5 minutes
        */
    }
    
    /**
     * How the physical delivery system works:
     */
    public void physicalDeliveryExplanation() {
        /*
        PHYSICAL DELIVERY PROCESS:
        
        1. HUNGER CHECK:
           - Villager gets hungry (hunger < 5)
           - System finds nearby villagers with food
        
        2. REQUEST INITIATION:
           - Hungry villager "requests" food from provider
           - Provider checks if they can spare food (keeps min 3, shares 60% of excess)
        
        3. PHYSICAL DELIVERY:
           - Provider villager starts walking toward the requester
           - When provider gets within 3 blocks of requester, they drop the item
           - Dropped item has special metadata marking it for the specific requester
        
        4. ITEM PICKUP:
           - Only the intended recipient can pick up the marked item
           - Other villagers are prevented from taking it (for 30 seconds)
           - After 30 seconds, metadata is removed and anyone can pick it up
        
        WHAT PLAYERS SEE:
        ✓ Villager A walks toward Villager B  
        ✓ Villager A drops food item near Villager B
        ✓ Villager B picks up the food item
        ✓ Only Villager B can pick up that specific item (others are blocked)
        
        FALLBACK MODE:
        - If physical-interaction.enabled = false
        - Falls back to instant inventory transfers (original system)
        */
    }
}