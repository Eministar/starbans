package dev.eministar.starbans.command;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.gui.AdminGuiFactory;
import dev.eministar.starbans.model.CaseRecord;
import dev.eministar.starbans.model.CommandActor;
import dev.eministar.starbans.model.ModerationActionResult;
import dev.eministar.starbans.model.ModerationActionType;
import dev.eministar.starbans.model.PlayerIdentity;
import dev.eministar.starbans.model.PlayerProfile;
import dev.eministar.starbans.model.PlayerSummary;
import dev.eministar.starbans.service.IpUtil;
import dev.eministar.starbans.service.TimeUtil;
import dev.eministar.starbans.utils.LoggerUtil;
import dev.eministar.starbans.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class StarBansCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT_SUBCOMMANDS = List.of(
            "help", "reload", "gui", "check", "cases", "case", "notes", "note", "ban", "tempban",
            "unban", "ipban", "tempipban", "unipban", "mute", "tempmute", "unmute", "kick", "alt", "ipblacklist"
    );
    private static final List<String> DURATION_SUGGESTIONS = List.of("30m", "1h", "12h", "1d", "7d", "30d");
    private static final List<String> ALT_SUBCOMMANDS = List.of("mark", "list", "clear");
    private static final List<String> BLACKLIST_SUBCOMMANDS = List.of("add", "remove");

    private final StarBans plugin;

    public StarBansCommand(StarBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        try {
            return switch (commandName) {
                case "sban", "ban" -> executeBan(sender, args, "COMMAND:DIRECT");
                case "stempban", "tempban" -> executeTempBan(sender, args, "COMMAND:DIRECT");
                case "sunban", "unban", "pardon" -> executeUnban(sender, args);
                case "sipban", "ipban", "banip", "ip-ban", "ban-ip" -> executeIpBan(sender, args, "COMMAND:DIRECT");
                case "stempipban" -> executeTempIpBan(sender, args, "COMMAND:DIRECT");
                case "sunipban", "unipban", "ipunban", "unbanip", "pardonip" -> executeUnipban(sender, args);
                case "smute", "mute" -> executeMute(sender, args, "COMMAND:DIRECT");
                case "stempmute", "tempmute" -> executeTempMute(sender, args, "COMMAND:DIRECT");
                case "sunmute", "unmute" -> executeUnmute(sender, args);
                case "skick", "kick" -> executeKick(sender, args, "COMMAND:DIRECT");
                case "bancheck" -> executeCheck(sender, args);
                case "banhistory", "scases", "cases", "casehistory", "history" -> executeCases(sender, args);
                case "snotes", "notes", "notehistory" -> executeNotes(sender, args);
                case "snote" -> executeAddNote(sender, args, "COMMAND:DIRECT");
                case "salt" -> executeAlt(sender, args, "COMMAND:DIRECT");
                case "sipblacklist" -> executeIpBlacklist(sender, args, "COMMAND:DIRECT");
                default -> executeRoot(sender, args);
            };
        } catch (Exception exception) {
            LoggerUtil.error("A command execution failed unexpectedly.", exception);
            sender.sendMessage(plugin.getLang().prefixed("messages.internal-error"));
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (commandName.equals("starbans")) {
            return tabCompleteRoot(args);
        }
        return tabCompleteDirect(commandName, args);
    }

    private boolean executeRoot(CommandSender sender, String[] args) throws Exception {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                if (!hasPermission(sender, "starbans.command.gui")) {
                    deny(sender);
                    return true;
                }
                AdminGuiFactory.openMainMenu(plugin, player);
                return true;
            }
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            case "reload" -> executeReload(sender);
            case "gui" -> executeGui(sender, subArguments(args));
            case "check" -> executeCheck(sender, subArguments(args));
            case "cases", "history" -> executeCases(sender, subArguments(args));
            case "case" -> executeCaseDetails(sender, subArguments(args));
            case "notes" -> executeNotes(sender, subArguments(args));
            case "note" -> executeAddNote(sender, subArguments(args), "COMMAND:ROOT");
            case "ban" -> executeBan(sender, subArguments(args), "COMMAND:ROOT");
            case "tempban" -> executeTempBan(sender, subArguments(args), "COMMAND:ROOT");
            case "unban" -> executeUnban(sender, subArguments(args));
            case "ipban" -> executeIpBan(sender, subArguments(args), "COMMAND:ROOT");
            case "tempipban" -> executeTempIpBan(sender, subArguments(args), "COMMAND:ROOT");
            case "unipban" -> executeUnipban(sender, subArguments(args));
            case "mute" -> executeMute(sender, subArguments(args), "COMMAND:ROOT");
            case "tempmute" -> executeTempMute(sender, subArguments(args), "COMMAND:ROOT");
            case "unmute" -> executeUnmute(sender, subArguments(args));
            case "kick" -> executeKick(sender, subArguments(args), "COMMAND:ROOT");
            case "alt" -> executeAlt(sender, subArguments(args), "COMMAND:ROOT");
            case "ipblacklist" -> executeIpBlacklist(sender, subArguments(args), "COMMAND:ROOT");
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean executeReload(CommandSender sender) {
        if (!hasPermission(sender, "starbans.command.reload")) {
            deny(sender);
            return true;
        }

        if (plugin.reloadPluginState()) {
            sender.sendMessage(plugin.getLang().prefixed("messages.reload-success"));
        } else {
            sender.sendMessage(plugin.getLang().prefixed("messages.reload-failed"));
        }
        return true;
    }

    private boolean executeGui(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "starbans.command.gui")) {
            deny(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLang().prefixed("messages.player-only"));
            return true;
        }

        if (args.length == 0) {
            AdminGuiFactory.openPlayerBrowser(plugin, player, 0);
            return true;
        }

        Optional<PlayerIdentity> target = resolveTarget(sender, args[0]);
        if (target.isEmpty()) {
            return true;
        }

        AdminGuiFactory.openActionMenu(plugin, player, target.get());
        return true;
    }

    private boolean executeCheck(CommandSender sender, String[] args) throws Exception {
        if (!hasPermission(sender, "starbans.command.check")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-check"));
            return true;
        }

        Optional<PlayerIdentity> target = resolveTarget(sender, args[0]);
        if (target.isEmpty()) {
            return true;
        }

        PlayerSummary summary = plugin.getModerationService().getPlayerSummary(target.get());
        sender.sendMessage(plugin.getLang().prefixed("messages.check-header", "player", target.get().name()));
        for (String line : plugin.getLang().prefixedList(
                "messages.check-lines",
                "player", target.get().name(),
                "last_ip", summary.lastKnownIp() == null ? plugin.getLang().get("labels.none") : summary.lastKnownIp(),
                "active_ban", summary.activeBan() == null ? plugin.getLang().get("labels.none") : summary.activeBan().getReason(),
                "active_mute", summary.activeMute() == null ? plugin.getLang().get("labels.none") : summary.activeMute().getReason(),
                "case_count", summary.visibleCaseCount(),
                "note_count", summary.noteCount(),
                "alt_count", summary.altFlagCount(),
                "last_case_type", summary.latestCase() == null ? plugin.getLang().get("labels.none") : plugin.getModerationService().formatCaseType(summary.latestCase().getType()),
                "last_case_reason", summary.latestCase() == null ? plugin.getLang().get("labels.none") : summary.latestCase().getReason()
        )) {
            sender.sendMessage(line);
        }

        List<PlayerProfile> relatedProfiles = plugin.getModerationService().getRelatedProfiles(target.get().uniqueId());
        if (!relatedProfiles.isEmpty()) {
            sender.sendMessage(plugin.getLang().prefixed("messages.check-related-header"));
            for (PlayerProfile profile : relatedProfiles) {
                sender.sendMessage(plugin.getLang().prefixed("messages.check-related-entry", "player", profile.getLastName(), "ip", profile.getLastIp()));
            }
        }
        return true;
    }

    private boolean executeCases(CommandSender sender, String[] args) throws Exception {
        if (!hasPermission(sender, "starbans.command.cases")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-cases"));
            return true;
        }

        Optional<PlayerIdentity> target = resolveTarget(sender, args[0]);
        if (target.isEmpty()) {
            return true;
        }

        if (sender instanceof Player player) {
            AdminGuiFactory.openHistory(plugin, player, target.get(), 0, 0);
            return true;
        }

        int limit = Math.max(1, plugin.getConfig().getInt("commands.console-history-limit", 10));
        List<CaseRecord> cases = plugin.getModerationService().getPlayerCases(target.get().uniqueId(), limit, 0);
        sender.sendMessage(plugin.getLang().prefixed("messages.cases-header", "player", target.get().name(), "case_count", plugin.getModerationService().countPlayerCases(target.get().uniqueId())));
        if (cases.isEmpty()) {
            sender.sendMessage(plugin.getLang().prefixed("messages.cases-empty", "player", target.get().name()));
            return true;
        }

        for (CaseRecord record : cases) {
            sender.sendMessage(plugin.getLang().prefixed(
                    "messages.case-entry",
                    "id", record.getId(),
                    "type", plugin.getModerationService().formatCaseType(record.getType()),
                    "reason", record.getReason(),
                    "actor", record.getActorName(),
                    "created_at", plugin.getModerationService().formatDate(record.getCreatedAt()),
                    "expires_at", plugin.getModerationService().formatExpiry(record),
                    "status", plugin.getLang().get("labels.case-status-" + record.getStatus().name().toLowerCase(Locale.ROOT)),
                    "ip", record.getTargetIp() == null ? plugin.getLang().get("labels.none") : record.getTargetIp(),
                    "label", record.getLabel() == null ? plugin.getLang().get("labels.none") : record.getLabel(),
                    "related_player", record.getRelatedPlayerName() == null ? plugin.getLang().get("labels.none") : record.getRelatedPlayerName()
            ));
        }
        return true;
    }

    private boolean executeCaseDetails(CommandSender sender, String[] args) throws Exception {
        if (!hasPermission(sender, "starbans.command.cases")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-case"));
            return true;
        }

        long caseId;
        try {
            caseId = Long.parseLong(args[0]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(plugin.getLang().prefixed("messages.invalid-case-id", "input", args[0]));
            return true;
        }

        Optional<CaseRecord> record = plugin.getModerationService().getCase(caseId);
        if (record.isEmpty()) {
            sender.sendMessage(plugin.getLang().prefixed("messages.case-not-found", "id", caseId));
            return true;
        }

        for (String line : plugin.getLang().prefixedList(
                "messages.case-details",
                "id", record.get().getId(),
                "type", plugin.getModerationService().formatCaseType(record.get().getType()),
                "player", record.get().getTargetPlayerName() == null ? plugin.getLang().get("labels.none") : record.get().getTargetPlayerName(),
                "related_player", record.get().getRelatedPlayerName() == null ? plugin.getLang().get("labels.none") : record.get().getRelatedPlayerName(),
                "ip", record.get().getTargetIp() == null ? plugin.getLang().get("labels.none") : record.get().getTargetIp(),
                "label", record.get().getLabel() == null ? plugin.getLang().get("labels.none") : record.get().getLabel(),
                "reason", record.get().getReason(),
                "actor", record.get().getActorName(),
                "source", record.get().getSource(),
                "created_at", plugin.getModerationService().formatDate(record.get().getCreatedAt()),
                "expires_at", plugin.getModerationService().formatExpiry(record.get()),
                "status", plugin.getLang().get("labels.case-status-" + record.get().getStatus().name().toLowerCase(Locale.ROOT)),
                "status_actor", record.get().getStatusActorName() == null ? plugin.getLang().get("labels.none") : record.get().getStatusActorName(),
                "status_note", record.get().getStatusNote() == null ? plugin.getLang().get("labels.none") : record.get().getStatusNote()
        )) {
            sender.sendMessage(line);
        }
        return true;
    }

    private boolean executeNotes(CommandSender sender, String[] args) throws Exception {
        if (!hasPermission(sender, "starbans.command.notes")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-notes"));
            return true;
        }

        Optional<PlayerIdentity> target = resolveTarget(sender, args[0]);
        if (target.isEmpty()) {
            return true;
        }

        int limit = Math.max(1, plugin.getConfig().getInt("notes.console-limit", 10));
        List<CaseRecord> notes = plugin.getModerationService().getPlayerNotes(target.get().uniqueId(), limit, 0);
        sender.sendMessage(plugin.getLang().prefixed("messages.notes-header", "player", target.get().name(), "note_count", notes.size()));
        if (notes.isEmpty()) {
            sender.sendMessage(plugin.getLang().prefixed("messages.notes-empty", "player", target.get().name()));
            return true;
        }

        for (CaseRecord record : notes) {
            sender.sendMessage(plugin.getLang().prefixed(
                    "messages.note-entry",
                    "id", record.getId(),
                    "label", record.getLabel() == null ? plugin.getLang().get("labels.none") : record.getLabel(),
                    "text", record.getReason(),
                    "actor", record.getActorName(),
                    "created_at", plugin.getModerationService().formatDate(record.getCreatedAt())
            ));
        }
        return true;
    }

    private boolean executeAddNote(CommandSender sender, String[] args, String source) throws Exception {
        if (!hasPermission(sender, "starbans.command.note")) {
            deny(sender);
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-note"));
            return true;
        }

        Optional<PlayerIdentity> target = resolveTarget(sender, args[0]);
        if (target.isEmpty()) {
            return true;
        }

        String label = args[1];
        String text = joinArgs(args, 2);
        ModerationActionResult result = plugin.getModerationService().addNote(target.get(), CommandActor.fromSender(sender), label, text, source);
        sender.sendMessage(plugin.getLang().prefixed("messages.note-success", "player", target.get().name(), "label", result.caseRecord().getLabel()));
        return true;
    }

    private boolean executeBan(CommandSender sender, String[] args, String source) throws Exception {
        if (!hasPermission(sender, "starbans.command.ban")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-ban"));
            return true;
        }

        Optional<PlayerIdentity> target = resolveTarget(sender, args[0]);
        if (target.isEmpty()) {
            return true;
        }

        ModerationActionResult result = plugin.getModerationService().banPlayer(target.get(), CommandActor.fromSender(sender), joinArgs(args, 1), null, source);
        return handleCreationResult(sender, target.get().name(), result, "messages.ban-success", "messages.already-banned", false);
    }

    private boolean executeTempBan(CommandSender sender, String[] args, String source) throws Exception {
        if (!hasPermission(sender, "starbans.command.tempban")) {
            deny(sender);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-tempban"));
            return true;
        }

        Optional<PlayerIdentity> target = resolveTarget(sender, args[0]);
        if (target.isEmpty()) {
            return true;
        }

        Long duration = parseDuration(sender, args[1]);
        if (duration == Long.MIN_VALUE) {
            return true;
        }

        Long expiresAt = duration == null ? null : System.currentTimeMillis() + duration;
        ModerationActionResult result = plugin.getModerationService().banPlayer(target.get(), CommandActor.fromSender(sender), joinArgs(args, 2), expiresAt, source);
        return handleCreationResult(sender, target.get().name(), result, "messages.tempban-success", "messages.already-banned", true);
    }

    private boolean executeUnban(CommandSender sender, String[] args) throws Exception {
        if (!hasPermission(sender, "starbans.command.unban")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-unban"));
            return true;
        }

        if (looksLikeIp(args[0])) {
            return executeUnipban(sender, args);
        }

        Optional<PlayerIdentity> target = resolveTarget(sender, args[0]);
        if (target.isEmpty()) {
            return true;
        }

        ModerationActionResult result = plugin.getModerationService().unbanPlayer(target.get(), CommandActor.fromSender(sender), joinArgs(args, 1));
        return handleRemovalResult(sender, target.get().name(), result, "messages.unban-success", "messages.not-banned");
    }

    private boolean executeIpBan(CommandSender sender, String[] args, String source) throws Exception {
        if (!hasPermission(sender, "starbans.command.ipban")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-ipban"));
            return true;
        }

        ResolvedIpTarget target = resolveIpTarget(sender, args[0]);
        if (target == null) {
            return true;
        }

        ModerationActionResult result = plugin.getModerationService().banIp(target.ipAddress(), target.player(), CommandActor.fromSender(sender), joinArgs(args, 1), null, source);
        return handleIpCreationResult(sender, target, result, "messages.ipban-success", false);
    }

    private boolean executeTempIpBan(CommandSender sender, String[] args, String source) throws Exception {
        if (!hasPermission(sender, "starbans.command.ipban")) {
            deny(sender);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-tempipban"));
            return true;
        }

        ResolvedIpTarget target = resolveIpTarget(sender, args[0]);
        if (target == null) {
            return true;
        }

        Long duration = parseDuration(sender, args[1]);
        if (duration == Long.MIN_VALUE) {
            return true;
        }

        Long expiresAt = duration == null ? null : System.currentTimeMillis() + duration;
        ModerationActionResult result = plugin.getModerationService().banIp(target.ipAddress(), target.player(), CommandActor.fromSender(sender), joinArgs(args, 2), expiresAt, source);
        return handleIpCreationResult(sender, target, result, "messages.tempipban-success", true);
    }

    private boolean executeUnipban(CommandSender sender, String[] args) throws Exception {
        if (!hasPermission(sender, "starbans.command.unipban")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-unipban"));
            return true;
        }

        ResolvedIpTarget target = resolveIpTarget(sender, args[0]);
        if (target == null) {
            return true;
        }

        ModerationActionResult result = plugin.getModerationService().unbanIp(target.ipAddress(), CommandActor.fromSender(sender), joinArgs(args, 1));
        if (result.type() == ModerationActionType.NOT_ACTIVE) {
            sender.sendMessage(plugin.getLang().prefixed("messages.ip-not-banned", "ip", target.ipAddress()));
            return true;
        }

        sender.sendMessage(plugin.getLang().prefixed("messages.unipban-success", "ip", target.ipAddress()));
        return true;
    }

    private boolean executeMute(CommandSender sender, String[] args, String source) throws Exception {
        if (!hasPermission(sender, "starbans.command.mute")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-mute"));
            return true;
        }

        Optional<PlayerIdentity> target = resolveTarget(sender, args[0]);
        if (target.isEmpty()) {
            return true;
        }

        ModerationActionResult result = plugin.getModerationService().mutePlayer(target.get(), CommandActor.fromSender(sender), joinArgs(args, 1), null, source);
        return handleCreationResult(sender, target.get().name(), result, "messages.mute-success", "messages.already-muted", false);
    }

    private boolean executeTempMute(CommandSender sender, String[] args, String source) throws Exception {
        if (!hasPermission(sender, "starbans.command.tempmute")) {
            deny(sender);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-tempmute"));
            return true;
        }

        Optional<PlayerIdentity> target = resolveTarget(sender, args[0]);
        if (target.isEmpty()) {
            return true;
        }

        Long duration = parseDuration(sender, args[1]);
        if (duration == Long.MIN_VALUE) {
            return true;
        }

        Long expiresAt = duration == null ? null : System.currentTimeMillis() + duration;
        ModerationActionResult result = plugin.getModerationService().mutePlayer(target.get(), CommandActor.fromSender(sender), joinArgs(args, 2), expiresAt, source);
        return handleCreationResult(sender, target.get().name(), result, "messages.tempmute-success", "messages.already-muted", true);
    }

    private boolean executeUnmute(CommandSender sender, String[] args) throws Exception {
        if (!hasPermission(sender, "starbans.command.unmute")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-unmute"));
            return true;
        }

        Optional<PlayerIdentity> target = resolveTarget(sender, args[0]);
        if (target.isEmpty()) {
            return true;
        }

        ModerationActionResult result = plugin.getModerationService().unmutePlayer(target.get(), CommandActor.fromSender(sender), joinArgs(args, 1));
        return handleRemovalResult(sender, target.get().name(), result, "messages.unmute-success", "messages.not-muted");
    }

    private boolean executeKick(CommandSender sender, String[] args, String source) throws Exception {
        if (!hasPermission(sender, "starbans.command.kick")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-kick"));
            return true;
        }

        Optional<PlayerIdentity> target = resolveTarget(sender, args[0]);
        if (target.isEmpty()) {
            return true;
        }

        plugin.getModerationService().kickPlayer(target.get(), CommandActor.fromSender(sender), joinArgs(args, 1), source);
        sender.sendMessage(plugin.getLang().prefixed("messages.kick-success", "player", target.get().name()));
        return true;
    }

    private boolean executeAlt(CommandSender sender, String[] args, String source) throws Exception {
        if (!hasPermission(sender, "starbans.command.alt")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-alt"));
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (subCommand.equals("mark")) {
            if (args.length < 4) {
                sender.sendMessage(plugin.getLang().prefixed("messages.usage-alt-mark"));
                return true;
            }
            Optional<PlayerIdentity> first = resolveTarget(sender, args[2]);
            Optional<PlayerIdentity> second = resolveTarget(sender, args[3]);
            if (first.isEmpty() || second.isEmpty()) {
                return true;
            }
            ModerationActionResult result = plugin.getModerationService().addAltFlag(first.get(), second.get(), CommandActor.fromSender(sender), args[1], joinArgs(args, 4), source);
            if (result.type() == ModerationActionType.ALREADY_ACTIVE) {
                sender.sendMessage(plugin.getLang().prefixed("messages.alt-already-marked", "player", first.get().name(), "related_player", second.get().name()));
                return true;
            }
            sender.sendMessage(plugin.getLang().prefixed("messages.alt-mark-success", "player", first.get().name(), "related_player", second.get().name(), "label", result.caseRecord().getLabel()));
            return true;
        }

        if (subCommand.equals("list")) {
            if (args.length < 2) {
                sender.sendMessage(plugin.getLang().prefixed("messages.usage-alt-list"));
                return true;
            }
            Optional<PlayerIdentity> target = resolveTarget(sender, args[1]);
            if (target.isEmpty()) {
                return true;
            }
            List<CaseRecord> flags = plugin.getModerationService().getAltFlags(target.get().uniqueId());
            sender.sendMessage(plugin.getLang().prefixed("messages.alt-list-header", "player", target.get().name(), "alt_count", flags.size()));
            if (flags.isEmpty()) {
                sender.sendMessage(plugin.getLang().prefixed("messages.alt-list-empty", "player", target.get().name()));
                return true;
            }
            for (CaseRecord record : flags) {
                sender.sendMessage(plugin.getLang().prefixed(
                        "messages.alt-entry",
                        "id", record.getId(),
                        "label", record.getLabel() == null ? plugin.getLang().get("labels.none") : record.getLabel(),
                        "player", record.getTargetPlayerName() == null ? plugin.getLang().get("labels.none") : record.getTargetPlayerName(),
                        "related_player", record.getRelatedPlayerName() == null ? plugin.getLang().get("labels.none") : record.getRelatedPlayerName(),
                        "actor", record.getActorName(),
                        "reason", record.getReason()
                ));
            }
            return true;
        }

        if (subCommand.equals("clear")) {
            if (args.length < 2) {
                sender.sendMessage(plugin.getLang().prefixed("messages.usage-alt-clear"));
                return true;
            }
            long caseId;
            try {
                caseId = Long.parseLong(args[1]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(plugin.getLang().prefixed("messages.invalid-case-id", "input", args[1]));
                return true;
            }
            ModerationActionResult result = plugin.getModerationService().resolveCase(caseId, CommandActor.fromSender(sender), joinArgs(args, 2));
            if (result.type() == ModerationActionType.NOT_FOUND) {
                sender.sendMessage(plugin.getLang().prefixed("messages.case-not-found", "id", caseId));
                return true;
            }
            if (result.type() == ModerationActionType.NOT_ACTIVE) {
                sender.sendMessage(plugin.getLang().prefixed("messages.case-not-active", "id", caseId));
                return true;
            }
            sender.sendMessage(plugin.getLang().prefixed("messages.alt-clear-success", "id", caseId));
            return true;
        }

        sender.sendMessage(plugin.getLang().prefixed("messages.usage-alt"));
        return true;
    }

    private boolean executeIpBlacklist(CommandSender sender, String[] args, String source) throws Exception {
        if (!hasPermission(sender, "starbans.command.ipblacklist")) {
            deny(sender);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-ipblacklist"));
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        String ipAddress = IpUtil.normalize(args[1]);
        if (subCommand.equals("add")) {
            ModerationActionResult result = plugin.getModerationService().blacklistIp(ipAddress, CommandActor.fromSender(sender), joinArgs(args, 2), source);
            if (result.type() == ModerationActionType.ALREADY_ACTIVE) {
                sender.sendMessage(plugin.getLang().prefixed("messages.ip-already-blacklisted", "ip", ipAddress));
                return true;
            }
            sender.sendMessage(plugin.getLang().prefixed("messages.ipblacklist-add-success", "ip", ipAddress));
            return true;
        }

        if (subCommand.equals("remove")) {
            ModerationActionResult result = plugin.getModerationService().unblacklistIp(ipAddress, CommandActor.fromSender(sender), joinArgs(args, 2));
            if (result.type() == ModerationActionType.NOT_ACTIVE) {
                sender.sendMessage(plugin.getLang().prefixed("messages.ip-not-blacklisted", "ip", ipAddress));
                return true;
            }
            sender.sendMessage(plugin.getLang().prefixed("messages.ipblacklist-remove-success", "ip", ipAddress));
            return true;
        }

        sender.sendMessage(plugin.getLang().prefixed("messages.usage-ipblacklist"));
        return true;
    }

    private boolean handleCreationResult(CommandSender sender,
                                         String targetName,
                                         ModerationActionResult result,
                                         String successPath,
                                         String alreadyPath,
                                         boolean timed) {
        if (result.type() == ModerationActionType.ALREADY_ACTIVE && result.caseRecord() != null) {
            sender.sendMessage(plugin.getLang().prefixed(alreadyPath, "player", targetName, "reason", result.caseRecord().getReason()));
            return true;
        }

        if (!result.successful() || result.caseRecord() == null) {
            sender.sendMessage(plugin.getLang().prefixed("messages.internal-error"));
            return true;
        }

        if (timed) {
            sender.sendMessage(plugin.getLang().prefixed(successPath, "player", targetName, "reason", result.caseRecord().getReason(), "remaining", plugin.getModerationService().formatRemaining(result.caseRecord())));
        } else {
            sender.sendMessage(plugin.getLang().prefixed(successPath, "player", targetName, "reason", result.caseRecord().getReason()));
        }
        return true;
    }

    private boolean handleRemovalResult(CommandSender sender,
                                        String targetName,
                                        ModerationActionResult result,
                                        String successPath,
                                        String inactivePath) {
        if (result.type() == ModerationActionType.NOT_ACTIVE) {
            sender.sendMessage(plugin.getLang().prefixed(inactivePath, "player", targetName));
            return true;
        }

        sender.sendMessage(plugin.getLang().prefixed(successPath, "player", targetName));
        return true;
    }

    private boolean handleIpCreationResult(CommandSender sender,
                                           ResolvedIpTarget target,
                                           ModerationActionResult result,
                                           String successPath,
                                           boolean timed) {
        if (result.type() == ModerationActionType.ALREADY_ACTIVE) {
            sender.sendMessage(plugin.getLang().prefixed("messages.ip-already-banned", "ip", target.ipAddress(), "reason", result.caseRecord().getReason()));
            return true;
        }

        if (timed) {
            sender.sendMessage(plugin.getLang().prefixed(successPath, "ip", target.ipAddress(), "player", target.displayTarget(), "reason", result.caseRecord().getReason(), "remaining", plugin.getModerationService().formatRemaining(result.caseRecord())));
        } else {
            sender.sendMessage(plugin.getLang().prefixed(successPath, "ip", target.ipAddress(), "player", target.displayTarget(), "reason", result.caseRecord().getReason()));
        }
        return true;
    }

    private Optional<PlayerIdentity> resolveTarget(CommandSender sender, String input) {
        Optional<PlayerIdentity> target = plugin.getPlayerLookupService().resolve(input);
        if (target.isPresent()) {
            return target;
        }
        sender.sendMessage(plugin.getLang().prefixed("messages.player-not-found", "player", input));
        return Optional.empty();
    }

    private ResolvedIpTarget resolveIpTarget(CommandSender sender, String input) throws Exception {
        if (looksLikeIp(input)) {
            return new ResolvedIpTarget(null, IpUtil.normalize(input));
        }

        Optional<PlayerIdentity> player = resolveTarget(sender, input);
        if (player.isEmpty()) {
            return null;
        }

        String ipAddress = null;
        Player online = Bukkit.getPlayer(player.get().uniqueId());
        if (online != null && online.getAddress() != null) {
            ipAddress = IpUtil.normalize(online.getAddress().getAddress().getHostAddress());
        }
        if (ipAddress == null || ipAddress.isBlank()) {
            ipAddress = plugin.getModerationService().getProfile(player.get().uniqueId()).map(PlayerProfile::getLastIp).orElse(null);
        }
        if (ipAddress == null || ipAddress.isBlank()) {
            sender.sendMessage(plugin.getLang().prefixed("messages.player-has-no-known-ip", "player", player.get().name()));
            return null;
        }
        return new ResolvedIpTarget(player.get(), ipAddress);
    }

    private Long parseDuration(CommandSender sender, String input) {
        try {
            return TimeUtil.parseDuration(input);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(plugin.getLang().prefixed("messages.invalid-duration", "input", input));
            return Long.MIN_VALUE;
        }
    }

    private boolean looksLikeIp(String input) {
        return input.matches("[0-9a-fA-F:.]+") && (input.contains(".") || input.contains(":"));
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        return !(sender instanceof Player) || sender.hasPermission("starbans.admin") || sender.hasPermission(permission);
    }

    private void deny(CommandSender sender) {
        sender.sendMessage(plugin.getLang().prefixed("messages.no-permission"));
        if (sender instanceof Player player) {
            SoundUtil.play(plugin, player, "gui.error");
        }
    }

    private void sendHelp(CommandSender sender) {
        for (String line : plugin.getLang().prefixedList("messages.help")) {
            sender.sendMessage(line);
        }
    }

    private String[] subArguments(String[] args) {
        if (args.length <= 1) {
            return new String[0];
        }
        return Arrays.copyOfRange(args, 1, args.length);
    }

    private String joinArgs(String[] args, int startIndex) {
        if (args.length <= startIndex) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, startIndex, args.length));
    }

    private List<String> tabCompleteRoot(String[] args) {
        if (args.length == 1) {
            return filter(ROOT_SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && needsPlayer(args[0])) {
            return filter(plugin.getPlayerLookupService().suggestNames(args[1]), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("alt")) {
            return filter(ALT_SUBCOMMANDS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("ipblacklist")) {
            return filter(BLACKLIST_SUBCOMMANDS, args[1]);
        }
        if (args.length == 3 && List.of("tempban", "tempmute", "tempipban").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(DURATION_SUGGESTIONS, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("alt") && args[1].equalsIgnoreCase("mark")) {
            return List.of("old-old-acc", "same-ip", "suspicious-alt");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("alt") && args[1].equalsIgnoreCase("mark")) {
            return filter(plugin.getPlayerLookupService().suggestNames(args[3]), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("alt") && args[1].equalsIgnoreCase("mark")) {
            return filter(plugin.getPlayerLookupService().suggestNames(args[4]), args[4]);
        }
        return Collections.emptyList();
    }

    private List<String> tabCompleteDirect(String commandName, String[] args) {
        if (List.of("sban", "sunban", "smute", "sunmute", "skick", "bancheck", "banhistory", "scases", "snotes", "snote", "mute", "unmute", "kick", "ban", "unban", "cases", "notes", "check", "ipban", "sipban", "stempipban", "sunipban", "unipban").contains(commandName)) {
            if (args.length == 1) {
                return filter(plugin.getPlayerLookupService().suggestNames(args[0]), args[0]);
            }
        }
        if (List.of("stempban", "stempmute", "tempban", "tempmute", "stempipban").contains(commandName) && args.length == 2) {
            return filter(DURATION_SUGGESTIONS, args[1]);
        }
        if (commandName.equals("salt")) {
            if (args.length == 1) {
                return filter(ALT_SUBCOMMANDS, args[0]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("mark")) {
                return List.of("old-old-acc", "same-ip", "suspicious-alt");
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("mark")) {
                return filter(plugin.getPlayerLookupService().suggestNames(args[2]), args[2]);
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("mark")) {
                return filter(plugin.getPlayerLookupService().suggestNames(args[3]), args[3]);
            }
        }
        if (commandName.equals("sipblacklist") && args.length == 1) {
            return filter(BLACKLIST_SUBCOMMANDS, args[0]);
        }
        return Collections.emptyList();
    }

    private boolean needsPlayer(String subCommand) {
        return List.of("gui", "check", "cases", "history", "notes", "ban", "tempban", "unban", "mute", "tempmute", "unmute", "kick").contains(subCommand.toLowerCase(Locale.ROOT));
    }

    private List<String> filter(List<String> input, String prefix) {
        String lowered = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> output = new ArrayList<>();
        for (String value : input) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lowered)) {
                output.add(value);
            }
        }
        return output;
    }

    private record ResolvedIpTarget(PlayerIdentity player, String ipAddress) {
        private String displayTarget() {
            return player == null ? ipAddress : player.name();
        }
    }
}
