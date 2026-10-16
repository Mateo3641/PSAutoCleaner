package dev.sh4dow.psautocleaner.core.config;

import dev.sh4dow.psautocleaner.model.CleanupAction;

import java.util.Collections;
import java.util.List;

/**
 * Strongly typed immutable configuration data model for PSAutoCleaner.
 */
public record CleanConfig(
        boolean enabled,
        int inactiveDays,
        CleanupAction cleanupAction,
        long intervalHours,
        long initialDelayMinutes,
        List<String> targetWorlds,
        boolean logToConsole,
        String timeFormat,
        boolean checkMembers
) {
    public CleanConfig {
        if (inactiveDays < 1) {
            inactiveDays = 30;
        }
        if (cleanupAction == null) {
            cleanupAction = CleanupAction.REMOVE;
        }
        if (intervalHours < 1) {
            intervalHours = 24;
        }
        if (initialDelayMinutes < 0) {
            initialDelayMinutes = 10;
        }
        targetWorlds = targetWorlds == null ? Collections.emptyList() : List.copyOf(targetWorlds);
        if (timeFormat == null || timeFormat.isBlank()) {
            timeFormat = "short";
        }
    }

    public static CleanConfig defaultValues() {
        return new CleanConfig(true, 30, CleanupAction.REMOVE, 24, 10, Collections.emptyList(), true, "short", true);
    }
}
