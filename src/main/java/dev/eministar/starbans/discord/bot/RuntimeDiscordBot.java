package dev.eministar.starbans.discord.bot;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.discord.DiscordBotBridge;
import dev.eministar.starbans.model.CasePriority;
import dev.eministar.starbans.model.CaseRecord;
import dev.eministar.starbans.model.CommandActor;
import dev.eministar.starbans.model.ModerationActionResult;
import dev.eministar.starbans.model.ModerationActionType;
import dev.eministar.starbans.model.PlayerIdentity;
import dev.eministar.starbans.utils.LoggerUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class RuntimeDiscordBot extends ListenerAdapter implements DiscordBotBridge {

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
        builder.setMemberCachePolicy(MemberCachePolicy.NONE);
        builder.setChunkingFilter(ChunkingFilter.NONE);
        builder.setActivity(resolveActivity());
        builder.addEventListeners(this);

        jda = builder.build();
        jda.awaitReady();
        registerSlashCommands();
        LoggerUtil.success("Discord bot integration enabled.");
    }

    private void registerSlashCommands() {
        List<CommandData> commands = new ArrayList<>();
        if (plugin.getConfig().getBoolean("discord-bot.commands.case-lookup", true)) {
            commands.add(
                    Commands.slash("case", "Look up a moderation case")
                            .addOption(OptionType.INTEGER, "case_id", "The StarBans case id", true)
            );
        }
        if (plugin.getConfig().getBoolean("discord-bot.commands.report", true)) {
            commands.add(
                    Commands.slash("report", "Submit a player report")
                            .addOption(OptionType.STRING, "player", "Minecraft player name", true)
                            .addOption(OptionType.STRING, "reason", "Reason for the report", true)
                            .addOption(OptionType.STRING, "priority", "Queue priority", false)
            );
        }
        if (plugin.getConfig().getBoolean("discord-bot.commands.appeal", true)) {
            commands.add(
                    Commands.slash("appeal", "Open or update an appeal")
                            .addOption(OptionType.INTEGER, "case_id", "The StarBans case id", true)
                            .addOption(OptionType.STRING, "message", "Appeal note or explanation", true)
                            .addOption(OptionType.STRING, "deadline", "Optional duration like 7d or 12h", false)
            );
        }
        if (plugin.getConfig().getBoolean("discord-bot.commands.unban-request", true)) {
            commands.add(
                    Commands.slash("unban-request", "Create an unban request for an active ban")
                            .addOption(OptionType.STRING, "player", "Minecraft player name", true)
                            .addOption(OptionType.STRING, "reason", "Why the ban should be reviewed", true)
            );
        }

        String guildId = plugin.getConfig().getString("discord-bot.guild-id", "");
        if (guildId != null && !guildId.isBlank()) {
            Guild guild = jda.getGuildById(guildId.trim());
            if (guild != null) {
                guild.updateCommands().addCommands(commands).complete();
                LoggerUtil.info("Discord bot slash commands registered for guild " + guild.getName() + '.');
                return;
            }
            LoggerUtil.warn("Configured Discord guild " + guildId + " was not found. Falling back to global slash commands.");
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

    private void handleInteraction(SlashCommandInteractionEvent event, InteractionHook hook) {
        try {
            switch (event.getName()) {
                case "case" -> handleCaseLookup(event, hook);
                case "report" -> handleReport(event, hook);
                case "appeal" -> handleAppeal(event, hook);
                case "unban-request" -> handleUnbanRequest(event, hook);
                default -> reply(hook, "Unknown command.");
            }
        } catch (Exception exception) {
            LoggerUtil.error("A Discord bot command failed.", exception);
            reply(hook, "The request failed. Check the server console.");
        }
    }

    private void handleCaseLookup(SlashCommandInteractionEvent event, InteractionHook hook) throws Exception {
        long caseId = requiredOption(event, "case_id").getAsLong();
        Optional<CaseRecord> record = plugin.getModerationService().getCase(caseId);
        if (record.isEmpty()) {
            reply(hook, "Case #" + caseId + " was not found.");
            return;
        }
        reply(hook, buildCaseResponse(record.get()));
    }

    private void handleReport(SlashCommandInteractionEvent event, InteractionHook hook) throws Exception {
        String playerName = requiredOption(event, "player").getAsString();
        Optional<PlayerIdentity> target = plugin.getPlayerLookupService().resolve(playerName);
        if (target.isEmpty()) {
            reply(hook, "Player '" + playerName + "' is unknown to StarBans.");
            return;
        }

        CasePriority priority = CasePriority.fromConfig(optionalOption(event, "priority"));
        ModerationActionResult result = plugin.getModerationService().reportPlayer(
                new PlayerIdentity(null, discordDisplayName(event)),
                target.get(),
                requiredOption(event, "reason").getAsString(),
                priority,
                "DISCORD:REPORT"
        );
        reply(hook, "Report stored as case #" + result.caseRecord().getId() + " for " + target.get().name() + '.');
    }

    private void handleAppeal(SlashCommandInteractionEvent event, InteractionHook hook) throws Exception {
        long caseId = requiredOption(event, "case_id").getAsLong();
        String deadlineInput = optionalOption(event, "deadline");
        Long deadlineAt = null;
        if (deadlineInput != null && !deadlineInput.isBlank()) {
            Long duration = dev.eministar.starbans.service.TimeUtil.parseDuration(deadlineInput);
            deadlineAt = duration == null ? null : System.currentTimeMillis() + duration;
        }

        ModerationActionResult result = plugin.getModerationService().openAppeal(
                caseId,
                discordActor(event),
                deadlineAt,
                requiredOption(event, "message").getAsString()
        );
        if (result.type() == ModerationActionType.NOT_FOUND || result.caseRecord() == null) {
            reply(hook, "Case #" + caseId + " was not found.");
            return;
        }
        reply(hook, "Appeal opened for case #" + caseId + '.');
    }

    private void handleUnbanRequest(SlashCommandInteractionEvent event, InteractionHook hook) throws Exception {
        String playerName = requiredOption(event, "player").getAsString();
        Optional<PlayerIdentity> target = plugin.getPlayerLookupService().resolve(playerName);
        if (target.isEmpty()) {
            reply(hook, "Player '" + playerName + "' is unknown to StarBans.");
            return;
        }

        Optional<CaseRecord> activeBan = plugin.getModerationService().getActivePlayerBan(target.get().uniqueId());
        if (activeBan.isEmpty()) {
            reply(hook, target.get().name() + " does not have an active ban.");
            return;
        }

        String reason = requiredOption(event, "reason").getAsString();
        ModerationActionResult result = plugin.getModerationService().openAppeal(
                activeBan.get().getId(),
                discordActor(event),
                null,
                "Discord unban request: " + reason
        );
        if (result.type() == ModerationActionType.NOT_FOUND || result.caseRecord() == null) {
            reply(hook, "The active ban case could not be updated.");
            return;
        }
        reply(hook, "Unban request attached to case #" + activeBan.get().getId() + '.');
    }

    private String buildCaseResponse(CaseRecord record) {
        List<String> lines = new ArrayList<>();
        lines.add("Case #" + record.getId());
        lines.add("Type: " + plugin.getModerationService().formatCaseType(record.getType()));
        lines.add("Player: " + defaultValue(record.getTargetPlayerName()));
        lines.add("Actor: " + defaultValue(record.getActorName()));
        lines.add("Reason: " + defaultValue(record.getReason()));
        lines.add("Status: " + record.getStatus().name());
        lines.add("Priority: " + record.getPriority().name());
        lines.add("Appeal: " + record.getAppealStatus().name());
        lines.add("Created: " + plugin.getModerationService().formatDate(record.getCreatedAt()));
        lines.add("Expires: " + plugin.getModerationService().formatExpiry(record));
        if (record.getClaimActorName() != null && !record.getClaimActorName().isBlank()) {
            lines.add("Claimed by: " + record.getClaimActorName());
        }
        if (record.getIncidentId() != null && !record.getIncidentId().isBlank()) {
            lines.add("Incident: " + record.getIncidentId());
        }
        if (!record.getEvidence().isEmpty()) {
            lines.add("Evidence: " + record.getEvidence().size());
        }
        return String.join("\n", lines);
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

    private void reply(InteractionHook hook, String message) {
        hook.editOriginal(message).queue();
    }

    private CommandActor discordActor(SlashCommandInteractionEvent event) {
        return new CommandActor(null, discordDisplayName(event), false);
    }

    private String discordDisplayName(SlashCommandInteractionEvent event) {
        String globalName = event.getUser().getGlobalName();
        String displayName = globalName == null || globalName.isBlank() ? event.getUser().getName() : globalName;
        return "DISCORD:" + displayName;
    }

    private String defaultValue(String input) {
        return input == null || input.isBlank() ? "none" : input;
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
}
