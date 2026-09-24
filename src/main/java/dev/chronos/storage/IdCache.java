package dev.chronos.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Cache di ID per dizionario e utenti. Le scritture avvengono solo dal thread writer. */
public final class IdCache {
    public static final int MATERIAL = 0, WORLD = 1, DATA = 2, ENTITY = 3;

    private final Map<String, Integer> dict = new ConcurrentHashMap<>();
    private final Map<String, Integer> users = new ConcurrentHashMap<>();
    private final Map<String, String> userNames = new ConcurrentHashMap<>();

    public void clear() { dict.clear(); users.clear(); userNames.clear(); }

    /** Restituisce l'ID (creandolo se serve). */
    public int dict(Connection c, int kind, String value) throws SQLException {
        String k = kind + ":" + value;
        Integer cached = dict.get(k);
        if (cached != null) return cached;
        int id = select(c, kind, value);
        if (id < 0) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO chronos_dict(kind, val) VALUES(?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, kind);
                ps.setString(2, value);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    id = (int) rs.getLong(1);
                }
            }
        }
        dict.put(k, id);
        return id;
    }

    /** Restituisce l'ID oppure -1 se non esiste (non crea nulla: usabile dai thread di lettura). */
    public int findDict(Connection c, int kind, String value) throws SQLException {
        String k = kind + ":" + value;
        Integer cached = dict.get(k);
        if (cached != null) return cached;
        int id = select(c, kind, value);
        if (id >= 0) dict.put(k, id);
        return id;
    }

    private int select(Connection c, int kind, String value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM chronos_dict WHERE kind = ? AND val = ?")) {
            ps.setInt(1, kind);
            ps.setString(2, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? (int) rs.getLong(1) : -1;
            }
        }
    }

    public int user(Connection c, String key, String name) throws SQLException {
        Integer cached = users.get(key);
        if (cached != null) {
            String old = userNames.get(key);
            if (old != null && !old.equals(name) && !key.startsWith("#")) {
                try (PreparedStatement ps = c.prepareStatement("UPDATE chronos_user SET name = ? WHERE id = ?")) {
                    ps.setString(1, name);
                    ps.setInt(2, cached);
                    ps.executeUpdate();
                }
                userNames.put(key, name);
            }
            return cached;
        }
        int id = -1;
        String dbName = null;
        try (PreparedStatement ps = c.prepareStatement("SELECT id, name FROM chronos_user WHERE ukey = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) { id = (int) rs.getLong(1); dbName = rs.getString(2); }
            }
        }
        if (id < 0) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO chronos_user(ukey, name) VALUES(?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, key);
                ps.setString(2, name);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    id = (int) rs.getLong(1);
                }
            }
            dbName = name;
        } else if (!key.startsWith("#") && !name.equals(dbName)) {
            try (PreparedStatement ps = c.prepareStatement("UPDATE chronos_user SET name = ? WHERE id = ?")) {
                ps.setString(1, name);
                ps.setInt(2, id);
                ps.executeUpdate();
            }
            dbName = name;
        }
        users.put(key, id);
        userNames.put(key, dbName);
        return id;
    }
}
