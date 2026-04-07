package com.fren_gor.lightInjector;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public abstract class LightInjector {

    private static final int VERSION;
    private static final String COMPLETE_VERSION;
    private static final String CRAFTBUKKIT_PACKAGE;
    private static final Class<?> NETWORK_MANAGER_CLASS;
    private static final Field GET_PLAYER_CONNECTION;
    private static final Field GET_NETWORK_MANAGER;
    private static final Field GET_CHANNEL;
    private static final Method GET_PLAYER_HANDLE;
    private static int ID;

    private final Plugin plugin;
    private final String identifier;
    private final EventListener listener;
    private final AtomicBoolean closed;
    private final Set<Channel> injectedChannels;

    static {
        VERSION = Integer.parseInt(Bukkit.getBukkitVersion().split("-")[0].split("\\.")[1]);
        COMPLETE_VERSION = VERSION >= 17 ? null : Bukkit.getServer().getClass().getName().split("\\.")[3];
        CRAFTBUKKIT_PACKAGE = Bukkit.getServer().getClass().getPackage().getName();

        Class<?> entityPlayerClass = getNmsClass("EntityPlayer", "ServerPlayer", "server.level");
        Class<?> playerConnectionClass = getNmsClass("PlayerConnection", "ServerGamePacketListenerImpl", "server.network");
        NETWORK_MANAGER_CLASS = getNmsClass("NetworkManager", "Connection", "network");

        GET_PLAYER_CONNECTION = getField(entityPlayerClass, playerConnectionClass, 1);
        GET_NETWORK_MANAGER = getField(playerConnectionClass, NETWORK_MANAGER_CLASS, 1);
        GET_CHANNEL = getField(NETWORK_MANAGER_CLASS, Channel.class, 1);
        GET_PLAYER_HANDLE = getMethod(getCraftBukkitClass("entity.CraftPlayer"), "getHandle");
    }

    public LightInjector(Plugin plugin) {
        if (!Bukkit.isPrimaryThread())
            throw new IllegalStateException("LightInjector must be constructed on the main thread.");

        this.plugin = Objects.requireNonNull(plugin, "Plugin is null.");
        if (!plugin.isEnabled())
            throw new IllegalArgumentException("Plugin " + plugin.getName() + " is not enabled");

        this.identifier = Objects.requireNonNull(getIdentifier(), "getIdentifier() returned a null value.") + "-" + ID++;
        this.listener = new EventListener();
        this.closed = new AtomicBoolean(false);
        this.injectedChannels = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

        Bukkit.getPluginManager().registerEvents(listener, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                injectPlayer(player);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.SEVERE, "[LightInjector] An error occurred while injecting a player:", exception);
            }
        }
    }

    protected abstract @Nullable Object onPacketReceiveAsync(@Nullable Player sender, @NotNull Channel channel, @NotNull Object nmsPacket);

    protected abstract @Nullable Object onPacketSendAsync(@Nullable Player receiver, @NotNull Channel channel, @NotNull Object nmsPacket);

    public final void sendPacket(Player player, Object packet) {
        Objects.requireNonNull(player, "Player is null.");
        try {
            sendPacket(getChannel(player), packet);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("[LightInjector] Could not resolve the player's channel.", exception);
        }
    }

    public final void sendPacket(Channel channel, Object packet) {
        Objects.requireNonNull(channel, "Channel is null.");
        Objects.requireNonNull(packet, "Packet is null.");
        channel.pipeline().writeAndFlush(packet);
    }

    public final void receivePacket(Player player, Object packet) {
        Objects.requireNonNull(player, "Player is null.");
        try {
            receivePacket(getChannel(player), packet);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("[LightInjector] Could not resolve the player's channel.", exception);
        }
    }

    public final void receivePacket(Channel channel, Object packet) {
        Objects.requireNonNull(channel, "Channel is null.");
        Objects.requireNonNull(packet, "Packet is null.");
        ChannelHandlerContext context = channel.pipeline().context("encoder");
        Objects.requireNonNull(context, "Channel is not a player channel").fireChannelRead(packet);
    }

    protected String getIdentifier() {
        return "light-injector-" + plugin.getName();
    }

    public final void close() {
        if (closed.getAndSet(true))
            return;

        listener.unregister();
        List<Channel> channels;
        synchronized (injectedChannels) {
            channels = new ArrayList<>(injectedChannels);
        }
        for (Channel channel : channels) {
            Runnable remove = () -> {
                ChannelPipeline pipeline = channel.pipeline();
                if (pipeline.get(identifier) != null) {
                    pipeline.remove(identifier);
                }
                injectedChannels.remove(channel);
            };
            if (channel.eventLoop().inEventLoop()) {
                remove.run();
            } else {
                channel.eventLoop().submit(remove);
            }
        }
    }

    public final boolean isClosed() {
        return closed.get();
    }

    private void injectPlayer(Player player) throws ReflectiveOperationException {
        Channel channel = getChannel(player);
        ChannelHandler handler = channel.pipeline().get(identifier);
        if (handler instanceof PacketHandler) {
            ((PacketHandler) handler).player = player;
            return;
        }

        // The player only exists once Bukkit finishes the join pipeline.
        PacketHandler packetHandler = new PacketHandler();
        packetHandler.player = player;
        injectChannel(channel, packetHandler);
    }

    private void injectChannel(Channel channel, PacketHandler packetHandler) {
        Runnable inject = () -> {
            if (isClosed())
                return;

            ChannelPipeline pipeline = channel.pipeline();
            ChannelHandler current = pipeline.get(identifier);
            if (current instanceof PacketHandler) {
                ((PacketHandler) current).player = packetHandler.player;
                return;
            }
            if (!injectedChannels.add(channel))
                return;

            try {
                pipeline.addBefore("packet_handler", identifier, packetHandler);
            } catch (IllegalArgumentException exception) {
                injectedChannels.remove(channel);
                plugin.getLogger().log(Level.SEVERE,
                        "[LightInjector] Could not inject a player channel before packet_handler.", exception);
            }
        };

        if (channel.eventLoop().inEventLoop()) {
            inject.run();
        } else {
            channel.eventLoop().submit(inject);
        }
    }

    private Channel getChannel(Player player) throws ReflectiveOperationException {
        return getChannel(getNetworkManager(player));
    }

    private Object getNetworkManager(Player player) throws ReflectiveOperationException {
        Object entityPlayer = GET_PLAYER_HANDLE.invoke(player);
        Object playerConnection = GET_PLAYER_CONNECTION.get(entityPlayer);
        return GET_NETWORK_MANAGER.get(playerConnection);
    }

    private Channel getChannel(Object networkManager) throws ReflectiveOperationException {
        return (Channel) GET_CHANNEL.get(networkManager);
    }

    private final class EventListener implements Listener {

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerJoin(PlayerJoinEvent event) {
            if (isClosed())
                return;
            try {
                injectPlayer(event.getPlayer());
            } catch (Exception exception) {
                plugin.getLogger().log(Level.SEVERE, "[LightInjector] An error occurred while injecting a player:", exception);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPluginDisable(PluginDisableEvent event) {
            if (plugin.equals(event.getPlugin())) {
                close();
            }
        }

        private void unregister() {
            HandlerList.unregisterAll(this);
        }
    }

    private final class PacketHandler extends ChannelDuplexHandler {
        private volatile Player player;

        @Override
        public void channelUnregistered(ChannelHandlerContext context) throws Exception {
            injectedChannels.remove(context.channel());
            super.channelUnregistered(context);
        }

        @Override
        public void write(ChannelHandlerContext context, Object packet, ChannelPromise promise) throws Exception {
            Object handledPacket;
            try {
                handledPacket = onPacketSendAsync(player, context.channel(), packet);
            } catch (OutOfMemoryError error) {
                throw error;
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.SEVERE, "[LightInjector] An error occurred while calling onPacketSendAsync:", throwable);
                super.write(context, packet, promise);
                return;
            }

            if (handledPacket != null) {
                super.write(context, handledPacket, promise);
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext context, Object packet) throws Exception {
            Object handledPacket;
            try {
                handledPacket = onPacketReceiveAsync(player, context.channel(), packet);
            } catch (OutOfMemoryError error) {
                throw error;
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.SEVERE, "[LightInjector] An error occurred while calling onPacketReceiveAsync:", throwable);
                super.channelRead(context, packet);
                return;
            }

            if (handledPacket != null) {
                super.channelRead(context, handledPacket);
            }
        }
    }

    private static Class<?> getCraftBukkitClass(String className) {
        try {
            return Class.forName(CRAFTBUKKIT_PACKAGE + "." + className);
        } catch (ClassNotFoundException exception) {
            throw new RuntimeException("[LightInjector] Can not find class! (" + CRAFTBUKKIT_PACKAGE + "." + className + ")", exception);
        }
    }

    private static Class<?> getNmsClass(String legacyClassName, String modernClassName, String modernPackage) {
        String className;
        if (VERSION >= 17) {
            className = "net.minecraft." + modernPackage + "." + modernClassName;
        } else {
            className = "net.minecraft.server." + COMPLETE_VERSION + "." + legacyClassName;
        }

        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new RuntimeException("[LightInjector] Can not find class! (" + className + ")", exception);
        }
    }

    private static Field getField(Class<?> clazz, Class<?> fieldClass, int ordinal) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null) {
            Collections.addAll(fields, current.getDeclaredFields());
            current = current.getSuperclass();
        }

        int remaining = ordinal;
        for (Field field : fields) {
            if (!fieldClass.equals(field.getType()))
                continue;
            remaining--;
            if (remaining > 0)
                continue;
            field.setAccessible(true);
            return field;
        }

        remaining = ordinal;
        for (Field field : fields) {
            if (!fieldClass.isAssignableFrom(field.getType()))
                continue;
            remaining--;
            if (remaining > 0)
                continue;
            field.setAccessible(true);
            return field;
        }

        throw new RuntimeException("[LightInjector] Can not find field! (" + ordinal + getOrdinal(ordinal) +
                " returning " + fieldClass.getName() + " in " + clazz.getName() + ")");
    }

    private static Method getMethod(Class<?> clazz, String methodName) {
        try {
            Method method = clazz.getMethod(methodName);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("[LightInjector] Can not find method! (" + clazz.getName() + "." + methodName + ")", exception);
        }
    }

    private static String getOrdinal(int value) {
        switch (value) {
            case 1:
                return "st";
            case 2:
                return "nd";
            case 3:
                return "rd";
            default:
                return "th";
        }
    }

}
