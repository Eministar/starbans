# StarBans Commands

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

## Duration Format

- `30m`
- `1h`
- `12h`
- `1d`
- `7d`
- `30d`
- combined values like `1d12h`
- `perm` / `permanent` where a permanent state is valid

## Notes

- `queue` opens the report queue GUI for players and prints a console list for console senders.
- `report` supports optional priorities: `low`, `normal`, `high`, `critical`.
- `appeal`, `evidence`, `incident`, `review`, `quarantine`, `search` and `import` are staff workflow commands and are not exposed as short aliases.
- If the optional Discord bot is enabled, StarBans also registers slash commands for case lookup, reports, appeals and unban requests.
