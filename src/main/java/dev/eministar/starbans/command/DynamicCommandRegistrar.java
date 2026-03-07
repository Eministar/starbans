package dev.eministar.starbans.command;

import dev.eministar.starbans.StarBans;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class DynamicCommandRegistrar {

    private final StarBans plugin;
    private final List<DynamicStarCommand> registered = new ArrayList<>();
    private final Map<String, Command> replacedCommands = new HashMap<>();

    public DynamicCommandRegistrar(StarBans plugin) {
        this.plugin = plugin;
    }

    public void reload(StarBansCommand commandHandler) {
        unregisterAll();
        if (!plugin.getConfig().getBoolean("command-overrides.enabled", false)) {
            return;
        }

        registerConfigured(commandHandler, "command-overrides.commands.ban", "ban", List.of(), "starbans.command.ban", "Override the vanilla /ban command.");
        registerConfigured(commandHandler, "command-overrides.commands.tempban", "tempban", List.of(), "starbans.command.tempban", "Override the vanilla /tempban command.");
        registerConfigured(commandHandler, "command-overrides.commands.ipban", "ipban", List.of("banip", "ip-ban", "ban-ip"), "starbans.command.ipban", "Override IP-ban command variants.");
        registerConfigured(commandHandler, "command-overrides.commands.unban", "unban", List.of("pardon"), "starbans.command.unban", "Override the vanilla /unban command.");
        registerConfigured(commandHandler, "command-overrides.commands.unipban", "unipban", List.of("pardonip", "ipunban", "unbanip"), "starbans.command.unipban", "Override IP-unban command variants.");
        registerConfigured(commandHandler, "command-overrides.commands.mute", "mute", List.of(), "starbans.command.mute", "Provide /mute directly.");
        registerConfigured(commandHandler, "command-overrides.commands.tempmute", "tempmute", List.of(), "starbans.command.tempmute", "Provide /tempmute directly.");
        registerConfigured(commandHandler, "command-overrides.commands.unmute", "unmute", List.of(), "starbans.command.unmute", "Provide /unmute directly.");
        registerConfigured(commandHandler, "command-overrides.commands.kick", "kick", List.of(), "starbans.command.kick", "Override /kick.");
        registerConfigured(commandHandler, "command-overrides.commands.notes", "notes", List.of("notehistory"), "starbans.command.notes", "Provide note listing commands.");
        registerConfigured(commandHandler, "command-overrides.commands.cases", "cases", List.of("casehistory", "history"), "starbans.command.cases", "Provide case listing commands.");
        syncCommands();
    }

    public void unregisterAll() {
        try {
            CommandMap commandMap = commandMap();
            Map<String, Command> knownCommands = knownCommands(commandMap);
            for (DynamicStarCommand command : registered) {
                removeCommandEntries(knownCommands, command.getName(), command);
                for (String alias : command.getAliases()) {
                    removeCommandEntries(knownCommands, alias, command);
                }
                command.unregister(commandMap);
            }

            for (Map.Entry<String, Command> entry : replacedCommands.entrySet()) {
                knownCommands.putIfAbsent(entry.getKey(), entry.getValue());
            }
            syncCommands();
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "StarBans command overrides could not be unregistered cleanly.", exception);
        } finally {
            registered.clear();
            replacedCommands.clear();
        }
    }

    private void registerConfigured(StarBansCommand commandHandler,
                                    String path,
                                    String defaultName,
                                    List<String> defaultAliases,
                                    String permission,
                                    String description) {
        if (!plugin.getConfig().getBoolean(path + ".enabled", false)) {
            return;
        }

        String name = plugin.getConfig().getString(path + ".name", defaultName);
        List<String> aliases = plugin.getConfig().getStringList(path + ".aliases");
        if (aliases.isEmpty()) {
            aliases = defaultAliases;
        }

        DynamicStarCommand command = new DynamicStarCommand(
                plugin,
                name,
                description,
                "/" + name,
                aliases,
                permission,
                commandHandler,
                commandHandler
        );

        try {
            CommandMap commandMap = commandMap();
            Map<String, Command> knownCommands = knownCommands(commandMap);
            replaceKnownCommand(knownCommands, name);
            for (String alias : aliases) {
                replaceKnownCommand(knownCommands, alias);
            }
            commandMap.register(plugin.getName().toLowerCase(Locale.ROOT), command);
            registered.add(command);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "StarBans could not register command override '/" + name + "'.", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> knownCommands(CommandMap commandMap) throws Exception {
        Field field = findField(commandMap.getClass(), "knownCommands");
        field.setAccessible(true);
        Map<String, Command> current = (Map<String, Command>) field.get(commandMap);
        if (current == null) {
            Map<String, Command> created = new LinkedHashMap<>();
            field.set(commandMap, created);
            return created;
        }

        Map<String, Command> mutableCopy = new LinkedHashMap<>(current);
        field.set(commandMap, mutableCopy);
        return mutableCopy;
    }

    private CommandMap commandMap() throws Exception {
        Method method = plugin.getServer().getClass().getMethod("getCommandMap");
        method.setAccessible(true);
        return (CommandMap) method.invoke(plugin.getServer());
    }

    private void replaceKnownCommand(Map<String, Command> knownCommands, String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        List<String> removals = new ArrayList<>();
        for (Map.Entry<String, Command> entry : knownCommands.entrySet()) {
            String currentKey = entry.getKey().toLowerCase(Locale.ROOT);
            if (!matchesCommandKey(currentKey, normalized)) {
                continue;
            }

            replacedCommands.putIfAbsent(entry.getKey(), entry.getValue());
            removals.add(entry.getKey());
        }

        for (String removal : removals) {
            knownCommands.remove(removal);
        }
    }

    private void removeCommandEntries(Map<String, Command> knownCommands, String key, Command command) {
        String normalized = key.toLowerCase(Locale.ROOT);
        List<String> removals = new ArrayList<>();
        for (Map.Entry<String, Command> entry : knownCommands.entrySet()) {
            if (entry.getValue() != command) {
                continue;
            }
            if (matchesCommandKey(entry.getKey().toLowerCase(Locale.ROOT), normalized)) {
                removals.add(entry.getKey());
            }
        }

        for (String removal : removals) {
            knownCommands.remove(removal);
        }
    }

    private boolean matchesCommandKey(String currentKey, String expectedKey) {
        return currentKey.equals(expectedKey) || currentKey.endsWith(':' + expectedKey);
    }

    private void syncCommands() {
        try {
            Method method = findMethod(plugin.getServer().getClass(), "syncCommands");
            if (method == null) {
                return;
            }
            method.setAccessible(true);
            method.invoke(plugin.getServer());
        } catch (Exception exception) {
            plugin.getLogger().warning("StarBans could not sync command overrides with the server command dispatcher.");
        }
    }

    private Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
