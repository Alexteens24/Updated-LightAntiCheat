package me.vekster.lightanticheat.player.cache;

import me.vekster.lightanticheat.event.packetrecive.FlyingPacketData;
import me.vekster.lightanticheat.event.playermove.LACAsyncPlayerMoveEvent;
import me.vekster.lightanticheat.player.LACPlayer;
import me.vekster.lightanticheat.player.cache.history.HistoryElement;
import me.vekster.lightanticheat.util.detection.CheckUtil;
import me.vekster.lightanticheat.util.detection.LeanTowards;
import me.vekster.lightanticheat.util.detection.specific.GroundUtil;
import me.vekster.lightanticheat.util.hook.server.folia.FoliaUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Set;

public final class PlayerHistoryRecorder {

    private static final double PACKET_GROUND_PADDING = 0.15D;

    private PlayerHistoryRecorder() {
    }

    public static void recordEventHistory(Player player, LACPlayer lacPlayer, LACAsyncPlayerMoveEvent event) {
        PlayerCache cache = lacPlayer.cache;
        Location from = event.getFrom();
        PlayerCache.OnGround onGround;
        if (!FoliaUtil.isFolia() || FoliaUtil.canAccessLocation(from)) {
            Set<Block> downBlocks = event.getFromDownBlocks();
            double fromY = from.getY();
            boolean towardsFalse = GroundUtil.isOnGroundAt(fromY, downBlocks, cache, LeanTowards.FALSE);
            boolean towardsTrue = GroundUtil.isOnGroundAt(fromY, downBlocks, cache, LeanTowards.TRUE);
            onGround = new PlayerCache.OnGround(towardsFalse, towardsTrue);
        } else {
            onGround = carryForwardEventOnGround(cache);
        }
        pushEvent(cache, from, onGround);
    }

    public static void recordPacketHistory(Player player, LACPlayer lacPlayer, FlyingPacketData flying) {
        PlayerCache cache = lacPlayer.cache;
        boolean towardsTrue = flying.onGround;

        if (flying.hasPosition) {
            Location location = new Location(player.getWorld(), flying.x, flying.y, flying.z);
            if (flying.hasRotation) {
                location.setYaw(flying.yaw);
                location.setPitch(flying.pitch);
            }
            if (!FoliaUtil.isFolia() || FoliaUtil.canAccessLocation(location)) {
                Set<Block> downBlocks = CheckUtil.getDownBlocks(player, location, PACKET_GROUND_PADDING);
                boolean towardsFalse = GroundUtil.isOnGroundAt(flying.y, downBlocks, cache, LeanTowards.FALSE);
                cache.lastClaimedPacketLocation = location.clone();
                pushPacketLocationAndOnGround(cache, location.clone(), towardsFalse, towardsTrue);
                return;
            }
            boolean towardsFalse = carryForwardPacketTowardsFalse(cache);
            pushPacketOnGround(cache, towardsFalse, towardsTrue);
            return;
        }

        boolean towardsFalse;
        if (cache.lastClaimedPacketLocation != null) {
            Location lastClaimed = cache.lastClaimedPacketLocation;
            if (!FoliaUtil.isFolia() || FoliaUtil.canAccessLocation(lastClaimed)) {
                Set<Block> downBlocks = CheckUtil.getDownBlocks(player, lastClaimed, PACKET_GROUND_PADDING);
                towardsFalse = GroundUtil.isOnGroundAt(lastClaimed.getY(), downBlocks, cache, LeanTowards.FALSE);
            } else {
                towardsFalse = carryForwardPacketTowardsFalse(cache);
            }
        } else {
            towardsFalse = carryForwardPacketTowardsFalse(cache);
        }
        pushPacketOnGround(cache, towardsFalse, towardsTrue);
    }

    public static void recordPacketHistoryLegacy(Player player, LACPlayer lacPlayer) {
        PlayerCache cache = lacPlayer.cache;
        Location location = cache.lastClaimedPacketLocation != null
                ? cache.lastClaimedPacketLocation.clone()
                : player.getLocation().clone();
        if (!FoliaUtil.isFolia() || FoliaUtil.canAccessLocation(location)) {
            Set<Block> downBlocks = CheckUtil.getDownBlocks(player, location, PACKET_GROUND_PADDING);
            boolean towardsFalse = GroundUtil.isOnGroundAt(location.getY(), downBlocks, cache, LeanTowards.FALSE);
            boolean towardsTrue = GroundUtil.isOnGroundAt(location.getY(), downBlocks, cache, LeanTowards.TRUE);
            if (cache.lastClaimedPacketLocation == null)
                cache.lastClaimedPacketLocation = location.clone();
            pushPacketLocationAndOnGround(cache, location, towardsFalse, towardsTrue);
            return;
        }
        boolean towardsFalse = carryForwardPacketTowardsFalse(cache);
        boolean towardsTrue = carryForwardPacketTowardsTrue(cache);
        pushPacketOnGround(cache, towardsFalse, towardsTrue);
    }

    private static void pushEvent(PlayerCache cache, Location from, PlayerCache.OnGround onGround) {
        cache.history.onEvent.location.add(from.clone());
        cache.history.onEvent.onGround.add(onGround);
    }

    private static void pushPacketLocationAndOnGround(PlayerCache cache, Location location,
                                                      boolean towardsFalse, boolean towardsTrue) {
        cache.history.onPacket.location.add(location);
        cache.history.onPacket.onGround.add(new PlayerCache.OnGround(towardsFalse, towardsTrue));
    }

    private static void pushPacketOnGround(PlayerCache cache, boolean towardsFalse, boolean towardsTrue) {
        cache.history.onPacket.onGround.add(new PlayerCache.OnGround(towardsFalse, towardsTrue));
    }

    private static PlayerCache.OnGround carryForwardEventOnGround(PlayerCache cache) {
        return cache.history.onEvent.onGround.get(HistoryElement.FROM);
    }

    private static boolean carryForwardPacketTowardsFalse(PlayerCache cache) {
        return cache.history.onPacket.onGround.get(HistoryElement.FROM).towardsFalse;
    }

    private static boolean carryForwardPacketTowardsTrue(PlayerCache cache) {
        return cache.history.onPacket.onGround.get(HistoryElement.FROM).towardsTrue;
    }

}
