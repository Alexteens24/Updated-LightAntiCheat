package me.vekster.lightanticheat.util.hook.plugin;

import me.vekster.lightanticheat.Main;
import me.vekster.lightanticheat.check.CheckName;
import me.vekster.lightanticheat.player.LACPlayer;
import me.vekster.lightanticheat.player.cache.PlayerCache;
import me.vekster.lightanticheat.util.annotation.SecureAsync;
import me.vekster.lightanticheat.util.config.ConfigManager;
import me.vekster.lightanticheat.util.cooldown.CooldownUtil;
import me.vekster.lightanticheat.util.detection.CheckUtil;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.geysermc.floodgate.api.FloodgateApi;

public class FloodgateHook {

    private static PluginManager pluginManager;
    private static final String PLUGIN_NAME = "floodgate";

    public static void loadFloodgateHook() {
        pluginManager = Main.getInstance().getServer().getPluginManager();
    }

    public static boolean isBedrockPlayerWithoutCache(Player player) {
        if (player == null)
            return false;
        if (!ConfigManager.Config.GeyserHook.enabled)
            return false;
        if (ConfigManager.Config.GeyserHook.UUID.enabled &&
                player.getUniqueId().toString().startsWith("000000"))
            return true;
        if (ConfigManager.Config.GeyserHook.Prefix.enabled && player.getName()
                .startsWith(ConfigManager.Config.GeyserHook.Prefix.prefixString))
            return true;

        if (!ConfigManager.Config.GeyserHook.Floodgate.enabled ||
                pluginManager == null || pluginManager.getPlugin(PLUGIN_NAME) == null)
            return false;
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (NoClassDefFoundError | IllegalStateException | NullPointerException e) {
            return false;
        }
    }

    public static boolean isBedrockPlayer(Player player) {
        if (player == null)
            return false;
        LACPlayer lacPlayer = LACPlayer.getLacPlayer(player);
        if (lacPlayer == null || lacPlayer.cooldown == null)
            return isBedrockPlayerWithoutCache(player);
        return CooldownUtil.isBedrockPlayer(lacPlayer.cooldown, player);
    }

    @SecureAsync
    public static boolean isBedrockPlayer(Player player, boolean async) {
        if (player == null)
            return false;
        LACPlayer lacPlayer = LACPlayer.getLacPlayer(player);
        if (lacPlayer == null || lacPlayer.cooldown == null)
            return isBedrockPlayerWithoutCache(player);
        return CooldownUtil.isBedrockPlayer(lacPlayer.cooldown, player, async);
    }

    @SecureAsync
    public static boolean isProbablyPocketEditionPlayer(Player player, boolean async) {
        if (!isBedrockPlayer(player, async))
            return false;

        if (!ConfigManager.Config.GeyserHook.Floodgate.enabled ||
                pluginManager == null || pluginManager.getPlugin(PLUGIN_NAME) == null)
            return true;
        try {
            if (!FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId()))
                return true;
            if (FloodgateApi.getInstance().getPlayer(player.getUniqueId()) == null ||
                    FloodgateApi.getInstance().getPlayer(player.getUniqueId()).getDeviceOs() == null)
                return true;
        } catch (NoClassDefFoundError | IllegalStateException | NullPointerException e) {
            return true;
        }
        String deviceOs;
        try {
            deviceOs = FloodgateApi.getInstance().getPlayer(player.getUniqueId()).getDeviceOs().name();
        } catch (NoClassDefFoundError | IllegalStateException | NullPointerException e) {
            return true;
        }
        if (deviceOs.equals("UNKNOWN") || deviceOs.equals("GOOGLE") || deviceOs.equals("IOS") ||
                deviceOs.equals("AMAZON") || deviceOs.equals("GEARVR") || deviceOs.equals("TVOS") ||
                deviceOs.equals("PS4") || deviceOs.equals("NX") || deviceOs.equals("XBOX") ||
                deviceOs.equals("WINDOWS_PHONE"))
            return true;
        return false;
    }

    public static boolean isProbablyPocketEditionPlayer(Player player) {
        return isProbablyPocketEditionPlayer(player, false);
    }

    @SecureAsync
    public static boolean isCancelledCombat(CheckName checkName, Player player, boolean async) {
        if (checkName != CheckName.KILLAURA_B &&
                checkName != CheckName.REACH_A && checkName != CheckName.REACH_B)
            return false;
        if (!isProbablyPocketEditionPlayer(player, async))
            return false;
        return true;
    }

    @SecureAsync
    public static boolean isCancelledMovement(CheckName checkName, Player player, boolean async) {
        if (checkName == null || checkName.type != CheckName.CheckType.MOVEMENT)
            return false;
        if (!isProbablyPocketEditionPlayer(player, async))
            return false;
        if (isRecentlyUnstableBedrockMovement(player))
            return true;
        if (checkName == CheckName.SPEED_B) {
            for (Block block : CheckUtil.getDownBlocks(player, 0.12))
                if (block.getType().name().endsWith("_STAIRS"))
                    return true;
        }
        if (checkName == CheckName.STEP_A) {
            for (Block block : CheckUtil.getDownBlocks(player, 0.12))
                if (block.getType().name().endsWith("_STAIRS"))
                    return true;
            for (Block block : CheckUtil.getCollisionBlockLayer(player))
                if (block.getType().name().endsWith("_STAIRS") ||
                        block.getRelative(BlockFace.UP).getType().name().endsWith("_STAIRS"))
                    return true;
        }
        return false;
    }

    private static boolean isRecentlyUnstableBedrockMovement(Player player) {
        LACPlayer lacPlayer = LACPlayer.getLacPlayer(player);
        if (lacPlayer == null || lacPlayer.cache == null)
            return true;

        long time = System.currentTimeMillis();
        PlayerCache cache = lacPlayer.cache;
        return time - lacPlayer.joinTime < 5000 ||
                time - cache.lastTeleport < 2500 ||
                time - cache.lastWorldChange < 2500 ||
                time - cache.lastRespawn < 2500 ||
                time - cache.lastInsideVehicle < 1000 ||
                time - cache.lastInWater < 1000;
    }

}
