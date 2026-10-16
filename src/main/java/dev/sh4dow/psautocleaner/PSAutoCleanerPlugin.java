package dev.sh4dow.psautocleaner;

import dev.sh4dow.psautocleaner.command.PSAutoCleanerCommand;
import dev.sh4dow.psautocleaner.command.PSAutoCleanerTabCompleter;
import dev.sh4dow.psautocleaner.core.config.ConfigManager;
import dev.sh4dow.psautocleaner.core.message.MessageService;
import dev.sh4dow.psautocleaner.core.scheduler.PaperTaskScheduler;
import dev.sh4dow.psautocleaner.core.scheduler.TaskScheduler;
import dev.sh4dow.psautocleaner.hook.PlaceholderApiHook;
import dev.sh4dow.psautocleaner.hook.ProtectionStonesHook;
import dev.sh4dow.psautocleaner.service.CleanerService;
import dev.sh4dow.psautocleaner.service.InactivityService;
import dev.sh4dow.psautocleaner.service.RegionInspectorService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PSAutoCleaner main plugin class.
 * Orchestrates lifecycle, dependency hooks, services, and scheduled tasks.
 */
public class PSAutoCleanerPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MessageService messageService;
    private TaskScheduler taskScheduler;
    private InactivityService inactivityService;
    private CleanerService cleanerService;
    private RegionInspectorService regionInspectorService;
    private PlaceholderApiHook papiHook;

    @Override
    public void onEnable() {
        getLogger().info("Initializing PSAutoCleaner v" + getDescription().getVersion() + "...");

        // 1. Dependency Validation: Verify ProtectionStones presence
        ProtectionStonesHook protectionStonesHook = new ProtectionStonesHook(this);
        if (!protectionStonesHook.checkAndHook()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 2. Load configurations and messages
        this.configManager = new ConfigManager(this);
        this.configManager.load();

        // 3. Initialize message service (MiniMessage)
        this.messageService = new MessageService(configManager);

        // 4. Initialize scheduler and core services
        this.taskScheduler = new PaperTaskScheduler(this);
        this.inactivityService = new InactivityService(configManager);
        this.cleanerService = new CleanerService(this, configManager, messageService, taskScheduler);
        this.regionInspectorService = new RegionInspectorService(inactivityService);

        // 5. Register commands and tab completion
        PluginCommand command = getCommand("psautocleaner");
        if (command != null) {
            command.setExecutor(new PSAutoCleanerCommand(
                    configManager,
                    messageService,
                    inactivityService,
                    cleanerService,
                    regionInspectorService
            ));
            command.setTabCompleter(new PSAutoCleanerTabCompleter());
        } else {
            getLogger().warning("Could not register /psautocleaner command from plugin.yml!");
        }

        // 6. Register PlaceholderAPI expansion if present
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.papiHook = new PlaceholderApiHook(
                    this,
                    configManager,
                    inactivityService,
                    cleanerService,
                    regionInspectorService
            );
            if (this.papiHook.register()) {
                getLogger().info("PlaceholderAPI expansion registered successfully.");
            }
        }

        // 7. Start autonomous periodic task
        this.cleanerService.startScheduledTask();

        getLogger().info("PSAutoCleaner enabled successfully.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling PSAutoCleaner...");

        if (cleanerService != null) {
            cleanerService.stopScheduledTask();
        }

        if (taskScheduler != null) {
            taskScheduler.cancelAll();
        }

        if (papiHook != null) {
            try {
                papiHook.unregister();
            } catch (Throwable ignored) {
            }
        }

        getLogger().info("PSAutoCleaner disabled successfully.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public InactivityService getInactivityService() {
        return inactivityService;
    }

    public CleanerService getCleanerService() {
        return cleanerService;
    }

    public RegionInspectorService getRegionInspectorService() {
        return regionInspectorService;
    }
}
