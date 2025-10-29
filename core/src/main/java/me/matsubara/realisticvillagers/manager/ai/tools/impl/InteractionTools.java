package me.matsubara.realisticvillagers.manager.ai.tools.impl;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.manager.ai.tools.AITool;
import me.matsubara.realisticvillagers.manager.ai.tools.AIToolResult;
import me.matsubara.realisticvillagers.manager.ai.tools.ToolCategory;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * Interaction-related AI tools for villagers.
 */
public class InteractionTools {

    /**
     * Tool that makes the villager shake their head (refusal gesture).
     */
    public static class ShakeHeadTool implements AITool {

        @Override
        public @NotNull String getName() {
            return "shake_head";
        }

        @Override
        public @NotNull String getDescription() {
            return "Shake head at player (refusal/negative gesture)";
        }

        @Override
        public @NotNull ToolCategory getCategory() {
            return ToolCategory.INTERACTION;
        }

        @Override
        public @NotNull Map<String, String> getParameters() {
            return Collections.emptyMap();
        }

        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            // Can't shake head if already doing so
            return !villager.isShakingHead();
        }

        @Override
        public @NotNull AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            villager.shakeHead(player);
            return AIToolResult.success("Shook head at " + player.getName());
        }
    }

    /**
     * Tool that stops the villager's current interaction.
     */
    public static class StopInteractionTool implements AITool {

        @Override
        public @NotNull String getName() {
            return "stop_interaction";
        }

        @Override
        public @NotNull String getDescription() {
            return "Stop current interaction with the player";
        }

        @Override
        public @NotNull ToolCategory getCategory() {
            return ToolCategory.INTERACTION;
        }

        @Override
        public @NotNull Map<String, String> getParameters() {
            return Collections.emptyMap();
        }

        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            // Can only stop if currently interacting
            return villager.isInteracting();
        }

        @Override
        public @NotNull AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            villager.stopInteracting();
            return AIToolResult.success("Stopped interaction with " + player.getName());
        }
    }

    /**
     * Tool that toggles the villager's fishing state.
     */
    public static class ToggleFishingTool implements AITool {

        @Override
        public @NotNull String getName() {
            return "toggle_fishing";
        }

        @Override
        public @NotNull String getDescription() {
            return "Start or stop fishing";
        }

        @Override
        public @NotNull ToolCategory getCategory() {
            return ToolCategory.INTERACTION;
        }

        @Override
        public @NotNull Map<String, String> getParameters() {
            return Collections.emptyMap();
        }

        @Override
        public boolean canExecute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            // Can't fish while fighting or in raid
            if (villager.isFighting()) return false;
            if (villager.isInsideRaid()) return false;

            return true;
        }

        @Override
        public @NotNull AIToolResult execute(@NotNull IVillagerNPC villager, @NotNull Player player, @NotNull Map<String, Object> args) {
            boolean wasFishing = villager.isFishing();

            villager.toggleFishing();

            if (wasFishing) {
                return AIToolResult.success("Stopped fishing");
            } else {
                return AIToolResult.success("Started fishing");
            }
        }
    }
}
