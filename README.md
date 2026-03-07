<div align="center">

```
╔════════════════════════════════════════════╗
║          ✨ S T A R B A N S ✨           ║
║   The Ultimate Moderation Experience 🚀    ║
║        v1.0.0 • By Eministar 💎           ║
╚════════════════════════════════════════════╝
```

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21+-brightgreen.svg)](https://www.spigotmc.org/)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Website](https://img.shields.io/badge/Website-star--dev.xyz-blue.svg)](https://star-dev.xyz)

**A premium, design-focused moderation and punishment management plugin for Spigot/Paper servers.**

[Features](#-features) • [Installation](#-installation) • [Commands](#-commands) • [Permissions](#-permissions) • [Configuration](#-configuration) • [Support](#-support)

</div>

---

## ✨ Features

StarBans provides a complete moderation suite with an elegant interface and powerful features:

### 🎯 Core Moderation
- ⚡ **Player Bans** - Permanent and temporary player bans
- 🌐 **IP Bans** - IP-based banning with temporary options
- 🔇 **Mute System** - Permanent and temporary mutes
- 👢 **Kick System** - Kick players with case logging
- 📝 **Note System** - Add administrative notes to player profiles
- 🔗 **Alt Account Detection** - Mark and track alternative accounts
- 🚫 **IP Blacklist** - Maintain a blacklist of banned IP addresses

### 🎨 User Interface
- 🖥️ **Interactive GUI** - Beautiful, easy-to-use admin interface
- 📊 **Case History** - Complete moderation history per player
- 👥 **Player Browser** - Browse and manage all known players
- 📈 **Activity Log** - Global activity tracking
- 🔍 **Profile View** - Detailed player profiles with all relevant data

### 🔧 Advanced Features
- 🛡️ **VPN/Proxy Detection** - Optional integration with ProxyCheck
- 📢 **Discord Webhooks** - Send moderation actions to Discord channels
- 📱 **PlaceholderAPI** - Full PlaceholderAPI support with 17+ placeholders
- 🌍 **Network Support** - Proxy/BungeeCord/Velocity support for synchronized bans
- 🎭 **Command Overrides** - Optional override of vanilla `/ban`, `/mute`, etc.
- 🌐 **Multi-Language** - English and German language files included

### 💾 Storage Options
- 🗄️ **SQLite** - Built-in, zero-config database (recommended)
- 🐬 **MariaDB/MySQL** - Remote database for network setups
- 📄 **JSON** - File-based storage for small servers

---

## 📦 Installation

### Requirements
- **Minecraft**: 1.21 or higher
- **Server Software**: Spigot or Paper (Paper recommended)
- **Java**: 21 or higher

### Steps

1. **Download** the latest `StarBans-1.0.0.jar` from releases
2. **Place** the JAR file in your server's `plugins/` folder
3. **Restart** your server
4. **Configure** the plugin in `plugins/StarBans/config.yml`
5. **(Optional)** Install [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) for placeholder support

### Network Setup (Optional)

For BungeeCord/Velocity networks:

1. Install StarBans on all backend servers
2. Configure a shared MariaDB database in `config.yml`
3. Enable proxy support in network settings
4. **(Optional)** Install the Velocity addon for enhanced integration

---

## 🎮 Commands

### Main Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/starbans help` | Show help menu | `starbans.command.gui` |
| `/starbans reload` | Reload configuration | `starbans.command.reload` |
| `/starbans gui [player]` | Open admin GUI | `starbans.gui.open` |
| `/starbans check <player>` | Check player status | `starbans.command.check` |
| `/starbans cases <player>` | View case history | `starbans.command.cases` |
| `/starbans case <id>` | View specific case | `starbans.command.cases` |

### Ban Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/starbans ban <player> [reason]` | Permanently ban a player | `starbans.command.ban` |
| `/starbans tempban <player> <duration> [reason]` | Temporarily ban a player | `starbans.command.tempban` |
| `/starbans unban <player\|ip> [reason]` | Unban a player or IP | `starbans.command.unban` |
| `/starbans ipban <player\|ip> [reason]` | Ban an IP address | `starbans.command.ipban` |
| `/starbans tempipban <player\|ip> <duration> [reason]` | Temporarily ban an IP | `starbans.command.ipban` |
| `/starbans unipban <player\|ip> [reason]` | Unban an IP address | `starbans.command.unipban` |

### Mute Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/starbans mute <player> [reason]` | Permanently mute a player | `starbans.command.mute` |
| `/starbans tempmute <player> <duration> [reason]` | Temporarily mute a player | `starbans.command.tempmute` |
| `/starbans unmute <player> [reason]` | Unmute a player | `starbans.command.unmute` |

### Other Moderation

| Command | Description | Permission |
|---------|-------------|------------|
| `/starbans kick <player> [reason]` | Kick a player | `starbans.command.kick` |
| `/starbans note <player> <label> <text>` | Add a note to a player | `starbans.command.note` |
| `/starbans notes <player>` | View player notes | `starbans.command.notes` |
| `/starbans alt mark <label> <player1> <player2> [note]` | Mark alt accounts | `starbans.command.alt` |
| `/starbans alt list <player>` | List alt accounts | `starbans.command.alt` |
| `/starbans alt clear <caseId> [reason]` | Clear alt flag | `starbans.command.alt` |
| `/starbans ipblacklist <add\|remove> <ip> [reason]` | Manage IP blacklist | `starbans.command.ipblacklist` |

### Short Aliases

All commands have convenient short aliases starting with `s`:

- `/sban` → `/starbans ban`
- `/stempban` → `/starbans tempban`
- `/sunban` → `/starbans unban`
- `/sipban` → `/starbans ipban`
- `/stempipban` → `/starbans tempipban`
- `/sunipban` → `/starbans unipban`
- `/smute` → `/starbans mute`
- `/stempmute` → `/starbans tempmute`
- `/sunmute` → `/starbans unmute`
- `/skick` → `/starbans kick`
- `/snote` → `/starbans note`
- `/snotes` → `/starbans notes`
- `/scases` → `/starbans cases`
- `/salt` → `/starbans alt`
- `/sipblacklist` → `/starbans ipblacklist`

### Duration Format

Durations can be specified using the following formats:

- `30m` - 30 minutes
- `1h` - 1 hour
- `12h` - 12 hours
- `1d` - 1 day
- `7d` - 7 days
- `30d` - 30 days
- `1d12h` - Combined (1 day and 12 hours)
- `perm` or `permanent` - Permanent (where applicable)

---

## 🔐 Permissions

### Core Permissions

| Permission | Description |
|------------|-------------|
| `starbans.admin` | Full access to all StarBans features (bypass) |
| `starbans.notify` | Receive update notifications |

### Command Permissions

| Permission | Description |
|------------|-------------|
| `starbans.command.gui` | Access main GUI commands |
| `starbans.command.reload` | Reload configuration |
| `starbans.command.check` | Check player status |
| `starbans.command.cases` | View case history |
| `starbans.command.resolve` | Resolve active cases |
| `starbans.command.note` | Create notes |
| `starbans.command.notes` | View notes |
| `starbans.command.ban` | Permanent bans |
| `starbans.command.tempban` | Temporary bans |
| `starbans.command.unban` | Remove bans |
| `starbans.command.ipban` | IP bans |
| `starbans.command.unipban` | Remove IP bans |
| `starbans.command.mute` | Permanent mutes |
| `starbans.command.tempmute` | Temporary mutes |
| `starbans.command.unmute` | Remove mutes |
| `starbans.command.kick` | Kick players |
| `starbans.command.alt` | Alt account management |
| `starbans.command.ipblacklist` | IP blacklist management |

### GUI Permissions

| Permission | Description |
|------------|-------------|
| `starbans.gui.open` | Open main GUI |
| `starbans.gui.browser` | Browse players |
| `starbans.gui.profile` | Open player profiles |
| `starbans.gui.activity` | View activity log |
| `starbans.gui.history` | View case history |
| `starbans.gui.case.view` | View case details |
| `starbans.gui.case.resolve` | Resolve cases from GUI |
| `starbans.gui.notes.view` | View notes in GUI |
| `starbans.gui.notes.create` | Create notes via GUI |
| `starbans.gui.related` | View related accounts |
| `starbans.gui.alt.mark` | Mark alts from GUI |
| `starbans.gui.punish.ban` | GUI ban button |
| `starbans.gui.punish.tempban` | GUI tempban presets |
| `starbans.gui.punish.unban` | GUI unban button |
| `starbans.gui.punish.mute` | GUI mute button |
| `starbans.gui.punish.tempmute` | GUI tempmute presets |
| `starbans.gui.punish.unmute` | GUI unmute button |

> **Note**: GUI punishment actions require both the GUI permission AND the related command permission.

---

## 📊 PlaceholderAPI

StarBans provides 17+ placeholders for use with PlaceholderAPI:

### Player Placeholders

| Placeholder | Description |
|-------------|-------------|
| `%starbans_status%` | Player's current status |
| `%starbans_is_banned%` | Whether player is banned (true/false) |
| `%starbans_ban_reason%` | Current ban reason |
| `%starbans_ban_remaining%` | Time remaining on ban |
| `%starbans_is_muted%` | Whether player is muted (true/false) |
| `%starbans_mute_reason%` | Current mute reason |
| `%starbans_mute_remaining%` | Time remaining on mute |
| `%starbans_last_ip%` | Player's last known IP |
| `%starbans_case_count%` | Total case count |
| `%starbans_note_count%` | Total note count |
| `%starbans_alt_count%` | Number of alt accounts |
| `%starbans_last_case_type%` | Type of last case |
| `%starbans_last_case_reason%` | Reason for last case |

### Global Placeholders

| Placeholder | Description |
|-------------|-------------|
| `%starbans_active_bans%` | Total active bans |
| `%starbans_active_ip_bans%` | Total active IP bans |
| `%starbans_active_mutes%` | Total active mutes |
| `%starbans_total_cases%` | Total cases in system |

---

## ⚙️ Configuration

### Basic Setup

```yaml
# Database configuration
database:
  type: SQLITE  # SQLITE, MARIADB, or JSON
  table-prefix: starbans
  
  # MariaDB settings (if using MARIADB)
  mariadb:
    host: localhost
    port: 3306
    database: minecraft
    username: root
    password: password
    pool-size: 10

# Network settings
network:
  proxy-support:
    enabled: false  # Enable for BungeeCord/Velocity
    mode: PROXY     # PROXY or BACKEND
    
# Discord webhooks
discord:
  enabled: false
  webhooks:
    bans: "https://discord.com/api/webhooks/..."
    mutes: "https://discord.com/api/webhooks/..."
    kicks: "https://discord.com/api/webhooks/..."

# VPN/Proxy detection
vpn-detection:
  enabled: false
  provider: PROXYCHECK
  api-key: "your-api-key"
  
# Language
language: en  # en or de
```

### Command Overrides

StarBans can optionally override vanilla commands like `/ban`, `/mute`, etc.:

```yaml
command-overrides:
  enabled: true
  commands:
    - ban
    - tempban
    - unban
    - mute
    - kick
```

---

## 🗄️ Database

### Supported Backends

| Type | Use Case | Recommendation |
|------|----------|----------------|
| **SQLite** | Single server, local storage | ✅ Recommended for most setups |
| **MariaDB/MySQL** | Network setup, shared database | ✅ For multi-server networks |
| **JSON** | Very small servers | ⚠️ Not recommended for production |

### Created Tables

- `<prefix>_cases` - Stores all moderation cases
- `<prefix>_profiles` - Stores player profiles and IP data

### Stored Data

- Player bans (permanent and temporary)
- IP bans (permanent and temporary)
- Mutes (permanent and temporary)
- Kick records
- Administrative notes
- Alt account markers
- IP blacklist entries
- Player profile data with last known IP

---

## 🌐 Network Setup

StarBans supports BungeeCord and Velocity networks with synchronized punishments across servers.

### Setup Steps

1. **Install** StarBans on all backend servers
2. **Configure** MariaDB database in `config.yml` on all servers (same credentials)
3. **Enable** proxy support:
   ```yaml
   network:
     proxy-support:
       enabled: true
       mode: BACKEND
   ```
4. **Configure** proxy mode on your proxy server (if using Velocity addon)
5. **Restart** all servers

### Velocity Addon

For enhanced Velocity support, install the StarBans-VelocityAddon:

1. Place `StarBans-VelocityAddon-1.0.0.jar` in your Velocity `plugins/` folder
2. Configure the addon's `config.yml`
3. Restart Velocity

---

## 🎨 Features in Detail

### Interactive GUI System

The GUI provides a complete moderation interface:

- **Player Browser**: Search and browse all known players
- **Profile View**: See all player data, cases, and notes at a glance
- **Quick Actions**: Ban, mute, kick directly from the GUI
- **Case Management**: View, filter, and resolve cases
- **Note System**: Add and view administrative notes
- **Alt Detection**: View and manage related accounts

### Discord Integration

Send moderation actions to Discord channels:

- Customizable webhooks per action type
- Rich embeds with all case details
- Moderator information
- Timestamps and durations
- Reason display

### VPN/Proxy Detection

Block VPN and proxy connections:

- Integration with ProxyCheck.io
- Configurable action (kick/ban)
- Whitelist support
- Cache for performance

---

## 🤝 Support

- 🌐 **Website**: [star-dev.xyz](https://star-dev.xyz)
- 📧 **Email**: Contact via website
- 🐛 **Issues**: [GitHub Issues](https://github.com/eministar/StarBans/issues)

---

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **Paper/Spigot Team** - For the excellent server software
- **PlaceholderAPI** - For the placeholder system
- **HikariCP** - For database connection pooling
- **Community** - For feedback and support

---

## 🚀 Roadmap

Future features planned:

- [ ] Web panel for remote administration
- [ ] Advanced analytics and reporting
- [ ] Template system for punishment reasons
- [ ] Warning point system
- [ ] Appeal system
- [ ] More placeholder expansions
- [ ] API for developers

---

<div align="center">

**Made with ❤️ by Eministar**

⭐ **Star this repository if you find it helpful!** ⭐

</div>

