package dev.sh4dow.psautocleaner.service;

import dev.sh4dow.psautocleaner.core.config.CleanConfig;
import dev.sh4dow.psautocleaner.core.config.ConfigManager;
import dev.sh4dow.psautocleaner.core.message.MessageService;
import dev.sh4dow.psautocleaner.core.scheduler.TaskScheduler;
import dev.sh4dow.psautocleaner.model.CleanupAction;
import dev.sh4dow.psautocleaner.model.CleanupExecutionResult;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service orchestrating scheduled and on-demand cleanup sweeps across target worlds.
 * Ensures all WorldGuard and ProtectionStones mutations execute synchronously on the server thread.
 */
public class CleanerService {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageService messageService;
    private final TaskScheduler scheduler;

    private final AtomicBoolean isExecuting = new AtomicBoolean(false);
    private TaskScheduler.CancellableTask currentTimerTask;
    private Instant nextScheduledRun;
    private CleanupExecutionResult lastResult;

    public CleanerService(
            JavaPlugin plugin,
            ConfigManager configManager,
            MessageService messageService,
            TaskScheduler scheduler
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.configManager = Objects.requireNonNull(configManager, "configManager cannot be null");
        this.messageService = Objects.requireNonNull(messageService, "messageService cannot be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler cannot be null");
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

        long delayTicks = config.initialDelayMinutes() * 60L * 20L;
        long periodTicks = config.intervalHours() * 3600L * 20L;

        long delayMillis = config.initialDelayMinutes() * 60L * 1000L;
        this.nextScheduledRun = Instant.ofEpochMilli(System.currentTimeMillis() + delayMillis);

        this.currentTimerTask = scheduler.runTimer(() -> {
            // Update next scheduled run for the following interval
            long periodMillis = configManager.getCleanConfig().intervalHours() * 3600L * 1000L;
            this.nextScheduledRun = Instant.ofEpochMilli(System.currentTimeMillis() + periodMillis);

            CleanConfig currentConfig = configManager.getCleanConfig();
            if (!currentConfig.enabled()) {
                return;
            }

            if (currentConfig.logToConsole()) {
                plugin.getLogger().info("Executing scheduled ProtectionStones cleanup sweep...");
            }

            executeSweep(
                    currentConfig.cleanupAction(),
                    currentConfig.inactiveDays(),
                    currentConfig.targetWorlds(),
                    null
            );
        }, delayTicks, periodTicks);

        plugin.getLogger().info(String.format(
                "Autonomous cleaner scheduled: First run in %d minutes, then every %d hours.",
                config.initialDelayMinutes(),
                config.intervalHours()
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
     * Executes a cleanup sweep across targeted worlds.
     * Guaranteed to dispatch commands synchronously on the primary server thread.
     */
    public CompletableFuture<CleanupExecutionResult> executeSweep(
            CleanupAction action,
            int days,
            List<String> explicitWorlds,
            CommandSender feedbackSender
    ) {
        CompletableFuture<CleanupExecutionResult> future = new CompletableFuture<>();

        if (!isExecuting.compareAndSet(false, true)) {
            String msg = "A cleanup sweep is already in progress.";
            if (feedbackSender != null) {
                messageService.send(feedbackSender, "cleanup.already-running", Collections.emptyMap());
            }
            future.complete(CleanupExecutionResult.ofFailure(action, days, explicitWorlds, msg));
            return future;
        }

        // Ensure execution happens on main thread
        scheduler.runSync(() -> {
            long startTime = System.currentTimeMillis();
            List<String> worldsToProcess = resolveTargetWorlds(explicitWorlds);

            if (feedbackSender != null) {
                messageService.send(feedbackSender, "cleanup.started", Map.of("count", String.valueOf(worldsToProcess.size())));
            }

            int processedCount = 0;
            try {
                for (String worldName : worldsToProcess) {
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        plugin.getLogger().warning("Skipping unknown/unloaded world: " + worldName);
                        continue;
                    }

                    // Command dispatch format: ps admin cleanup <action> <days> <world>
                    String command = String.format("ps admin cleanup %s %d %s", action.getCommandArg(), days, world.getName());
                    if (configManager.getCleanConfig().logToConsole()) {
                        plugin.getLogger().info("Dispatching: /" + command);
                    }

                    boolean executed = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    if (executed) {
                        processedCount++;
                        if (feedbackSender != null) {
                            messageService.send(feedbackSender, "cleanup.world-executed", Map.of(
                                    "world", world.getName(),
                                    "action", action.getCommandArg(),
                                    "days", String.valueOf(days)
                            ));
                        }
                    } else {
                        plugin.getLogger().warning("Failed to dispatch command: " + command);
                    }
                }

                long duration = System.currentTimeMillis() - startTime;
                CleanupExecutionResult result = CleanupExecutionResult.ofSuccess(
                        action,
                        days,
                        worldsToProcess,
                        processedCount,
                        duration
                );

                this.lastResult = result;
                if (feedbackSender != null) {
                    messageService.send(feedbackSender, "cleanup.completed", Map.of("time", String.valueOf(duration)));
                }

                future.complete(result);
            } catch (Exception e) {
                plugin.getLogger().severe("Unexpected error during cleanup sweep: " + e.getMessage());
                CleanupExecutionResult result = CleanupExecutionResult.ofFailure(
                        action,
                        days,
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
