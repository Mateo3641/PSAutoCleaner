package dev.sh4dow.psautocleaner.core.config;

import dev.sh4dow.psautocleaner.model.CleanupAction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigManagerTest {

    @Test
    void testCleanupActionParsing() {
        assertThat(CleanupAction.fromString("remove")).contains(CleanupAction.REMOVE);
        assertThat(CleanupAction.fromString("REMOVE")).contains(CleanupAction.REMOVE);
        assertThat(CleanupAction.fromString("regen")).contains(CleanupAction.REGEN);
        assertThat(CleanupAction.fromString("disown")).contains(CleanupAction.DISOWN);
        assertThat(CleanupAction.fromString("preview")).contains(CleanupAction.PREVIEW);
        assertThat(CleanupAction.fromString("invalid_mode")).isEmpty();
        assertThat(CleanupAction.fromString(null)).isEmpty();
    }

    @Test
    void testCleanConfigSanitization() {
        // Null or negative durations should be sanitized to defaults
        CleanConfig config = new CleanConfig(
                true,
                Duration.ofDays(-5), // invalid inactiveDuration
                null, // invalid action
                Duration.ofHours(0),  // invalid interval
                Duration.ofMinutes(-2), // invalid initialDelay
                null,
                true,
                "",
                true
        );

        assertThat(config.inactiveDuration()).isEqualTo(Duration.ofDays(30));
        assertThat(config.inactiveDays()).isEqualTo(30);
        assertThat(config.cleanupAction()).isEqualTo(CleanupAction.REMOVE);
        assertThat(config.intervalDuration()).isEqualTo(Duration.ofHours(24));
        assertThat(config.intervalHours()).isEqualTo(24);
        assertThat(config.initialDelayDuration()).isEqualTo(Duration.ofMinutes(10));
        assertThat(config.initialDelayMinutes()).isEqualTo(10);
        assertThat(config.targetWorlds()).isEmpty();
        assertThat(config.timeFormat()).isEqualTo("short");
        assertThat(config.checkMembers()).isTrue();
    }

    @Test
    void testCleanConfigCustomValues() {
        CleanConfig config = new CleanConfig(
                true,
                Duration.ofMinutes(3), // fine-grained 3 minutes!
                CleanupAction.REMOVE,
                Duration.ofMinutes(1),
                Duration.ofSeconds(30),
                List.of("world", "world_nether"),
                false,
                "verbose",
                false
        );

        assertThat(config.enabled()).isTrue();
        assertThat(config.inactiveDuration()).isEqualTo(Duration.ofMinutes(3));
        assertThat(config.cleanupAction()).isEqualTo(CleanupAction.REMOVE);
        assertThat(config.intervalDuration()).isEqualTo(Duration.ofMinutes(1));
        assertThat(config.initialDelayDuration()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.targetWorlds()).containsExactly("world", "world_nether");
        assertThat(config.logToConsole()).isFalse();
        assertThat(config.timeFormat()).isEqualTo("verbose");
        assertThat(config.checkMembers()).isFalse();
    }
}
