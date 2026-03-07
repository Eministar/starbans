package dev.eministar.starbans.model;

public enum CaseType {
    BAN,
    IP_BAN,
    MUTE,
    KICK,
    NOTE,
    ALT_FLAG,
    IP_BLACKLIST;

    public boolean supportsExpiry() {
        return this == BAN || this == IP_BAN || this == MUTE;
    }
}
