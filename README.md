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
| `/psac logs <player>` | `psautocleaner.admin` / `.logs` | Query persistent SQLite audit records of deleted claims |
| `/psac run [action] [time] [world]` | `psautocleaner.admin` | Force an immediate cleanup sweep |
| `/psac preview [time] [world]` | `psautocleaner.admin` | Preview regions eligible for deletion |
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
| `%psautocleaner_threshold_days%` | Inactivity threshold in days defined in `config.yml` |
| `%psautocleaner_threshold_time%` | Inactivity threshold formatted (e.g. `30d`, `3m`) |
| `%psautocleaner_interval%` | Automated sweep interval formatted (e.g. `24h`, `30m`) |
| `%psautocleaner_next_cleanup%` | Time remaining until the next automatic server sweep |
| `%psautocleaner_current_region_id%` | ID of the claim region player is standing inside |
| `%psautocleaner_current_region_owner%` | Owner of the claim region player is standing inside |
| `%psautocleaner_current_region_days_left%` | Days left before the current claim expires (checks most active affiliate) |
| `%psautocleaner_current_region_time_left%` | Formatted time left before the current claim expires |
| `%psautocleaner_current_region_most_active_player%` | Name of the most recently active owner or member |
| `%psautocleaner_current_region_most_active_role%` | Role of the most active player (`owner` or `member`) |
| `%psautocleaner_current_region_is_protected_by_member%` | Whether the claim is currently kept active by a member |

---

## ⚙️ Configuration (`config.yml`)

```yaml
# Enable or disable the autonomous periodic cleaner task
enabled: true

# Inactivity threshold before claims qualify for cleanup
# Supports flexible units: s (seconds), m (minutes), h (hours), d (days), w (weeks), mo (months)
# Examples: "30d", "14d", "12h", "3m" (testing)
inactive-time: "30d"

# Cleanup action: remove
cleanup-action: "remove"

# Repetitive background task scheduler configuration
scheduler:
  # How often the cleanup sweep runs automatically (e.g. "24h", "12h", "1h", "30m", "3m")
  interval: "24h"

  # Delay before first execution after server boot (e.g. "10m", "5m", "1m", "30s")
  initial-delay: "10m"

# Target worlds (empty list = all loaded worlds)
# To specify worlds, remove the [] and list them like this:
# target-worlds:
#   - "world"
#   - "lobby"
target-worlds: []

# Log cleanup summary to console
log-to-console: true

# Format for countdown placeholders ("short" or "verbose")
time-format: "short"

# Whether to check region members (added via /ps add) in addition to owners
# If true, a claim won't expire as long as at least ONE member or owner remains active
check-members: true

# Number of claims deleted per server tick (20 TPS protection against tick lag spikes)
batch-size-per-tick: 2

# Grouped multiline broadcast when a player's claims are cleaned up
broadcast:
  enabled: true
  lines:
    - "<gold><bold>¡LIMPIEZA DE INACTIVIDAD!</bold></gold>"
    - "<yellow>Las protecciones de <gold><player></gold> han sido eliminadas por inactividad (<gold><time_inactive></gold> sin jugar).</yellow>"
    - "<gray>¡El terreno ha quedado libre para nuevos aventureros!</gray>"
```

---

## 🛠️ Building from Source

Requires Java 21 and Maven:

```bash
mvn clean package
```

The compiled shaded jar will be located in `target/psautocleaner-1.0.4.jar`.
