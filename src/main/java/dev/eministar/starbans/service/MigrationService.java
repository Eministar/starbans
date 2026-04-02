package dev.eministar.starbans.service;

import com.google.gson.Gson;
import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.model.CaseRecord;
import dev.eministar.starbans.model.CaseStatus;
import dev.eministar.starbans.model.CaseType;
import dev.eministar.starbans.model.CommandActor;
import dev.eministar.starbans.model.ImportSummary;
import dev.eministar.starbans.model.PlayerIdentity;
import dev.eministar.starbans.model.PlayerProfile;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MigrationService {

    private final StarBans plugin;
    private final Gson gson = new Gson();

    public MigrationService(StarBans plugin) {
        this.plugin = plugin;
    }

    public ImportSummary importSource(String sourceType, String location) throws Exception {
        String normalized = sourceType == null ? "" : sourceType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "STARBANS_JSON", "JSON" -> importStarBansJson(Path.of(location));
            case "LITEBANS_SQLITE", "LITEBANS" -> importHeuristicSqlite(Path.of(location), "litebans");
            case "ADVANCEDBAN_SQLITE", "ADVANCEDBAN" -> importHeuristicSqlite(Path.of(location), "advancedban");
            default -> throw new IllegalArgumentException("Unsupported import source: " + sourceType);
        };
    }

    public ImportSummary importStarBansJson(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonImportDocument document = gson.fromJson(reader, JsonImportDocument.class);
            if (document == null) {
                return new ImportSummary("STARBANS_JSON", 0, 0, 0);
            }

            int profiles = 0;
            if (document.profiles != null) {
                for (PlayerProfile profile : document.profiles) {
                    if (profile == null || profile.getUniqueId() == null) {
                        continue;
                    }
                    plugin.getStorage().upsertPlayerProfile(profile);
                    profiles++;
                }
            }

            int imported = 0;
            int skipped = 0;
            if (document.cases != null) {
                for (CaseRecord record : document.cases) {
                    if (record == null || record.getType() == null) {
                        skipped++;
                        continue;
                    }
                    plugin.getStorage().createCase(record.withId(0L));
                    imported++;
                }
            }
            return new ImportSummary("STARBANS_JSON", imported, profiles, skipped);
        }
    }

    public ImportSummary importHeuristicSqlite(Path path, String flavor) throws Exception {
        String jdbcUrl = "jdbc:sqlite:" + path.toAbsolutePath();
        int imported = 0;
        int profiles = 0;
        int skipped = 0;
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            List<String> tableNames = listTables(connection);
            for (String tableName : tableNames) {
                String normalized = tableName.toLowerCase(Locale.ROOT);
                if (normalized.contains("ban")) {
                    ImportCounter counter = importRows(connection, tableName, flavor, CaseType.BAN);
                    imported += counter.imported();
                    profiles += counter.profiles();
                    skipped += counter.skipped();
                } else if (normalized.contains("mute")) {
                    ImportCounter counter = importRows(connection, tableName, flavor, CaseType.MUTE);
                    imported += counter.imported();
                    profiles += counter.profiles();
                    skipped += counter.skipped();
                } else if (normalized.contains("warn")) {
                    ImportCounter counter = importRows(connection, tableName, flavor, CaseType.WARN);
                    imported += counter.imported();
                    profiles += counter.profiles();
                    skipped += counter.skipped();
                } else if (normalized.contains("kick")) {
                    ImportCounter counter = importRows(connection, tableName, flavor, CaseType.KICK);
                    imported += counter.imported();
                    profiles += counter.profiles();
                    skipped += counter.skipped();
                }
            }
        }
        return new ImportSummary(flavor.toUpperCase(Locale.ROOT), imported, profiles, skipped);
    }

    private ImportCounter importRows(Connection connection, String tableName, String flavor, CaseType type) throws Exception {
        int imported = 0;
        int profiles = 0;
        int skipped = 0;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM " + tableName)) {
            ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, tableName, null);
            List<String> names = new ArrayList<>();
            while (columns.next()) {
                names.add(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }

            while (resultSet.next()) {
                String targetName = readFirst(resultSet, names, "name", "player_name", "target_name");
                String targetUuidRaw = readFirst(resultSet, names, "uuid", "player_uuid", "target_uuid");
                PlayerIdentity target = buildIdentity(targetUuidRaw, targetName);
                if (target == null) {
                    skipped++;
                    continue;
                }

                String actorName = readFirst(resultSet, names, "banned_by_name", "muted_by_name", "warned_by_name", "kicked_by_name", "operator", "staff_name");
                String reason = readFirst(resultSet, names, "reason", "message");
                Long createdAt = readLong(resultSet, names, "time", "created_at", "date", "timestamp");
                Long expiresAt = readLong(resultSet, names, "until", "expires", "expires_at");
                boolean active = readBoolean(resultSet, names, "active", "open", "is_active");

                plugin.getStorage().upsertPlayerProfile(new PlayerProfile(
                        target.uniqueId(),
                        target.name(),
                        null,
                        createdAt == null ? System.currentTimeMillis() : createdAt,
                        createdAt == null ? System.currentTimeMillis() : createdAt
                ));
                profiles++;

                CommandActor actor = new CommandActor(null, actorName == null || actorName.isBlank() ? flavor.toUpperCase(Locale.ROOT) : actorName, true);
                CaseRecord importedRecord = CaseRecord.create(
                        type,
                        type.name().toLowerCase(Locale.ROOT),
                        target,
                        null,
                        null,
                        actor,
                        reason == null || reason.isBlank() ? "Imported from " + flavor : reason,
                        "IMPORT:" + flavor.toUpperCase(Locale.ROOT),
                        expiresAt,
                        "imported",
                        null,
                        List.of("imported", flavor.toLowerCase(Locale.ROOT)),
                        type == CaseType.WARN ? 1 : 0,
                        dev.eministar.starbans.model.CaseVisibility.INTERNAL,
                        null,
                        plugin.getServerRuleService().getActiveProfileId(),
                        null,
                        dev.eministar.starbans.model.CasePriority.NORMAL,
                        null,
                        null
                ).withStatus(
                        active ? CaseStatus.ACTIVE : CaseStatus.RESOLVED,
                        createdAt == null ? System.currentTimeMillis() : createdAt,
                        null,
                        actor.name(),
                        active ? null : "Imported as inactive"
                );
                plugin.getStorage().createCase(importedRecord.withId(0L));
                imported++;
            }
        }
        return new ImportCounter(imported, profiles, skipped);
    }

    private List<String> listTables(Connection connection) throws Exception {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                tables.add(resultSet.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private PlayerIdentity buildIdentity(String uuidRaw, String name) {
        if ((uuidRaw == null || uuidRaw.isBlank()) && (name == null || name.isBlank())) {
            return null;
        }
        UUID uniqueId;
        try {
            uniqueId = uuidRaw == null || uuidRaw.isBlank()
                    ? UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8))
                    : UUID.fromString(uuidRaw);
        } catch (Exception exception) {
            uniqueId = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        }
        String resolvedName = name == null || name.isBlank() ? "imported-" + Instant.now().toEpochMilli() : name;
        return new PlayerIdentity(uniqueId, resolvedName);
    }

    private String readFirst(ResultSet resultSet, List<String> columns, String... candidates) throws Exception {
        for (String candidate : candidates) {
            if (columns.contains(candidate.toLowerCase(Locale.ROOT))) {
                String value = resultSet.getString(candidate);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private Long readLong(ResultSet resultSet, List<String> columns, String... candidates) throws Exception {
        for (String candidate : candidates) {
            if (columns.contains(candidate.toLowerCase(Locale.ROOT))) {
                long value = resultSet.getLong(candidate);
                if (!resultSet.wasNull()) {
                    return value;
                }
            }
        }
        return null;
    }

    private boolean readBoolean(ResultSet resultSet, List<String> columns, String... candidates) throws Exception {
        for (String candidate : candidates) {
            if (columns.contains(candidate.toLowerCase(Locale.ROOT))) {
                Object raw = resultSet.getObject(candidate);
                if (raw instanceof Boolean bool) {
                    return bool;
                }
                if (raw instanceof Number number) {
                    return number.intValue() != 0;
                }
                if (raw != null) {
                    String value = String.valueOf(raw).trim();
                    if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("active")) {
                        return true;
                    }
                    if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("inactive")) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private record JsonImportDocument(List<CaseRecord> cases, List<PlayerProfile> profiles, Map<String, Object> extra) {
    }

    private record ImportCounter(int imported, int profiles, int skipped) {
    }
}
