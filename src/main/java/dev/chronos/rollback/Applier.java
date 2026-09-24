package dev.chronos.rollback;

import dev.chronos.Chronos;
import dev.chronos.log.Extras;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Applica le operazioni a "budget di tick": al massimo N operazioni per tick, raggruppate per chunk
 * ed eseguite sul thread proprietario del chunk (compatibile con Folia).
 */
public final class Applier {
    public record Snapshot(String world, int x, int y, int z, String data, byte[] extra, boolean extraOnly) { }
    public record Result(List<Snapshot> snapshots, int applied, int failed) { }

    private record Work(String world, int cx, int cz, List<StateOp> ops) { }

    private final Chronos p;

    public Applier(Chronos p) { this.p = p; }

    public CompletableFuture<Result> apply(List<StateOp> ops) {
        CompletableFuture<Result> done = new CompletableFuture<>();
        if (ops.isEmpty()) { done.complete(new Result(List.of(), 0, 0)); return done; }
        int per = Math.max(100, p.cfg.blocksPerTick);

        Map<String, Map<Long, List<StateOp>>> grouped = new LinkedHashMap<>();
        for (StateOp o : ops) {
            long ck = ((long) o.cx() << 32) ^ (o.cz() & 0xffffffffL);
            grouped.computeIfAbsent(o.world, k -> new LinkedHashMap<>()).computeIfAbsent(ck, k -> new ArrayList<>()).add(o);
        }
        Deque<Work> queue = new ArrayDeque<>();
        for (var we : grouped.entrySet()) {
            for (var ce : we.getValue().entrySet()) {
                List<StateOp> l = ce.getValue();
                int cx = (int) (ce.getKey() >> 32), cz = (int) (long) ce.getKey();
                for (int i = 0; i < l.size(); i += per) {
                    queue.add(new Work(we.getKey(), cx, cz, l.subList(i, Math.min(l.size(), i + per))));
                }
            }
        }

        AtomicInteger pending = new AtomicInteger(queue.size());
        AtomicInteger applied = new AtomicInteger(), failed = new AtomicInteger();
        ConcurrentLinkedQueue<Snapshot> snaps = new ConcurrentLinkedQueue<>();
        Set<String> captured = ConcurrentHashMap.newKeySet();
        Runnable finish = () -> {
            if (pending.decrementAndGet() == 0) done.complete(new Result(new ArrayList<>(snaps), applied.get(), failed.get()));
        };

        ScheduledTask[] handle = new ScheduledTask[1];
        handle[0] = p.sched.globalTimer(() -> {
            int budget = per;
            while (budget > 0 && !queue.isEmpty()) {
                Work w = queue.poll();
                budget -= w.ops().size();
                dispatch(w, snaps, captured, applied, failed, finish);
            }
            if (queue.isEmpty() && handle[0] != null) handle[0].cancel();
        }, 1, 1);
        return done;
    }

    private void dispatch(Work w, ConcurrentLinkedQueue<Snapshot> snaps, Set<String> captured,
                          AtomicInteger applied, AtomicInteger failed, Runnable finish) {
        World world = Bukkit.getWorld(w.world());
        if (world == null) { failed.addAndGet(w.ops().size()); finish.run(); return; }
        world.getChunkAtAsync(w.cx(), w.cz()).whenComplete((chunk, err) -> {
            if (err != null) { failed.addAndGet(w.ops().size()); finish.run(); return; }
            p.sched.region(world, w.cx(), w.cz(), () -> {
                try {
                    for (StateOp o : w.ops()) {
                        try {
                            applyOne(world, o, snaps, captured);
                            applied.incrementAndGet();
                        } catch (Exception ex) {
                            failed.incrementAndGet();
                        }
                    }
                } finally {
                    finish.run();
                }
            });
        });
    }

    private void applyOne(World world, StateOp o, ConcurrentLinkedQueue<Snapshot> snaps, Set<String> captured) {
        Block b = world.getBlockAt(o.x, o.y, o.z);
        if (captured.add(o.key())) {
            snaps.add(new Snapshot(o.world, o.x, o.y, o.z, b.getBlockData().getAsString(), Extras.capture(b, true), o.data == null));
        }
        if (o.data != null) {
            BlockData cur = b.getBlockData();
            BlockData bd = Bukkit.createBlockData(o.data);
            if (!cur.equals(bd)) {
                boolean liquid = isLiquid(cur.getMaterial()) || isLiquid(bd.getMaterial());
                b.setBlockData(bd, liquid); // i fluidi richiedono la fisica per ritirarsi
            }
        }
        if (o.extra != null) Extras.apply(b, o.extra);
        if (o.item != null) Extras.delta(b, o.item, o.amount);
    }

    private static boolean isLiquid(Material m) { return m == Material.WATER || m == Material.LAVA; }
}
