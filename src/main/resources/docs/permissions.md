# StarBans Permissions

## Core

- `starbans.admin`: full access to all StarBans features
- `starbans.notify`: receive update notifications
- `starbans.alerts.receive`: receive join, security and review alerts

## Commands

- `starbans.command.base`: use `/starbans` and `/sb`
- `starbans.command.gui`: open GUI entry points
- `starbans.command.reload`: reload config, language files, storage bindings and webhook config
- `starbans.command.check`: inspect player moderation state
- `starbans.command.cases`: open or print case history
- `starbans.command.resolve`: reserved for active case resolution flows
- `starbans.command.note`: create notes
- `starbans.command.notes`: view note history
- `starbans.command.ban`: permanent player bans
- `starbans.command.tempban`: temporary player bans
- `starbans.command.unban`: remove bans
- `starbans.command.ipban`: IP bans and temp IP bans
- `starbans.command.unipban`: remove IP bans
- `starbans.command.mute`: permanent mutes
- `starbans.command.tempmute`: temporary mutes
- `starbans.command.unmute`: remove mutes
- `starbans.command.kick`: kicks with case creation
- `starbans.command.warn`: warning system with points
- `starbans.command.watchlist`: watchlist management
- `starbans.command.template`: punishment template browsing and application
- `starbans.command.webhooktest`: send test webhook payloads
- `starbans.command.audit`: moderator audit summaries
- `starbans.command.undo`: revert active cases
- `starbans.command.reopen`: reopen inactive cases
- `starbans.command.export`: export player files
- `starbans.command.dump`: generate support dumps
- `starbans.command.setup`: update supported config values from commands
- `starbans.command.feedback`: send developer feedback
- `starbans.command.tags`: edit case tags
- `starbans.command.alt`: alt-flag management
- `starbans.command.ipblacklist`: IP blacklist management
- `starbans.command.report`: submit player reports
- `starbans.command.queue`: view and manage the report queue
- `starbans.command.appeal`: manage appeals
- `starbans.command.evidence`: attach evidence to cases
- `starbans.command.incident`: create and link incidents
- `starbans.command.review`: manage review cases and due-review listings
- `starbans.command.quarantine`: quarantine and release players
- `starbans.command.search`: search cases with filters
- `starbans.command.import`: import supported external datasets

## GUI

- `starbans.gui.open`: open the main menu
- `starbans.gui.browser`: browse known player profiles
- `starbans.gui.profile`: open player moderation profiles
- `starbans.gui.activity`: open the global activity log
- `starbans.gui.history`: open history screens
- `starbans.gui.case.view`: open case details
- `starbans.gui.case.resolve`: resolve a case from GUI actions
- `starbans.gui.notes.view`: open note GUIs
- `starbans.gui.notes.create`: create notes from GUI prompt flow
- `starbans.gui.related`: open related-account GUIs
- `starbans.gui.alt.mark`: create alt flags from the related GUI

## GUI Punishments

- `starbans.gui.punish.ban`: permanent ban button
- `starbans.gui.punish.tempban`: temp-ban preset buttons
- `starbans.gui.punish.unban`: unban button
- `starbans.gui.punish.mute`: permanent mute button
- `starbans.gui.punish.tempmute`: temp-mute preset buttons
- `starbans.gui.punish.unmute`: unmute button

## Default Expectations

- most permissions default to `op`
- `starbans.command.report` defaults to `true`
- GUI punishment actions also require the related command permission
- review and join alerts use `starbans.alerts.receive`
