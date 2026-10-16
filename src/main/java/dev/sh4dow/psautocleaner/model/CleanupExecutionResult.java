package dev.sh4dow.psautocleaner.model;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Immutable report detailing the outcome of a cleanup sweep across targeted worlds.
 */
public record CleanupExecutionResult(
        CleanupAction action,
        Duration durationThreshold,
        List<String> targetWorlds,
        int worldsProcessed,
        int regionsEvaluated,
        int regionsCleaned,
        List<String> affectedRegionIds,
        long durationMillis,
        boolean success,
        String errorMessage
) {
    public CleanupExecutionResult {
        if (durationThreshold == null) {
            durationThreshold = Duration.ofDays(30);
        }
        targetWorlds = targetWorlds == null ? Collections.emptyList() : List.copyOf(targetWorlds);
        affectedRegionIds = affectedRegionIds == null ? Collections.emptyList() : List.copyOf(affectedRegionIds);
    }

    public int daysThreshold() {
        return (int) Math.max(1, durationThreshold.toDays());
    }

    public static CleanupExecutionResult ofSuccess(
            CleanupAction action,
            Duration durationThreshold,
            List<String> targetWorlds,
            int worldsProcessed,
            int regionsEvaluated,
            int regionsCleaned,
            List<String> affectedRegionIds,
            long durationMillis
    ) {
        return new CleanupExecutionResult(
                action,
                durationThreshold,
                targetWorlds,
                worldsProcessed,
                regionsEvaluated,
                regionsCleaned,
                affectedRegionIds,
                durationMillis,
                true,
                null
        );
    }

    public static CleanupExecutionResult ofFailure(
            CleanupAction action,
            Duration durationThreshold,
            List<String> targetWorlds,
            String errorMessage
    ) {
        return new CleanupExecutionResult(
                action,
                durationThreshold,
                targetWorlds,
                0,
                0,
                0,
                Collections.emptyList(),
                0,
                false,
                errorMessage
        );
    }
}
