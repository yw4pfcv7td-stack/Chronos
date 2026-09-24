package dev.chronos.rollback;

import dev.chronos.Chronos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Pile di undo/redo per amministratore (piu' livelli). */
public final class UndoStack {
    public record Batch(List<Applier.Snapshot> snaps, long[] ids, int undoFlag, int redoFlag, String label) { }

    private final Chronos p;
    private final Map<UUID, Deque<Batch>> undo = new ConcurrentHashMap<>(), redo = new ConcurrentHashMap<>();

    public UndoStack(Chronos p) { this.p = p; }

    private void push(Map<UUID, Deque<Batch>> m, UUID u, Batch b) {
        Deque<Batch> d = m.computeIfAbsent(u, k -> new ArrayDeque<>());
        synchronized (d) {
            d.addLast(b);
            while (d.size() > Math.max(1, p.cfg.undoDepth)) d.removeFirst();
        }
    }

    private Batch pop(Map<UUID, Deque<Batch>> m, UUID u) {
        Deque<Batch> d = m.get(u);
        if (d == null) return null;
        synchronized (d) { return d.pollLast(); }
    }

    /** Nuova operazione: azzera il redo. */
    public void pushNew(UUID u, Batch b) { redo.remove(u); push(undo, u, b); }
    public void pushUndo(UUID u, Batch b) { push(undo, u, b); }
    public void pushRedo(UUID u, Batch b) { push(redo, u, b); }
    public Batch popUndo(UUID u) { return pop(undo, u); }
    public Batch popRedo(UUID u) { return pop(redo, u); }
    public int undoSize(UUID u) { Deque<Batch> d = undo.get(u); return d == null ? 0 : d.size(); }
    public int redoSize(UUID u) { Deque<Batch> d = redo.get(u); return d == null ? 0 : d.size(); }
}
