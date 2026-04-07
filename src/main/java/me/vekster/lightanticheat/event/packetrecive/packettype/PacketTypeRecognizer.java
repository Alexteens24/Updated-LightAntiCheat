package me.vekster.lightanticheat.event.packetrecive.packettype;

import java.lang.reflect.Field;

public class PacketTypeRecognizer {

    public static PacketType getPacketType(Object nmsPacket) {
        String className = nmsPacket.getClass().getName();
        className = className.split("\\.")[className.split("\\.").length - 1].split("\\$")[0];
        switch (className) {
            case "PacketPlayInFlying":
            case "ServerboundMovePlayerPacket":
                return PacketType.FLYING;
            case "PacketPlayInArmAnimation":
            case "ServerboundSwingPacket":
                return PacketType.ARM_ANIMATION;
            case "PacketPlayInBlockDig":
            case "ServerboundPlayerActionPacket":
                return PacketType.BLOCK_DIG;
            case "PacketPlayInSteerVehicle":
            case "ServerboundPlayerInputPacket":
                return PacketType.STEER_VEHICLE;
            case "PacketPlayInSetCreativeSlot":
            case "ServerboundSetCreativeModeSlotPacket":
                return PacketType.SET_CREATIVE_SLOT;
            case "ServerboundClientInformationPacket":
                return PacketType.CLIENT_INFORMATION;
            case "ServerboundKeepAlivePacket":
                return PacketType.ALIVE;
            case "PacketPlayInUseEntity":
            case "ServerboundInteractPacket":
                return PacketType.USE_ENTITY;
            default:
                return PacketType.OTHER;
        }
    }

    public static int getEntityId(Object nmsPacket) {
        if (getPacketType(nmsPacket) != PacketType.USE_ENTITY)
            return 0;
        try {
            Object value = nmsPacket.getClass().getMethod("getEntityId").invoke(nmsPacket);
            if (value instanceof Integer && (int) value != 0)
                return (int) value;
        } catch (ReflectiveOperationException ignored) {
        }

        Field[] fields = nmsPacket.getClass().getDeclaredFields();
        for (Field field : fields) {
            boolean accessible = field.isAccessible();
            if (!accessible) {
                field.setAccessible(true);
            }
            try {
                Object object = field.get(nmsPacket);
                if (object instanceof Integer) {
                    int value = (int) object;
                    if (value != 0) {
                        if (!accessible) {
                            field.setAccessible(false);
                        }
                        return value;
                    }
                }
            } catch (IllegalAccessException ignored) {
            }
            if (!accessible) {
                field.setAccessible(false);
            }
        }
        return 0;
    }

}
