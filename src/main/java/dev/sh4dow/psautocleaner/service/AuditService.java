package dev.sh4dow.psautocleaner.service;

import dev.espi.protectionstones.PSRegion;
import dev.sh4dow.psautocleaner.model.AuditLogEntry;
import dev.sh4dow.psautocleaner.storage.AuditLogRepository;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service managing audit logging and retrieval for deleted inactive claims.
 */
public class AuditService implements AutoCloseable {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    /**
     * Records an audit log entry for a deleted claim asynchronously.
     */
    public CompletableFuture<Void> recordDeletion(
            UUID ownerUuid,
            String ownerName,
            PSRegion psRegion,
            String reason,
            long inactiveMillis
    ) {
        int x = 0;
        int y = 0;
        int z = 0;
        String worldName = "unknown";

        try {
            if (psRegion != null) {
                if (psRegion.getWorld() != null) {
                    worldName = psRegion.getWorld().getName();
                }
                if (psRegion.getProtectBlock() != null) {
                    Location loc = psRegion.getProtectBlock().getLocation();
                    x = loc.getBlockX();
                    y = loc.getBlockY();
                    z = loc.getBlockZ();
                }
            }
        } catch (Throwable ignored) {
        }

        AuditLogEntry entry = new AuditLogEntry(
                0,
                ownerUuid != null ? ownerUuid : UUID.randomUUID(),
                ownerName != null ? ownerName : "Unknown",
                psRegion != null ? psRegion.getId() : "unknown",
                worldName,
                x,
                y,
                z,
                inactiveMillis,
                reason,
                System.currentTimeMillis()
        );

        return repository.save(entry);
    }

    /**
     * Finds audit records for a player by username or UUID.
     */
    public CompletableFuture<List<AuditLogEntry>> findLogs(String playerIdentifier, int limit) {
        return repository.findByPlayer(playerIdentifier, limit);
    }

    @Override
    public void close() {
        repository.close();
    }
}
