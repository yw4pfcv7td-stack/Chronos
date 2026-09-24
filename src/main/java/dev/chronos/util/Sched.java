package dev.chronos.util;

import dev.chronos.Chronos;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.concurrent.TimeUnit;

/** Wrapper around the Paper/Folia schedulers di Paper/Folia (funzionano anche su Paper "normale"). */
public final class Sched {
    private final Chronos plugin;

    public Sched(Chronos plugin) { this.plugin = plugin; }

    public void global(Runnable r) { Bukkit.getGlobalRegionScheduler().execute(plugin, r); }

    public void async(Runnable r) { Bukkit.getAsyncScheduler().runNow(plugin, t -> r.run()); }

    public void entity(Entity e, Runnable r) { e.getScheduler().run(plugin, t -> r.run(), null); }

    public void region(World w, int cx, int cz, Runnable r) { Bukkit.getRegionScheduler().execute(plugin, w, cx, cz, r); }

    public ScheduledTask globalTimer(Runnable r, long delayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> r.run(), Math.max(1, delayTicks), Math.max(1, periodTicks));
    }

    public ScheduledTask asyncTimer(Runnable r, long delay, long period, TimeUnit unit) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> r.run(), delay, period, unit);
    }
}
