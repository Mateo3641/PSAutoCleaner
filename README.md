# PSAutoCleaner 🛡️🧹

**PSAutoCleaner** is a high-performance, autonomous maintenance companion addon for [ProtectionStones](https://github.com/espidev/ProtectionStones) on Paper 1.21+ servers (Java 21).

It automatically monitors and cleans up protection regions belonging to inactive players without lagging the server, corrupting WorldGuard state, or causing concurrency conflicts.

---

## ✨ Features

- **Autonomous Periodic Scheduling:** Configurable sweep frequency (e.g. every 24 hours) with safe initial delay to avoid boot lag.
- **Main-Thread Safe:** All WorldGuard and ProtectionStones operations execute safely on the primary tick loop.
- **Multiple Cleanup Modes:** Supports `remove` (delete region & break block), `regen` (natural terrain regeneration), and `disown` (revoke ownership).
- **PlaceholderAPI Integration:** Extensive placeholders for scoreboards, action bars, and tabs to show expiration countdowns and standing claim status.
- **Region Inspection:** Players and admins can inspect claims they are standing in to see who owns them and when they expire.
- **Bypass Protection:** Give players or VIP groups `psautocleaner.bypass` to permanently exempt them from cleanup sweeps.
- **Hot Reloading:** Safely reload configuration and reschedule timers on the fly via `/psac reload`.

---

## 📋 Commands & Permissions

| Command | Permission | Description |
|---|---|---|
| `/psac check [player]` | `psautocleaner.check` / `.check.others` | Check inactivity status and claim deletion timer |
| `/psac region` | `psautocleaner.check` | Inspect the claim you are currently standing inside |
| `/psac status` | `psautocleaner.admin` | View scheduler status, next sweep countdown, and active settings |
| `/psac run [action] [days] [world]` | `psautocleaner.admin` | Force an immediate cleanup sweep |
| `/psac preview [days] [world]` | `psautocleaner.admin` | Preview regions eligible for deletion |
| `/psac reload` | `psautocleaner.admin` | Reload configuration and restart scheduled tasks |

*Bypass Permission:* `psautocleaner.bypass` (exempts player from automated deletion).

---

## 🧩 PlaceholderAPI Placeholders

| Placeholder | Description |
|---|---|
| `%psautocleaner_days_left%` | Days remaining before player's claims are eligible for cleanup |
| `%psautocleaner_time_left%` | Formatted countdown until claim expiration (e.g. `14d 06h` or `Never`) |
| `%psautocleaner_days_inactive%` | Days elapsed since player was last seen |
| `%psautocleaner_time_inactive%` | Formatted duration since last login |
| `%psautocleaner_status%` | Inactivity state (`ACTIVE`, `INACTIVE`, `EXPIRING_SOON`, `EXPIRED`, `EXEMPT`) |
| `%psautocleaner_threshold_days%` | Inactivity threshold defined in `config.yml` |
| `%psautocleaner_next_cleanup%` | Time remaining until the next automatic server sweep |
| `%psautocleaner_current_region_id%` | ID of the claim region player is standing inside |
| `%psautocleaner_current_region_owner%` | Owner of the claim region player is standing inside |
| `%psautocleaner_current_region_days_left%` | Days left before the current claim expires |
| `%psautocleaner_current_region_time_left%` | Formatted time left before the current claim expires |

---

## ⚙️ Configuration (`config.yml`)

```yaml
# Enable or disable the autonomous periodic cleaner task
enabled: true

# Inactivity threshold in days
inactive-days: 30

# Cleanup action: remove, regen, disown
cleanup-action: "remove"

# Frequency of cleanup in hours
interval-hours: 24

# Initial delay in minutes after server boot
initial-delay-minutes: 10

# Target worlds (empty list = all loaded worlds)
target-worlds: []
# - "world"
# - "lobby"

# Log cleanup summary to console
log-to-console: true

# Format for countdown placeholders ("short" or "verbose")
time-format: "short"
```

---

## 🛠️ Building from Source

Requires Java 21 and Maven:

```bash
mvn clean package
```

The compiled shaded jar will be located in `target/psautocleaner-1.0.0-SNAPSHOT.jar`.
