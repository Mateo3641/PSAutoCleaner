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
        int inactiveDays = config.getInt("inactive-days", 30);
        String actionStr = config.getString("cleanup-action", "remove");
        CleanupAction action = CleanupAction.fromString(actionStr).orElse(CleanupAction.REMOVE);
        long intervalHours = config.getLong("interval-hours", 24L);
        long initialDelayMinutes = config.getLong("initial-delay-minutes", 10L);
        List<String> targetWorlds = config.getStringList("target-worlds");
        boolean logToConsole = config.getBoolean("log-to-console", true);
        String timeFormat = config.getString("time-format", "short");

        if (inactiveDays < 1) {
            plugin.getLogger().warning("Invalid inactive-days (" + inactiveDays + ") in config.yml. Defaulting to 30.");
            inactiveDays = 30;
        }
        if (intervalHours < 1) {
            plugin.getLogger().warning("Invalid interval-hours (" + intervalHours + ") in config.yml. Defaulting to 24.");
            intervalHours = 24;
        }

        this.cleanConfig = new CleanConfig(
                enabled,
                inactiveDays,
                action,
                intervalHours,
                initialDelayMinutes,
                targetWorlds,
                logToConsole,
                timeFormat
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
