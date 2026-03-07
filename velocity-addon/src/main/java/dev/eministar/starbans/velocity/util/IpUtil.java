package dev.eministar.starbans.velocity.util;

public final class IpUtil {

    private IpUtil() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().toLowerCase();
    }
}
