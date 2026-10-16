package dev.sh4dow.psautocleaner.model;

import java.util.List;

/**
 * Immutable report detailing the outcome of a cleanup sweep across targeted worlds.
 */
public record CleanupExecutionResult(
        CleanupAction action,
        int daysThreshold,
        List<String> targetWorlds,
        int worldsProcessed,
        long durationMillis,
        boolean success,
        String errorMessage
) {
    public static CleanupExecutionResult ofSuccess(
            CleanupAction action,
            int daysThreshold,
            List<String> targetWorlds,
            int worldsProcessed,
            long durationMillis
    ) {
        return new CleanupExecutionResult(action, daysThreshold, targetWorlds, worldsProcessed, durationMillis, true, null);
    }

    public static CleanupExecutionResult ofFailure(
            CleanupAction action,
            int daysThreshold,
            List<String> targetWorlds,
            String errorMessage
    ) {
        return new CleanupExecutionResult(action, daysThreshold, targetWorlds, 0, 0, false, errorMessage);
    }
}
