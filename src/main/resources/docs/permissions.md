# StarBans Permissions

## Core

- `starbans.admin`: bypass / full access to all StarBans features
- `starbans.notify`: receive update notifications

## Commands

- `starbans.command.gui`: use `/starbans` and `/sb`
- `starbans.command.reload`: reload config and language files
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
