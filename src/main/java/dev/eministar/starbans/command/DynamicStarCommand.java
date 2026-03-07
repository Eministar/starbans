package dev.eministar.starbans.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.List;

public final class DynamicStarCommand extends Command implements PluginIdentifiableCommand {

    private final Plugin plugin;
    private final StarBansCommand executor;
    private final TabCompleter completer;

    public DynamicStarCommand(Plugin plugin,
                              String name,
                              String description,
                              String usage,
                              List<String> aliases,
                              String permission,
                              StarBansCommand executor,
                              TabCompleter completer) {
        super(name, description, usage, aliases);
        this.plugin = plugin;
        this.executor = executor;
        this.completer = completer;
        setPermission(permission);
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!testPermission(sender)) {
            return true;
        }
        return executor.onCommand(sender, this, commandLabel, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        if (completer == null) {
            return Collections.emptyList();
        }
        List<String> result = completer.onTabComplete(sender, this, alias, args);
        return result == null ? Collections.emptyList() : result;
    }

    @Override
    public Plugin getPlugin() {
        return plugin;
    }
}
