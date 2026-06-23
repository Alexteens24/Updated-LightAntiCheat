package me.vekster.lightanticheat.event.packetrecive;

public final class FlyingPacketData {

    public final boolean hasPosition;
    public final boolean hasRotation;
    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    public final float pitch;
    public final boolean onGround;

    public FlyingPacketData(boolean hasPosition, boolean hasRotation,
                            double x, double y, double z,
                            float yaw, float pitch, boolean onGround) {
        this.hasPosition = hasPosition;
        this.hasRotation = hasRotation;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
    }

}
