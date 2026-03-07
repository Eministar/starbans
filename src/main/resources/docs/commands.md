# StarBans Commands

Main command:
- `/starbans help`
- `/starbans reload`
- `/starbans gui [player]`
- `/starbans check <player>`
- `/starbans cases <player>`
- `/starbans case <id>`
- `/starbans notes <player>`
- `/starbans note <player> <label> <text>`
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
- `/starbans alt mark <label> <player1> <player2> [note]`
- `/starbans alt list <player>`
- `/starbans alt clear <caseId> [reason]`
- `/starbans ipblacklist <add|remove> <ip> [reason]`

Safe direct commands:
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

Durations:
- `30m`
- `1h`
- `12h`
- `1d`
- `7d`
- `30d`
- combined values like `1d12h` are supported
- `perm` / `permanent` can be used where permanent values are accepted
