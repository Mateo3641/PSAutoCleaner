package dev.sh4dow.psautocleaner.service;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import dev.espi.protectionstones.PSRegion;
import dev.sh4dow.psautocleaner.core.config.CleanConfig;
import dev.sh4dow.psautocleaner.core.config.ConfigManager;
import dev.sh4dow.psautocleaner.core.message.MessageService;
import dev.sh4dow.psautocleaner.core.scheduler.TaskScheduler;
import dev.sh4dow.psautocleaner.model.CleanupAction;
import dev.sh4dow.psautocleaner.model.CleanupExecutionResult;
import dev.sh4dow.psautocleaner.model.InactivityProfile;
import dev.sh4dow.psautocleaner.util.DurationParser;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service orchestrating scheduled and on-demand cleanup sweeps across target worlds.
 * Employs a paced tick-throttled deletion queue and asynchronous SQLite audit logging
 * to ensure 20.0 TPS stability during massive deletion runs.
 */
public class CleanerService {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageService messageService;
    private final TaskScheduler scheduler;
    private final InactivityService inactivityService;
    private final AuditService auditService;

    private final AtomicBoolean isExecuting = new AtomicBoolean(false);
    private TaskScheduler.CancellableTask currentTimerTask;
    private Instant nextScheduledRun;
    private CleanupExecutionResult lastResult;

    private record ExpiredClaim(
            UUID ownerUuid,
            String ownerName,
            PSRegion psRegion,
            String worldName,
            long inactiveMillis
    ) {}

    public CleanerService(
            JavaPlugin plugin,
            ConfigManager configManager,
            MessageService messageService,
            TaskScheduler scheduler,
            InactivityService inactivityService,
            AuditService auditService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.configManager = Objects.requireNonNull(configManager, "configManager cannot be null");
        this.messageService = Objects.requireNonNull(messageService, "messageService cannot be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler cannot be null");
        this.inactivityService = Objects.requireNonNull(inactivityService, "inactivityService cannot be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService cannot be null");
    }

    /**
     * Starts or restarts the autonomous background scheduler based on active config.
     */
    public synchronized void startScheduledTask() {
        stopScheduledTask();

        CleanConfig config = configManager.getCleanConfig();
        if (!config.enabled()) {
            plugin.getLogger().info("Autonomous cleaner is disabled in config.yml.");
            return;
        }

        long delayTicks = Math.max(1L, config.initialDelayDuration().toSeconds() * 20L);
        long periodTicks = Math.max(20L, config.intervalDuration().toSeconds() * 20L);

        long delayMillis = config.initialDelayDuration().toMillis();
        this.nextScheduledRun = Instant.ofEpochMilli(System.currentTimeMillis() + delayMillis);

        this.currentTimerTask = scheduler.runTimer(() -> {
            CleanConfig currentConfig = configManager.getCleanConfig();
            long periodMillis = currentConfig.intervalDuration().toMillis();
            this.nextScheduledRun = Instant.ofEpochMilli(System.currentTimeMillis() + periodMillis);

            if (!currentConfig.enabled()) {
                return;
            }

            if (currentConfig.logToConsole()) {
                plugin.getLogger().info("Executing scheduled ProtectionStones cleanup sweep...");
            }

            executeSweep(
                    currentConfig.cleanupAction(),
                    currentConfig.inactiveDuration(),
                    currentConfig.targetWorlds(),
                    null
            );
        }, delayTicks, periodTicks);

        plugin.getLogger().info(String.format(
                "Autonomous cleaner scheduled: First run in %s, then every %s.",
                DurationParser.formatShort(config.initialDelayDuration()),
                DurationParser.formatShort(config.intervalDuration())
        ));
    }

    /**
     * Cancels the currently active scheduled task.
     */
    public synchronized void stopScheduledTask() {
        if (currentTimerTask != null) {
            currentTimerTask.cancel();
            currentTimerTask = null;
        }
        nextScheduledRun = null;
    }

    /**
     * Backward-compatible overload for legacy day-based callers.
     */
    public CompletableFuture<CleanupExecutionResult> executeSweep(
            CleanupAction action,
            int days,
            List<String> explicitWorlds,
            CommandSender feedbackSender
    ) {
        return executeSweep(action, Duration.ofDays(days), explicitWorlds, feedbackSender);
    }

    /**
     * Executes a cleanup sweep across targeted worlds with fine-grained duration precision.
     * Queues deletions across server ticks to eliminate tick lag spikes and records audit logs.
     */
    public CompletableFuture<CleanupExecutionResult> executeSweep(
            CleanupAction action,
            Duration durationThreshold,
            List<String> explicitWorlds,
            CommandSender feedbackSender
    ) {
        CompletableFuture<CleanupExecutionResult> future = new CompletableFuture<>();

        if (!isExecuting.compareAndSet(false, true)) {
            String msg = "A cleanup sweep is already in progress.";
            if (feedbackSender != null) {
                messageService.send(feedbackSender, "cleanup.already-running", Collections.emptyMap());
            }
            future.complete(CleanupExecutionResult.ofFailure(action, durationThreshold, explicitWorlds, msg));
            return future;
        }

        scheduler.runSync(() -> {
            long startTime = System.currentTimeMillis();
            List<String> worldsToProcess = resolveTargetWorlds(explicitWorlds);

            if (feedbackSender != null) {
                messageService.send(feedbackSender, "cleanup.started", Map.of("count", String.valueOf(worldsToProcess.size())));
            }

            int processedWorlds = 0;
            int totalRegionsEvaluated = 0;
            List<ExpiredClaim> allExpiredClaims = new ArrayList<>();
            List<String> affectedRegionIds = new ArrayList<>();

            boolean checkMembers = configManager.getCleanConfig().checkMembers();
            long thresholdMillis = durationThreshold.toMillis();

            try {
                for (String worldName : worldsToProcess) {
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        plugin.getLogger().warning("Skipping unknown/unloaded world: " + worldName);
                        continue;
                    }

                    processedWorlds++;
                    RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                    RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
                    if (regionManager == null) {
                        continue;
                    }

                    List<ProtectedRegion> candidateRegions = new ArrayList<>(regionManager.getRegions().values());

                    for (ProtectedRegion wgRegion : candidateRegions) {
                        PSRegion psRegion = PSRegion.fromWGRegion(world, wgRegion);
                        if (psRegion == null) {
                            continue;
                        }
                        totalRegionsEvaluated++;

                        Set<UUID> ownerUuids = wgRegion.getOwners().getUniqueIds();
                        Set<UUID> memberUuids = checkMembers ? wgRegion.getMembers().getUniqueIds() : Collections.emptySet();

                        // Orphaned region with no owners or members
                        if (ownerUuids.isEmpty() && memberUuids.isEmpty()) {
                            allExpiredClaims.add(new ExpiredClaim(
                                    UUID.randomUUID(),
                                    "Orphaned/Server",
                                    psRegion,
                                    worldName,
                                    thresholdMillis
                            ));
                            affectedRegionIds.add(wgRegion.getId());
                            continue;
                        }

                        boolean hasExemptAffiliate = false;
                        InactivityProfile mostActive = null;

                        Set<UUID> allAffiliates = new HashSet<>(ownerUuids);
                        allAffiliates.addAll(memberUuids);

                        for (UUID uuid : allAffiliates) {
                            OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
                            InactivityProfile prof = inactivityService.calculate(p);

                            if (prof.isExempt()) {
                                hasExemptAffiliate = true;
                                break;
                            }

                            if (mostActive == null || prof.inactiveMillis() < mostActive.inactiveMillis()) {
                                mostActive = prof;
                            }
                        }

                        if (hasExemptAffiliate) {
                            continue;
                        }

                        if (mostActive != null && mostActive.inactiveMillis() >= thresholdMillis) {
                            // Find primary owner name
                            UUID primaryOwnerUuid = ownerUuids.stream().findFirst().orElse(null);
                            String primaryOwnerName = "Unknown";
                            if (primaryOwnerUuid != null) {
                                OfflinePlayer op = Bukkit.getOfflinePlayer(primaryOwnerUuid);
                                primaryOwnerName = op.getName() != null ? op.getName() : primaryOwnerUuid.toString();
                            }

                            allExpiredClaims.add(new ExpiredClaim(
                                    primaryOwnerUuid != null ? primaryOwnerUuid : UUID.randomUUID(),
                                    primaryOwnerName,
                                    psRegion,
                                    worldName,
                                    mostActive.inactiveMillis()
                            ));
                            affectedRegionIds.add(wgRegion.getId());
                        }
                    }

                    if (feedbackSender != null) {
                        messageService.send(feedbackSender, "cleanup.world-executed", Map.of(
                                "world", world.getName(),
                                "action", action.getCommandArg(),
                                "time", DurationParser.formatShort(durationThreshold),
                                "found", String.valueOf(affectedRegionIds.size()),
                                "cleaned", String.valueOf(action == CleanupAction.REMOVE ? affectedRegionIds.size() : 0),
                                "days", String.valueOf(Math.max(1, durationThreshold.toDays()))
                        ));
                    }
                }

                // If PREVIEW or no claims found, finish immediately
                if (action == CleanupAction.PREVIEW || allExpiredClaims.isEmpty()) {
                    long duration = System.currentTimeMillis() - startTime;
                    CleanupExecutionResult result = CleanupExecutionResult.ofSuccess(
                            action,
                            durationThreshold,
                            worldsToProcess,
                            processedWorlds,
                            totalRegionsEvaluated,
                            allExpiredClaims.size(),
                            affectedRegionIds,
                            duration
                    );

                    this.lastResult = result;
                    if (feedbackSender != null) {
                        messageService.send(feedbackSender, "cleanup.completed", Map.of(
                                "time", String.valueOf(duration),
                                "count", String.valueOf(allExpiredClaims.size())
                        ));
                    }
                    isExecuting.set(false);
                    future.complete(result);
                    return;
                }

                // If REMOVE, execute deletions via paced tick queue (anti-lag batching)
                processPacedDeletionQueue(
                        allExpiredClaims,
                        durationThreshold,
                        worldsToProcess,
                        processedWorlds,
                        totalRegionsEvaluated,
                        affectedRegionIds,
                        startTime,
                        feedbackSender,
                        future
                );

            } catch (Exception e) {
                plugin.getLogger().severe("Unexpected error during cleanup sweep: " + e.getMessage());
                CleanupExecutionResult result = CleanupExecutionResult.ofFailure(
                        action,
                        durationThreshold,
                        worldsToProcess,
                        e.getMessage()
                );
                this.lastResult = result;
                isExecuting.set(false);
                future.complete(result);
            }
        });

        return future;
    }

    /**
     * Executes claim deletions smoothly across server ticks (e.g. 2 per tick).
     * Triggers a grouped multiline broadcast per player when all of that player's claims are purged.
     */
    private void processPacedDeletionQueue(
            List<ExpiredClaim> claimsToDelete,
            Duration durationThreshold,
            List<String> worldsToProcess,
            int processedWorlds,
            int totalRegionsEvaluated,
            List<String> affectedRegionIds,
            long startTime,
            CommandSender feedbackSender,
            CompletableFuture<CleanupExecutionResult> future
    ) {
        CleanConfig config = configManager.getCleanConfig();
        int batchSize = config.batchSizePerTick();

        // Track remaining claims per player to emit a single grouped broadcast per player
        Map<UUID, Integer> remainingClaimsPerOwner = new HashMap<>();
        Map<UUID, String> ownerNames = new HashMap<>();
        Map<UUID, Long> maxInactivePerOwner = new HashMap<>();

        for (ExpiredClaim claim : claimsToDelete) {
            remainingClaimsPerOwner.merge(claim.ownerUuid(), 1, Integer::sum);
            ownerNames.put(claim.ownerUuid(), claim.ownerName());
            maxInactivePerOwner.merge(claim.ownerUuid(), claim.inactiveMillis(), Math::max);
        }

        Queue<ExpiredClaim> queue = new ArrayDeque<>(claimsToDelete);
        int totalToClean = claimsToDelete.size();

        final TaskScheduler.CancellableTask[] taskHolder = new TaskScheduler.CancellableTask[1];

        taskHolder[0] = scheduler.runTimer(new Runnable() {
            @Override
            public void run() {
                int processedInThisTick = 0;

                while (!queue.isEmpty() && processedInThisTick < batchSize) {
                    ExpiredClaim claim = queue.poll();
                    processedInThisTick++;

                    // 1. Record audit log asynchronously
                    String reason = "Inactive for " + DurationParser.formatShort(Duration.ofMillis(claim.inactiveMillis()));
                    auditService.recordDeletion(
                            claim.ownerUuid(),
                            claim.ownerName(),
                            claim.psRegion(),
                            reason,
                            claim.inactiveMillis()
                    );

                    // 2. Physical claim deletion on server thread
                    try {
                        claim.psRegion().deleteRegion(true);
                    } catch (Throwable t) {
                        plugin.getLogger().warning("Error deleting region " + claim.psRegion().getId() + ": " + t.getMessage());
                    }

                    // 3. Update player claim counter & check for grouped player broadcast
                    int remaining = remainingClaimsPerOwner.compute(claim.ownerUuid(), (k, v) -> (v == null || v <= 1) ? 0 : v - 1);
                    if (remaining == 0) {
                        // All claims of this player have now been deleted! Send grouped broadcast
                        emitPlayerDeletionBroadcast(claim.ownerUuid(), claim.ownerName(), maxInactivePerOwner.get(claim.ownerUuid()));
                        remainingClaimsPerOwner.remove(claim.ownerUuid());
                    }
                }

                if (queue.isEmpty()) {
                    // All queued deletions are finished!
                    if (taskHolder[0] != null) {
                        taskHolder[0].cancel();
                    }

                    long duration = System.currentTimeMillis() - startTime;
                    CleanupExecutionResult result = CleanupExecutionResult.ofSuccess(
                            CleanupAction.REMOVE,
                            durationThreshold,
                            worldsToProcess,
                            processedWorlds,
                            totalRegionsEvaluated,
                            totalToClean,
                            affectedRegionIds,
                            duration
                    );

                    lastResult = result;
                    if (feedbackSender != null) {
                        messageService.send(feedbackSender, "cleanup.completed", Map.of(
                                "time", String.valueOf(duration),
                                "count", String.valueOf(totalToClean)
                        ));
                    }
                    if (config.logToConsole()) {
                        plugin.getLogger().info(String.format("Cleanup sweep completed: %d claims deleted across %d worlds in %dms.",
                                totalToClean, processedWorlds, duration));
                    }

                    isExecuting.set(false);
                    future.complete(result);
                }
            }
        }, 1L, 1L);
    }

    /**
     * Emits a single multiline broadcast to all players when a user's protections are eliminated.
     */
    private void emitPlayerDeletionBroadcast(UUID playerUuid, String playerName, Long inactiveMillis) {
        CleanConfig config = configManager.getCleanConfig();
        if (!config.broadcastEnabled() || config.broadcastLines().isEmpty()) {
            return;
        }

        // Avoid broadcasting for internal orphaned regions
        if ("Orphaned/Server".equalsIgnoreCase(playerName) || "Unknown".equalsIgnoreCase(playerName)) {
            return;
        }

        String inactiveFormatted = DurationParser.formatShort(Duration.ofMillis(inactiveMillis != null ? inactiveMillis : 0));
        MiniMessage mm = MiniMessage.miniMessage();

        for (String rawLine : config.broadcastLines()) {
            String populated = rawLine
                    .replace("<player>", playerName)
                    .replace("<time>", inactiveFormatted);
            Bukkit.broadcast(mm.deserialize(populated));
        }
    }

    private List<String> resolveTargetWorlds(List<String> explicitWorlds) {
        if (explicitWorlds != null && !explicitWorlds.isEmpty()) {
            return new ArrayList<>(explicitWorlds);
        }
        CleanConfig config = configManager.getCleanConfig();
        if (!config.targetWorlds().isEmpty()) {
            return new ArrayList<>(config.targetWorlds());
        }
        return Bukkit.getWorlds().stream().map(World::getName).toList();
    }

    public boolean isExecuting() {
        return isExecuting.get();
    }

    public Optional<Instant> getNextScheduledRun() {
        return Optional.ofNullable(nextScheduledRun);
    }

    public Optional<CleanupExecutionResult> getLastResult() {
        return Optional.ofNullable(lastResult);
    }
}
