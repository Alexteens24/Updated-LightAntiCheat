package me.vekster.lightanticheat.check.checks.movement.fastclimb;

import me.vekster.lightanticheat.check.CheckName;
import me.vekster.lightanticheat.check.buffer.Buffer;
import me.vekster.lightanticheat.check.checks.movement.MovementCheck;
import me.vekster.lightanticheat.event.playermove.LACAsyncPlayerMoveEvent;
import me.vekster.lightanticheat.player.LACPlayer;
import me.vekster.lightanticheat.player.cache.PlayerCache;
import me.vekster.lightanticheat.util.scheduler.Scheduler;
import me.vekster.lightanticheat.version.VerUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffectType;

public class FastClimbA extends MovementCheck implements Listener {
    private static final double MAX_CLIMB_DELTA = 0.118D;
    private static final double HARD_CLIMB_DELTA = 1.0D;
    private static final double BUFFER_THRESHOLD = 2.0D;

    public FastClimbA() {
        super(CheckName.FASTCLIMB_A);
    }

    @Override
    public boolean isConditionAllowed(Player player, LACPlayer lacPlayer, PlayerCache cache, boolean isClimbing, boolean isInWater,
                                      boolean isFlying, boolean isInsideVehicle, boolean isGliding, boolean isRiptiding) {
        if (isFlying || isInsideVehicle || !isClimbing || isGliding || isRiptiding || isInWater)
            return false;
        if (cache.flyingTicks >= -5 || cache.glidingTicks >= -3 || cache.riptidingTicks >= -3)
            return false;
        long time = System.currentTimeMillis();
        return time - cache.lastInsideVehicle > 150 && time - cache.lastInWater > 150 &&
                time - cache.lastKnockback > 500 && time - cache.lastKnockbackNotVanilla > 2000 &&
                time - cache.lastWasFished > 4000 && time - cache.lastTeleport > 500 &&
                time - cache.lastWorldChange > 500 && time - cache.lastRespawn > 500 &&
                time - cache.lastBlockExplosion > 6500 && time - cache.lastEntityExplosion > 2500 &&
                time - cache.lastSlimeBlockVertical > 1000 && time - cache.lastSlimeBlockHorizontal > 1000 &&
                time - cache.lastHoneyBlockVertical > 700 && time - cache.lastHoneyBlockHorizontal > 700 &&
                time - cache.lastWasHit > 250 && time - cache.lastWasDamaged > 100 &&
                time - cache.lastFlight > 750;
    }

    @EventHandler
    public void onAsyncMovement(LACAsyncPlayerMoveEvent event) {
        LACPlayer lacPlayer = event.getLacPlayer();
        PlayerCache cache = lacPlayer.cache;
        Player player = event.getPlayer();
        Buffer buffer = getBuffer(player, true);

        if (!isCheckAllowed(player, lacPlayer, true) || !isConditionAllowed(player, lacPlayer, event)) {
            reset(buffer);
            return;
        }

        if (getEffectAmplifier(cache, PotionEffectType.JUMP) > 0 ||
                getEffectAmplifier(cache, VerUtil.potions.get("LEVITATION")) > 0 ||
                isScaffolding(event)) {
            reset(buffer);
            return;
        }

        double deltaY = distanceVertical(event.getFrom(), event.getTo());
        boolean invalid = deltaY > MAX_CLIMB_DELTA;
        if (invalid) {
            buffer.put("climbTicks", buffer.getInt("climbTicks") + 1);
        } else {
            buffer.put("climbTicks", 0);
            decay(buffer);
            return;
        }

        if (buffer.getInt("climbTicks") <= 3 && deltaY <= HARD_CLIMB_DELTA) {
            decay(buffer);
            return;
        }

        double violations = buffer.getDouble("violations") + 1.0D;
        buffer.put("violations", violations);
        if (violations <= BUFFER_THRESHOLD && deltaY <= HARD_CLIMB_DELTA)
            return;

        Scheduler.runTask(true, () -> callViolationEvent(player, lacPlayer, event));
        reset(buffer);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        reset(getBuffer(event.getPlayer(), true));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        reset(getBuffer(event.getPlayer(), true));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        reset(getBuffer(event.getPlayer(), true));
    }

    private boolean isScaffolding(LACAsyncPlayerMoveEvent event) {
        Material scaffolding = VerUtil.material.get("SCAFFOLDING");
        return scaffolding != null &&
                (event.getFromWithinMaterials().contains(scaffolding) || event.getToWithinMaterials().contains(scaffolding));
    }

    private void decay(Buffer buffer) {
        buffer.put("violations", Math.max(buffer.getDouble("violations") - 0.1D, 0.0D));
    }

    private void reset(Buffer buffer) {
        buffer.put("climbTicks", 0);
        buffer.put("violations", 0.0D);
    }
}
