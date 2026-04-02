package dev.eministar.starbans.listener;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.model.CaseRecord;
import dev.eministar.starbans.utils.LoggerUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class QuarantineListener implements Listener {

    private final StarBans plugin;

    public QuarantineListener(StarBans plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        try {
            Optional<CaseRecord> quarantine = plugin.getModerationService().getActiveQuarantine(event.getPlayer().getUniqueId());
            if (quarantine.isEmpty()) {
                return;
            }

            String label = extractLabel(event.getMessage());
            if (isAllowed(label)) {
                return;
            }

            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getLang().prefixed(
                    "messages.quarantine-command-blocked",
                    "command", label,
                    "reason", quarantine.get().getReason(),
                    "remaining", plugin.getModerationService().formatRemaining(quarantine.get())
            ));
        } catch (Exception exception) {
            LoggerUtil.error("The quarantine command check failed.", exception);
        }
    }

    private boolean isAllowed(String label) {
        List<String> allowed = plugin.getConfig().getStringList("quarantine.allowed-commands");
        for (String entry : allowed) {
            if (entry == null || entry.isBlank()) {
                continue;
            }

            String normalized = normalize(entry);
            if (normalized.equals(label)) {
                return true;
            }
        }
        return false;
    }

    private String extractLabel(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }

        String raw = message.startsWith("/") ? message.substring(1) : message;
        int separator = raw.indexOf(' ');
        return normalize(separator < 0 ? raw : raw.substring(0, separator));
    }

    private String normalize(String input) {
        String value = input.startsWith("/") ? input.substring(1) : input;
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
