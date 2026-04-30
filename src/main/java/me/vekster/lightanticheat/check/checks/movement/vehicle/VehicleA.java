package me.vekster.lightanticheat.check.checks.movement.vehicle;

import me.vekster.lightanticheat.check.CheckName;
import me.vekster.lightanticheat.check.buffer.Buffer;
import me.vekster.lightanticheat.check.checks.movement.MovementCheck;
import me.vekster.lightanticheat.event.packetrecive.LACAsyncPacketReceiveEvent;
import me.vekster.lightanticheat.event.packetrecive.packettype.PacketType;
import me.vekster.lightanticheat.event.playermove.LACPlayerMoveEvent;
import me.vekster.lightanticheat.player.LACPlayer;
import me.vekster.lightanticheat.player.cache.PlayerCache;
import me.vekster.lightanticheat.util.async.AsyncUtil;
import me.vekster.lightanticheat.util.detection.LeanTowards;
import me.vekster.lightanticheat.util.scheduler.Scheduler;
import me.vekster.lightanticheat.version.VerUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Set;

/**
 * Boat fly and vehicle speed limiter.
 */
public class VehicleA extends MovementCheck implements Listener {
    private static final double MAX_BOAT_GROUND_SPEED = 0.15D;
    private static final double MAX_BOAT_WATER_SPEED = 0.41D;
    private static final double BOAT_FLY_TOLERANCE = 0.02D;
    private static final double BOAT_GRAVITY = 0.04D;
    private static final double BOAT_DRAG = 0.98D;
    private static final int BOAT_GROUND_GRACE_TICKS = 2;
    private static final int BOAT_FLY_GRACE_TICKS = 4;
    private static final long BOAT_ENTITY_COLLISION_GRACE_MS = 1500L;
    private static final long ICE_GRACE_MS = 3000L;
    private static final double BOAT_WATER_BALANCE_THRESHOLD = 105.0D;
    private static final double BOAT_GROUND_BALANCE_THRESHOLD = 100.0D;
    private static final double BOAT_AIR_BALANCE_THRESHOLD = 95.0D;

    public VehicleA() {
        super(CheckName.VEHICLE_A);
    }

    @Override
    public boolean isConditionAllowed(Player player, LACPlayer lacPlayer, PlayerCache cache, boolean isClimbing, boolean isInWater,
                                      boolean isFlying, boolean isInsideVehicle, boolean isGliding, boolean isRiptiding) {
        if (isFlying || !isInsideVehicle || isGliding || isRiptiding)
            return false;
        if (cache.flyingTicks >= -5 || cache.climbingTicks >= -2 ||
                cache.glidingTicks >= -3 || cache.riptidingTicks >= -5)
            return false;
        long time = System.currentTimeMillis();
        return time - cache.lastKnockback > 500 && time - cache.lastKnockbackNotVanilla > 2000 &&
                time - cache.lastWasFished > 3000 && time - cache.lastTeleport > 500 &&
                time - cache.lastRespawn > 500 &&
                time - cache.lastBlockExplosion > 5500 && time - cache.lastEntityExplosion > 2000 &&
                time - cache.lastSlimeBlockVertical > 3500 && time - cache.lastSlimeBlockHorizontal > 3000 &&
                time - cache.lastHoneyBlockVertical > 2000 && time - cache.lastHoneyBlockHorizontal > 2000 &&
                time - cache.lastWasHit > 300 && time - cache.lastWasDamaged > 150 &&
                time - cache.lastKbVelocity > 250 && time - cache.lastAirKbVelocity > 500 &&
                time - cache.lastStrongKbVelocity > 1250 && time - cache.lastStrongAirKbVelocity > 2500 &&
                time - cache.lastFlight > 750;
    }

    @EventHandler
    public void boatMovement(LACPlayerMoveEvent event) {
        LACPlayer lacPlayer = event.getLacPlayer();
        Player player = event.getPlayer();
        Buffer buffer = getBuffer(player);

        if (!isCheckAllowed(player, lacPlayer) ||
                !isConditionAllowed(player, lacPlayer, event, false, false)) {
            resetBoat(buffer, true);
            return;
        }

        Entity boat = player.getVehicle();
        if (!isBoat(boat)) {
            resetBoat(buffer, true);
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (boat.isInsideVehicle() ||
                currentTime - lacPlayer.cache.lastTeleport < 500 ||
                currentTime - lacPlayer.cache.lastSlimeBlock < 1500 ||
                currentTime - lacPlayer.cache.lastHoneyBlock < 1500) {
            resetBoat(buffer, true);
            return;
        }

        if (currentTime - buffer.getLong("boatEntityCollisionTime") < BOAT_ENTITY_COLLISION_GRACE_MS) {
            resetBoat(buffer, true);
            return;
        }
        if (AsyncUtil.getNearbyEntities(boat, 1, 2, 1).size() > 1) {
            buffer.put("boatEntityCollisionTime", currentTime);
            resetBoat(buffer, true);
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        Location previous = buffer.getLocation("boatPreviousLocation");
        buffer.put("boatPreviousLocation", from);

        Location boatLocation = boat.getLocation();
        Location previousBoatLocation = buffer.getLocation("boatLocation");
        buffer.put("boatLocation", boatLocation);

        double deltaY = distanceVertical(from, to);
        double previousDeltaY = buffer.getDouble("boatLastDeltaY");
        buffer.put("boatLastDeltaY", deltaY);

        if (previous == null || previousBoatLocation == null)
            return;

        if (hasSolidCollision(boat, previousBoatLocation) || hasSolidCollision(boat, boatLocation)) {
            resetBoat(buffer, true);
            return;
        }

        double horizontalSpeed = getBoatHorizontalSpeed(previous, from, to);

        Set<Block> toDownBlocks = getDownBlocks(boat, boatLocation, 0.45);
        Set<Block> fromDownBlocks = getDownBlocks(boat, previousBoatLocation, 0.45);
        if (isIce(toDownBlocks) || isIce(fromDownBlocks))
            buffer.put("boatIceTime", currentTime);

        boolean onWater = isTouchingLiquid(boat, previousBoatLocation) || isTouchingLiquid(boat, boatLocation);
        if (onWater) {
            buffer.put("boatGroundTicks", 0);
            buffer.put("boatAirTicks", 0);
            buffer.put("boatExpectedFallVelocity", 0.0D);
            decayBoatAir(buffer);
            decayBoatGround(buffer);
            int waterTicks = buffer.getInt("boatWaterTicks") + 1;
            buffer.put("boatWaterTicks", waterTicks);

            if (waterTicks <= 1 || currentTime - buffer.getLong("boatIceTime") < ICE_GRACE_MS) {
                decayBoatWater(buffer);
                return;
            }

            double waterExcess = horizontalSpeed - MAX_BOAT_WATER_SPEED;
            if (waterExcess > 0.0D) {
                int boatWaterFlags = buffer.getInt("boatWaterFlags") + (waterExcess > 0.08D ? 2 : 1);
                buffer.put("boatWaterFlags", Math.min(boatWaterFlags, 6));

                double boatWaterBalance = Math.max(buffer.getDouble("boatWaterBalance") - 20.0D, -250.0D);
                boatWaterBalance += 35.0D + waterExcess * 260.0D;
                buffer.put("boatWaterBalance", Math.min(boatWaterBalance, 320.0D));
            } else {
                decayBoatWater(buffer);
            }

            if (buffer.getInt("boatWaterFlags") >= 3 ||
                    buffer.getDouble("boatWaterBalance") >= BOAT_WATER_BALANCE_THRESHOLD)
                flagBoat(player, lacPlayer, event, buffer);
            return;
        }

        buffer.put("boatWaterTicks", 0);
        decayBoatWater(buffer);

        if (isBoatOnGround(boat, boatLocation, false)) {
            buffer.put("boatAirTicks", 0);
            buffer.put("boatExpectedFallVelocity", 0.0D);
            decayBoatAir(buffer);
            int groundTicks = buffer.getInt("boatGroundTicks") + 1;
            buffer.put("boatGroundTicks", groundTicks);

            if (groundTicks <= BOAT_GROUND_GRACE_TICKS || currentTime - buffer.getLong("boatIceTime") < ICE_GRACE_MS) {
                decayBoatGround(buffer);
                return;
            }

            double groundExcess = horizontalSpeed - MAX_BOAT_GROUND_SPEED;
            if (groundExcess > 0.0D) {
                int boatGroundFlags = buffer.getInt("boatGroundFlags") + (groundExcess > 0.06D ? 2 : 1);
                buffer.put("boatGroundFlags", Math.min(boatGroundFlags, 6));

                double boatGroundBalance = Math.max(buffer.getDouble("boatGroundBalance") - 20.0D, -250.0D);
                boatGroundBalance += 40.0D + groundExcess * 320.0D;
                buffer.put("boatGroundBalance", Math.min(boatGroundBalance, 320.0D));
            } else {
                decayBoatGround(buffer);
            }

            if (buffer.getInt("boatGroundFlags") >= 3 ||
                    buffer.getDouble("boatGroundBalance") >= BOAT_GROUND_BALANCE_THRESHOLD)
                flagBoat(player, lacPlayer, event, buffer);
            return;
        }

        buffer.put("boatGroundTicks", 0);
        decayBoatGround(buffer);

        int airTicks = buffer.getInt("boatAirTicks") + 1;
        buffer.put("boatAirTicks", airTicks);

        double expectedFallVelocity = airTicks == 1 ? previousDeltaY : buffer.getDouble("boatExpectedFallVelocity");
        expectedFallVelocity = (expectedFallVelocity - BOAT_GRAVITY) * BOAT_DRAG;
        buffer.put("boatExpectedFallVelocity", expectedFallVelocity);

        if (airTicks <= BOAT_FLY_GRACE_TICKS) {
            decayBoatAir(buffer);
            return;
        }

        double boatFlyExcess = deltaY - expectedFallVelocity - BOAT_FLY_TOLERANCE;
        if (boatFlyExcess > 0.0D) {
            int boatAirFlags = buffer.getInt("boatAirFlags") + (boatFlyExcess > 0.08D ? 2 : 1);
            buffer.put("boatAirFlags", Math.min(boatAirFlags, 6));

            double boatAirBalance = Math.max(buffer.getDouble("boatAirBalance") - 15.0D, -250.0D);
            boatAirBalance += 35.0D + boatFlyExcess * 500.0D;
            buffer.put("boatAirBalance", Math.min(boatAirBalance, 320.0D));
        } else {
            decayBoatAir(buffer);
        }

        if (buffer.getInt("boatAirFlags") >= 3 ||
                buffer.getDouble("boatAirBalance") >= BOAT_AIR_BALANCE_THRESHOLD)
            flagBoat(player, lacPlayer, event, buffer);
    }

    @EventHandler
    public void vehicleSpeedAndFlight(LACAsyncPacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.STEER_VEHICLE)
            return;

        LACPlayer lacPlayer = event.getLacPlayer();
        Player player = event.getPlayer();
        Buffer buffer = getBuffer(player, true);

        if (!isCheckAllowed(player, lacPlayer, true)) {
            resetLegacy(buffer);
            resetBoat(buffer, true);
            return;
        }

        if (!isConditionAllowed(player, lacPlayer, lacPlayer.cache, false, false, player.isFlying(),
                player.isInsideVehicle() && player.getVehicle() != null, lacPlayer.isGliding(), lacPlayer.isRiptiding())) {
            resetLegacy(buffer);
            resetBoat(buffer, true);
            return;
        }

        Entity vehicle = player.getVehicle();
        if (vehicle == null) {
            resetLegacy(buffer);
            resetBoat(buffer, true);
            return;
        }
        if (isBoat(vehicle)) {
            boatMovementPacket(player, lacPlayer, vehicle, buffer);
            return;
        }

        if (vehicle.getType() != EntityType.HORSE &&
                vehicle.getType() != VerUtil.entityTypes.get("MULE") &&
                vehicle.getType() != EntityType.PIG) {
            resetLegacy(buffer);
            return;
        }

        Location to = vehicle.getLocation();
        Location from = buffer.getLocation("fromLocation");
        Location previous = buffer.getLocation("previousLocation");
        buffer.put("previousLocation", from);
        buffer.put("fromLocation", to);
        if (from == null || previous == null) {
            buffer.put("vehicleSpeedEvents", 0);
            return;
        }

        for (Block block : getWithinBlocks(vehicle, to))
            if (!isActuallyPassable(block)) {
                buffer.put("vehicleSpeedEvents", 0);
                buffer.put("previousVerticalSpeed", distanceVertical(from, to));
                return;
            }
        for (Block block : getWithinBlocks(vehicle, from))
            if (!isActuallyPassable(block)) {
                buffer.put("vehicleSpeedEvents", 0);
                buffer.put("previousVerticalSpeed", distanceVertical(from, to));
                return;
            }

        buffer.put("vehicleSpeedEvents", buffer.getInt("vehicleSpeedEvents") + 1);
        if (buffer.getInt("vehicleSpeedEvents") <= 2) {
            buffer.put("previousVerticalSpeed", distanceVertical(from, to));
            return;
        }

        boolean flag = false;
        double horizontalSpeed = getBoatHorizontalSpeed(previous, from, to);
        if (horizontalSpeed > 3.65 * 1.35)
            flag = true;

        double verticalSpeed = distanceVertical(from, to);
        if (!flag && buffer.getInt("vehicleSpeedEvents") >= 4 && buffer.isExists("previousVerticalSpeed")) {
            if (!isBlockHeight((float) getBlockY(from.getY())) &&
                    !isBlockHeight((float) getBlockY(to.getY())) &&
                    !isOnGround(vehicle, 0.5, LeanTowards.TRUE, true)) {
                double previousVerticalSpeed = buffer.getDouble("previousVerticalSpeed");
                if (previousVerticalSpeed != 0) {
                    if (previousVerticalSpeed + 0.002 >= verticalSpeed)
                        buffer.put("verticalFlags", Math.min(buffer.getInt("verticalFlags") + 1, 4));
                    else
                        buffer.put("verticalFlags", Math.max(buffer.getInt("verticalFlags") - 2, 0));
                    if (buffer.getInt("verticalFlags") >= 4)
                        flag = true;
                }
            }
            for (Block block : getDownBlocks(vehicle, 0.4)) {
                if (isActuallyPassable(block))
                    continue;
                flag = false;
                break;
            }
        }

        buffer.put("previousVerticalSpeed", verticalSpeed);
        if (!flag)
            return;

        Scheduler.runTask(true, () -> callViolationEventIfRepeat(player, lacPlayer, null, buffer, 2000L));
    }

    private void flagBoat(Player player, LACPlayer lacPlayer, LACPlayerMoveEvent event, Buffer buffer) {
        Scheduler.runTask(true, () -> callViolationEventIfRepeat(player, lacPlayer, event.getEvent(), buffer, 1500L));
    }

    private void flagBoat(Player player, LACPlayer lacPlayer, Buffer buffer) {
        Scheduler.runTask(true, () -> callViolationEventIfRepeat(player, lacPlayer, null, buffer, 1500L));
    }

    private void boatMovementPacket(Player player, LACPlayer lacPlayer, Entity boat, Buffer buffer) {
        long currentTime = System.currentTimeMillis();
        if (boat.isInsideVehicle() ||
                currentTime - lacPlayer.cache.lastTeleport < 500 ||
                currentTime - lacPlayer.cache.lastSlimeBlock < 1500 ||
                currentTime - lacPlayer.cache.lastHoneyBlock < 1500) {
            resetBoat(buffer, true);
            return;
        }

        if (currentTime - buffer.getLong("boatEntityCollisionTime") < BOAT_ENTITY_COLLISION_GRACE_MS) {
            resetBoat(buffer, true);
            return;
        }
        if (AsyncUtil.getNearbyEntities(boat, 1, 2, 1).size() > 1) {
            buffer.put("boatEntityCollisionTime", currentTime);
            resetBoat(buffer, true);
            return;
        }

        Location to = boat.getLocation();
        Location from = buffer.getLocation("boatLocation");
        Location previous = buffer.getLocation("boatPreviousLocation");
        buffer.put("boatPreviousLocation", from);
        buffer.put("boatLocation", to);
        if (from == null || previous == null)
            return;

        double previousDeltaY = buffer.getDouble("boatLastDeltaY");
        double deltaY = distanceVertical(from, to);
        buffer.put("boatLastDeltaY", deltaY);

        if (hasSolidCollision(boat, from) || hasSolidCollision(boat, to)) {
            resetBoat(buffer, true);
            return;
        }

        double horizontalSpeed = Math.min(
                distanceHorizontal(from, to),
                distanceHorizontal(previous, to) / 2.0
        );

        Set<Block> toDownBlocks = getDownBlocks(boat, to, 0.45);
        Set<Block> fromDownBlocks = getDownBlocks(boat, from, 0.45);
        if (isIce(toDownBlocks) || isIce(fromDownBlocks))
            buffer.put("boatIceTime", currentTime);

        boolean onWater = isTouchingLiquid(boat, from) || isTouchingLiquid(boat, to);
        if (onWater) {
            buffer.put("boatGroundTicks", 0);
            buffer.put("boatAirTicks", 0);
            buffer.put("boatExpectedFallVelocity", 0.0D);
            decayBoatAir(buffer);
            decayBoatGround(buffer);
            int waterTicks = buffer.getInt("boatWaterTicks") + 1;
            buffer.put("boatWaterTicks", waterTicks);

            if (waterTicks <= 1 || currentTime - buffer.getLong("boatIceTime") < ICE_GRACE_MS) {
                decayBoatWater(buffer);
                return;
            }

            double waterExcess = horizontalSpeed - MAX_BOAT_WATER_SPEED;
            if (waterExcess > 0.0D) {
                int boatWaterFlags = buffer.getInt("boatWaterFlags") + (waterExcess > 0.08D ? 2 : 1);
                buffer.put("boatWaterFlags", Math.min(boatWaterFlags, 6));

                double boatWaterBalance = Math.max(buffer.getDouble("boatWaterBalance") - 20.0D, -250.0D);
                boatWaterBalance += 35.0D + waterExcess * 260.0D;
                buffer.put("boatWaterBalance", Math.min(boatWaterBalance, 320.0D));
            } else {
                decayBoatWater(buffer);
            }

            if (buffer.getInt("boatWaterFlags") >= 3 ||
                    buffer.getDouble("boatWaterBalance") >= BOAT_WATER_BALANCE_THRESHOLD)
                flagBoat(player, lacPlayer, buffer);
            return;
        }

        buffer.put("boatWaterTicks", 0);
        decayBoatWater(buffer);

        if (isBoatOnGround(boat, to, true)) {
            buffer.put("boatAirTicks", 0);
            buffer.put("boatExpectedFallVelocity", 0.0D);
            decayBoatAir(buffer);
            int groundTicks = buffer.getInt("boatGroundTicks") + 1;
            buffer.put("boatGroundTicks", groundTicks);

            if (groundTicks <= BOAT_GROUND_GRACE_TICKS || currentTime - buffer.getLong("boatIceTime") < ICE_GRACE_MS) {
                decayBoatGround(buffer);
                return;
            }

            double groundExcess = horizontalSpeed - MAX_BOAT_GROUND_SPEED;
            if (groundExcess > 0.0D) {
                int boatGroundFlags = buffer.getInt("boatGroundFlags") + (groundExcess > 0.06D ? 2 : 1);
                buffer.put("boatGroundFlags", Math.min(boatGroundFlags, 6));

                double boatGroundBalance = Math.max(buffer.getDouble("boatGroundBalance") - 20.0D, -250.0D);
                boatGroundBalance += 40.0D + groundExcess * 320.0D;
                buffer.put("boatGroundBalance", Math.min(boatGroundBalance, 320.0D));
            } else {
                decayBoatGround(buffer);
            }

            if (buffer.getInt("boatGroundFlags") >= 3 ||
                    buffer.getDouble("boatGroundBalance") >= BOAT_GROUND_BALANCE_THRESHOLD)
                flagBoat(player, lacPlayer, buffer);
            return;
        }

        buffer.put("boatGroundTicks", 0);
        decayBoatGround(buffer);

        int airTicks = buffer.getInt("boatAirTicks") + 1;
        buffer.put("boatAirTicks", airTicks);

        double expectedFallVelocity = airTicks == 1 ? previousDeltaY : buffer.getDouble("boatExpectedFallVelocity");
        expectedFallVelocity = (expectedFallVelocity - BOAT_GRAVITY) * BOAT_DRAG;
        buffer.put("boatExpectedFallVelocity", expectedFallVelocity);

        if (airTicks <= BOAT_FLY_GRACE_TICKS) {
            decayBoatAir(buffer);
            return;
        }

        double boatFlyExcess = deltaY - expectedFallVelocity - BOAT_FLY_TOLERANCE;
        if (boatFlyExcess > 0.0D) {
            int boatAirFlags = buffer.getInt("boatAirFlags") + (boatFlyExcess > 0.08D ? 2 : 1);
            buffer.put("boatAirFlags", Math.min(boatAirFlags, 6));

            double boatAirBalance = Math.max(buffer.getDouble("boatAirBalance") - 15.0D, -250.0D);
            boatAirBalance += 35.0D + boatFlyExcess * 500.0D;
            buffer.put("boatAirBalance", Math.min(boatAirBalance, 320.0D));
        } else {
            decayBoatAir(buffer);
        }

        if (buffer.getInt("boatAirFlags") >= 3 ||
                buffer.getDouble("boatAirBalance") >= BOAT_AIR_BALANCE_THRESHOLD)
            flagBoat(player, lacPlayer, buffer);
    }

    private boolean isBoat(Entity vehicle) {
        return vehicle != null && (vehicle.getType() == EntityType.BOAT ||
                vehicle.getType().name().equalsIgnoreCase("CHEST_BOAT"));
    }

    private double getBoatHorizontalSpeed(Location previous, Location from, Location to) {
        double instantSpeed = distanceHorizontal(from, to);
        if (previous == null)
            return instantSpeed;
        return Math.max(instantSpeed, distanceHorizontal(previous, to) / 2.0D);
    }

    private boolean isTouchingLiquid(Entity vehicle, Location location) {
        for (Block block : getWithinBlocks(vehicle, location)) {
            if (block.isLiquid())
                return true;
        }
        for (Block block : getDownBlocks(vehicle, location, 0.45)) {
            if (block.isLiquid() || block.getRelative(0, -1, 0).isLiquid())
                return true;
        }
        return false;
    }

    private boolean isBoatOnGround(Entity vehicle, Location location, boolean async) {
        if (isOnGround(vehicle, 0.08, LeanTowards.FALSE, async))
            return true;
        for (Block block : getDownBlocks(vehicle, location, 0.08)) {
            if (block.isLiquid())
                continue;
            if (!isActuallyPassable(block))
                return true;
        }
        return false;
    }

    private boolean hasSolidCollision(Entity vehicle, Location location) {
        for (Block block : getWithinBlocks(vehicle, location)) {
            if (block.isLiquid())
                continue;
            if (!isActuallyPassable(block))
                return true;
        }
        return false;
    }

    private void resetBoat(Buffer buffer, boolean clearLocations) {
        buffer.put("boatWaterTicks", 0);
        buffer.put("boatGroundTicks", 0);
        buffer.put("boatGroundFlags", 0);
        buffer.put("boatGroundBalance", 0.0D);
        buffer.put("boatWaterFlags", 0);
        buffer.put("boatWaterBalance", 0.0D);
        buffer.put("boatAirTicks", 0);
        buffer.put("boatAirFlags", 0);
        buffer.put("boatAirBalance", 0.0D);
        buffer.put("boatExpectedFallVelocity", 0.0D);
        if (clearLocations) {
            buffer.put("boatLocation", null);
            buffer.put("boatPreviousLocation", null);
            buffer.put("boatLastDeltaY", 0.0D);
        }
    }

    private void decayBoatWater(Buffer buffer) {
        buffer.put("boatWaterFlags", Math.max(buffer.getInt("boatWaterFlags") - 1, 0));
        buffer.put("boatWaterBalance", Math.max(buffer.getDouble("boatWaterBalance") - 30.0D, -250.0D));
    }

    private void decayBoatGround(Buffer buffer) {
        buffer.put("boatGroundFlags", Math.max(buffer.getInt("boatGroundFlags") - 1, 0));
        buffer.put("boatGroundBalance", Math.max(buffer.getDouble("boatGroundBalance") - 30.0D, -250.0D));
    }

    private void decayBoatAir(Buffer buffer) {
        buffer.put("boatAirFlags", Math.max(buffer.getInt("boatAirFlags") - 1, 0));
        buffer.put("boatAirBalance", Math.max(buffer.getDouble("boatAirBalance") - 25.0D, -250.0D));
    }

    private void resetLegacy(Buffer buffer) {
        buffer.put("vehicleSpeedEvents", 0);
        buffer.put("fromLocation", null);
        buffer.put("previousLocation", null);
        buffer.put("previousVerticalSpeed", 0.0D);
        buffer.put("verticalFlags", 0);
    }

    private static boolean isIce(Set<Block> blocks) {
        Material blueIce = VerUtil.material.get("BLUE_ICE");
        for (Block block : blocks) {
            Material type = block.getType();
            if (type == Material.ICE || type == Material.PACKED_ICE || type == blueIce)
                return true;
        }
        return false;
    }
}
