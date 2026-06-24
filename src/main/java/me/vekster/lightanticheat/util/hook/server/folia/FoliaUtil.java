package me.vekster.lightanticheat.util.hook.server.folia;

import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.util.FoliaLibOptions;
import me.vekster.lightanticheat.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public class FoliaUtil {

    private static boolean folia;
    private static FoliaLib foliaLib;
    private static Method isOwnedByCurrentRegionLocation;

    public static void loadFoliaUtil() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }

        if (isFolia()) {
            FoliaLibOptions options = new FoliaLibOptions();
            options.disableNotifications();
            foliaLib = new FoliaLib(Main.getInstance(), options);
            try {
                isOwnedByCurrentRegionLocation = Bukkit.class.getMethod("isOwnedByCurrentRegion", Location.class);
            } catch (NoSuchMethodException ignored) {
                isOwnedByCurrentRegionLocation = null;
            }
        }
    }

    public static void shutdown() {
        if (foliaLib == null)
            return;
        foliaLib.getScheduler().cancelAllTasks();
        foliaLib = null;
    }

    public static boolean isFolia() {
        return folia;
    }

    public static boolean canAccessLocation(Location location) {
        if (!isFolia() || location == null)
            return true;
        if (isOwnedByCurrentRegionLocation == null)
            return false;
        try {
            return (boolean) isOwnedByCurrentRegionLocation.invoke(null, location);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public static void runTask(Runnable runnable) {
        foliaLib.getScheduler().runNextTick(wrappedTask -> runnable.run());
    }

    public static void runTask(Entity entity, Runnable runnable) {
        foliaLib.getScheduler().runAtEntity(entity, wrappedTask -> runnable.run());
    }

    public static void runTaskAsynchronously(Runnable runnable) {
        foliaLib.getScheduler().runAsync(wrappedTask -> runnable.run());
    }


    public static void runTaskLater(Runnable runnable, long delayInTicks) {
        foliaLib.getScheduler().runLater(runnable, delayInTicks);
    }

    public static void runTaskLater(Entity entity, Runnable runnable, long delayInTicks) {
        foliaLib.getScheduler().runAtEntityLater(entity, runnable, delayInTicks);
    }

    public static void runTaskLaterAsynchronously(Runnable runnable, long delayInTicks) {
        foliaLib.getScheduler().runLaterAsync(runnable, delayInTicks);
    }

    public static void runTaskTimer(Runnable task, long delayInTicks, long periodInTicks) {
        foliaLib.getScheduler().runTimer(task, delayInTicks, periodInTicks);
    }

    public static void runTaskTimer(Entity entity, Runnable task, long delayInTicks, long periodInTicks) {
        foliaLib.getScheduler().runAtEntityTimer(entity, task, delayInTicks, periodInTicks);
    }

    public static void runTaskTimerAsynchronously(Runnable task, long delayInTicks, long periodInTicks) {
        foliaLib.getScheduler().runTimerAsync(task, delayInTicks, periodInTicks);
    }

    public static void teleportPlayer(Player player, Location location) {
        if (!isFolia()) player.teleport(location);
        else foliaLib.getScheduler().teleportAsync(player, location);
    }

}
