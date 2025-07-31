package me.matsubara.realisticvillagers.entity.v1_21_7.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_7.villager.VillagerNPC;
import me.matsubara.realisticvillagers.util.EquipmentManager;
import me.matsubara.realisticvillagers.util.ItemStackUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_21_R5.inventory.CraftItemStack;

public class CheckInventory extends Behavior<Villager> {

    private int tryAgain;
    private static final int TRY_AGAIN_COOLDOWN = 100;

    public CheckInventory() {
        super(ImmutableMap.of());
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (tryAgain > 0) {
            tryAgain--;
            return false;
        }

        return villager instanceof VillagerNPC npc
                && !npc.is(VillagerProfession.FISHERMAN)
                && !npc.isSleeping()
                && !npc.checkCurrentActivity(Activity.WORK)
                && (!npc.isHoldingWeapon() || needsArmor(npc))
                && npc.isDoingNothing(true);
    }

    private boolean needsArmor(VillagerNPC npc) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HAND) continue;
            if (npc.getItemBySlot(slot).isEmpty()) return true;
        }
        return false;
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        if (!(villager instanceof VillagerNPC npc)) return;

        // Check if villager should have combat equipment based on threat-based mode
        boolean shouldHaveCombatGear = EquipmentManager.shouldHaveCombatEquipment(npc);
        boolean currentlyHasCombatGear = npc.isHoldingWeapon() || hasAnyArmor(npc);
        
        
        if (shouldHaveCombatGear && !currentlyHasCombatGear) {
            // Need to equip combat gear
            EquipmentManager.equipCombatGear(npc);
            // Use the original equipping logic since equipCombatGear is now just a placeholder
            equipBetterGearFromInventory(npc);
        } else if (!shouldHaveCombatGear && currentlyHasCombatGear) {
            // Need to store combat gear back in inventory
            EquipmentManager.storeCombatEquipment(npc);
        } else if (shouldHaveCombatGear) {
            // Already has some gear but check if we can equip better items
            equipBetterGearFromInventory(npc);
        }

        // Only try again in 5 seconds if villager isn't fighting.
        if (!npc.isFighting()) {
            tryAgain = TRY_AGAIN_COOLDOWN;
        }
    }
    
    /**
     * Checks if the villager has any armor equipped.
     */
    private boolean hasAnyArmor(VillagerNPC npc) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HAND) continue;
            if (!npc.getItemBySlot(slot).isEmpty()) return true;
        }
        return false;
    }
    
    /**
     * Legacy method to equip better gear from inventory (original behavior).
     */
    private void equipBetterGearFromInventory(VillagerNPC npc) {
        SimpleContainer inventory = npc.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack item = inventory.getItem(i);

            CraftItemStack bukkitItem = CraftItemStack.asCraftMirror(item);
            if (!ItemStackUtils.isWeapon(bukkitItem)
                    && ItemStackUtils.getSlotByItem(bukkitItem) == null
                    && bukkitItem.getType() != Material.SHIELD) continue;

            // Equip armor/weapon if it's better than current.
            if (ItemStackUtils.setBetterWeaponInMaindHand(
                    npc.bukkit(),
                    bukkitItem,
                    false,
                    ItemStackUtils.isMeleeWeapon(bukkitItem) && !npc.isHoldingRangeWeapon())
                    || ItemStackUtils.setArmorItem(npc.bukkit(), bukkitItem, false)) {
                item.shrink(1);
            }
        }
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return false;
    }
    
    /**
     * Public method to immediately equip combat gear for a villager who needs it.
     * This bypasses the normal cooldown and can be called when a villager gets targeted.
     * 
     * @param villager The villager who needs immediate equipment
     */
    public static void equipImmediately(VillagerNPC villager) {
        if (villager == null) return;
        
        // Check if villager should have combat equipment
        boolean shouldHaveCombatGear = me.matsubara.realisticvillagers.util.EquipmentManager.shouldHaveCombatEquipment(villager);
        if (!shouldHaveCombatGear) return;
        
        // Use the same logic as the normal CheckInventory behavior
        SimpleContainer inventory = villager.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack item = inventory.getItem(i);

            CraftItemStack bukkitItem = CraftItemStack.asCraftMirror(item);
            if (!ItemStackUtils.isWeapon(bukkitItem)
                    && ItemStackUtils.getSlotByItem(bukkitItem) == null
                    && bukkitItem.getType() != Material.SHIELD) continue;

            // Equip armor/weapon if it's better than current.
            if (ItemStackUtils.setBetterWeaponInMaindHand(
                    villager.bukkit(),
                    bukkitItem,
                    false,
                    ItemStackUtils.isMeleeWeapon(bukkitItem) && !villager.isHoldingRangeWeapon())
                    || ItemStackUtils.setArmorItem(villager.bukkit(), bukkitItem, false)) {
                item.shrink(1);
            }
        }
        
    }
}