package dev.chronos.rollback;

import dev.chronos.Chronos;
import dev.chronos.util.Msg;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Anteprima "fantasma" del rollback: i blocchi cambiano solo per chi lancia il comando. */
public final class PreviewService {
    private record Pending(RollbackService.Plan plan, long expires, List<StateOp> ghosts, ScheduledTask[] sender) { }

    private final Chronos p;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public PreviewService(Chronos p) { this.p = p; }

    public void start() {
        p.sched.globalTimer(() -> {
            long now = System.currentTimeMillis();
            for (var e : pending.entrySet()) {
                if (e.getValue().expires() < now && pending.remove(e.getKey(), e.getValue())) {
                    Player pl = Bukkit.getPlayer(e.getKey());
                    if (pl != null) {
                        p.sched.entity(pl, () -> { revert(pl, e.getValue()); Msg.send(pl, p.lang.t("preview.expired")); });
                    }
                }
            }
        }, 20, 20);
    }

    public void show(Player pl, RollbackService.Plan plan, dev.chronos.storage.QuerySpec spec) {
        cancel(pl, true);
        List<StateOp> ghosts = new ArrayList<>();
        boolean visual = plan.blocks() <= p.cfg.maxPreviewBlocks;
        if (visual) for (StateOp o : plan.ops()) if (o.data != null && o.world.equals(pl.getWorld().getName())) ghosts.add(o);
        Pending pd = new Pending(plan, System.currentTimeMillis() + p.cfg.previewSeconds * 1000L, ghosts, new ScheduledTask[1]);
        pending.put(pl.getUniqueId(), pd);
        sendGhosts(pl, pd);

        Msg.send(pl, p.lang.t(plan.restore() ? "preview.summary_restore" : "preview.summary_rollback",
                "blocks", plan.blocks(), "items", plan.items(), "containers", plan.containers(), "rows", plan.rows())
                + (plan.truncated() ? p.lang.t("preview.truncated") : "") + (visual ? "" : p.lang.t("preview.novisual")));
        Msg.raw(pl, p.lang.t("preview.buttons", "seconds", p.cfg.previewSeconds));
    }

    private void sendGhosts(Player pl, Pending pd) {
        if (pd.ghosts().isEmpty()) return;
        Iterator<StateOp> it = pd.ghosts().iterator();
        Map<String, BlockData> cache = new HashMap<>();
        World w = pl.getWorld();
        pd.sender()[0] = pl.getScheduler().runAtFixedRate(p, t -> {
            int n = 0;
            while (it.hasNext() && n++ < 1500) {
                StateOp o = it.next();
                BlockData bd = cache.computeIfAbsent(o.data, Bukkit::createBlockData);
                pl.sendBlockChange(new Location(w, o.x, o.y, o.z), bd);
            }
            if (!it.hasNext()) t.cancel();
        }, null, 1, 1);
    }

    /** Ripristina la vista reale dei blocchi (lettura sul thread del chunk). */
    private void revert(Player pl, Pending pd) {
        if (pd.sender()[0] != null) pd.sender()[0].cancel();
        World w = pl.getWorld();
        Map<Long, List<StateOp>> byChunk = new HashMap<>();
        for (StateOp o : pd.ghosts()) byChunk.computeIfAbsent(((long) o.cx() << 32) ^ (o.cz() & 0xffffffffL), k -> new ArrayList<>()).add(o);
        for (var e : byChunk.entrySet()) {
            int cx = (int) (e.getKey() >> 32), cz = (int) (long) e.getKey();
            p.sched.region(w, cx, cz, () -> {
                for (StateOp o : e.getValue()) {
                    pl.sendBlockChange(new Location(w, o.x, o.y, o.z), w.getBlockAt(o.x, o.y, o.z).getBlockData());
                }
            });
        }
    }

    public void cancel(Player pl, boolean silent) {
        Pending pd = pending.remove(pl.getUniqueId());
        if (pd == null) { if (!silent) Msg.send(pl, p.lang.t("preview.none")); return; }
        revert(pl, pd);
        if (!silent) Msg.send(pl, p.lang.t("preview.cancelled"));
    }

    public void confirm(Player pl) {
        Pending pd = pending.remove(pl.getUniqueId());
        if (pd == null) { Msg.send(pl, p.lang.t("preview.none_expired")); return; }
        revert(pl, pd);
        p.rollback.applyNow(pl, pd.plan());
    }
}
