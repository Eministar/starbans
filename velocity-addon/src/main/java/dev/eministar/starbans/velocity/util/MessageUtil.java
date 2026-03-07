package dev.eministar.starbans.velocity.util;

import dev.eministar.starbans.velocity.config.YamlConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)&?#([0-9A-F]{6})");
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private final YamlConfig lang;

    public MessageUtil(YamlConfig lang) {
        this.lang = lang;
    }

    public String get(String path, String fallback, Object... replacements) {
        return replace(lang.getString(path, fallback), replacements);
    }

    public List<String> getList(String path, Object... replacements) {
        return lang.getStringList(path).stream()
                .map(line -> replace(line, replacements))
                .toList();
    }

    public String prefixed(String path, Object... replacements) {
        return get("general.prefix", "") + get(path, "", replacements);
    }

    public Component component(String input) {
        return SERIALIZER.deserialize(applyHex(input == null ? "" : input));
    }

    public Component componentFromPath(String path, Object... replacements) {
        return component(get(path, "", replacements));
    }

    public String replace(String input, Object... replacements) {
        String output = input == null ? "" : input;
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            String key = String.valueOf(replacements[index]);
            String value = String.valueOf(replacements[index + 1]);
            output = output.replace("{" + key + "}", value);
        }
        return output;
    }

    private String applyHex(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1).toUpperCase(Locale.ROOT);
            StringBuilder replacement = new StringBuilder("&x");
            for (char character : hex.toCharArray()) {
                replacement.append('&').append(character);
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(output);
        return output.toString();
    }
}
