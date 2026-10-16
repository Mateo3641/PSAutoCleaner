package dev.sh4dow.psautocleaner.model;

import java.util.Optional;
import java.util.UUID;

/**
 * Immutable record representing a ProtectionStones region inspection,
 * including its owners, members, and the activity status of the most active affiliate.
 */
public record RegionInactivitySummary(
        String regionId,
        String worldName,
        UUID ownerUuid,
        String ownerName,
        InactivityProfile ownerProfile,
        String mostActivePlayerName,
        String mostActiveRole,
        InactivityProfile activeProfile,
        int totalOwners,
        int totalMembers
) {
    public boolean hasOwner() {
        return ownerUuid != null;
    }

    public Optional<InactivityProfile> getProfile() {
        return Optional.ofNullable(activeProfile != null ? activeProfile : ownerProfile);
    }

    public boolean isProtectedByMember() {
        return "member".equalsIgnoreCase(mostActiveRole);
    }
}
