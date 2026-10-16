package dev.sh4dow.psautocleaner.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable domain record representing an audit log entry of a deleted claim.
 */
public record AuditLogEntry(
        long id,
        UUID playerUuid,
        String playerName,
        String regionId,
        String world,
        int x,
        int y,
        int z,
        long inactiveMillis,
        String reason,
        long deletedAt
) {
    public Instant getDeletedInstant() {
        return Instant.ofEpochMilli(deletedAt);
    }
}
