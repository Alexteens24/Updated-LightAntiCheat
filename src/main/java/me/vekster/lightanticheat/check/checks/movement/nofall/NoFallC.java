package me.vekster.lightanticheat.check.checks.movement.nofall;

import me.vekster.lightanticheat.check.CheckName;
import me.vekster.lightanticheat.check.buffer.Buffer;
import me.vekster.lightanticheat.check.checks.movement.MovementCheck;
import me.vekster.lightanticheat.event.playermove.LACAsyncPlayerMoveEvent;
import me.vekster.lightanticheat.player.LACPlayer;
import me.vekster.lightanticheat.player.cache.PlayerCache;
import me.vekster.lightanticheat.util.detection.LeanTowards;
import me.vekster.lightanticheat.util.scheduler.Scheduler;
import me.vekster.lightanticheat.version.VerUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

/**
 * Waits for the fall damage after suspicious landings.
 */
public class NoFallC extends MovementCheck implements Listener {
    private static final double MINIMUM_FALL_DISTANCE = 4.0D;
    private static final long DAMAGE_WAIT_MS = 500L;

    public NoFallC() {
        super(CheckName.NOFALL_C);
    }

    @Override
    public boolean isConditionAllowed(Player player, LACPlayer lacPlayer, PlayerCache cache, boolean isClimbing, boolean isInWater,
                                      boolean isFlying, boolean isInsideVehicle, boolean isGliding, boolean isRiptiding) {
        if (isFlying || isInsideVehicle || isClimbing || isGliding || isRiptiding || isInWater)
            return false;
        if (cache.flyingTicks >= -5 || cache.climbingTicks >= -2 ||
                cache.glidingTicks >= -3 || cache.riptidingTicks >= -5)
            return false;
        long time = System.currentTimeMillis();
        return time - cache.lastInsideVehicle > 150 && time - cache.lastInWater > 150 &&
                time - cache.lastKnockback > 750 && time - cache.lastKnockbackNotVanilla > 3000 &&
                time - cache.lastWasFished > 4000 && time - cache.lastTeleport > 900 &&
                time - cache.lastRespawn > 500 && time - cache.lastEntityVeryNearby > 700 &&
                time - cache.lastBlockExplosion > 4000 && time - cache.lastEntityExplosion > 2000 &&
                time - cache.lastSlimeBlockVertical > 2500 && time - cache.lastSlimeBlockHorizontal > 2500 &&
                time - cache.lastHoneyBlockVertical > 2500 && time - cache.lastHoneyBlockHorizontal > 2500 &&
                time - cache.lastWasHit > 350 && time - cache.lastWasDamaged > 150 &&
                time - cache.lastFlight > 750 &&
                time - cache.lastWindCharge > 2000 && time - cache.lastWindChargeReceive > 1500 &&
                time - cache.lastWindBurst > 2000 && time - cache.lastWindBurstNotVanilla > 4500;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntityType() != EntityType.PLAYER)
            return;
        if (!(event.getEntity() instanceof Player))
            return;
        Player player = (Player) event.getEntity();
        if (isExternalNPC(player))
            return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL)
            return;
        Buffer buffer = getBuffer(player);
        resetBuffer(buffer);
        buffer.put("wasNearGround", true);
    }

    @EventHandler
    public void onAsyncMovement(LACAsyncPlayerMoveEvent event) {
        LACPlayer lacPlayer = event.getLacPlayer();
        PlayerCache cache = lacPlayer.cache;
        Player player = event.getPlayer();
        Buffer buffer = getBuffer(player, true);

        if (!isCheckAllowed(player, lacPlayer, true) ||
                !isConditionAllowed(player, lacPlayer, event) ||
                player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE ||
                !event.isToWithinBlocksPassable() || !event.isFromWithinBlocksPassable() ||
                System.currentTimeMillis() - lacPlayer.joinTime < 5000 ||
                System.currentTimeMillis() - cache.lastEntityNearby <= 3000 ||
                System.currentTimeMillis() - buffer.getLong("effectTime") < 1000 ||
                event.isPlayerInWater() || player.getAllowFlight() ||
                isSoftLandingBlock(player, event.getTo())) {
            resetBuffer(buffer);
            return;
        }

        boolean nearGround = isOnGround(player, 0.25, cache, LeanTowards.TRUE);
        double deltaY = distanceVertical(event.getFrom(), event.getTo());
        double fallDistance = buffer.getDouble("fallDistance");
        float serverFallDistance = player.getFallDistance();
        boolean wasNearGround = !buffer.isExists("wasNearGround") || buffer.getBoolean("wasNearGround");

        if (!nearGround && deltaY < 0.0D)
            fallDistance += -deltaY;

        if (nearGround && !wasNearGround) {
            if (fallDistance >= MINIMUM_FALL_DISTANCE) {
                buffer.put("expectingDamage", true);
                buffer.put("expectDamageSince", System.currentTimeMillis());
            }
            fallDistance = 0.0D;
        } else if (nearGround) {
            fallDistance = 0.0D;
        } else {
            boolean fallingFast = deltaY < -0.08D;
            boolean bigFall = fallDistance > 2.5D;
            double lastServerFallDistance = buffer.getDouble("lastServerFallDistance");
            boolean resetDetected = serverFallDistance == 0.0F ||
                    lastServerFallDistance > 2.0D && serverFallDistance + 0.25F < lastServerFallDistance;
            boolean groundSpoof = player.isOnGround() && !nearGround;
            if (fallingFast && bigFall && (groundSpoof || resetDetected))
                buffer.put("flags", Math.min(buffer.getInt("flags") + 1, 4));
            else
                buffer.put("flags", Math.max(buffer.getInt("flags") - 1, 0));

            if (buffer.getInt("flags") >= 3) {
                Scheduler.runTask(true, () -> callViolationEventIfRepeat(player, lacPlayer, event, buffer, 1500L));
                buffer.put("flags", 0);
            }
        }

        if (buffer.getBoolean("expectingDamage") &&
                System.currentTimeMillis() - buffer.getLong("expectDamageSince") > DAMAGE_WAIT_MS) {
            Scheduler.runTask(true, () -> callViolationEventIfRepeat(player, lacPlayer, event, buffer, 2000L));
            buffer.put("expectingDamage", false);
        }

        buffer.put("fallDistance", fallDistance);
        buffer.put("lastServerFallDistance", (double) serverFallDistance);
        buffer.put("wasNearGround", nearGround);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void beforeMovement(LACAsyncPlayerMoveEvent event) {
        LACPlayer lacPlayer = event.getLacPlayer();
        Player player = event.getPlayer();

        if (!isCheckAllowed(player, lacPlayer, true))
            return;

        if (getEffectAmplifier(lacPlayer.cache, VerUtil.potions.get("LEVITATION")) > 0 ||
                getEffectAmplifier(lacPlayer.cache, VerUtil.potions.get("SLOW_FALLING")) > 0 ||
                getEffectAmplifier(lacPlayer.cache, PotionEffectType.JUMP) > 3) {
            Buffer buffer = getBuffer(player, true);
            buffer.put("effectTime", System.currentTimeMillis());
        }
    }

    private boolean isSoftLandingBlock(Player player, Location location) {
        for (Block block : getWithinBlocks(player, location)) {
            if (isSoftLandingMaterial(block.getType()))
                return true;
        }
        for (Block block : getDownBlocks(player, location, 0.25)) {
            if (isSoftLandingMaterial(block.getType()))
                return true;
        }
        return false;
    }

    private boolean isSoftLandingMaterial(Material type) {
        Material cobweb = VerUtil.material.get("COBWEB");
        return type == cobweb || type == Material.HAY_BLOCK || type.name().endsWith("_BED") ||
                type == Material.WATER || type == Material.LAVA;
    }

    private void resetBuffer(Buffer buffer) {
        buffer.put("flags", 0);
        buffer.put("fallDistance", 0.0D);
        buffer.put("lastServerFallDistance", 0.0D);
        buffer.put("expectingDamage", false);
        buffer.put("expectDamageSince", 0L);
    }
}