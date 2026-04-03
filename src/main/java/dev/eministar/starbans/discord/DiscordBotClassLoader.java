package dev.eministar.starbans.discord;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

final class DiscordBotClassLoader extends URLClassLoader {

    private static final List<String> PARENT_FIRST_PREFIXES = List.of(
            "java.",
            "javax.",
            "jdk.",
            "sun.",
            "com.sun.",
            "org.bukkit.",
            "org.spigotmc.",
            "io.papermc.",
            "net.minecraft.",
            "org.slf4j.",
            "dev.eministar.starbans."
    );

    DiscordBotClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (shouldLoadParentFirst(name)) {
            return super.loadClass(name, resolve);
        }

        Class<?> loaded = findLoadedClass(name);
        if (loaded == null) {
            try {
                loaded = findClass(name);
            } catch (ClassNotFoundException ignored) {
                loaded = super.loadClass(name, false);
            }
        }
        if (resolve) {
            resolveClass(loaded);
        }
        return loaded;
    }

    private boolean shouldLoadParentFirst(String className) {
        if (className.startsWith("dev.eministar.starbans.discord.bot.")) {
            return false;
        }

        for (String prefix : PARENT_FIRST_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
