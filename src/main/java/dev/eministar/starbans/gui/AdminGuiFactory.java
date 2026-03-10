package dev.eministar.starbans.gui;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.model.CaseRecord;
import dev.eministar.starbans.model.CommandActor;
import dev.eministar.starbans.model.ModerationActionResult;
import dev.eministar.starbans.model.ModerationActionType;
import dev.eministar.starbans.model.PlayerIdentity;
import dev.eministar.starbans.model.PlayerProfile;
import dev.eministar.starbans.model.PlayerSummary;
import dev.eministar.starbans.model.PluginStats;
import dev.eministar.starbans.service.TimeUtil;
import dev.eministar.starbans.utils.LoggerUtil;
import dev.eministar.starbans.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.IntConsumer;

public final class AdminGuiFactory {

    private AdminGuiFactory() {
    }

    public static void openMainMenu(StarBans plugin, Player viewer) {
        if (!ensurePermission(plugin, viewer, "starbans.gui.open")) {
            return;
        }
        try {
            int size = normalizeSize(plugin.getConfig().getInt("gui.main-menu.size", 27));
            InteractiveGui gui = new InteractiveGui(size, plugin.getLang().get("gui.main-menu.title"));
            fillWithFiller(plugin, gui);

            PluginStats stats = plugin.getModerationService().getStats();
            int knownPlayers = plugin.getModerationService().countKnownProfiles();

            int browserSlot = plugin.getConfig().getInt("gui.main-menu.slots.browser", 11);
            placeStaticItem(plugin, gui, browserSlot, "gui.main-menu.browser", "known_players", knownPlayers, "case_count", stats.totalCases());
            gui.setAction(browserSlot, event -> openPlayerBrowser(plugin, viewer, 0));

            int activitySlot = plugin.getConfig().getInt("gui.main-menu.slots.activity", 13);
            placeStaticItem(plugin, gui, activitySlot, "gui.main-menu.activity", "case_count", stats.totalCases());
            gui.setAction(activitySlot, event -> openRecentActivity(plugin, viewer, 0));

            placeStaticItem(
                    plugin,
                    gui,
                    plugin.getConfig().getInt("gui.main-menu.slots.stats", 15),
                    "gui.main-menu.stats",
                    "known_players", knownPlayers,
                    "ban_count", stats.activeBans(),
                    "ip_ban_count", stats.activeIpBans(),
                    "mute_count", stats.activeMutes(),
                    "warn_count", stats.activeWarns(),
                    "watchlist_count", stats.activeWatchlists(),
                    "case_count", stats.totalCases()
            );
            placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.main-menu.slots.close", 22), "gui.main-menu.close", event -> viewer.closeInventory());
            viewer.openInventory(gui.getInventory());
            SoundUtil.play(plugin, viewer, "gui.open");
        } catch (Exception exception) {
            LoggerUtil.error("The main moderation GUI could not be opened.", exception);
            viewer.sendMessage(plugin.getLang().prefixed("messages.internal-error"));
            SoundUtil.play(plugin, viewer, "gui.error");
        }
    }

    public static void openPlayerBrowser(StarBans plugin, Player viewer, int requestedPage) {
        if (!ensurePermission(plugin, viewer, "starbans.gui.browser")) {
            return;
        }
        List<Integer> playerSlots = plugin.getConfig().getIntegerList("gui.player-browser.player-slots");
        if (playerSlots.isEmpty()) {
            viewer.sendMessage(plugin.getLang().prefixed("messages.gui-misconfigured"));
            return;
        }

        try {
            int totalEntries = plugin.getModerationService().countKnownProfiles();
            int maxPage = Math.max(1, (int) Math.ceil(totalEntries / (double) playerSlots.size()));
            int page = clampPage(requestedPage, maxPage);
            List<PlayerProfile> profiles = plugin.getModerationService().getKnownProfiles(playerSlots.size(), page);

            int size = normalizeSize(plugin.getConfig().getInt("gui.player-browser.size", 54));
            InteractiveGui gui = new InteractiveGui(
                    size,
                    plugin.getLang().get("gui.player-browser.title", "page", page + 1, "max_page", maxPage)
            );
            fillWithFiller(plugin, gui);

            for (int index = 0; index < profiles.size() && index < playerSlots.size(); index++) {
                PlayerProfile profile = profiles.get(index);
                PlayerIdentity identity = new PlayerIdentity(profile.getUniqueId(), defaultName(plugin, profile.getLastName()));
                PlayerSummary summary = plugin.getModerationService().getPlayerSummary(identity);
                boolean online = Bukkit.getPlayer(profile.getUniqueId()) != null;
                String status = buildStatus(plugin, summary);

                ItemStack head = GuiItemFactory.create(plugin, 
                        plugin.getConfig().getConfigurationSection("gui.player-browser.player-head"),
                        Bukkit.getOfflinePlayer(profile.getUniqueId()),
                        "player", identity.name(),
                        "status", status,
                        "online_status", online ? plugin.getLang().get("labels.online") : plugin.getLang().get("labels.offline"),
                        "case_count", summary.visibleCaseCount(),
                        "note_count", summary.noteCount(),
                        "alt_count", summary.altFlagCount(),
                        "warn_count", summary.warnCount(),
                        "warning_points", summary.warningPoints(),
                        "last_ip", defaultText(plugin, summary.lastKnownIp()),
                        "last_seen", formatProfileTime(plugin, profile.getLastSeen())
                );
                int slot = playerSlots.get(index);
                gui.getInventory().setItem(slot, head);
                gui.setAction(slot, event -> openActionMenu(plugin, viewer, identity, page));
            }

            setNavigation(plugin, gui, page, maxPage, "gui.player-browser.navigation.previous", "gui.player-browser.navigation.next", requested -> openPlayerBrowser(plugin, viewer, requested));
            placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.player-browser.navigation.home-slot", 46), "gui.player-browser.navigation.home", event -> openMainMenu(plugin, viewer));
            placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.player-browser.navigation.close-slot", 49), "gui.player-browser.navigation.close", event -> viewer.closeInventory());
            viewer.openInventory(gui.getInventory());
            SoundUtil.play(plugin, viewer, page == requestedPage ? "gui.open" : "gui.navigate");
        } catch (Exception exception) {
            LoggerUtil.error("The player browser could not be opened.", exception);
            viewer.sendMessage(plugin.getLang().prefixed("messages.internal-error"));
            SoundUtil.play(plugin, viewer, "gui.error");
        }
    }

    public static void openActionMenu(StarBans plugin, Player viewer, PlayerIdentity target) {
        openActionMenu(plugin, viewer, target, 0);
    }

    public static void openActionMenu(StarBans plugin, Player viewer, PlayerIdentity target, int returnPage) {
        if (!ensurePermission(plugin, viewer, "starbans.gui.profile")) {
            return;
        }
        PlayerSummary summary;
        Optional<PlayerProfile> profile;
        try {
            summary = plugin.getModerationService().getPlayerSummary(target);
            profile = plugin.getModerationService().getProfile(target.uniqueId());
        } catch (Exception exception) {
            LoggerUtil.error("The action menu could not load the target summary.", exception);
            viewer.sendMessage(plugin.getLang().prefixed("messages.internal-error"));
            SoundUtil.play(plugin, viewer, "gui.error");
            return;
        }

        int size = normalizeSize(plugin.getConfig().getInt("gui.action-menu.size", 54));
        InteractiveGui gui = new InteractiveGui(size, plugin.getLang().get("gui.action-menu.title", "player", target.name()));
        fillWithFiller(plugin, gui);

        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(target.uniqueId());
        boolean online = Bukkit.getPlayer(target.uniqueId()) != null;
        String banStatus = summary.activeBan() == null ? plugin.getLang().get("labels.none") : summary.activeBan().getReason();
        String muteStatus = summary.activeMute() == null ? plugin.getLang().get("labels.none") : summary.activeMute().getReason();
        String watchlistStatus = summary.activeWatchlist() == null ? plugin.getLang().get("labels.none") : summary.activeWatchlist().getReason();
        String lastCaseType = summary.latestCase() == null ? plugin.getLang().get("labels.none") : plugin.getModerationService().formatCaseType(summary.latestCase().getType());

        gui.getInventory().setItem(
                plugin.getConfig().getInt("gui.action-menu.slots.header", 13),
                GuiItemFactory.create(plugin, 
                        plugin.getConfig().getConfigurationSection("gui.action-menu.header"),
                        offlineTarget,
                        "player", target.name(),
                        "ban_status", banStatus,
                        "mute_status", muteStatus,
                        "watchlist_status", watchlistStatus,
                        "case_count", summary.visibleCaseCount(),
                        "note_count", summary.noteCount(),
                        "alt_count", summary.altFlagCount(),
                        "warn_count", summary.warnCount(),
                        "warning_points", summary.warningPoints(),
                        "last_case_type", lastCaseType,
                        "last_ip", defaultText(plugin, summary.lastKnownIp()),
                        "online_status", online ? plugin.getLang().get("labels.online") : plugin.getLang().get("labels.offline"),
                        "first_seen", profile.map(PlayerProfile::getFirstSeen).map(value -> formatProfileTime(plugin, value)).orElse(plugin.getLang().get("labels.none")),
                        "last_seen", profile.map(PlayerProfile::getLastSeen).map(value -> formatProfileTime(plugin, value)).orElse(plugin.getLang().get("labels.none"))
                )
        );

        placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.action-menu.slots.permanent-ban", 29), "gui.action-menu.permanent-ban", event -> {
            if (!ensurePermission(plugin, viewer, "starbans.command.ban", "starbans.gui.punish.ban")) {
                return;
            }
            try {
                ModerationActionResult result = plugin.getModerationService().banPlayer(
                        target,
                        CommandActor.fromSender(viewer),
                        plugin.getConfig().getString("gui.action-menu.permanent-ban.reason", plugin.getConfig().getString("punishments.defaults.ban-reason", "No reason specified.")),
                        null,
                        "GUI:PERMANENT"
                );
                handleActionResult(plugin, viewer, result, "messages.ban-success", "messages.already-banned", target.name());
                openActionMenu(plugin, viewer, target, returnPage);
            } catch (Exception exception) {
                LoggerUtil.error("The permanent ban action failed.", exception);
                viewer.sendMessage(plugin.getLang().prefixed("messages.internal-error"));
            }
        });

        placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.action-menu.slots.unban", 33), "gui.action-menu.unban", event -> {
            if (!ensurePermission(plugin, viewer, "starbans.command.unban", "starbans.gui.punish.unban")) {
                return;
            }
            try {
                ModerationActionResult result = plugin.getModerationService().unbanPlayer(target, CommandActor.fromSender(viewer), plugin.getConfig().getString("gui.action-menu.unban.reason", "GUI unban"));
                handleRemoval(plugin, viewer, result, "messages.unban-success", "messages.not-banned", target.name());
                openActionMenu(plugin, viewer, target, returnPage);
            } catch (Exception exception) {
                LoggerUtil.error("The unban action failed.", exception);
                viewer.sendMessage(plugin.getLang().prefixed("messages.internal-error"));
            }
        });

        placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.action-menu.slots.permanent-mute", 31), "gui.action-menu.permanent-mute", event -> {
            if (!ensurePermission(plugin, viewer, "starbans.command.mute", "starbans.gui.punish.mute")) {
                return;
            }
            try {
                ModerationActionResult result = plugin.getModerationService().mutePlayer(
                        target,
                        CommandActor.fromSender(viewer),
                        plugin.getConfig().getString("gui.action-menu.permanent-mute.reason", plugin.getConfig().getString("punishments.defaults.mute-reason", "No reason specified.")),
                        null,
                        "GUI:PERMANENT-MUTE"
                );
                handleActionResult(plugin, viewer, result, "messages.mute-success", "messages.already-muted", target.name());
                openActionMenu(plugin, viewer, target, returnPage);
            } catch (Exception exception) {
                LoggerUtil.error("The permanent mute action failed.", exception);
                viewer.sendMessage(plugin.getLang().prefixed("messages.internal-error"));
            }
        });

        placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.action-menu.slots.unmute", 35), "gui.action-menu.unmute", event -> {
            if (!ensurePermission(plugin, viewer, "starbans.command.unmute", "starbans.gui.punish.unmute")) {
                return;
            }
            try {
                ModerationActionResult result = plugin.getModerationService().unmutePlayer(target, CommandActor.fromSender(viewer), plugin.getConfig().getString("gui.action-menu.unmute.reason", "GUI unmute"));
                handleRemoval(plugin, viewer, result, "messages.unmute-success", "messages.not-muted", target.name());
                openActionMenu(plugin, viewer, target, returnPage);
            } catch (Exception exception) {
                LoggerUtil.error("The unmute action failed.", exception);
                viewer.sendMessage(plugin.getLang().prefixed("messages.internal-error"));
            }
        });

        placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.action-menu.slots.history", 49), "gui.action-menu.history", event -> openHistory(plugin, viewer, target, 0, returnPage));
        placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.action-menu.slots.notes", 39), "gui.action-menu.notes", event -> openNotes(plugin, viewer, target, 0, returnPage));
        placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.action-menu.slots.related", 43), "gui.action-menu.related", event -> openRelatedProfiles(plugin, viewer, target, 0, returnPage));
        placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.action-menu.slots.add-note", 41), "gui.action-menu.add-note", event -> {
            if (!ensurePermission(plugin, viewer, "starbans.command.note", "starbans.gui.notes.create")) {
                return;
            }
            plugin.getGuiInputService().startNotePrompt(viewer, target, returnPage);
        });
        placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.action-menu.slots.back", 45), "gui.action-menu.back", event -> openPlayerBrowser(plugin, viewer, returnPage));
        placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.action-menu.slots.close", 53), "gui.action-menu.close", event -> viewer.closeInventory());

        attachTimedPresets(plugin, gui, viewer, target, returnPage, "gui.action-menu.temp-ban-presets", true);
        attachTimedPresets(plugin, gui, viewer, target, returnPage, "gui.action-menu.temp-mute-presets", false);

        viewer.openInventory(gui.getInventory());
        SoundUtil.play(plugin, viewer, "gui.open");
    }

    public static void openHistory(StarBans plugin, Player viewer, PlayerIdentity target, int requestedPage, int returnPage) {
        if (!ensurePermission(plugin, viewer, "starbans.command.cases", "starbans.gui.history")) {
            return;
        }
        List<Integer> entrySlots = plugin.getConfig().getIntegerList("gui.history.entry-slots");
        if (entrySlots.isEmpty()) {
            viewer.sendMessage(plugin.getLang().prefixed("messages.gui-misconfigured"));
            return;
        }

        try {
            int totalEntries = plugin.getModerationService().countPlayerCases(target.uniqueId());
            int maxPage = Math.max(1, (int) Math.ceil(totalEntries / (double) entrySlots.size()));
            int page = clampPage(requestedPage, maxPage);
            List<CaseRecord> history = plugin.getModerationService().getPlayerCases(target.uniqueId(), entrySlots.size(), page);

            InteractiveGui gui = new InteractiveGui(
                    normalizeSize(plugin.getConfig().getInt("gui.history.size", 54)),
                    plugin.getLang().get("gui.history.title", "player", target.name(), "page", page + 1, "max_page", maxPage)
            );
            fillWithFiller(plugin, gui);
            gui.getInventory().setItem(
                    plugin.getConfig().getInt("gui.history.slots.header", 4),
                    GuiItemFactory.create(plugin, 
                            plugin.getConfig().getConfigurationSection("gui.history.header"),
                            Bukkit.getOfflinePlayer(target.uniqueId()),
                            "player", target.name(),
                            "case_count", totalEntries
                    )
            );

            for (int index = 0; index < history.size() && index < entrySlots.size(); index++) {
                CaseRecord record = history.get(index);
                int slot = entrySlots.get(index);
                gui.getInventory().setItem(slot, createCaseItem(plugin, record));
                gui.setAction(slot, event -> openCaseDetails(plugin, viewer, record.getId(), () -> openHistory(plugin, viewer, target, page, returnPage)));
            }

            setNavigation(plugin, gui, page, maxPage, "gui.history.previous", "gui.history.next", requested -> openHistory(plugin, viewer, target, requested, returnPage));
            placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.history.slots.back", 45), "gui.history.back", event -> openActionMenu(plugin, viewer, target, returnPage));
            placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.history.slots.close", 49), "gui.history.close", event -> viewer.closeInventory());
            viewer.openInventory(gui.getInventory());
            SoundUtil.play(plugin, viewer, requestedPage == page ? "gui.open" : "gui.navigate");
        } catch (Exception exception) {
            LoggerUtil.error("The history menu could not be opened.", exception);
            viewer.sendMessage(plugin.getLang().prefixed("messages.internal-error"));
            SoundUtil.play(plugin, viewer, "gui.error");
        }
    }

    public static void openNotes(StarBans plugin, Player viewer, PlayerIdentity target, int requestedPage, int returnPage) {
        if (!ensurePermission(plugin, viewer, "starbans.command.notes", "starbans.gui.notes.view")) {
            return;
        }
        List<Integer> entrySlots = plugin.getConfig().getIntegerList("gui.notes.entry-slots");
        if (entrySlots.isEmpty()) {
            viewer.sendMessage(plugin.getLang().prefixed("messages.gui-misconfigured"));
            return;
        }

        try {
            int totalEntries = plugin.getModerationService().getPlayerSummary(target).noteCount();
            int maxPage = Math.max(1, (int) Math.ceil(totalEntries / (double) entrySlots.size()));
            int page = clampPage(requestedPage, maxPage);
            List<CaseRecord> notes = plugin.getModerationService().getPlayerNotes(target.uniqueId(), entrySlots.size(), page);

            InteractiveGui gui = new InteractiveGui(
                    normalizeSize(plugin.getConfig().getInt("gui.notes.size", 54)),
                    plugin.getLang().get("gui.notes.title", "player", target.name(), "page", page + 1, "max_page", maxPage)
            );
            fillWithFiller(plugin, gui);
            gui.getInventory().setItem(
                    plugin.getConfig().getInt("gui.notes.slots.header", 4),
                    GuiItemFactory.create(plugin, 
                            plugin.getConfig().getConfigurationSection("gui.notes.header"),
                            Bukkit.getOfflinePlayer(target.uniqueId()),
                            "player", target.name(),
                            "note_count", totalEntries
                    )
            );

            for (int index = 0; index < notes.size() && index < entrySlots.size(); index++) {
                CaseRecord record = notes.get(index);
                int slot = entrySlots.get(index);
                gui.getInventory().setItem(
                        slot,
                        GuiItemFactory.create(plugin, 
                                plugin.getConfig().getConfigurationSection("gui.notes.entry"),
                                null,
                                plugin.getModerationService().recordReplacements(record)
                        )
                );
                gui.setAction(slot, event -> openCaseDetails(plugin, viewer, record.getId(), () -> openNotes(plugin, viewer, target, page, returnPage)));
            }

            setNavigation(plugin, gui, page, maxPage, "gui.notes.previous", "gui.notes.next", requested -> openNotes(plugin, viewer, target, requested, returnPage));
            placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.notes.slots.back", 45), "gui.notes.back", event -> openActionMenu(plugin, viewer, target, returnPage));
            placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.notes.slots.close", 49), "gui.notes.close", event -> viewer.closeInventory());
            viewer.openInventory(gui.getInventory());
            SoundUtil.play(plugin, viewer, requestedPage == page ? "gui.open" : "gui.navigate");
        } catch (Exception exception) {
            LoggerUtil.error("The notes GUI could not be opened.", exception);
            viewer.sendMessage(plugin.getLang().prefixed("messages.internal-error"));
            SoundUtil.play(plugin, viewer, "gui.error");
        }
    }

    public static void openRelatedProfiles(StarBans plugin, Player viewer, PlayerIdentity target, int requestedPage, int returnPage) {
        if (!ensurePermission(plugin, viewer, "starbans.gui.related")) {
            return;
        }
        List<Integer> entrySlots = plugin.getConfig().getIntegerList("gui.related.entry-slots");
        if (entrySlots.isEmpty()) {
            viewer.sendMessage(plugin.getLang().prefixed("messages.gui-misconfigured"));
            return;
        }

        try {
            List<PlayerProfile> relatedProfiles = plugin.getModerationService().getRelatedProfiles(target.uniqueId());
            int totalEntries = relatedProfiles.size();
            int maxPage = Math.max(1, (int) Math.ceil(totalEntries / (double) entrySlots.size()));
            int page = clampPage(requestedPage, maxPage);
            int startIndex = page * entrySlots.size();
            int endIndex = Math.min(startIndex + entrySlots.size(), totalEntries);
            List<PlayerProfile> pageEntries = relatedProfiles.subList(startIndex, endIndex);

            InteractiveGui gui = new InteractiveGui(
                    normalizeSize(plugin.getConfig().getInt("gui.related.size", 54)),
                    plugin.getLang().get("gui.related.title", "player", target.name(), "page", page + 1, "max_page", maxPage)
            );
            fillWithFiller(plugin, gui);
            gui.getInventory().setItem(
                    plugin.getConfig().getInt("gui.related.slots.header", 4),
                    GuiItemFactory.create(plugin, 
                            plugin.getConfig().getConfigurationSection("gui.related.header"),
                            Bukkit.getOfflinePlayer(target.uniqueId()),
                            "player", target.name(),
                            "related_count", totalEntries
                    )
            );

            for (int index = 0; index < pageEntries.size() && index < entrySlots.size(); index++) {
                PlayerProfile related = pageEntries.get(index);
                PlayerIdentity relatedIdentity = new PlayerIdentity(related.getUniqueId(), defaultName(plugin, related.getLastName()));
                PlayerSummary relatedSummary = plugin.getModerationService().getPlayerSummary(relatedIdentity);
                boolean online = Bukkit.getPlayer(related.getUniqueId()) != null;
                int slot = entrySlots.get(index);
                gui.getInventory().setItem(
                        slot,
                        GuiItemFactory.create(plugin, 
                                plugin.getConfig().getConfigurationSection("gui.related.entry"),
                                Bukkit.getOfflinePlayer(related.getUniqueId()),
                                "player", relatedIdentity.name(),
                                "status", buildStatus(plugin, relatedSummary),
                                "online_status", online ? plugin.getLang().get("labels.online") : plugin.getLang().get("labels.offline"),
                                "last_ip", defaultText(plugin, related.getLastIp()),
                                "case_count", relatedSummary.visibleCaseCount(),
                                "note_count", relatedSummary.noteCount(),
                                "alt_count", relatedSummary.altFlagCount(),
                                "last_seen", formatProfileTime(plugin, related.getLastSeen())
                        )
                );
                gui.setAction(slot, event -> handleRelatedProfileClick(plugin, viewer, target, relatedIdentity, page, returnPage, event.getClick()));
            }

            setNavigation(plugin, gui, page, maxPage, "gui.related.previous", "gui.related.next", requested -> openRelatedProfiles(plugin, viewer, target, requested, returnPage));
            placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.related.slots.back", 45), "gui.related.back", event -> openActionMenu(plugin, viewer, target, returnPage));
            placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.related.slots.close", 49), "gui.related.close", event -> viewer.closeInventory());
            viewer.openInventory(gui.getInventory());
            SoundUtil.play(plugin, viewer, requestedPage == page ? "gui.open" : "gui.navigate");
        } catch (Exception exception) {
            LoggerUtil.error("The related-profile GUI could not be opened.", exception);
            viewer.sendMessage(plugin.getLang().prefixed("messages.internal-error"));
            SoundUtil.play(plugin, viewer, "gui.error");
        }
    }

    public static void openRecentActivity(StarBans plugin, Player viewer, int requestedPage) {
        if (!ensurePermission(plugin, viewer, "starbans.gui.activity")) {
            return;
        }
        List<Integer> entrySlots = plugin.getConfig().getIntegerList("gui.activity.entry-slots");
        if (entrySlots.isEmpty()) {
            viewer.sendMessage(plugin.getLang().prefixed("messages.gui-misconfigured"));
            return;
        }

        try {
            int totalEntries = plugin.getModerationService().getStats().totalCases();
            int maxPage = Math.max(1, (int) Math.ceil(totalEntries / (double) entrySlots.size()));
            int page = clampPage(requestedPage, maxPage);
            List<CaseRecord> records = plugin.getModerationService().getRecentCases(entrySlots.size(), page);

            InteractiveGui gui = new InteractiveGui(
                    normalizeSize(plugin.getConfig().getInt("gui.activity.size", 54)),
                    plugin.getLang().get("gui.activity.title", "page", page + 1, "max_page", maxPage)
            );
            fillWithFiller(plugin, gui);
            gui.getInventory().setItem(
                    plugin.getConfig().getInt("gui.activity.slots.header", 4),
                    GuiItemFactory.create(plugin, 
                            plugin.getConfig().getConfigurationSection("gui.activity.header"),
                            null,
                            "case_count", totalEntries
                    )
            );

            for (int index = 0; index < records.size() && index < entrySlots.size(); index++) {
                CaseRecord record = records.get(index);
                int slot = entrySlots.get(index);
                gui.getInventory().setItem(slot, createCaseItem(plugin, record));
                gui.setAction(slot, event -> openCaseDetails(plugin, viewer, record.getId(), () -> openRecentActivity(plugin, viewer, page)));
            }

            setNavigation(plugin, gui, page, maxPage, "gui.activity.previous", "gui.activity.next", requested -> openRecentActivity(plugin, viewer, requested));
            placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.activity.slots.back", 45), "gui.activity.back", event -> openMainMenu(plugin, viewer));
            placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.activity.slots.close", 49), "gui.activity.close", event -> viewer.closeInventory());
            viewer.openInventory(gui.getInventory());
            SoundUtil.play(plugin, viewer, requestedPage == page ? "gui.open" : "gui.navigate");
        } catch (Exception exception) {
            LoggerUtil.error("The recent-activity GUI could not be opened.", exception);
            viewer.sendMessage(plugin.getLang().prefixed("messages.internal-error"));
            SoundUtil.play(plugin, viewer, "gui.error");
        }
    }

    public static void openCaseDetails(StarBans plugin, Player viewer, long caseId, Runnable backAction) {
        if (!ensurePermission(plugin, viewer, "starbans.gui.case.view")) {
            return;
        }
        try {
            Optional<CaseRecord> recordOptional = plugin.getModerationService().getCase(caseId);
            if (recordOptional.isEmpty()) {
                viewer.sendMessage(plugin.getLang().prefixed("messages.case-not-found", "id", caseId));
                if (backAction != null) {
                    backAction.run();
                }
                return;
            }

            CaseRecord record = recordOptional.get();
            InteractiveGui gui = new InteractiveGui(
                    normalizeSize(plugin.getConfig().getInt("gui.case-details.size", 27)),
                    plugin.getLang().get("gui.case-details.title", "id", record.getId())
            );
            fillWithFiller(plugin, gui);

            gui.getInventory().setItem(
                    plugin.getConfig().getInt("gui.case-details.slots.header", 11),
                    GuiItemFactory.create(plugin, 
                            plugin.getConfig().getConfigurationSection("gui.case-details.header"),
                            record.getTargetPlayerUniqueId() == null ? null : Bukkit.getOfflinePlayer(record.getTargetPlayerUniqueId()),
                            plugin.getModerationService().recordReplacements(record)
                    )
            );
            gui.getInventory().setItem(
                    plugin.getConfig().getInt("gui.case-details.slots.status", 15),
                    GuiItemFactory.create(plugin, 
                            plugin.getConfig().getConfigurationSection("gui.case-details.status"),
                            null,
                            plugin.getModerationService().recordReplacements(record)
                    )
            );

            if (record.getTargetPlayerUniqueId() != null && record.getTargetPlayerName() != null) {
                int targetSlot = plugin.getConfig().getInt("gui.case-details.slots.target", 10);
                gui.getInventory().setItem(
                        targetSlot,
                        GuiItemFactory.create(plugin, 
                                plugin.getConfig().getConfigurationSection("gui.case-details.target"),
                                Bukkit.getOfflinePlayer(record.getTargetPlayerUniqueId()),
                                plugin.getModerationService().recordReplacements(record)
                        )
                );
                gui.setAction(targetSlot, event -> openActionMenu(plugin, viewer, new PlayerIdentity(record.getTargetPlayerUniqueId(), record.getTargetPlayerName()), 0));
            }

            if (record.getStatus().name().equalsIgnoreCase("ACTIVE")) {
                placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.case-details.slots.resolve", 13), "gui.case-details.resolve", event -> {
                    if (!ensurePermission(plugin, viewer, "starbans.command.resolve", "starbans.gui.case.resolve")) {
                        return;
                    }
                    try {
                        ModerationActionResult result = plugin.getModerationService().resolveCase(record.getId(), CommandActor.fromSender(viewer), plugin.getConfig().getString("gui.case-details.resolve.reason", "Resolved through GUI"));
                        if (result.type() == ModerationActionType.NOT_ACTIVE) {
                            viewer.sendMessage(plugin.getLang().prefixed("messages.case-not-active", "id", record.getId()));
                            SoundUtil.play(plugin, viewer, "gui.error");
                        } else {
                            viewer.sendMessage(plugin.getLang().prefixed("messages.case-resolved", "id", record.getId()));
                            SoundUtil.play(plugin, viewer, "gui.resolve");
                        }
                        openCaseDetails(plugin, viewer, record.getId(), backAction);
                    } catch (Exception exception) {
                        LoggerUtil.error("The case resolve action failed.", exception);
                        viewer.sendMessage(plugin.getLang().prefixed("messages.internal-error"));
                        SoundUtil.play(plugin, viewer, "gui.error");
                    }
                });
            }

            placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.case-details.slots.back", 18), "gui.case-details.back", event -> {
                if (backAction != null) {
                    backAction.run();
                } else {
                    viewer.closeInventory();
                }
            });
            placeActionItem(plugin, gui, plugin.getConfig().getInt("gui.case-details.slots.close", 22), "gui.case-details.close", event -> viewer.closeInventory());
            viewer.openInventory(gui.getInventory());
            SoundUtil.play(plugin, viewer, "gui.open");
        } catch (Exception exception) {
            LoggerUtil.error("The case-detail GUI could not be opened.", exception);
            viewer.sendMessage(plugin.getLang().prefixed("messages.internal-error"));
            SoundUtil.play(plugin, viewer, "gui.error");
        }
    }

    private static void handleRelatedProfileClick(StarBans plugin,
                                                  Player viewer,
                                                  PlayerIdentity target,
                                                  PlayerIdentity related,
                                                  int page,
                                                  int returnPage,
                                                  ClickType clickType) {
        if (clickType.isRightClick()) {
            if (!ensurePermission(plugin, viewer, "starbans.command.alt", "starbans.gui.alt.mark")) {
                return;
            }
            try {
                ModerationActionResult result = plugin.getModerationService().addAltFlag(
                        target,
                        related,
                        CommandActor.fromSender(viewer),
                        plugin.getConfig().getString("gui.related.default-alt-label", plugin.getConfig().getString("alt-flags.default-label", "alt-account")),
                        plugin.getConfig().getString("gui.related.default-alt-note", plugin.getConfig().getString("alt-flags.default-note", "Linked from related-profile GUI.")),
                        "GUI:RELATED"
                );
                if (result.type() == ModerationActionType.ALREADY_ACTIVE) {
                    viewer.sendMessage(plugin.getLang().prefixed("messages.alt-already-marked", "player", target.name(), "related_player", related.name()));
                    SoundUtil.play(plugin, viewer, "gui.error");
                } else {
                    viewer.sendMessage(plugin.getLang().prefixed("messages.alt-mark-success", "player", target.name(), "related_player", related.name(), "label", result.caseRecord().getLabel()));
                    SoundUtil.play(plugin, viewer, "gui.success");
                }
                openRelatedProfiles(plugin, viewer, target, page, returnPage);
                return;
            } catch (Exception exception) {
                LoggerUtil.error("The GUI alt-link action failed.", exception);
                viewer.sendMessage(plugin.getLang().prefixed("messages.internal-error"));
                SoundUtil.play(plugin, viewer, "gui.error");
                return;
            }
        }

        openActionMenu(plugin, viewer, related, returnPage);
    }

    private static void attachTimedPresets(StarBans plugin, InteractiveGui gui, Player viewer, PlayerIdentity target, int returnPage, String path, boolean banPreset) {
        ConfigurationSection presets = plugin.getConfig().getConfigurationSection(path);
        if (presets == null) {
            return;
        }

        for (String key : presets.getKeys(false)) {
            ConfigurationSection section = presets.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            int slot = section.getInt("slot", -1);
            if (slot < 0) {
                continue;
            }

            gui.getInventory().setItem(slot, GuiItemFactory.create(plugin, section, null, "player", target.name()));
            gui.setAction(slot, event -> {
                String commandPermission = banPreset ? "starbans.command.tempban" : "starbans.command.tempmute";
                String guiPermission = banPreset ? "starbans.gui.punish.tempban" : "starbans.gui.punish.tempmute";
                if (!ensurePermission(plugin, viewer, commandPermission, guiPermission)) {
                    return;
                }
                try {
                    Long duration = TimeUtil.parseDuration(section.getString("duration", "1d"));
                    Long expiresAt = duration == null ? null : System.currentTimeMillis() + duration;
                    ModerationActionResult result = banPreset
                            ? plugin.getModerationService().banPlayer(target, CommandActor.fromSender(viewer), section.getString("reason", plugin.getConfig().getString("punishments.defaults.ban-reason", "No reason specified.")), expiresAt, "GUI:TEMP-BAN:" + key.toUpperCase(Locale.ROOT))
                            : plugin.getModerationService().mutePlayer(target, CommandActor.fromSender(viewer), section.getString("reason", plugin.getConfig().getString("punishments.defaults.mute-reason", "No reason specified.")), expiresAt, "GUI:TEMP-MUTE:" + key.toUpperCase(Locale.ROOT));
                    String successPath = banPreset ? "messages.tempban-success" : "messages.tempmute-success";
                    String alreadyPath = banPreset ? "messages.already-banned" : "messages.already-muted";
                    handleActionResult(plugin, viewer, result, successPath, alreadyPath, target.name());
                    openActionMenu(plugin, viewer, target, returnPage);
                } catch (Exception exception) {
                    LoggerUtil.error("A timed GUI preset failed.", exception);
                    viewer.sendMessage(plugin.getLang().prefixed("messages.internal-error"));
                    SoundUtil.play(plugin, viewer, "gui.error");
                }
            });
        }
    }

    private static ItemStack createCaseItem(StarBans plugin, CaseRecord record) {
        ConfigurationSection section = entrySection(plugin, record);
        return GuiItemFactory.create(plugin, section, null, plugin.getModerationService().recordReplacements(record));
    }

    private static ConfigurationSection entrySection(StarBans plugin, CaseRecord record) {
        String specific = "gui.history.entry-" + record.getType().name().toLowerCase(Locale.ROOT).replace('_', '-');
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(specific);
        if (section != null) {
            return section;
        }
        return plugin.getConfig().getConfigurationSection("gui.history.entry-default");
    }

    private static void fillWithFiller(StarBans plugin, InteractiveGui gui) {
        ItemStack filler = GuiItemFactory.create(plugin, plugin.getConfig().getConfigurationSection("gui.shared.filler"), null);
        GuiItemFactory.fillEmpty(gui.getInventory(), filler);
    }

    private static String buildStatus(StarBans plugin, PlayerSummary summary) {
        if (summary.activeBan() != null) {
            return plugin.getLang().get("labels.status-banned");
        }
        if (summary.activeMute() != null) {
            return plugin.getLang().get("labels.status-muted");
        }
        if (summary.activeWatchlist() != null) {
            return plugin.getLang().get("labels.status-watchlisted");
        }
        return plugin.getLang().get("labels.status-clean");
    }

    private static String formatProfileTime(StarBans plugin, long epochMillis) {
        if (epochMillis <= 0L) {
            return plugin.getLang().get("labels.none");
        }
        return plugin.getModerationService().formatDate(epochMillis);
    }

    private static String defaultText(StarBans plugin, String value) {
        return value == null || value.isBlank() ? plugin.getLang().get("labels.none") : value;
    }

    private static String defaultName(StarBans plugin, String value) {
        return defaultText(plugin, value);
    }

    private static void handleActionResult(StarBans plugin, Player viewer, ModerationActionResult result, String successPath, String alreadyPath, String targetName) {
        if (result.type() == ModerationActionType.ALREADY_ACTIVE && result.caseRecord() != null) {
            viewer.sendMessage(plugin.getLang().prefixed(alreadyPath, "player", targetName, "reason", result.caseRecord().getReason()));
            SoundUtil.play(plugin, viewer, "gui.error");
            return;
        }
        if (!result.successful() || result.caseRecord() == null) {
            viewer.sendMessage(plugin.getLang().prefixed("messages.internal-error"));
            SoundUtil.play(plugin, viewer, "gui.error");
            return;
        }
        viewer.sendMessage(plugin.getLang().prefixed(successPath, "player", targetName, "reason", result.caseRecord().getReason(), "remaining", plugin.getModerationService().formatRemaining(result.caseRecord())));
        SoundUtil.play(plugin, viewer, "gui.success");
    }

    private static void handleRemoval(StarBans plugin, Player viewer, ModerationActionResult result, String successPath, String failurePath, String targetName) {
        if (result.type() == ModerationActionType.NOT_ACTIVE) {
            viewer.sendMessage(plugin.getLang().prefixed(failurePath, "player", targetName));
            SoundUtil.play(plugin, viewer, "gui.error");
            return;
        }
        viewer.sendMessage(plugin.getLang().prefixed(successPath, "player", targetName));
        SoundUtil.play(plugin, viewer, "gui.success");
    }

    private static boolean ensurePermission(StarBans plugin, Player viewer, String... permissions) {
        if (viewer.hasPermission("starbans.admin")) {
            return true;
        }

        for (String permission : permissions) {
            if (permission == null || permission.isBlank()) {
                continue;
            }
            if (!viewer.hasPermission(permission)) {
                viewer.sendMessage(plugin.getLang().prefixed("messages.no-permission"));
                SoundUtil.play(plugin, viewer, "gui.error");
                return false;
            }
        }
        return true;
    }

    private static void placeActionItem(StarBans plugin, InteractiveGui gui, int slot, String sectionPath, java.util.function.Consumer<InventoryClickEvent> action) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(sectionPath);
        if (section == null || slot < 0) {
            return;
        }
        gui.getInventory().setItem(slot, GuiItemFactory.create(plugin, section, null));
        gui.setAction(slot, action);
    }

    private static void placeStaticItem(StarBans plugin, InteractiveGui gui, int slot, String sectionPath, Object... replacements) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(sectionPath);
        if (section == null || slot < 0) {
            return;
        }
        gui.getInventory().setItem(slot, GuiItemFactory.create(plugin, section, null, replacements));
    }

    private static void setNavigation(StarBans plugin,
                                      InteractiveGui gui,
                                      int page,
                                      int maxPage,
                                      String previousSection,
                                      String nextSection,
                                      IntConsumer opener) {
        int previousSlot = plugin.getConfig().getInt(previousSection + "-slot", 48);
        int nextSlot = plugin.getConfig().getInt(nextSection + "-slot", 50);

        if (page > 0) {
            gui.getInventory().setItem(previousSlot, GuiItemFactory.create(plugin, plugin.getConfig().getConfigurationSection(previousSection), null, "page", page, "max_page", maxPage));
            gui.setAction(previousSlot, event -> opener.accept(page - 1));
        }
        if (page + 1 < maxPage) {
            gui.getInventory().setItem(nextSlot, GuiItemFactory.create(plugin, plugin.getConfig().getConfigurationSection(nextSection), null, "page", page + 2, "max_page", maxPage));
            gui.setAction(nextSlot, event -> opener.accept(page + 1));
        }
    }

    private static int normalizeSize(int size) {
        int normalized = Math.max(9, Math.min(54, size));
        int remainder = normalized % 9;
        return remainder == 0 ? normalized : normalized + (9 - remainder);
    }

    private static int clampPage(int requestedPage, int maxPage) {
        return Math.max(0, Math.min(requestedPage, Math.max(0, maxPage - 1)));
    }
}

