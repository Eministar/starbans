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
    WATCHLIST,
    REPORT,
    INCIDENT,
    QUARANTINE,
    REVIEW;

    public boolean supportsExpiry() {
        return this == BAN
                || this == IP_BAN
                || this == MUTE
                || this == WARN
                || this == WATCHLIST
                || this == QUARANTINE
                || this == REVIEW;
    }
}
