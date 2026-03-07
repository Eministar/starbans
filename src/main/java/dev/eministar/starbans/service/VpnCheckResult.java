package dev.eministar.starbans.service;

public record VpnCheckResult(boolean flagged, int risk, String provider, String details) {
}
