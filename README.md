<div align="center">

![StarBans Banner](https://capsule-render.vercel.app/api?type=waving&height=220&color=0:7C3AED,50:2563EB,100:06B6D4&text=StarBans%202.0.1&fontColor=ffffff&fontAlignY=38&desc=Advanced%20Moderation%20%7C%20Case%20Management%20for%20Minecraft&descAlignY=58)

# StarBans

### 🚀 Advanced moderation + case workflows for modern Minecraft servers

![Minecraft](https://img.shields.io/badge/Minecraft-1.21%2B-22C55E?style=for-the-badge)
![Platform](https://img.shields.io/badge/Platform-Spigot%20%7C%20Paper-F59E0B?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-21%2B-EA580C?style=for-the-badge)
![Storage](https://img.shields.io/badge/Storage-JSON%20%7C%20SQLite%20%7C%20MariaDB-2563EB?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-10B981?style=for-the-badge)

</div>

StarBans combines classic punishments with modern moderation workflows: reports, queue handling, appeals, evidence, incidents, reviews, quarantine, exports, audits, and optional Discord integrations.

## ✨ Feature Overview

### 🛡️ Core Moderation

- Permanent and temporary player bans
- Permanent and temporary IP bans
- Permanent and temporary mutes
- Warnings with points and configurable escalation
- Kicks with case logging
- Internal and public notes
- Watchlists
- IP blacklist entries
- Alt-account flags and join-time alt heuristics

### 🔁 Workflow Systems

- Player report intake via `/report`
- Staff report queue with claim and priority workflow
- Appeal states with deadlines and internal notes
- Evidence attachments for links, images, videos, and text references
- Incident grouping and case linking
- Review cases with automatic reminders
- Quarantine mode for suspicious players
- Extended case search and filtering
- Import pipeline for StarBans JSON, LiteBans SQLite, and AdvancedBan SQLite
- Risk scoring based on history, watchlists, quarantine, and alt links

### 🧭 GUI and Staff UX

- Main moderation GUI
- Player browser
- Profile/action menu
- Case history screens
- Report queue GUI
- Single-case detail view with workflow metadata
- Related-account browser
- Recent activity log

### 🌐 Network and Alerts

- Per-server rule profiles
- Optional command overrides
- Staff join alerts
- Review-due alerts
- Optional VPN/proxy detection
- Optional backend-to-proxy synchronization for network punishments

### 🤖 Discord Integrations

- Rich webhook logging via external `discord-webhooks.yml`
- Optional Discord bot with slash commands for case lookup, reports, appeals, and unban requests
- Bot runtime libraries are downloaded only when `discord-bot.enabled: true`
- Downloaded bot libraries are stored in `plugins/StarBans/libs/`
- By default the Discord runtime resolves the latest published JDA release and the matching dependency tree automatically
- `discord-bot.download.jda-version` can pin an exact version instead of `RELEASE`/`LATEST`

### 💾 Data and Operations

- JSON, SQLite, and MariaDB storage
- Player file export in `txt` and `json`
- HTML support dumps
- Moderator audit summaries
- Developer feedback relay
- PlaceholderAPI expansion

## 📦 Installation

### Requirements

- Minecraft `1.21+`
- Spigot or Paper
- Java `21+`

### Steps

1. Build or download `StarBans-2.0.1.jar`.
2. Place it in your server `plugins/` directory.
3. Start the server once.
4. Configure `plugins/StarBans/config.yml`.
5. Optional: install PlaceholderAPI.
6. Optional: configure `plugins/StarBans/discord-webhooks.yml`.
7. Optional: enable the Discord bot in `config.yml`.

## ⚙️ Commands

## Main Command

- `/starbans help`
- `/starbans reload`
- `/starbans gui [player]`
- `/starbans check <player>`
- `/starbans cases <player>`
- `/starbans case <id>`
- `/starbans case tags <id> <add|remove|set|clear> [tag,tag2]`
- `/starbans notes <player>`
- `/starbans note <player> <label> [internal|public] <text>`
- `/starbans ban <player> [reason]`
- `/starbans tempban <player> <duration> [reason]`
- `/starbans unban <player|ip> [reason]`
- `/starbans ipban <player|ip> [reason]`
- `/starbans tempipban <player|ip> <duration> [reason]`
- `/starbans unipban <player|ip> [reason]`
- `/starbans mute <player> [reason]`
- `/starbans tempmute <player> <duration> [reason]`
- `/starbans unmute <player> [reason]`
- `/starbans kick <player> [reason]`
- `/starbans warn <player> [points] [duration] [reason]`
- `/starbans watchlist <add|remove|list> <player> [duration] [reason]`
- `/starbans template <list|info|apply> ...`
- `/starbans audit <staff> [page]`
- `/starbans undo <caseId> [note]`
- `/starbans reopen <caseId> [note]`
- `/starbans export <player> <txt|json>`
- `/starbans webhooktest <action>`
- `/starbans dump`
- `/starbans setup <webhooks|general> ...`
- `/starbans feedback <message>`
- `/starbans alt mark <label> <player1> <player2> [note]`
- `/starbans alt list <player>`
- `/starbans alt clear <caseId> [reason]`
- `/starbans ipblacklist <add|remove> <ip> [reason]`

## Workflow Commands

- `/report <player> [priority] <reason>`
- `/starbans queue`
- `/starbans queue list`
- `/starbans queue claim <caseId>`
- `/starbans queue priority <caseId> <low|normal|high|critical>`
- `/starbans appeal <open|reviewing|accept|deny|note> <caseId> [duration] [note]`
- `/starbans evidence <caseId> <link|image|video|text> <value> [note]`
- `/starbans incident create <incidentId> [priority] <description>`
- `/starbans incident link <caseId> <incidentId>`
- `/starbans review list`
- `/starbans review create <player> [duration] <reason>`
- `/starbans review done <caseId> [next-duration] [note]`
- `/starbans quarantine add <player> [duration] [reason]`
- `/starbans quarantine remove <player> [reason]`
- `/starbans search <type|*> <status|*> <actor|*> <tag|*> <server-profile|*> [days]`
- `/starbans import <starbans_json|litebans_sqlite|advancedban_sqlite> <path>`

## Safe Direct Commands

- `/sban`
- `/stempban`
- `/sunban <player|ip> [reason]`
- `/sipban`
- `/stempipban`
- `/sunipban <player|ip> [reason]`
- `/smute`
- `/stempmute`
- `/sunmute`
- `/skick`
- `/snote`
- `/snotes`
- `/scases`
- `/salt`
- `/sipblacklist`
- `/report`

### Duration Format

- `30m`
- `1h`
- `12h`
- `1d`
- `7d`
- `30d`
- Combined values like `1d12h`
- `perm` / `permanent` where a permanent state is valid

## 🧩 Permissions

Core permissions:

- `starbans.admin`
- `starbans.notify`
- `starbans.alerts.receive`

Most permissions default to `op`, while `starbans.command.report` defaults to `true`.

For full permission nodes, see: `src/main/resources/docs/permissions.md`

## 📚 Documentation Hub

Repository docs (recommended for setup and administration):

- `src/main/resources/docs/commands.md`
- `src/main/resources/docs/features.md`
- `src/main/resources/docs/permissions.md`
- `src/main/resources/docs/placeholders.md`
- `src/main/resources/docs/database.md`

Runtime docs (generated inside your server):

- `plugins/StarBans/docs/commands.md`
- `plugins/StarBans/docs/features.md`
- `plugins/StarBans/docs/permissions.md`
- `plugins/StarBans/docs/placeholders.md`
- `plugins/StarBans/docs/database.md`

Important config files:

- `plugins/StarBans/config.yml`
- `plugins/StarBans/lang-en.yml`
- `plugins/StarBans/lang-de.yml`
- `plugins/StarBans/discord-webhooks.yml`

## 🧠 Placeholders

Identifier: `%starbans_<placeholder>%`

Includes player state, counters, latest-case values, and global moderation stats.

Full list: `src/main/resources/docs/placeholders.md`

## 🔌 Networking

StarBans can run on backend Paper/Spigot servers in a proxied setup.

- `network.proxy-support` is informational for backend awareness
- `network.velocity-bridge.enabled` expects MariaDB and a separate proxy-side component
- Network-wide ban/IP sync is triggered from backend moderation actions

## 🛠️ Development

- Version: `2.0.1`
- Build tool: Maven
- Java release target: `21`
- Main class: `dev.eministar.starbans.StarBans`

## 📄 License

This project is licensed under the MIT License.
See `LICENSE` for full text.
