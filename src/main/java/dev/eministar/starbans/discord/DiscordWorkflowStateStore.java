package dev.eministar.starbans.discord;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.utils.LoggerUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DiscordWorkflowStateStore {

    private final StarBans plugin;
    private final File file;

    private final Map<Long, DiscordWorkflowRequest> requests = new LinkedHashMap<>();
    private final Map<String, String> panelMessages = new LinkedHashMap<>();

    public DiscordWorkflowStateStore(StarBans plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "discord-workflows.yml");
        reload();
    }

    public synchronized void reload() {
        requests.clear();
        panelMessages.clear();
        if (!file.exists()) {
            save();
            return;
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection requestsSection = configuration.getConfigurationSection("requests");
        if (requestsSection != null) {
            for (String key : requestsSection.getKeys(false)) {
                ConfigurationSection section = requestsSection.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                long caseId;
                try {
                    caseId = Long.parseLong(key);
                } catch (NumberFormatException exception) {
                    continue;
                }
                requests.put(caseId, readRequest(caseId, section));
            }
        }

        ConfigurationSection panelsSection = configuration.getConfigurationSection("panels");
        if (panelsSection != null) {
            for (String guildId : panelsSection.getKeys(false)) {
                ConfigurationSection guildSection = panelsSection.getConfigurationSection(guildId);
                if (guildSection == null) {
                    continue;
                }
                for (String channelId : guildSection.getKeys(false)) {
                    String messageId = guildSection.getString(channelId + ".message-id", "");
                    if (messageId != null && !messageId.isBlank()) {
                        panelMessages.put(panelKey(guildId, channelId), messageId.trim());
                    }
                }
            }
        }
    }

    public synchronized Optional<DiscordWorkflowRequest> getRequest(long caseId) {
        return Optional.ofNullable(requests.get(caseId));
    }

    public synchronized List<DiscordWorkflowRequest> getRequests() {
        return requests.values().stream()
                .sorted(Comparator.comparingLong(DiscordWorkflowRequest::updatedAt).reversed())
                .toList();
    }

    public synchronized List<DiscordWorkflowRequest> getRequests(DiscordWorkflowRequestKind kind) {
        List<DiscordWorkflowRequest> result = new ArrayList<>();
        for (DiscordWorkflowRequest request : requests.values()) {
            if (request.kind() == kind) {
                result.add(request);
            }
        }
        result.sort(Comparator.comparingLong(DiscordWorkflowRequest::updatedAt).reversed());
        return List.copyOf(result);
    }

    public synchronized void upsertRequest(DiscordWorkflowRequest request) {
        requests.put(request.caseId(), request);
        save();
    }

    public synchronized void removeRequest(long caseId) {
        if (requests.remove(caseId) != null) {
            save();
        }
    }

    public synchronized void markDecisionNotified(long caseId, long notifiedAt) {
        DiscordWorkflowRequest request = requests.get(caseId);
        if (request == null) {
            return;
        }
        requests.put(caseId, request.withDecisionNotified(notifiedAt));
        save();
    }

    public synchronized Optional<String> getPanelMessageId(String guildId, String channelId) {
        return Optional.ofNullable(panelMessages.get(panelKey(guildId, channelId)));
    }

    public synchronized void setPanelMessageId(String guildId, String channelId, String messageId) {
        String key = panelKey(guildId, channelId);
        if (messageId == null || messageId.isBlank()) {
            if (panelMessages.remove(key) != null) {
                save();
            }
            return;
        }
        panelMessages.put(key, messageId.trim());
        save();
    }

    private DiscordWorkflowRequest readRequest(long caseId, ConfigurationSection section) {
        return new DiscordWorkflowRequest(
                caseId,
                DiscordWorkflowRequestKind.fromConfig(section.getString("kind", "APPEAL")),
                DiscordWorkflowOrigin.fromConfig(section.getString("origin", "INGAME")),
                section.getString("requester-user-id", ""),
                section.getString("requester-display-name", ""),
                section.getString("guild-id", ""),
                section.getString("staff-channel-id", ""),
                section.getString("staff-message-id", ""),
                section.getLong("created-at", System.currentTimeMillis()),
                section.getLong("updated-at", System.currentTimeMillis()),
                section.contains("decision-notified-at") ? section.getLong("decision-notified-at") : null
        );
    }

    private synchronized void save() {
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                return;
            }

            YamlConfiguration configuration = new YamlConfiguration();
            configuration.options().header("""
                    StarBans Discord workflow state
                    This file is managed automatically.
                    """);

            for (DiscordWorkflowRequest request : requests.values()) {
                String path = "requests." + request.caseId();
                configuration.set(path + ".kind", request.kind().name());
                configuration.set(path + ".origin", request.origin().name());
                configuration.set(path + ".requester-user-id", request.requesterDiscordUserId());
                configuration.set(path + ".requester-display-name", request.requesterDisplayName());
                configuration.set(path + ".guild-id", request.guildId());
                configuration.set(path + ".staff-channel-id", request.staffChannelId());
                configuration.set(path + ".staff-message-id", request.staffMessageId());
                configuration.set(path + ".created-at", request.createdAt());
                configuration.set(path + ".updated-at", request.updatedAt());
                configuration.set(path + ".decision-notified-at", request.decisionNotifiedAt());
            }

            for (Map.Entry<String, String> entry : panelMessages.entrySet()) {
                String[] parts = entry.getKey().split(":", 2);
                if (parts.length != 2) {
                    continue;
                }
                configuration.set("panels." + parts[0] + '.' + parts[1] + ".message-id", entry.getValue());
            }

            configuration.save(file);
        } catch (Exception exception) {
            LoggerUtil.error("The Discord workflow state file could not be saved.", exception);
        }
    }

    private String panelKey(String guildId, String channelId) {
        return normalize(guildId) + ':' + normalize(channelId);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
