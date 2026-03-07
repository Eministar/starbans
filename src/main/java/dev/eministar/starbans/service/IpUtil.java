package dev.eministar.starbans.service;

import java.net.InetAddress;
import java.util.Locale;

public final class IpUtil {

    private IpUtil() {
    }

    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String value = input.trim();
        if (value.startsWith("/")) {
            value = value.substring(1);
        }

        if (value.contains(":")) {
            int separator = value.lastIndexOf(':');
            if (separator > 0 && value.indexOf(':') == separator) {
                value = value.substring(0, separator);
            }
        }

        try {
            return InetAddress.getByName(value).getHostAddress().toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return value.toLowerCase(Locale.ROOT);
        }
    }
}
