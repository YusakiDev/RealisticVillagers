package me.matsubara.realisticvillagers.task;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.apache.commons.lang3.RandomUtils;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.kyori.adventure.key.Key;

import java.util.List;

public class BabyTask {

    private final RealisticVillagers plugin;
    private final IVillagerNPC villager;
    private final Player player;
    private final boolean isBoy;
    private com.tcoded.folialib.wrapper.task.WrappedTask task;

    private int count = 0;
    private boolean success = false;

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public BabyTask(@NotNull RealisticVillagers plugin, Villager villager, Player player) {
        this.plugin = plugin;
        // No need to check if it's invalid, the main GUI can only be opened by valid villagers.
        this.villager = plugin.getConverter().getNPC(villager).get();
        this.player = player;
        this.isBoy = RandomUtils.nextBoolean();
    }

    public void start() {
        task = plugin.getFoliaLib().getImpl().runAtEntityTimer(villager.bukkit(), this::run, 0L, 20L);
    }
    
    public void cancel() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
    
    public void run() {
        if (++count == 10) {
            openInventory();
            cancel();
            return;
        }

        villager.jumpIfPossible();
        player.spawnParticle(Particle.HEART, villager.bukkit().getEyeLocation(), 3, 0.1d, 0.1d, 0.1d);
    }

    private void openInventory() {
        String sex = isBoy ? Config.BOY.asString() : Config.GIRL.asString();
        
        // Create a dialog for baby naming
        Dialog dialog = Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(Component.text("Name Your Baby", NamedTextColor.LIGHT_PURPLE))
                .body(List.of(
                    DialogBody.plainMessage(Component.text("Congratulations! You have a new baby " + sex.toLowerCase() + "!", NamedTextColor.GREEN)),
                    DialogBody.plainMessage(Component.text("Please enter a name for your baby:", NamedTextColor.YELLOW))
                ))
                .inputs(List.of(
                    DialogInput.text("baby_name", Component.text("Baby Name", NamedTextColor.AQUA))
                        .build()
                ))
                .build()
            )
            .type(DialogType.notice(
                ActionButton.builder(Component.text("Create Baby", TextColor.color(0xAEFFC1)))
                    .tooltip(Component.text("Click to create your baby with the chosen name"))
                    .action(DialogAction.customClick(
                        Key.key("realisticvillagers:baby/create"), 
                        null
                    ))
                    .build()
            ))
        );
        
        // Store the baby task data for later use in the dialog handler
        plugin.getFoliaLib().getImpl().runLater(() -> 
            plugin.getBabyDialogData().put(player.getUniqueId(), this)
        , 1L);
        
        // Show the dialog to the player (cast to Audience)
        ((net.kyori.adventure.audience.Audience) player).showDialog(dialog);
    }
    
    public void createBabyWithName(String babyName) {
        // Validate the name
        if (babyName == null || babyName.trim().isEmpty()) {
            // Use a random name from names config
            String sex = isBoy ? "male" : "female";
            babyName = plugin.getTracker().getRandomNameBySex(sex);
        } else {
            // Clean the name (remove invalid characters, limit length)
            babyName = babyName.trim().replaceAll("[^a-zA-Z0-9_]", "");
            if (babyName.length() > 16) {
                babyName = babyName.substring(0, 16);
            }
            if (babyName.isEmpty()) {
                babyName = "Baby_" + RandomUtils.nextInt(100, 999);
            }
        }
        
        // Create the baby
        long procreation = System.currentTimeMillis();
        player.getInventory().addItem(plugin.createBaby(isBoy, babyName, procreation, villager.bukkit().getUniqueId()));

        int reputation = Config.BABY_REPUTATION.asInt();
        if (reputation > 1) villager.addMinorPositive(player.getUniqueId(), reputation);
        villager.setProcreatingWith(null);
        villager.setLastProcreation(procreation);

        success = true;
        
        String sex = isBoy ? Config.BOY.asString() : Config.GIRL.asString();
        player.sendMessage("✓ Baby '" + babyName + "' (" + sex + ") has been created! Check your inventory.");
        
        // Clean up the dialog data
        plugin.getBabyDialogData().remove(player.getUniqueId());
    }
}