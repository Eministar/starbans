package dev.eministar.starbans.model;

public record CaseSearchFilter(CaseType type,
                               CaseStatus status,
                               String actorName,
                               String targetName,
                               String tag,
                               String category,
                               String serverProfileId,
                               String incidentId,
                               Long createdAfter,
                               Long createdBefore,
                               AppealStatus appealStatus,
                               CasePriority priority,
                               String text) {
}
