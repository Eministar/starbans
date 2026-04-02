package dev.eministar.starbans.discord;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

final class DiscordBotClassLoader extends URLClassLoader {

    private static final List<String> CHILD_FIRST_PREFIXES = List.of(
            "dev.eministar.starbans.discord.bot.",
            "net.dv8tion.jda.",
            "com.neovisionaries.ws.client.",
            "okhttp3.",
            "okio.",
            "kotlin.",
            "com.fasterxml.jackson."
    );

    DiscordBotClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (shouldLoadChildFirst(name)) {
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
        return super.loadClass(name, resolve);
    }

    private boolean shouldLoadChildFirst(String className) {
        for (String prefix : CHILD_FIRST_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
