package me.vekster.lightanticheat.event.packetrecive;

import me.vekster.lightanticheat.event.packetrecive.packettype.PacketType;
import me.vekster.lightanticheat.event.packetrecive.packettype.PacketTypeRecognizer;
import me.vekster.lightanticheat.version.identifier.LACVersion;
import me.vekster.lightanticheat.version.identifier.VerIdentifier;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class FlyingPacketReader {

    private static boolean loggedParseFailure;

    public static void load() {
        loggedParseFailure = false;
    }

    @Nullable
    public static FlyingPacketData read(Object nmsPacket) {
        if (nmsPacket == null || VerIdentifier.getVersion().isOlderThan(LACVersion.V1_17))
            return null;
        if (PacketTypeRecognizer.getPacketType(nmsPacket) != PacketType.FLYING)
            return null;

        String simpleName = nmsPacket.getClass().getSimpleName();
        if ("PacketPlayInFlying".equals(simpleName))
            return readLegacyPacketPlayInFlying(nmsPacket);

        boolean hasPosition = simpleName.contains("Pos");
        boolean hasRotation = simpleName.contains("Rot") || simpleName.contains("Look");

        Double x = null;
        Double y = null;
        Double z = null;
        if (hasPosition) {
            x = invokeDouble(nmsPacket, "getX", "x");
            y = invokeDouble(nmsPacket, "getY", "y");
            z = invokeDouble(nmsPacket, "getZ", "z");
            if (x == null || y == null || z == null) {
                logParseFailureOnce();
                return null;
            }
        }

        float yaw = 0.0F;
        float pitch = 0.0F;
        if (hasRotation) {
            Float yawValue = invokeFloat(nmsPacket, "getYRot", "yRot", "getYaw", "yaw");
            Float pitchValue = invokeFloat(nmsPacket, "getXRot", "xRot", "getPitch", "pitch");
            if (yawValue == null || pitchValue == null) {
                logParseFailureOnce();
                return null;
            }
            yaw = yawValue;
            pitch = pitchValue;
        }

        Boolean onGround = invokeBoolean(nmsPacket, "isOnGround", "onGround");
        if (onGround == null)
            onGround = false;

        return new FlyingPacketData(hasPosition, hasRotation,
                x != null ? x : 0.0D,
                y != null ? y : 0.0D,
                z != null ? z : 0.0D,
                yaw, pitch, onGround);
    }

    @Nullable
    private static FlyingPacketData readLegacyPacketPlayInFlying(Object nmsPacket) {
        try {
            boolean hasPosition = false;
            boolean hasRotation = false;
            double x = 0.0D;
            double y = 0.0D;
            double z = 0.0D;
            float yaw = 0.0F;
            float pitch = 0.0F;
            boolean onGround = false;

            for (Field field : nmsPacket.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                String name = field.getName().toLowerCase();
                Object value = field.get(nmsPacket);
                if (value instanceof Double || value instanceof Float) {
                    double number = value instanceof Double ? (Double) value : ((Float) value).doubleValue();
                    if (name.equals("x") || name.endsWith("x")) {
                        x = number;
                        hasPosition = true;
                    } else if (name.equals("y") || name.endsWith("y")) {
                        y = number;
                        hasPosition = true;
                    } else if (name.equals("z") || name.endsWith("z")) {
                        z = number;
                        hasPosition = true;
                    } else if (name.contains("yaw")) {
                        yaw = (float) number;
                        hasRotation = true;
                    } else if (name.contains("pitch")) {
                        pitch = (float) number;
                        hasRotation = true;
                    }
                } else if (value instanceof Boolean && (name.contains("ground") || name.equals("f")))
                    onGround = (Boolean) value;
            }

            if (!hasPosition && !hasRotation) {
                Boolean ground = invokeBoolean(nmsPacket, "isOnGround", "onGround");
                if (ground != null)
                    onGround = ground;
            }

            return new FlyingPacketData(hasPosition, hasRotation, x, y, z, yaw, pitch, onGround);
        } catch (ReflectiveOperationException e) {
            logParseFailureOnce();
            return null;
        }
    }

    @Nullable
    private static Double invokeDouble(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                Object value = method.invoke(target);
                if (value instanceof Double)
                    return (Double) value;
                if (value instanceof Float)
                    return ((Float) value).doubleValue();
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    @Nullable
    private static Float invokeFloat(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                Object value = method.invoke(target);
                if (value instanceof Float)
                    return (Float) value;
                if (value instanceof Double)
                    return ((Double) value).floatValue();
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    @Nullable
    private static Boolean invokeBoolean(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                Object value = method.invoke(target);
                if (value instanceof Boolean)
                    return (Boolean) value;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static void logParseFailureOnce() {
        if (loggedParseFailure)
            return;
        loggedParseFailure = true;
    }

}
