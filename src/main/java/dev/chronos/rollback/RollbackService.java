package dev.chronos.rollback;

import dev.chronos.Chronos;
import dev.chronos.model.ActionRow;
import dev.chronos.model.ActionType;
import dev.chronos.storage.QuerySpec;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class RollbackService {
    public static final UUID CONSOLE = new UUID(0, 0);

    public record Plan(boolean restore, List<StateOp> ops, long[] ids, int blocks, int containers, int items,
                       int rows, boolean truncated) { }

    private final Chronos p;

    public RollbackService(Chronos p) { this.p = p; }

    public static UUID owner(CommandSender s) { return s instanceof Player pl ? pl.getUniqueId() : CONSOLE; }

    /** Calcola il piano (thread asincrono). Rollback: stato del piu' vecchio evento; restore: stato del piu' recente. */
    public Plan plan(QuerySpec in, boolean restore) throws SQLException {
        QuerySpec s = in.copy();
        s.rolledBack = restore ? 1 : 0;
        s.types.clear();
        if (in.types.isEmpty()) {
            s.types.add(ActionType.BLOCK_BREAK); s.types.add(ActionType.BLOCK_PLACE);
            s.types.add(ActionType.CONTAINER_ADD); s.types.add(ActionType.CONTAINER_REMOVE);
        } else {
            s.types.addAll(in.types);
            s.types.remove(ActionType.KILL);
        }
        int max = p.cfg.maxRollbackRows;
        List<ActionRow> rows = p.queries.rows(s, true, restore, max, 0);
        boolean truncated = rows.size() >= max;

        Map<String, StateOp> blockOps = new LinkedHashMap<>();
        List<ActionRow> containerRows = new ArrayList<>();
        long[] ids = new long[rows.size()];
        int n = 0;
        for (ActionRow r : rows) {
            ids[n++] = r.id();
            if (r.type().isBlock()) {
                String target = restore ? r.newData() : r.oldData();
                if (target == null) continue;
                byte[] ex = (!restore && r.type() == ActionType.BLOCK_BREAK) ? r.extra() : null;
                StateOp op = StateOp.state(r.world(), r.x(), r.y(), r.z(), target, ex);
                blockOps.put(op.key(), op); // l'ultimo inserito vince: rollback = piu' vecchio, restore = piu' recente
            } else if (r.type().isContainer()) {
                containerRows.add(r);
            }
        }
        List<StateOp> ops = new ArrayList<>(blockOps.values());
        int blocks = ops.size();
        Set<String> contLocs = new HashSet<>();
        int items = 0;
        for (ActionRow r : containerRows) {
            StateOp probe = StateOp.delta(r.world(), r.x(), r.y(), r.z(), null, 0);
            if (blockOps.containsKey(probe.key()) || r.extra() == null) continue; // il blocco viene gia' ripristinato per intero
            boolean add = r.type() == ActionType.CONTAINER_ADD;
            int d = (add ^ restore) ? -r.amount() : r.amount();
            ops.add(StateOp.delta(r.world(), r.x(), r.y(), r.z(), r.extra(), d));
            contLocs.add(probe.key());
            items += r.amount();
        }
        return new Plan(restore, ops, ids, blocks, contLocs.size(), items, rows.size(), truncated);
    }

    /** Punto d'ingresso da comando/GUI: pianifica, poi anteprima o applicazione diretta. */
    public void begin(CommandSender sender, QuerySpec spec, boolean restore) {
        if (!spec.hasScope() || (spec.since < 0 && spec.incident < 0)) {
            p.tell(sender, p.lang.t("rb.scope"));
            return;
        }
        p.tell(sender, p.lang.t("rb.calculating"));
        CompletableFuture.supplyAsync(() -> {
            try {
                p.queue.awaitDrain(3000);
                return plan(spec, restore);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, p.dbPool).whenComplete((plan, err) -> {
            if (err != null) {
                p.getLogger().log(Level.SEVERE, "Error while computing rollback", err);
                p.tell(sender, p.lang.t("error.db"));
                return;
            }
            Runnable r = () -> deliver(sender, spec, plan);
            if (sender instanceof Player pl) p.sched.entity(pl, r); else p.sched.global(r);
        });
    }

    private void deliver(CommandSender sender, QuerySpec spec, Plan plan) {
        if (plan.ops().isEmpty()) {
            say(sender, p.lang.t(plan.restore() ? "rb.none_restore" : "rb.none_rollback"));
            return;
        }
        if (sender instanceof Player pl && p.cfg.previewEnabled && !spec.now) {
            p.preview.show(pl, plan, spec);
        } else {
            applyNow(sender, plan);
        }
    }

    private void say(CommandSender s, String m) { dev.chronos.util.Msg.send(s, m); }

    public void applyNow(CommandSender sender, Plan plan) {
        UUID owner = owner(sender);
        say(sender, p.lang.t("rb.applying", "count", plan.ops().size(), "perTick", p.cfg.blocksPerTick));
        if (p.cfg.notifyRollback) p.notifyStaff(sender, p.lang.t("rb.notify", "player", sender.getName(), "kind", p.lang.raw(plan.restore() ? "word.restore" : "word.rollback"), "count", plan.rows()));
        long t0 = System.currentTimeMillis();
        int after = plan.restore() ? 0 : 1, undoFlag = plan.restore() ? 1 : 0;
        p.applier.apply(plan.ops()).thenAccept(res -> {
            String label = p.lang.t(plan.restore() ? "rb.label_restore" : "rb.label_rollback", "rows", plan.rows());
            p.undo.pushNew(owner, new UndoStack.Batch(res.snapshots(), plan.ids(), undoFlag, after, label));
            markFlags(plan.ids(), after);
            p.tell(sender, p.lang.t("rb.done", "ms", System.currentTimeMillis() - t0, "blocks", plan.blocks(), "items", plan.items(),
                    "containers", plan.containers())
                    + (res.failed() > 0 ? p.lang.t("rb.done_failed", "n", res.failed()) : "") + p.lang.t("rb.done_hint"));
        });
    }

    private static List<StateOp> toOps(List<Applier.Snapshot> snaps) {
        List<StateOp> ops = new ArrayList<>();
        for (Applier.Snapshot s : snaps) {
            if (s.extraOnly()) {
                if (s.extra() != null) ops.add(StateOp.extraOnly(s.world(), s.x(), s.y(), s.z(), s.extra()));
            } else {
                ops.add(StateOp.state(s.world(), s.x(), s.y(), s.z(), s.data(), s.extra()));
            }
        }
        return ops;
    }

    public void undo(CommandSender sender) {
        UUID o = owner(sender);
        UndoStack.Batch b = p.undo.popUndo(o);
        if (b == null) { say(sender, p.lang.t("undo.none")); return; }
        say(sender, p.lang.t("undo.start", "label", b.label()));
        p.applier.apply(toOps(b.snaps())).thenAccept(res -> {
            p.undo.pushRedo(o, new UndoStack.Batch(res.snapshots(), b.ids(), b.undoFlag(), b.redoFlag(), b.label()));
            markFlags(b.ids(), b.undoFlag());
            p.tell(sender, p.lang.t("undo.done", "undo", p.undo.undoSize(o), "redo", p.undo.redoSize(o)));
        });
    }

    public void redo(CommandSender sender) {
        UUID o = owner(sender);
        UndoStack.Batch b = p.undo.popRedo(o);
        if (b == null) { say(sender, p.lang.t("redo.none")); return; }
        say(sender, p.lang.t("redo.start", "label", b.label()));
        p.applier.apply(toOps(b.snaps())).thenAccept(res -> {
            p.undo.pushUndo(o, new UndoStack.Batch(res.snapshots(), b.ids(), b.undoFlag(), b.redoFlag(), b.label()));
            markFlags(b.ids(), b.redoFlag());
            p.tell(sender, p.lang.t("redo.done"));
        });
    }

    /** Aggiorna il flag rolled_back delle righe coinvolte. */
    private void markFlags(long[] ids, int flag) {
        p.dbPool.execute(() -> {
            try (Connection c = p.db.connection()) {
                c.setAutoCommit(false);
                for (int i = 0; i < ids.length; i += 900) {
                    int end = Math.min(ids.length, i + 900);
                    StringBuilder sb = new StringBuilder("UPDATE chronos_action SET rolled_back = ? WHERE id IN (");
                    for (int k = i; k < end; k++) sb.append(k > i ? ",?" : "?");
                    sb.append(')');
                    try (PreparedStatement ps = c.prepareStatement(sb.toString())) {
                        ps.setInt(1, flag);
                        for (int k = i; k < end; k++) ps.setLong(2 + k - i, ids[k]);
                        ps.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                p.getLogger().log(Level.SEVERE, "Error updating rollback flags", e);
            }
        });
    }
}
