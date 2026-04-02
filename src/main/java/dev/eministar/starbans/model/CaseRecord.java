package dev.eministar.starbans.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private String category;
    private String templateKey;
    private List<String> tags;
    private int points;
    private CaseVisibility visibility;
    private Long referenceCaseId;
    private String serverProfileId;
    private String incidentId;
    private CasePriority priority;
    private UUID claimActorUniqueId;
    private String claimActorName;
    private Long claimChangedAt;
    private AppealStatus appealStatus;
    private Long appealDeadlineAt;
    private Long appealChangedAt;
    private UUID appealActorUniqueId;
    private String appealActorName;
    private List<CaseComment> appealNotes;
    private Long nextReviewAt;
    private Long lastReviewedAt;
    private String reviewReason;
    private List<CaseEvidence> evidence;
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
        this(
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
                null,
                null,
                List.of(),
                0,
                CaseVisibility.INTERNAL,
                null,
                null,
                null,
                CasePriority.NORMAL,
                null,
                null,
                null,
                AppealStatus.NONE,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                List.of(),
                createdAt,
                expiresAt,
                status,
                statusChangedAt,
                statusActorUniqueId,
                statusActorName,
                statusNote
        );
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
                      String category,
                      String templateKey,
                      List<String> tags,
                      int points,
                      CaseVisibility visibility,
                      Long referenceCaseId,
                      String serverProfileId,
                      String incidentId,
                      CasePriority priority,
                      UUID claimActorUniqueId,
                      String claimActorName,
                      Long claimChangedAt,
                      AppealStatus appealStatus,
                      Long appealDeadlineAt,
                      Long appealChangedAt,
                      UUID appealActorUniqueId,
                      String appealActorName,
                      List<CaseComment> appealNotes,
                      Long nextReviewAt,
                      Long lastReviewedAt,
                      String reviewReason,
                      List<CaseEvidence> evidence,
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
        this.category = category;
        this.templateKey = templateKey;
        this.tags = normalizeTags(tags);
        this.points = Math.max(0, points);
        this.visibility = visibility == null ? CaseVisibility.INTERNAL : visibility;
        this.referenceCaseId = referenceCaseId;
        this.serverProfileId = normalizeText(serverProfileId);
        this.incidentId = normalizeText(incidentId);
        this.priority = priority == null ? CasePriority.NORMAL : priority;
        this.claimActorUniqueId = claimActorUniqueId;
        this.claimActorName = claimActorName;
        this.claimChangedAt = claimChangedAt;
        this.appealStatus = appealStatus == null ? AppealStatus.NONE : appealStatus;
        this.appealDeadlineAt = appealDeadlineAt;
        this.appealChangedAt = appealChangedAt;
        this.appealActorUniqueId = appealActorUniqueId;
        this.appealActorName = appealActorName;
        this.appealNotes = normalizeComments(appealNotes);
        this.nextReviewAt = nextReviewAt;
        this.lastReviewedAt = lastReviewedAt;
        this.reviewReason = reviewReason;
        this.evidence = normalizeEvidence(evidence);
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = status;
        this.statusChangedAt = statusChangedAt;
        this.statusActorUniqueId = statusActorUniqueId;
        this.statusActorName = statusActorName;
        this.statusNote = statusNote;
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
                      String category,
                      String templateKey,
                      List<String> tags,
                      int points,
                      CaseVisibility visibility,
                      Long referenceCaseId,
                      long createdAt,
                      Long expiresAt,
                      CaseStatus status,
                      Long statusChangedAt,
                      UUID statusActorUniqueId,
                      String statusActorName,
                      String statusNote) {
        this(
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
                category,
                templateKey,
                tags,
                points,
                visibility,
                referenceCaseId,
                null,
                null,
                CasePriority.NORMAL,
                null,
                null,
                null,
                AppealStatus.NONE,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                List.of(),
                createdAt,
                expiresAt,
                status,
                statusChangedAt,
                statusActorUniqueId,
                statusActorName,
                statusNote
        );
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
        return create(
                type,
                label,
                targetPlayer,
                targetIp,
                relatedPlayer,
                actor,
                reason,
                source,
                expiresAt,
                null,
                null,
                List.of(),
                0,
                CaseVisibility.INTERNAL,
                null,
                null,
                null,
                CasePriority.NORMAL,
                null,
                null
        );
    }

    public static CaseRecord create(CaseType type,
                                    String label,
                                    PlayerIdentity targetPlayer,
                                    String targetIp,
                                    PlayerIdentity relatedPlayer,
                                    CommandActor actor,
                                    String reason,
                                    String source,
                                    Long expiresAt,
                                    String category,
                                    String templateKey,
                                    List<String> tags,
                                    int points,
                                    CaseVisibility visibility,
                                    Long referenceCaseId) {
        return create(
                type,
                label,
                targetPlayer,
                targetIp,
                relatedPlayer,
                actor,
                reason,
                source,
                expiresAt,
                category,
                templateKey,
                tags,
                points,
                visibility,
                referenceCaseId,
                null,
                null,
                CasePriority.NORMAL,
                null,
                null
        );
    }

    public static CaseRecord create(CaseType type,
                                    String label,
                                    PlayerIdentity targetPlayer,
                                    String targetIp,
                                    PlayerIdentity relatedPlayer,
                                    CommandActor actor,
                                    String reason,
                                    String source,
                                    Long expiresAt,
                                    String category,
                                    String templateKey,
                                    List<String> tags,
                                    int points,
                                    CaseVisibility visibility,
                                    Long referenceCaseId,
                                    String serverProfileId,
                                    String incidentId,
                                    CasePriority priority,
                                    Long nextReviewAt,
                                    String reviewReason) {
        boolean kick = type == CaseType.KICK;
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
                category,
                templateKey,
                tags,
                points,
                visibility,
                referenceCaseId,
                serverProfileId,
                incidentId,
                priority,
                null,
                null,
                null,
                AppealStatus.NONE,
                null,
                null,
                null,
                null,
                List.of(),
                nextReviewAt,
                null,
                reviewReason,
                List.of(),
                System.currentTimeMillis(),
                expiresAt,
                kick ? CaseStatus.RESOLVED : CaseStatus.ACTIVE,
                kick ? System.currentTimeMillis() : null,
                kick && actor != null ? actor.uniqueId() : null,
                kick && actor != null ? actor.name() : null,
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

    public String getCategory() {
        return category;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public List<String> getTags() {
        return tags == null ? List.of() : List.copyOf(tags);
    }

    public String getTagsDisplay() {
        if (getTags().isEmpty()) {
            return "";
        }
        return String.join(", ", getTags());
    }

    public int getPoints() {
        return points;
    }

    public CaseVisibility getVisibility() {
        return visibility == null ? CaseVisibility.INTERNAL : visibility;
    }

    public Long getReferenceCaseId() {
        return referenceCaseId;
    }

    public String getServerProfileId() {
        return serverProfileId;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public CasePriority getPriority() {
        return priority == null ? CasePriority.NORMAL : priority;
    }

    public UUID getClaimActorUniqueId() {
        return claimActorUniqueId;
    }

    public String getClaimActorName() {
        return claimActorName;
    }

    public Long getClaimChangedAt() {
        return claimChangedAt;
    }

    public AppealStatus getAppealStatus() {
        return appealStatus == null ? AppealStatus.NONE : appealStatus;
    }

    public Long getAppealDeadlineAt() {
        return appealDeadlineAt;
    }

    public Long getAppealChangedAt() {
        return appealChangedAt;
    }

    public UUID getAppealActorUniqueId() {
        return appealActorUniqueId;
    }

    public String getAppealActorName() {
        return appealActorName;
    }

    public List<CaseComment> getAppealNotes() {
        return appealNotes == null ? List.of() : List.copyOf(appealNotes);
    }

    public Long getNextReviewAt() {
        return nextReviewAt;
    }

    public Long getLastReviewedAt() {
        return lastReviewedAt;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public List<CaseEvidence> getEvidence() {
        return evidence == null ? List.of() : List.copyOf(evidence);
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
                category,
                templateKey,
                getTags(),
                points,
                getVisibility(),
                referenceCaseId,
                serverProfileId,
                incidentId,
                getPriority(),
                claimActorUniqueId,
                claimActorName,
                claimChangedAt,
                getAppealStatus(),
                appealDeadlineAt,
                appealChangedAt,
                appealActorUniqueId,
                appealActorName,
                getAppealNotes(),
                nextReviewAt,
                lastReviewedAt,
                reviewReason,
                getEvidence(),
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
                category,
                templateKey,
                getTags(),
                points,
                getVisibility(),
                referenceCaseId,
                serverProfileId,
                incidentId,
                getPriority(),
                claimActorUniqueId,
                claimActorName,
                claimChangedAt,
                getAppealStatus(),
                appealDeadlineAt,
                appealChangedAt,
                appealActorUniqueId,
                appealActorName,
                getAppealNotes(),
                nextReviewAt,
                lastReviewedAt,
                reviewReason,
                getEvidence(),
                createdAt,
                expiresAt,
                newStatus,
                changedAt,
                changedByUniqueId,
                changedByName,
                note
        );
    }

    public CaseRecord withMetadata(String newCategory,
                                   String newTemplateKey,
                                   List<String> newTags,
                                   int newPoints,
                                   CaseVisibility newVisibility,
                                   Long newReferenceCaseId) {
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
                newCategory,
                newTemplateKey,
                newTags,
                newPoints,
                newVisibility,
                newReferenceCaseId,
                serverProfileId,
                incidentId,
                getPriority(),
                claimActorUniqueId,
                claimActorName,
                claimChangedAt,
                getAppealStatus(),
                appealDeadlineAt,
                appealChangedAt,
                appealActorUniqueId,
                appealActorName,
                getAppealNotes(),
                nextReviewAt,
                lastReviewedAt,
                reviewReason,
                getEvidence(),
                createdAt,
                expiresAt,
                status,
                statusChangedAt,
                statusActorUniqueId,
                statusActorName,
                statusNote
        );
    }

    public CaseRecord withWorkflow(String newServerProfileId,
                                   String newIncidentId,
                                   CasePriority newPriority,
                                   UUID newClaimActorUniqueId,
                                   String newClaimActorName,
                                   Long newClaimChangedAt,
                                   AppealStatus newAppealStatus,
                                   Long newAppealDeadlineAt,
                                   Long newAppealChangedAt,
                                   UUID newAppealActorUniqueId,
                                   String newAppealActorName,
                                   List<CaseComment> newAppealNotes,
                                   Long newNextReviewAt,
                                   Long newLastReviewedAt,
                                   String newReviewReason,
                                   List<CaseEvidence> newEvidence) {
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
                category,
                templateKey,
                getTags(),
                points,
                getVisibility(),
                referenceCaseId,
                newServerProfileId,
                newIncidentId,
                newPriority,
                newClaimActorUniqueId,
                newClaimActorName,
                newClaimChangedAt,
                newAppealStatus,
                newAppealDeadlineAt,
                newAppealChangedAt,
                newAppealActorUniqueId,
                newAppealActorName,
                newAppealNotes,
                newNextReviewAt,
                newLastReviewedAt,
                newReviewReason,
                newEvidence,
                createdAt,
                expiresAt,
                status,
                statusChangedAt,
                statusActorUniqueId,
                statusActorName,
                statusNote
        );
    }

    private static List<String> normalizeTags(List<String> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }

        List<String> output = new ArrayList<>();
        for (String tag : input) {
            if (tag == null || tag.isBlank()) {
                continue;
            }

            String normalized = tag.trim().toLowerCase(Locale.ROOT);
            if (!output.contains(normalized)) {
                output.add(normalized);
            }
        }
        return List.copyOf(output);
    }

    private static List<CaseComment> normalizeComments(List<CaseComment> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }

        List<CaseComment> output = new ArrayList<>();
        for (CaseComment entry : input) {
            if (entry != null && entry.getMessage() != null && !entry.getMessage().isBlank()) {
                output.add(entry);
            }
        }
        return List.copyOf(output);
    }

    private static List<CaseEvidence> normalizeEvidence(List<CaseEvidence> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }

        List<CaseEvidence> output = new ArrayList<>();
        for (CaseEvidence entry : input) {
            if (entry != null && entry.getValue() != null && !entry.getValue().isBlank()) {
                output.add(entry);
            }
        }
        return List.copyOf(output);
    }

    private static String normalizeText(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return input.trim();
    }
}
