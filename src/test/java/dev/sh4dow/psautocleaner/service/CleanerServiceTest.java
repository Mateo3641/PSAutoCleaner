package dev.sh4dow.psautocleaner.service;

import dev.sh4dow.psautocleaner.core.config.CleanConfig;
import dev.sh4dow.psautocleaner.core.config.ConfigManager;
import dev.sh4dow.psautocleaner.core.message.MessageService;
import dev.sh4dow.psautocleaner.core.scheduler.TaskScheduler;
import dev.sh4dow.psautocleaner.model.CleanupAction;
import dev.sh4dow.psautocleaner.model.CleanupExecutionResult;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class CleanerServiceTest {

    private JavaPlugin plugin;
    private ConfigManager configManager;
    private MessageService messageService;
    private TaskScheduler scheduler;
    private InactivityService inactivityService;
    private AuditService auditService;
    private CleanerService cleanerService;

    @BeforeEach
    void setUp() {
        plugin = Mockito.mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CleanerServiceTest"));

        configManager = Mockito.mock(ConfigManager.class);
        messageService = Mockito.mock(MessageService.class);
        scheduler = Mockito.mock(TaskScheduler.class);
        inactivityService = Mockito.mock(InactivityService.class);
        auditService = Mockito.mock(AuditService.class);

        // Immediate sync task execution
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(scheduler).runSync(any(Runnable.class));

        CleanConfig config = new CleanConfig(
                true,
                Duration.ofDays(30),
                CleanupAction.REMOVE,
                Duration.ofHours(24),
                Duration.ofMinutes(10),
                Collections.emptyList(),
                false,
                "short",
                true,
                2,
                true,
                Collections.emptyList()
        );
        when(configManager.getCleanConfig()).thenReturn(config);

        cleanerService = new CleanerService(plugin, configManager, messageService, scheduler, inactivityService, auditService);
    }

    @Test
    void testExecutionLockPreventsConcurrentSweeps() {
        // Mock a long running scheduler to keep isExecuting true
        TaskScheduler blockingScheduler = Mockito.mock(TaskScheduler.class);
        doAnswer(invocation -> {
            // Do not run immediately, simulate running in background
            return null;
        }).when(blockingScheduler).runSync(any(Runnable.class));

        CleanerService lockedService = new CleanerService(plugin, configManager, messageService, blockingScheduler, inactivityService, auditService);

        // First call starts execution
        CompletableFuture<CleanupExecutionResult> firstCall = lockedService.executeSweep(
                CleanupAction.REMOVE,
                Duration.ofDays(30),
                List.of("world"),
                null
        );

        assertThat(lockedService.isExecuting()).isTrue();

        // Second call while first is executing must fail immediately
        CompletableFuture<CleanupExecutionResult> secondCall = lockedService.executeSweep(
                CleanupAction.REMOVE,
                Duration.ofDays(30),
                List.of("world"),
                null
        );

        assertThat(secondCall.isDone()).isTrue();
        CleanupExecutionResult result = secondCall.join();
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("already in progress");
    }
}
