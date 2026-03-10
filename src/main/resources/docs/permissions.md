# StarBans Permissions

## Core

- `starbans.admin`: bypass / full access to all StarBans features
- `starbans.notify`: receive update notifications
- `starbans.alerts.receive`: receive join-intelligence and VPN/security alerts

## Commands

- `starbans.command.base`: use `/starbans` and `/sb`
- `starbans.command.gui`: open the GUI via `/starbans` or `/sb`
- `starbans.command.reload`: reload config, `discord-webhooks.yml` and language files
- `starbans.command.check`: use `/bancheck`
- `starbans.command.cases`: use `/scases`, `/banhistory`
- `starbans.command.resolve`: resolve active cases from GUI or future commands
- `starbans.command.note`: create notes
- `starbans.command.notes`: view note history
- `starbans.command.ban`: permanent bans
- `starbans.command.tempban`: temporary bans
- `starbans.command.unban`: remove bans
- `starbans.command.ipban`: IP bans
- `starbans.command.unipban`: remove IP bans
- `starbans.command.mute`: permanent mutes
- `starbans.command.tempmute`: temporary mutes
- `starbans.command.unmute`: remove mutes
- `starbans.command.kick`: kicks
- `starbans.command.warn`: warning system with points
- `starbans.command.watchlist`: watchlist management
- `starbans.command.template`: template browsing / application
- `starbans.command.webhooktest`: webhook test dispatch
- `starbans.command.audit`: moderator audit summaries
- `starbans.command.undo`: revert active cases
- `starbans.command.reopen`: reopen inactive cases
- `starbans.command.export`: export player case history
- `starbans.command.tags`: edit case tags
- `starbans.command.alt`: alt-flag management
- `starbans.command.ipblacklist`: IP blacklist management

## GUI

- `starbans.gui.open`: open the main GUI
- `starbans.gui.browser`: browse known players
- `starbans.gui.profile`: open a player profile
- `starbans.gui.activity`: open the global activity log
- `starbans.gui.history`: open player case history GUIs
- `starbans.gui.case.view`: open a single case detail GUI
- `starbans.gui.case.resolve`: resolve a case from the GUI
- `starbans.gui.notes.view`: open note GUIs
- `starbans.gui.notes.create`: create notes via GUI prompt
- `starbans.gui.related`: view related-account GUIs
- `starbans.gui.alt.mark`: create alt flags from the related GUI

## GUI Punishments

- `starbans.gui.punish.ban`: permanent ban button
- `starbans.gui.punish.tempban`: temp-ban preset buttons
- `starbans.gui.punish.unban`: unban button
- `starbans.gui.punish.mute`: permanent mute button
- `starbans.gui.punish.tempmute`: temp-mute preset buttons
- `starbans.gui.punish.unmute`: unmute button

## Note

- GUI actions still also require the related command permission.
- Example: for GUI temp-bans you should grant both `starbans.command.tempban` and `starbans.gui.punish.tempban`.
- Join alerts use `starbans.alerts.receive`.
