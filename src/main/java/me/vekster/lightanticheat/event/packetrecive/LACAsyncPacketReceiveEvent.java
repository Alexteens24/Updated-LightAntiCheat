package me.vekster.lightanticheat.event.packetrecive;

import me.vekster.lightanticheat.event.packetrecive.packettype.PacketType;
import me.vekster.lightanticheat.event.packetrecive.packettype.PacketTypeRecognizer;
import me.vekster.lightanticheat.player.LACPlayer;
import me.vekster.lightanticheat.util.hook.server.folia.FoliaUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.Nullable;

public class LACAsyncPacketReceiveEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final LACPlayer lacPlayer;
    private final String packetName;
    private final PacketType packetType;
    private final int entityId;
    private final Object nmsPacket;
    private final FlyingPacketData flyingData;

    public LACAsyncPacketReceiveEvent(Player player, LACPlayer lacPlayer, Object nmsPacket,
                                      @Nullable FlyingPacketData flyingData) {
        super(!FoliaUtil.isFolia());

        this.player = player;
        this.lacPlayer = lacPlayer;
        this.nmsPacket = nmsPacket;
        this.packetName = nmsPacket.getClass().getSimpleName().split("\\$")[0];
        this.packetType = PacketTypeRecognizer.getPacketType(nmsPacket);
        this.entityId = PacketTypeRecognizer.getEntityId(nmsPacket);
        this.flyingData = flyingData;
    }

    public Player getPlayer() {
        return player;
    }

    public LACPlayer getLacPlayer() {
        return lacPlayer;
    }

    public String getPacketName() {
        return packetName;
    }

    public PacketType getPacketType() {
        return packetType;
    }

    public int getEntityId() {
        return entityId;
    }

    @Nullable
    public FlyingPacketData getFlyingData() {
        return flyingData;
    }

    public Object getNmsPacket() {
        return nmsPacket;
    }

    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
