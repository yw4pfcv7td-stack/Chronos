package dev.chronos;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.*;

public final class ChronosConfig {
    public final String dbType, sqliteFile, host, database, user, password;
    public final int port, poolSize;
    public final boolean logBlocks, logContainers, logKills, killsAll, logChat, logCommands, logSessions,
            logEnvironment, logPistons, itemData;
    public final Set<String> ignoredWorlds = new HashSet<>(), ignoredCommands = new HashSet<>();
    public final Set<EntityType> entityBlockChange = EnumSet.noneOf(EntityType.class);
    public final int batchSize, flushMs, blocksPerTick, maxRollbackRows, undoDepth;
    public final boolean previewEnabled;
    public final int previewSeconds, maxPreviewBlocks, maxRadius;
    public final String wandMaterial;
    public final boolean retentionEnabled, retentionArchive;
    public final int retActionsDays, retMessagesDays;
    public final boolean snapEnabled, snapDeath, snapLogout, snapLogin;
    public final int snapIntervalMin, snapKeep;
    public final boolean detEnabled;
    public final int alertCooldownSec, xrayWindowMin, xrayMinOres, xrayHidden, massBlocks, massSeconds, contItems, contStacks;
    public final double xrayRatio;
    public final boolean discordEnabled;
    public final String discordUrl, discordName;
    public final String language, updateSlug;
    public final boolean updateCheck, notifyRollback, logCreative, lookupGui;
    public final int pageSize;
    public final Set<String> ignoredPlayers = new HashSet<>(), ignoredBlocks = new HashSet<>(), detIgnoredWorlds = new HashSet<>();

    public ChronosConfig(FileConfiguration c) {
        dbType = c.getString("storage.type", "sqlite");
        sqliteFile = c.getString("storage.sqlite-file", "chronos.db");
        host = c.getString("storage.host", "localhost");
        port = c.getInt("storage.port", 3306);
        database = c.getString("storage.database", "chronos");
        user = c.getString("storage.username", "root");
        password = c.getString("storage.password", "");
        poolSize = c.getInt("storage.pool-size", 6);

        logBlocks = c.getBoolean("logging.blocks", true);
        logContainers = c.getBoolean("logging.containers", true);
        logKills = c.getBoolean("logging.kills", true);
        killsAll = c.getBoolean("logging.kills-all-mobs", false);
        logChat = c.getBoolean("logging.chat", true);
        logCommands = c.getBoolean("logging.commands", true);
        logSessions = c.getBoolean("logging.sessions", true);
        logEnvironment = c.getBoolean("logging.environment", true);
        logPistons = c.getBoolean("logging.pistons", false);
        itemData = c.getBoolean("logging.item-data", true);
        for (String s : c.getStringList("logging.ignored-worlds")) ignoredWorlds.add(s.toLowerCase(Locale.ROOT));
        for (String s : c.getStringList("logging.ignored-commands")) ignoredCommands.add(s.toLowerCase(Locale.ROOT));
        for (String s : c.getStringList("logging.entity-block-change")) {
            try { entityBlockChange.add(EntityType.valueOf(s.toUpperCase(Locale.ROOT))); } catch (IllegalArgumentException ignored) { }
        }

        batchSize = c.getInt("performance.batch-size", 2000);
        flushMs = c.getInt("performance.flush-interval-ms", 500);
        blocksPerTick = c.getInt("performance.blocks-per-tick", 2000);
        maxRollbackRows = c.getInt("performance.max-rollback-rows", 500000);
        undoDepth = c.getInt("performance.undo-depth", 10);

        previewEnabled = c.getBoolean("rollback.preview", true);
        previewSeconds = c.getInt("rollback.preview-seconds", 120);
        maxPreviewBlocks = c.getInt("rollback.max-preview-blocks", 30000);
        maxRadius = c.getInt("rollback.max-radius", 250);
        wandMaterial = c.getString("wand-material", "BLAZE_ROD");

        retentionEnabled = c.getBoolean("retention.enabled", true);
        retActionsDays = c.getInt("retention.actions-days", 60);
        retMessagesDays = c.getInt("retention.messages-days", 14);
        retentionArchive = c.getBoolean("retention.archive", true);

        snapEnabled = c.getBoolean("snapshots.enabled", true);
        snapDeath = c.getBoolean("snapshots.on-death", true);
        snapLogout = c.getBoolean("snapshots.on-logout", true);
        snapLogin = c.getBoolean("snapshots.on-login", false);
        snapIntervalMin = c.getInt("snapshots.interval-minutes", 15);
        snapKeep = c.getInt("snapshots.keep-per-player", 40);

        detEnabled = c.getBoolean("detection.enabled", true);
        alertCooldownSec = c.getInt("detection.alert-cooldown-seconds", 300);
        xrayWindowMin = c.getInt("detection.xray.window-minutes", 10);
        xrayMinOres = c.getInt("detection.xray.min-ores", 8);
        xrayRatio = c.getDouble("detection.xray.ore-ratio", 0.30);
        xrayHidden = c.getInt("detection.xray.hidden-ores", 6);
        massBlocks = c.getInt("detection.mass-break.blocks", 250);
        massSeconds = c.getInt("detection.mass-break.seconds", 10);
        contItems = c.getInt("detection.container.items-removed", 400);
        contStacks = c.getInt("detection.container.emptied-min-stacks", 12);

        discordEnabled = c.getBoolean("discord.enabled", false);
        discordUrl = c.getString("discord.webhook-url", "");
        discordName = c.getString("discord.username", "Chronos");

        language = c.getString("language", "en");
        updateCheck = c.getBoolean("update-checker.enabled", true);
        updateSlug = c.getString("update-checker.modrinth-slug", "");
        notifyRollback = c.getBoolean("rollback.notify-staff", true);
        logCreative = c.getBoolean("logging.log-creative", true);
        lookupGui = c.getBoolean("inspector.lookup-gui", true);
        pageSize = Math.max(3, Math.min(30, c.getInt("inspector.results-per-page", 10)));
        for (String x : c.getStringList("logging.ignored-players")) ignoredPlayers.add(x.toLowerCase(Locale.ROOT));
        for (String x : c.getStringList("logging.ignored-blocks")) ignoredBlocks.add(x.toLowerCase(Locale.ROOT));
        for (String x : c.getStringList("detection.ignored-worlds")) detIgnoredWorlds.add(x.toLowerCase(Locale.ROOT));
    }
}
