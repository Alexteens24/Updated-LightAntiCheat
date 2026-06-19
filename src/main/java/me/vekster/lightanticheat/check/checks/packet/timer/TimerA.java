package me.vekster.lightanticheat.check.checks.packet.timer;

import me.vekster.lightanticheat.check.CheckName;
import me.vekster.lightanticheat.check.buffer.Buffer;
import me.vekster.lightanticheat.check.checks.packet.PacketCheck;
import me.vekster.lightanticheat.event.playermove.LACAsyncPlayerMoveEvent;
import me.vekster.lightanticheat.player.LACPlayer;
import me.vekster.lightanticheat.player.cache.PlayerCache;
import me.vekster.lightanticheat.util.hook.plugin.FloodgateHook;
import me.vekster.lightanticheat.util.tps.TPSCalculator;
import me.vekster.lightanticheat.version.VerPlayer;
import me.vekster.lightanticheat.version.identifier.LACVersion;
import me.vekster.lightanticheat.version.identifier.VerIdentifier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Timer hack
 */
public class TimerA extends PacketCheck implements Listener {
    private static final long NEGATIVE_BALANCE_LIMIT = -800L;
    private static final long BASE_BALANCE_THRESHOLD = 125L;
    private static final long THRESHOLD_INCREMENT = 50L;
    private static final long PACKET_INCREMENT = 50L;

    public TimerA() {
        super(CheckName.TIMER_A);
    }

    @EventHandler
    public void onAsyncMovement(LACAsyncPlayerMoveEvent event) {
        Player player = event.getPlayer();
        LACPlayer lacPlayer = event.getLacPlayer();
        PlayerCache cache = lacPlayer.cache;

        if (!isCheckAllowed(player, lacPlayer, true))
            return;

        Buffer buffer = getBuffer(player, true);
        long currentTime = System.currentTimeMillis();

        boolean positionChanged = distance(event.getFrom(), event.getTo()) > 0.0D;
        boolean rotationChanged = event.getFrom().getYaw() != event.getTo().getYaw() ||
                event.getFrom().getPitch() != event.getTo().getPitch();
        if (positionChanged || rotationChanged)
            buffer.put("moved", true);

        if (!buffer.getBoolean("moved") || currentTime - lacPlayer.joinTime < 5000L) {
            resetTiming(buffer, currentTime, true);
            return;
        }

        if (VerIdentifier.getVersion().isOlderOrEqualsTo(LACVersion.V1_8) && currentTime - lacPlayer.joinTime < 12000L) {
            resetTiming(buffer, currentTime, true);
            return;
        }

        int ping = VerPlayer.getPing(player, true);
        double tps = TPSCalculator.getTPS();
        if (ping > 300 || tps < 18.5D || TPSCalculator.getTickDurationInMs() > 150L) {
            resetTiming(buffer, currentTime, false);
            buffer.put("largeDelayGrace", 5);
            decay(buffer, 0.5D);
            return;
        }

        if (isNearWeb(event)) {
            resetTiming(buffer, currentTime, true);
            buffer.put("largeDelayGrace", 5);
            decay(buffer, 0.05D);
            return;
        }

        if (currentTime - cache.lastTeleport < 1000L ||
                currentTime - cache.lastWorldChange < 1000L ||
                currentTime - cache.lastGamemodeChange < 2500L ||
                currentTime - cache.lastRespawn < 1500L) {
            resetTiming(buffer, currentTime, true);
            buffer.put("largeDelayGrace", 3);
            return;
        }

        if (player.isInsideVehicle()) {
            buffer.put("skipVehiclePacket", !buffer.getBoolean("skipVehiclePacket"));
            if (!buffer.getBoolean("skipVehiclePacket"))
                return;
        }

        long lastTime = buffer.getLong("lastTime");
        if (lastTime == 0L) {
            buffer.put("lastTime", currentTime);
            return;
        }

        long elapsed = currentTime - lastTime;
        buffer.put("lastTime", currentTime);
        if (elapsed < 1L)
            elapsed = 1L;

        boolean bedrockPlayer = FloodgateHook.isBedrockPlayer(player, true) ||
                FloodgateHook.isProbablyPocketEditionPlayer(player, true);
        long pingAllowance = Math.min(175L, Math.max(0L, ping - 50L) / 2L);
        long tpsAllowance = (long) Math.ceil(Math.max(0.0D, 20.0D - Math.min(20.0D, tps)) * 35.0D);
        long bedrockAllowance = bedrockPlayer ? 175L : 0L;
        long dynamicThreshold = BASE_BALANCE_THRESHOLD + pingAllowance + tpsAllowance + bedrockAllowance;

        long balanceThreshold = buffer.getLong("balanceThreshold");
        if (balanceThreshold < dynamicThreshold)
            balanceThreshold = dynamicThreshold;

        long newBalance = buffer.getLong("timerBalance") + PACKET_INCREMENT - elapsed;
        if (elapsed > 250L) {
            buffer.put("timerBalance", Math.max(NEGATIVE_BALANCE_LIMIT, newBalance));
            buffer.put("largeDelayGrace", 5);
            buffer.put("balanceThreshold", balanceThreshold);
            return;
        }
        if (elapsed > 120L) {
            buffer.put("timerBalance", Math.max(NEGATIVE_BALANCE_LIMIT, newBalance));
            buffer.put("largeDelayGrace", 3);
            buffer.put("balanceThreshold", balanceThreshold);
            return;
        }

        if (buffer.getInt("largeDelayGrace") > 0) {
            buffer.put("timerBalance", Math.max(NEGATIVE_BALANCE_LIMIT, newBalance));
            buffer.put("largeDelayGrace", buffer.getInt("largeDelayGrace") - 1);
            buffer.put("balanceThreshold", balanceThreshold);
            return;
        }

        if (newBalance > balanceThreshold) {
            if (currentTime - buffer.getLong("lastInvalidTime") > 750L) {
                buffer.put("lastInvalidTime", currentTime);
                flag(player, lacPlayer);
            }
            balanceThreshold += THRESHOLD_INCREMENT;
        } else {
            if (balanceThreshold > dynamicThreshold && newBalance < dynamicThreshold / 2L)
                balanceThreshold--;
            decay(buffer, 0.02D);
        }

        buffer.put("timerBalance", Math.max(NEGATIVE_BALANCE_LIMIT, newBalance));
        buffer.put("balanceThreshold", balanceThreshold);
    }

    private void resetTiming(Buffer buffer, long currentTime, boolean resetBalance) {
        buffer.put("lastTime", currentTime);
        if (!resetBalance)
            return;
        buffer.put("timerBalance", 0L);
        buffer.put("balanceThreshold", BASE_BALANCE_THRESHOLD);
    }

    private boolean isNearWeb(LACAsyncPlayerMoveEvent event) {
        return hasWeb(event.getFromWithinMaterials()) || hasWeb(event.getToWithinMaterials()) ||
                hasWeb(event.getFromDownMaterials()) || hasWeb(event.getToDownMaterials());
    }

    private boolean hasWeb(Iterable<Material> materials) {
        for (Material material : materials)
            if (material != null && material.name().contains("WEB"))
                return true;
        return false;
    }

    private void decay(Buffer buffer, double amount) {
        buffer.put("violations", Math.max(buffer.getDouble("violations") - amount, 0.0D));
    }
}
