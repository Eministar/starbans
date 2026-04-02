package dev.eministar.starbans.model;

import java.util.UUID;

public final class CaseEvidence {

    private String id;
    private EvidenceType type;
    private String value;
    private String note;
    private long addedAt;
    private UUID actorUniqueId;
    private String actorName;

    public CaseEvidence() {
    }

    public CaseEvidence(String id,
                        EvidenceType type,
                        String value,
                        String note,
                        long addedAt,
                        UUID actorUniqueId,
                        String actorName) {
        this.id = id;
        this.type = type == null ? EvidenceType.LINK : type;
        this.value = value;
        this.note = note;
        this.addedAt = addedAt;
        this.actorUniqueId = actorUniqueId;
        this.actorName = actorName;
    }

    public static CaseEvidence create(String id, EvidenceType type, String value, String note, CommandActor actor) {
        return new CaseEvidence(
                id,
                type,
                value,
                note,
                System.currentTimeMillis(),
                actor == null ? null : actor.uniqueId(),
                actor == null ? "SYSTEM" : actor.name()
        );
    }

    public String getId() {
        return id;
    }

    public EvidenceType getType() {
        return type == null ? EvidenceType.LINK : type;
    }

    public String getValue() {
        return value;
    }

    public String getNote() {
        return note;
    }

    public long getAddedAt() {
        return addedAt;
    }

    public UUID getActorUniqueId() {
        return actorUniqueId;
    }

    public String getActorName() {
        return actorName;
    }
}
