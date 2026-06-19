package me.vekster.lightanticheat.check.checks.movement.step;

import me.vekster.lightanticheat.Main;
import me.vekster.lightanticheat.check.CheckName;
import me.vekster.lightanticheat.check.buffer.Buffer;
import me.vekster.lightanticheat.check.checks.movement.MovementCheck;
import me.vekster.lightanticheat.event.playermove.LACAsyncPlayerMoveEvent;
import me.vekster.lightanticheat.player.LACPlayer;
import me.vekster.lightanticheat.player.cache.PlayerCache;
import me.vekster.lightanticheat.player.cache.history.HistoryElement;
import me.vekster.lightanticheat.util.hook.plugin.FloodgateHook;
import me.vekster.lightanticheat.util.scheduler.Scheduler;
import me.vekster.lightanticheat.version.VerPlayer;
import me.vekster.lightanticheat.version.VerUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;

/**
 * Step hack (vanilla step-height bypass)
 */
public class StepA extends MovementCheck implements Listener {
    private static final double MAX_STEP_HEIGHT = 0.6000000238418579D;
    private static final long GLIDE_LINGER_MS = 2500L;

    public StepA() {
        super(CheckName.STEP_A);
    }

    @Override
    public boolean isConditionAllowed(Player player, LACPlayer lacPlayer, PlayerCache cache, boolean isClimbing, boolean isInWater,
                                      boolean isFlying, boolean isInsideVehicle, boolean isGliding, boolean isRiptiding) {
        if (isFlying || isInsideVehicle || isClimbing || isGliding || isRiptiding || isInWater)
            return false;
        if (cache.flyingTicks >= -4 || cache.climbingTicks >= -2 ||
                cache.glidingTicks >= -4 || cache.riptidingTicks >= -4)
            return false;
        long time = System.currentTimeMillis();
        return time - cache.lastInsideVehicle > 250 && time - cache.lastInWater > 250 &&
                time - cache.lastKnockback > 2500 && time - cache.lastKnockbackNotVanilla > 5000 &&
                time - cache.lastWasFished > 4000 && time - cache.lastTeleport > 3000 &&
                time - cache.lastWorldChange > 3000 && time - cache.lastRespawn > 1000 &&
                time - cache.lastBlockPlace > 1200 && time - cache.lastBlockBreak > 750 &&
                time - cache.lastEntityVeryNearby > 500 &&
                time - cache.lastBlockExplosion > 5000 && time - cache.lastEntityExplosion > 3000 &&
                time - cache.lastSlimeBlock > 3500 && time - cache.lastHoneyBlock > 2500 &&
                time - cache.lastWasHit > 2500 && time - cache.lastWasDamaged > 2500 &&
                time - cache.lastKbVelocity > 2500 && time - cache.lastAirKbVelocity > 2500 &&
                time - cache.lastStrongKbVelocity > 2500 && time - cache.lastStrongAirKbVelocity > 2500 &&
                time - cache.lastFlight > 1000 && time - cache.lastGliding > GLIDE_LINGER_MS &&
                time - cache.lastRiptiding > 2000 && time - cache.lastWindCharge > 2000 &&
                time - cache.lastWindChargeReceive > 2000 && time - cache.lastWindBurst > 2000;
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

        if (FloodgateHook.isCancelledMovement(getCheckSetting().name, player, true)) {
            reset(buffer);
            return;
        }

        if (getEffectAmplifier(cache, PotionEffectType.JUMP) > 0 ||
                getEffectAmplifier(cache, VerUtil.potions.get("LEVITATION")) > 0 ||
                getEffectAmplifier(cache, VerUtil.potions.get("SLOW_FALLING")) > 1) {
            reset(buffer);
            return;
        }

        if (hasRiptide(player) || !event.isFromWithinBlocksPassable() || !event.isToWithinBlocksPassable() ||
                hasPartialSupport(event.getFromWithinMaterials()) || hasPartialSupport(event.getToWithinMaterials()) ||
                hasPartialSupport(event.getFromDownMaterials()) || hasPartialSupport(event.getToDownMaterials())) {
            reset(buffer);
            return;
        }

        long now = System.currentTimeMillis();
        double deltaY = distanceVertical(event.getFrom(), event.getTo());
        double deltaXZ = distanceHorizontal(event.getFrom(), event.getTo());

        buffer.put("moveTicks", buffer.getInt("moveTicks") + 1);
        if (deltaY > 0.08D && deltaY <= MAX_STEP_HEIGHT)
            buffer.put("lastNaturalAscendTime", now);

        boolean nearGround = !event.isFromDownBlocksPassable() || !event.isToDownBlocksPassable() ||
                hasGround(event.getFromDownBlocks()) || hasGround(event.getToDownBlocks());
        if (nearGround)
            buffer.put("nearGroundTicks", buffer.getInt("nearGroundTicks") + 1);
        else
            buffer.put("nearGroundTicks", 0);

        int ascensionTicks = buffer.getInt("ascensionTicks");
        if (deltaY > 0.2D) {
            ascensionTicks++;
            buffer.put("lastAscensionTick", buffer.getInt("moveTicks"));
        } else if ((deltaY < 0.2D && deltaY > 0.0D) || cache.history.onEvent.onGround.get(HistoryElement.FROM).towardsTrue) {
            ascensionTicks = 0;
        }
        if (buffer.getInt("moveTicks") - buffer.getInt("lastAscensionTick") > 20)
            ascensionTicks = 0;
        buffer.put("ascensionTicks", ascensionTicks);

        boolean clientGround = cache.history.onEvent.onGround.get(HistoryElement.FROM).towardsTrue ||
                cache.history.onPacket.onGround.get(HistoryElement.FROM).towardsTrue || nearGround;
        boolean recentNaturalAscend = now - buffer.getLong("lastNaturalAscendTime") < 750L;
        boolean terrainBlockRise = clientGround && deltaY > MAX_STEP_HEIGHT && deltaY <= 1.05D &&
                deltaXZ > 0.02D && buffer.getInt("nearGroundTicks") > 0 &&
                (recentNaturalAscend || ascensionTicks > 0);
        boolean terrainBlockDescent = deltaY < -0.35D && deltaY > -1.25D &&
                deltaXZ > 0.035D && buffer.getInt("nearGroundTicks") > 0 && clientGround;

        if (terrainBlockRise || terrainBlockDescent) {
            buffer.put("ascensionTicks", Math.max(0, ascensionTicks - 1));
            decay(buffer, 0.15D);
            return;
        }

        boolean vanillaInvalid = clientGround && deltaY > MAX_STEP_HEIGHT;
        boolean ascensionInvalid = ascensionTicks > 3;
        if (!vanillaInvalid && !ascensionInvalid) {
            decay(buffer, 0.1D);
            return;
        }

        double threshold = 2.5D;
        double violations = buffer.getDouble("violations") + 1.0D;
        buffer.put("violations", violations);
        if (violations <= threshold)
            return;

        Scheduler.runTask(true, () ->
                callViolationEventIfRepeat(player, lacPlayer, event, buffer, Main.getBufferDurationMils() - 1000L));
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

    private boolean hasGround(Set<Block> blocks) {
        for (Block block : blocks)
            if (!isActuallyPassable(block))
                return true;
        return false;
    }

    private boolean hasPartialSupport(Set<Material> materials) {
        for (Material material : materials)
            if (isPartialSupport(material))
                return true;
        return false;
    }

    private boolean isPartialSupport(Material material) {
        if (material == null)
            return false;
        String name = material.name();
        return name.contains("HEAVY_CORE") || name.contains("ENCHANTING_TABLE") ||
                name.contains("END_PORTAL_FRAME") || name.contains("SLAB") ||
                name.contains("STEP") || name.contains("STAIR") || name.contains("BED") ||
                name.contains("TRAPDOOR") || name.contains("HONEY") || name.contains("SKULL") ||
                name.contains("HEAD") || name.contains("FENCE") || name.contains("WALL") ||
                name.contains("SNOW") || name.contains("CARPET") || name.contains("DAYLIGHT") ||
                name.contains("LILY") || name.contains("CAMPFIRE") || name.contains("LANTERN") ||
                name.contains("PUMPKIN") || name.contains("AMETHYST") || name.contains("CANDLE") ||
                name.contains("TURTLE_EGG") || name.contains("CHORUS") || name.contains("END_ROD") ||
                name.contains("DECORATED_POT") || name.contains("FLOWER_POT") || name.contains("CONDUIT") ||
                name.contains("BELL") || name.contains("SWEET_BERRY_BUSH") || name.contains("SHULKER") ||
                name.contains("CAKE") || name.contains("CAULDRON") || name.contains("HOPPER") ||
                name.contains("ANVIL") || name.contains("CHAIN") || name.contains("SEA_PICKLE") ||
                name.contains("POWDER_SNOW") || name.contains("POINTED_DRIPSTONE") ||
                name.contains("DRIPSTONE");
    }

    private boolean hasRiptide(Player player) {
        Material trident = VerUtil.material.get("TRIDENT");
        Enchantment riptide = VerUtil.enchantment.get("RIPTIDE");
        if (trident == null || riptide == null)
            return false;
        ItemStack mainHand = VerPlayer.getItemInMainHand(player);
        ItemStack offHand = VerPlayer.getItemInOffHand(player);
        return hasRiptide(mainHand, trident, riptide) || hasRiptide(offHand, trident, riptide);
    }

    private boolean hasRiptide(ItemStack itemStack, Material trident, Enchantment riptide) {
        return itemStack != null && itemStack.getType() == trident &&
                itemStack.getEnchantmentLevel(riptide) > 0;
    }

    private void decay(Buffer buffer, double amount) {
        buffer.put("violations", Math.max(buffer.getDouble("violations") - amount, 0.0D));
    }

    private void reset(Buffer buffer) {
        buffer.put("violations", 0.0D);
        buffer.put("ascensionTicks", 0);
        buffer.put("nearGroundTicks", 0);
        buffer.put("lastAscensionTick", 0);
        buffer.put("lastNaturalAscendTime", 0L);
    }
}
