package dev.chronos.storage;

import dev.chronos.Chronos;

import java.io.BufferedWriter;
import java.io.File;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

/** Retention: elimina (opzionalmente archiviando in CSV compresso) i dati vecchi a finestre di ID, senza bloccare il DB. */
public final class RetentionService {
    private static final int WINDOW = 20_000;
    private final Chronos p;

    public RetentionService(Chronos p) { this.p = p; }

    public void run(long actionMs, long messageMs) {
        try {
            long now = System.currentTimeMillis();
            long deleted = purgeActions(now - actionMs);
            long msgs = purgeSimple("chronos_message", now - messageMs);
            purgeSimple("chronos_incident", now - actionMs);
            p.snapshots.prune();
            if (deleted + msgs > 0) p.getLogger().info("Retention: deleted " + deleted + " actions and " + msgs + " messages.");
        } catch (Exception e) {
            p.getLogger().log(Level.SEVERE, "Retention error", e);
        }
    }

    public void runDefault() { run(p.cfg.retActionsDays * 86_400_000L, p.cfg.retMessagesDays * 86_400_000L); }

    private long purgeActions(long cutoffTs) throws Exception {
        try (Connection c = p.db.connection()) {
            long max, min;
            try (PreparedStatement ps = c.prepareStatement("SELECT MAX(id) FROM chronos_action WHERE ts < ?")) {
                ps.setLong(1, cutoffTs);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); max = rs.getLong(1); if (rs.wasNull()) return 0; }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT MIN(id) FROM chronos_action");
                 ResultSet rs = ps.executeQuery()) { rs.next(); min = rs.getLong(1); }
            BufferedWriter out = null;
            if (p.cfg.retentionArchive) {
                File dir = new File(p.getDataFolder(), "archive");
                dir.mkdirs();
                File f = new File(dir, "actions-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".csv.gz");
                out = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new java.io.FileOutputStream(f)), StandardCharsets.UTF_8));
                out.write("id,ts,user,world,x,y,z,type,material,amount,incident\n");
            }
            long total = 0;
            try {
                for (long from = min - 1; from < max; from += WINDOW) {
                    long to = Math.min(max, from + WINDOW);
                    if (out != null) {
                        try (PreparedStatement ps = c.prepareStatement("SELECT a.id, a.ts, u.name, w.val, a.x, a.y, a.z, a.atype, m.val, a.amount, a.incident"
                                + " FROM chronos_action a JOIN chronos_user u ON u.id = a.user_id JOIN chronos_dict w ON w.id = a.world_id"
                                + " JOIN chronos_dict m ON m.id = a.material_id WHERE a.id > ? AND a.id <= ?")) {
                            ps.setLong(1, from);
                            ps.setLong(2, to);
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    out.write(rs.getLong(1) + "," + rs.getLong(2) + "," + rs.getString(3) + "," + rs.getString(4) + ","
                                            + rs.getInt(5) + "," + rs.getInt(6) + "," + rs.getInt(7) + "," + rs.getInt(8) + ","
                                            + rs.getString(9) + "," + rs.getInt(10) + "," + rs.getLong(11) + "\n");
                                }
                            }
                        }
                    }
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM chronos_action WHERE id > ? AND id <= ?")) {
                        ps.setLong(1, from);
                        ps.setLong(2, to);
                        total += ps.executeUpdate();
                    }
                    Thread.sleep(50);
                }
            } finally {
                if (out != null) out.close();
            }
            return total;
        }
    }

    private long purgeSimple(String table, long cutoffTs) throws SQLException {
        try (Connection c = p.db.connection(); PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE ts < ?")) {
            ps.setLong(1, cutoffTs);
            return ps.executeUpdate();
        }
    }
}
