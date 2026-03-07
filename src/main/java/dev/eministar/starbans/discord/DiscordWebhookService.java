package dev.eministar.starbans.discord;

import com.google.gson.Gson;
import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.utils.ColorUtil;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DiscordWebhookService {

    private final StarBans plugin;
    private final Gson gson = new Gson();

    public DiscordWebhookService(StarBans plugin) {
        this.plugin = plugin;
    }

    public void send(String actionKey, Object... replacements) {
        if (!plugin.getConfig().getBoolean("discord-webhooks.enabled", false)) {
            return;
        }

        String actionPath = "discord-webhooks.actions." + actionKey;
        if (!plugin.getConfig().getBoolean(actionPath + ".enabled", false)) {
            return;
        }

        String url = plugin.getConfig().getString(actionPath + ".url", plugin.getConfig().getString("discord-webhooks.default-url", ""));
        if (url == null || url.isBlank()) {
            return;
        }

        List<String> lines = plugin.getConfig().getStringList(actionPath + ".content");
        if (lines.isEmpty()) {
            String single = plugin.getConfig().getString(actionPath + ".content-single", "");
            if (single != null && !single.isBlank()) {
                lines = List.of(single);
            }
        }
        if (lines.isEmpty()) {
            return;
        }

        String username = plugin.getConfig().getString(actionPath + ".username", plugin.getConfig().getString("discord-webhooks.username", "StarBans"));
        String avatarUrl = plugin.getConfig().getString(actionPath + ".avatar-url", plugin.getConfig().getString("discord-webhooks.avatar-url", ""));
        String content = String.join("\n", lines.stream().map(line -> ColorUtil.replace(line, replacements)).toList());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", username);
        payload.put("content", content);
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            payload.put("avatar_url", avatarUrl);
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> post(url, payload));
    }

    private void post(String targetUrl, Map<String, Object> payload) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(targetUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setConnectTimeout(plugin.getConfig().getInt("discord-webhooks.timeout-ms", 5000));
            connection.setReadTimeout(plugin.getConfig().getInt("discord-webhooks.timeout-ms", 5000));

            byte[] body = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body);
            }
            connection.getInputStream().close();
        } catch (Exception ignored) {
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
