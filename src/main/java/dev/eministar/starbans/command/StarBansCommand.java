package dev.eministar.starbans.command;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.gui.AdminGuiFactory;
import dev.eministar.starbans.model.AppealStatus;
import dev.eministar.starbans.model.CasePriority;
import dev.eministar.starbans.model.CaseRecord;
import dev.eministar.starbans.model.CaseSearchFilter;
import dev.eministar.starbans.model.CaseStatus;
import dev.eministar.starbans.model.CaseType;
import dev.eministar.starbans.model.CaseVisibility;
import dev.eministar.starbans.model.CommandActor;
import dev.eministar.starbans.model.EvidenceType;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class StarBansCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT_SUBCOMMANDS = List.of(
            "help", "reload", "gui", "check", "cases", "case", "notes", "note", "ban", "tempban",
            "unban", "ipban", "tempipban", "unipban", "mute", "tempmute", "unmute", "kick", "alt", "ipblacklist",
            "warn", "watchlist", "template", "webhooktest", "audit", "undo", "reopen", "export", "dump", "setup", "feedback",
            "report", "queue", "appeal", "evidence", "incident", "review", "quarantine", "search", "import"
    );
    private static final List<String> DURATION_SUGGESTIONS = List.of("30m", "1h", "12h", "1d", "7d", "30d");
    private static final List<String> ALT_SUBCOMMANDS = List.of("mark", "list", "clear");
    private static final List<String> BLACKLIST_SUBCOMMANDS = List.of("add", "remove");
    private static final List<String> WATCHLIST_SUBCOMMANDS = List.of("add", "remove", "list");
    private static final List<String> TEMPLATE_SUBCOMMANDS = List.of("list", "info", "apply");
    private static final List<String> CASE_TAG_SUBCOMMANDS = List.of("add", "remove", "set", "clear");
    private static final List<String> QUEUE_SUBCOMMANDS = List.of("list", "claim", "priority");
    private static final List<String> APPEAL_SUBCOMMANDS = List.of("open", "reviewing", "accept", "deny", "note");
    private static final List<String> EVIDENCE_TYPES = List.of("link", "image", "video", "text");
    private static final List<String> INCIDENT_SUBCOMMANDS = List.of("create", "link");
    private static final List<String> REVIEW_SUBCOMMANDS = List.of("create", "done", "list");
    private static final List<String> QUARANTINE_SUBCOMMANDS = List.of("add", "remove");
    private static final List<String> CASE_PRIORITY_VALUES = List.of("low", "normal", "high", "critical");
    private static final List<String> IMPORT_SOURCES = List.of("starbans_json", "litebans_sqlite", "advancedban_sqlite");
    private static final List<String> SETUP_ROOT_SUBCOMMANDS = List.of("webhooks", "general");
    private static final List<String> SETUP_WEBHOOK_SUBCOMMANDS = List.of("list", "enabled", "default-url", "clear-default", "action-url", "clear-action-url", "action-enabled");
    private static final List<String> SETUP_GENERAL_SUBCOMMANDS = List.of("language", "timezone", "server-profile");

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
                case "report" -> executePlayerReport(sender, args, "COMMAND:REPORT");
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
            case "warn" -> executeWarn(sender, subArguments(args), "COMMAND:ROOT");
            case "watchlist" -> executeWatchlist(sender, subArguments(args), "COMMAND:ROOT");
            case "template" -> executeTemplate(sender, subArguments(args));
            case "webhooktest" -> executeWebhookTest(sender, subArguments(args));
            case "audit" -> executeAudit(sender, subArguments(args));
            case "undo" -> executeUndo(sender, subArguments(args));
            case "reopen" -> executeReopen(sender, subArguments(args));
            case "export" -> executeExport(sender, subArguments(args));
            case "dump" -> executeDump(sender, subArguments(args));
            case "setup" -> executeSetup(sender, subArguments(args));
            case "feedback" -> executeFeedback(sender, subArguments(args));
            case "report" -> executePlayerReport(sender, subArguments(args), "COMMAND:ROOT-REPORT");
            case "queue" -> executeQueue(sender, subArguments(args));
            case "appeal" -> executeAppeal(sender, subArguments(args));
            case "evidence" -> executeEvidence(sender, subArguments(args));
            case "incident" -> executeIncident(sender, subArguments(args));
            case "review" -> executeReview(sender, subArguments(args));
            case "quarantine" -> executeQuarantine(sender, subArguments(args), "COMMAND:ROOT-QUARANTINE");
            case "search" -> executeSearch(sender, subArguments(args));
            case "import" -> executeImport(sender, subArguments(args));
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
            plugin.getDiscordWebhookService().send(
                    "plugin-reloaded",
                    "actor", sender.getName(),
                    "actor_uuid", sender instanceof Player player ? player.getUniqueId() : "",
                    "source", "COMMAND:RELOAD"
            );
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
                "active_watchlist", summary.activeWatchlist() == null ? plugin.getLang().get("labels.none") : summary.activeWatchlist().getReason(),
                "case_count", summary.visibleCaseCount(),
                "note_count", summary.noteCount(),
                "alt_count", summary.altFlagCount(),
                "warn_count", summary.warnCount(),
                "warning_points", summary.warningPoints(),
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
        if (args.length > 0 && args[0].equalsIgnoreCase("tags")) {
            return executeCaseTags(sender, subArguments(args));
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
                "category", record.get().getCategory() == null ? plugin.getLang().get("labels.none") : record.get().getCategory(),
                "template_key", record.get().getTemplateKey() == null ? plugin.getLang().get("labels.none") : record.get().getTemplateKey(),
                "tags", record.get().getTagsDisplay().isBlank() ? plugin.getLang().get("labels.none") : record.get().getTagsDisplay(),
                "points", record.get().getPoints(),
                "visibility", record.get().getVisibility().name(),
                "reference_case_id", record.get().getReferenceCaseId() == null ? plugin.getLang().get("labels.none") : record.get().getReferenceCaseId(),
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

    private boolean executeCaseTags(CommandSender sender, String[] args) throws Exception {
        if (!hasPermission(sender, "starbans.command.tags")) {
            deny(sender);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-case-tags"));
            return true;
        }

        long caseId;
        try {
            caseId = Long.parseLong(args[0]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(plugin.getLang().prefixed("messages.invalid-case-id", "input", args[0]));
            return true;
        }

        List<String> tags = splitTags(args, 2);
        ModerationActionResult result = plugin.getModerationService().updateCaseTags(caseId, CommandActor.fromSender(sender), args[1], tags);
        if (result.type() == ModerationActionType.NOT_FOUND || result.caseRecord() == null) {
            sender.sendMessage(plugin.getLang().prefixed("messages.case-not-found", "id", caseId));
            return true;
        }

        sender.sendMessage(plugin.getLang().prefixed(
                "messages.case-tags-updated",
                "id", caseId,
                "tags", result.caseRecord().getTagsDisplay().isBlank() ? plugin.getLang().get("labels.none") : result.caseRecord().getTagsDisplay()
        ));
        return true;
    }

    private boolean executeWarn(CommandSender sender, String[] args, String source) throws Exception {
        if (!hasPermission(sender, "starbans.command.warn")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-warn"));
            return true;
        }

        Optional<PlayerIdentity> target = resolveTarget(sender, args[0]);
        if (target.isEmpty()) {
            return true;
        }

        int index = 1;
        int points = Math.max(1, plugin.getConfig().getInt("warnings.default-points", 1));
        if (index < args.length && args[index].matches("\\d+")) {
            points = Math.max(1, Integer.parseInt(args[index]));
            index++;
        }

        Long expiresAt = null;
        if (index < args.length && looksLikeDurationToken(args[index])) {
            Long duration = parseDuration(sender, args[index]);
            if (duration == Long.MIN_VALUE) {
                return true;
            }
            expiresAt = duration == null ? null : System.currentTimeMillis() + duration;
            index++;
        }

        ModerationActionResult result = plugin.getModerationService().warnPlayer(
                target.get(),
                CommandActor.fromSender(sender),
                joinArgs(args, index),
                points,
                expiresAt,
                source,
                null,
                null,
                List.of()
        );

        sender.sendMessage(plugin.getLang().prefixed(
                "messages.warn-success",
                "player", target.get().name(),
                "reason", result.caseRecord().getReason(),
                "points", result.caseRecord().getPoints(),
                "remaining", plugin.getModerationService().formatExpiry(result.caseRecord())
        ));

        PlayerSummary summary = plugin.getModerationService().getPlayerSummary(target.get());
        if (summary.activeBan() != null && result.caseRecord().getId() == safeReference(summary.activeBan())) {
            sender.sendMessage(plugin.getLang().prefixed("messages.warn-escalation-ban", "player", target.get().name(), "reason", summary.activeBan().getReason()));
        } else if (summary.activeMute() != null && result.caseRecord().getId() == safeReference(summary.activeMute())) {
            sender.sendMessage(plugin.getLang().prefixed("messages.warn-escalation-mute", "player", target.get().name(), "reason", summary.activeMute().getReason()));
        }
        return true;
    }

    private boolean executeWatchlist(CommandSender sender, String[] args, String source) throws Exception {
        if (!hasPermission(sender, "starbans.command.watchlist")) {
            deny(sender);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-watchlist"));
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        Optional<PlayerIdentity> target = resolveTarget(sender, args[1]);
        if (target.isEmpty()) {
            return true;
        }

        if (subCommand.equals("list")) {
            PlayerSummary summary = plugin.getModerationService().getPlayerSummary(target.get());
            if (summary.activeWatchlist() == null) {
                sender.sendMessage(plugin.getLang().prefixed("messages.watchlist-empty", "player", target.get().name()));
                return true;
            }
            sender.sendMessage(plugin.getLang().prefixed(
                    "messages.watchlist-entry",
                    "player", target.get().name(),
                    "reason", summary.activeWatchlist().getReason(),
                    "expires_at", plugin.getModerationService().formatExpiry(summary.activeWatchlist())
            ));
            return true;
        }

        if (subCommand.equals("remove")) {
            ModerationActionResult result = plugin.getModerationService().unwatchlistPlayer(target.get(), CommandActor.fromSender(sender), joinArgs(args, 2));
            if (result.type() == ModerationActionType.NOT_ACTIVE) {
                sender.sendMessage(plugin.getLang().prefixed("messages.watchlist-not-active", "player", target.get().name()));
                return true;
            }
            sender.sendMessage(plugin.getLang().prefixed("messages.watchlist-remove-success", "player", target.get().name()));
            return true;
        }

        if (!subCommand.equals("add")) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-watchlist"));
            return true;
        }

        int index = 2;
        Long expiresAt = null;
        if (index < args.length && looksLikeDurationToken(args[index])) {
            Long duration = parseDuration(sender, args[index]);
            if (duration == Long.MIN_VALUE) {
                return true;
            }
            expiresAt = duration == null ? null : System.currentTimeMillis() + duration;
            index++;
        }

        ModerationActionResult result = plugin.getModerationService().watchlistPlayer(
                target.get(),
                CommandActor.fromSender(sender),
                joinArgs(args, index),
                expiresAt,
                source,
                List.of("watchlist")
        );
        if (result.type() == ModerationActionType.ALREADY_ACTIVE) {
            sender.sendMessage(plugin.getLang().prefixed("messages.watchlist-already-active", "player", target.get().name()));
            return true;
        }

        sender.sendMessage(plugin.getLang().prefixed("messages.watchlist-add-success", "player", target.get().name(), "reason", result.caseRecord().getReason()));
        return true;
    }

    private boolean executeTemplate(CommandSender sender, String[] args) throws Exception {
        if (!hasPermission(sender, "starbans.command.template")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-template"));
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (subCommand.equals("list")) {
            List<dev.eministar.starbans.service.PunishmentTemplateService.Template> templates = plugin.getPunishmentTemplateService().getTemplates();
            sender.sendMessage(plugin.getLang().prefixed("messages.template-list-header", "count", templates.size()));
            for (dev.eministar.starbans.service.PunishmentTemplateService.Template template : templates) {
                sender.sendMessage(plugin.getLang().prefixed(
                        "messages.template-entry",
                        "key", template.key(),
                        "display", template.displayName(),
                        "type", template.type().name(),
                        "points", template.points(),
                        "duration", template.durationMillis() == null ? plugin.getLang().get("time.permanent") : TimeUtil.formatRemaining(System.currentTimeMillis() + template.durationMillis(), plugin.getLang())
                ));
            }
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-template"));
            return true;
        }

        Optional<dev.eministar.starbans.service.PunishmentTemplateService.Template> template = plugin.getPunishmentTemplateService().getTemplate(args[1]);
        if (template.isEmpty()) {
            sender.sendMessage(plugin.getLang().prefixed("messages.template-not-found", "key", args[1]));
            return true;
        }

        if (subCommand.equals("info")) {
            sender.sendMessage(plugin.getLang().prefixed(
                    "messages.template-info",
                    "key", template.get().key(),
                    "display", template.get().displayName(),
                    "type", template.get().type().name(),
                    "reason", template.get().reason(),
                    "category", template.get().category() == null ? plugin.getLang().get("labels.none") : template.get().category(),
                    "points", template.get().points(),
                    "visibility", template.get().visibility().name(),
                    "tags", template.get().tags().isEmpty() ? plugin.getLang().get("labels.none") : String.join(", ", template.get().tags())
            ));
            return true;
        }

        if (!subCommand.equals("apply") || args.length < 3) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-template"));
            return true;
        }

        Optional<PlayerIdentity> target = resolveTarget(sender, args[2]);
        if (target.isEmpty()) {
            return true;
        }

        String source = "TEMPLATE:" + template.get().key();
        Long expiresAt = template.get().durationMillis() == null ? null : System.currentTimeMillis() + template.get().durationMillis();

        switch (template.get().type()) {
            case BAN -> plugin.getModerationService().banPlayer(target.get(), CommandActor.fromSender(sender), template.get().reason(), expiresAt, source);
            case MUTE -> plugin.getModerationService().mutePlayer(target.get(), CommandActor.fromSender(sender), template.get().reason(), expiresAt, source);
            case KICK -> plugin.getModerationService().kickPlayer(target.get(), CommandActor.fromSender(sender), template.get().reason(), source);
            case NOTE -> plugin.getModerationService().addNote(target.get(), CommandActor.fromSender(sender), template.get().label(), template.get().reason(), source, template.get().visibility(), template.get().tags(), template.get().category(), template.get().key());
            case WARN -> plugin.getModerationService().warnPlayer(target.get(), CommandActor.fromSender(sender), template.get().reason(), Math.max(1, template.get().points()), expiresAt, source, template.get().category(), template.get().key(), template.get().tags());
            case WATCHLIST -> plugin.getModerationService().watchlistPlayer(target.get(), CommandActor.fromSender(sender), template.get().reason(), expiresAt, source, template.get().tags());
            case IP_BAN -> {
                ResolvedIpTarget ipTarget = resolveIpTarget(sender, target.get().name());
                if (ipTarget == null) {
                    return true;
                }
                plugin.getModerationService().banIp(ipTarget.ipAddress(), target.get(), CommandActor.fromSender(sender), template.get().reason(), expiresAt, source);
            }
            default -> {
                sender.sendMessage(plugin.getLang().prefixed("messages.template-unsupported", "key", template.get().key(), "type", template.get().type().name()));
                return true;
            }
        }

        sender.sendMessage(plugin.getLang().prefixed("messages.template-apply-success", "key", template.get().key(), "player", target.get().name()));
        return true;
    }

    private boolean executeWebhookTest(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "starbans.command.webhooktest")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-webhooktest"));
            return true;
        }

        plugin.getDiscordWebhookService().send(
                args[0],
                "player", "WebhookTarget",
                "target_player", "WebhookTarget",
                "target_uuid", "00000000-0000-0000-0000-000000000001",
                "related_player", "RelatedAccount",
                "related_uuid", "00000000-0000-0000-0000-000000000002",
                "ip", "127.0.0.1",
                "reason", "Webhook test dispatch",
                "actor", sender.getName(),
                "actor_uuid", sender instanceof Player player ? player.getUniqueId() : "",
                "source", "COMMAND:WEBHOOKTEST",
                "category", "testing",
                "template_key", "webhook-test",
                "tags", "test, webhook",
                "points", 3,
                "server_profile", plugin.getServerRuleService().getActiveProfileId(),
                "server_profile_name", plugin.getServerRuleService().getDisplayName()
        );
        sender.sendMessage(plugin.getLang().prefixed("messages.webhooktest-success", "action", args[0]));
        return true;
    }

    private boolean executeAudit(CommandSender sender, String[] args) throws Exception {
        if (!hasPermission(sender, "starbans.command.audit")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-audit"));
            return true;
        }

        int page = args.length >= 2 ? Math.max(0, parsePositiveInt(args[1], 1) - 1) : 0;
        int limit = Math.max(1, plugin.getConfig().getInt("audit.console-limit", 5));
        var snapshot = plugin.getAuditLogService().getSnapshot(args[0], limit, page);

        sender.sendMessage(plugin.getLang().prefixed("messages.audit-header", "actor", snapshot.actorName()));
        sender.sendMessage(plugin.getLang().prefixed(
                "messages.audit-summary",
                "total", snapshot.totalActions(),
                "bans", snapshot.bans(),
                "mutes", snapshot.mutes(),
                "warns", snapshot.warns(),
                "kicks", snapshot.kicks(),
                "notes", snapshot.notes(),
                "watchlists", snapshot.watchlists(),
                "status_changes", snapshot.statusChanges()
        ));

        for (CaseRecord record : snapshot.recentActions()) {
            sender.sendMessage(plugin.getLang().prefixed(
                    "messages.audit-entry",
                    "id", record.getId(),
                    "type", plugin.getModerationService().formatCaseType(record.getType()),
                    "player", record.getTargetPlayerName() == null ? plugin.getLang().get("labels.none") : record.getTargetPlayerName(),
                    "reason", record.getReason()
            ));
        }
        return true;
    }

    private boolean executeUndo(CommandSender sender, String[] args) throws Exception {
        if (!hasPermission(sender, "starbans.command.undo")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-undo"));
            return true;
        }

        long caseId;
        try {
            caseId = Long.parseLong(args[0]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(plugin.getLang().prefixed("messages.invalid-case-id", "input", args[0]));
            return true;
        }

        ModerationActionResult result = plugin.getModerationService().undoCase(caseId, CommandActor.fromSender(sender), joinArgs(args, 1));
        if (result.type() == ModerationActionType.NOT_FOUND) {
            sender.sendMessage(plugin.getLang().prefixed("messages.case-not-found", "id", caseId));
            return true;
        }
        if (result.type() == ModerationActionType.NOT_ACTIVE) {
            sender.sendMessage(plugin.getLang().prefixed("messages.case-not-active", "id", caseId));
            return true;
        }

        sender.sendMessage(plugin.getLang().prefixed("messages.undo-success", "id", caseId));
        return true;
    }

    private boolean executeReopen(CommandSender sender, String[] args) throws Exception {
        if (!hasPermission(sender, "starbans.command.reopen")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-reopen"));
            return true;
        }

        long caseId;
        try {
            caseId = Long.parseLong(args[0]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(plugin.getLang().prefixed("messages.invalid-case-id", "input", args[0]));
            return true;
        }

        ModerationActionResult result = plugin.getModerationService().reopenCase(caseId, CommandActor.fromSender(sender), joinArgs(args, 1));
        if (result.type() == ModerationActionType.NOT_FOUND) {
            sender.sendMessage(plugin.getLang().prefixed("messages.case-not-found", "id", caseId));
            return true;
        }
        if (result.type() == ModerationActionType.ALREADY_ACTIVE) {
            sender.sendMessage(plugin.getLang().prefixed("messages.case-already-active", "id", caseId));
            return true;
        }

        sender.sendMessage(plugin.getLang().prefixed("messages.reopen-success", "id", caseId));
        return true;
    }

    private boolean executeExport(CommandSender sender, String[] args) throws Exception {
        if (!hasPermission(sender, "starbans.command.export")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-export"));
            return true;
        }

        Optional<PlayerIdentity> target = resolveTarget(sender, args[0]);
        if (target.isEmpty()) {
            return true;
        }

        String format = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "txt";
        if (!format.equals("txt") && !format.equals("json")) {
            sender.sendMessage(plugin.getLang().prefixed("messages.export-invalid-format", "format", format));
            return true;
        }

        File file = plugin.getCaseExportService().exportPlayerCases(target.get(), format);
        sender.sendMessage(plugin.getLang().prefixed("messages.export-success", "player", target.get().name(), "file", file.getName()));
        return true;
    }

    private boolean executeDump(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "starbans.command.dump")) {
            deny(sender);
            return true;
        }

        sender.sendMessage(plugin.getLang().prefixed("messages.dump-started"));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                var result = plugin.getSupportDumpService().generateDump(sender);
                plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.getLang().prefixed(
                        "messages.dump-success",
                        "latest", result.latestFile().getName(),
                        "file", result.timestampedFile().getName()
                )));
            } catch (Exception exception) {
                LoggerUtil.error("The support dump could not be generated.", exception);
                plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.getLang().prefixed("messages.dump-failed")));
            }
        });
        return true;
    }

    private boolean executeSetup(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "starbans.command.setup")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-setup"));
            return true;
        }

        try {
            String branch = args[0].toLowerCase(Locale.ROOT);
            if (branch.equals("webhooks")) {
                return executeWebhookSetup(sender, subArguments(args));
            }
            if (branch.equals("general")) {
                return executeGeneralSetup(sender, subArguments(args));
            }
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-setup"));
            return true;
        } catch (Exception exception) {
            LoggerUtil.error("The setup command failed.", exception);
            sender.sendMessage(plugin.getLang().prefixed("messages.internal-error"));
            return true;
        }
    }

    private boolean executeWebhookSetup(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-setup-webhooks"));
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (subCommand.equals("list")) {
            sender.sendMessage(plugin.getLang().prefix() + plugin.getLang().format("&6Webhook Setup"));
            sender.sendMessage(plugin.getLang().prefix() + plugin.getLang().format("&7Global enabled: &f{value}", "value", plugin.getDiscordWebhookConfig().getConfiguration().getBoolean("enabled", false)));
            sender.sendMessage(plugin.getLang().prefix() + plugin.getLang().format("&7Default URL: &f{value}", "value", plugin.getDiscordWebhookConfig().getConfiguration().getString("default-url", plugin.getLang().get("labels.none"))));
            for (String action : plugin.getSetupService().getWebhookActions()) {
                sender.sendMessage(plugin.getLang().prefix() + plugin.getLang().format("&e{action} &8- &f{detail}", "action", action, "detail", plugin.getSetupService().describeWebhookAction(action)));
            }
            return true;
        }

        if (subCommand.equals("enabled") && args.length >= 2) {
            Boolean enabled = parseBooleanInput(args[1]);
            if (enabled == null) {
                sender.sendMessage(plugin.getLang().prefixed("messages.invalid-boolean", "input", args[1]));
                return true;
            }
            plugin.getSetupService().setWebhookGlobalEnabled(enabled);
            sender.sendMessage(plugin.getLang().prefixed("messages.setup-updated", "setting", "webhooks.enabled", "value", enabled));
            return true;
        }

        if (subCommand.equals("default-url") && args.length >= 2) {
            plugin.getSetupService().setWebhookDefaultUrl(args[1]);
            sender.sendMessage(plugin.getLang().prefixed("messages.setup-updated", "setting", "webhooks.default-url", "value", args[1]));
            return true;
        }

        if (subCommand.equals("clear-default")) {
            plugin.getSetupService().clearWebhookDefaultUrl();
            sender.sendMessage(plugin.getLang().prefixed("messages.setup-updated", "setting", "webhooks.default-url", "value", "cleared"));
            return true;
        }

        if (subCommand.equals("action-url") && args.length >= 3) {
            plugin.getSetupService().setWebhookActionUrl(args[1], args[2]);
            sender.sendMessage(plugin.getLang().prefixed("messages.setup-updated", "setting", "webhooks.action-url", "value", args[1] + " -> " + args[2]));
            return true;
        }

        if (subCommand.equals("clear-action-url") && args.length >= 2) {
            plugin.getSetupService().clearWebhookActionUrl(args[1]);
            sender.sendMessage(plugin.getLang().prefixed("messages.setup-updated", "setting", "webhooks.action-url", "value", args[1] + " -> cleared"));
            return true;
        }

        if (subCommand.equals("action-enabled") && args.length >= 3) {
            Boolean enabled = parseBooleanInput(args[2]);
            if (enabled == null) {
                sender.sendMessage(plugin.getLang().prefixed("messages.invalid-boolean", "input", args[2]));
                return true;
            }
            plugin.getSetupService().setWebhookActionEnabled(args[1], enabled);
            sender.sendMessage(plugin.getLang().prefixed("messages.setup-updated", "setting", "webhooks.action-enabled", "value", args[1] + " -> " + enabled));
            return true;
        }

        sender.sendMessage(plugin.getLang().prefixed("messages.usage-setup-webhooks"));
        return true;
    }

    private boolean executeGeneralSetup(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-setup-general"));
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        boolean success;
        String value = args[1];
        switch (subCommand) {
            case "language" -> success = plugin.getSetupService().setLanguageFile(value);
            case "timezone" -> success = plugin.getSetupService().setTimezone(value);
            case "server-profile" -> success = plugin.getSetupService().setServerProfile(value);
            default -> {
                sender.sendMessage(plugin.getLang().prefixed("messages.usage-setup-general"));
                return true;
            }
        }

        if (!success) {
            sender.sendMessage(plugin.getLang().prefixed("messages.reload-failed"));
            return true;
        }
        sender.sendMessage(plugin.getLang().prefixed("messages.setup-updated", "setting", "general." + subCommand, "value", value));
        return true;
    }

    private boolean executeFeedback(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "starbans.command.feedback")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-feedback"));
            return true;
        }

        String message = joinArgs(args, 0);
        if (message.length() < 6) {
            sender.sendMessage(plugin.getLang().prefixed("messages.feedback-too-short"));
            return true;
        }

        sender.sendMessage(plugin.getLang().prefixed("messages.feedback-started"));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                File latestDump = plugin.getSupportDumpService().getLatestDumpFile();
                var result = plugin.getFeedbackService().sendFeedback(sender, message, latestDump.exists() ? latestDump.getName() : "");
                plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.getLang().prefixed("messages.feedback-success")));
            } catch (Exception exception) {
                LoggerUtil.error("The feedback command failed.", exception);
                plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.getLang().prefixed("messages.feedback-failed")));
            }
        });
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
                    "visibility", record.getVisibility().name(),
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
        int textIndex = 2;
        CaseVisibility visibility = CaseVisibility.fromConfig(plugin.getConfig().getString("notes.default-visibility", "INTERNAL"));
        if (args.length >= 4 && (args[2].equalsIgnoreCase("internal") || args[2].equalsIgnoreCase("public"))) {
            visibility = CaseVisibility.fromConfig(args[2]);
            textIndex = 3;
        }
        if (args.length <= textIndex) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-note"));
            return true;
        }

        String text = joinArgs(args, textIndex);
        ModerationActionResult result = plugin.getModerationService().addNote(target.get(), CommandActor.fromSender(sender), label, text, source, visibility, List.of(), null, null);
        sender.sendMessage(plugin.getLang().prefixed("messages.note-success", "player", target.get().name(), "label", result.caseRecord().getLabel(), "visibility", visibility.name()));
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

    private boolean executePlayerReport(CommandSender sender, String[] args, String source) throws Exception {
        if (sender instanceof Player && !hasPermission(sender, "starbans.command.report")) {
            deny(sender);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-report"));
            return true;
        }

        Optional<PlayerIdentity> target = resolveTarget(sender, args[0]);
        if (target.isEmpty()) {
            return true;
        }

        int reasonIndex = 1;
        CasePriority priority = CasePriority.NORMAL;
        if (args.length >= 3 && CASE_PRIORITY_VALUES.contains(args[1].toLowerCase(Locale.ROOT))) {
            priority = CasePriority.fromConfig(args[1]);
            reasonIndex = 2;
        }

        String reason = joinArgs(args, reasonIndex);
        if (reason.isBlank()) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-report"));
            return true;
        }

        PlayerIdentity reporterIdentity = sender instanceof Player player
                ? new PlayerIdentity(player.getUniqueId(), player.getName())
                : new PlayerIdentity(null, sender.getName());
        ModerationActionResult result = plugin.getModerationService().reportPlayer(reporterIdentity, target.get(), reason, priority, source);
        sender.sendMessage(plugin.getLang().prefixed("messages.report-success", "player", target.get().name(), "reason", result.caseRecord().getReason(), "priority", result.caseRecord().getPriority().name()));
        return true;
    }

    private boolean executeQueue(CommandSender sender, String[] args) throws Exception {
        if (!hasPermission(sender, "starbans.command.queue")) {
            deny(sender);
            return true;
        }

        if (args.length < 1) {
            if (sender instanceof Player player) {
                AdminGuiFactory.openReportQueue(plugin, player, 0);
            } else {
                sender.sendMessage(plugin.getLang().prefixed("messages.usage-queue"));
            }
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (subCommand.equals("list") && sender instanceof Player player) {
            AdminGuiFactory.openReportQueue(plugin, player, 0);
            return true;
        }
        if (subCommand.equals("list")) {
            int limit = Math.max(1, plugin.getConfig().getInt("reports.queue.console-limit", 10));
            List<CaseRecord> reports = plugin.getModerationService().searchCases(
                    new CaseSearchFilter(CaseType.REPORT, CaseStatus.ACTIVE, null, null, null, null, null, null, null, null, null, null, null),
                    limit,
                    0
            );
            sender.sendMessage(plugin.getLang().prefixed("messages.queue-header", "count", reports.size()));
            for (CaseRecord report : reports) {
                sender.sendMessage(plugin.getLang().prefixed(
                        "messages.queue-entry",
                        "id", report.getId(),
                        "player", report.getTargetPlayerName(),
                        "actor", report.getRelatedPlayerName() == null ? report.getActorName() : report.getRelatedPlayerName(),
                        "reason", report.getReason(),
                        "priority", report.getPriority().name(),
                        "claim_actor", report.getClaimActorName() == null ? plugin.getLang().get("labels.none") : report.getClaimActorName()
                ));
            }
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-queue"));
            return true;
        }

        long caseId = parseCaseId(sender, args[1]);
        if (caseId < 0L) {
            return true;
        }

        if (subCommand.equals("claim")) {
            ModerationActionResult result = plugin.getModerationService().claimCase(caseId, CommandActor.fromSender(sender));
            if (result.type() == ModerationActionType.NOT_FOUND || result.caseRecord() == null) {
                sender.sendMessage(plugin.getLang().prefixed("messages.case-not-found", "id", caseId));
                return true;
            }
            sender.sendMessage(plugin.getLang().prefixed("messages.queue-claim-success", "id", caseId, "actor", sender.getName()));
            return true;
        }

        if (subCommand.equals("priority") && args.length >= 3) {
            ModerationActionResult result = plugin.getModerationService().setCasePriority(caseId, CommandActor.fromSender(sender), CasePriority.fromConfig(args[2]));
            if (result.type() == ModerationActionType.NOT_FOUND || result.caseRecord() == null) {
                sender.sendMessage(plugin.getLang().prefixed("messages.case-not-found", "id", caseId));
                return true;
            }
            sender.sendMessage(plugin.getLang().prefixed("messages.queue-priority-success", "id", caseId, "priority", result.caseRecord().getPriority().name()));
            return true;
        }

        sender.sendMessage(plugin.getLang().prefixed("messages.usage-queue"));
        return true;
    }

    private boolean executeAppeal(CommandSender sender, String[] args) throws Exception {
        if (!hasPermission(sender, "starbans.command.appeal")) {
            deny(sender);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-appeal"));
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        long caseId = parseCaseId(sender, args[1]);
        if (caseId < 0L) {
            return true;
        }

        AppealStatus status = switch (subCommand) {
            case "open" -> AppealStatus.OPEN;
            case "reviewing" -> AppealStatus.REVIEWING;
            case "accept" -> AppealStatus.ACCEPTED;
            case "deny" -> AppealStatus.DENIED;
            case "note" -> null;
            default -> AppealStatus.NONE;
        };
        if (!APPEAL_SUBCOMMANDS.contains(subCommand)) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-appeal"));
            return true;
        }

        int noteIndex = 2;
        Long deadlineAt = null;
        if (subCommand.equals("open") && args.length >= 3 && looksLikeDurationToken(args[2])) {
            Long duration = parseDuration(sender, args[2]);
            if (duration == Long.MIN_VALUE) {
                return true;
            }
            deadlineAt = duration == null ? null : System.currentTimeMillis() + duration;
            noteIndex = 3;
        }

        ModerationActionResult result = subCommand.equals("open")
                ? plugin.getModerationService().openAppeal(caseId, CommandActor.fromSender(sender), deadlineAt, joinArgs(args, noteIndex))
                : plugin.getModerationService().updateAppeal(caseId, CommandActor.fromSender(sender), status, null, joinArgs(args, noteIndex));
        if (result.type() == ModerationActionType.NOT_FOUND || result.caseRecord() == null) {
            sender.sendMessage(plugin.getLang().prefixed("messages.case-not-found", "id", caseId));
            return true;
        }
        sender.sendMessage(plugin.getLang().prefixed("messages.appeal-update-success", "id", caseId, "status", result.caseRecord().getAppealStatus().name()));
        return true;
    }

    private boolean executeEvidence(CommandSender sender, String[] args) throws Exception {
        if (!hasPermission(sender, "starbans.command.evidence")) {
            deny(sender);
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-evidence"));
            return true;
        }

        long caseId = parseCaseId(sender, args[0]);
        if (caseId < 0L) {
            return true;
        }

        ModerationActionResult result = plugin.getModerationService().addEvidence(
                caseId,
                CommandActor.fromSender(sender),
                EvidenceType.fromConfig(args[1]),
                args[2],
                joinArgs(args, 3)
        );
        if (result.type() == ModerationActionType.NOT_FOUND || result.caseRecord() == null) {
            sender.sendMessage(plugin.getLang().prefixed("messages.case-not-found", "id", caseId));
            return true;
        }
        sender.sendMessage(plugin.getLang().prefixed("messages.evidence-add-success", "id", caseId, "count", result.caseRecord().getEvidence().size()));
        return true;
    }

    private boolean executeIncident(CommandSender sender, String[] args) throws Exception {
        if (!hasPermission(sender, "starbans.command.incident")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-incident"));
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (subCommand.equals("create")) {
            if (args.length < 3) {
                sender.sendMessage(plugin.getLang().prefixed("messages.usage-incident"));
                return true;
            }
            CasePriority priority = CASE_PRIORITY_VALUES.contains(args[2].toLowerCase(Locale.ROOT)) ? CasePriority.fromConfig(args[2]) : CasePriority.HIGH;
            String description = CASE_PRIORITY_VALUES.contains(args[2].toLowerCase(Locale.ROOT)) ? joinArgs(args, 3) : joinArgs(args, 2);
            ModerationActionResult result = plugin.getModerationService().createIncident(args[1], CommandActor.fromSender(sender), args[1], description, priority, "COMMAND:INCIDENT");
            sender.sendMessage(plugin.getLang().prefixed("messages.incident-create-success", "id", result.caseRecord().getIncidentId(), "case_id", result.caseRecord().getId()));
            return true;
        }

        if (subCommand.equals("link") && args.length >= 3) {
            long caseId = parseCaseId(sender, args[1]);
            if (caseId < 0L) {
                return true;
            }
            ModerationActionResult result = plugin.getModerationService().linkCaseToIncident(caseId, args[2], CommandActor.fromSender(sender));
            if (result.type() == ModerationActionType.NOT_FOUND || result.caseRecord() == null) {
                sender.sendMessage(plugin.getLang().prefixed("messages.case-not-found", "id", caseId));
                return true;
            }
            sender.sendMessage(plugin.getLang().prefixed("messages.incident-link-success", "id", caseId, "incident_id", args[2]));
            return true;
        }

        sender.sendMessage(plugin.getLang().prefixed("messages.usage-incident"));
        return true;
    }

    private boolean executeReview(CommandSender sender, String[] args) throws Exception {
        if (!hasPermission(sender, "starbans.command.review")) {
            deny(sender);
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-review"));
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (subCommand.equals("list")) {
            List<CaseRecord> reviews = plugin.getModerationService().getDueReviews(Math.max(1, plugin.getConfig().getInt("reviews.console-limit", 10)), 0);
            sender.sendMessage(plugin.getLang().prefixed("messages.review-list-header", "count", reviews.size()));
            for (CaseRecord review : reviews) {
                sender.sendMessage(plugin.getLang().prefixed("messages.review-list-entry", "id", review.getId(), "player", review.getTargetPlayerName(), "reason", review.getReason(), "review_at", review.getNextReviewAt() == null ? plugin.getLang().get("labels.none") : plugin.getModerationService().formatDate(review.getNextReviewAt())));
            }
            return true;
        }

        if (subCommand.equals("create")) {
            if (args.length < 3) {
                sender.sendMessage(plugin.getLang().prefixed("messages.usage-review"));
                return true;
            }
            Optional<PlayerIdentity> target = resolveTarget(sender, args[1]);
            if (target.isEmpty()) {
                return true;
            }
            int reasonIndex = 2;
            Long reviewAt = System.currentTimeMillis();
            if (args.length >= 4 && looksLikeDurationToken(args[2])) {
                Long duration = parseDuration(sender, args[2]);
                if (duration == Long.MIN_VALUE) {
                    return true;
                }
                reviewAt = duration == null ? System.currentTimeMillis() : System.currentTimeMillis() + duration;
                reasonIndex = 3;
            }
            ModerationActionResult result = plugin.getModerationService().createReview(target.get(), CommandActor.fromSender(sender), joinArgs(args, reasonIndex), reviewAt, CasePriority.NORMAL, "COMMAND:REVIEW");
            sender.sendMessage(plugin.getLang().prefixed("messages.review-create-success", "player", target.get().name(), "id", result.caseRecord().getId()));
            return true;
        }

        if (subCommand.equals("done") && args.length >= 2) {
            long caseId = parseCaseId(sender, args[1]);
            if (caseId < 0L) {
                return true;
            }
            Long nextReviewAt = null;
            int noteIndex = 2;
            if (args.length >= 3 && looksLikeDurationToken(args[2])) {
                Long duration = parseDuration(sender, args[2]);
                if (duration == Long.MIN_VALUE) {
                    return true;
                }
                nextReviewAt = duration == null ? null : System.currentTimeMillis() + duration;
                noteIndex = 3;
            }
            ModerationActionResult result = plugin.getModerationService().markReview(caseId, CommandActor.fromSender(sender), joinArgs(args, noteIndex), nextReviewAt);
            if (result.type() == ModerationActionType.NOT_FOUND || result.caseRecord() == null) {
                sender.sendMessage(plugin.getLang().prefixed("messages.case-not-found", "id", caseId));
                return true;
            }
            sender.sendMessage(plugin.getLang().prefixed("messages.review-done-success", "id", caseId));
            return true;
        }

        sender.sendMessage(plugin.getLang().prefixed("messages.usage-review"));
        return true;
    }

    private boolean executeQuarantine(CommandSender sender, String[] args, String source) throws Exception {
        if (!hasPermission(sender, "starbans.command.quarantine")) {
            deny(sender);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-quarantine"));
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        Optional<PlayerIdentity> target = resolveTarget(sender, args[1]);
        if (target.isEmpty()) {
            return true;
        }

        if (subCommand.equals("remove")) {
            ModerationActionResult result = plugin.getModerationService().unquarantinePlayer(target.get(), CommandActor.fromSender(sender), joinArgs(args, 2));
            return handleRemovalResult(sender, target.get().name(), result, "messages.quarantine-remove-success", "messages.quarantine-not-active");
        }
        if (!subCommand.equals("add")) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-quarantine"));
            return true;
        }

        int index = 2;
        Long expiresAt = null;
        if (index < args.length && looksLikeDurationToken(args[index])) {
            Long duration = parseDuration(sender, args[index]);
            if (duration == Long.MIN_VALUE) {
                return true;
            }
            expiresAt = duration == null ? null : System.currentTimeMillis() + duration;
            index++;
        }

        ModerationActionResult result = plugin.getModerationService().quarantinePlayer(
                target.get(),
                CommandActor.fromSender(sender),
                joinArgs(args, index),
                expiresAt,
                CasePriority.HIGH,
                source
        );
        if (result.type() == ModerationActionType.ALREADY_ACTIVE) {
            sender.sendMessage(plugin.getLang().prefixed("messages.quarantine-already-active", "player", target.get().name()));
            return true;
        }
        sender.sendMessage(plugin.getLang().prefixed("messages.quarantine-add-success", "player", target.get().name(), "reason", result.caseRecord().getReason()));
        return true;
    }

    private boolean executeSearch(CommandSender sender, String[] args) throws Exception {
        if (!hasPermission(sender, "starbans.command.search")) {
            deny(sender);
            return true;
        }

        CaseType type = null;
        if (args.length >= 1 && !args[0].equals("*")) {
            type = parseCaseType(sender, args[0]);
            if (type == null) {
                return true;
            }
        }

        CaseStatus status = null;
        if (args.length >= 2 && !args[1].equals("*")) {
            status = parseCaseStatus(sender, args[1]);
            if (status == null) {
                return true;
            }
        }
        String actor = args.length >= 3 && !args[2].equals("*") ? args[2] : null;
        String tag = args.length >= 4 && !args[3].equals("*") ? args[3] : null;
        String profile = args.length >= 5 && !args[4].equals("*") ? args[4] : null;
        Long createdAfter = null;
        if (args.length >= 6) {
            try {
                int days = Math.max(0, Integer.parseInt(args[5]));
                createdAfter = System.currentTimeMillis() - days * 86_400_000L;
            } catch (NumberFormatException ignored) {
                createdAfter = null;
            }
        }

        List<CaseRecord> results = plugin.getModerationService().searchCases(
                new CaseSearchFilter(type, status, actor, null, tag, null, profile, null, createdAfter, null, null, null, null),
                Math.max(1, plugin.getConfig().getInt("search.console-limit", 12)),
                0
        );
        sender.sendMessage(plugin.getLang().prefixed("messages.search-header", "count", results.size()));
        for (CaseRecord record : results) {
            sender.sendMessage(plugin.getLang().prefixed("messages.search-entry", "id", record.getId(), "type", plugin.getModerationService().formatCaseType(record.getType()), "player", record.getTargetPlayerName(), "actor", record.getActorName(), "status", record.getStatus().name(), "priority", record.getPriority().name(), "reason", record.getReason()));
        }
        return true;
    }

    private boolean executeImport(CommandSender sender, String[] args) throws Exception {
        if (!hasPermission(sender, "starbans.command.import")) {
            deny(sender);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.getLang().prefixed("messages.usage-import"));
            return true;
        }

        String source = args[0];
        String location = joinArgs(args, 1);
        var summary = plugin.getMigrationService().importSource(source, location);
        sender.sendMessage(plugin.getLang().prefixed("messages.import-success", "source", summary.source(), "case_count", summary.importedCases(), "profile_count", summary.importedProfiles(), "skipped", summary.skippedRows()));
        return true;
    }

    private long parseCaseId(CommandSender sender, String input) {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException exception) {
            sender.sendMessage(plugin.getLang().prefixed("messages.invalid-case-id", "input", input));
            return -1L;
        }
    }

    private CaseType parseCaseType(CommandSender sender, String input) {
        try {
            return CaseType.valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(plugin.getLang().prefixed("messages.invalid-case-type", "input", input));
            return null;
        }
    }

    private CaseStatus parseCaseStatus(CommandSender sender, String input) {
        try {
            return CaseStatus.valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(plugin.getLang().prefixed("messages.invalid-case-status", "input", input));
            return null;
        }
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
        if (args.length == 2 && args[0].equalsIgnoreCase("watchlist")) {
            return filter(WATCHLIST_SUBCOMMANDS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("queue")) {
            return filter(QUEUE_SUBCOMMANDS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("appeal")) {
            return filter(APPEAL_SUBCOMMANDS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("incident")) {
            return filter(INCIDENT_SUBCOMMANDS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("review")) {
            return filter(REVIEW_SUBCOMMANDS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("quarantine")) {
            return filter(QUARANTINE_SUBCOMMANDS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("import")) {
            return filter(IMPORT_SOURCES, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("template")) {
            return filter(TEMPLATE_SUBCOMMANDS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setup")) {
            return filter(SETUP_ROOT_SUBCOMMANDS, args[1]);
        }
        if (args.length == 2 && List.of("case", "undo", "reopen", "webhooktest", "audit").contains(args[0].toLowerCase(Locale.ROOT))) {
            if (args[0].equalsIgnoreCase("case")) {
                return filter(List.of("tags"), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setup") && args[1].equalsIgnoreCase("webhooks")) {
            return filter(SETUP_WEBHOOK_SUBCOMMANDS, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setup") && args[1].equalsIgnoreCase("general")) {
            return filter(SETUP_GENERAL_SUBCOMMANDS, args[2]);
        }
        if (args.length == 3 && List.of("tempban", "tempmute", "tempipban").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(DURATION_SUGGESTIONS, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("warn")) {
            return filter(DURATION_SUGGESTIONS, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("report")) {
            return filter(plugin.getPlayerLookupService().suggestNames(args[1]), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("queue") && args[1].equalsIgnoreCase("priority")) {
            return filter(CASE_PRIORITY_VALUES, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("appeal") && args[1].equalsIgnoreCase("open")) {
            return filter(DURATION_SUGGESTIONS, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("review") && args[1].equalsIgnoreCase("create")) {
            return filter(plugin.getPlayerLookupService().suggestNames(args[2]), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("review") && args[1].equalsIgnoreCase("done")) {
            return filter(DURATION_SUGGESTIONS, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("quarantine") && args[1].equalsIgnoreCase("add")) {
            return filter(plugin.getPlayerLookupService().suggestNames(args[2]), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("quarantine") && args[1].equalsIgnoreCase("remove")) {
            return filter(plugin.getPlayerLookupService().suggestNames(args[2]), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("evidence")) {
            return filter(EVIDENCE_TYPES, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("watchlist") && args[1].equalsIgnoreCase("add")) {
            return filter(plugin.getPlayerLookupService().suggestNames(args[2]), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("watchlist") && List.of("remove", "list").contains(args[1].toLowerCase(Locale.ROOT))) {
            return filter(plugin.getPlayerLookupService().suggestNames(args[2]), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("template") && List.of("info", "apply").contains(args[1].toLowerCase(Locale.ROOT))) {
            return filter(plugin.getPunishmentTemplateService().getTemplateKeys(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("template") && args[1].equalsIgnoreCase("apply")) {
            return filter(plugin.getPlayerLookupService().suggestNames(args[3]), args[3]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("setup") && args[1].equalsIgnoreCase("webhooks") && List.of("action-url", "clear-action-url", "action-enabled").contains(args[2].toLowerCase(Locale.ROOT))) {
            return filter(plugin.getSetupService().getWebhookActions(), args[3]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("setup") && args[1].equalsIgnoreCase("general") && args[2].equalsIgnoreCase("language")) {
            return filter(List.of("lang-en.yml", "lang-de.yml"), args[3]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("setup") && args[1].equalsIgnoreCase("general") && args[2].equalsIgnoreCase("server-profile")) {
            return filter(List.of(plugin.getServerRuleService().getActiveProfileId()), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("setup") && args[1].equalsIgnoreCase("webhooks") && List.of("enabled", "action-enabled").contains(args[2].toLowerCase(Locale.ROOT))) {
            return filter(List.of("true", "false"), args[4]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("setup") && args[1].equalsIgnoreCase("webhooks") && args[2].equalsIgnoreCase("enabled")) {
            return filter(List.of("true", "false"), args[3]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("case") && args[1].equalsIgnoreCase("tags")) {
            return Collections.emptyList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("case") && args[1].equalsIgnoreCase("tags")) {
            return filter(CASE_TAG_SUBCOMMANDS, args[3]);
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
        if (commandName.equals("report")) {
            if (args.length == 1) {
                return filter(plugin.getPlayerLookupService().suggestNames(args[0]), args[0]);
            }
            if (args.length == 2) {
                return filter(CASE_PRIORITY_VALUES, args[1]);
            }
        }
        return Collections.emptyList();
    }

    private boolean needsPlayer(String subCommand) {
        return List.of("gui", "check", "cases", "history", "notes", "ban", "tempban", "unban", "mute", "tempmute", "unmute", "kick", "warn", "export", "report").contains(subCommand.toLowerCase(Locale.ROOT));
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

    private List<String> splitTags(String[] args, int startIndex) {
        if (args.length <= startIndex) {
            return List.of();
        }

        List<String> tags = new ArrayList<>();
        for (int index = startIndex; index < args.length; index++) {
            for (String tag : args[index].split(",")) {
                if (!tag.isBlank()) {
                    tags.add(tag.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return tags;
    }

    private boolean looksLikeDurationToken(String input) {
        return input != null && input.matches("(?i)^\\d+[smhdwoy].*");
    }

    private int parsePositiveInt(String input, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(input));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private Boolean parseBooleanInput(String input) {
        if (input == null) {
            return null;
        }
        if (input.equalsIgnoreCase("true") || input.equalsIgnoreCase("on") || input.equalsIgnoreCase("yes")) {
            return true;
        }
        if (input.equalsIgnoreCase("false") || input.equalsIgnoreCase("off") || input.equalsIgnoreCase("no")) {
            return false;
        }
        return null;
    }

    private long safeReference(CaseRecord record) {
        return record == null || record.getReferenceCaseId() == null ? -1L : record.getReferenceCaseId();
    }

    private record ResolvedIpTarget(PlayerIdentity player, String ipAddress) {
        private String displayTarget() {
            return player == null ? ipAddress : player.name();
        }
    }
}
