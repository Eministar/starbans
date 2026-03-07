package dev.eministar.starbans.utils;

import dev.eministar.starbans.StarBans;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class UpdateChecker implements Listener {

    private final StarBans plugin;

    private volatile String currentVersion;
    private volatile String latestVersion;
    private volatile boolean updateAvailable;

    public UpdateChecker(StarBans plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        currentVersion = Version.get();
        latestVersion = null;
        updateAvailable = false;

        if (!plugin.getConfig().getBoolean("update-checker.enabled", true)) {
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String latest = fetchLatestVersion(plugin.getConfig().getString("update-checker.version-url", ""));
            if (latest == null || latest.isBlank() || !isNewerVersion(latest, currentVersion)) {
                return;
            }

            latestVersion = latest;
            updateAvailable = true;

            for (String line : plugin.getLang().getList(
                    "messages.update.console",
                    "current", currentVersion,
                    "latest", latestVersion,
                    "url", plugin.getConfig().getString("update-checker.download-url", "")
            )) {
                LoggerUtil.info(line);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!updateAvailable || !plugin.getConfig().getBoolean("update-checker.notify-on-join", true)) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("starbans.notify")) {
            return;
        }

        List<String> lines = plugin.getLang().getList(
                "messages.update.player",
                "current", currentVersion,
                "latest", latestVersion,
                "url", plugin.getConfig().getString("update-checker.download-url", "")
        );
        for (String line : lines) {
            player.sendMessage(line);
        }
    }

    private String fetchLatestVersion(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "StarBans-UpdateChecker");

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                return line == null ? null : line.trim();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isNewerVersion(String latest, String current) {
        String[] latestParts = latest.split("\\.");
        String[] currentParts = current.split("\\.");
        int length = Math.max(latestParts.length, currentParts.length);
        for (int index = 0; index < length; index++) {
            int latestValue = parseVersionPart(latestParts, index);
            int currentValue = parseVersionPart(currentParts, index);
            if (latestValue > currentValue) {
                return true;
            }
            if (latestValue < currentValue) {
                return false;
            }
        }
        return !latest.equalsIgnoreCase(current);
    }

    private int parseVersionPart(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index].replaceAll("[^0-9]", ""));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
