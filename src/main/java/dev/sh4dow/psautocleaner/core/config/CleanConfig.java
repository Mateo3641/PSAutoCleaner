package dev.sh4dow.psautocleaner.core.config;

import dev.sh4dow.psautocleaner.model.CleanupAction;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Strongly typed immutable configuration data model for PSAutoCleaner.
 */
public record CleanConfig(
        boolean enabled,
        Duration inactiveDuration,
        CleanupAction cleanupAction,
        Duration intervalDuration,
        Duration initialDelayDuration,
        List<String> targetWorlds,
        boolean logToConsole,
        String timeFormat,
        boolean checkMembers,
        int batchSizePerTick,
        boolean broadcastEnabled,
        List<String> broadcastLines
) {
    public CleanConfig {
        if (inactiveDuration == null || inactiveDuration.isNegative() || inactiveDuration.isZero()) {
            inactiveDuration = Duration.ofDays(30);
        }
        if (cleanupAction == null) {
            cleanupAction = CleanupAction.REMOVE;
        }
        if (intervalDuration == null || intervalDuration.isNegative() || intervalDuration.isZero()) {
            intervalDuration = Duration.ofHours(24);
        }
        if (initialDelayDuration == null || initialDelayDuration.isNegative()) {
            initialDelayDuration = Duration.ofMinutes(10);
        }
        targetWorlds = targetWorlds == null ? Collections.emptyList() : List.copyOf(targetWorlds);
        if (timeFormat == null || timeFormat.isBlank()) {
            timeFormat = "short";
        }
        if (batchSizePerTick < 1) {
            batchSizePerTick = 2;
        }
        if (broadcastLines == null || broadcastLines.isEmpty()) {
            broadcastLines = List.of(
                    "<gradient:#ff5555:#ff0000><bold>¡LIMPIEZA DE INACTIVIDAD!</bold></gradient>",
                    "<gray>Las protecciones de <gold><player></gold> han sido eliminadas por inactividad (<time> desconectado).",
                    "<yellow>¡El terreno ha quedado libre para nuevos aventureros!</yellow>"
            );
        } else {
            broadcastLines = List.copyOf(broadcastLines);
        }
    }

    /**
     * Backward-compatibility accessor for inactive days.
     */
    public int inactiveDays() {
        return (int) Math.max(1, inactiveDuration.toDays());
    }

    /**
     * Backward-compatibility accessor for interval hours.
     */
    public long intervalHours() {
        return Math.max(1, intervalDuration.toHours());
    }

    /**
     * Backward-compatibility accessor for initial delay minutes.
     */
    public long initialDelayMinutes() {
        return initialDelayDuration.toMinutes();
    }

    public static CleanConfig defaultValues() {
        return new CleanConfig(
                true,
                Duration.ofDays(30),
                CleanupAction.REMOVE,
                Duration.ofHours(24),
                Duration.ofMinutes(10),
                Collections.emptyList(),
                true,
                "short",
                true,
                2,
                true,
                Collections.emptyList()
        );
    }
}
