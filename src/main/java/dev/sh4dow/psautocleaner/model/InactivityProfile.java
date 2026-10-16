package dev.sh4dow.psautocleaner.model;

import java.util.UUID;

/**
 * Immutable domain record representing a player's inactivity status and expiration metrics.
 */
public record InactivityProfile(
        UUID playerUuid,
        String playerName,
        long lastPlayedMillis,
        long inactiveMillis,
        long daysInactive,
        long daysRemaining,
        long millisRemaining,
        boolean isExempt,
        boolean isExpired,
        boolean isOnline
) {
    public InactivityProfile {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid cannot be null");
        }
        if (playerName == null || playerName.isBlank()) {
            playerName = playerUuid.toString();
        }
    }
}
