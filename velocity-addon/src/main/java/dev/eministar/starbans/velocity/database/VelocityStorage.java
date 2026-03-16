package dev.eministar.starbans.velocity.database;

import dev.eministar.starbans.velocity.model.CaseRecord;
import dev.eministar.starbans.velocity.model.CaseStatus;
import dev.eministar.starbans.velocity.model.CaseType;
import dev.eministar.starbans.velocity.model.PlayerProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VelocityStorage extends AutoCloseable {

    void init() throws Exception;

    Optional<CaseRecord> findActiveCaseForPlayer(UUID playerUniqueId, CaseType type) throws Exception;

    Optional<CaseRecord> findActiveCaseForIp(String ipAddress, CaseType type) throws Exception;

    Optional<CaseRecord> findLatestCaseForPlayer(UUID playerUniqueId, CaseType type) throws Exception;

    Optional<CaseRecord> findCaseById(long caseId) throws Exception;

    Optional<PlayerProfile> findPlayerProfile(UUID playerUniqueId) throws Exception;

    Optional<PlayerProfile> findPlayerProfileByName(String playerName) throws Exception;

    List<PlayerProfile> searchProfilesByName(String input, int limit) throws Exception;

    void upsertPlayerProfile(PlayerProfile profile) throws Exception;

    CaseRecord updateCaseStatus(long caseId,
                                CaseStatus newStatus,
                                long changedAt,
                                UUID changedByUniqueId,
                                String changedByName,
                                String note) throws Exception;

    @Override
    void close() throws Exception;
}
