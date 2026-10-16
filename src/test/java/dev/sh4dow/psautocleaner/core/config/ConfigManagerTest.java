package dev.sh4dow.psautocleaner.core.config;

import dev.sh4dow.psautocleaner.model.CleanupAction;
import org.junit.jupiter.api.Test;

import java.util.Collections;
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
        // Values below 1 should be sanitized
        CleanConfig config = new CleanConfig(
                true,
                -5, // invalid inactiveDays
                null, // invalid action
                0,  // invalid intervalHours
                -2, // invalid initialDelay
                null,
                true,
                ""
        );

        assertThat(config.inactiveDays()).isEqualTo(30);
        assertThat(config.cleanupAction()).isEqualTo(CleanupAction.REMOVE);
        assertThat(config.intervalHours()).isEqualTo(24);
        assertThat(config.initialDelayMinutes()).isEqualTo(10);
        assertThat(config.targetWorlds()).isEmpty();
        assertThat(config.timeFormat()).isEqualTo("short");
    }

    @Test
    void testCleanConfigCustomValues() {
        CleanConfig config = new CleanConfig(
                true,
                60,
                CleanupAction.REGEN,
                12,
                5,
                List.of("world", "world_nether"),
                false,
                "verbose"
        );

        assertThat(config.enabled()).isTrue();
        assertThat(config.inactiveDays()).isEqualTo(60);
        assertThat(config.cleanupAction()).isEqualTo(CleanupAction.REGEN);
        assertThat(config.intervalHours()).isEqualTo(12);
        assertThat(config.initialDelayMinutes()).isEqualTo(5);
        assertThat(config.targetWorlds()).containsExactly("world", "world_nether");
        assertThat(config.logToConsole()).isFalse();
        assertThat(config.timeFormat()).isEqualTo("verbose");
    }
}
