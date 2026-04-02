package dev.eministar.starbans.model;

import java.util.UUID;

public final class CaseComment {

    private long createdAt;
    private UUID actorUniqueId;
    private String actorName;
    private String message;

    public CaseComment() {
    }

    public CaseComment(long createdAt, UUID actorUniqueId, String actorName, String message) {
        this.createdAt = createdAt;
        this.actorUniqueId = actorUniqueId;
        this.actorName = actorName;
        this.message = message;
    }

    public static CaseComment create(CommandActor actor, String message) {
        return new CaseComment(
                System.currentTimeMillis(),
                actor == null ? null : actor.uniqueId(),
                actor == null ? "SYSTEM" : actor.name(),
                message
        );
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public UUID getActorUniqueId() {
        return actorUniqueId;
    }

    public String getActorName() {
        return actorName;
    }

    public String getMessage() {
        return message;
    }
}
