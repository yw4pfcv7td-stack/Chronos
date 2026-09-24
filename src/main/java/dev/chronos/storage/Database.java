package dev.chronos.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.chronos.Chronos;
import dev.chronos.ChronosConfig;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public final class Database implements AutoCloseable {
    public enum Type { SQLITE, MYSQL, POSTGRESQL }

    private final HikariDataSource ds;
    public final Type type;

    public Database(Chronos plugin) {
        ChronosConfig c = plugin.cfg;
        Type t;
        try { t = Type.valueOf(c.dbType.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { t = Type.SQLITE; }
        this.type = t;

        HikariConfig h = new HikariConfig();
        h.setPoolName("Chronos");
        switch (type) {
            case SQLITE -> {
                plugin.getDataFolder().mkdirs();
                File f = new File(plugin.getDataFolder(), c.sqliteFile);
                h.setJdbcUrl("jdbc:sqlite:" + f.getAbsolutePath());
                h.setDriverClassName("org.sqlite.JDBC");
                h.setMaximumPoolSize(4);
                h.addDataSourceProperty("journal_mode", "WAL");
                h.addDataSourceProperty("synchronous", "NORMAL");
                h.addDataSourceProperty("busy_timeout", "10000");
            }
            case MYSQL -> {
                h.setJdbcUrl("jdbc:mysql://" + c.host + ":" + c.port + "/" + c.database
                        + "?useSSL=false&characterEncoding=utf8&rewriteBatchedStatements=true");
                h.setDriverClassName("com.mysql.cj.jdbc.Driver");
                h.setUsername(c.user);
                h.setPassword(c.password);
                h.setMaximumPoolSize(c.poolSize);
            }
            case POSTGRESQL -> {
                h.setJdbcUrl("jdbc:postgresql://" + c.host + ":" + c.port + "/" + c.database + "?reWriteBatchedInserts=true");
                h.setDriverClassName("org.postgresql.Driver");
                h.setUsername(c.user);
                h.setPassword(c.password);
                h.setMaximumPoolSize(c.poolSize);
            }
        }
        this.ds = new HikariDataSource(h);
    }

    public Connection connection() throws SQLException { return ds.getConnection(); }

    public void init() throws SQLException {
        String pk = switch (type) {
            case SQLITE -> "INTEGER PRIMARY KEY AUTOINCREMENT";
            case MYSQL -> "BIGINT PRIMARY KEY AUTO_INCREMENT";
            case POSTGRESQL -> "BIGSERIAL PRIMARY KEY";
        };
        String blob = switch (type) {
            case SQLITE -> "BLOB";
            case MYSQL -> "MEDIUMBLOB";
            case POSTGRESQL -> "BYTEA";
        };
        String[] ddl = {
            // dizionario compatto: materiali, mondi, blockdata, entita' -> ID numerici
            "CREATE TABLE IF NOT EXISTS chronos_dict(id " + pk + ", kind INT NOT NULL, val VARCHAR(512) NOT NULL, UNIQUE(kind, val))",
            "CREATE TABLE IF NOT EXISTS chronos_user(id " + pk + ", ukey VARCHAR(64) NOT NULL UNIQUE, name VARCHAR(64) NOT NULL)",
            "CREATE TABLE IF NOT EXISTS chronos_incident(id BIGINT PRIMARY KEY, ts BIGINT NOT NULL, user_id BIGINT NOT NULL, kind VARCHAR(32) NOT NULL)",
            "CREATE TABLE IF NOT EXISTS chronos_action(id " + pk + ", ts BIGINT NOT NULL, user_id BIGINT NOT NULL, world_id BIGINT NOT NULL,"
                + " x INT NOT NULL, y INT NOT NULL, z INT NOT NULL, atype SMALLINT NOT NULL, material_id BIGINT NOT NULL,"
                + " old_id BIGINT NOT NULL DEFAULT 0, new_id BIGINT NOT NULL DEFAULT 0, extra " + blob + ","
                + " incident BIGINT NOT NULL DEFAULT 0, amount INT NOT NULL DEFAULT 1, rolled_back SMALLINT NOT NULL DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS chronos_message(id " + pk + ", ts BIGINT NOT NULL, user_id BIGINT NOT NULL, world_id BIGINT NOT NULL,"
                + " x INT NOT NULL, y INT NOT NULL, z INT NOT NULL, mtype SMALLINT NOT NULL, msg TEXT)",
            "CREATE TABLE IF NOT EXISTS chronos_snapshot(id " + pk + ", ts BIGINT NOT NULL, user_id BIGINT NOT NULL, reason SMALLINT NOT NULL,"
                + " world_id BIGINT NOT NULL, x INT NOT NULL, y INT NOT NULL, z INT NOT NULL, health DOUBLE PRECISION, food INT,"
                + " lvl INT, exp DOUBLE PRECISION, inv " + blob + ", ender " + blob + ")"
        };
        String[] idx = {
            "CREATE INDEX idx_action_pos ON chronos_action(world_id, x, z, y)",
            "CREATE INDEX idx_action_user ON chronos_action(user_id, ts)",
            "CREATE INDEX idx_action_ts ON chronos_action(ts)",
            "CREATE INDEX idx_action_inc ON chronos_action(incident)",
            "CREATE INDEX idx_message_ts ON chronos_message(ts)",
            "CREATE INDEX idx_message_user ON chronos_message(user_id, ts)",
            "CREATE INDEX idx_snapshot_user ON chronos_snapshot(user_id, id)",
            "CREATE INDEX idx_incident_ts ON chronos_incident(ts)"
        };
        try (Connection c = connection(); Statement st = c.createStatement()) {
            for (String s : ddl) st.execute(s);
            for (String s : idx) {
                try { st.execute(s); } catch (SQLException ignored) { /* indice gia' esistente */ }
            }
        }
    }

    @Override public void close() { if (!ds.isClosed()) ds.close(); }
}
