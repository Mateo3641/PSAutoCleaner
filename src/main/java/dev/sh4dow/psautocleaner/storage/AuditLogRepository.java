package dev.sh4dow.psautocleaner.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.sh4dow.psautocleaner.model.AuditLogEntry;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 100% asynchronous SQLite repository for claim deletion audit logs.
 * Adheres to Pragmatic Clean Architecture and Minecraft Database Patterns.
 */
public class AuditLogRepository implements AutoCloseable {

    private final Logger logger;
    private final HikariDataSource dataSource;
    private final ExecutorService asyncExecutor;

    public AuditLogRepository(File dataFolder, Logger logger) {
        this.logger = logger;
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File dbFile = new File(dataFolder, "audit.db");
        this.asyncExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "PSAutoCleaner-Database-IO");
            thread.setDaemon(true);
            return thread;
        });

        HikariConfig config = new HikariConfig();
        config.setPoolName("PSAutoCleaner-SQLitePool");
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        // SQLite has file-level locking; 1 connection avoids "database is locked" errors
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        this.dataSource = new HikariDataSource(config);
        initDatabase();
    }

    private void initDatabase() {
        asyncExecutor.execute(() -> {
            String createTableSql = """
                    CREATE TABLE IF NOT EXISTS psac_audit_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid VARCHAR(36) NOT NULL,
                        player_name VARCHAR(32) NOT NULL,
                        region_id VARCHAR(128) NOT NULL,
                        world VARCHAR(64) NOT NULL,
                        x INT NOT NULL,
                        y INT NOT NULL,
                        z INT NOT NULL,
                        inactive_millis BIGINT NOT NULL,
                        reason VARCHAR(255) NOT NULL,
                        deleted_at BIGINT NOT NULL
                    );
                    """;
            String createIdxName = "CREATE INDEX IF NOT EXISTS idx_audit_name ON psac_audit_logs(player_name);";
            String createIdxUuid = "CREATE INDEX IF NOT EXISTS idx_audit_uuid ON psac_audit_logs(player_uuid);";

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSql);
                stmt.execute(createIdxName);
                stmt.execute(createIdxUuid);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to initialize SQLite audit database", e);
            }
        });
    }

    /**
     * Persists an audit log entry asynchronously without blocking the server thread.
     */
    public CompletableFuture<Void> save(AuditLogEntry entry) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO psac_audit_logs (
                        player_uuid, player_name, region_id, world, x, y, z, inactive_millis, reason, deleted_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, entry.playerUuid().toString());
                stmt.setString(2, entry.playerName());
                stmt.setString(3, entry.regionId());
                stmt.setString(4, entry.world());
                stmt.setInt(5, entry.x());
                stmt.setInt(6, entry.y());
                stmt.setInt(7, entry.z());
                stmt.setLong(8, entry.inactiveMillis());
                stmt.setString(9, entry.reason());
                stmt.setLong(10, entry.deletedAt());
                stmt.executeUpdate();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to save audit log entry for region " + entry.regionId(), e);
            }
        }, asyncExecutor);
    }

    /**
     * Retrieves audit logs for a player by exact name or UUID (case-insensitive) asynchronously.
     */
    public CompletableFuture<List<AuditLogEntry>> findByPlayer(String playerIdentifier, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<AuditLogEntry> results = new ArrayList<>();
            String sql = """
                    SELECT id, player_uuid, player_name, region_id, world, x, y, z, inactive_millis, reason, deleted_at
                    FROM psac_audit_logs
                    WHERE LOWER(player_name) = LOWER(?) OR player_uuid = ?
                    ORDER BY deleted_at DESC
                    LIMIT ?;
                    """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerIdentifier);
                stmt.setString(2, playerIdentifier);
                stmt.setInt(3, Math.max(1, limit));

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID uuid;
                        try {
                            uuid = UUID.fromString(rs.getString("player_uuid"));
                        } catch (Exception ignored) {
                            uuid = UUID.randomUUID();
                        }

                        results.add(new AuditLogEntry(
                                rs.getLong("id"),
                                uuid,
                                rs.getString("player_name"),
                                rs.getString("region_id"),
                                rs.getString("world"),
                                rs.getInt("x"),
                                rs.getInt("y"),
                                rs.getInt("z"),
                                rs.getLong("inactive_millis"),
                                rs.getString("reason"),
                                rs.getLong("deleted_at")
                        ));
                    }
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to query audit logs for player " + playerIdentifier, e);
            }
            return results;
        }, asyncExecutor);
    }

    /**
     * Purges audit logs older than a given epoch millisecond timestamp.
     */
    public CompletableFuture<Integer> purgeOlderThan(long epochMillis) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM psac_audit_logs WHERE deleted_at < ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, epochMillis);
                return stmt.executeUpdate();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to purge old audit logs", e);
                return 0;
            }
        }, asyncExecutor);
    }

    @Override
    public void close() {
        try {
            asyncExecutor.shutdown();
            if (!asyncExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
