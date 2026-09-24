package dev.chronos;

import dev.chronos.attribution.Attribution;
import dev.chronos.attribution.IncidentManager;
import dev.chronos.command.ChronosCommand;
import dev.chronos.detect.SuspicionMonitor;
import dev.chronos.gui.GuiListener;
import dev.chronos.listener.*;
import dev.chronos.log.ActionLogger;
import dev.chronos.rollback.*;
import dev.chronos.snapshot.SnapshotService;
import dev.chronos.storage.*;
import dev.chronos.util.Lang;
import dev.chronos.util.Msg;
import dev.chronos.util.Sched;
import dev.chronos.util.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class Chronos extends JavaPlugin {
    public volatile ChronosConfig cfg;
    public volatile Lang lang;
    public UpdateChecker updates;
    public Sched sched;
    public Database db;
    public IdCache ids;
    public LogQueue queue;
    public QueryService queries;
    public IncidentManager incidents;
    public Attribution attribution;
    public ActionLogger actions;
    public Applier applier;
    public RollbackService rollback;
    public UndoStack undo;
    public PreviewService preview;
    public Selections selections;
    public SnapshotService snapshots;
    public SuspicionMonitor monitor;
    public InspectListener inspector;
    public RetentionService retention;
    public ExecutorService dbPool;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cfg = new ChronosConfig(getConfig());
        sched = new Sched(this);
        lang = new Lang(this, cfg.language);
        try {
            ids = new IdCache();
            db = new Database(this);
            db.init();
            dbPool = Executors.newFixedThreadPool(3, r -> {
                Thread t = new Thread(r, "Chronos-DB");
                t.setDaemon(true);
                return t;
            });
            queue = new LogQueue(this);
            queue.start();
            queries = new QueryService(this);
            incidents = new IncidentManager(this, queries.nextIncidentId());
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not initialize the database: disabling plugin.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        attribution = new Attribution();
        actions = new ActionLogger(this);
        applier = new Applier(this);
        undo = new UndoStack(this);
        selections = new Selections(this);
        updates = new UpdateChecker(this);
        rollback = new RollbackService(this);
        preview = new PreviewService(this);
        snapshots = new SnapshotService(this);
        monitor = new SuspicionMonitor(this);
        retention = new RetentionService(this);
        inspector = new InspectListener(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new BlockListener(this), this);
        pm.registerEvents(new EnvironmentListener(this), this);
        pm.registerEvents(new ContainerListener(this), this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(inspector, this);
        pm.registerEvents(new GuiListener(), this);

        PluginCommand pc = getCommand("chronos");
        if (pc != null) {
            ChronosCommand cc = new ChronosCommand(this);
            pc.setExecutor(cc);
            pc.setTabCompleter(cc);
        }

        preview.start();
        snapshots.startPeriodic();
        sched.asyncTimer(attribution::sweep, 30, 30, TimeUnit.SECONDS);
        if (cfg.retentionEnabled) sched.asyncTimer(retention::runDefault, 5, 24 * 60, TimeUnit.MINUTES);

        updates.check();
        getLogger().info("Chronos enabled (" + db.type + ", language: " + cfg.language + ").");
    }

    @Override
    public void onDisable() {
        if (queue == null) return;
        Bukkit.getGlobalRegionScheduler().cancelTasks(this);
        Bukkit.getAsyncScheduler().cancelTasks(this);
        queue.stop();
        if (dbPool != null) dbPool.shutdown();
        if (db != null) db.close();
    }

    /** Avvisa lo staff (permesso chronos.alerts), escluso il mittente. */
    public void notifyStaff(CommandSender except, String mini) {
        getLogger().info(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(Msg.c(mini)));
        for (Player pl : getServer().getOnlinePlayers()) {
            if (pl == except || !pl.hasPermission("chronos.alerts")) continue;
            sched.entity(pl, () -> Msg.send(pl, mini));
        }
    }

    /** Invia un messaggio dal thread giusto (giocatore = suo scheduler, altrimenti globale). */
    public void tell(CommandSender s, String mini) {
        Runnable r = () -> Msg.send(s, mini);
        if (s instanceof Player pl) sched.entity(pl, r); else sched.global(r);
    }
}
