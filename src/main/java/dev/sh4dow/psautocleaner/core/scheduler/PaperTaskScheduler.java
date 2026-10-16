package dev.sh4dow.psautocleaner.core.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Paper implementation of TaskScheduler wrapping BukkitScheduler.
 */
public class PaperTaskScheduler implements TaskScheduler {

    private final Plugin plugin;

    public PaperTaskScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runSync(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    @Override
    public void runAsync(Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    @Override
    public CancellableTask runTimer(Runnable runnable, long delayTicks, long periodTicks) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
        return new CancellableTask() {
            @Override
            public void cancel() {
                task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return task.isCancelled();
            }
        };
    }

    @Override
    public void cancelAll() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}
