package dev.sh4dow.psautocleaner.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Validates and bridges interaction with the ProtectionStones plugin.
 */
public class ProtectionStonesHook {

    private final JavaPlugin plugin;
    private boolean isHooked = false;
    private String version = "unknown";

    public ProtectionStonesHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Verifies if ProtectionStones is present and active on the server.
     */
    public boolean checkAndHook() {
        Plugin psPlugin = Bukkit.getPluginManager().getPlugin("ProtectionStones");
        if (psPlugin != null && psPlugin.isEnabled()) {
            this.isHooked = true;
            this.version = psPlugin.getDescription().getVersion();
            plugin.getLogger().info("Successfully hooked into ProtectionStones v" + version);
            return true;
        }

        this.isHooked = false;
        plugin.getLogger().severe("==========================================================");
        plugin.getLogger().severe(" ProtectionStones was NOT found or is not enabled!");
        plugin.getLogger().severe(" PSAutoCleaner requires ProtectionStones to function.");
        plugin.getLogger().severe("==========================================================");
        return false;
    }

    public boolean isHooked() {
        return isHooked;
    }

    public String getVersion() {
        return version;
    }
}
