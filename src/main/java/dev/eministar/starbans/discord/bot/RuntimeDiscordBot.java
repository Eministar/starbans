package dev.eministar.starbans.discord.bot;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.discord.DiscordBotBridge;
import dev.eministar.starbans.discord.DiscordWorkflowOrigin;
import dev.eministar.starbans.discord.DiscordWorkflowRequest;
import dev.eministar.starbans.discord.DiscordWorkflowRequestKind;
import dev.eministar.starbans.model.CasePriority;
import dev.eministar.starbans.model.CaseRecord;
import dev.eministar.starbans.model.CaseStatus;
import dev.eministar.starbans.model.CommandActor;
import dev.eministar.starbans.model.EvidenceType;
import dev.eministar.starbans.model.ModerationActionResult;
import dev.eministar.starbans.model.ModerationActionType;
import dev.eministar.starbans.model.PlayerIdentity;
import dev.eministar.starbans.utils.LoggerUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.ChatColor;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class RuntimeDiscordBot extends ListenerAdapter implements DiscordBotBridge {

    private static final String PANEL_BUTTON_PREFIX = "starbans:workflow:panel:";
    private static final String PANEL_APPEAL_BUTTON = PANEL_BUTTON_PREFIX + "appeal";
    private static final String PANEL_UNBAN_BUTTON = PANEL_BUTTON_PREFIX + "unban";

    private static final String STAFF_BUTTON_PREFIX = "starbans:workflow:staff:";
    private static final String STAFF_ACTION_CLAIM = "claim";
    private static final String STAFF_ACTION_ACCEPT = "accept";
    private static final String STAFF_ACTION_DENY = "deny";

    private static final String MODAL_PREFIX = "starbans:workflow:modal:";
    private static final String MODAL_APPEAL = MODAL_PREFIX + "appeal";
    private static final String MODAL_UNBAN = MODAL_PREFIX + "unban";

    private static final String FIELD_PLAYER = "player";
    private static final String FIELD_CASE_ID = "case_id";
    private static final String FIELD_REASON = "reason";
    private static final String FIELD_EVIDENCE = "evidence";

    private final StarBans plugin;
    private JDA jda;

    public RuntimeDiscordBot(StarBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() throws Exception {
        String token = plugin.getConfig().getString("discord-bot.token", "");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("discord-bot.token is empty.");
        }

        JDABuilder builder = JDABuilder.createDefault(token.trim());
        builder.setEnableShutdownHook(false);
        builder.setMemberCachePolicy(MemberCachePolicy.DEFAULT);
        builder.setChunkingFilter(ChunkingFilter.NONE);
        builder.setActivity(resolveActivity());
        builder.addEventListeners(this);

        jda = builder.build();
        jda.awaitReady();
        applyConfiguredNickname();
        registerSlashCommands();
        ensureWorkflowPanel();
        syncKnownWorkflowRequests();
        LoggerUtil.success("Discord bot integration enabled.");
    }

    private void registerSlashCommands() {
        List<CommandData> commands = new ArrayList<>();
        if (plugin.getConfig().getBoolean("discord-bot.commands.case-lookup", true)) {
            commands.add(
                    Commands.slash("case", discordText("commands.case.description", "Look up a moderation case"))
                            .addOption(OptionType.INTEGER, "case_id", discordText("commands.case.options.case-id", "The StarBans case id"), true)
            );
        }
        if (plugin.getConfig().getBoolean("discord-bot.commands.report", true)) {
            commands.add(
                    Commands.slash("report", discordText("commands.report.description", "Submit a player report"))
                            .addOption(OptionType.STRING, "player", discordText("commands.report.options.player", "Minecraft player name"), true)
                            .addOption(OptionType.STRING, "reason", discordText("commands.report.options.reason", "Reason for the report"), true)
                            .addOption(OptionType.STRING, "priority", discordText("commands.report.options.priority", "Queue priority"), false)
            );
        }
        if (plugin.getConfig().getBoolean("discord-bot.commands.appeal", true)) {
            commands.add(
                    Commands.slash("appeal", discordText("commands.appeal.description", "Open an appeal for a case"))
                            .addOption(OptionType.INTEGER, "case_id", discordText("commands.appeal.options.case-id", "The StarBans case id"), true)
                            .addOption(OptionType.STRING, "message", discordText("commands.appeal.options.message", "Appeal note or explanation"), true)
                            .addOption(OptionType.STRING, "deadline", discordText("commands.appeal.options.deadline", "Optional duration like 7d or 12h"), false)
            );
        }
        if (plugin.getConfig().getBoolean("discord-bot.commands.unban-request", true)) {
            commands.add(
                    Commands.slash("unban-request", discordText("commands.unban-request.description", "Create an unban request for an active ban"))
                            .addOption(OptionType.STRING, "player", discordText("commands.unban-request.options.player", "Minecraft player name"), true)
                            .addOption(OptionType.STRING, "reason", discordText("commands.unban-request.options.reason", "Why the ban should be reviewed"), true)
            );
        }

        Guild configuredGuild = resolveConfiguredGuild(null);
        if (configuredGuild != null) {
            configuredGuild.updateCommands().addCommands(commands).complete();
            LoggerUtil.info("Discord bot slash commands registered for guild " + configuredGuild.getName() + '.');
            return;
        }

        jda.updateCommands().addCommands(commands).complete();
        LoggerUtil.info("Discord bot slash commands registered globally.");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue(
                hook -> plugin.getServer().getScheduler().runTask(plugin, () -> handleInteraction(event, hook)),
                failure -> LoggerUtil.warn("A Discord slash command could not be acknowledged: " + failure.getMessage())
        );
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (PANEL_APPEAL_BUTTON.equals(componentId)) {
            event.replyModal(buildRequestModal(DiscordWorkflowRequestKind.APPEAL)).queue();
            return;
        }
        if (PANEL_UNBAN_BUTTON.equals(componentId)) {
            event.replyModal(buildRequestModal(DiscordWorkflowRequestKind.UNBAN_REQUEST)).queue();
            return;
        }
        if (!componentId.startsWith(STAFF_BUTTON_PREFIX)) {
            return;
        }

        event.deferReply(true).queue(
                hook -> plugin.getServer().getScheduler().runTask(plugin, () -> handleStaffButton(event, hook)),
                failure -> LoggerUtil.warn("A Discord workflow button could not be acknowledged: " + failure.getMessage())
        );
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().startsWith(MODAL_PREFIX)) {
            return;
        }

        event.deferReply(true).queue(
                hook -> plugin.getServer().getScheduler().runTask(plugin, () -> handleWorkflowModal(event, hook)),
                failure -> LoggerUtil.warn("A Discord workflow modal could not be acknowledged: " + failure.getMessage())
        );
    }

    private void handleInteraction(SlashCommandInteractionEvent event, InteractionHook hook) {
        try {
            switch (event.getName()) {
                case "case" -> handleCaseLookup(event, hook);
                case "report" -> handleReport(event, hook);
                case "appeal" -> handleAppeal(event, hook);
                case "unban-request" -> handleUnbanRequest(event, hook);
                default -> replyTemplate(hook, "replies.unknown-command", "warning");
            }
        } catch (Exception exception) {
            LoggerUtil.error("A Discord bot command failed.", exception);
            replyTemplate(hook, "replies.request-failed", "danger");
        }
    }

    private void handleCaseLookup(SlashCommandInteractionEvent event, InteractionHook hook) throws Exception {
        long caseId = requiredOption(event, "case_id").getAsLong();
        Optional<CaseRecord> record = plugin.getModerationService().getCase(caseId);
        if (record.isEmpty()) {
            replyTemplate(hook, "replies.case-not-found", "warning", "case_id", caseId, "id", caseId);
            return;
        }
        reply(hook, buildCaseLookupEmbed(record.get()));
    }

    private void handleReport(SlashCommandInteractionEvent event, InteractionHook hook) throws Exception {
        String playerName = requiredOption(event, "player").getAsString();
        Optional<PlayerIdentity> target = plugin.getPlayerLookupService().resolve(playerName);
        if (target.isEmpty()) {
            replyTemplate(hook, "replies.player-unknown", "warning", "player", playerName);
            return;
        }

        CasePriority priority = CasePriority.fromConfig(optionalOption(event, "priority"));
        ModerationActionResult result = plugin.getModerationService().reportPlayer(
                new PlayerIdentity(null, discordDisplayName(event.getUser(), event.getMember())),
                target.get(),
                requiredOption(event, "reason").getAsString(),
                priority,
                "DISCORD:REPORT"
        );
        replyTemplate(
                hook,
                "replies.report-created",
                "success",
                "case_id", result.caseRecord().getId(),
                "id", result.caseRecord().getId(),
                "player", target.get().name()
        );
    }

    private void handleAppeal(SlashCommandInteractionEvent event, InteractionHook hook) throws Exception {
        long caseId = requiredOption(event, "case_id").getAsLong();
        String deadlineInput = optionalOption(event, "deadline");
        Long deadlineAt = null;
        if (deadlineInput != null && !deadlineInput.isBlank()) {
            Long duration = dev.eministar.starbans.service.TimeUtil.parseDuration(deadlineInput);
            deadlineAt = duration == null ? null : System.currentTimeMillis() + duration;
        }

        ModerationActionResult result = plugin.getModerationService().openWorkflowRequest(
                caseId,
                discordActor(event.getUser(), event.getMember()),
                deadlineAt,
                requiredOption(event, "message").getAsString(),
                DiscordWorkflowRequestKind.APPEAL,
                DiscordWorkflowOrigin.DISCORD,
                event.getUser().getId(),
                discordDisplayName(event.getUser(), event.getMember()),
                event.getGuild() == null ? null : event.getGuild().getId()
        );
        if (result.type() == ModerationActionType.NOT_FOUND || result.caseRecord() == null) {
            replyTemplate(hook, "replies.case-not-found", "warning", "case_id", caseId, "id", caseId);
            return;
        }
        replyTemplate(hook, "replies.appeal-opened", "success", "case_id", caseId, "id", caseId);
    }

    private void handleUnbanRequest(SlashCommandInteractionEvent event, InteractionHook hook) throws Exception {
        String playerName = requiredOption(event, "player").getAsString();
        Optional<PlayerIdentity> target = plugin.getPlayerLookupService().resolve(playerName);
        if (target.isEmpty()) {
            replyTemplate(hook, "replies.player-unknown", "warning", "player", playerName);
            return;
        }

        Optional<CaseRecord> activeBan = plugin.getModerationService().getActivePlayerBan(target.get().uniqueId());
        if (activeBan.isEmpty()) {
            replyTemplate(hook, "replies.no-active-ban", "warning", "player", target.get().name());
            return;
        }

        ModerationActionResult result = plugin.getModerationService().openWorkflowRequest(
                activeBan.get().getId(),
                discordActor(event.getUser(), event.getMember()),
                null,
                discordText("workflow.note-prefix.unban-request", "Discord unban request") + ": " + requiredOption(event, "reason").getAsString(),
                DiscordWorkflowRequestKind.UNBAN_REQUEST,
                DiscordWorkflowOrigin.DISCORD,
                event.getUser().getId(),
                discordDisplayName(event.getUser(), event.getMember()),
                event.getGuild() == null ? null : event.getGuild().getId()
        );
        if (result.type() == ModerationActionType.NOT_FOUND || result.caseRecord() == null) {
            replyTemplate(hook, "replies.unban-request-update-failed", "danger", "player", target.get().name());
            return;
        }
        replyTemplate(
                hook,
                "replies.unban-request-attached",
                "success",
                "case_id", activeBan.get().getId(),
                "id", activeBan.get().getId(),
                "player", target.get().name()
        );
    }

    private void handleStaffButton(ButtonInteractionEvent event, InteractionHook hook) {
        try {
            if (!hasStaffPermission(event.getMember())) {
                replyTemplate(hook, "replies.no-staff-permission", "danger");
                return;
            }

            StaffButtonAction action = parseStaffAction(event.getComponentId());
            if (action == null) {
                replyTemplate(hook, "replies.unknown-workflow-action", "warning");
                return;
            }

            CommandActor actor = discordActor(event.getUser(), event.getMember());
            ModerationActionResult result = switch (action.action()) {
                case STAFF_ACTION_CLAIM -> plugin.getModerationService().claimCase(action.caseId(), actor);
                case STAFF_ACTION_ACCEPT -> plugin.getModerationService().acceptAppeal(action.caseId(), actor, discordText("workflow.notes.accept", "Accepted via Discord workflow"));
                case STAFF_ACTION_DENY -> plugin.getModerationService().denyAppeal(action.caseId(), actor, discordText("workflow.notes.deny", "Denied via Discord workflow"));
                default -> null;
            };

            if (result == null) {
                replyTemplate(hook, "replies.unknown-workflow-action", "warning");
                return;
            }
            if (result.type() == ModerationActionType.NOT_FOUND || result.caseRecord() == null) {
                replyTemplate(hook, "replies.case-not-found", "warning", "case_id", action.caseId(), "id", action.caseId());
                return;
            }
            if (result.type() == ModerationActionType.NOT_ACTIVE && action.action().equals(STAFF_ACTION_ACCEPT)) {
                replyTemplate(hook, "replies.case-no-longer-active", "warning", "case_id", action.caseId(), "id", action.caseId());
                return;
            }

            syncWorkflowCase(action.caseId());
            String replyKey = switch (action.action()) {
                case STAFF_ACTION_CLAIM -> "replies.case-claimed";
                case STAFF_ACTION_ACCEPT -> "replies.appeal-accepted";
                case STAFF_ACTION_DENY -> "replies.appeal-denied";
                default -> "replies.workflow-updated";
            };
            String colorKey = switch (action.action()) {
                case STAFF_ACTION_ACCEPT, STAFF_ACTION_CLAIM -> "success";
                case STAFF_ACTION_DENY -> "danger";
                default -> "info";
            };
            replyTemplate(
                    hook,
                    replyKey,
                    colorKey,
                    "case_id", action.caseId(),
                    "id", action.caseId(),
                    "actor", discordDisplayName(event.getUser(), event.getMember())
            );
        } catch (Exception exception) {
            LoggerUtil.error("A Discord workflow staff action failed.", exception);
            replyTemplate(hook, "replies.workflow-action-failed", "danger");
        }
    }

    private void handleWorkflowModal(ModalInteractionEvent event, InteractionHook hook) {
        try {
            DiscordWorkflowRequestKind kind = event.getModalId().endsWith("unban")
                    ? DiscordWorkflowRequestKind.UNBAN_REQUEST
                    : DiscordWorkflowRequestKind.APPEAL;
            String playerInput = modalValue(event, FIELD_PLAYER);
            String caseIdInput = modalValue(event, FIELD_CASE_ID);
            String reason = modalValue(event, FIELD_REASON);
            String evidence = modalValue(event, FIELD_EVIDENCE);

            Optional<CaseRecord> targetCase = resolveWorkflowTarget(playerInput, caseIdInput, kind);
            if (targetCase.isEmpty()) {
                replyTemplate(
                        hook,
                        kind == DiscordWorkflowRequestKind.UNBAN_REQUEST
                                ? "replies.unban-target-missing"
                                : "replies.appeal-target-missing",
                        "warning"
                );
                return;
            }

            String notePrefix = kind == DiscordWorkflowRequestKind.UNBAN_REQUEST
                    ? discordText("workflow.note-prefix.unban-request", "Discord unban request")
                    : discordText("workflow.note-prefix.appeal", "Discord appeal");
            ModerationActionResult result = plugin.getModerationService().openWorkflowRequest(
                    targetCase.get().getId(),
                    discordActor(event.getUser(), event.getMember()),
                    null,
                    notePrefix + ": " + reason,
                    kind,
                    DiscordWorkflowOrigin.DISCORD,
                    event.getUser().getId(),
                    discordDisplayName(event.getUser(), event.getMember()),
                    event.getGuild() == null ? null : event.getGuild().getId()
            );
            if (result.caseRecord() == null) {
                replyTemplate(hook, "replies.request-attach-failed", "danger");
                return;
            }

            if (evidence != null && !evidence.isBlank()) {
                plugin.getModerationService().addEvidence(
                        result.caseRecord().getId(),
                        discordActor(event.getUser(), event.getMember()),
                        EvidenceType.LINK,
                        evidence,
                        discordText("workflow.note-prefix.evidence", "Discord workflow evidence")
                );
            }

            replyTemplate(
                    hook,
                    kind == DiscordWorkflowRequestKind.UNBAN_REQUEST
                            ? "replies.unban-request-submitted"
                            : "replies.appeal-submitted",
                    "success",
                    "case_id", result.caseRecord().getId(),
                    "id", result.caseRecord().getId()
            );
        } catch (Exception exception) {
            LoggerUtil.error("A Discord workflow modal submission failed.", exception);
            replyTemplate(hook, "replies.workflow-submit-failed", "danger");
        }
    }

    private Optional<CaseRecord> resolveWorkflowTarget(String playerInput,
                                                       String caseIdInput,
                                                       DiscordWorkflowRequestKind kind) throws Exception {
        if (caseIdInput != null && !caseIdInput.isBlank()) {
            try {
                return plugin.getModerationService().getCase(Long.parseLong(caseIdInput.trim()));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        }

        if (playerInput == null || playerInput.isBlank()) {
            return Optional.empty();
        }

        Optional<PlayerIdentity> target = plugin.getPlayerLookupService().resolve(playerInput.trim());
        if (target.isEmpty() || target.get().uniqueId() == null) {
            return Optional.empty();
        }
        if (kind == DiscordWorkflowRequestKind.UNBAN_REQUEST) {
            return plugin.getModerationService().getActivePlayerBan(target.get().uniqueId());
        }

        Optional<CaseRecord> activeBan = plugin.getModerationService().getActivePlayerBan(target.get().uniqueId());
        if (activeBan.isPresent()) {
            return activeBan;
        }
        Optional<CaseRecord> activeMute = plugin.getModerationService().getActivePlayerMute(target.get().uniqueId());
        if (activeMute.isPresent()) {
            return activeMute;
        }
        Optional<CaseRecord> activeQuarantine = plugin.getModerationService().getActiveQuarantine(target.get().uniqueId());
        if (activeQuarantine.isPresent()) {
            return activeQuarantine;
        }
        return plugin.getModerationService().getActiveWatchlist(target.get().uniqueId());
    }

    private MessageEmbed buildCaseLookupEmbed(CaseRecord record) {
        return buildConfiguredEmbed(
                "embeds.case-lookup",
                discordColor("info", new Color(88, 166, 255)),
                discordReplacements(record, plugin.getDiscordWorkflowStateStore().getRequest(record.getId()).orElse(null))
        );
    }

    @Override
    public void syncWorkflowCase(long caseId) throws Exception {
        Optional<DiscordWorkflowRequest> requestOptional = plugin.getDiscordWorkflowStateStore().getRequest(caseId);
        if (requestOptional.isEmpty()) {
            return;
        }

        Optional<CaseRecord> recordOptional = plugin.getModerationService().getCase(caseId);
        if (recordOptional.isEmpty()) {
            plugin.getDiscordWorkflowStateStore().removeRequest(caseId);
            return;
        }

        CaseRecord record = recordOptional.get();
        DiscordWorkflowRequest request = requestOptional.get();
        TextChannel staffChannel = resolveStaffChannel(request);
        if (staffChannel == null) {
            return;
        }

        ensureStaffReviewMessage(staffChannel, record, request);
        notifyDecisionIfNeeded(record, request);
    }

    private void ensureStaffReviewMessage(TextChannel staffChannel,
                                          CaseRecord record,
                                          DiscordWorkflowRequest request) {
        List<ActionRow> actionRows = buildStaffActionRows(record);
        MessageEmbed embed = buildStaffEmbed(record, request);
        String configuredChannelId = configuredStaffChannelId();
        if (!configuredChannelId.isBlank() && !configuredChannelId.equals(normalizeChannelId(request.staffChannelId()))) {
            postStaffReviewMessage(staffChannel, embed, actionRows, request);
            return;
        }
        if (request.staffMessageId() == null || request.staffMessageId().isBlank()) {
            postStaffReviewMessage(staffChannel, embed, actionRows, request);
            return;
        }

        try {
            staffChannel.retrieveMessageById(request.staffMessageId()).queue(
                    message -> message.editMessageEmbeds(embed)
                            .setComponents(actionRows)
                            .queue(
                                    success -> {
                                    },
                                    failure -> postStaffReviewMessage(staffChannel, embed, actionRows, request)
                            ),
                    failure -> postStaffReviewMessage(staffChannel, embed, actionRows, request)
            );
        } catch (Exception exception) {
            LoggerUtil.error("A Discord workflow review message could not be updated.", exception);
        }
    }

    private void postStaffReviewMessage(TextChannel staffChannel,
                                        MessageEmbed embed,
                                        List<ActionRow> actionRows,
                                        DiscordWorkflowRequest request) {
        try {
            staffChannel.sendMessageEmbeds(embed)
                    .setComponents(actionRows)
                    .queue(
                            message -> plugin.getDiscordWorkflowStateStore().upsertRequest(request.withStaffMessage(staffChannel.getId(), message.getId())),
                            failure -> LoggerUtil.warn("A Discord workflow review message could not be sent: " + failure.getMessage())
                    );
        } catch (Exception exception) {
            LoggerUtil.error("A Discord workflow review message could not be created.", exception);
        }
    }

    private void notifyDecisionIfNeeded(CaseRecord record, DiscordWorkflowRequest request) {
        if (!plugin.getConfig().getBoolean("discord-bot.appeals.decision-dm-enabled", true)) {
            return;
        }
        if (request.requesterDiscordUserId() == null || request.requesterDiscordUserId().isBlank()) {
            return;
        }
        if (request.decisionNotifiedAt() != null) {
            return;
        }
        if (record.getAppealStatus() != dev.eministar.starbans.model.AppealStatus.ACCEPTED
                && record.getAppealStatus() != dev.eministar.starbans.model.AppealStatus.DENIED) {
            return;
        }

        jda.retrieveUserById(request.requesterDiscordUserId()).queue(
                user -> sendDecisionDm(record, user, request),
                failure -> {
                    plugin.getDiscordWorkflowStateStore().markDecisionNotified(record.getId(), System.currentTimeMillis());
                    LoggerUtil.warn("A Discord decision DM user could not be resolved: " + failure.getMessage());
                }
        );
    }

    private void sendDecisionDm(CaseRecord record, User user, DiscordWorkflowRequest request) {
        boolean accepted = record.getAppealStatus() == dev.eministar.starbans.model.AppealStatus.ACCEPTED;
        MessageEmbed embed = buildConfiguredEmbed(
                accepted ? "embeds.dm.accepted" : "embeds.dm.denied",
                discordColor(accepted ? "success" : "danger", accepted ? Color.GREEN : Color.RED),
                discordReplacements(record, request, "decision", accepted ? "accepted" : "denied")
        );

        user.openPrivateChannel().flatMap(channel -> channel.sendMessageEmbeds(embed)).queue(
                success -> plugin.getDiscordWorkflowStateStore().markDecisionNotified(record.getId(), System.currentTimeMillis()),
                failure -> {
                    plugin.getDiscordWorkflowStateStore().markDecisionNotified(record.getId(), System.currentTimeMillis());
                    LoggerUtil.warn("A Discord decision DM could not be delivered: " + failure.getMessage());
                }
        );
    }

    private void ensureWorkflowPanel() {
        if (!plugin.getConfig().getBoolean("discord-bot.appeals.post-panel-on-startup", true)) {
            return;
        }

        TextChannel channel = configuredPanelChannel();
        if (channel == null) {
            return;
        }

        Optional<String> existingMessageId = plugin.getDiscordWorkflowStateStore().getPanelMessageId(channel.getGuild().getId(), channel.getId());
        if (existingMessageId.isPresent()) {
            try {
                channel.retrieveMessageById(existingMessageId.get()).complete()
                        .editMessageEmbeds(buildPanelEmbed())
                        .setActionRow(buildPanelButtons().toArray(Button[]::new))
                        .complete();
                return;
            } catch (Exception ignored) {
            }
        }

        try {
            String messageId = channel.sendMessageEmbeds(buildPanelEmbed())
                    .setActionRow(buildPanelButtons().toArray(Button[]::new))
                    .complete()
                    .getId();
            plugin.getDiscordWorkflowStateStore().setPanelMessageId(channel.getGuild().getId(), channel.getId(), messageId);
        } catch (Exception exception) {
            LoggerUtil.error("The Discord workflow panel could not be posted.", exception);
        }
    }

    private void syncKnownWorkflowRequests() {
        for (DiscordWorkflowRequest request : plugin.getDiscordWorkflowStateStore().getRequests()) {
            try {
                syncWorkflowCase(request.caseId());
            } catch (Exception exception) {
                LoggerUtil.error("A stored Discord workflow request could not be synchronized.", exception);
            }
        }
    }

    private MessageEmbed buildPanelEmbed() {
        return buildConfiguredEmbed(
                "embeds.panel",
                discordColor("panel", new Color(255, 184, 77))
        );
    }

    private List<Button> buildPanelButtons() {
        return List.of(
                Button.primary(PANEL_APPEAL_BUTTON, discordText("panel.buttons.appeal", "Submit Appeal")),
                Button.success(PANEL_UNBAN_BUTTON, discordText("panel.buttons.unban-request", "Submit Unban Request"))
        );
    }

    private Modal buildRequestModal(DiscordWorkflowRequestKind kind) {
        String key = kind == DiscordWorkflowRequestKind.UNBAN_REQUEST ? "modals.unban-request" : "modals.appeal";
        String title = discordText(key + ".title", kind == DiscordWorkflowRequestKind.UNBAN_REQUEST ? "Unban Request" : "Appeal Request");
        return Modal.create(kind == DiscordWorkflowRequestKind.UNBAN_REQUEST ? MODAL_UNBAN : MODAL_APPEAL, title)
                .addComponents(
                        ActionRow.of(TextInput.create(FIELD_PLAYER, discordText(key + ".player.label", "Minecraft Name"), TextInputStyle.SHORT)
                                .setPlaceholder(discordText(key + ".player.placeholder", "Your Minecraft name"))
                                .setRequired(true)
                                .setMaxLength(32)
                                .build()),
                        ActionRow.of(TextInput.create(FIELD_CASE_ID, discordText(key + ".case-id.label", "Case ID"), TextInputStyle.SHORT)
                                .setPlaceholder(discordText(key + ".case-id.placeholder", "Optional existing case id"))
                                .setRequired(false)
                                .setMaxLength(20)
                                .build()),
                        ActionRow.of(TextInput.create(FIELD_REASON, discordText(key + ".reason.label", "Reason"), TextInputStyle.PARAGRAPH)
                                .setPlaceholder(discordText(
                                        key + ".reason.placeholder",
                                        kind == DiscordWorkflowRequestKind.UNBAN_REQUEST
                                                ? "Why should your ban be reviewed?"
                                                : "Explain your appeal."
                                ))
                                .setRequired(true)
                                .setMinLength(10)
                                .setMaxLength(1000)
                                .build()),
                        ActionRow.of(TextInput.create(FIELD_EVIDENCE, discordText(key + ".evidence.label", "Evidence / Link"), TextInputStyle.SHORT)
                                .setPlaceholder(discordText(key + ".evidence.placeholder", "Optional proof, clip or screenshot URL"))
                                .setRequired(false)
                                .setMaxLength(500)
                                .build())
                )
                .build();
    }

    private List<ActionRow> buildStaffActionRows(CaseRecord record) {
        if (record.getAppealStatus() != dev.eministar.starbans.model.AppealStatus.OPEN) {
            return List.of();
        }
        return List.of(ActionRow.of(
                Button.secondary(staffButtonId(STAFF_ACTION_CLAIM, record.getId()), discordText("staff.buttons.claim", "Claim")),
                Button.success(staffButtonId(STAFF_ACTION_ACCEPT, record.getId()), discordText("staff.buttons.accept", "Accept")),
                Button.danger(staffButtonId(STAFF_ACTION_DENY, record.getId()), discordText("staff.buttons.deny", "Deny"))
        ));
    }

    private MessageEmbed buildStaffEmbed(CaseRecord record, DiscordWorkflowRequest request) {
        return buildConfiguredEmbed(
                "embeds.staff-review",
                colorFor(record),
                discordReplacements(record, request)
        );
    }

    private Color colorFor(CaseRecord record) {
        if (record.getAppealStatus() == dev.eministar.starbans.model.AppealStatus.ACCEPTED) {
            return discordColor("success", Color.GREEN);
        }
        if (record.getAppealStatus() == dev.eministar.starbans.model.AppealStatus.DENIED) {
            return discordColor("danger", Color.RED);
        }
        if (record.getStatus() == CaseStatus.EXPIRED) {
            return discordColor("neutral", Color.GRAY);
        }
        return switch (record.getPriority()) {
            case CRITICAL -> discordColor("critical", new Color(196, 30, 58));
            case HIGH -> discordColor("warning", new Color(255, 140, 0));
            case LOW -> discordColor("low", new Color(72, 201, 176));
            default -> discordColor("info", new Color(88, 166, 255));
        };
    }

    private String latestAppealNote(CaseRecord record) {
        if (record.getAppealNotes().isEmpty()) {
            return discordText("common.none", "none");
        }
        return defaultValue(record.getAppealNotes().getLast().getMessage());
    }

    private String defaultRequester(DiscordWorkflowRequest request) {
        return request.requesterDisplayName() == null || request.requesterDisplayName().isBlank()
                ? workflowOriginLabel(request.origin())
                : request.requesterDisplayName();
    }

    private TextChannel configuredPanelChannel() {
        String channelId = plugin.getConfig().getString("discord-bot.appeals.panel-channel-id", "");
        if (channelId == null || channelId.isBlank()) {
            return null;
        }
        return jda.getTextChannelById(channelId.trim());
    }

    private TextChannel resolveStaffChannel(DiscordWorkflowRequest request) {
        String configuredChannelId = configuredStaffChannelId();
        if (!configuredChannelId.isBlank()) {
            TextChannel configuredChannel = jda.getTextChannelById(configuredChannelId);
            if (configuredChannel != null) {
                return configuredChannel;
            }
        }
        if (request.staffChannelId() != null && !request.staffChannelId().isBlank()) {
            return jda.getTextChannelById(request.staffChannelId());
        }
        return null;
    }

    private String configuredStaffChannelId() {
        String configuredChannelId = plugin.getConfig().getString("discord-bot.appeals.staff-channel-id", "");
        return configuredChannelId == null ? "" : configuredChannelId.trim();
    }

    private String normalizeChannelId(String channelId) {
        return channelId == null ? "" : channelId.trim();
    }

    private Guild resolveConfiguredGuild(Guild fallbackGuild) {
        String configuredGuildId = plugin.getConfig().getString("discord-bot.guild-id", "");
        if (configuredGuildId != null && !configuredGuildId.isBlank()) {
            Guild configuredGuild = jda.getGuildById(configuredGuildId.trim());
            if (configuredGuild == null) {
                LoggerUtil.warn("Configured Discord guild " + configuredGuildId + " was not found.");
            }
            return configuredGuild;
        }
        if (fallbackGuild != null) {
            return fallbackGuild;
        }
        return jda.getGuilds().size() == 1 ? jda.getGuilds().getFirst() : null;
    }

    private boolean hasStaffPermission(Member member) {
        if (member == null) {
            return false;
        }

        List<String> configuredRoles = plugin.getConfig().getStringList("discord-bot.appeals.staff-role-ids");
        if (!configuredRoles.isEmpty()) {
            for (String roleId : configuredRoles) {
                if (roleId == null || roleId.isBlank()) {
                    continue;
                }
                if (member.getRoles().stream().anyMatch(role -> role.getId().equals(roleId.trim()))) {
                    return true;
                }
            }
        }
        return member.hasPermission(Permission.ADMINISTRATOR) || member.hasPermission(Permission.MANAGE_SERVER);
    }

    private void applyConfiguredNickname() {
        String nickname = plugin.getConfig().getString("discord-bot.appeals.bot-display-name", "");
        if (nickname == null || nickname.isBlank()) {
            return;
        }

        Guild guild = resolveConfiguredGuild(null);
        if (guild == null) {
            return;
        }

        try {
            guild.getSelfMember().modifyNickname(nickname.trim()).complete();
        } catch (Exception exception) {
            LoggerUtil.warn("The Discord bot nickname could not be updated: " + exception.getMessage());
        }
    }

    private OptionMapping requiredOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping mapping = event.getOption(name);
        if (mapping == null) {
            throw new IllegalStateException("Required option '" + name + "' was missing.");
        }
        return mapping;
    }

    private String optionalOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping mapping = event.getOption(name);
        return mapping == null ? null : mapping.getAsString();
    }

    private String modalValue(ModalInteractionEvent event, String fieldId) {
        if (event.getValue(fieldId) == null) {
            return "";
        }
        return event.getValue(fieldId).getAsString();
    }

    private void reply(InteractionHook hook, MessageEmbed embed) {
        hook.editOriginalEmbeds(embed).setContent("").queue();
    }

    private void replyTemplate(InteractionHook hook, String path, String colorKey, Object... replacements) {
        reply(
                hook,
                buildConfiguredEmbed(
                        path,
                        discordColor(colorKey, new Color(88, 166, 255)),
                        replacements
                )
        );
    }

    private CommandActor discordActor(User user, Member member) {
        return new CommandActor(null, "DISCORD:" + discordDisplayName(user, member), false);
    }

    private String discordDisplayName(User user, Member member) {
        if (member != null && member.getEffectiveName() != null && !member.getEffectiveName().isBlank()) {
            return member.getEffectiveName();
        }
        String globalName = user.getGlobalName();
        return globalName == null || globalName.isBlank() ? user.getName() : globalName;
    }

    private String defaultValue(String input) {
        return input == null || input.isBlank() ? discordText("common.none", "none") : input;
    }

    private String staffButtonId(String action, long caseId) {
        return STAFF_BUTTON_PREFIX + action + ':' + caseId;
    }

    private StaffButtonAction parseStaffAction(String componentId) {
        String[] parts = componentId.split(":");
        if (parts.length != 5) {
            return null;
        }
        try {
            return new StaffButtonAction(parts[3], Long.parseLong(parts[4]));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private MessageEmbed buildConfiguredEmbed(String path, Color color, Object... replacements) {
        EmbedBuilder builder = new EmbedBuilder().setColor(color);
        String title = discordText(path + ".title", "", replacements);
        if (!title.isBlank()) {
            builder.setTitle(title);
        }

        String description = joinLines(discordLines(path + ".description", replacements));
        if (!description.isBlank()) {
            builder.setDescription(description);
        }

        String footer = discordText(path + ".footer", discordText("common.footer", "StarBans"), replacements);
        if (!footer.isBlank()) {
            builder.setFooter(footer);
        }

        if (plugin.getConfig().getBoolean("discord-bot.style.embeds.show-timestamp", true)) {
            builder.setTimestamp(Instant.now());
        }
        return builder.build();
    }

    private Object[] discordReplacements(CaseRecord record, DiscordWorkflowRequest request, Object... extras) {
        List<Object> replacements = new ArrayList<>();
        addReplacement(replacements, "id", record.getId());
        addReplacement(replacements, "case_id", record.getId());
        addReplacement(replacements, "type", plain(plugin.getModerationService().formatCaseType(record.getType())));
        addReplacement(replacements, "type_key", record.getType().name());
        addReplacement(replacements, "player", plain(record.getTargetPlayerName()));
        addReplacement(replacements, "target_player", plain(record.getTargetPlayerName()));
        addReplacement(replacements, "related_player", plain(record.getRelatedPlayerName()));
        addReplacement(replacements, "ip", plain(record.getTargetIp()));
        addReplacement(replacements, "label", plain(record.getLabel()));
        addReplacement(replacements, "incident_id", plain(record.getIncidentId()));
        addReplacement(replacements, "priority", record.getPriority().name());
        addReplacement(replacements, "reason", plain(record.getReason()));
        addReplacement(replacements, "actor", plain(record.getActorName()));
        addReplacement(replacements, "source", plain(record.getSource()));
        addReplacement(replacements, "status", record.getStatus().name());
        addReplacement(replacements, "status_key", record.getStatus().name());
        addReplacement(replacements, "status_note", plain(record.getStatusNote()));
        addReplacement(replacements, "appeal_status", record.getAppealStatus().name());
        addReplacement(replacements, "appeal_actor", plain(record.getAppealActorName()));
        addReplacement(replacements, "appeal_deadline", record.getAppealDeadlineAt() == null ? discordText("common.none", "none") : plugin.getModerationService().formatDate(record.getAppealDeadlineAt()));
        addReplacement(replacements, "appeal_note_count", record.getAppealNotes().size());
        addReplacement(replacements, "appeal_latest_note", latestAppealNote(record));
        addReplacement(replacements, "claim_actor", plain(record.getClaimActorName()));
        addReplacement(replacements, "created_at", plugin.getModerationService().formatDate(record.getCreatedAt()));
        addReplacement(replacements, "expires_at", plain(plugin.getModerationService().formatExpiry(record)));
        addReplacement(replacements, "review_at", record.getNextReviewAt() == null ? discordText("common.none", "none") : plugin.getModerationService().formatDate(record.getNextReviewAt()));
        addReplacement(replacements, "review_reason", plain(record.getReviewReason()));
        addReplacement(replacements, "evidence_count", record.getEvidence().size());
        addReplacement(replacements, "workflow_kind", workflowKindLabel(request == null ? DiscordWorkflowRequestKind.APPEAL : request.kind()));
        addReplacement(replacements, "workflow_origin", workflowOriginLabel(request == null ? DiscordWorkflowOrigin.INGAME : request.origin()));
        addReplacement(replacements, "workflow_requester", request == null ? defaultValue(record.getAppealActorName()) : defaultRequester(request));
        addReplacement(replacements, "workflow_created_at", request == null ? plugin.getModerationService().formatDate(record.getCreatedAt()) : plugin.getModerationService().formatDate(request.createdAt()));
        addReplacement(replacements, "workflow_updated_at", request == null
                ? (record.getAppealChangedAt() == null ? plugin.getModerationService().formatDate(record.getCreatedAt()) : plugin.getModerationService().formatDate(record.getAppealChangedAt()))
                : plugin.getModerationService().formatDate(request.updatedAt()));

        for (int index = 0; index + 1 < extras.length; index += 2) {
            addReplacement(replacements, String.valueOf(extras[index]), extras[index + 1]);
        }
        return replacements.toArray();
    }

    private void addReplacement(List<Object> output, String key, Object value) {
        output.add(key);
        output.add(value == null ? discordText("common.none", "none") : value);
    }

    private String discordText(String path, String fallback, Object... replacements) {
        String raw = plugin.getLang().getRaw("discord." + path, fallback);
        return applyPlaceholders(raw == null ? fallback : raw, replacements);
    }

    private List<String> discordLines(String path, Object... replacements) {
        List<String> raw = plugin.getLang().getRawList("discord." + path);
        if (raw.isEmpty()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>(raw.size());
        for (String line : raw) {
            lines.add(applyPlaceholders(line, replacements));
        }
        return lines;
    }

    private String applyPlaceholders(String input, Object... replacements) {
        String output = input == null ? "" : input;
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            output = output.replace("{" + replacements[index] + "}", String.valueOf(replacements[index + 1]));
        }
        return output;
    }

    private String joinLines(List<String> lines) {
        if (lines.isEmpty()) {
            return "";
        }

        List<String> formatted = new ArrayList<>(lines.size());
        for (String line : lines) {
            formatted.add(line == null ? "" : line);
        }
        return String.join("\n", formatted);
    }

    private Color discordColor(String key, Color fallback) {
        String configured = plugin.getConfig().getString("discord-bot.style.colors." + key, "");
        if (configured == null || configured.isBlank()) {
            return fallback;
        }

        try {
            String normalized = configured.trim();
            if (!normalized.startsWith("#")) {
                normalized = "#" + normalized;
            }
            return Color.decode(normalized);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String plain(String input) {
        String stripped = input == null ? "" : ChatColor.stripColor(input);
        return stripped == null || stripped.isBlank() ? discordText("common.none", "none") : stripped;
    }

    private String workflowKindLabel(DiscordWorkflowRequestKind kind) {
        return switch (kind == null ? DiscordWorkflowRequestKind.APPEAL : kind) {
            case APPEAL -> discordText("labels.kind.appeal", "Appeal");
            case UNBAN_REQUEST -> discordText("labels.kind.unban-request", "Unban Request");
        };
    }

    private String workflowOriginLabel(DiscordWorkflowOrigin origin) {
        return switch (origin == null ? DiscordWorkflowOrigin.INGAME : origin) {
            case DISCORD -> discordText("labels.origin.discord", "Discord");
            case INGAME -> discordText("labels.origin.ingame", "In-game");
        };
    }

    private Activity resolveActivity() {
        String type = plugin.getConfig().getString("discord-bot.activity-type", "WATCHING");
        String text = plugin.getConfig().getString("discord-bot.activity-text", "the StarBans queue");
        String value = text == null || text.isBlank() ? "the StarBans queue" : text;
        return switch ((type == null ? "WATCHING" : type).toUpperCase(Locale.ROOT)) {
            case "PLAYING" -> Activity.playing(value);
            case "LISTENING" -> Activity.listening(value);
            case "COMPETING" -> Activity.competing(value);
            default -> Activity.watching(value);
        };
    }

    @Override
    public void close() throws Exception {
        if (jda == null) {
            return;
        }

        jda.shutdown();
        if (!jda.awaitShutdown(10, TimeUnit.SECONDS)) {
            jda.shutdownNow();
        }
        jda = null;
        LoggerUtil.info("Discord bot integration disabled.");
    }

    private record StaffButtonAction(String action, long caseId) {
    }
}
