package dev.eministar.starbans.model;

public enum CaseType {
    BAN,
    IP_BAN,
    MUTE,
    KICK,
    WARN,
    NOTE,
    ALT_FLAG,
    IP_BLACKLIST,
    WATCHLIST;

    public boolean supportsExpiry() {
        return this == BAN || this == IP_BAN || this == MUTE || this == WARN || this == WATCHLIST;
    }
}
