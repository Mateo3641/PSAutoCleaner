package dev.sh4dow.psautocleaner.service;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import dev.sh4dow.psautocleaner.core.config.ConfigManager;
import dev.sh4dow.psautocleaner.model.InactivityProfile;
import dev.sh4dow.psautocleaner.model.RegionInactivitySummary;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service that inspects WorldGuard regions at a specific location to identify
 * ProtectionStone claims and evaluate the inactivity of both owners and members.
 */
public class RegionInspectorService {

    private final ConfigManager configManager;
    private final InactivityService inactivityService;
    private final boolean isWorldGuardPresent;

    public RegionInspectorService(ConfigManager configManager, InactivityService inactivityService) {
        this.configManager = Objects.requireNonNull(configManager, "configManager cannot be null");
        this.inactivityService = Objects.requireNonNull(inactivityService, "inactivityService cannot be null");
        this.isWorldGuardPresent = checkWorldGuardPresence();
    }

    private boolean checkWorldGuardPresence() {
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            return Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Inspects the ProtectionStone claim at the given location if one exists.
     */
    public Optional<RegionInactivitySummary> inspectLocation(Location location) {
        if (!isWorldGuardPresent || location == null || location.getWorld() == null) {
            return Optional.empty();
        }

        try {
            World world = location.getWorld();
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager manager = container.get(BukkitAdapter.adapt(world));
            if (manager == null) {
                return Optional.empty();
            }

            BlockVector3 point = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            ApplicableRegionSet applicable = manager.getApplicableRegions(point);

            ProtectedRegion targetRegion = null;
            // First look for standard ProtectionStones region prefixes
            for (ProtectedRegion region : applicable) {
                if (region.getId().toLowerCase().startsWith("ps_")) {
                    targetRegion = region;
                    break;
                }
            }

            // Fallback to highest priority region if no ps_ prefix found
            if (targetRegion == null && applicable.size() > 0) {
                targetRegion = applicable.iterator().next();
            }

            if (targetRegion == null) {
                return Optional.empty();
            }

            Set<UUID> ownerUuids = targetRegion.getOwners().getUniqueIds();
            Set<UUID> memberUuids = targetRegion.getMembers().getUniqueIds();

            UUID primaryOwnerUuid = ownerUuids.stream().findFirst().orElse(null);
            String primaryOwnerName = null;
            InactivityProfile primaryOwnerProfile = null;

            if (primaryOwnerUuid != null) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(primaryOwnerUuid);
                primaryOwnerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : primaryOwnerUuid.toString();
                primaryOwnerProfile = inactivityService.calculate(offlinePlayer);
            }

            // Find the most active affiliate (owner or member)
            InactivityProfile mostActiveProfile = primaryOwnerProfile;
            String mostActivePlayerName = primaryOwnerName;
            String mostActiveRole = "owner";

            boolean checkMembers = configManager.getCleanConfig().checkMembers();

            // Check other owners first
            for (UUID uuid : ownerUuids) {
                if (uuid.equals(primaryOwnerUuid)) continue;
                OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
                InactivityProfile prof = inactivityService.calculate(p);
                if (isMoreActive(prof, mostActiveProfile)) {
                    mostActiveProfile = prof;
                    mostActivePlayerName = p.getName() != null ? p.getName() : uuid.toString();
                    mostActiveRole = "owner";
                }
            }

            // Check members if enabled
            if (checkMembers) {
                for (UUID uuid : memberUuids) {
                    OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
                    InactivityProfile prof = inactivityService.calculate(p);
                    if (isMoreActive(prof, mostActiveProfile)) {
                        mostActiveProfile = prof;
                        mostActivePlayerName = p.getName() != null ? p.getName() : uuid.toString();
                        mostActiveRole = "member";
                    }
                }
            }

            return Optional.of(new RegionInactivitySummary(
                    targetRegion.getId(),
                    world.getName(),
                    primaryOwnerUuid,
                    primaryOwnerName,
                    primaryOwnerProfile,
                    mostActivePlayerName,
                    mostActiveRole,
                    mostActiveProfile,
                    ownerUuids.size(),
                    memberUuids.size()
            ));
        } catch (Throwable t) {
            // Guard against WorldGuard runtime issues or missing plugins
            return Optional.empty();
        }
    }

    private boolean isMoreActive(InactivityProfile candidate, InactivityProfile current) {
        if (current == null) return candidate != null;
        if (candidate == null) return false;
        if (candidate.isExempt() && !current.isExempt()) return true;
        if (!candidate.isExempt() && current.isExempt()) return false;
        return candidate.inactiveMillis() < current.inactiveMillis();
    }

    public boolean isWorldGuardPresent() {
        return isWorldGuardPresent;
    }
}
