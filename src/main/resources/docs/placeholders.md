# StarBans Placeholders

Identifier: `%starbans_<placeholder>%`

## Player Placeholders

- `%starbans_status%`: current moderation status
- `%starbans_is_banned%`: `true` if the player has an active ban
- `%starbans_ban_reason%`: active ban reason
- `%starbans_ban_remaining%`: remaining ban duration
- `%starbans_is_muted%`: `true` if the player has an active mute
- `%starbans_mute_reason%`: active mute reason
- `%starbans_mute_remaining%`: remaining mute duration
- `%starbans_is_watchlisted%`: `true` if the player is on the watchlist
- `%starbans_watchlist_reason%`: active watchlist reason
- `%starbans_last_ip%`: last known IP
- `%starbans_case_count%`: visible case count
- `%starbans_note_count%`: note count
- `%starbans_alt_count%`: active alt-flag count
- `%starbans_warn_count%`: active warn count
- `%starbans_warning_points%`: active warning points
- `%starbans_last_case_type%`: latest case type
- `%starbans_last_case_reason%`: latest case reason

## Global Placeholders

- `%starbans_active_bans%`: active player bans
- `%starbans_active_ip_bans%`: active IP bans
- `%starbans_active_mutes%`: active mutes
- `%starbans_active_warns%`: active warns
- `%starbans_active_watchlists%`: active watchlists
- `%starbans_total_cases%`: total stored cases

## Notes

- Placeholder values are cached briefly to reduce database load.
- Workflow-specific data like report queue stats, appeal states or evidence counts are not exposed as PlaceholderAPI values yet.
