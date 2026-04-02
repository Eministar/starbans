package dev.eministar.starbans.model;

public record ImportSummary(String source, int importedCases, int importedProfiles, int skippedRows) {
}
