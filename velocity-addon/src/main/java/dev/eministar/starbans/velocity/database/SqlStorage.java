package dev.eministar.starbans.velocity.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.eministar.starbans.velocity.StarBansVelocityAddon;
import dev.eministar.starbans.velocity.model.CaseRecord;
import dev.eministar.starbans.velocity.model.CaseStatus;
import dev.eministar.starbans.velocity.model.CaseType;
import dev.eministar.starbans.velocity.model.PlayerProfile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SqlStorage implements VelocityStorage {

    private final StarBansVelocityAddon plugin;
    private final StorageSettings settings;
    private final String casesTable;
    private final String profilesTable;

    private HikariDataSource dataSource;

    public SqlStorage(StarBansVelocityAddon plugin, StorageSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.casesTable = settings.table() + "_cases";
        this.profilesTable = settings.table() + "_profiles";
    }

    @Override
    public void init() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setPoolName("StarBansVelocity-" + settings.type().name());
        config.setConnectionTimeout(settings.connectionTimeoutMillis());

        if (settings.type() == StorageType.SQLITE) {
            Path databaseFile = plugin.getDataDirectory().resolve(settings.sqliteFileName());
            if (Files.notExists(databaseFile.getParent())) {
                Files.createDirectories(databaseFile.getParent());
            }
            config.setJdbcUrl("jdbc:sqlite:" + databaseFile.toAbsolutePath().toString().replace('\\', '/'));
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
            config.setConnectionTestQuery("SELECT 1");
        } else {
            String jdbcUrl = "jdbc:mariadb://" + settings.mariaHost() + ':' + settings.mariaPort() + '/' + settings.mariaDatabase();
            if (settings.mariaParameters() != null && !settings.mariaParameters().isBlank()) {
                jdbcUrl += '?' + settings.mariaParameters();
            }
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(settings.mariaUsername());
            config.setPassword(settings.mariaPassword());
            config.setMaximumPoolSize(settings.maximumPoolSize());
            config.setMinimumIdle(Math.min(settings.minimumIdle(), settings.maximumPoolSize()));
        }

        dataSource = new HikariDataSource(config);
        createSchema();
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
    public Optional<CaseRecord> findLatestCaseForPlayer(UUID playerUniqueId, CaseType type) throws Exception {
        String sql = "SELECT * FROM " + casesTable + " WHERE target_player_uuid = ? AND type = ? ORDER BY created_at DESC LIMIT 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUniqueId.toString());
            statement.setString(2, type.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapCase(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<CaseRecord> findCaseById(long caseId) throws Exception {
        return loadCaseById(caseId);
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
    public Optional<PlayerProfile> findPlayerProfileByName(String playerName) throws Exception {
        String sql = settings.type() == StorageType.SQLITE
                ? "SELECT * FROM " + profilesTable + " WHERE LOWER(player_name) = ? ORDER BY last_seen DESC LIMIT 1"
                : "SELECT * FROM " + profilesTable + " WHERE player_name = ? ORDER BY last_seen DESC LIMIT 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, settings.type() == StorageType.SQLITE ? playerName.toLowerCase() : playerName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapProfile(resultSet)) : Optional.empty();
            }
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
    public CaseRecord updateCaseStatus(long caseId,
                                       CaseStatus newStatus,
                                       long changedAt,
                                       UUID changedByUniqueId,
                                       String changedByName,
                                       String note) throws Exception {
        CaseRecord current = loadCaseById(caseId).orElseThrow(() -> new IllegalArgumentException("No case record found for id " + caseId));
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
        return new CaseRecord(
                current.getId(),
                current.getType(),
                current.getLabel(),
                current.getTargetPlayerUniqueId(),
                current.getTargetPlayerName(),
                current.getTargetIp(),
                current.getRelatedPlayerUniqueId(),
                current.getRelatedPlayerName(),
                current.getActorUniqueId(),
                current.getActorName(),
                current.getReason(),
                current.getSource(),
                current.getCreatedAt(),
                current.getExpiresAt(),
                newStatus,
                changedAt,
                changedByUniqueId,
                changedByName,
                note
        );
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    private Optional<CaseRecord> loadCaseById(long caseId) throws Exception {
        String sql = "SELECT * FROM " + casesTable + " WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, caseId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapCase(resultSet)) : Optional.empty();
            }
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
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + casesTable + "_target_uuid ON " + casesTable + " (target_player_uuid)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + casesTable + "_target_ip ON " + casesTable + " (target_ip)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + casesTable + "_type ON " + casesTable + " (type)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + casesTable + "_status ON " + casesTable + " (status)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + profilesTable + "_name ON " + profilesTable + " (player_name)");
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
                createMariaIndex(statement, "idx_" + casesTable + "_target_uuid", casesTable, "target_player_uuid");
                createMariaIndex(statement, "idx_" + casesTable + "_target_ip", casesTable, "target_ip");
                createMariaIndex(statement, "idx_" + casesTable + "_type", casesTable, "type");
                createMariaIndex(statement, "idx_" + casesTable + "_status", casesTable, "status");
                createMariaIndex(statement, "idx_" + profilesTable + "_name", profilesTable, "player_name");
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

    private List<PlayerProfile> readProfiles(PreparedStatement statement) throws Exception {
        List<PlayerProfile> profiles = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                profiles.add(mapProfile(resultSet));
            }
        }
        return profiles;
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

    private UUID parseUuid(String raw) {
        return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
    }

    private Long parseLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }
}
