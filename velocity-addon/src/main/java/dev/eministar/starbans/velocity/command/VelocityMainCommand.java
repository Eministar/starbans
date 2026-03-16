package dev.eministar.starbans.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import dev.eministar.starbans.velocity.StarBansVelocityAddon;
import dev.eministar.starbans.velocity.model.CaseRecord;
import dev.eministar.starbans.velocity.model.PlayerProfile;
import dev.eministar.starbans.velocity.util.IpUtil;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class VelocityMainCommand implements SimpleCommand {

    private final StarBansVelocityAddon plugin;

    public VelocityMainCommand(StarBansVelocityAddon plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        try {
            if (args.length == 0) {
                sendHelp(source);
                return;
            }

            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "reload" -> executeReload(source);
                case "check" -> executeCheck(source, args);
                default -> sendHelp(source);
            }
        } catch (Exception exception) {
            plugin.getLogger().error("Velocity command execution failed.", exception);
            source.sendMessage(plugin.getLang().component(plugin.getNetworkPrefix() + plugin.getNetworkText("messages.internal-error", plugin.getLang().get("messages.internal-error", "&cAn internal proxy error occurred. Check the console."))));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 1) {
            return filter(List.of("reload", "check"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            try {
                List<String> suggestions = new ArrayList<>();
                for (PlayerProfile profile : plugin.getModerationService().resolveProfile(args[1]).stream().toList()) {
                    suggestions.add(profile.getLastName());
                }
                return filter(suggestions, args[1]);
            } catch (Exception ignored) {
            }
        }
        return List.of();
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(suggest(invocation));
    }

    private void executeReload(CommandSource source) {
        if (!source.hasPermission("starbansvelocity.command.reload")) {
            source.sendMessage(plugin.getLang().component(plugin.getNetworkPrefix() + plugin.getNetworkText("messages.no-permission", plugin.getLang().get("messages.no-permission", "&cYou do not have permission for this action."))));
            return;
        }

        if (plugin.reload()) {
            source.sendMessage(plugin.getLang().component(plugin.getLang().prefixed("messages.reload-success")));
        } else {
            source.sendMessage(plugin.getLang().component(plugin.getLang().prefixed("messages.reload-failed")));
        }
    }

    private void executeCheck(CommandSource source, String[] args) throws Exception {
        if (!source.hasPermission("starbansvelocity.command.check")) {
            source.sendMessage(plugin.getLang().component(plugin.getNetworkPrefix() + plugin.getNetworkText("messages.no-permission", plugin.getLang().get("messages.no-permission", "&cYou do not have permission for this action."))));
            return;
        }
        if (args.length < 2) {
            source.sendMessage(plugin.getLang().component(plugin.getLang().prefixed("messages.usage-check")));
            return;
        }

        String input = args[1];
        if (looksLikeIp(input)) {
            String normalizedIp = IpUtil.normalize(input);
            Optional<CaseRecord> ipBan = plugin.getModerationService().getActiveIpBan(normalizedIp);
            Optional<CaseRecord> ipBlacklist = plugin.getModerationService().getActiveIpBlacklist(normalizedIp);

            sendLine(source, plugin.getNetworkPrefix() + plugin.getNetworkText("messages.check-header", plugin.getLang().get("messages.check-header", "&8---------- &6Proxy Check: &f{target} &8----------"), "target", normalizedIp, "player", normalizedIp));
            for (String line : buildCheckLines(plugin.getNetworkText("labels.none", plugin.getLang().get("labels.none", "&7none")), normalizedIp, ipBan, ipBlacklist)) {
                sendLine(source, plugin.getNetworkPrefix() + line);
            }
            return;
        }

        Optional<PlayerProfile> profileOptional = plugin.getModerationService().resolveProfile(input);
        if (profileOptional.isEmpty()) {
            source.sendMessage(plugin.getLang().component(plugin.getNetworkPrefix() + plugin.getNetworkText("messages.player-not-found", plugin.getLang().get("messages.player-not-found", "&cNo known player found for &f{player}&c."), "player", input)));
            return;
        }

        PlayerProfile profile = profileOptional.get();
        Optional<CaseRecord> playerBan = plugin.getModerationService().getActivePlayerBan(profile.getUniqueId());
        Optional<CaseRecord> playerMute = plugin.getModerationService().getActivePlayerMute(profile.getUniqueId());
        Optional<CaseRecord> ipBan = profile.getLastIp() == null ? Optional.empty() : plugin.getModerationService().getActiveIpBan(profile.getLastIp());
        Optional<CaseRecord> ipBlacklist = profile.getLastIp() == null ? Optional.empty() : plugin.getModerationService().getActiveIpBlacklist(profile.getLastIp());
        String none = plugin.getNetworkText("labels.none", plugin.getLang().get("labels.none", "&7none"));

        sendLine(source, plugin.getNetworkPrefix() + plugin.getNetworkText("messages.check-header", plugin.getLang().get("messages.check-header", "&8---------- &6Proxy Check: &f{target} &8----------"), "target", profile.getLastName(), "player", profile.getLastName()));
        for (String line : buildCheckLines(none, profile.getLastIp() == null ? none : profile.getLastIp(), playerBan, playerMute, ipBan, ipBlacklist, profile.getLastName())) {
            sendLine(source, plugin.getNetworkPrefix() + line);
        }
    }

    private void sendHelp(CommandSource source) {
        for (String line : plugin.getLang().getList("messages.usage-main")) {
            sendLine(source, plugin.getLang().get("general.prefix", "") + line);
        }
    }

    private void sendLine(CommandSource source, String line) {
        source.sendMessage(plugin.getLang().component(line));
    }

    private List<String> buildCheckLines(String none, String lastIp, Optional<CaseRecord> ipBan, Optional<CaseRecord> ipBlacklist) {
        return buildCheckLines(none, lastIp, Optional.empty(), Optional.empty(), ipBan, ipBlacklist, none);
    }

    private List<String> buildCheckLines(String none,
                                         String lastIp,
                                         Optional<CaseRecord> playerBan,
                                         Optional<CaseRecord> playerMute,
                                         Optional<CaseRecord> ipBan,
                                         Optional<CaseRecord> ipBlacklist,
                                         String name) {
        return plugin.getNetworkTextList(
                "messages.check-lines",
                plugin.getLang().getList("messages.check-lines"),
                "name", name,
                "player", name,
                "last_ip", lastIp,
                "active_ban", playerBan.map(CaseRecord::getReason).orElse(none),
                "active_mute", playerMute.map(CaseRecord::getReason).orElse(none),
                "case_count", none,
                "note_count", none,
                "alt_count", none,
                "last_case_type", none,
                "last_case_reason", none,
                "player_ban", playerBan.map(CaseRecord::getReason).orElse(none),
                "ip_ban", ipBan.map(CaseRecord::getReason).orElse(none),
                "ip_blacklist", ipBlacklist.map(CaseRecord::getReason).orElse(none)
        );
    }

    private List<String> filter(List<String> input, String prefix) {
        String lowered = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> output = new ArrayList<>();
        for (String value : input) {
            if (value != null && value.toLowerCase(Locale.ROOT).startsWith(lowered)) {
                output.add(value);
            }
        }
        return output;
    }

    private boolean looksLikeIp(String input) {
        return input.matches("[0-9a-fA-F:.]+") && (input.contains(".") || input.contains(":"));
    }
}
