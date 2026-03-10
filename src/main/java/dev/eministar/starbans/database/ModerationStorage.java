package dev.eministar.starbans.database;

import dev.eministar.starbans.model.CaseRecord;
import dev.eministar.starbans.model.CaseStatus;
import dev.eministar.starbans.model.CaseType;
import dev.eministar.starbans.model.PlayerProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModerationStorage extends AutoCloseable {

    void init() throws Exception;

    CaseRecord createCase(CaseRecord record) throws Exception;

    CaseRecord updateCase(CaseRecord record) throws Exception;

    Optional<CaseRecord> findCaseById(long caseId) throws Exception;

    Optional<CaseRecord> findLatestCaseForPlayer(UUID playerUniqueId) throws Exception;

    Optional<CaseRecord> findActiveCaseForPlayer(UUID playerUniqueId, CaseType type) throws Exception;

    Optional<CaseRecord> findActiveCaseForIp(String ipAddress, CaseType type) throws Exception;

    List<CaseRecord> getCasesForPlayer(UUID playerUniqueId, int limit, int offset) throws Exception;

    List<CaseRecord> getCasesForIp(String ipAddress, int limit, int offset) throws Exception;

    List<CaseRecord> getCasesByTypeForPlayer(UUID playerUniqueId, CaseType type, int limit, int offset) throws Exception;

    List<CaseRecord> getCasesByType(CaseType type, int limit, int offset) throws Exception;

    List<CaseRecord> getActiveCasesForPlayer(UUID playerUniqueId, CaseType type) throws Exception;

    List<CaseRecord> getRecentCases(int limit, int offset) throws Exception;

    List<CaseRecord> getCasesByActor(String actorName, int limit, int offset) throws Exception;

    List<CaseRecord> getCasesByStatusActor(String actorName, int limit, int offset) throws Exception;

    List<CaseRecord> getActiveAltFlags(UUID playerUniqueId) throws Exception;

    int countVisibleCasesForPlayer(UUID playerUniqueId) throws Exception;

    int countCasesByTypeForPlayer(UUID playerUniqueId, CaseType type) throws Exception;

    int countCasesByActor(String actorName) throws Exception;

    int countCasesByActorAndType(String actorName, CaseType type) throws Exception;

    int countStatusChangesByActor(String actorName) throws Exception;

    int countActiveCases(CaseType type) throws Exception;

    int countAllCases() throws Exception;

    CaseRecord updateCaseStatus(long caseId,
                                CaseStatus newStatus,
                                long changedAt,
                                UUID changedByUniqueId,
                                String changedByName,
                                String note) throws Exception;

    void upsertPlayerProfile(PlayerProfile profile) throws Exception;

    Optional<PlayerProfile> findPlayerProfile(UUID playerUniqueId) throws Exception;

    List<PlayerProfile> findPlayerProfilesByIp(String ipAddress) throws Exception;

    List<PlayerProfile> searchProfilesByName(String input, int limit) throws Exception;

    List<PlayerProfile> getKnownProfiles(int limit, int offset) throws Exception;

    int countKnownProfiles() throws Exception;

    @Override
    void close() throws Exception;
}
