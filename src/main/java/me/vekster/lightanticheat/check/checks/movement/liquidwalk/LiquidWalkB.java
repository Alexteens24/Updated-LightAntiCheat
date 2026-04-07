package me.vekster.lightanticheat.check.checks.movement.liquidwalk;

import me.vekster.lightanticheat.check.CheckName;
import me.vekster.lightanticheat.check.buffer.Buffer;
import me.vekster.lightanticheat.check.checks.movement.MovementCheck;
import me.vekster.lightanticheat.event.playermove.LACAsyncPlayerMoveEvent;
import me.vekster.lightanticheat.player.LACPlayer;
import me.vekster.lightanticheat.player.cache.PlayerCache;
import me.vekster.lightanticheat.util.detection.LeanTowards;
import me.vekster.lightanticheat.util.scheduler.Scheduler;
import me.vekster.lightanticheat.version.VerUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class LiquidWalkB extends MovementCheck implements Listener {
    private static final double HOVER_EPSILON = 0.001;
    private static final double SURFACE_HORIZONTAL_SPEED = 0.04;

    public LiquidWalkB() {
        super(CheckName.LIQUIDWALK_B);
    }

    @Override
    public boolean isConditionAllowed(Player player, LACPlayer lacPlayer, PlayerCache cache, boolean isClimbing, boolean isInWater,
                                      boolean isFlying, boolean isInsideVehicle, boolean isGliding, boolean isRiptiding) {
        if (isFlying || isInsideVehicle || isClimbing || isGliding || isRiptiding || isInWater)
            return false;
        if (cache.flyingTicks >= -5 || cache.climbingTicks >= -2 ||
                cache.glidingTicks >= -3 || cache.riptidingTicks >= -4)
            return false;
        long time = System.currentTimeMillis();
        return time - cache.lastInsideVehicle > 150 &&
                time - cache.lastKnockback > 250 && time - cache.lastKnockbackNotVanilla > 1000 &&
                time - cache.lastWasFished > 1000 && time - cache.lastTeleport > 500 &&
                time - cache.lastRespawn > 500 && time - cache.lastEntityVeryNearby > 200 &&
                time - cache.lastBlockExplosion > 2000 && time - cache.lastEntityExplosion > 1000 &&
                time - cache.lastSlimeBlockVertical > 3000 && time - cache.lastSlimeBlockHorizontal > 2500 &&
                time - cache.lastHoneyBlockVertical > 3000 && time - cache.lastHoneyBlockHorizontal > 3000 &&
                time - cache.lastWasHit > 150 && time - cache.lastWasDamaged > 50 &&
                time - cache.lastFlight > 750;
    }

    @EventHandler
    public void onAsyncMovement(LACAsyncPlayerMoveEvent event) {
        LACPlayer lacPlayer = event.getLacPlayer();
        PlayerCache cache = lacPlayer.cache;
        Player player = event.getPlayer();
        Buffer buffer = getBuffer(player, true);

        if (!isCheckAllowed(player, lacPlayer, true) ||
                !isConditionAllowed(player, lacPlayer, event) ||
                !event.isToWithinBlocksPassable() || !event.isFromWithinBlocksPassable() ||
                getEffectAmplifier(cache, VerUtil.potions.get("LEVITATION")) != 0 ||
                player.isSneaking() || event.isPlayerInWater() ||
                isNearLilyPad(player, event.getTo())) {
            resetBuffer(buffer);
            return;
        }

        boolean actualGround = isOnGround(player, 0.2, cache, LeanTowards.TRUE);
        if (actualGround || !isSupportedLiquidSurface(player, event.getTo())) {
            resetBuffer(buffer);
            return;
        }

        double verticalSpeed = distanceVertical(event.getFrom(), event.getTo());
        double horizontalSpeed = distanceHorizontal(event.getFrom(), event.getTo());
        double lastDeltaY = buffer.getDouble("lastDeltaY");
        buffer.put("lastDeltaY", verticalSpeed);

        if (System.currentTimeMillis() - buffer.getLong("lastUpdate") > 1500L) {
            buffer.put("up", false);
            buffer.put("down", false);
            buffer.put("hoverTicks", 0);
            buffer.put("flags", 0);
        }
        buffer.put("lastUpdate", System.currentTimeMillis());

        int hoverTicks = Math.abs(verticalSpeed) < HOVER_EPSILON ? buffer.getInt("hoverTicks") + 1 : 0;
        buffer.put("hoverTicks", hoverTicks);

        if (!buffer.getBoolean("up")) {
            buffer.put("up", verticalSpeed > 0.03);
        } else if (!buffer.getBoolean("down")) {
            buffer.put("down", verticalSpeed < -0.03);
        }

        double yModulo = event.getTo().getY() % 0.015625;
        if (yModulo < 0)
            yModulo += 0.015625;

        boolean clientGroundSpoof = player.isOnGround() && !actualGround;
        boolean bobbing = buffer.getBoolean("up") && buffer.getBoolean("down");
        boolean jitter = Math.abs(verticalSpeed) >= 0.01 && Math.abs(verticalSpeed) <= 0.08 &&
                Math.abs(lastDeltaY) >= 0.01 && Math.abs(lastDeltaY) <= 0.08 &&
                Math.signum(verticalSpeed) != Math.signum(lastDeltaY);
        boolean invalidConstant = Math.abs(verticalSpeed - 0.11) < 0.01 ||
                Math.abs(verticalSpeed - 0.30) < 0.02 ||
                Math.abs(Math.abs(verticalSpeed) - 0.05) < 0.01;
        boolean invalidYOffset = yModulo < 0.0001 || yModulo > 0.0155;

        if (horizontalSpeed <= SURFACE_HORIZONTAL_SPEED) {
            buffer.put("flags", Math.max(buffer.getInt("flags") - 1, 0));
            return;
        }

        if (clientGroundSpoof || hoverTicks > 3 || bobbing || jitter || invalidConstant || invalidYOffset)
            buffer.put("flags", Math.min(buffer.getInt("flags") + 1, 6));
        else
            buffer.put("flags", Math.max(buffer.getInt("flags") - 1, 0));

        if (buffer.getInt("flags") <= 3)
            return;

        updateDownBlocks(player, lacPlayer, event.getToDownBlocks());
        Scheduler.runTask(true, () -> callViolationEventIfRepeat(player, lacPlayer, event, buffer, 5000L));
    }

    private boolean isSupportedLiquidSurface(Player player, Location location) {
        boolean downLiquid = true;
        for (Block block : getDownBlocks(player, location, 0.22)) {
            if (downLiquid && !block.isLiquid())
                downLiquid = false;
            if (!block.isLiquid() && !block.getRelative(BlockFace.DOWN).isLiquid() &&
                    !block.getRelative(0, -2, 0).isLiquid())
                return false;
        }
        if (downLiquid)
            return true;

        double subtract = location.getY() % 1;
        if (!(subtract < -0.3 || subtract > 0 && subtract < 0.7))
            return false;
        if (location.getY() < 0)
            subtract = 1 + subtract;
        for (Block block : getDownBlocks(player, location.clone().subtract(0, subtract, 0), 0.25)) {
            if (!block.isLiquid())
                return false;
        }
        return true;
    }

    private boolean isNearLilyPad(Player player, Location location) {
        Material lilyPad = VerUtil.material.get("LILY_PAD");
        if (lilyPad == null)
            return false;
        for (Block block : getInteractiveBlocks(player, location)) {
            if (block.getType() == lilyPad ||
                    block.getRelative(BlockFace.UP).getType() == lilyPad ||
                    block.getRelative(BlockFace.DOWN).getType() == lilyPad)
                return true;
            if (!isActuallyPassable(block))
                return true;
        }
        return false;
    }

    private void resetBuffer(Buffer buffer) {
        buffer.put("flags", 0);
        buffer.put("up", false);
        buffer.put("down", false);
        buffer.put("hoverTicks", 0);
        buffer.put("lastDeltaY", 0.0);
        buffer.put("lastUpdate", 0L);
    }
}
