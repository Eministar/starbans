package dev.eministar.starbans.velocity.model;

import java.util.UUID;

public final class CaseRecord {

    private final long id;
    private final CaseType type;
    private final String label;
    private final UUID targetPlayerUniqueId;
    private final String targetPlayerName;
    private final String targetIp;
    private final UUID relatedPlayerUniqueId;
    private final String relatedPlayerName;
    private final UUID actorUniqueId;
    private final String actorName;
    private final String reason;
    private final String source;
    private final long createdAt;
    private final Long expiresAt;
    private final CaseStatus status;
    private final Long statusChangedAt;
    private final UUID statusActorUniqueId;
    private final String statusActorName;
    private final String statusNote;

    public CaseRecord(long id,
                      CaseType type,
                      String label,
                      UUID targetPlayerUniqueId,
                      String targetPlayerName,
                      String targetIp,
                      UUID relatedPlayerUniqueId,
                      String relatedPlayerName,
                      UUID actorUniqueId,
                      String actorName,
                      String reason,
                      String source,
                      long createdAt,
                      Long expiresAt,
                      CaseStatus status,
                      Long statusChangedAt,
                      UUID statusActorUniqueId,
                      String statusActorName,
                      String statusNote) {
        this.id = id;
        this.type = type;
        this.label = label;
        this.targetPlayerUniqueId = targetPlayerUniqueId;
        this.targetPlayerName = targetPlayerName;
        this.targetIp = targetIp;
        this.relatedPlayerUniqueId = relatedPlayerUniqueId;
        this.relatedPlayerName = relatedPlayerName;
        this.actorUniqueId = actorUniqueId;
        this.actorName = actorName;
        this.reason = reason;
        this.source = source;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = status;
        this.statusChangedAt = statusChangedAt;
        this.statusActorUniqueId = statusActorUniqueId;
        this.statusActorName = statusActorName;
        this.statusNote = statusNote;
    }

    public long getId() {
        return id;
    }

    public CaseType getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public UUID getTargetPlayerUniqueId() {
        return targetPlayerUniqueId;
    }

    public String getTargetPlayerName() {
        return targetPlayerName;
    }

    public String getTargetIp() {
        return targetIp;
    }

    public UUID getRelatedPlayerUniqueId() {
        return relatedPlayerUniqueId;
    }

    public String getRelatedPlayerName() {
        return relatedPlayerName;
    }

    public UUID getActorUniqueId() {
        return actorUniqueId;
    }

    public String getActorName() {
        return actorName;
    }

    public String getReason() {
        return reason;
    }

    public String getSource() {
        return source;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public CaseStatus getStatus() {
        return status;
    }

    public Long getStatusChangedAt() {
        return statusChangedAt;
    }

    public UUID getStatusActorUniqueId() {
        return statusActorUniqueId;
    }

    public String getStatusActorName() {
        return statusActorName;
    }

    public String getStatusNote() {
        return statusNote;
    }

    public boolean isExpired(long now) {
        return status == CaseStatus.ACTIVE && expiresAt != null && expiresAt <= now;
    }

    public boolean isTemporary() {
        return expiresAt != null;
    }
}
