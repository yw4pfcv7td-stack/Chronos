package dev.chronos.storage;

import dev.chronos.Chronos;
import dev.chronos.model.ActionRow;
import dev.chronos.model.ActionType;
import dev.chronos.model.IncidentInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class QueryService {
    private static final String FROM =
            " FROM chronos_action a JOIN chronos_user u ON u.id = a.user_id JOIN chronos_dict w ON w.id = a.world_id"
          + " JOIN chronos_dict m ON m.id = a.material_id LEFT JOIN chronos_dict od ON od.id = a.old_id"
          + " LEFT JOIN chronos_dict nd ON nd.id = a.new_id";

    private final Chronos plugin;

    public QueryService(Chronos plugin) { this.plugin = plugin; }

    public long nextIncidentId() throws SQLException {
        try (Connection c = plugin.db.connection();
             PreparedStatement ps = c.prepareStatement("SELECT COALESCE(MAX(id), 0) + 1 FROM chronos_incident");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 1;
        }
    }

    private boolean where(Connection c, QuerySpec s, StringBuilder w, List<Object> p) throws SQLException {
        w.append(" WHERE 1=1");
        if (s.world != null) {
            int wid = plugin.ids.findDict(c, IdCache.WORLD, s.world);
            if (wid < 0) return false;
            w.append(" AND a.world_id = ?");
            p.add(wid);
        }
        if (s.box) {
            w.append(" AND a.x BETWEEN ? AND ? AND a.z BETWEEN ? AND ? AND a.y BETWEEN ? AND ?");
            p.add(s.minX); p.add(s.maxX); p.add(s.minZ); p.add(s.maxZ); p.add(s.minY); p.add(s.maxY);
        }
        in(w, p, "LOWER(u.name)", s.users, false);
        in(w, p, "LOWER(u.name)", s.xUsers, true);
        in(w, p, "m.val", s.blocks, false);
        in(w, p, "m.val", s.xBlocks, true);
        if (!s.types.isEmpty()) {
            w.append(" AND a.atype IN (");
            boolean first = true;
            for (ActionType t : s.types) {
                if (!first) w.append(',');
                w.append(t.id);
                first = false;
            }
            w.append(')');
        }
        if (s.since >= 0) { w.append(" AND a.ts >= ?"); p.add(s.since); }
        if (s.until >= 0) { w.append(" AND a.ts <= ?"); p.add(s.until); }
        if (s.incident >= 0) { w.append(" AND a.incident = ?"); p.add(s.incident); }
        if (s.rolledBack >= 0) { w.append(" AND a.rolled_back = ?"); p.add(s.rolledBack); }
        return true;
    }

    private static void in(StringBuilder w, List<Object> p, String col, List<String> vals, boolean not) {
        if (vals.isEmpty()) return;
        w.append(" AND ").append(col).append(not ? " NOT IN (" : " IN (");
        for (int i = 0; i < vals.size(); i++) {
            if (i > 0) w.append(',');
            w.append('?');
            p.add(vals.get(i).toLowerCase(Locale.ROOT));
        }
        w.append(')');
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        int i = 1;
        for (Object o : params) {
            if (o instanceof Long l) ps.setLong(i++, l);
            else if (o instanceof Integer n) ps.setInt(i++, n);
            else ps.setString(i++, String.valueOf(o));
        }
    }

    private static ActionRow map(ResultSet rs, boolean extra) throws SQLException {
        return new ActionRow(rs.getLong("id"), rs.getLong("ts"), rs.getString("uname"), rs.getString("wname"),
                rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), ActionType.byId(rs.getInt("atype")),
                rs.getString("mat"), rs.getString("oldv"), rs.getString("newv"), rs.getLong("incident"),
                rs.getInt("amount"), rs.getInt("rolled_back") == 1, extra ? rs.getBytes("extra") : null);
    }

    public List<ActionRow> rows(QuerySpec s, boolean extra, boolean asc, int limit, int offset) throws SQLException {
        try (Connection c = plugin.db.connection()) {
            StringBuilder w = new StringBuilder();
            List<Object> p = new ArrayList<>();
            if (!where(c, s, w, p)) return List.of();
            String sql = "SELECT a.id, a.ts, u.name AS uname, w.val AS wname, a.x, a.y, a.z, a.atype, m.val AS mat,"
                    + " od.val AS oldv, nd.val AS newv, a.incident, a.amount, a.rolled_back" + (extra ? ", a.extra" : "")
                    + FROM + w + " ORDER BY a.id " + (asc ? "ASC" : "DESC") + " LIMIT ? OFFSET ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                bind(ps, p);
                ps.setInt(p.size() + 1, limit);
                ps.setInt(p.size() + 2, offset);
                List<ActionRow> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(map(rs, extra));
                }
                return out;
            }
        }
    }

    public int count(QuerySpec s) throws SQLException {
        try (Connection c = plugin.db.connection()) {
            StringBuilder w = new StringBuilder();
            List<Object> p = new ArrayList<>();
            if (!where(c, s, w, p)) return 0;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*)" + FROM + w)) {
                bind(ps, p);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        }
    }

    /** Cronologia di un singolo blocco. */
    public QuerySpec blockSpec(String world, int x, int y, int z) {
        QuerySpec s = new QuerySpec();
        s.world = world;
        s.box = true;
        s.minX = s.maxX = x;
        s.minY = s.maxY = y;
        s.minZ = s.maxZ = z;
        return s;
    }

    public List<IncidentInfo> incidents(QuerySpec s, int limit) throws SQLException {
        try (Connection c = plugin.db.connection()) {
            StringBuilder sql = new StringBuilder("SELECT i.id, i.ts, u.name, i.kind, (SELECT COUNT(*) FROM chronos_action a WHERE a.incident = i.id) AS cnt"
                    + " FROM chronos_incident i JOIN chronos_user u ON u.id = i.user_id WHERE 1=1");
            List<Object> p = new ArrayList<>();
            in(sql, p, "LOWER(u.name)", s.users, false);
            if (s.since >= 0) { sql.append(" AND i.ts >= ?"); p.add(s.since); }
            sql.append(" ORDER BY i.id DESC LIMIT ?");
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                bind(ps, p);
                ps.setInt(p.size() + 1, limit);
                List<IncidentInfo> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new IncidentInfo(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getInt(5)));
                }
                return out;
            }
        }
    }
}
