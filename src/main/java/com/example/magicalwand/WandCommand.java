package com.example.magicalwand;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class WandCommand implements CommandExecutor, TabCompleter {

    private final MagicalWandPlugin plugin;

    public WandCommand(MagicalWandPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "give" -> {
                if (!player.hasPermission("magicalwand.give")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                    return true;
                }
                int stage = 1;
                if (args.length >= 2) {
                    try {
                        stage = Integer.parseInt(args[1]);
                        if (stage < 1 || stage > 3) {
                            player.sendMessage(ChatColor.RED + "Stage must be between 1 and 3!");
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Invalid stage number!");
                        return true;
                    }
                }
                ItemStack wand = plugin.createWand(stage);
                player.getInventory().addItem(wand);
                player.sendMessage(ChatColor.GREEN + "Received Magical Wand " + plugin.getStageName(stage) + "!");
            }
            case "setstage" -> {
                if (!player.hasPermission("magicalwand.admin")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /mw setstage <stage>");
                    return true;
                }
                try {
                    int stage = Integer.parseInt(args[1]);
                    if (stage < 1 || stage > 3) {
                        player.sendMessage(ChatColor.RED + "Stage must be between 1 and 3!");
                        return true;
                    }
                    plugin.setPlayerStage(player, stage);
                    player.sendMessage(ChatColor.GREEN + "Wand stage set to " + plugin.getStageName(stage) + "!");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid stage number!");
                }
            }
            case "cooldown" -> {
                if (!player.hasPermission("magicalwand.admin")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /mw cooldown <seconds>");
                    return true;
                }
                try {
                    int seconds = Integer.parseInt(args[1]);
                    if (seconds < 0) {
                        player.sendMessage(ChatColor.RED + "Cooldown must be a positive number!");
                        return true;
                    }
                    plugin.setCooldownSeconds(seconds);
                    player.sendMessage(ChatColor.GREEN + "Wand cooldown set to " + seconds + " seconds!");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid number!");
                }
            }
            case "check" -> {
                ItemStack item = player.getInventory().getItemInMainHand();
                if (plugin.isWand(item)) {
                    int stage = plugin.getWandStage(item);
                    player.sendMessage(ChatColor.GOLD + "Wand Stage: " + ChatColor.AQUA + plugin.getStageName(stage));
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        int kills = meta.getPersistentDataContainer().getOrDefault(
                            new org.bukkit.NamespacedKey("magicalwand", "kills"),
                            org.bukkit.persistence.PersistentDataType.INTEGER, 0
                        );
                        player.sendMessage(ChatColor.GOLD + "Unique Kills: " + ChatColor.AQUA + kills);
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "You're not holding a Magical Wand!");
                }
            }
            case "reload" -> {
                if (!player.hasPermission("magicalwand.admin")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                    return true;
                }
                plugin.reloadConfig();
                plugin.loadConfigValues();
                player.sendMessage(ChatColor.GREEN + "Config reloaded!");
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Magical Wand Commands ===");
        player.sendMessage(ChatColor.AQUA + "/mw give [stage]" + ChatColor.WHITE + " - Give yourself a wand (default stage 1)");
        player.sendMessage(ChatColor.AQUA + "/mw setstage <stage>" + ChatColor.WHITE + " - Set your wand's stage (1-3) [Admin]");
        player.sendMessage(ChatColor.AQUA + "/mw cooldown <seconds>" + ChatColor.WHITE + " - Set wand cooldown [Admin]");
        player.sendMessage(ChatColor.AQUA + "/mw check" + ChatColor.WHITE + " - Check your wand's stage and kills");
        player.sendMessage(ChatColor.AQUA + "/mw reload" + ChatColor.WHITE + " - Reload config [Admin]");
        player.sendMessage(ChatColor.GRAY + "Right-click with wand to use. Kill players to upgrade stages!");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("give", "setstage", "cooldown", "check", "reload");
            return subCommands.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("setstage")) {
                return Arrays.asList("1", "2", "3").stream()
                    .filter(s -> s.startsWith(args[1]))
                    .collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("cooldown")) {
                return Arrays.asList("10", "30", "60", "120").stream()
                    .filter(s -> s.startsWith(args[1]))
                    .collect(Collectors.toList());
            }
        }
        return completions;
    }
}
