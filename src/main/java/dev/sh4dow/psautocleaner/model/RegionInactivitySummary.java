package dev.sh4dow.psautocleaner.model;

import java.util.Optional;
import java.util.UUID;

/**
 * Immutable record representing a ProtectionStones region inspection and its owner's inactivity state.
 */
public record RegionInactivitySummary(
        String regionId,
        String worldName,
        UUID ownerUuid,
        String ownerName,
        InactivityProfile ownerProfile
) {
    public boolean hasOwner() {
        return ownerUuid != null;
    }

    public Optional<InactivityProfile> getProfile() {
        return Optional.ofNullable(ownerProfile);
    }
}
