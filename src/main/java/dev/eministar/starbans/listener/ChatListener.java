package dev.eministar.starbans.listener;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.model.CaseRecord;
import dev.eministar.starbans.utils.LoggerUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Optional;

public final class ChatListener implements Listener {

    private final StarBans plugin;

    public ChatListener(StarBans plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        try {
            if (plugin.getGuiInputService().hasPendingPrompt(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                plugin.getGuiInputService().handleChat(event.getPlayer(), event.getMessage());
                return;
            }

            Optional<CaseRecord> quarantine = plugin.getModerationService().getActiveQuarantine(event.getPlayer().getUniqueId());
            if (quarantine.isPresent()) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(plugin.getLang().prefixed(
                        "messages.quarantine-chat-blocked",
                        "reason", quarantine.get().getReason(),
                        "remaining", plugin.getModerationService().formatRemaining(quarantine.get())
                ));
                return;
            }

            Optional<CaseRecord> mute = plugin.getModerationService().getActivePlayerMute(event.getPlayer().getUniqueId());
            if (mute.isEmpty()) {
                return;
            }

            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getModerationService().buildMuteMessage(mute.get()));
        } catch (Exception exception) {
            LoggerUtil.error("The chat moderation check failed.", exception);
        }
    }
}
