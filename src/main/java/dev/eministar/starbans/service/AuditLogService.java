package dev.eministar.starbans.service;

import dev.eministar.starbans.database.ModerationStorage;
import dev.eministar.starbans.model.CaseRecord;
import dev.eministar.starbans.model.CaseType;

import java.util.List;

public final class AuditLogService {

    private final ModerationStorage storage;

    public AuditLogService(ModerationStorage storage) {
        this.storage = storage;
    }

    public AuditSnapshot getSnapshot(String actorName, int limit, int page) throws Exception {
        int safeLimit = Math.max(1, limit);
        int safeOffset = Math.max(0, page) * safeLimit;
        return new AuditSnapshot(
                actorName,
                storage.countCasesByActor(actorName),
                storage.countCasesByActorAndType(actorName, CaseType.BAN),
                storage.countCasesByActorAndType(actorName, CaseType.MUTE),
                storage.countCasesByActorAndType(actorName, CaseType.WARN),
                storage.countCasesByActorAndType(actorName, CaseType.KICK),
                storage.countCasesByActorAndType(actorName, CaseType.NOTE),
                storage.countCasesByActorAndType(actorName, CaseType.WATCHLIST),
                storage.countStatusChangesByActor(actorName),
                storage.getCasesByActor(actorName, safeLimit, safeOffset),
                storage.getCasesByStatusActor(actorName, safeLimit, safeOffset)
        );
    }

    public record AuditSnapshot(String actorName,
                                int totalActions,
                                int bans,
                                int mutes,
                                int warns,
                                int kicks,
                                int notes,
                                int watchlists,
                                int statusChanges,
                                List<CaseRecord> recentActions,
                                List<CaseRecord> recentStatusChanges) {
    }
}
