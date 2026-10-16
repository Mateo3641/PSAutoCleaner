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
 * Executes direct claim evaluations and deletions synchronously on the server thread via ProtectionStones API.
 */
public class CleanerService {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageService messageService;
    private final TaskScheduler scheduler;
    private final InactivityService inactivityService;

    private final AtomicBoolean isExecuting = new AtomicBoolean(false);
    private TaskScheduler.CancellableTask currentTimerTask;
    private Instant nextScheduledRun;
    private CleanupExecutionResult lastResult;

    public CleanerService(
            JavaPlugin plugin,
            ConfigManager configManager,
            MessageService messageService,
            TaskScheduler scheduler,
            InactivityService inactivityService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.configManager = Objects.requireNonNull(configManager, "configManager cannot be null");
        this.messageService = Objects.requireNonNull(messageService, "messageService cannot be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler cannot be null");
        this.inactivityService = Objects.requireNonNull(inactivityService, "inactivityService cannot be null");
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
     * Evaluates and mutates claims directly on the main thread via ProtectionStones API.
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

        // Ensure execution happens on main thread
        scheduler.runSync(() -> {
            long startTime = System.currentTimeMillis();
            List<String> worldsToProcess = resolveTargetWorlds(explicitWorlds);

            if (feedbackSender != null) {
                messageService.send(feedbackSender, "cleanup.started", Map.of("count", String.valueOf(worldsToProcess.size())));
            }

            int processedWorlds = 0;
            int totalRegionsEvaluated = 0;
            int totalRegionsCleaned = 0;
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

                    // Snapshot regions to avoid concurrent modification issues during iteration
                    List<ProtectedRegion> candidateRegions = new ArrayList<>(regionManager.getRegions().values());
                    List<PSRegion> expiredRegions = new ArrayList<>();

                    for (ProtectedRegion wgRegion : candidateRegions) {
                        PSRegion psRegion = PSRegion.fromWGRegion(world, wgRegion);
                        if (psRegion == null) {
                            continue;
                        }
                        totalRegionsEvaluated++;

                        Set<UUID> ownerUuids = wgRegion.getOwners().getUniqueIds();
                        Set<UUID> memberUuids = checkMembers ? wgRegion.getMembers().getUniqueIds() : Collections.emptySet();

                        // If the region has no owners or members, it is an abandoned orphan
                        if (ownerUuids.isEmpty() && memberUuids.isEmpty()) {
                            expiredRegions.add(psRegion);
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

                        // Region qualifies for cleanup if even the most active affiliate exceeded the threshold
                        if (mostActive != null && mostActive.inactiveMillis() >= thresholdMillis) {
                            expiredRegions.add(psRegion);
                            affectedRegionIds.add(wgRegion.getId());
                        }
                    }

                    // Execute action
                    if (action == CleanupAction.REMOVE) {
                        for (PSRegion psRegion : expiredRegions) {
                            psRegion.deleteRegion(true);
                            totalRegionsCleaned++;
                        }
                    } else {
                        totalRegionsCleaned += expiredRegions.size();
                    }

                    if (feedbackSender != null) {
                        messageService.send(feedbackSender, "cleanup.world-executed", Map.of(
                                "world", world.getName(),
                                "action", action.getCommandArg(),
                                "time", DurationParser.formatShort(durationThreshold),
                                "found", String.valueOf(expiredRegions.size()),
                                "cleaned", String.valueOf(action == CleanupAction.REMOVE ? expiredRegions.size() : 0),
                                "days", String.valueOf(Math.max(1, durationThreshold.toDays()))
                        ));
                    }

                    if (configManager.getCleanConfig().logToConsole()) {
                        plugin.getLogger().info(String.format(
                                "[World: %s] %s %d claim(s) (threshold: %s)",
                                world.getName(),
                                action == CleanupAction.REMOVE ? "Deleted" : "Previewed",
                                expiredRegions.size(),
                                DurationParser.formatShort(durationThreshold)
                        ));
                    }
                }

                long duration = System.currentTimeMillis() - startTime;
                CleanupExecutionResult result = CleanupExecutionResult.ofSuccess(
                        action,
                        durationThreshold,
                        worldsToProcess,
                        processedWorlds,
                        totalRegionsEvaluated,
                        totalRegionsCleaned,
                        affectedRegionIds,
                        duration
                );

                this.lastResult = result;
                if (feedbackSender != null) {
                    messageService.send(feedbackSender, "cleanup.completed", Map.of(
                            "time", String.valueOf(duration),
                            "count", String.valueOf(totalRegionsCleaned)
                    ));
                }

                future.complete(result);
            } catch (Exception e) {
                plugin.getLogger().severe("Unexpected error during cleanup sweep: " + e.getMessage());
                CleanupExecutionResult result = CleanupExecutionResult.ofFailure(
                        action,
                        durationThreshold,
                        worldsToProcess,
                        e.getMessage()
                );
                this.lastResult = result;
                future.complete(result);
            } finally {
                isExecuting.set(false);
            }
        });

        return future;
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
