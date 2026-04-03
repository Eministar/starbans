# StarBans Database Guide

## Supported Backends

- `SQLITE`
- `MARIADB`
- `JSON`

## Recommendation

- use `SQLITE` for most standalone servers
- use `MARIADB` for shared-network setups and proxy synchronization
- use `JSON` only for very small or temporary setups

## Core Storage Objects

SQL backends create:

- `<table-prefix>_cases`
- `<table-prefix>_profiles`

JSON storage keeps the same logical data in `storage/database.json`.

## Stored Data

- punishments: bans, IP bans, mutes, kicks, warns
- staff data: notes, tags, categories, template metadata
- workflow data: reports, claims, priorities, incidents, appeals, reviews, quarantines
- evidence entries attached to cases
- alt-account links and player relation signals
- player profiles with first seen, last seen and last known IP
- watchlists, IP blacklist entries and risk-related history

## Network Notes

- `network.velocity-bridge.enabled` requires `database.type=MARIADB`
- shared MariaDB storage is the expected setup for multi-backend punishment sync
