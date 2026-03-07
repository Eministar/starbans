package dev.eministar.starbans.utils;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)&?#([0-9A-F]{6})");

    private ColorUtil() {
    }

    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', applyHex(input));
    }

    public static String color(String input, Object... replacements) {
        return color(replace(input, replacements));
    }

    public static List<String> color(List<String> lines, Object... replacements) {
        List<String> output = new ArrayList<>(lines.size());
        for (String line : lines) {
            output.add(color(line, replacements));
        }
        return output;
    }

    public static String replace(String input, Object... replacements) {
        if (input == null || replacements == null || replacements.length == 0) {
            return input == null ? "" : input;
        }

        String output = input;
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            String key = String.valueOf(replacements[index]);
            String value = String.valueOf(replacements[index + 1]);
            output = output.replace("{" + key + "}", value);
        }
        return output;
    }

    private static String applyHex(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1).toUpperCase(Locale.ROOT);
            StringBuilder replacement = new StringBuilder("§x");
            for (char character : hex.toCharArray()) {
                replacement.append('§').append(character);
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(output);
        return output.toString();
    }
}
