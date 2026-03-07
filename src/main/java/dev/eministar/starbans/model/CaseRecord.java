package dev.eministar.starbans.model;

import java.util.UUID;

public final class CaseRecord {

    private long id;
    private CaseType type;
    private String label;
    private UUID targetPlayerUniqueId;
    private String targetPlayerName;
    private String targetIp;
    private UUID relatedPlayerUniqueId;
    private String relatedPlayerName;
    private UUID actorUniqueId;
    private String actorName;
    private String reason;
    private String source;
    private long createdAt;
    private Long expiresAt;
    private CaseStatus status;
    private Long statusChangedAt;
    private UUID statusActorUniqueId;
    private String statusActorName;
    private String statusNote;

    public CaseRecord() {
    }

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

    public static CaseRecord create(CaseType type,
                                    String label,
                                    PlayerIdentity targetPlayer,
                                    String targetIp,
                                    PlayerIdentity relatedPlayer,
                                    CommandActor actor,
                                    String reason,
                                    String source,
                                    Long expiresAt) {
        return new CaseRecord(
                0L,
                type,
                label,
                targetPlayer == null ? null : targetPlayer.uniqueId(),
                targetPlayer == null ? null : targetPlayer.name(),
                targetIp,
                relatedPlayer == null ? null : relatedPlayer.uniqueId(),
                relatedPlayer == null ? null : relatedPlayer.name(),
                actor == null ? null : actor.uniqueId(),
                actor == null ? "SYSTEM" : actor.name(),
                reason,
                source,
                System.currentTimeMillis(),
                expiresAt,
                type == CaseType.KICK ? CaseStatus.RESOLVED : CaseStatus.ACTIVE,
                type == CaseType.KICK ? System.currentTimeMillis() : null,
                type == CaseType.KICK && actor != null ? actor.uniqueId() : null,
                type == CaseType.KICK && actor != null ? actor.name() : null,
                null
        );
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

    public boolean isVisibleFor(UUID playerUniqueId) {
        if (playerUniqueId == null) {
            return false;
        }
        return playerUniqueId.equals(targetPlayerUniqueId) || playerUniqueId.equals(relatedPlayerUniqueId);
    }

    public boolean isActive(long now) {
        return status == CaseStatus.ACTIVE && (expiresAt == null || expiresAt > now);
    }

    public boolean isExpired(long now) {
        return status == CaseStatus.ACTIVE && expiresAt != null && expiresAt <= now;
    }

    public boolean isTemporary() {
        return expiresAt != null;
    }

    public CaseRecord withId(long newId) {
        return new CaseRecord(
                newId,
                type,
                label,
                targetPlayerUniqueId,
                targetPlayerName,
                targetIp,
                relatedPlayerUniqueId,
                relatedPlayerName,
                actorUniqueId,
                actorName,
                reason,
                source,
                createdAt,
                expiresAt,
                status,
                statusChangedAt,
                statusActorUniqueId,
                statusActorName,
                statusNote
        );
    }

    public CaseRecord withStatus(CaseStatus newStatus,
                                 long changedAt,
                                 UUID changedByUniqueId,
                                 String changedByName,
                                 String note) {
        return new CaseRecord(
                id,
                type,
                label,
                targetPlayerUniqueId,
                targetPlayerName,
                targetIp,
                relatedPlayerUniqueId,
                relatedPlayerName,
                actorUniqueId,
                actorName,
                reason,
                source,
                createdAt,
                expiresAt,
                newStatus,
                changedAt,
                changedByUniqueId,
                changedByName,
                note
        );
    }
}
