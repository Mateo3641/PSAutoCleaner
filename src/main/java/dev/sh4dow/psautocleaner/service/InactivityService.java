package dev.sh4dow.psautocleaner.service;

import dev.sh4dow.psautocleaner.core.config.CleanConfig;
import dev.sh4dow.psautocleaner.core.config.ConfigManager;
import dev.sh4dow.psautocleaner.model.InactivityProfile;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service providing calculation and formatting of player inactivity and claim expiration.
 */
public class InactivityService {

    public static final String BYPASS_PERMISSION = "psautocleaner.bypass";
    private static final long MILLIS_PER_DAY = TimeUnit.DAYS.toMillis(1);

    private final ConfigManager configManager;

    public InactivityService(ConfigManager configManager) {
        this.configManager = Objects.requireNonNull(configManager, "configManager cannot be null");
    }

    /**
     * Calculates inactivity metrics for a given OfflinePlayer.
     */
    public InactivityProfile calculate(OfflinePlayer offlinePlayer) {
        if (offlinePlayer == null) {
            throw new IllegalArgumentException("offlinePlayer cannot be null");
        }

        UUID uuid = offlinePlayer.getUniqueId();
        String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : uuid.toString();
        CleanConfig config = configManager.getCleanConfig();
        int thresholdDays = config.inactiveDays();

        boolean isOnline = offlinePlayer.isOnline();
        Player onlinePlayer = offlinePlayer.getPlayer();
        boolean isExempt = (onlinePlayer != null && onlinePlayer.hasPermission(BYPASS_PERMISSION));

        long now = System.currentTimeMillis();
        long lastPlayed;

        if (isOnline) {
            lastPlayed = now;
        } else {
            long lastLogin = offlinePlayer.getLastLogin();
            long lastSeen = offlinePlayer.getLastPlayed();
            lastPlayed = lastLogin > 0 ? lastLogin : lastSeen;
            if (lastPlayed <= 0) {
                lastPlayed = now;
            }
        }

        long inactiveMillis = Math.max(0, now - lastPlayed);
        long daysInactive = TimeUnit.MILLISECONDS.toDays(inactiveMillis);

        long thresholdMillis = (long) thresholdDays * MILLIS_PER_DAY;
        long millisRemaining = isExempt ? Long.MAX_VALUE : Math.max(0, thresholdMillis - inactiveMillis);
        long daysRemaining = isExempt ? Long.MAX_VALUE : Math.max(0, thresholdDays - daysInactive);

        boolean isExpired = !isExempt && (daysInactive >= thresholdDays);

        return new InactivityProfile(
                uuid,
                name,
                lastPlayed,
                inactiveMillis,
                daysInactive,
                daysRemaining,
                millisRemaining,
                isExempt,
                isExpired,
                isOnline
        );
    }

    /**
     * Formats duration in milliseconds to human-readable format according to config.
     */
    public String formatDuration(long millis) {
        if (millis <= 0) {
            return "0m";
        }
        if (millis == Long.MAX_VALUE) {
            return "Never";
        }

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;

        String format = configManager.getCleanConfig().timeFormat();
        if ("verbose".equalsIgnoreCase(format)) {
            if (days > 0) {
                return String.format("%dd %dh %dm", days, hours, minutes);
            }
            if (hours > 0) {
                return String.format("%dh %dm", hours, minutes);
            }
            return String.format("%dm", minutes);
        }

        // Default short format
        if (days > 0) {
            return String.format("%dd %dh", days, hours);
        }
        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        }
        return String.format("%dm", minutes);
    }

    /**
     * Returns a standardized status descriptor for placeholders and reports.
     */
    public String getStatusDescriptor(InactivityProfile profile) {
        if (profile.isExempt()) {
            return "EXEMPT";
        }
        if (profile.isExpired()) {
            return "EXPIRED";
        }
        if (profile.isOnline()) {
            return "ACTIVE";
        }
        if (profile.daysRemaining() <= 3) {
            return "EXPIRING_SOON";
        }
        return "INACTIVE";
    }
}
