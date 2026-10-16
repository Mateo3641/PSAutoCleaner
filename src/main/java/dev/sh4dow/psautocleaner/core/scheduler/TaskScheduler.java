package dev.sh4dow.psautocleaner.core.scheduler;

import java.util.function.Consumer;

/**
 * Abstraction for scheduling tasks in Paper and testing environments.
 */
public interface TaskScheduler {

    void runSync(Runnable runnable);

    void runAsync(Runnable runnable);

    CancellableTask runTimer(Runnable runnable, long delayTicks, long periodTicks);

    void cancelAll();

    interface CancellableTask {
        void cancel();
        boolean isCancelled();
    }
}
