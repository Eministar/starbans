# StarBans Feature Overview

## Core Moderation

- permanent and temporary player bans
- permanent and temporary IP bans
- permanent and temporary mutes
- warnings with points and configurable escalation
- kicks with case logging
- internal and public notes
- watchlists
- IP blacklist entries
- alt-account flags and join-time alt heuristics

## Workflow Systems

- player report intake via `/report`
- staff report queue with claim and priority workflow
- appeal states with deadlines and internal notes
- unified workflow center for appeals, unban requests, reports, reviews and quarantines
- Discord and in-game requests merge into the same workflow queues
- evidence attachments for links, images, videos and text references
- incident grouping and case linking
- review cases with automatic reminders
- quarantine mode for suspicious players
- extended case search and filtering
- import pipeline for StarBans JSON, LiteBans SQLite and AdvancedBan SQLite
- risk scoring based on history, watchlists, quarantine and alt links

## GUI and Staff UX

- main moderation GUI
- workflow center GUI with dedicated queues for appeals, unban requests, reports, reviews and quarantines
- player browser
- profile/action menu
- case history screens
- queue GUIs with claim and priority shortcuts
- single-case detail view with workflow metadata plus claim / accept / deny buttons
- related-account browser
- recent activity log

## Network and Alerts

- per-server rule profiles
- optional command overrides
- staff join alerts
- review-due alerts
- optional VPN/proxy detection
- optional backend to proxy synchronization for network punishments

## Discord Integrations

- rich webhook logging via external `discord-webhooks.yml`
- optional Discord bot with slash commands for case lookup, reports, appeals and unban requests
- Discord appeal / unban panels, modal intake, staff buttons and decision DMs
- bot runtime libraries are downloaded only when `discord-bot.enabled: true`
- downloaded bot libraries are stored in `plugins/StarBans/libs`
- the Discord runtime resolves the latest published JDA release and its matching runtime dependency tree automatically by default
- `discord-bot.download.jda-version` can pin an exact JDA version when automatic updates are not wanted

## Data and Operations

- JSON, SQLite and MariaDB storage
- player file export in `txt` and `json`
- HTML support dumps
- moderator audit summaries
- developer feedback relay
- PlaceholderAPI expansion
