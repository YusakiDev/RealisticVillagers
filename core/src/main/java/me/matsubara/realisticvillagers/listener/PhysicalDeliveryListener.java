package me.matsubara.realisticvillagers.listener;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.util.PhysicalItemDelivery;
import org.bukkit.entity.Item;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Handles villager pickup events for physical item delivery system
 * Ensures only the intended recipient can pick up delivered items
 */
public class PhysicalDeliveryListener implements Listener {
    
    private final RealisticVillagers plugin;
    
    public PhysicalDeliveryListener(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onVillagerPickupItem(EntityPickupItemEvent event) {
        // Only handle villager pickups
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        
        // Check if physical delivery is enabled
        if (!PhysicalItemDelivery.isPhysicalInteractionEnabled()) {
            return;
        }
        
        Item droppedItem = event.getItem();
        
        // Check if this item has an intended recipient
        IVillagerNPC villagerNPC = plugin.getConverter().getNPC(villager).orElse(null);
        if (villagerNPC == null) {
            return;
        }
        
        // Debug: Log all pickup attempts
        if (droppedItem.hasMetadata("IntendedRecipient") || droppedItem.hasMetadata("ItemProvider")) {
            String intendedFor = droppedItem.hasMetadata("IntendedRecipient") ? 
                    droppedItem.getMetadata("IntendedRecipient").get(0).asString() : "none";
            String providedBy = droppedItem.hasMetadata("ItemProvider") ? 
                    droppedItem.getMetadata("ItemProvider").get(0).asString() : "none";
            plugin.getLogger().info(String.format("Villager %s (UUID: %s) attempting to pick up delivery item %s. IntendedFor: %s, ProvidedBy: %s", 
                    villagerNPC.getVillagerName(), villager.getUniqueId().toString(), 
                    droppedItem.getItemStack().getType().name(), intendedFor, providedBy));
        }
        
        // If the item is intended for someone specific
        if (PhysicalItemDelivery.isItemForVillager(droppedItem, villagerNPC)) {
            // This villager is the intended recipient - allow pickup
            plugin.getLogger().info(String.format("PhysicalDeliveryListener: Villager %s is intended recipient, allowing pickup of %s", 
                    villagerNPC.getVillagerName(), droppedItem.getItemStack().getType().name()));
            return;
        }
        
        // Check if this villager was the provider who dropped the item
        if (droppedItem.hasMetadata("ItemProvider")) {
            String providerUUID = droppedItem.getMetadata("ItemProvider").get(0).asString();
            if (villager.getUniqueId().toString().equals(providerUUID)) {
                // This villager dropped this item - prevent them from picking it up
                event.setCancelled(true);
                plugin.getLogger().info(String.format("Prevented provider %s from picking up item they dropped for delivery", 
                        villagerNPC.getVillagerName()));
                return;
            }
        }
        
        // Check if this item was intended for someone else
        if (droppedItem.hasMetadata("IntendedRecipient")) {
            // This item is for someone else - prevent pickup
            event.setCancelled(true);
            plugin.getLogger().info(String.format("PhysicalDeliveryListener: Prevented villager %s from picking up item intended for another villager", 
                    villagerNPC.getVillagerName()));
            return;
        }
        
        // If no metadata, allow normal pickup behavior
    }
}