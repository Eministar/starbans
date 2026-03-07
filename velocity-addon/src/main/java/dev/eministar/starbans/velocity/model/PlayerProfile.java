package dev.eministar.starbans.velocity.model;

import java.util.UUID;

public final class PlayerProfile {

    private final UUID uniqueId;
    private final String lastName;
    private final String lastIp;
    private final long firstSeen;
    private final long lastSeen;

    public PlayerProfile(UUID uniqueId, String lastName, String lastIp, long firstSeen, long lastSeen) {
        this.uniqueId = uniqueId;
        this.lastName = lastName;
        this.lastIp = lastIp;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public String getLastName() {
        return lastName;
    }

    public String getLastIp() {
        return lastIp;
    }

    public long getFirstSeen() {
        return firstSeen;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public PlayerProfile withSeen(String playerName, String ipAddress, long seenAt) {
        long createdAt = firstSeen == 0L ? seenAt : firstSeen;
        return new PlayerProfile(uniqueId, playerName, ipAddress, createdAt, seenAt);
    }
}
