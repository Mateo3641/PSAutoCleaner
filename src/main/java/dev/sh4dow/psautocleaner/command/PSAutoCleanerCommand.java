package dev.sh4dow.psautocleaner.command;

import dev.sh4dow.psautocleaner.core.config.CleanConfig;
import dev.sh4dow.psautocleaner.core.config.ConfigManager;
import dev.sh4dow.psautocleaner.core.message.MessageService;
import dev.sh4dow.psautocleaner.model.CleanupAction;
import dev.sh4dow.psautocleaner.model.InactivityProfile;
import dev.sh4dow.psautocleaner.model.RegionInactivitySummary;
import dev.sh4dow.psautocleaner.service.CleanerService;
import dev.sh4dow.psautocleaner.service.InactivityService;
import dev.sh4dow.psautocleaner.service.RegionInspectorService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Command executor handling all PSAutoCleaner subcommands.
 */
public class PSAutoCleanerCommand implements CommandExecutor {

    private static final String PERM_ADMIN = "psautocleaner.admin";
    private static final String PERM_CHECK = "psautocleaner.check";
    private static final String PERM_CHECK_OTHERS = "psautocleaner.check.others";

    private final ConfigManager configManager;
    private final MessageService messageService;
    private final InactivityService inactivityService;
    private final CleanerService cleanerService;
    private final RegionInspectorService regionInspectorService;

    public PSAutoCleanerCommand(
            ConfigManager configManager,
            MessageService messageService,
            InactivityService inactivityService,
            CleanerService cleanerService,
            RegionInspectorService regionInspectorService
    ) {
        this.configManager = configManager;
        this.messageService = messageService;
        this.inactivityService = inactivityService;
        this.cleanerService = cleanerService;
        this.regionInspectorService = regionInspectorService;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "check" -> handleCheck(sender, args);
            case "region" -> handleRegion(sender);
            case "status" -> handleStatus(sender);
            case "run" -> handleRun(sender, args, false);
            case "preview" -> handleRun(sender, args, true);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleCheck(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_CHECK)) {
            messageService.send(sender, "no-permission", Collections.emptyMap());
            return;
        }

        OfflinePlayer target;
        if (args.length > 1) {
            if (!sender.hasPermission(PERM_CHECK_OTHERS)) {
                messageService.send(sender, "no-permission", Collections.emptyMap());
                return;
            }
            String targetName = args[1];
            target = Bukkit.getOfflinePlayer(targetName);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                messageService.send(sender, "player-not-found", Map.of("player", targetName));
                return;
            }
        } else {
            if (!(sender instanceof Player player)) {
                messageService.send(sender, "player-only", Collections.emptyMap());
                return;
            }
            target = player;
        }

        InactivityProfile profile = inactivityService.calculate(target);
        String timeInactive = inactivityService.formatDuration(profile.inactiveMillis());
        String timeRemaining = inactivityService.formatDuration(profile.millisRemaining());

        messageService.sendWithoutPrefix(sender, "check.header", Collections.emptyMap());
        messageService.sendWithoutPrefix(sender, "check.player", Map.of("player", profile.playerName()));

        if (profile.isExempt()) {
            messageService.sendWithoutPrefix(sender, "check.status-exempt", Collections.emptyMap());
            messageService.sendWithoutPrefix(sender, "check.time-remaining", Map.of("time_remaining", "Never (Exempt)"));
        } else if (profile.isExpired()) {
            messageService.sendWithoutPrefix(sender, "check.status-expired", Map.of(
                    "days_inactive", String.valueOf(profile.daysInactive()),
                    "time_inactive", timeInactive
            ));
            messageService.sendWithoutPrefix(sender, "check.expired-warning", Collections.emptyMap());
        } else if (profile.isOnline()) {
            messageService.sendWithoutPrefix(sender, "check.status-active", Collections.emptyMap());
            messageService.sendWithoutPrefix(sender, "check.time-remaining", Map.of("time_remaining", timeRemaining));
        } else {
            messageService.sendWithoutPrefix(sender, "check.status-inactive", Map.of(
                    "days_inactive", String.valueOf(profile.daysInactive()),
                    "time_inactive", timeInactive
            ));
            messageService.sendWithoutPrefix(sender, "check.time-remaining", Map.of("time_remaining", timeRemaining));
        }
    }

    private void handleRegion(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "player-only", Collections.emptyMap());
            return;
        }
        if (!player.hasPermission(PERM_CHECK)) {
            messageService.send(sender, "no-permission", Collections.emptyMap());
            return;
        }

        Optional<RegionInactivitySummary> optSummary = regionInspectorService.inspectLocation(player.getLocation());
        if (optSummary.isEmpty()) {
            messageService.send(player, "region.not-in-region", Collections.emptyMap());
            return;
        }

        RegionInactivitySummary summary = optSummary.get();
        messageService.sendWithoutPrefix(player, "region.header", Collections.emptyMap());
        messageService.sendWithoutPrefix(player, "region.region-id", Map.of(
                "region", summary.regionId(),
                "world", summary.worldName()
        ));

        if (!summary.hasOwner()) {
            messageService.sendWithoutPrefix(player, "region.no-owner", Collections.emptyMap());
            return;
        }

        messageService.sendWithoutPrefix(player, "region.owner", Map.of("owner", summary.ownerName()));

        InactivityProfile profile = summary.ownerProfile();
        if (profile != null) {
            if (profile.isExempt()) {
                messageService.sendWithoutPrefix(player, "region.owner-exempt", Collections.emptyMap());
            } else if (profile.isExpired()) {
                messageService.sendWithoutPrefix(player, "region.owner-expired", Collections.emptyMap());
            } else if (profile.isOnline()) {
                messageService.sendWithoutPrefix(player, "region.owner-active", Map.of(
                        "time_remaining", inactivityService.formatDuration(profile.millisRemaining())
                ));
            } else {
                messageService.sendWithoutPrefix(player, "region.owner-inactive", Map.of(
                        "days_inactive", String.valueOf(profile.daysInactive()),
                        "time_remaining", inactivityService.formatDuration(profile.millisRemaining())
                ));
            }
        }
    }

    private void handleStatus(CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            messageService.send(sender, "no-permission", Collections.emptyMap());
            return;
        }

        CleanConfig config = configManager.getCleanConfig();
        String nextRun = cleanerService.getNextScheduledRun()
                .map(instant -> {
                    long millis = Duration.between(Instant.now(), instant).toMillis();
                    return millis > 0 ? inactivityService.formatDuration(millis) : "Imminent";
                })
                .orElse("Disabled");

        String lastRun = cleanerService.getLastResult()
                .map(r -> String.format("%d worlds processed in %dms (%s)", r.worldsProcessed(), r.durationMillis(), r.action().name()))
                .orElse("None since boot");

        String worlds = config.targetWorlds().isEmpty() ? "All loaded worlds" : String.join(", ", config.targetWorlds());

        messageService.sendWithoutPrefix(sender, "status.header", Collections.emptyMap());
        messageService.sendWithoutPrefix(sender, "status.enabled", Map.of("value", config.enabled() ? "<green>Yes</green>" : "<red>No</red>"));
        messageService.sendWithoutPrefix(sender, "status.threshold", Map.of("days", String.valueOf(config.inactiveDays())));
        messageService.sendWithoutPrefix(sender, "status.action", Map.of("action", config.cleanupAction().getCommandArg()));
        messageService.sendWithoutPrefix(sender, "status.interval", Map.of("hours", String.valueOf(config.intervalHours())));
        messageService.sendWithoutPrefix(sender, "status.next-run", Map.of("time", nextRun));
        messageService.sendWithoutPrefix(sender, "status.target-worlds", Map.of("worlds", worlds));
        messageService.sendWithoutPrefix(sender, "status.last-run", Map.of("time", lastRun));
    }

    private void handleRun(CommandSender sender, String[] args, boolean isPreview) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            messageService.send(sender, "no-permission", Collections.emptyMap());
            return;
        }

        CleanConfig config = configManager.getCleanConfig();
        CleanupAction action = isPreview ? CleanupAction.PREVIEW : config.cleanupAction();
        int days = config.inactiveDays();
        List<String> explicitWorlds = Collections.emptyList();

        // Parameter parsing: /psac run [action] [days] [world]
        // or /psac preview [days] [world]
        int argOffset = 1;
        if (!isPreview && args.length > argOffset) {
            Optional<CleanupAction> parsedAction = CleanupAction.fromString(args[argOffset]);
            if (parsedAction.isPresent()) {
                action = parsedAction.get();
                argOffset++;
            }
        }

        if (args.length > argOffset) {
            try {
                days = Integer.parseInt(args[argOffset]);
                argOffset++;
            } catch (NumberFormatException ignored) {
            }
        }

        if (args.length > argOffset) {
            explicitWorlds = List.of(args[argOffset]);
        }

        cleanerService.executeSweep(action, days, explicitWorlds, sender);
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            messageService.send(sender, "no-permission", Collections.emptyMap());
            return;
        }

        configManager.load();
        cleanerService.startScheduledTask();
        messageService.send(sender, "reload-success", Collections.emptyMap());
    }

    private void sendHelp(CommandSender sender) {
        messageService.sendWithoutPrefix(sender, "help.header", Collections.emptyMap());
        messageService.sendWithoutPrefix(sender, "help.check", Collections.emptyMap());
        messageService.sendWithoutPrefix(sender, "help.region", Collections.emptyMap());
        messageService.sendWithoutPrefix(sender, "help.status", Collections.emptyMap());

        if (sender.hasPermission(PERM_ADMIN)) {
            messageService.sendWithoutPrefix(sender, "help.run", Collections.emptyMap());
            messageService.sendWithoutPrefix(sender, "help.preview", Collections.emptyMap());
            messageService.sendWithoutPrefix(sender, "help.reload", Collections.emptyMap());
        }
    }
}
