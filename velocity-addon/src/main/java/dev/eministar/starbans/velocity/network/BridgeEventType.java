package dev.eministar.starbans.velocity.network;

public enum BridgeEventType {
    SYNC_NOW,
    SNAPSHOT_REFRESH,
    BAN_PLAYER,
    UNBAN_PLAYER,
    KICK_PLAYER,
    BAN_IP,
    UNBAN_IP,
    IP_BLACKLIST,
    IP_UNBLACKLIST
}
