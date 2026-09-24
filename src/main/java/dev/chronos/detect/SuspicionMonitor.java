package dev.chronos.detect;

import dev.chronos.Chronos;
import dev.chronos.util.Msg;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Rilevamento X-Ray (rapporto minerali/pietra e minerali sepolti), rottura di massa, svuotamento contenitori. */
public final class SuspicionMonitor {
    private static final Set<Material> STONES = EnumSet.of(Material.STONE, Material.DEEPSLATE, Material.GRANITE,
            Material.DIORITE, Material.ANDESITE, Material.TUFF, Material.NETHERRACK, Material.BASALT, Material.BLACKSTONE);
    private static final Set<Material> ORES = EnumSet.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE, Material.ANCIENT_DEBRIS, Material.GOLD_ORE,
            Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE);
    private static final BlockFace[] FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private static final class Stat {
        long windowStart, lastXray, lastMass, lastCont;
        int stone, ores, hidden;
        final ArrayDeque<Long> breaks = new ArrayDeque<>();
    }

    private final Chronos p;
    private final Discord discord;
    private final Map<UUID, Stat> stats = new ConcurrentHashMap<>();
    private final Set<UUID> muted = ConcurrentHashMap.newKeySet();

    public SuspicionMonitor(Chronos p) {
        this.p = p;
        this.discord = new Discord(p);
    }

    public boolean toggleAlerts(UUID id) {
        if (!muted.remove(id)) { muted.add(id); return false; }
        return true;
    }

    private boolean ignoredWorld(Player pl) {
        return p.cfg.detIgnoredWorlds.contains(pl.getWorld().getName().toLowerCase(java.util.Locale.ROOT));
    }

    private static int exposedFaces(Block b) {
        int n = 0;
        for (BlockFace f : FACES) if (!b.getRelative(f).getType().isOccluding()) n++;
        return n;
    }

    public void onBlockBreak(Player pl, Block b) {
        if (!p.cfg.detEnabled || pl.hasPermission("chronos.bypass.detect") || ignoredWorld(pl)) return;
        long now = System.currentTimeMillis();
        Stat s = stats.computeIfAbsent(pl.getUniqueId(), k -> new Stat());
        String alert = null, type = null;
        synchronized (s) {
            if (pl.getGameMode() != GameMode.CREATIVE) {
                s.breaks.addLast(now);
                while (!s.breaks.isEmpty() && now - s.breaks.peekFirst() > p.cfg.massSeconds * 1000L) s.breaks.pollFirst();
                if (s.breaks.size() >= p.cfg.massBlocks && now - s.lastMass > 60_000) {
                    s.lastMass = now;
                    type = p.lang.raw("alert.type_mass");
                    alert = p.lang.t("alert.detail_mass", "count", s.breaks.size(), "seconds", p.cfg.massSeconds);
                }
            }
            Material m = b.getType();
            boolean ore = ORES.contains(m), stone = STONES.contains(m);
            if (alert == null && (ore || stone)) {
                if (now - s.windowStart > p.cfg.xrayWindowMin * 60_000L) { s.windowStart = now; s.stone = s.ores = s.hidden = 0; }
                if (ore) { s.ores++; if (exposedFaces(b) <= 1) s.hidden++; } else s.stone++;
                if (s.ores >= p.cfg.xrayMinOres && now - s.lastXray > p.cfg.alertCooldownSec * 1000L) {
                    double ratio = s.ores / (double) (s.ores + s.stone);
                    if (ratio >= p.cfg.xrayRatio || s.hidden >= p.cfg.xrayHidden) {
                        type = p.lang.raw("alert.type_xray");
                        alert = p.lang.t("alert.detail_xray", "ores", s.ores, "total", s.ores + s.stone, "percent", Math.round(ratio * 100),
                                "hidden", s.hidden, "minutes", p.cfg.xrayWindowMin);
                        s.lastXray = now;
                        s.stone = s.ores = s.hidden = 0;
                    }
                }
            }
        }
        if (alert != null) alert(type, pl, alert, b.getLocation());
    }

    public void onContainer(Player pl, Location loc, int removed, int stacksBefore, int stacksAfter) {
        if (!p.cfg.detEnabled || pl.hasPermission("chronos.bypass.detect") || ignoredWorld(pl)) return;
        boolean big = removed >= p.cfg.contItems;
        boolean emptied = stacksBefore >= p.cfg.contStacks && stacksAfter == 0;
        if (!big && !emptied) return;
        Stat s = stats.computeIfAbsent(pl.getUniqueId(), k -> new Stat());
        long now = System.currentTimeMillis();
        synchronized (s) {
            if (now - s.lastCont < 30_000) return;
            s.lastCont = now;
        }
        alert(p.lang.raw(emptied ? "alert.type_emptied" : "alert.type_bulk"), pl,
                p.lang.t("alert.detail_container", "removed", removed, "before", stacksBefore, "after", stacksAfter), loc);
    }

    private void alert(String type, Player pl, String detail, Location l) {
        String where = l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
        String plain = p.lang.t("alert.plain", "type", type, "player", pl.getName(), "detail", detail, "where", where);
        p.getLogger().warning(plain);
        String mini = p.lang.t("alert.format", "type", type, "player", pl.getName(), "detail", detail, "where", where);
        for (Player staff : p.getServer().getOnlinePlayers()) {
            if (!staff.hasPermission("chronos.alerts") || muted.contains(staff.getUniqueId())) continue;
            p.sched.entity(staff, () -> Msg.raw(staff, mini));
        }
        discord.send(plain);
    }
}
