package dev.eministar.starbans.service;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.gui.AdminGuiFactory;
import dev.eministar.starbans.model.CommandActor;
import dev.eministar.starbans.model.PlayerIdentity;
import dev.eministar.starbans.utils.LoggerUtil;
import dev.eministar.starbans.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiInputService {

    private final StarBans plugin;
    private final Map<UUID, NotePrompt> notePrompts = new ConcurrentHashMap<>();

    public GuiInputService(StarBans plugin) {
        this.plugin = plugin;
    }

    public void startNotePrompt(Player player, PlayerIdentity target, int returnPage) {
        notePrompts.put(player.getUniqueId(), new NotePrompt(target, returnPage));
        player.closeInventory();
        player.sendMessage(plugin.getLang().prefixed("messages.gui-note-prompt-start", "player", target.name()));
        SoundUtil.play(plugin, player, "gui.prompt");
    }

    public boolean hasPendingPrompt(UUID playerUniqueId) {
        return notePrompts.containsKey(playerUniqueId);
    }

    public void handleChat(Player player, String message) {
        NotePrompt prompt = notePrompts.remove(player.getUniqueId());
        if (prompt == null) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> processNotePrompt(player, prompt, message));
    }

    private void processNotePrompt(Player player, NotePrompt prompt, String message) {
        if (message == null || message.isBlank() || message.equalsIgnoreCase("cancel")) {
            player.sendMessage(plugin.getLang().prefixed("messages.gui-note-prompt-cancelled", "player", prompt.target().name()));
            SoundUtil.play(plugin, player, "gui.error");
            AdminGuiFactory.openActionMenu(plugin, player, prompt.target(), prompt.returnPage());
            return;
        }

        try {
            plugin.getModerationService().addNote(
                    prompt.target(),
                    CommandActor.fromSender(player),
                    plugin.getConfig().getString("gui.action-menu.add-note.default-label", plugin.getConfig().getString("notes.default-label", "general-note")),
                    message,
                    "GUI:NOTE-PROMPT"
            );
            player.sendMessage(plugin.getLang().prefixed("messages.note-success", "player", prompt.target().name(), "label", plugin.getConfig().getString("gui.action-menu.add-note.default-label", plugin.getConfig().getString("notes.default-label", "general-note"))));
            SoundUtil.play(plugin, player, "gui.success");
        } catch (Exception exception) {
            LoggerUtil.error("The GUI note prompt failed.", exception);
            player.sendMessage(plugin.getLang().prefixed("messages.internal-error"));
            SoundUtil.play(plugin, player, "gui.error");
        }

        AdminGuiFactory.openActionMenu(plugin, player, prompt.target(), prompt.returnPage());
    }

    private record NotePrompt(PlayerIdentity target, int returnPage) {
    }
}
