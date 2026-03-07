package dev.eministar.starbans.model;

import java.util.UUID;

public record PlayerIdentity(UUID uniqueId, String name) {
}
