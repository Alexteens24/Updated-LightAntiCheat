package me.vekster.lightanticheat.event.packetrecive;

import me.vekster.lightanticheat.event.packetrecive.packettype.PacketType;
import me.vekster.lightanticheat.event.packetrecive.packettype.PacketTypeRecognizer;
import me.vekster.lightanticheat.version.identifier.LACVersion;
import me.vekster.lightanticheat.version.identifier.VerIdentifier;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class FlyingPacketReader {

    private static final double POSITION_DEFAULT = Double.MAX_VALUE;
    private static final float ROTATION_DEFAULT = Float.MAX_VALUE;

    private static boolean loggedParseFailure;
    private static String loggedParseFailureClass;

    public static void load() {
        loggedParseFailure = false;
        loggedParseFailureClass = null;
    }

    @Nullable
    public static Boolean readOnGround(Object nmsPacket) {
        if (nmsPacket == null || VerIdentifier.getVersion().isOlderThan(LACVersion.V1_17))
            return null;
        if (PacketTypeRecognizer.getPacketType(nmsPacket) != PacketType.FLYING)
            return null;
        Boolean onGround = invokeBoolean(nmsPacket, "isOnGround", "onGround");
        if (onGround != null)
            return onGround;
        return readBooleanFieldNullable(nmsPacket, "onGround");
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

        Boolean modernHasPosition = invokeBoolean(nmsPacket, "hasPosition", "changesPosition");
        Boolean modernHasRotation = invokeBoolean(nmsPacket, "hasRotation", "changesLook");
        if (modernHasPosition != null || modernHasRotation != null)
            return readModernMovePlayerPacket(nmsPacket, modernHasPosition, modernHasRotation);

        return readInnerClassMovePlayerPacket(nmsPacket, simpleName);
    }

    @Nullable
    private static FlyingPacketData readModernMovePlayerPacket(Object nmsPacket,
                                                               @Nullable Boolean modernHasPosition,
                                                               @Nullable Boolean modernHasRotation) {
        boolean hasPosition = Boolean.TRUE.equals(modernHasPosition);
        boolean hasRotation = Boolean.TRUE.equals(modernHasRotation);
        if (!hasPosition && !hasRotation) {
            if (modernHasPosition == null)
                hasPosition = readBooleanField(nmsPacket, "hasPos", "changePosition");
            if (modernHasRotation == null)
                hasRotation = readBooleanField(nmsPacket, "hasRot", "changeLook");
        }

        Double x = null;
        Double y = null;
        Double z = null;
        if (hasPosition) {
            x = readPositionComponent(nmsPacket, true);
            y = x != null ? readCoordinate(nmsPacket, "getY", "y", true) : null;
            z = x != null ? readCoordinate(nmsPacket, "getZ", "z", true) : null;
            if (x == null || y == null || z == null) {
                Double[] position = readPositionVector(nmsPacket);
                if (position != null) {
                    x = position[0];
                    y = position[1];
                    z = position[2];
                }
            }
            if (x == null || y == null || z == null) {
                logParseFailureOnce(nmsPacket.getClass().getName());
                return null;
            }
        }

        float yaw = 0.0F;
        float pitch = 0.0F;
        if (hasRotation) {
            Float yawValue = readRotationComponent(nmsPacket, true);
            Float pitchValue = yawValue != null ? readRotationComponent(nmsPacket, false) : null;
            if (yawValue == null || pitchValue == null) {
                logParseFailureOnce(nmsPacket.getClass().getName());
                return null;
            }
            yaw = yawValue;
            pitch = pitchValue;
        }

        Boolean onGround = invokeBoolean(nmsPacket, "isOnGround", "onGround");
        if (onGround == null)
            onGround = readBooleanField(nmsPacket, "onGround");

        return new FlyingPacketData(hasPosition, hasRotation,
                x != null ? x : 0.0D,
                y != null ? y : 0.0D,
                z != null ? z : 0.0D,
                yaw, pitch, onGround != null && onGround);
    }

    @Nullable
    private static FlyingPacketData readInnerClassMovePlayerPacket(Object nmsPacket, String simpleName) {
        boolean hasPosition = simpleName.contains("Pos");
        boolean hasRotation = simpleName.contains("Rot") || simpleName.contains("Look");

        Double x = null;
        Double y = null;
        Double z = null;
        if (hasPosition) {
            x = readCoordinate(nmsPacket, "getX", "x", false);
            y = readCoordinate(nmsPacket, "getY", "y", false);
            z = readCoordinate(nmsPacket, "getZ", "z", false);
            if (x == null || y == null || z == null) {
                Double[] position = readPositionVector(nmsPacket);
                if (position != null) {
                    x = position[0];
                    y = position[1];
                    z = position[2];
                }
            }
            if (x == null || y == null || z == null) {
                logParseFailureOnce(nmsPacket.getClass().getName());
                return null;
            }
        }

        float yaw = 0.0F;
        float pitch = 0.0F;
        if (hasRotation) {
            Float yawValue = readRotationComponent(nmsPacket, true);
            Float pitchValue = yawValue != null ? readRotationComponent(nmsPacket, false) : null;
            if (yawValue == null || pitchValue == null) {
                logParseFailureOnce(nmsPacket.getClass().getName());
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
    private static Double readCoordinate(Object target, String getterName, String fieldName, boolean withDefaultArg) {
        Double value = withDefaultArg
                ? invokeDoubleWithDefault(target, getterName, POSITION_DEFAULT)
                : invokeDouble(target, getterName, fieldName);
        if (value != null)
            return value;
        return readDoubleField(target, fieldName);
    }

    @Nullable
    private static Double readPositionComponent(Object target, boolean withDefaultArg) {
        Double value = withDefaultArg
                ? invokeDoubleWithDefault(target, "getX", POSITION_DEFAULT)
                : invokeDouble(target, "getX", "x");
        if (value != null)
            return value;
        return readDoubleField(target, "x");
    }

    @Nullable
    private static Float readRotationComponent(Object target, boolean yaw) {
        if (yaw) {
            Float value = invokeFloatWithDefault(target, "getYRot", ROTATION_DEFAULT);
            if (value != null)
                return value;
            value = invokeFloat(target, "getYRot", "yRot", "getYaw", "yaw");
            if (value != null)
                return value;
            return readFloatField(target, "yRot", "yaw");
        }
        Float value = invokeFloatWithDefault(target, "getXRot", ROTATION_DEFAULT);
        if (value != null)
            return value;
        value = invokeFloat(target, "getXRot", "xRot", "getPitch", "pitch");
        if (value != null)
            return value;
        return readFloatField(target, "xRot", "pitch");
    }

    @Nullable
    private static Double[] readPositionVector(Object target) {
        Double[] fromComponent = readPositionComponent(target, "position", "pos");
        if (fromComponent != null)
            return fromComponent;
        Double x = readDoubleField(target, "x");
        Double y = readDoubleField(target, "y");
        Double z = readDoubleField(target, "z");
        if (x != null && y != null && z != null)
            return new Double[]{x, y, z};
        return null;
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
                    } else if (name.contains("yaw") || name.contains("yrot")) {
                        yaw = (float) number;
                        hasRotation = true;
                    } else if (name.contains("pitch") || name.contains("xrot")) {
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
            logParseFailureOnce(nmsPacket.getClass().getName());
            return null;
        }
    }

    @Nullable
    private static Double[] readPositionComponent(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                Object value = method.invoke(target);
                if (value == null)
                    continue;
                Double x = invokeDouble(value, "getX", "x");
                Double y = invokeDouble(value, "getY", "y");
                Double z = invokeDouble(value, "getZ", "z");
                if (x != null && y != null && z != null)
                    return new Double[]{x, y, z};
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    @Nullable
    private static Double invokeDoubleWithDefault(Object target, String methodName, double defaultValue) {
        try {
            Method method = target.getClass().getMethod(methodName, double.class);
            Object value = method.invoke(target, defaultValue);
            if (value instanceof Double)
                return (Double) value;
            if (value instanceof Float)
                return ((Float) value).doubleValue();
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    @Nullable
    private static Float invokeFloatWithDefault(Object target, String methodName, float defaultValue) {
        try {
            Method method = target.getClass().getMethod(methodName, float.class);
            Object value = method.invoke(target, defaultValue);
            if (value instanceof Float)
                return (Float) value;
            if (value instanceof Double)
                return ((Double) value).floatValue();
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
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

    private static boolean readBooleanField(Object target, String... fieldNames) {
        Boolean value = readBooleanFieldNullable(target, fieldNames);
        return value != null && value;
    }

    @Nullable
    private static Boolean readBooleanFieldNullable(Object target, String... fieldNames) {
        for (String fieldName : fieldNames) {
            try {
                Field field = target.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(target);
                if (value instanceof Boolean)
                    return (Boolean) value;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    @Nullable
    private static Double readDoubleField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(target);
            if (value instanceof Double)
                return (Double) value;
            if (value instanceof Float)
                return ((Float) value).doubleValue();
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    @Nullable
    private static Float readFloatField(Object target, String... fieldNames) {
        for (String fieldName : fieldNames) {
            try {
                Field field = target.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(target);
                if (value instanceof Float)
                    return (Float) value;
                if (value instanceof Double)
                    return ((Double) value).floatValue();
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static void logParseFailureOnce(String className) {
        if (loggedParseFailure && className.equals(loggedParseFailureClass))
            return;
        loggedParseFailure = true;
        loggedParseFailureClass = className;
        Bukkit.getLogger().warning("[FlyingPacketReader] Failed to parse flying packet fields for " + className);
    }

}
