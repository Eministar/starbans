package dev.eministar.starbans.service;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.model.PlayerIdentity;
import dev.eministar.starbans.model.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PlayerLookupService {

    private final StarBans plugin;

    public PlayerLookupService(StarBans plugin) {
        this.plugin = plugin;
    }

    public Optional<PlayerIdentity> resolve(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return Optional.of(new PlayerIdentity(online.getUniqueId(), online.getName()));
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(input)) {
                return Optional.of(new PlayerIdentity(player.getUniqueId(), player.getName()));
            }
        }

        try {
            UUID uniqueId = UUID.fromString(input);
            Optional<PlayerProfile> profile = plugin.getModerationService().getProfile(uniqueId);
            if (profile.isPresent()) {
                return Optional.of(new PlayerIdentity(profile.get().getUniqueId(), profile.get().getLastName()));
            }
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uniqueId);
            if (offlinePlayer.getName() != null) {
                return Optional.of(new PlayerIdentity(offlinePlayer.getUniqueId(), offlinePlayer.getName()));
            }
        } catch (Exception ignored) {
        }

        try {
            for (PlayerProfile profile : plugin.getModerationService().searchKnownProfiles(input, 5)) {
                if (profile.getLastName() != null && profile.getLastName().equalsIgnoreCase(input)) {
                    return Optional.of(new PlayerIdentity(profile.getUniqueId(), profile.getLastName()));
                }
            }
        } catch (Exception ignored) {
        }

        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            if (offlinePlayer.getName() != null && offlinePlayer.getName().equalsIgnoreCase(input)) {
                return Optional.of(new PlayerIdentity(offlinePlayer.getUniqueId(), offlinePlayer.getName()));
            }
        }

        return Optional.empty();
    }

    public List<String> suggestNames(String prefix) {
        String filter = prefix == null ? "" : prefix.toLowerCase();
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(filter)) {
                suggestions.add(player.getName());
            }
        }

        try {
            for (PlayerProfile profile : plugin.getModerationService().searchKnownProfiles(prefix == null ? "" : prefix, 20)) {
                if (profile.getLastName() != null && profile.getLastName().toLowerCase().startsWith(filter)) {
                    suggestions.add(profile.getLastName());
                }
            }
        } catch (Exception ignored) {
        }

        return new ArrayList<>(suggestions);
    }
}
