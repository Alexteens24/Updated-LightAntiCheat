package me.vekster.lightanticheat.event;

import com.fren_gor.lightInjector.LightInjector;
import io.netty.channel.Channel;
import me.vekster.lightanticheat.Main;
import me.vekster.lightanticheat.event.packetrecive.LACAsyncPacketReceiveEvent;
import me.vekster.lightanticheat.event.packetrecive.packettype.PacketType;
import me.vekster.lightanticheat.event.playerattack.LACAsyncPlayerAttackEvent;
import me.vekster.lightanticheat.event.playerattack.LACPlayerAttackEvent;
import me.vekster.lightanticheat.event.playerbreakblock.LACAsyncPlayerBreakBlockEvent;
import me.vekster.lightanticheat.event.playerbreakblock.LACPlayerBreakBlockEvent;
import me.vekster.lightanticheat.event.playermove.LACAsyncPlayerMoveEvent;
import me.vekster.lightanticheat.event.playermove.LACPlayerMoveEvent;
import me.vekster.lightanticheat.event.playerplaceblock.LACAsyncPlayerPlaceBlockEvent;
import me.vekster.lightanticheat.event.playerplaceblock.LACPlayerPlaceBlockEvent;
import me.vekster.lightanticheat.player.LACPlayer;
import me.vekster.lightanticheat.player.LACPlayerListener;
import me.vekster.lightanticheat.util.config.ConfigManager;
import me.vekster.lightanticheat.util.detection.CheckUtil;
import me.vekster.lightanticheat.util.hook.server.folia.FoliaUtil;
import me.vekster.lightanticheat.util.scheduler.Scheduler;
import me.vekster.lightanticheat.version.identifier.LACVersion;
import me.vekster.lightanticheat.version.identifier.VerIdentifier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LACEventCaller extends LightInjector implements Listener {

    private static final PluginManager PLUGIN_MANAGER;

    static {
        PLUGIN_MANAGER = Bukkit.getServer().getPluginManager();
    }

    public LACEventCaller() {
        super(Main.getInstance());
    }

    private static boolean isPlayerReady(Player player, LACPlayer lacPlayer) {
        return player != null && lacPlayer != null && player.isOnline() && lacPlayer.leaveTime == 0L &&
                lacPlayer.cache != null && lacPlayer.cooldown != null && lacPlayer.violations != null;
    }

    private static void callAsyncEvent(Player player, LACPlayer lacPlayer, Event event) {
        if (FoliaUtil.isFolia()) {
            Scheduler.entityThread(player, () -> callEventIfReady(player, lacPlayer, event));
            return;
        }
        Scheduler.runTaskAsynchronously(false, () -> {
            callEventIfReady(player, lacPlayer, event);
        });
    }

    private static void callAsyncEventFromEntityThread(Player player, LACPlayer lacPlayer, Event event) {
        if (FoliaUtil.isFolia()) {
            callEventIfReady(player, lacPlayer, event);
            return;
        }
        callAsyncEvent(player, lacPlayer, event);
    }

    private static void callEventIfReady(Player player, LACPlayer lacPlayer, Event event) {
        if (!isPlayerReady(player, lacPlayer))
            return;
        PLUGIN_MANAGER.callEvent(event);
    }

    public static void callMovementEvents(PlayerMoveEvent event) {
        if (CheckUtil.isExternalNPC(event))
            return;
        if (event.getTo() == null)
            return;
        Player player = event.getPlayer();
        LACPlayer lacPlayer = LACPlayer.getLacPlayer(player);
        if (!isPlayerReady(player, lacPlayer))
            return;
        LACPlayerMoveEvent lacPlayerMoveEvent = new LACPlayerMoveEvent(event, player, lacPlayer, event.getFrom(), event.getTo());
        Scheduler.entityThread(player, () -> {
            if (!FoliaUtil.isStable(player))
                return;
            PLUGIN_MANAGER.callEvent(lacPlayerMoveEvent);
            callAsyncEventFromEntityThread(player, lacPlayer, new LACAsyncPlayerMoveEvent(lacPlayerMoveEvent));
        });
    }

    public static void callEntityDamageEvent(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player))
            return;
        Player player = (Player) event.getDamager();
        if (CheckUtil.isExternalNPC(player))
            return;
        if (CheckUtil.isExternalNPC(event.getEntity()))
            return;
        LACPlayer lacPlayer = LACPlayer.getLacPlayer(player);
        if (!isPlayerReady(player, lacPlayer))
            return;
        Scheduler.entityThread(player, () -> {
            if (!FoliaUtil.isStable(player))
                return;
            PLUGIN_MANAGER.callEvent(new LACPlayerAttackEvent(event, player, lacPlayer, event.getEntity()));
            callAsyncEventFromEntityThread(player, lacPlayer, new LACAsyncPlayerAttackEvent(player, lacPlayer, event.getEntity().getEntityId()));
        });
    }

    public static void callBlockPlaceEvents(BlockPlaceEvent event) {
        if (CheckUtil.isExternalNPC(event.getPlayer()))
            return;
        Player player = event.getPlayer();
        LACPlayer lacPlayer = LACPlayer.getLacPlayer(player);
        if (!isPlayerReady(player, lacPlayer))
            return;
        LACPlayerPlaceBlockEvent lacPlayerPlaceBlockEvent = new LACPlayerPlaceBlockEvent(event, player, lacPlayer,
                event.getBlock(), event.getBlockAgainst(), event.getBlockReplacedState());
        Scheduler.entityThread(player, () -> {
            if (!FoliaUtil.isStable(player))
                return;
            PLUGIN_MANAGER.callEvent(lacPlayerPlaceBlockEvent);
            callAsyncEventFromEntityThread(player, lacPlayer, new LACAsyncPlayerPlaceBlockEvent(lacPlayerPlaceBlockEvent));
        });
    }

    public static void callBlockBreakEvents(BlockBreakEvent event) {
        if (CheckUtil.isExternalNPC(event.getPlayer()))
            return;
        Player player = event.getPlayer();
        LACPlayer lacPlayer = LACPlayer.getLacPlayer(player);
        if (!isPlayerReady(player, lacPlayer))
            return;
        LACPlayerBreakBlockEvent lacPlayerBreakBlockEvent = new LACPlayerBreakBlockEvent(event, player, lacPlayer, event.getBlock());
        Scheduler.entityThread(player, () -> {
            if (!FoliaUtil.isStable(player))
                return;
            PLUGIN_MANAGER.callEvent(lacPlayerBreakBlockEvent);
            callAsyncEventFromEntityThread(player, lacPlayer, new LACAsyncPlayerBreakBlockEvent(lacPlayerBreakBlockEvent));
        });
    }

    @Override
    protected @Nullable Object onPacketReceiveAsync(@Nullable Player sender, @NotNull Channel channel, @NotNull Object nmsPacket) {
        if (isClosed()) return nmsPacket;
        Main instance = Main.getInstance();
        if (instance == null || !instance.isEnabled()) return nmsPacket;
        if (!ConfigManager.Config.enabled) return nmsPacket;
        if (sender == null) return nmsPacket;
        LACPlayer lacPlayer = LACPlayerListener.getAsyncPlayers().getOrDefault(sender.getUniqueId(), null);
        if (!isPlayerReady(sender, lacPlayer))
            return nmsPacket;
        LACAsyncPacketReceiveEvent event = new LACAsyncPacketReceiveEvent(sender, lacPlayer, nmsPacket);
        if (event.getPacketType() == PacketType.USE_ENTITY && VerIdentifier.getVersion().isNewerThan(LACVersion.V1_8)) {
            callAsyncEvent(event.getPlayer(), event.getLacPlayer(),
                    new LACAsyncPlayerAttackEvent(event.getPlayer(), event.getLacPlayer(), event.getEntityId()));
        }
        callAsyncEvent(event.getPlayer(), event.getLacPlayer(), event);
        return nmsPacket;
    }

    @Override
    protected @Nullable Object onPacketSendAsync(@Nullable Player receiver, @NotNull Channel channel, @NotNull Object nmsPacket) {
        return nmsPacket;
    }

}
