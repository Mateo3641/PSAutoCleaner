package dev.sh4dow.psautocleaner.service;

import dev.sh4dow.psautocleaner.core.config.CleanConfig;
import dev.sh4dow.psautocleaner.core.config.ConfigManager;
import dev.sh4dow.psautocleaner.model.CleanupAction;
import dev.sh4dow.psautocleaner.model.InactivityProfile;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class InactivityServiceTest {

    private ConfigManager configManager;
    private InactivityService inactivityService;

    @BeforeEach
    void setUp() {
        configManager = Mockito.mock(ConfigManager.class);
        CleanConfig config = new CleanConfig(
                true,
                Duration.ofDays(30),
                CleanupAction.REMOVE,
                Duration.ofHours(24),
                Duration.ofMinutes(10),
                Collections.emptyList(),
                true,
                "short",
                true,
                2,
                true,
                Collections.emptyList()
        );
        when(configManager.getCleanConfig()).thenReturn(config);
        inactivityService = new InactivityService(configManager);
    }

    @Test
    void testOnlinePlayerHasZeroInactivityAndFullRemainingDays() {
        OfflinePlayer offlinePlayer = Mockito.mock(OfflinePlayer.class);
        Player onlinePlayer = Mockito.mock(Player.class);
        UUID uuid = UUID.randomUUID();

        when(offlinePlayer.getUniqueId()).thenReturn(uuid);
        when(offlinePlayer.getName()).thenReturn("TestPlayer");
        when(offlinePlayer.isOnline()).thenReturn(true);
        when(offlinePlayer.getPlayer()).thenReturn(onlinePlayer);
        when(onlinePlayer.hasPermission(InactivityService.BYPASS_PERMISSION)).thenReturn(false);

        InactivityProfile profile = inactivityService.calculate(offlinePlayer);

        assertThat(profile.isOnline()).isTrue();
        assertThat(profile.daysInactive()).isEqualTo(0);
        assertThat(profile.daysRemaining()).isEqualTo(30);
        assertThat(profile.isExpired()).isFalse();
        assertThat(profile.isExempt()).isFalse();
        assertThat(inactivityService.getStatusDescriptor(profile)).isEqualTo("ACTIVE");
    }

    @Test
    void testOfflinePlayerInactiveFor10Days() {
        OfflinePlayer offlinePlayer = Mockito.mock(OfflinePlayer.class);
        UUID uuid = UUID.randomUUID();

        long now = System.currentTimeMillis();
        long tenDaysAgo = now - TimeUnit.DAYS.toMillis(10);

        when(offlinePlayer.getUniqueId()).thenReturn(uuid);
        when(offlinePlayer.getName()).thenReturn("Inactive10");
        when(offlinePlayer.isOnline()).thenReturn(false);
        when(offlinePlayer.getPlayer()).thenReturn(null);
        when(offlinePlayer.getLastLogin()).thenReturn(tenDaysAgo);
        when(offlinePlayer.getLastPlayed()).thenReturn(tenDaysAgo);

        InactivityProfile profile = inactivityService.calculate(offlinePlayer);

        assertThat(profile.isOnline()).isFalse();
        assertThat(profile.daysInactive()).isEqualTo(10);
        assertThat(profile.daysRemaining()).isEqualTo(20);
        assertThat(profile.isExpired()).isFalse();
        assertThat(profile.isExempt()).isFalse();
        assertThat(inactivityService.getStatusDescriptor(profile)).isEqualTo("INACTIVE");
    }

    @Test
    void testOfflinePlayerInactiveFor40DaysIsExpired() {
        OfflinePlayer offlinePlayer = Mockito.mock(OfflinePlayer.class);
        UUID uuid = UUID.randomUUID();

        long now = System.currentTimeMillis();
        long fortyDaysAgo = now - TimeUnit.DAYS.toMillis(40);

        when(offlinePlayer.getUniqueId()).thenReturn(uuid);
        when(offlinePlayer.getName()).thenReturn("ExpiredPlayer");
        when(offlinePlayer.isOnline()).thenReturn(false);
        when(offlinePlayer.getPlayer()).thenReturn(null);
        when(offlinePlayer.getLastLogin()).thenReturn(fortyDaysAgo);
        when(offlinePlayer.getLastPlayed()).thenReturn(fortyDaysAgo);

        InactivityProfile profile = inactivityService.calculate(offlinePlayer);

        assertThat(profile.daysInactive()).isEqualTo(40);
        assertThat(profile.daysRemaining()).isEqualTo(0);
        assertThat(profile.isExpired()).isTrue();
        assertThat(profile.isExempt()).isFalse();
        assertThat(inactivityService.getStatusDescriptor(profile)).isEqualTo("EXPIRED");
    }

    @Test
    void testMinuteLevelInactivityThresholdExpiresInThreeMinutes() {
        CleanConfig minuteConfig = new CleanConfig(
                true,
                Duration.ofMinutes(3), // 3 minutes threshold!
                CleanupAction.REMOVE,
                Duration.ofMinutes(1),
                Duration.ofSeconds(10),
                Collections.emptyList(),
                true,
                "short",
                true,
                2,
                true,
                Collections.emptyList()
        );
        when(configManager.getCleanConfig()).thenReturn(minuteConfig);

        OfflinePlayer offlinePlayer = Mockito.mock(OfflinePlayer.class);
        UUID uuid = UUID.randomUUID();

        long now = System.currentTimeMillis();
        long fourMinutesAgo = now - TimeUnit.MINUTES.toMillis(4);

        when(offlinePlayer.getUniqueId()).thenReturn(uuid);
        when(offlinePlayer.getName()).thenReturn("MinutePlayer");
        when(offlinePlayer.isOnline()).thenReturn(false);
        when(offlinePlayer.getPlayer()).thenReturn(null);
        when(offlinePlayer.getLastLogin()).thenReturn(fourMinutesAgo);
        when(offlinePlayer.getLastPlayed()).thenReturn(fourMinutesAgo);

        InactivityProfile profile = inactivityService.calculate(offlinePlayer);

        assertThat(profile.isExpired()).isTrue();
        assertThat(profile.millisRemaining()).isEqualTo(0);
    }

    @Test
    void testPlayerWithBypassPermissionIsExempt() {
        OfflinePlayer offlinePlayer = Mockito.mock(OfflinePlayer.class);
        Player onlinePlayer = Mockito.mock(Player.class);
        UUID uuid = UUID.randomUUID();

        when(offlinePlayer.getUniqueId()).thenReturn(uuid);
        when(offlinePlayer.getName()).thenReturn("AdminVIP");
        when(offlinePlayer.isOnline()).thenReturn(true);
        when(offlinePlayer.getPlayer()).thenReturn(onlinePlayer);
        when(onlinePlayer.hasPermission(InactivityService.BYPASS_PERMISSION)).thenReturn(true);

        InactivityProfile profile = inactivityService.calculate(offlinePlayer);

        assertThat(profile.isExempt()).isTrue();
        assertThat(profile.isExpired()).isFalse();
        assertThat(profile.daysRemaining()).isEqualTo(Long.MAX_VALUE);
        assertThat(inactivityService.getStatusDescriptor(profile)).isEqualTo("EXEMPT");
    }

    @Test
    void testFormatDurationShortAndVerbose() {
        long duration = TimeUnit.DAYS.toMillis(5) + TimeUnit.HOURS.toMillis(3) + TimeUnit.MINUTES.toMillis(15);

        // Short format (default from setUp)
        assertThat(inactivityService.formatDuration(duration)).isEqualTo("5d 3h");

        // Verbose format
        CleanConfig verboseConfig = new CleanConfig(
                true,
                Duration.ofDays(30),
                CleanupAction.REMOVE,
                Duration.ofHours(24),
                Duration.ofMinutes(10),
                Collections.emptyList(),
                true,
                "verbose",
                true,
                2,
                true,
                Collections.emptyList()
        );
        when(configManager.getCleanConfig()).thenReturn(verboseConfig);
        assertThat(inactivityService.formatDuration(duration)).isEqualTo("5d 3h 15m");

        // Edge case: Long.MAX_VALUE (Exempt)
        assertThat(inactivityService.formatDuration(Long.MAX_VALUE)).isEqualTo("Never");
    }
}
