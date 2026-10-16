package dev.sh4dow.psautocleaner.core.config;

import dev.sh4dow.psautocleaner.model.CleanupAction;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;

/**
 * Manages loading, validation, and retrieval of configurations and localized messages.
 */
public class ConfigManager {

    private final JavaPlugin plugin;
    private final File messagesFile;
    private FileConfiguration messagesConfig;
    private CleanConfig cleanConfig;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
    }

    /**
     * Initializes config files and loads active configuration.
     */
    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        boolean enabled = config.getBoolean("enabled", true);
        String actionStr = config.getString("cleanup-action", "remove");
        CleanupAction action = CleanupAction.fromString(actionStr).orElse(CleanupAction.REMOVE);
        List<String> targetWorlds = config.getStringList("target-worlds");
        boolean logToConsole = config.getBoolean("log-to-console", true);
        String timeFormat = config.getString("time-format", "short");
        boolean checkMembers = config.getBoolean("check-members", true);

        // Parse inactive time
        java.time.Duration inactiveDuration;
        String inactiveTimeStr = config.getString("inactive-time");
        if (inactiveTimeStr != null && !inactiveTimeStr.isBlank()) {
            try {
                inactiveDuration = dev.sh4dow.psautocleaner.util.DurationParser.parse(inactiveTimeStr);
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid inactive-time '" + inactiveTimeStr + "' in config.yml. Defaulting to 30d.");
                inactiveDuration = java.time.Duration.ofDays(30);
            }
        } else if (config.contains("inactive-days")) {
            inactiveDuration = java.time.Duration.ofDays(config.getInt("inactive-days", 30));
        } else {
            inactiveDuration = java.time.Duration.ofDays(30);
        }

        // Parse scheduler interval
        java.time.Duration intervalDuration;
        String intervalStr = config.getString("scheduler.interval", config.getString("interval"));
        if (intervalStr != null && !intervalStr.isBlank()) {
            try {
                intervalDuration = dev.sh4dow.psautocleaner.util.DurationParser.parse(intervalStr);
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid scheduler.interval '" + intervalStr + "' in config.yml. Defaulting to 24h.");
                intervalDuration = java.time.Duration.ofHours(24);
            }
        } else if (config.contains("interval-hours")) {
            intervalDuration = java.time.Duration.ofHours(config.getLong("interval-hours", 24L));
        } else {
            intervalDuration = java.time.Duration.ofHours(24);
        }

        // Parse scheduler initial delay
        java.time.Duration initialDelayDuration;
        String delayStr = config.getString("scheduler.initial-delay", config.getString("initial-delay"));
        if (delayStr != null && !delayStr.isBlank()) {
            try {
                initialDelayDuration = dev.sh4dow.psautocleaner.util.DurationParser.parse(delayStr);
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid scheduler.initial-delay '" + delayStr + "' in config.yml. Defaulting to 10m.");
                initialDelayDuration = java.time.Duration.ofMinutes(10);
            }
        } else if (config.contains("initial-delay-minutes")) {
            initialDelayDuration = java.time.Duration.ofMinutes(config.getLong("initial-delay-minutes", 10L));
        } else {
            initialDelayDuration = java.time.Duration.ofMinutes(10);
        }

        this.cleanConfig = new CleanConfig(
                enabled,
                inactiveDuration,
                action,
                intervalDuration,
                initialDelayDuration,
                targetWorlds,
                logToConsole,
                timeFormat,
                checkMembers
        );

        loadMessages();
    }

    private void loadMessages() {
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Merge missing default keys from jar
        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream != null) {
            try (InputStreamReader reader = new InputStreamReader(defStream, StandardCharsets.UTF_8)) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(reader);
                this.messagesConfig.setDefaults(defConfig);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load default messages from JAR", e);
            }
        }
    }

    public CleanConfig getCleanConfig() {
        return cleanConfig != null ? cleanConfig : CleanConfig.defaultValues();
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    public String getRawMessage(String path, String defaultValue) {
        if (messagesConfig == null) {
            return defaultValue;
        }
        return messagesConfig.getString(path, defaultValue);
    }
}
