# StarBans Velocity Addon

This addon runs on Velocity and reads the same moderation storage as the backend `StarBans` plugin.
It requires `MARIADB` for synchronized network moderation.

## Purpose

- block banned players before they reach backend servers
- block active IP bans on the proxy
- block blacklisted IPs on the proxy
- disconnect players proxy-wide when a backend kick is executed
- keep shared player profiles updated with proxy login IPs
- provide a shared moderation view for proxy checks, including active mutes

## Recommended setup

1. install the normal `StarBans` plugin on your Paper/Spigot backend
2. install `StarBans-VelocityAddon` on your Velocity proxy
3. point every backend plugin instance and the Velocity addon to the same MariaDB database

If you want network-wide mute sync, every backend server still needs the normal `StarBans` plugin, because chat muting is enforced on the backend while the proxy addon handles proxy-wide join and kick enforcement.

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
