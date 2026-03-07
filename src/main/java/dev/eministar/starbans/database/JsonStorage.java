package dev.eministar.starbans.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.model.CaseRecord;
import dev.eministar.starbans.model.CaseStatus;
import dev.eministar.starbans.model.CaseType;
import dev.eministar.starbans.model.PlayerProfile;
import dev.eministar.starbans.utils.LoggerUtil;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class JsonStorage implements ModerationStorage {

    private final StarBans plugin;
    private final StorageSettings settings;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private File file;
    private JsonDatabaseDocument document;

    public JsonStorage(StarBans plugin, StorageSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    @Override
    public synchronized void init() throws Exception {
        file = new File(plugin.getDataFolder(), settings.jsonFileName());
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            Files.createDirectories(parent.toPath());
        }

        if (!file.exists()) {
            document = new JsonDatabaseDocument();
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(file.toPath())) {
            document = gson.fromJson(reader, JsonDatabaseDocument.class);
            if (document == null) {
                document = new JsonDatabaseDocument();
            }
            if (document.cases == null) {
                document.cases = new ArrayList<>();
            }
            if (document.profiles == null) {
                document.profiles = new ArrayList<>();
            }
            if (document.nextId <= 0L) {
                long highestId = document.cases.stream().mapToLong(CaseRecord::getId).max().orElse(0L);
                document.nextId = highestId + 1L;
            }
        } catch (IOException exception) {
            LoggerUtil.error("The JSON storage file could not be read.", exception);
            throw exception;
        }
    }

    @Override
    public synchronized CaseRecord createCase(CaseRecord record) throws Exception {
        CaseRecord stored = record.withId(document.nextId++);
        document.cases.add(stored);
        save();
        return stored;
    }

    @Override
    public synchronized Optional<CaseRecord> findCaseById(long caseId) {
        return document.cases.stream()
                .filter(record -> record.getId() == caseId)
                .findFirst();
    }

    @Override
    public synchronized Optional<CaseRecord> findLatestCaseForPlayer(UUID playerUniqueId) {
        return document.cases.stream()
                .filter(record -> record.isVisibleFor(playerUniqueId))
                .max(Comparator.comparingLong(CaseRecord::getCreatedAt));
    }

    @Override
    public synchronized Optional<CaseRecord> findActiveCaseForPlayer(UUID playerUniqueId, CaseType type) {
        return document.cases.stream()
                .filter(record -> type == record.getType())
                .filter(record -> playerUniqueId.equals(record.getTargetPlayerUniqueId()))
                .filter(record -> record.getStatus() == CaseStatus.ACTIVE)
                .max(Comparator.comparingLong(CaseRecord::getCreatedAt));
    }

    @Override
    public synchronized Optional<CaseRecord> findActiveCaseForIp(String ipAddress, CaseType type) {
        return document.cases.stream()
                .filter(record -> type == record.getType())
                .filter(record -> ipAddress.equalsIgnoreCase(record.getTargetIp()))
                .filter(record -> record.getStatus() == CaseStatus.ACTIVE)
                .max(Comparator.comparingLong(CaseRecord::getCreatedAt));
    }

    @Override
    public synchronized List<CaseRecord> getCasesForPlayer(UUID playerUniqueId, int limit, int offset) {
        return document.cases.stream()
                .filter(record -> record.isVisibleFor(playerUniqueId))
                .sorted(Comparator.comparingLong(CaseRecord::getCreatedAt).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized List<CaseRecord> getCasesForIp(String ipAddress, int limit, int offset) {
        return document.cases.stream()
                .filter(record -> ipAddress.equalsIgnoreCase(record.getTargetIp()))
                .sorted(Comparator.comparingLong(CaseRecord::getCreatedAt).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized List<CaseRecord> getCasesByTypeForPlayer(UUID playerUniqueId, CaseType type, int limit, int offset) {
        return document.cases.stream()
                .filter(record -> type == record.getType())
                .filter(record -> record.isVisibleFor(playerUniqueId))
                .sorted(Comparator.comparingLong(CaseRecord::getCreatedAt).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized List<CaseRecord> getRecentCases(int limit, int offset) {
        return document.cases.stream()
                .sorted(Comparator.comparingLong(CaseRecord::getCreatedAt).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized List<CaseRecord> getActiveAltFlags(UUID playerUniqueId) {
        return document.cases.stream()
                .filter(record -> record.getType() == CaseType.ALT_FLAG)
                .filter(record -> record.getStatus() == CaseStatus.ACTIVE)
                .filter(record -> record.isVisibleFor(playerUniqueId))
                .sorted(Comparator.comparingLong(CaseRecord::getCreatedAt).reversed())
                .toList();
    }

    @Override
    public synchronized int countVisibleCasesForPlayer(UUID playerUniqueId) {
        return (int) document.cases.stream()
                .filter(record -> record.isVisibleFor(playerUniqueId))
                .count();
    }

    @Override
    public synchronized int countCasesByTypeForPlayer(UUID playerUniqueId, CaseType type) {
        return (int) document.cases.stream()
                .filter(record -> type == record.getType())
                .filter(record -> record.isVisibleFor(playerUniqueId))
                .count();
    }

    @Override
    public synchronized int countActiveCases(CaseType type) {
        return (int) document.cases.stream()
                .filter(record -> type == record.getType())
                .filter(record -> record.getStatus() == CaseStatus.ACTIVE)
                .count();
    }

    @Override
    public synchronized int countAllCases() {
        return document.cases.size();
    }

    @Override
    public synchronized CaseRecord updateCaseStatus(long caseId,
                                                    CaseStatus newStatus,
                                                    long changedAt,
                                                    UUID changedByUniqueId,
                                                    String changedByName,
                                                    String note) throws Exception {
        for (int index = 0; index < document.cases.size(); index++) {
            CaseRecord current = document.cases.get(index);
            if (current.getId() != caseId) {
                continue;
            }

            CaseRecord updated = current.withStatus(newStatus, changedAt, changedByUniqueId, changedByName, note);
            document.cases.set(index, updated);
            save();
            return updated;
        }

        throw new IllegalArgumentException("No case record found for id " + caseId);
    }

    @Override
    public synchronized void upsertPlayerProfile(PlayerProfile profile) throws Exception {
        for (int index = 0; index < document.profiles.size(); index++) {
            PlayerProfile current = document.profiles.get(index);
            if (profile.getUniqueId().equals(current.getUniqueId())) {
                document.profiles.set(index, profile);
                save();
                return;
            }
        }

        document.profiles.add(profile);
        save();
    }

    @Override
    public synchronized Optional<PlayerProfile> findPlayerProfile(UUID playerUniqueId) {
        return document.profiles.stream()
                .filter(profile -> playerUniqueId.equals(profile.getUniqueId()))
                .findFirst();
    }

    @Override
    public synchronized List<PlayerProfile> findPlayerProfilesByIp(String ipAddress) {
        return document.profiles.stream()
                .filter(profile -> ipAddress.equalsIgnoreCase(profile.getLastIp()))
                .sorted(Comparator.comparingLong(PlayerProfile::getLastSeen).reversed())
                .toList();
    }

    @Override
    public synchronized List<PlayerProfile> searchProfilesByName(String input, int limit) {
        String filter = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return document.profiles.stream()
                .filter(profile -> profile.getLastName() != null)
                .filter(profile -> profile.getLastName().toLowerCase(Locale.ROOT).startsWith(filter))
                .sorted(Comparator.comparing(PlayerProfile::getLastName, String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized List<PlayerProfile> getKnownProfiles(int limit, int offset) {
        return document.profiles.stream()
                .sorted(Comparator.comparingLong(PlayerProfile::getLastSeen).reversed()
                        .thenComparing(PlayerProfile::getLastName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized int countKnownProfiles() {
        return document.profiles.size();
    }

    @Override
    public synchronized void close() {
    }

    private void save() throws IOException {
        try (Writer writer = Files.newBufferedWriter(file.toPath())) {
            gson.toJson(document, writer);
        }
    }

    private static final class JsonDatabaseDocument {
        private long nextId = 1L;
        private List<CaseRecord> cases = new ArrayList<>();
        private List<PlayerProfile> profiles = new ArrayList<>();
    }
}
