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
- evidence attachments for links, images, videos and text references
- incident grouping and case linking
- review cases with automatic reminders
- quarantine mode for suspicious players
- extended case search and filtering
- import pipeline for StarBans JSON, LiteBans SQLite and AdvancedBan SQLite
- risk scoring based on history, watchlists, quarantine and alt links

## GUI and Staff UX

- main moderation GUI
- player browser
- profile/action menu
- case history screens
- report queue GUI
- single-case detail view with workflow metadata
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
- bot runtime libraries are downloaded only when `discord-bot.enabled: true`
- downloaded bot libraries are stored in `plugins/StarBans/libs`

## Data and Operations

- JSON, SQLite and MariaDB storage
- player file export in `txt` and `json`
- HTML support dumps
- moderator audit summaries
- developer feedback relay
- PlaceholderAPI expansion
