package dev.eministar.starbans.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.eministar.starbans.StarBans;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class VpnCheckService {

    private final StarBans plugin;

    public VpnCheckService(StarBans plugin) {
        this.plugin = plugin;
    }

    public VpnCheckResult check(String ipAddress) {
        if (!plugin.getConfig().getBoolean("security.vpn-detection.enabled", false)) {
            return new VpnCheckResult(false, 0, "NONE", "disabled");
        }

        String provider = plugin.getConfig().getString("security.vpn-detection.provider", "PROXYCHECK");
        if (!"PROXYCHECK".equalsIgnoreCase(provider)) {
            return new VpnCheckResult(false, 0, provider, "unsupported-provider");
        }

        HttpURLConnection connection = null;
        try {
            String apiKey = plugin.getConfig().getString("security.vpn-detection.api-key", "");
            String url = "https://proxycheck.io/v2/" + ipAddress + "?vpn=1&risk=1&asn=1&node=1&time=1";
            if (apiKey != null && !apiKey.isBlank()) {
                url += "&key=" + apiKey;
            }

            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(plugin.getConfig().getInt("security.vpn-detection.timeout-ms", 3000));
            connection.setReadTimeout(plugin.getConfig().getInt("security.vpn-detection.timeout-ms", 3000));
            connection.setRequestProperty("User-Agent", "StarBans-VpnCheck");

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return parseProxyCheck(ipAddress, response.toString());
            }
        } catch (Exception exception) {
            return new VpnCheckResult(false, 0, provider, "request-failed");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private VpnCheckResult parseProxyCheck(String ipAddress, String rawJson) {
        JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();
        JsonElement node = root.get(ipAddress);
        if (node == null || !node.isJsonObject()) {
            return new VpnCheckResult(false, 0, "PROXYCHECK", "no-node");
        }

        JsonObject data = node.getAsJsonObject();
        boolean flagged = "yes".equalsIgnoreCase(stringValue(data, "proxy"))
                || "yes".equalsIgnoreCase(stringValue(data, "vpn"));
        int risk = intValue(data, "risk");
        String provider = stringValue(data, "provider");
        String details = stringValue(data, "type") + ", risk=" + risk + ", provider=" + provider;
        return new VpnCheckResult(flagged, risk, "PROXYCHECK", details);
    }

    private String stringValue(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        return element.getAsString();
    }

    private int intValue(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return 0;
        }
        try {
            return element.getAsInt();
        } catch (Exception ignored) {
            try {
                return Integer.parseInt(element.getAsString());
            } catch (Exception ignoredAgain) {
                return 0;
            }
        }
    }
}
