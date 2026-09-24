package dev.chronos.snapshot;

import dev.chronos.Chronos;
import dev.chronos.log.Extras;
import dev.chronos.model.Actor;
import dev.chronos.model.LogRecord;
import dev.chronos.util.Msg;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Snapshot di inventario, ender chest, XP e posizione: alla morte, al logout e a intervalli. */
public final class SnapshotService {
    public static final int DEATH = 0, LOGOUT = 1, PERIODIC = 2, LOGIN = 3, PRE_RESTORE = 4, MANUAL = 5;

    public record Info(long id, long ts, int reason, String world, int x, int y, int z, int level) { }
    public record Data(long id, long ts, String user, int reason, byte[] inv, byte[] ender, int level, float exp, double health, int food) { }

    private final Chronos p;

    public SnapshotService(Chronos p) { this.p = p; }

    /** Da chiamare sul thread del giocatore. */
    public void capture(Player pl, int reason) {
        if (!p.cfg.snapEnabled) return;
        Location l = pl.getLocation();
        byte[] inv = Extras.items(pl.getInventory().getContents());
        byte[] ender = Extras.items(pl.getEnderChest().getContents());
        Actor a = Actor.of(pl);
        p.queue.add(new LogRecord.Snapshot(System.currentTimeMillis(), a.key(), a.name(), l.getWorld().getName(),
                l.getBlockX(), l.getBlockY(), l.getBlockZ(), reason, pl.getHealth(), pl.getFoodLevel(),
                pl.getLevel(), pl.getExp(), inv, ender));
    }

    public void startPeriodic() {
        if (!p.cfg.snapEnabled || p.cfg.snapIntervalMin <= 0) return;
        long ticks = p.cfg.snapIntervalMin * 60L * 20L;
        p.sched.globalTimer(() -> {
            for (Player pl : p.getServer().getOnlinePlayers()) p.sched.entity(pl, () -> capture(pl, PERIODIC));
        }, ticks, ticks);
    }

    public CompletableFuture<List<Info>> list(String player, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            p.queue.awaitDrain(2000);
            try (Connection c = p.db.connection();
                 PreparedStatement ps = c.prepareStatement("SELECT s.id, s.ts, s.reason, w.val, s.x, s.y, s.z, s.lvl FROM chronos_snapshot s"
                         + " JOIN chronos_user u ON u.id = s.user_id JOIN chronos_dict w ON w.id = s.world_id"
                         + " WHERE LOWER(u.name) = ? ORDER BY s.id DESC LIMIT ?")) {
                ps.setString(1, player.toLowerCase(Locale.ROOT));
                ps.setInt(2, limit);
                List<Info> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new Info(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getInt(8)));
                }
                return out;
            } catch (SQLException e) { throw new CompletionException(e); }
        }, p.dbPool);
    }

    /** selector: "last", "death" oppure un ID numerico. */
    public CompletableFuture<Data> find(String player, String selector) {
        return CompletableFuture.supplyAsync(() -> {
            p.queue.awaitDrain(2000);
            String sql = "SELECT s.id, s.ts, u.name, s.reason, s.inv, s.ender, s.lvl, s.exp, s.health, s.food FROM chronos_snapshot s"
                    + " JOIN chronos_user u ON u.id = s.user_id WHERE LOWER(u.name) = ?";
            long id = -1;
            if (selector.equalsIgnoreCase("death")) sql += " AND s.reason = 0";
            else if (!selector.equalsIgnoreCase("last")) {
                try { id = Long.parseLong(selector); } catch (NumberFormatException e) { return null; }
                sql += " AND s.id = ?";
            }
            sql += " ORDER BY s.id DESC LIMIT 1";
            try (Connection c = p.db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, player.toLowerCase(Locale.ROOT));
                if (id >= 0) ps.setLong(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    return new Data(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4), rs.getBytes(5), rs.getBytes(6),
                            rs.getInt(7), (float) rs.getDouble(8), rs.getDouble(9), rs.getInt(10));
                }
            } catch (SQLException e) { throw new CompletionException(e); }
        }, p.dbPool);
    }

    /** Ripristina (sul thread del giocatore). parts: inv, ender, xp. Salva prima uno snapshot "pre-restore". */
    public void restore(Player target, Data d, Set<String> parts, CommandSender by) {
        capture(target, PRE_RESTORE);
        List<String> done = new ArrayList<>();
        if (parts.contains("inv") && d.inv() != null) {
            ItemStack[] items = Extras.readItems(d.inv());
            target.getInventory().setContents(Arrays.copyOf(items, target.getInventory().getSize()));
            done.add(p.lang.raw("snap.part_inv"));
        }
        if (parts.contains("ender") && d.ender() != null) {
            ItemStack[] items = Extras.readItems(d.ender());
            target.getEnderChest().setContents(Arrays.copyOf(items, target.getEnderChest().getSize()));
            done.add(p.lang.raw("snap.part_ender"));
        }
        if (parts.contains("xp")) {
            target.setLevel(d.level());
            target.setExp(d.exp());
            done.add(p.lang.raw("snap.part_xp"));
        }
        Msg.send(by, p.lang.t("snap.restored", "id", d.id(), "player", target.getName(), "parts", String.join(", ", done)));
        if (by != target) Msg.send(target, p.lang.t("snap.restored_notice"));
    }

    /** Elimina gli snapshot oltre il limite per giocatore. */
    public void prune() throws SQLException {
        int keep = p.cfg.snapKeep;
        if (keep <= 0) return;
        try (Connection c = p.db.connection()) {
            List<Long> users = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT DISTINCT user_id FROM chronos_snapshot"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) users.add(rs.getLong(1));
            }
            for (long u : users) {
                long cutoff = -1;
                try (PreparedStatement ps = c.prepareStatement("SELECT id FROM chronos_snapshot WHERE user_id = ? ORDER BY id DESC LIMIT 1 OFFSET ?")) {
                    ps.setLong(1, u);
                    ps.setInt(2, keep);
                    try (ResultSet rs = ps.executeQuery()) { if (rs.next()) cutoff = rs.getLong(1); }
                }
                if (cutoff < 0) continue;
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM chronos_snapshot WHERE user_id = ? AND id <= ?")) {
                    ps.setLong(1, u);
                    ps.setLong(2, cutoff);
                    ps.executeUpdate();
                }
            }
        }
    }
}
