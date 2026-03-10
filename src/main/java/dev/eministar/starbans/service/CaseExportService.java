package dev.eministar.starbans.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.model.CaseRecord;
import dev.eministar.starbans.model.PlayerIdentity;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CaseExportService {

    private final StarBans plugin;
    private final ModerationService moderationService;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public CaseExportService(StarBans plugin, ModerationService moderationService) {
        this.plugin = plugin;
        this.moderationService = moderationService;
    }

    public File exportPlayerCases(PlayerIdentity target, String format) throws Exception {
        String normalizedFormat = format == null ? "txt" : format.trim().toLowerCase();
        int totalCases = Math.max(1, moderationService.countPlayerCases(target.uniqueId()));
        List<CaseRecord> cases = moderationService.getPlayerCases(target.uniqueId(), totalCases + 25, 0);

        File directory = new File(plugin.getDataFolder(), "exports");
        Files.createDirectories(directory.toPath());

        String baseName = safeName(target.name()) + "-" + System.currentTimeMillis();
        File output = new File(directory, baseName + "." + normalizedFormat);
        if (normalizedFormat.equals("json")) {
            writeJson(output, target, cases);
        } else {
            writeText(output, target, cases);
        }
        return output;
    }

    private void writeJson(File output, PlayerIdentity target, List<CaseRecord> cases) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("player", target.name());
        payload.put("playerUuid", target.uniqueId());
        payload.put("exportedAt", Instant.now().toString());
        payload.put("caseCount", cases.size());
        payload.put("cases", cases);
        Files.writeString(output.toPath(), gson.toJson(payload), StandardCharsets.UTF_8);
    }

    private void writeText(File output, PlayerIdentity target, List<CaseRecord> cases) throws Exception {
        StringBuilder builder = new StringBuilder();
        builder.append("StarBans Export").append(System.lineSeparator());
        builder.append("Player: ").append(target.name()).append(System.lineSeparator());
        builder.append("UUID: ").append(target.uniqueId()).append(System.lineSeparator());
        builder.append("Exported: ").append(Instant.now()).append(System.lineSeparator());
        builder.append("Cases: ").append(cases.size()).append(System.lineSeparator()).append(System.lineSeparator());

        for (CaseRecord record : cases) {
            builder.append('#').append(record.getId()).append(' ')
                    .append(record.getType().name()).append(" | ")
                    .append(record.getStatus().name()).append(" | ")
                    .append(record.getReason()).append(System.lineSeparator());
            builder.append("Actor: ").append(record.getActorName()).append(System.lineSeparator());
            builder.append("Created: ").append(moderationService.formatDate(record.getCreatedAt())).append(System.lineSeparator());
            builder.append("Expires: ").append(moderationService.formatExpiry(record)).append(System.lineSeparator());
            builder.append("Tags: ").append(record.getTagsDisplay().isBlank() ? "-" : record.getTagsDisplay()).append(System.lineSeparator());
            builder.append("Category: ").append(record.getCategory() == null ? "-" : record.getCategory()).append(System.lineSeparator());
            builder.append("Visibility: ").append(record.getVisibility().name()).append(System.lineSeparator());
            builder.append("Points: ").append(record.getPoints()).append(System.lineSeparator());
            builder.append(System.lineSeparator());
        }

        Files.writeString(output.toPath(), builder.toString(), StandardCharsets.UTF_8);
    }

    private String safeName(String input) {
        if (input == null || input.isBlank()) {
            return "unknown-player";
        }
        return input.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
