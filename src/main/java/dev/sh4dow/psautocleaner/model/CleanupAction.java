package dev.sh4dow.psautocleaner.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Cleanup action modes supported by ProtectionStones admin cleanup command.
 */
public enum CleanupAction {
    REMOVE("remove", "Permanently deletes claim and breaks/removes protection stone block"),
    REGEN("regen", "Deletes claim and regenerates terrain to natural state"),
    DISOWN("disown", "Removes player ownership while preserving region"),
    PREVIEW("preview", "Previews targeted regions without modifying terrain or regions");

    private final String commandArg;
    private final String description;

    CleanupAction(String commandArg, String description) {
        this.commandArg = commandArg;
        this.description = description;
    }

    public String getCommandArg() {
        return commandArg;
    }

    public String getDescription() {
        return description;
    }

    public static Optional<CleanupAction> fromString(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String clean = text.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(action -> action.commandArg.equalsIgnoreCase(clean) || action.name().equalsIgnoreCase(clean))
                .findFirst();
    }
}
