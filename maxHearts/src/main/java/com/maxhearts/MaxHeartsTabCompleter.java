package com.maxhearts;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MaxHeartsTabCompleter implements TabCompleter {
    private static final List<String> SUBS = Arrays.asList(
            "set", "give", "take", "donate", "transfer", "get"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String start = args[0].toLowerCase();
            return SUBS.stream().filter(s -> s.startsWith(start)).collect(Collectors.toList());
        }
        if (args.length == 2) {
            String start = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(start))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("transfer")) {
            String start = args[2].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(start))
                    .collect(Collectors.toList());
        }
        return List.of(); // no numeric suggestions
    }
}

