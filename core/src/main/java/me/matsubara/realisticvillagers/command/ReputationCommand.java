package me.matsubara.realisticvillagers.command;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.reputation.ReputationManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.util.*;

public class ReputationCommand implements CommandExecutor, TabCompleter {
    
    private final RealisticVillagers plugin;
    
    public ReputationCommand(RealisticVillagers plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }
        
        if (!player.hasPermission("realisticvillagers.reputation")) {
            player.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }
        
        if (args.length == 0) {
            showUsage(player);
            return true;
        }
        
        ReputationManager repManager = plugin.getReputationManager();
        if (repManager == null) {
            player.sendMessage("§cReputation manager is not initialized!");
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "check" -> {
                // Check reputation with nearest villager
                Villager targetVillager = getNearestVillager(player, 10);
                if (targetVillager == null) {
                    player.sendMessage("§cNo villager found within 10 blocks!");
                    return true;
                }
                
                Optional<IVillagerNPC> npcOpt = plugin.getConverter().getNPC(targetVillager);
                if (npcOpt.isEmpty()) {
                    player.sendMessage("§cThis is not a RealisticVillagers NPC!");
                    return true;
                }
                IVillagerNPC npc = npcOpt.get();
                
                player.sendMessage("§6=== Reputation Check ===");
                player.sendMessage("§eVillager: §f" + npc.getVillagerName());
                player.sendMessage("§eProfession: §f" + targetVillager.getProfession());
                player.sendMessage("§eVanilla Reputation: §f" + npc.getReputation(player.getUniqueId()));
                
                if (repManager.hasActiveProviders()) {
                    int totalRep = repManager.getTotalReputation(npc, player);
                    player.sendMessage("§eTotal Reputation: §a" + totalRep);
                    player.sendMessage("§eActive Providers: §f" + String.join(", ", repManager.getActiveProviderNames()));
                    
                    // Show AI reputation level
                    String level = getReputationLevel(totalRep);
                    player.sendMessage("§eAI Behavior Level: §b" + level.toUpperCase());
                } else {
                    player.sendMessage("§7No external reputation providers active");
                }
            }
            
            case "debug" -> {
                // Show debug info
                Map<String, Object> debugInfo = repManager.getDebugInfo(player);
                player.sendMessage("§6=== Reputation Debug Info ===");
                displayDebugInfo(player, debugInfo, "");
            }
            
            case "providers" -> {
                // List active providers
                player.sendMessage("§6=== Reputation Providers Status ===");
                List<String> providers = repManager.getActiveProviderNames();
                if (providers.isEmpty()) {
                    player.sendMessage("§7No external providers active");
                    player.sendMessage("§7");
                    player.sendMessage("§7To enable LamCore integration:");
                    player.sendMessage("§7  1. Install LamCore plugin");
                    player.sendMessage("§7  2. Enable vouch module in LamCore config");
                    player.sendMessage("§7  3. Set 'reputation.lamcore.enabled: true' in RV config");
                    player.sendMessage("§7  4. Reload both plugins");
                } else {
                    for (String provider : providers) {
                        player.sendMessage("§e- §a" + provider + " §f(Active)");
                    }
                }
                
                // Show configuration status
                player.sendMessage("§6=== Configuration Status ===");
                Map<String, Object> debug = repManager.getDebugInfo(player);
                if (debug.containsKey("providers")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> providerList = (List<Map<String, Object>>) debug.get("providers");
                    for (Map<String, Object> providerInfo : providerList) {
                        String name = (String) providerInfo.get("provider");
                        Boolean enabled = (Boolean) providerInfo.get("enabled");
                        if (name != null && enabled != null) {
                            String status = enabled ? "§aEnabled" : "§cDisabled";
                            player.sendMessage("§e" + name + ": " + status);
                            
                            if (providerInfo.containsKey("error")) {
                                player.sendMessage("§c  Error: " + providerInfo.get("error"));
                            }
                        }
                    }
                }
            }
            
            case "reload" -> {
                // Reload reputation config
                if (!player.hasPermission("realisticvillagers.reputation.reload")) {
                    player.sendMessage("§cYou don't have permission to reload!");
                    return true;
                }
                repManager.reload();
                player.sendMessage("§aReputation configuration reloaded!");
            }
            
            default -> showUsage(player);
        }
        
        return true;
    }
    
    private void showUsage(Player player) {
        player.sendMessage("§6=== Reputation Commands ===");
        player.sendMessage("§e/rvrep check §7- Check reputation with nearest villager");
        player.sendMessage("§e/rvrep debug §7- Show debug information");
        player.sendMessage("§e/rvrep providers §7- List active reputation providers");
        if (player.hasPermission("realisticvillagers.reputation.reload")) {
            player.sendMessage("§e/rvrep reload §7- Reload reputation configuration");
        }
    }
    
    private Villager getNearestVillager(Player player, double maxDistance) {
        List<Entity> nearby = player.getNearbyEntities(maxDistance, maxDistance, maxDistance);
        Villager nearest = null;
        double nearestDist = maxDistance * maxDistance;
        
        for (Entity entity : nearby) {
            if (!(entity instanceof Villager villager)) continue;
            
            double dist = entity.getLocation().distanceSquared(player.getLocation());
            if (dist < nearestDist) {
                nearest = villager;
                nearestDist = dist;
            }
        }
        
        return nearest;
    }
    
    @SuppressWarnings("unchecked")
    private void displayDebugInfo(Player player, Map<String, Object> info, String indent) {
        for (Map.Entry<String, Object> entry : info.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof Map) {
                player.sendMessage(indent + "§e" + key + ":");
                displayDebugInfo(player, (Map<String, Object>) value, indent + "  ");
            } else if (value instanceof List) {
                player.sendMessage(indent + "§e" + key + ":");
                for (Object item : (List<?>) value) {
                    if (item instanceof Map) {
                        displayDebugInfo(player, (Map<String, Object>) item, indent + "  ");
                    } else {
                        player.sendMessage(indent + "  §7- §f" + item);
                    }
                }
            } else {
                player.sendMessage(indent + "§e" + key + ": §f" + value);
            }
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            List<String> options = Arrays.asList("check", "debug", "providers");
            
            if (sender.hasPermission("realisticvillagers.reputation.reload")) {
                options = new ArrayList<>(options);
                options.add("reload");
            }
            
            String partial = args[0].toLowerCase();
            for (String option : options) {
                if (option.startsWith(partial)) {
                    completions.add(option);
                }
            }
            
            return completions;
        }
        
        return Collections.emptyList();
    }
    
    private String getReputationLevel(int reputation) {
        if (reputation <= -100) {
            return "hostile";
        } else if (reputation <= -30) {
            return "unfriendly";
        } else if (reputation <= 29) {
            return "neutral";
        } else if (reputation <= 99) {
            return "friendly";
        } else {
            return "beloved";
        }
    }
}