package dev.sh4dow.psautocleaner.storage;

import dev.sh4dow.psautocleaner.model.AuditLogEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogRepositoryTest {

    @TempDir
    Path tempDir;

    private AuditLogRepository repository;

    @BeforeEach
    void setUp() {
        File dataFolder = tempDir.toFile();
        repository = new AuditLogRepository(dataFolder, Logger.getLogger("AuditLogRepositoryTest"));
    }

    @AfterEach
    void tearDown() {
        if (repository != null) {
            repository.close();
        }
    }

    @Test
    void testSaveAndQueryAuditLogs() {
        UUID uuid = UUID.randomUUID();
        AuditLogEntry entry = new AuditLogEntry(
                0,
                uuid,
                "sh4dow31",
                "ps_world_100_64_200",
                "world",
                100,
                64,
                200,
                180_000L,
                "Inactive for 3m",
                System.currentTimeMillis()
        );

        // Save asynchronously and wait
        repository.save(entry).join();

        // Query by username (case-insensitive)
        List<AuditLogEntry> logsByName = repository.findByPlayer("SH4DOW31", 10).join();
        assertThat(logsByName).hasSize(1);
        AuditLogEntry retrieved = logsByName.getFirst();
        assertThat(retrieved.playerName()).isEqualTo("sh4dow31");
        assertThat(retrieved.regionId()).isEqualTo("ps_world_100_64_200");
        assertThat(retrieved.world()).isEqualTo("world");
        assertThat(retrieved.x()).isEqualTo(100);
        assertThat(retrieved.y()).isEqualTo(64);
        assertThat(retrieved.z()).isEqualTo(200);

        // Query by UUID
        List<AuditLogEntry> logsByUuid = repository.findByPlayer(uuid.toString(), 10).join();
        assertThat(logsByUuid).hasSize(1);
    }

    @Test
    void testQueryNonExistentPlayerReturnsEmptyList() {
        List<AuditLogEntry> logs = repository.findByPlayer("NonExistentPlayer", 10).join();
        assertThat(logs).isEmpty();
    }
}
