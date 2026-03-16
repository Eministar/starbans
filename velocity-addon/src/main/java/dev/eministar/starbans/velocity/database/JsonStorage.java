package dev.eministar.starbans.velocity.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.eministar.starbans.velocity.StarBansVelocityAddon;
import dev.eministar.starbans.velocity.model.CaseRecord;
import dev.eministar.starbans.velocity.model.CaseStatus;
import dev.eministar.starbans.velocity.model.CaseType;
import dev.eministar.starbans.velocity.model.PlayerProfile;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class JsonStorage implements VelocityStorage {

    private final StarBansVelocityAddon plugin;
    private final StorageSettings settings;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private Path file;
    private JsonDatabaseDocument document;

    public JsonStorage(StarBansVelocityAddon plugin, StorageSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    @Override
    public synchronized void init() throws Exception {
        file = plugin.getDataDirectory().resolve(settings.jsonFileName());
        if (Files.notExists(file.getParent())) {
            Files.createDirectories(file.getParent());
        }

        if (Files.notExists(file)) {
            document = new JsonDatabaseDocument();
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(file)) {
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
        }
    }

    @Override
    public synchronized Optional<CaseRecord> findActiveCaseForPlayer(UUID playerUniqueId, CaseType type) {
        return document.cases.stream()
                .filter(record -> record.getType() == type)
                .filter(record -> playerUniqueId.equals(record.getTargetPlayerUniqueId()))
                .filter(record -> record.getStatus() == CaseStatus.ACTIVE)
                .max(Comparator.comparingLong(CaseRecord::getCreatedAt));
    }

    @Override
    public synchronized Optional<CaseRecord> findActiveCaseForIp(String ipAddress, CaseType type) {
        return document.cases.stream()
                .filter(record -> record.getType() == type)
                .filter(record -> ipAddress.equalsIgnoreCase(record.getTargetIp()))
                .filter(record -> record.getStatus() == CaseStatus.ACTIVE)
                .max(Comparator.comparingLong(CaseRecord::getCreatedAt));
    }

    @Override
    public synchronized Optional<CaseRecord> findLatestCaseForPlayer(UUID playerUniqueId, CaseType type) {
        return document.cases.stream()
                .filter(record -> record.getType() == type)
                .filter(record -> playerUniqueId.equals(record.getTargetPlayerUniqueId()))
                .max(Comparator.comparingLong(CaseRecord::getCreatedAt));
    }

    @Override
    public synchronized Optional<CaseRecord> findCaseById(long caseId) {
        return document.cases.stream()
                .filter(record -> record.getId() == caseId)
                .findFirst();
    }

    @Override
    public synchronized Optional<PlayerProfile> findPlayerProfile(UUID playerUniqueId) {
        return document.profiles.stream()
                .filter(profile -> playerUniqueId.equals(profile.getUniqueId()))
                .findFirst();
    }

    @Override
    public synchronized Optional<PlayerProfile> findPlayerProfileByName(String playerName) {
        return document.profiles.stream()
                .filter(profile -> profile.getLastName() != null)
                .filter(profile -> profile.getLastName().equalsIgnoreCase(playerName))
                .max(Comparator.comparingLong(PlayerProfile::getLastSeen));
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

            CaseRecord updated = new CaseRecord(
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
            document.cases.set(index, updated);
            save();
            return updated;
        }

        throw new IllegalArgumentException("No case record found for id " + caseId);
    }

    @Override
    public synchronized void close() {
    }

    private void save() throws IOException {
        try (Writer writer = Files.newBufferedWriter(file)) {
            gson.toJson(document, writer);
        }
    }

    private static final class JsonDatabaseDocument {
        private List<CaseRecord> cases = new ArrayList<>();
        private List<PlayerProfile> profiles = new ArrayList<>();
    }
}
