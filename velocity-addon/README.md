# StarBans Velocity Addon

This addon runs on Velocity and reads the same moderation storage as the backend `StarBans` plugin.

## Purpose

- block banned players before they reach backend servers
- block active IP bans on the proxy
- block blacklisted IPs on the proxy
- keep shared player profiles updated with proxy login IPs

## Recommended setup

1. install the normal `StarBans` plugin on your Paper/Spigot backend
2. install `StarBans-VelocityAddon` on your Velocity proxy
3. point both plugins to the same MariaDB database

## Storage notes

- `MARIADB`: recommended for proxy + backend
- `SQLITE`: possible on one machine, but shared-file locking can still become a problem
- `JSON`: not recommended for proxy setups

## Commands

- `/starbansvelocity reload`
- `/starbansvelocity check <player|uuid|ip>`

## Permissions

- `starbansvelocity.command.reload`
- `starbansvelocity.command.check`
