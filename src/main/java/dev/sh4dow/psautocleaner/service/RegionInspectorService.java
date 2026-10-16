package dev.sh4dow.psautocleaner.service;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import dev.sh4dow.psautocleaner.model.InactivityProfile;
import dev.sh4dow.psautocleaner.model.RegionInactivitySummary;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service that inspects WorldGuard regions at a specific location to identify
 * ProtectionStone claims and evaluate the inactivity of their owners.
 */
public class RegionInspectorService {

    private final InactivityService inactivityService;
    private final boolean isWorldGuardPresent;

    public RegionInspectorService(InactivityService inactivityService) {
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

            UUID ownerUuid = targetRegion.getOwners().getUniqueIds().stream().findFirst().orElse(null);
            String ownerName = null;
            InactivityProfile profile = null;

            if (ownerUuid != null) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(ownerUuid);
                ownerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : ownerUuid.toString();
                profile = inactivityService.calculate(offlinePlayer);
            }

            return Optional.of(new RegionInactivitySummary(
                    targetRegion.getId(),
                    world.getName(),
                    ownerUuid,
                    ownerName,
                    profile
            ));
        } catch (Throwable t) {
            // Guard against WorldGuard runtime issues or missing plugins
            return Optional.empty();
        }
    }

    public boolean isWorldGuardPresent() {
        return isWorldGuardPresent;
    }
}
