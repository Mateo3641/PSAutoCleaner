package dev.sh4dow.psautocleaner.hook;

import dev.sh4dow.psautocleaner.PSAutoCleanerPlugin;
import dev.sh4dow.psautocleaner.core.config.ConfigManager;
import dev.sh4dow.psautocleaner.model.InactivityProfile;
import dev.sh4dow.psautocleaner.model.RegionInactivitySummary;
import dev.sh4dow.psautocleaner.service.CleanerService;
import dev.sh4dow.psautocleaner.service.InactivityService;
import dev.sh4dow.psautocleaner.service.RegionInspectorService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * PlaceholderAPI expansion providing placeholders for inactivity metrics,
 * claim expiration timers, and standing region inspection.
 */
public class PlaceholderApiHook extends PlaceholderExpansion {

    private final PSAutoCleanerPlugin plugin;
    private final ConfigManager configManager;
    private final InactivityService inactivityService;
    private final CleanerService cleanerService;
    private final RegionInspectorService regionInspectorService;

    public PlaceholderApiHook(
            PSAutoCleanerPlugin plugin,
            ConfigManager configManager,
            InactivityService inactivityService,
            CleanerService cleanerService,
            RegionInspectorService regionInspectorService
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.inactivityService = inactivityService;
        this.cleanerService = cleanerService;
        this.regionInspectorService = regionInspectorService;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "psautocleaner";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        String param = params.toLowerCase();

        // Server-wide placeholders (independent of player)
        if (param.equals("threshold_days")) {
            return String.valueOf(configManager.getCleanConfig().inactiveDays());
        }
        if (param.equals("threshold_time")) {
            return dev.sh4dow.psautocleaner.util.DurationParser.formatShort(configManager.getCleanConfig().inactiveDuration());
        }
        if (param.equals("interval")) {
            return dev.sh4dow.psautocleaner.util.DurationParser.formatShort(configManager.getCleanConfig().intervalDuration());
        }
        if (param.equals("cleanup_action")) {
            return configManager.getCleanConfig().cleanupAction().getCommandArg();
        }
        if (param.equals("next_cleanup")) {
            return cleanerService.getNextScheduledRun()
                    .map(instant -> {
                        long millis = Duration.between(Instant.now(), instant).toMillis();
                        return millis > 0 ? inactivityService.formatDuration(millis) : "Imminent";
                    })
                    .orElse("Disabled");
        }

        // Region placeholders (require online player to obtain location)
        if (param.startsWith("current_region_")) {
            if (offlinePlayer == null || !offlinePlayer.isOnline()) {
                return "";
            }
            Player onlinePlayer = offlinePlayer.getPlayer();
            if (onlinePlayer == null) {
                return "";
            }

            Optional<RegionInactivitySummary> optSummary = regionInspectorService.inspectLocation(onlinePlayer.getLocation());
            if (optSummary.isEmpty()) {
                return "None";
            }

            RegionInactivitySummary summary = optSummary.get();
            String sub = param.substring("current_region_".length());

            return switch (sub) {
                case "id" -> summary.regionId();
                case "owner" -> summary.ownerName() != null ? summary.ownerName() : "None";
                case "days_left" -> summary.getProfile()
                        .map(p -> p.isExempt() ? "Never" : String.valueOf(p.daysRemaining()))
                        .orElse("N/A");
                case "time_left" -> summary.getProfile()
                        .map(p -> p.isExempt() ? "Never" : inactivityService.formatDuration(p.millisRemaining()))
                        .orElse("N/A");
                case "days_inactive" -> summary.getProfile()
                        .map(p -> String.valueOf(p.daysInactive()))
                        .orElse("N/A");
                case "status" -> summary.getProfile()
                        .map(inactivityService::getStatusDescriptor)
                        .orElse("N/A");
                case "most_active_player" -> summary.mostActivePlayerName() != null ? summary.mostActivePlayerName() : "None";
                case "most_active_role" -> summary.mostActiveRole() != null ? summary.mostActiveRole() : "owner";
                case "is_protected_by_member" -> String.valueOf(summary.isProtectedByMember());
                default -> null;
            };
        }

        // Player-specific placeholders
        if (offlinePlayer == null) {
            return "";
        }

        InactivityProfile profile = inactivityService.calculate(offlinePlayer);

        return switch (param) {
            case "days_left" -> profile.isExempt() ? "Never" : String.valueOf(profile.daysRemaining());
            case "time_left" -> profile.isExempt() ? "Never" : inactivityService.formatDuration(profile.millisRemaining());
            case "days_inactive" -> String.valueOf(profile.daysInactive());
            case "time_inactive" -> inactivityService.formatDuration(profile.inactiveMillis());
            case "status" -> inactivityService.getStatusDescriptor(profile);
            case "is_exempt" -> String.valueOf(profile.isExempt());
            case "is_expired" -> String.valueOf(profile.isExpired());
            default -> null;
        };
    }
}
