package dev.sh4dow.psautocleaner.command;

import dev.sh4dow.psautocleaner.model.CleanupAction;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tab completer providing auto-completions for all PSAutoCleaner subcommands.
 */
public class PSAutoCleanerTabCompleter implements TabCompleter {

    private static final String PERM_ADMIN = "psautocleaner.admin";
    private static final String PERM_CHECK_OTHERS = "psautocleaner.check.others";

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("check", "region", "help"));
            if (sender.hasPermission(PERM_ADMIN)) {
                subs.addAll(List.of("status", "run", "preview", "reload"));
            }
            return filter(subs, args[0]);
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("check") && args.length == 2 && sender.hasPermission(PERM_CHECK_OTHERS)) {
            List<String> players = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            return filter(players, args[1]);
        }

        if (sub.equals("run") && sender.hasPermission(PERM_ADMIN)) {
            if (args.length == 2) {
                List<String> actions = Arrays.stream(CleanupAction.values())
                        .map(CleanupAction::getCommandArg)
                        .toList();
                return filter(actions, args[1]);
            }
            if (args.length == 3) {
                return filter(List.of("14", "30", "60", "90"), args[2]);
            }
            if (args.length == 4) {
                List<String> worlds = Bukkit.getWorlds().stream().map(World::getName).toList();
                return filter(worlds, args[3]);
            }
        }

        if (sub.equals("preview") && sender.hasPermission(PERM_ADMIN)) {
            if (args.length == 2) {
                return filter(List.of("14", "30", "60", "90"), args[1]);
            }
            if (args.length == 3) {
                List<String> worlds = Bukkit.getWorlds().stream().map(World::getName).toList();
                return filter(worlds, args[2]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> candidates, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return candidates;
        }
        String lower = prefix.toLowerCase();
        return candidates.stream()
                .filter(c -> c.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}
