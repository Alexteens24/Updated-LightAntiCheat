package me.vekster.lightanticheat.check.checks.movement.fastclimb;

import me.vekster.lightanticheat.check.CheckName;
import me.vekster.lightanticheat.check.buffer.Buffer;
import me.vekster.lightanticheat.check.checks.movement.MovementCheck;
import me.vekster.lightanticheat.event.playermove.LACAsyncPlayerMoveEvent;
import me.vekster.lightanticheat.player.LACPlayer;
import me.vekster.lightanticheat.player.cache.PlayerCache;
import me.vekster.lightanticheat.player.cache.history.HistoryElement;
import me.vekster.lightanticheat.util.async.AsyncUtil;
import me.vekster.lightanticheat.util.hook.plugin.FloodgateHook;
import me.vekster.lightanticheat.util.scheduler.Scheduler;
import me.vekster.lightanticheat.version.VerUtil;
import me.vekster.lightanticheat.version.identifier.LACVersion;
import me.vekster.lightanticheat.version.identifier.VerIdentifier;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;

public class FastClimbA extends MovementCheck implements Listener {
    private static final double VANILLA_CLIMB_UP = 0.1176001D;
    private static final double SOFT_MAX_CLIMB_UP = 0.1375D;
    private static final double HARD_MAX_CLIMB_UP = 0.1625D;
    private static final long CLIMB_PAUSE_GRACE_MS = 175L;
    private static final long CLIMB_WINDOW_MS = 175L;
    private static final double CLIMB_WINDOW_GRACE_DISTANCE = 0.035D;

    public FastClimbA() {
        super(CheckName.FASTCLIMB_A);
    }

    @Override
    public boolean isConditionAllowed(Player player, LACPlayer lacPlayer, PlayerCache cache, boolean isClimbing, boolean isInWater,
                                      boolean isFlying, boolean isInsideVehicle, boolean isGliding, boolean isRiptiding) {
        if (isFlying || isInsideVehicle || !isClimbing || isGliding || isRiptiding || isInWater)
            return false;
        if (cache.flyingTicks >= -5 || cache.climbingTicks <= 2 ||
                cache.glidingTicks >= -3 || cache.riptidingTicks >= -3)
            return false;
        long time = System.currentTimeMillis();
        return time - cache.lastInsideVehicle > 150 && time - cache.lastInWater > 150 &&
                time - cache.lastKnockback > 500 && time - cache.lastKnockbackNotVanilla > 2000 &&
                time - cache.lastWasFished > 4000 && time - cache.lastTeleport > 500 &&
                time - cache.lastRespawn > 500 && time - cache.lastEntityVeryNearby > 200 &&
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

        if (!isCheckAllowed(player, lacPlayer, true)) {
            reset(buffer);
            return;
        }

        if (!isConditionAllowed(player, lacPlayer, event)) {
            reset(buffer);
            return;
        }

        if (FloodgateHook.isBedrockPlayer(player, true)) {
            reset(buffer);
            return;
        }

        if (getEffectAmplifier(cache, PotionEffectType.JUMP) > 0 ||
                getEffectAmplifier(cache, VerUtil.potions.get("LEVITATION")) > 0) {
            reset(buffer);
            return;
        }

        Set<Material> fromMaterials = getClimbMaterials(player, event.getFrom(), event.getFromWithinMaterials());
        Set<Material> toMaterials = getClimbMaterials(player, event.getTo(), event.getToWithinMaterials());
        Material scaffolding = VerUtil.material.get("SCAFFOLDING");
        if (scaffolding != null && (fromMaterials.contains(scaffolding) || toMaterials.contains(scaffolding))) {
            reset(buffer);
            return;
        }
        if (!hasClimbMaterial(fromMaterials) || !hasClimbMaterial(toMaterials)) {
            reset(buffer);
            return;
        }
        if (!hasStableClimbColumn(player)) {
            reset(buffer);
            return;
        }

        long currentTime = System.currentTimeMillis();
        long lastClimbMillis = buffer.getLong("lastClimbMillis");
        buffer.put("lastClimbMillis", currentTime);

        double deltaY = distanceVertical(event.getFrom(), event.getTo());
        double horizontalSpeed = distanceHorizontal(event.getFrom(), event.getTo());
        if (deltaY <= 0.0D) {
            if (currentTime - buffer.getLong("lastPositiveClimbMillis") > CLIMB_PAUSE_GRACE_MS) {
                buffer.put("positiveClimbTicks", 0);
                buffer.put("climbWindowStart", 0L);
                buffer.put("climbWindowDistance", 0.0D);
            }
            decay(buffer);
            return;
        }
        if (horizontalSpeed > 0.18D && deltaY < HARD_MAX_CLIMB_UP) {
            reset(buffer);
            return;
        }

        buffer.put("lastPositiveClimbMillis", currentTime);
        if (lastClimbMillis == 0L)
            return;

        double sampledDeltaY = getSampledDeltaY(cache, event, deltaY);
        if (isLegacyClimbTransition(deltaY) || isLegacyClimbTransition(sampledDeltaY)) {
            decay(buffer);
            return;
        }

        int positiveClimbTicks = buffer.getInt("positiveClimbTicks") + 1;
        buffer.put("positiveClimbTicks", positiveClimbTicks);

        double softMax = SOFT_MAX_CLIMB_UP;
        double hardMax = HARD_MAX_CLIMB_UP;
        if (VerIdentifier.getVersion().isOlderThan(LACVersion.V1_13)) {
            softMax += 0.02D;
            hardMax += 0.03D;
        }

        int rapidClimbFlags = buffer.getInt("rapidClimbFlags");
        if (sampledDeltaY > hardMax)
            rapidClimbFlags = Math.min(rapidClimbFlags + 2, 6);
        else if (sampledDeltaY > softMax)
            rapidClimbFlags = Math.min(rapidClimbFlags + 1, 6);
        else
            rapidClimbFlags = Math.max(rapidClimbFlags - 1, 0);
        buffer.put("rapidClimbFlags", rapidClimbFlags);

        long deltaMillis = currentTime - lastClimbMillis;
        double climbBalance = Math.max(buffer.getDouble("climbBalance") - Math.min(deltaMillis, 120L), -250.0D);
        double climbExcess = sampledDeltaY - VANILLA_CLIMB_UP - 0.01D;
        if (climbExcess > 0.0D)
            climbBalance += 25.0D + climbExcess * 1150.0D;
        else
            climbBalance = Math.max(climbBalance - 15.0D, -250.0D);
        if (sampledDeltaY > softMax)
            climbBalance += 20.0D + (sampledDeltaY - softMax) * 1250.0D;
        buffer.put("climbBalance", Math.min(climbBalance, 320.0D));

        long climbWindowStart = buffer.getLong("climbWindowStart");
        double climbWindowDistance = buffer.getDouble("climbWindowDistance");
        if (climbWindowStart == 0L || deltaMillis > CLIMB_PAUSE_GRACE_MS || currentTime - climbWindowStart > CLIMB_WINDOW_MS) {
            climbWindowStart = currentTime;
            climbWindowDistance = deltaY;
        } else {
            climbWindowDistance += deltaY;
        }
        buffer.put("climbWindowStart", climbWindowStart);
        buffer.put("climbWindowDistance", climbWindowDistance);

        long climbWindowAge = Math.max(currentTime - climbWindowStart, 1L);
        double allowedSamples = Math.floor((climbWindowAge + 25.0D) / 50.0D) + 1.0D;
        double allowedWindowDistance = allowedSamples * VANILLA_CLIMB_UP + CLIMB_WINDOW_GRACE_DISTANCE;
        double climbWindowBalance = Math.max(buffer.getDouble("climbWindowBalance") - Math.min(deltaMillis, 120L), -250.0D);
        double climbWindowExcess = climbWindowDistance - allowedWindowDistance;
        if (climbWindowExcess > 0.0D)
            climbWindowBalance += 30.0D + climbWindowExcess * 1050.0D;
        else
            climbWindowBalance = Math.max(climbWindowBalance - 20.0D, -250.0D);
        buffer.put("climbWindowBalance", Math.min(climbWindowBalance, 320.0D));

        if (positiveClimbTicks < 2)
            return;
        if (rapidClimbFlags < 3 && climbBalance < 110.0D && climbWindowBalance < 120.0D)
            return;

        Scheduler.runTask(true, () -> callViolationEventIfRepeat(player, lacPlayer, event, buffer, 1500L));
        reset(buffer);
    }

    private Set<Material> getClimbMaterials(Player player, Location location, Set<Material> withinMaterials) {
        Set<Material> materials = new HashSet<>();
        materials.addAll(withinMaterials);
        getWithinBlocks(player, location).forEach(block -> materials.add(block.getType()));
        return materials;
    }

    private boolean hasStableClimbColumn(Player player) {
        Location location = player.getLocation();
        Block feet = AsyncUtil.getBlock(location);
        Block head = AsyncUtil.getBlock(location.clone().add(0.0D, 1.0D, 0.0D));
        Block below = AsyncUtil.getBlock(location.clone().subtract(0.0D, 1.0D, 0.0D));
        return isSameClimbType(feet, head) || isSameClimbType(feet, below) || isSameClimbType(head, below);
    }

    private boolean isSameClimbType(Block first, Block second) {
        return first != null && second != null &&
                first.getType() == second.getType() &&
                isClimbMaterial(first.getType());
    }

    private boolean hasClimbMaterial(Set<Material> materials) {
        for (Material material : materials)
            if (isClimbMaterial(material))
                return true;
        return false;
    }

    private boolean isClimbMaterial(Material material) {
        if (material == null)
            return false;
        String name = material.name();
        return name.equals("LADDER") || name.contains("VINE");
    }

    private double getSampledDeltaY(PlayerCache cache, LACAsyncPlayerMoveEvent event, double deltaY) {
        double sampledDeltaY = deltaY;

        double eventSample1 = distanceVertical(cache.history.onEvent.location.get(HistoryElement.FIRST), event.getTo()) / 2.0D;
        if (Math.abs(eventSample1) < Math.abs(sampledDeltaY))
            sampledDeltaY = eventSample1;

        double eventSample2 = distanceVertical(cache.history.onEvent.location.get(HistoryElement.SECOND), event.getTo()) / 3.0D;
        if (Math.abs(eventSample2) < Math.abs(sampledDeltaY))
            sampledDeltaY = eventSample2;

        double packetSample = distanceVertical(cache.history.onPacket.location.get(HistoryElement.FIRST), event.getTo());
        if (Math.abs(packetSample) < Math.abs(sampledDeltaY))
            sampledDeltaY = packetSample;

        return sampledDeltaY;
    }

    private boolean isLegacyClimbTransition(double deltaY) {
        return Math.abs(deltaY - 0.5D - VANILLA_CLIMB_UP) < 0.03D ||
                Math.abs(deltaY - 0.5D + 0.15001D) < 0.03D;
    }

    private void decay(Buffer buffer) {
        buffer.put("rapidClimbFlags", Math.max(buffer.getInt("rapidClimbFlags") - 1, 0));
        buffer.put("climbBalance", Math.max(buffer.getDouble("climbBalance") - 35.0D, -250.0D));
        buffer.put("climbWindowBalance", Math.max(buffer.getDouble("climbWindowBalance") - 40.0D, -250.0D));
    }

    private void reset(Buffer buffer) {
        buffer.put("positiveClimbTicks", 0);
        buffer.put("rapidClimbFlags", 0);
        buffer.put("climbBalance", 0.0D);
        buffer.put("climbWindowBalance", 0.0D);
        buffer.put("climbWindowStart", 0L);
        buffer.put("climbWindowDistance", 0.0D);
        buffer.put("lastClimbMillis", 0L);
        buffer.put("lastPositiveClimbMillis", 0L);
    }
}
