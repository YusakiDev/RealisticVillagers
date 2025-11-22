package me.matsubara.realisticvillagers.manager.ai.tools.impl;

import me.matsubara.realisticvillagers.data.InteractType;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.manager.ai.tools.AITool;
import me.matsubara.realisticvillagers.manager.ai.tools.AIToolResult;
import me.matsubara.realisticvillagers.manager.ai.tools.ToolCategory;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * Movement-related AI tools for villagers.
 */
public class MovementTools {

    /**
     * Tool that makes the villager follow a player.
     */
    public static class FollowPlayerTool implements AITool {

        @Override
        public @NotNull String getName() {
            return "follow_player";
        }

        @Override
        public @NotNull String getDescription() {
            return "Start following the player";
        }

        @Override
        public @NotNull ToolCategory getCategory() {
            return ToolCategory.MOVEMENT;
        }

        @Override
        public @NotNull Map<String, String> getParameters() {
            return Collections.emptyMap();
        }

        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            // Can't follow if fighting, in a raid, or already following
            if (villager.isFighting()) return false;
            if (villager.isInsideRaid()) return false;
            if (villager.isFollowing()) return false;

            return true;
        }

        @Override
        public @NotNull AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            villager.setInteractingWithAndType(player.getUniqueId(), InteractType.FOLLOW_ME);
            return AIToolResult.success("Started following " + player.getName());
        }
    }

    /**
     * Tool that makes the villager stay in their current location.
     */
    public static class StayHereTool implements AITool {

        @Override
        public @NotNull String getName() {
            return "stay_here";
        }

        @Override
        public @NotNull String getDescription() {
            return "Stay in current location";
        }

        @Override
        public @NotNull ToolCategory getCategory() {
            return ToolCategory.MOVEMENT;
        }

        @Override
        public @NotNull Map<String, String> getParameters() {
            return Collections.emptyMap();
        }

        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            // Can't stay if fighting, in a raid, or already staying
            if (villager.isFighting()) return false;
            if (villager.isInsideRaid()) return false;
            if (villager.isStayingInPlace()) return false;

            return true;
        }

        @Override
        public @NotNull AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            villager.setInteractingWithAndType(player.getUniqueId(), InteractType.STAY_HERE);
            return AIToolResult.success("Now staying in place");
        }
    }

    /**
     * Tool that stops the villager's current movement behavior (follow/stay).
     */
    public static class StopMovementTool implements AITool {

        @Override
        public @NotNull String getName() {
            return "stop_movement";
        }

        @Override
        public @NotNull String getDescription() {
            return "Stop following or staying, resume normal behavior";
        }

        @Override
        public @NotNull ToolCategory getCategory() {
            return ToolCategory.MOVEMENT;
        }

        @Override
        public @NotNull Map<String, String> getParameters() {
            return Collections.emptyMap();
        }

        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            // Can only stop if currently following or staying
            return villager.isFollowing() || villager.isStayingInPlace();
        }

        @Override
        public @NotNull AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            boolean wasFollowing = villager.isFollowing();
            boolean wasStaying = villager.isStayingInPlace();

            villager.stopInteracting();

            if (wasFollowing) {
                return AIToolResult.success("Stopped following, resuming normal behavior");
            } else if (wasStaying) {
                return AIToolResult.success("Stopped staying in place, resuming normal behavior");
            } else {
                return AIToolResult.success("Stopped movement behavior");
            }
        }
    }
}
