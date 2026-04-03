package dev.eministar.starbans.database;

import com.google.gson.Gson;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.model.AppealStatus;
import dev.eministar.starbans.model.CaseComment;
import dev.eministar.starbans.model.CaseEvidence;
import dev.eministar.starbans.model.CasePriority;
import dev.eministar.starbans.model.CaseRecord;
import dev.eministar.starbans.model.CaseSearchFilter;
import dev.eministar.starbans.model.CaseStatus;
import dev.eministar.starbans.model.CaseType;
import dev.eministar.starbans.model.CaseVisibility;
import dev.eministar.starbans.model.PlayerProfile;
import dev.eministar.starbans.utils.LoggerUtil;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SqlStorage implements ModerationStorage {

    private final StarBans plugin;
    private final StorageSettings settings;
    private final String casesTable;
    private final String profilesTable;
    private final Gson gson = new Gson();

    private HikariDataSource dataSource;

    public SqlStorage(StarBans plugin, StorageSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.casesTable = settings.table() + "_cases";
        this.profilesTable = settings.table() + "_profiles";
    }

    @Override
    public void init() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setPoolName("StarBans-" + settings.type().name());
        config.setConnectionTimeout(settings.connectionTimeoutMillis());

        if (settings.type() == StorageType.SQLITE) {
            File databaseFile = new File(plugin.getDataFolder(), settings.sqliteFileName());
            File parent = databaseFile.getParentFile();
            if (parent != null && !parent.exists()) {
                Files.createDirectories(parent.toPath());
            }
            config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath().replace('\\', '/'));
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
            config.setConnectionTestQuery("SELECT 1");
        } else {
            String jdbcUrl = "jdbc:mariadb://"
                    + settings.mariaHost() + ':' + settings.mariaPort() + '/'
                    + settings.mariaDatabase();
            if (settings.mariaParameters() != null && !settings.mariaParameters().isBlank()) {
                jdbcUrl += '?' + settings.mariaParameters();
            }
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(settings.mariaUsername());
            config.setPassword(settings.mariaPassword());
            config.setMaximumPoolSize(settings.maximumPoolSize());
            config.setMinimumIdle(Math.min(settings.minimumIdle(), settings.maximumPoolSize()));
        }

        LoggerUtil.quietThirdPartyStartupLogs();
        dataSource = new HikariDataSource(config);
        createSchema();
    }

    @Override
    public CaseRecord createCase(CaseRecord record) throws Exception {
        String sql = "INSERT INTO " + casesTable + " (type, label, target_player_uuid, target_player_name, target_ip, related_player_uuid, related_player_name, actor_uuid, actor_name, reason, source, category, template_key, tags, points, visibility, reference_case_id, server_profile_id, incident_id, priority, claim_actor_uuid, claim_actor_name, claim_changed_at, appeal_status, appeal_deadline_at, appeal_changed_at, appeal_actor_uuid, appeal_actor_name, appeal_notes, next_review_at, last_reviewed_at, review_reason, evidence, created_at, expires_at, status, status_changed_at, status_actor_uuid, status_actor_name, status_note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, record.getType().name());
            statement.setString(2, record.getLabel());
            setUuid(statement, 3, record.getTargetPlayerUniqueId());
            statement.setString(4, record.getTargetPlayerName());
            statement.setString(5, record.getTargetIp());
            setUuid(statement, 6, record.getRelatedPlayerUniqueId());
            statement.setString(7, record.getRelatedPlayerName());
            setUuid(statement, 8, record.getActorUniqueId());
            statement.setString(9, record.getActorName());
            statement.setString(10, record.getReason());
            statement.setString(11, record.getSource());
            statement.setString(12, record.getCategory());
            statement.setString(13, record.getTemplateKey());
            statement.setString(14, encodeTags(record.getTags()));
            statement.setInt(15, record.getPoints());
            statement.setString(16, record.getVisibility().name());
            setLong(statement, 17, record.getReferenceCaseId());
            statement.setString(18, record.getServerProfileId());
            statement.setString(19, record.getIncidentId());
            statement.setString(20, record.getPriority().name());
            setUuid(statement, 21, record.getClaimActorUniqueId());
            statement.setString(22, record.getClaimActorName());
            setLong(statement, 23, record.getClaimChangedAt());
            statement.setString(24, record.getAppealStatus().name());
            setLong(statement, 25, record.getAppealDeadlineAt());
            setLong(statement, 26, record.getAppealChangedAt());
            setUuid(statement, 27, record.getAppealActorUniqueId());
            statement.setString(28, record.getAppealActorName());
            statement.setString(29, encodeComments(record.getAppealNotes()));
            setLong(statement, 30, record.getNextReviewAt());
            setLong(statement, 31, record.getLastReviewedAt());
            statement.setString(32, record.getReviewReason());
            statement.setString(33, encodeEvidence(record.getEvidence()));
            statement.setLong(34, record.getCreatedAt());
            setLong(statement, 35, record.getExpiresAt());
            statement.setString(36, record.getStatus().name());
            setLong(statement, 37, record.getStatusChangedAt());
            setUuid(statement, 38, record.getStatusActorUniqueId());
            statement.setString(39, record.getStatusActorName());
            statement.setString(40, record.getStatusNote());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return record.withId(generatedKeys.getLong(1));
                }
            }
            return record;
        }
    }

    @Override
    public CaseRecord updateCase(CaseRecord record) throws Exception {
        String sql = "UPDATE " + casesTable + " SET type = ?, label = ?, target_player_uuid = ?, target_player_name = ?, target_ip = ?, related_player_uuid = ?, related_player_name = ?, actor_uuid = ?, actor_name = ?, reason = ?, source = ?, category = ?, template_key = ?, tags = ?, points = ?, visibility = ?, reference_case_id = ?, server_profile_id = ?, incident_id = ?, priority = ?, claim_actor_uuid = ?, claim_actor_name = ?, claim_changed_at = ?, appeal_status = ?, appeal_deadline_at = ?, appeal_changed_at = ?, appeal_actor_uuid = ?, appeal_actor_name = ?, appeal_notes = ?, next_review_at = ?, last_reviewed_at = ?, review_reason = ?, evidence = ?, created_at = ?, expires_at = ?, status = ?, status_changed_at = ?, status_actor_uuid = ?, status_actor_name = ?, status_note = ? WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getType().name());
            statement.setString(2, record.getLabel());
            setUuid(statement, 3, record.getTargetPlayerUniqueId());
            statement.setString(4, record.getTargetPlayerName());
            statement.setString(5, record.getTargetIp());
            setUuid(statement, 6, record.getRelatedPlayerUniqueId());
            statement.setString(7, record.getRelatedPlayerName());
            setUuid(statement, 8, record.getActorUniqueId());
            statement.setString(9, record.getActorName());
            statement.setString(10, record.getReason());
            statement.setString(11, record.getSource());
            statement.setString(12, record.getCategory());
            statement.setString(13, record.getTemplateKey());
            statement.setString(14, encodeTags(record.getTags()));
            statement.setInt(15, record.getPoints());
            statement.setString(16, record.getVisibility().name());
            setLong(statement, 17, record.getReferenceCaseId());
            statement.setString(18, record.getServerProfileId());
            statement.setString(19, record.getIncidentId());
            statement.setString(20, record.getPriority().name());
            setUuid(statement, 21, record.getClaimActorUniqueId());
            statement.setString(22, record.getClaimActorName());
            setLong(statement, 23, record.getClaimChangedAt());
            statement.setString(24, record.getAppealStatus().name());
            setLong(statement, 25, record.getAppealDeadlineAt());
            setLong(statement, 26, record.getAppealChangedAt());
            setUuid(statement, 27, record.getAppealActorUniqueId());
            statement.setString(28, record.getAppealActorName());
            statement.setString(29, encodeComments(record.getAppealNotes()));
            setLong(statement, 30, record.getNextReviewAt());
            setLong(statement, 31, record.getLastReviewedAt());
            statement.setString(32, record.getReviewReason());
            statement.setString(33, encodeEvidence(record.getEvidence()));
            statement.setLong(34, record.getCreatedAt());
            setLong(statement, 35, record.getExpiresAt());
            statement.setString(36, record.getStatus().name());
            setLong(statement, 37, record.getStatusChangedAt());
            setUuid(statement, 38, record.getStatusActorUniqueId());
            statement.setString(39, record.getStatusActorName());
            statement.setString(40, record.getStatusNote());
            statement.setLong(41, record.getId());
            statement.executeUpdate();
        }
        return record;
    }

    @Override
    public Optional<CaseRecord> findCaseById(long caseId) throws Exception {
        String sql = "SELECT * FROM " + casesTable + " WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, caseId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapCase(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<CaseRecord> findLatestCaseForPlayer(UUID playerUniqueId) throws Exception {
        String sql = "SELECT * FROM " + casesTable + " WHERE target_player_uuid = ? OR related_player_uuid = ? ORDER BY created_at DESC LIMIT 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUniqueId.toString());
            statement.setString(2, playerUniqueId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapCase(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<CaseRecord> findActiveCaseForPlayer(UUID playerUniqueId, CaseType type) throws Exception {
        String sql = "SELECT * FROM " + casesTable + " WHERE target_player_uuid = ? AND type = ? AND status = ? ORDER BY created_at DESC LIMIT 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUniqueId.toString());
            statement.setString(2, type.name());
            statement.setString(3, CaseStatus.ACTIVE.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapCase(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<CaseRecord> findActiveCaseForIp(String ipAddress, CaseType type) throws Exception {
        String sql = "SELECT * FROM " + casesTable + " WHERE target_ip = ? AND type = ? AND status = ? ORDER BY created_at DESC LIMIT 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ipAddress);
            statement.setString(2, type.name());
            statement.setString(3, CaseStatus.ACTIVE.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapCase(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public List<CaseRecord> getCasesForPlayer(UUID playerUniqueId, int limit, int offset) throws Exception {
        String sql = "SELECT * FROM " + casesTable + " WHERE target_player_uuid = ? OR related_player_uuid = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUniqueId.toString());
            statement.setString(2, playerUniqueId.toString());
            statement.setInt(3, limit);
            statement.setInt(4, offset);
            return readCases(statement);
        }
    }

    @Override
    public List<CaseRecord> getCasesForIp(String ipAddress, int limit, int offset) throws Exception {
        String sql = "SELECT * FROM " + casesTable + " WHERE target_ip = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ipAddress);
            statement.setInt(2, limit);
            statement.setInt(3, offset);
            return readCases(statement);
        }
    }

    @Override
    public List<CaseRecord> getCasesByTypeForPlayer(UUID playerUniqueId, CaseType type, int limit, int offset) throws Exception {
        String sql = "SELECT * FROM " + casesTable + " WHERE type = ? AND (target_player_uuid = ? OR related_player_uuid = ?) ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setString(2, playerUniqueId.toString());
            statement.setString(3, playerUniqueId.toString());
            statement.setInt(4, limit);
            statement.setInt(5, offset);
            return readCases(statement);
        }
    }

    @Override
    public List<CaseRecord> getCasesByType(CaseType type, int limit, int offset) throws Exception {
        String sql = "SELECT * FROM " + casesTable + " WHERE type = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setInt(2, limit);
            statement.setInt(3, offset);
            return readCases(statement);
        }
    }

    @Override
    public List<CaseRecord> getActiveCasesForPlayer(UUID playerUniqueId, CaseType type) throws Exception {
        String sql = "SELECT * FROM " + casesTable + " WHERE type = ? AND target_player_uuid = ? AND status = ? ORDER BY created_at DESC";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setString(2, playerUniqueId.toString());
            statement.setString(3, CaseStatus.ACTIVE.name());
            return readCases(statement);
        }
    }

    @Override
    public List<CaseRecord> getRecentCases(int limit, int offset) throws Exception {
        String sql = "SELECT * FROM " + casesTable + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            statement.setInt(2, offset);
            return readCases(statement);
        }
    }

    @Override
    public List<CaseRecord> searchCases(CaseSearchFilter filter, int limit, int offset) throws Exception {
        SearchQuery query = buildSearchQuery(filter, false);
        String sql = query.sql() + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindQuery(statement, query.params());
            int base = query.params().size();
            statement.setInt(base + 1, limit);
            statement.setInt(base + 2, offset);
            return readCases(statement);
        }
    }

    @Override
    public List<CaseRecord> getCasesByActor(String actorName, int limit, int offset) throws Exception {
        String sql = "SELECT * FROM " + casesTable + " WHERE LOWER(actor_name) = LOWER(?) ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, actorName);
            statement.setInt(2, limit);
            statement.setInt(3, offset);
            return readCases(statement);
        }
    }

    @Override
    public List<CaseRecord> getCasesByStatusActor(String actorName, int limit, int offset) throws Exception {
        String sql = "SELECT * FROM " + casesTable + " WHERE LOWER(status_actor_name) = LOWER(?) ORDER BY status_changed_at DESC LIMIT ? OFFSET ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, actorName);
            statement.setInt(2, limit);
            statement.setInt(3, offset);
            return readCases(statement);
        }
    }

    @Override
    public List<CaseRecord> getActiveAltFlags(UUID playerUniqueId) throws Exception {
        String sql = "SELECT * FROM " + casesTable + " WHERE type = ? AND status = ? AND (target_player_uuid = ? OR related_player_uuid = ?) ORDER BY created_at DESC";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, CaseType.ALT_FLAG.name());
            statement.setString(2, CaseStatus.ACTIVE.name());
            statement.setString(3, playerUniqueId.toString());
            statement.setString(4, playerUniqueId.toString());
            return readCases(statement);
        }
    }

    @Override
    public int countVisibleCasesForPlayer(UUID playerUniqueId) throws Exception {
        String sql = "SELECT COUNT(*) FROM " + casesTable + " WHERE target_player_uuid = ? OR related_player_uuid = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUniqueId.toString());
            statement.setString(2, playerUniqueId.toString());
            return readCount(statement);
        }
    }

    @Override
    public int countCasesByTypeForPlayer(UUID playerUniqueId, CaseType type) throws Exception {
        String sql = "SELECT COUNT(*) FROM " + casesTable + " WHERE type = ? AND (target_player_uuid = ? OR related_player_uuid = ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setString(2, playerUniqueId.toString());
            statement.setString(3, playerUniqueId.toString());
            return readCount(statement);
        }
    }

    @Override
    public int countCasesByActor(String actorName) throws Exception {
        String sql = "SELECT COUNT(*) FROM " + casesTable + " WHERE LOWER(actor_name) = LOWER(?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, actorName);
            return readCount(statement);
        }
    }

    @Override
    public int countCasesByActorAndType(String actorName, CaseType type) throws Exception {
        String sql = "SELECT COUNT(*) FROM " + casesTable + " WHERE type = ? AND LOWER(actor_name) = LOWER(?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setString(2, actorName);
            return readCount(statement);
        }
    }

    @Override
    public int countStatusChangesByActor(String actorName) throws Exception {
        String sql = "SELECT COUNT(*) FROM " + casesTable + " WHERE LOWER(status_actor_name) = LOWER(?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, actorName);
            return readCount(statement);
        }
    }

    @Override
    public int countCases(CaseSearchFilter filter) throws Exception {
        SearchQuery query = buildSearchQuery(filter, true);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query.sql())) {
            bindQuery(statement, query.params());
            return readCount(statement);
        }
    }

    @Override
    public int countActiveCases(CaseType type) throws Exception {
        String sql = "SELECT COUNT(*) FROM " + casesTable + " WHERE type = ? AND status = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setString(2, CaseStatus.ACTIVE.name());
            return readCount(statement);
        }
    }

    @Override
    public int countAllCases() throws Exception {
        String sql = "SELECT COUNT(*) FROM " + casesTable;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            return readCount(statement);
        }
    }

    @Override
    public CaseRecord updateCaseStatus(long caseId,
                                       CaseStatus newStatus,
                                       long changedAt,
                                       UUID changedByUniqueId,
                                       String changedByName,
                                       String note) throws Exception {
        CaseRecord current = findCaseById(caseId).orElseThrow(() -> new IllegalArgumentException("No case record found for id " + caseId));
        String sql = "UPDATE " + casesTable + " SET status = ?, status_changed_at = ?, status_actor_uuid = ?, status_actor_name = ?, status_note = ? WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, newStatus.name());
            statement.setLong(2, changedAt);
            setUuid(statement, 3, changedByUniqueId);
            statement.setString(4, changedByName);
            statement.setString(5, note);
            statement.setLong(6, caseId);
            statement.executeUpdate();
        }
        return current.withStatus(newStatus, changedAt, changedByUniqueId, changedByName, note);
    }

    @Override
    public void upsertPlayerProfile(PlayerProfile profile) throws Exception {
        if (settings.type() == StorageType.SQLITE) {
            upsertSqliteProfile(profile);
            return;
        }

        String sql = "INSERT INTO " + profilesTable + " (player_uuid, player_name, last_ip, first_seen, last_seen) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), last_ip = VALUES(last_ip), first_seen = VALUES(first_seen), last_seen = VALUES(last_seen)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.getUniqueId().toString());
            statement.setString(2, profile.getLastName());
            statement.setString(3, profile.getLastIp());
            statement.setLong(4, profile.getFirstSeen());
            statement.setLong(5, profile.getLastSeen());
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<PlayerProfile> findPlayerProfile(UUID playerUniqueId) throws Exception {
        String sql = "SELECT * FROM " + profilesTable + " WHERE player_uuid = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUniqueId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapProfile(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public List<PlayerProfile> findPlayerProfilesByIp(String ipAddress) throws Exception {
        String sql = "SELECT * FROM " + profilesTable + " WHERE last_ip = ? ORDER BY last_seen DESC";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ipAddress);
            return readProfiles(statement);
        }
    }

    @Override
    public List<PlayerProfile> searchProfilesByName(String input, int limit) throws Exception {
        String sql = settings.type() == StorageType.SQLITE
                ? "SELECT * FROM " + profilesTable + " WHERE LOWER(player_name) LIKE ? ORDER BY player_name ASC LIMIT ?"
                : "SELECT * FROM " + profilesTable + " WHERE player_name LIKE ? ORDER BY player_name ASC LIMIT ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, input.toLowerCase() + "%");
            statement.setInt(2, limit);
            return readProfiles(statement);
        }
    }

    @Override
    public List<PlayerProfile> getKnownProfiles(int limit, int offset) throws Exception {
        String sql = "SELECT * FROM " + profilesTable + " ORDER BY last_seen DESC, player_name ASC LIMIT ? OFFSET ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            statement.setInt(2, offset);
            return readProfiles(statement);
        }
    }

    @Override
    public int countKnownProfiles() throws Exception {
        String sql = "SELECT COUNT(*) FROM " + profilesTable;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            return readCount(statement);
        }
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    private void createSchema() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            if (settings.type() == StorageType.SQLITE) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + casesTable + " ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "type VARCHAR(32) NOT NULL, "
                        + "label VARCHAR(64) NULL, "
                        + "target_player_uuid VARCHAR(36) NULL, "
                        + "target_player_name VARCHAR(32) NULL, "
                        + "target_ip VARCHAR(64) NULL, "
                        + "related_player_uuid VARCHAR(36) NULL, "
                        + "related_player_name VARCHAR(32) NULL, "
                        + "actor_uuid VARCHAR(36) NULL, "
                        + "actor_name VARCHAR(64) NOT NULL, "
                        + "reason TEXT NOT NULL, "
                        + "source VARCHAR(64) NOT NULL, "
                        + "category VARCHAR(64) NULL, "
                        + "template_key VARCHAR(64) NULL, "
                        + "tags TEXT NULL, "
                        + "points INTEGER NOT NULL DEFAULT 0, "
                        + "visibility VARCHAR(16) NOT NULL DEFAULT 'INTERNAL', "
                        + "reference_case_id BIGINT NULL, "
                        + "server_profile_id VARCHAR(64) NULL, "
                        + "incident_id VARCHAR(64) NULL, "
                        + "priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL', "
                        + "claim_actor_uuid VARCHAR(36) NULL, "
                        + "claim_actor_name VARCHAR(64) NULL, "
                        + "claim_changed_at BIGINT NULL, "
                        + "appeal_status VARCHAR(16) NOT NULL DEFAULT 'NONE', "
                        + "appeal_deadline_at BIGINT NULL, "
                        + "appeal_changed_at BIGINT NULL, "
                        + "appeal_actor_uuid VARCHAR(36) NULL, "
                        + "appeal_actor_name VARCHAR(64) NULL, "
                        + "appeal_notes TEXT NULL, "
                        + "next_review_at BIGINT NULL, "
                        + "last_reviewed_at BIGINT NULL, "
                        + "review_reason TEXT NULL, "
                        + "evidence TEXT NULL, "
                        + "created_at BIGINT NOT NULL, "
                        + "expires_at BIGINT NULL, "
                        + "status VARCHAR(16) NOT NULL, "
                        + "status_changed_at BIGINT NULL, "
                        + "status_actor_uuid VARCHAR(36) NULL, "
                        + "status_actor_name VARCHAR(64) NULL, "
                        + "status_note TEXT NULL)"
                );
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + profilesTable + " ("
                        + "player_uuid VARCHAR(36) PRIMARY KEY, "
                        + "player_name VARCHAR(32) NOT NULL, "
                        + "last_ip VARCHAR(64) NULL, "
                        + "first_seen BIGINT NOT NULL, "
                        + "last_seen BIGINT NOT NULL)"
                );
            } else {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + casesTable + " ("
                        + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                        + "type VARCHAR(32) NOT NULL, "
                        + "label VARCHAR(64) NULL, "
                        + "target_player_uuid VARCHAR(36) NULL, "
                        + "target_player_name VARCHAR(32) NULL, "
                        + "target_ip VARCHAR(64) NULL, "
                        + "related_player_uuid VARCHAR(36) NULL, "
                        + "related_player_name VARCHAR(32) NULL, "
                        + "actor_uuid VARCHAR(36) NULL, "
                        + "actor_name VARCHAR(64) NOT NULL, "
                        + "reason TEXT NOT NULL, "
                        + "source VARCHAR(64) NOT NULL, "
                        + "category VARCHAR(64) NULL, "
                        + "template_key VARCHAR(64) NULL, "
                        + "tags TEXT NULL, "
                        + "points INT NOT NULL DEFAULT 0, "
                        + "visibility VARCHAR(16) NOT NULL DEFAULT 'INTERNAL', "
                        + "reference_case_id BIGINT NULL, "
                        + "server_profile_id VARCHAR(64) NULL, "
                        + "incident_id VARCHAR(64) NULL, "
                        + "priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL', "
                        + "claim_actor_uuid VARCHAR(36) NULL, "
                        + "claim_actor_name VARCHAR(64) NULL, "
                        + "claim_changed_at BIGINT NULL, "
                        + "appeal_status VARCHAR(16) NOT NULL DEFAULT 'NONE', "
                        + "appeal_deadline_at BIGINT NULL, "
                        + "appeal_changed_at BIGINT NULL, "
                        + "appeal_actor_uuid VARCHAR(36) NULL, "
                        + "appeal_actor_name VARCHAR(64) NULL, "
                        + "appeal_notes TEXT NULL, "
                        + "next_review_at BIGINT NULL, "
                        + "last_reviewed_at BIGINT NULL, "
                        + "review_reason TEXT NULL, "
                        + "evidence LONGTEXT NULL, "
                        + "created_at BIGINT NOT NULL, "
                        + "expires_at BIGINT NULL, "
                        + "status VARCHAR(16) NOT NULL, "
                        + "status_changed_at BIGINT NULL, "
                        + "status_actor_uuid VARCHAR(36) NULL, "
                        + "status_actor_name VARCHAR(64) NULL, "
                        + "status_note TEXT NULL"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
                );
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + profilesTable + " ("
                        + "player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                        + "player_name VARCHAR(32) NOT NULL, "
                        + "last_ip VARCHAR(64) NULL, "
                        + "first_seen BIGINT NOT NULL, "
                        + "last_seen BIGINT NOT NULL"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
                );
            }

            if (settings.type() == StorageType.SQLITE) {
                ensureCaseColumn(connection, "category", "VARCHAR(64) NULL");
                ensureCaseColumn(connection, "template_key", "VARCHAR(64) NULL");
                ensureCaseColumn(connection, "tags", "TEXT NULL");
                ensureCaseColumn(connection, "points", "INTEGER NOT NULL DEFAULT 0");
                ensureCaseColumn(connection, "visibility", "VARCHAR(16) NOT NULL DEFAULT 'INTERNAL'");
                ensureCaseColumn(connection, "reference_case_id", "BIGINT NULL");
                ensureCaseColumn(connection, "server_profile_id", "VARCHAR(64) NULL");
                ensureCaseColumn(connection, "incident_id", "VARCHAR(64) NULL");
                ensureCaseColumn(connection, "priority", "VARCHAR(16) NOT NULL DEFAULT 'NORMAL'");
                ensureCaseColumn(connection, "claim_actor_uuid", "VARCHAR(36) NULL");
                ensureCaseColumn(connection, "claim_actor_name", "VARCHAR(64) NULL");
                ensureCaseColumn(connection, "claim_changed_at", "BIGINT NULL");
                ensureCaseColumn(connection, "appeal_status", "VARCHAR(16) NOT NULL DEFAULT 'NONE'");
                ensureCaseColumn(connection, "appeal_deadline_at", "BIGINT NULL");
                ensureCaseColumn(connection, "appeal_changed_at", "BIGINT NULL");
                ensureCaseColumn(connection, "appeal_actor_uuid", "VARCHAR(36) NULL");
                ensureCaseColumn(connection, "appeal_actor_name", "VARCHAR(64) NULL");
                ensureCaseColumn(connection, "appeal_notes", "TEXT NULL");
                ensureCaseColumn(connection, "next_review_at", "BIGINT NULL");
                ensureCaseColumn(connection, "last_reviewed_at", "BIGINT NULL");
                ensureCaseColumn(connection, "review_reason", "TEXT NULL");
                ensureCaseColumn(connection, "evidence", "TEXT NULL");
            } else {
                ensureCaseColumn(connection, "category", "VARCHAR(64) NULL");
                ensureCaseColumn(connection, "template_key", "VARCHAR(64) NULL");
                ensureCaseColumn(connection, "tags", "TEXT NULL");
                ensureCaseColumn(connection, "points", "INT NOT NULL DEFAULT 0");
                ensureCaseColumn(connection, "visibility", "VARCHAR(16) NOT NULL DEFAULT 'INTERNAL'");
                ensureCaseColumn(connection, "reference_case_id", "BIGINT NULL");
                ensureCaseColumn(connection, "server_profile_id", "VARCHAR(64) NULL");
                ensureCaseColumn(connection, "incident_id", "VARCHAR(64) NULL");
                ensureCaseColumn(connection, "priority", "VARCHAR(16) NOT NULL DEFAULT 'NORMAL'");
                ensureCaseColumn(connection, "claim_actor_uuid", "VARCHAR(36) NULL");
                ensureCaseColumn(connection, "claim_actor_name", "VARCHAR(64) NULL");
                ensureCaseColumn(connection, "claim_changed_at", "BIGINT NULL");
                ensureCaseColumn(connection, "appeal_status", "VARCHAR(16) NOT NULL DEFAULT 'NONE'");
                ensureCaseColumn(connection, "appeal_deadline_at", "BIGINT NULL");
                ensureCaseColumn(connection, "appeal_changed_at", "BIGINT NULL");
                ensureCaseColumn(connection, "appeal_actor_uuid", "VARCHAR(36) NULL");
                ensureCaseColumn(connection, "appeal_actor_name", "VARCHAR(64) NULL");
                ensureCaseColumn(connection, "appeal_notes", "TEXT NULL");
                ensureCaseColumn(connection, "next_review_at", "BIGINT NULL");
                ensureCaseColumn(connection, "last_reviewed_at", "BIGINT NULL");
                ensureCaseColumn(connection, "review_reason", "TEXT NULL");
                ensureCaseColumn(connection, "evidence", "LONGTEXT NULL");
            }

            if (settings.type() == StorageType.SQLITE) {
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + casesTable + "_target_uuid ON " + casesTable + " (target_player_uuid)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + casesTable + "_related_uuid ON " + casesTable + " (related_player_uuid)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + casesTable + "_target_ip ON " + casesTable + " (target_ip)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + casesTable + "_type ON " + casesTable + " (type)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + casesTable + "_status ON " + casesTable + " (status)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + casesTable + "_actor_name ON " + casesTable + " (actor_name)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + casesTable + "_status_actor_name ON " + casesTable + " (status_actor_name)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + casesTable + "_server_profile ON " + casesTable + " (server_profile_id)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + casesTable + "_incident ON " + casesTable + " (incident_id)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + casesTable + "_appeal_status ON " + casesTable + " (appeal_status)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + profilesTable + "_name ON " + profilesTable + " (player_name)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + profilesTable + "_ip ON " + profilesTable + " (last_ip)");
            } else {
                createMariaIndex(statement, "idx_" + casesTable + "_target_uuid", casesTable, "target_player_uuid");
                createMariaIndex(statement, "idx_" + casesTable + "_related_uuid", casesTable, "related_player_uuid");
                createMariaIndex(statement, "idx_" + casesTable + "_target_ip", casesTable, "target_ip");
                createMariaIndex(statement, "idx_" + casesTable + "_type", casesTable, "type");
                createMariaIndex(statement, "idx_" + casesTable + "_status", casesTable, "status");
                createMariaIndex(statement, "idx_" + casesTable + "_actor_name", casesTable, "actor_name");
                createMariaIndex(statement, "idx_" + casesTable + "_status_actor_name", casesTable, "status_actor_name");
                createMariaIndex(statement, "idx_" + casesTable + "_server_profile", casesTable, "server_profile_id");
                createMariaIndex(statement, "idx_" + casesTable + "_incident", casesTable, "incident_id");
                createMariaIndex(statement, "idx_" + casesTable + "_appeal_status", casesTable, "appeal_status");
                createMariaIndex(statement, "idx_" + profilesTable + "_name", profilesTable, "player_name");
                createMariaIndex(statement, "idx_" + profilesTable + "_ip", profilesTable, "last_ip");
            }
        }
    }

    private void upsertSqliteProfile(PlayerProfile profile) throws Exception {
        String updateSql = "UPDATE " + profilesTable + " SET player_name = ?, last_ip = ?, first_seen = ?, last_seen = ? WHERE player_uuid = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(updateSql)) {
            update.setString(1, profile.getLastName());
            update.setString(2, profile.getLastIp());
            update.setLong(3, profile.getFirstSeen());
            update.setLong(4, profile.getLastSeen());
            update.setString(5, profile.getUniqueId().toString());
            int changed = update.executeUpdate();
            if (changed > 0) {
                return;
            }
        }

        String insertSql = "INSERT INTO " + profilesTable + " (player_uuid, player_name, last_ip, first_seen, last_seen) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(insertSql)) {
            insert.setString(1, profile.getUniqueId().toString());
            insert.setString(2, profile.getLastName());
            insert.setString(3, profile.getLastIp());
            insert.setLong(4, profile.getFirstSeen());
            insert.setLong(5, profile.getLastSeen());
            insert.executeUpdate();
        }
    }

    private List<CaseRecord> readCases(PreparedStatement statement) throws Exception {
        List<CaseRecord> records = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                records.add(mapCase(resultSet));
            }
        }
        return records;
    }

    private List<PlayerProfile> readProfiles(PreparedStatement statement) throws Exception {
        List<PlayerProfile> profiles = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                profiles.add(mapProfile(resultSet));
            }
        }
        return profiles;
    }

    private int readCount(PreparedStatement statement) throws Exception {
        try (ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private void createMariaIndex(Statement statement, String indexName, String tableName, String column) throws SQLException {
        try {
            statement.executeUpdate("CREATE INDEX " + indexName + " ON " + tableName + " (" + column + ")");
        } catch (SQLException exception) {
            if (exception.getErrorCode() != 1061) {
                throw exception;
            }
        }
    }

    private CaseRecord mapCase(ResultSet resultSet) throws SQLException {
        return new CaseRecord(
                resultSet.getLong("id"),
                CaseType.valueOf(resultSet.getString("type")),
                resultSet.getString("label"),
                parseUuid(resultSet.getString("target_player_uuid")),
                resultSet.getString("target_player_name"),
                resultSet.getString("target_ip"),
                parseUuid(resultSet.getString("related_player_uuid")),
                resultSet.getString("related_player_name"),
                parseUuid(resultSet.getString("actor_uuid")),
                resultSet.getString("actor_name"),
                resultSet.getString("reason"),
                resultSet.getString("source"),
                resultSet.getString("category"),
                resultSet.getString("template_key"),
                decodeTags(resultSet.getString("tags")),
                resultSet.getInt("points"),
                CaseVisibility.fromConfig(resultSet.getString("visibility")),
                parseLong(resultSet, "reference_case_id"),
                resultSet.getString("server_profile_id"),
                resultSet.getString("incident_id"),
                CasePriority.fromConfig(resultSet.getString("priority")),
                parseUuid(resultSet.getString("claim_actor_uuid")),
                resultSet.getString("claim_actor_name"),
                parseLong(resultSet, "claim_changed_at"),
                AppealStatus.fromConfig(resultSet.getString("appeal_status")),
                parseLong(resultSet, "appeal_deadline_at"),
                parseLong(resultSet, "appeal_changed_at"),
                parseUuid(resultSet.getString("appeal_actor_uuid")),
                resultSet.getString("appeal_actor_name"),
                decodeComments(resultSet.getString("appeal_notes")),
                parseLong(resultSet, "next_review_at"),
                parseLong(resultSet, "last_reviewed_at"),
                resultSet.getString("review_reason"),
                decodeEvidence(resultSet.getString("evidence")),
                resultSet.getLong("created_at"),
                parseLong(resultSet, "expires_at"),
                CaseStatus.valueOf(resultSet.getString("status")),
                parseLong(resultSet, "status_changed_at"),
                parseUuid(resultSet.getString("status_actor_uuid")),
                resultSet.getString("status_actor_name"),
                resultSet.getString("status_note")
        );
    }

    private PlayerProfile mapProfile(ResultSet resultSet) throws SQLException {
        return new PlayerProfile(
                UUID.fromString(resultSet.getString("player_uuid")),
                resultSet.getString("player_name"),
                resultSet.getString("last_ip"),
                resultSet.getLong("first_seen"),
                resultSet.getLong("last_seen")
        );
    }

    private void setUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, value.toString());
    }

    private void setLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
            return;
        }
        statement.setLong(index, value);
    }

    private UUID parseUuid(String raw) {
        return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
    }

    private Long parseLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private void ensureCaseColumn(Connection connection, String columnName, String definition) throws SQLException {
        if (hasColumn(connection, casesTable, columnName)) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + casesTable + " ADD COLUMN " + columnName + " " + definition);
        }
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        if (settings.type() == StorageType.SQLITE) {
            String sql = "PRAGMA table_info('" + tableName.replace("'", "''") + "')";
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(sql)) {
                while (resultSet.next()) {
                    String existing = resultSet.getString("name");
                    if (existing != null && existing.equalsIgnoreCase(columnName)) {
                        return true;
                    }
                }
            }
            return false;
        }

        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, tableName, columnName)) {
            if (resultSet.next()) {
                return true;
            }
        }

        try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, tableName.toUpperCase(), columnName.toUpperCase())) {
            return resultSet.next();
        }
    }

    private String encodeTags(List<String> tags) {
        return gson.toJson(tags == null ? List.of() : tags);
    }

    private String encodeComments(List<CaseComment> comments) {
        return gson.toJson(comments == null ? List.of() : comments);
    }

    private String encodeEvidence(List<CaseEvidence> evidence) {
        return gson.toJson(evidence == null ? List.of() : evidence);
    }

    private List<String> decodeTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        try {
            String[] values = gson.fromJson(raw, String[].class);
            if (values == null || values.length == 0) {
                return List.of();
            }
            return List.copyOf(Arrays.asList(values));
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<CaseComment> decodeComments(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        try {
            CaseComment[] values = gson.fromJson(raw, CaseComment[].class);
            if (values == null || values.length == 0) {
                return List.of();
            }
            return List.copyOf(Arrays.asList(values));
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<CaseEvidence> decodeEvidence(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        try {
            CaseEvidence[] values = gson.fromJson(raw, CaseEvidence[].class);
            if (values == null || values.length == 0) {
                return List.of();
            }
            return List.copyOf(Arrays.asList(values));
        } catch (Exception exception) {
            return List.of();
        }
    }

    private SearchQuery buildSearchQuery(CaseSearchFilter filter, boolean countOnly) {
        StringBuilder sql = new StringBuilder(countOnly ? "SELECT COUNT(*) FROM " : "SELECT * FROM ")
                .append(casesTable)
                .append(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (filter == null) {
            return new SearchQuery(sql.toString(), params);
        }

        if (filter.type() != null) {
            sql.append(" AND type = ?");
            params.add(filter.type().name());
        }
        if (filter.status() != null) {
            sql.append(" AND status = ?");
            params.add(filter.status().name());
        }
        if (filter.actorName() != null && !filter.actorName().isBlank()) {
            sql.append(" AND LOWER(actor_name) LIKE LOWER(?)");
            params.add('%' + filter.actorName() + '%');
        }
        if (filter.targetName() != null && !filter.targetName().isBlank()) {
            sql.append(" AND LOWER(target_player_name) LIKE LOWER(?)");
            params.add('%' + filter.targetName() + '%');
        }
        if (filter.tag() != null && !filter.tag().isBlank()) {
            sql.append(" AND LOWER(tags) LIKE LOWER(?)");
            params.add('%' + filter.tag() + '%');
        }
        if (filter.category() != null && !filter.category().isBlank()) {
            sql.append(" AND LOWER(category) = LOWER(?)");
            params.add(filter.category());
        }
        if (filter.serverProfileId() != null && !filter.serverProfileId().isBlank()) {
            sql.append(" AND LOWER(server_profile_id) = LOWER(?)");
            params.add(filter.serverProfileId());
        }
        if (filter.incidentId() != null && !filter.incidentId().isBlank()) {
            sql.append(" AND LOWER(incident_id) = LOWER(?)");
            params.add(filter.incidentId());
        }
        if (filter.createdAfter() != null) {
            sql.append(" AND created_at >= ?");
            params.add(filter.createdAfter());
        }
        if (filter.createdBefore() != null) {
            sql.append(" AND created_at <= ?");
            params.add(filter.createdBefore());
        }
        if (filter.appealStatus() != null && filter.appealStatus() != AppealStatus.NONE) {
            sql.append(" AND appeal_status = ?");
            params.add(filter.appealStatus().name());
        }
        if (filter.priority() != null && filter.priority() != CasePriority.NORMAL) {
            sql.append(" AND priority = ?");
            params.add(filter.priority().name());
        }
        if (filter.text() != null && !filter.text().isBlank()) {
            sql.append(" AND (LOWER(reason) LIKE LOWER(?) OR LOWER(label) LIKE LOWER(?) OR LOWER(target_player_name) LIKE LOWER(?) OR LOWER(actor_name) LIKE LOWER(?) OR LOWER(target_ip) LIKE LOWER(?))");
            String pattern = '%' + filter.text() + '%';
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        return new SearchQuery(sql.toString(), params);
    }

    private void bindQuery(PreparedStatement statement, List<Object> params) throws SQLException {
        for (int index = 0; index < params.size(); index++) {
            Object value = params.get(index);
            int parameterIndex = index + 1;
            if (value instanceof Long longValue) {
                statement.setLong(parameterIndex, longValue);
            } else if (value instanceof Integer intValue) {
                statement.setInt(parameterIndex, intValue);
            } else {
                statement.setString(parameterIndex, String.valueOf(value));
            }
        }
    }

    private record SearchQuery(String sql, List<Object> params) {
    }
}
