package dev.chronos.storage;

import dev.chronos.Chronos;
import dev.chronos.model.LogRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;

/** Coda lock-free + writer asincrono che scrive a batch in transazione. */
public final class LogQueue implements Runnable {
    private static final int MAX_QUEUE = 2_000_000;

    private final Chronos plugin;
    private final ConcurrentLinkedQueue<LogRecord> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();
    private final AtomicLong written = new AtomicLong(), dropped = new AtomicLong();
    private volatile boolean running = true;
    private Thread thread;

    public LogQueue(Chronos plugin) { this.plugin = plugin; }

    public void start() {
        thread = new Thread(this, "Chronos-Writer");
        thread.start();
    }

    public void add(LogRecord r) {
        if (size.get() >= MAX_QUEUE) {
            if (dropped.incrementAndGet() % 10_000 == 1) plugin.getLogger().warning("Queue full: records dropped (database too slow?)");
            return;
        }
        size.incrementAndGet();
        queue.add(r);
    }

    public int size() { return size.get(); }
    public long written() { return written.get(); }
    public long dropped() { return dropped.get(); }

    /** Attende (bloccando) che la coda sia stata scritta: da chiamare da thread asincroni. */
    public void awaitDrain(long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (size.get() > 0 && System.currentTimeMillis() < end) {
            LockSupport.unpark(thread);
            try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
    }

    public void stop() {
        running = false;
        LockSupport.unpark(thread);
        try { thread.join(60_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @Override public void run() {
        List<LogRecord> batch = new ArrayList<>();
        while (true) {
            batch.clear();
            int max = plugin.cfg.batchSize;
            LogRecord r;
            while (batch.size() < max && (r = queue.poll()) != null) batch.add(r);
            if (batch.isEmpty()) {
                if (!running) break;
                LockSupport.parkNanos(plugin.cfg.flushMs * 1_000_000L);
                continue;
            }
            boolean ok = false;
            for (int i = 0; i < 3 && !ok; i++) {
                try {
                    write(batch);
                    ok = true;
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Database write error (attempt " + (i + 1) + "/3)", e);
                    plugin.ids.clear();
                    try { Thread.sleep(1000L * (i + 1)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
            if (ok) written.addAndGet(batch.size()); else dropped.addAndGet(batch.size());
            size.addAndGet(-batch.size());
        }
    }

    private void write(List<LogRecord> batch) throws SQLException {
        IdCache ids = plugin.ids;
        try (Connection c = plugin.db.connection()) {
            c.setAutoCommit(false);
            try (PreparedStatement act = c.prepareStatement("INSERT INTO chronos_action(ts,user_id,world_id,x,y,z,atype,material_id,old_id,new_id,extra,incident,amount) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)");
                 PreparedStatement msg = c.prepareStatement("INSERT INTO chronos_message(ts,user_id,world_id,x,y,z,mtype,msg) VALUES(?,?,?,?,?,?,?,?)");
                 PreparedStatement inc = c.prepareStatement("INSERT INTO chronos_incident(id,ts,user_id,kind) VALUES(?,?,?,?)");
                 PreparedStatement snap = c.prepareStatement("INSERT INTO chronos_snapshot(ts,user_id,reason,world_id,x,y,z,health,food,lvl,exp,inv,ender) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                int na = 0, nm = 0, ni = 0, ns = 0;
                for (LogRecord rec : batch) {
                    switch (rec) {
                        case LogRecord.Action a -> {
                            act.setLong(1, a.time());
                            act.setInt(2, ids.user(c, a.userKey(), a.userName()));
                            act.setInt(3, ids.dict(c, IdCache.WORLD, a.world()));
                            act.setInt(4, a.x());
                            act.setInt(5, a.y());
                            act.setInt(6, a.z());
                            act.setInt(7, a.type().id);
                            act.setInt(8, ids.dict(c, IdCache.MATERIAL, a.material()));
                            act.setInt(9, a.oldData() == null ? 0 : ids.dict(c, IdCache.DATA, a.oldData()));
                            act.setInt(10, a.newData() == null ? 0 : ids.dict(c, IdCache.DATA, a.newData()));
                            act.setBytes(11, a.extra());
                            act.setLong(12, a.incident());
                            act.setInt(13, a.amount());
                            act.addBatch();
                            na++;
                        }
                        case LogRecord.Message m -> {
                            msg.setLong(1, m.time());
                            msg.setInt(2, ids.user(c, m.userKey(), m.userName()));
                            msg.setInt(3, ids.dict(c, IdCache.WORLD, m.world()));
                            msg.setInt(4, m.x());
                            msg.setInt(5, m.y());
                            msg.setInt(6, m.z());
                            msg.setInt(7, m.type().id);
                            msg.setString(8, m.text());
                            msg.addBatch();
                            nm++;
                        }
                        case LogRecord.Incident i -> {
                            inc.setLong(1, i.id());
                            inc.setLong(2, i.time());
                            inc.setInt(3, ids.user(c, i.userKey(), i.userName()));
                            inc.setString(4, i.kind());
                            inc.addBatch();
                            ni++;
                        }
                        case LogRecord.Snapshot s -> {
                            snap.setLong(1, s.time());
                            snap.setInt(2, ids.user(c, s.userKey(), s.userName()));
                            snap.setInt(3, s.reason());
                            snap.setInt(4, ids.dict(c, IdCache.WORLD, s.world()));
                            snap.setInt(5, s.x());
                            snap.setInt(6, s.y());
                            snap.setInt(7, s.z());
                            snap.setDouble(8, s.health());
                            snap.setInt(9, s.food());
                            snap.setInt(10, s.level());
                            snap.setDouble(11, s.exp());
                            snap.setBytes(12, s.inventory());
                            snap.setBytes(13, s.ender());
                            snap.addBatch();
                            ns++;
                        }
                    }
                }
                if (ni > 0) inc.executeBatch();
                if (na > 0) act.executeBatch();
                if (nm > 0) msg.executeBatch();
                if (ns > 0) snap.executeBatch();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }
}
