# StarBans Database Guide

Supported backends:
- `SQLITE`
- `MARIADB`
- `JSON`

Recommendation:
- Use `SQLITE` unless you specifically need a remote shared database.
- `JSON` is supported, but only recommended for very small setups.

Created SQL tables:
- `<table-prefix>_cases`
- `<table-prefix>_profiles`

Stored information:
- bans
- IP bans
- mutes
- kicks
- notes
- alt-account flags
- IP blacklist entries
- player profile data with last known IP
