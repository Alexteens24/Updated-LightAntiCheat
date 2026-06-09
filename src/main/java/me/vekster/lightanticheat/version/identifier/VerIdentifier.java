package me.vekster.lightanticheat.version.identifier;

import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VerIdentifier {

    private static final Pattern LEGACY_VERSION_PATTERN = Pattern.compile("v1_(\\d+)");
    private static final Pattern MODERN_VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)");
    private static LACVersion serverVersion = null;

    public static LACVersion getVersion() {
        if (serverVersion != null)
            return serverVersion;

        serverVersion = parseVersion(resolveServerVersion());
        return serverVersion;
    }

    private static String resolveServerVersion() {
        try {
            Method getMinecraftVersion = Bukkit.getServer().getClass().getMethod("getMinecraftVersion");
            Object version = getMinecraftVersion.invoke(Bukkit.getServer());
            if (version instanceof String && !((String) version).isEmpty())
                return (String) version;
        } catch (ReflectiveOperationException ignored) {
        }

        String name = Bukkit.getServer().getClass().getPackage().getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private static LACVersion parseVersion(String version) {
        Matcher legacyMatcher = LEGACY_VERSION_PATTERN.matcher(version);
        if (legacyMatcher.find())
            return fromMinorVersion(Integer.parseInt(legacyMatcher.group(1)));

        Matcher modernMatcher = MODERN_VERSION_PATTERN.matcher(version);
        if (modernMatcher.find()) {
            int majorVersion = Integer.parseInt(modernMatcher.group(1));
            int minorVersion = Integer.parseInt(modernMatcher.group(2));
            return fromMinorVersion(majorVersion == 1 ? minorVersion : majorVersion);
        }

        return LACVersion.V1_20;
    }

    private static LACVersion fromMinorVersion(int minorVersion) {
        switch (minorVersion) {
            case 8:
                return LACVersion.V1_8;
            case 9:
                return LACVersion.V1_9;
            case 10:
                return LACVersion.V1_10;
            case 11:
                return LACVersion.V1_11;
            case 12:
                return LACVersion.V1_12;
            case 13:
                return LACVersion.V1_13;
            case 14:
                return LACVersion.V1_14;
            case 15:
                return LACVersion.V1_15;
            case 16:
                return LACVersion.V1_16;
            case 17:
                return LACVersion.V1_17;
            case 18:
                return LACVersion.V1_18;
            case 19:
                return LACVersion.V1_19;
            case 20:
                return LACVersion.V1_20;
            default:
                return minorVersion >= 21 ? LACVersion.V1_21 : LACVersion.V1_20;
        }
    }

}
