package dev.chronos.command;

import dev.chronos.Chronos;
import dev.chronos.model.ActionType;
import dev.chronos.rollback.Selections;
import dev.chronos.storage.QuerySpec;
import dev.chronos.util.TimeUtil;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** Parametri: u: xu: b: xb: t: r: a: i: w: at: p: now */
public final class ParamParser {
    private ParamParser() { }

    private static void addAll(List<String> target, String csv) {
        for (String s : csv.split(",")) {
            String v = s.trim().toLowerCase(Locale.ROOT);
            if (v.startsWith("minecraft:")) v = v.substring(10);
            if (!v.isEmpty()) target.add(v);
        }
    }

    /** Lancia IllegalArgumentException con un messaggio gia' tradotto. */
    public static QuerySpec parse(Chronos p, CommandSender sender, List<String> tokens) {
        QuerySpec s = new QuerySpec();
        Player pl = sender instanceof Player x ? x : null;
        String radius = null;
        int[] at = null;
        for (String t : tokens) {
            if (t.equalsIgnoreCase("now")) { s.now = true; continue; }
            int i = t.indexOf(':');
            if (i < 0) throw new IllegalArgumentException(p.lang.t("param.invalid", "token", t));
            String k = t.substring(0, i).toLowerCase(Locale.ROOT), v = t.substring(i + 1);
            try {
                switch (k) {
                    case "u", "user" -> addAll(s.users, v);
                    case "xu" -> addAll(s.xUsers, v);
                    case "b", "block" -> addAll(s.blocks, v);
                    case "xb" -> addAll(s.xBlocks, v);
                    case "t", "time" -> {
                        long d = TimeUtil.parse(v);
                        if (d < 0) throw new IllegalArgumentException(p.lang.t("param.time", "value", v));
                        s.since = System.currentTimeMillis() - d;
                    }
                    case "r", "radius" -> radius = v;
                    case "a", "action" -> {
                        for (String part : v.split(",")) {
                            List<ActionType> l = ActionType.parse(part);
                            if (l.isEmpty()) throw new IllegalArgumentException(p.lang.t("param.action", "value", part));
                            s.types.addAll(l);
                        }
                    }
                    case "i", "incident" -> s.incident = Long.parseLong(v.replace("#", ""));
                    case "w", "world" -> s.world = v;
                    case "p", "page" -> s.page = Math.max(1, Integer.parseInt(v));
                    case "at" -> {
                        String[] c = v.split(",");
                        if (c.length != 3) throw new IllegalArgumentException(p.lang.t("param.at"));
                        at = new int[]{Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])};
                    }
                    default -> throw new IllegalArgumentException(p.lang.t("param.unknown", "key", k));
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(p.lang.t("param.number", "token", t));
            }
        }
        if (at != null) {
            if (s.world == null && pl != null) s.world = pl.getWorld().getName();
            if (s.world == null) throw new IllegalArgumentException(p.lang.t("param.world"));
            s.box = true;
            s.minX = s.maxX = at[0]; s.minY = s.maxY = at[1]; s.minZ = s.maxZ = at[2];
        } else if (radius != null) {
            if (radius.equalsIgnoreCase("sel")) {
                if (pl == null) throw new IllegalArgumentException(p.lang.t("param.sel_player"));
                Selections.Sel sel = p.selections.get(pl);
                if (sel == null) throw new IllegalArgumentException(p.lang.t("param.sel_none"));
                s.world = sel.world;
                s.box = true;
                s.minX = Math.min(sel.p1[0], sel.p2[0]); s.maxX = Math.max(sel.p1[0], sel.p2[0]);
                s.minY = Math.min(sel.p1[1], sel.p2[1]); s.maxY = Math.max(sel.p1[1], sel.p2[1]);
                s.minZ = Math.min(sel.p1[2], sel.p2[2]); s.maxZ = Math.max(sel.p1[2], sel.p2[2]);
            } else {
                if (pl == null) throw new IllegalArgumentException(p.lang.t("param.radius_player"));
                int r;
                try { r = Integer.parseInt(radius); } catch (NumberFormatException e) { throw new IllegalArgumentException(p.lang.t("param.radius_invalid", "value", radius)); }
                if (r < 0 || r > p.cfg.maxRadius) throw new IllegalArgumentException(p.lang.t("param.radius_max", "max", p.cfg.maxRadius));
                Location l = pl.getLocation();
                s.world = pl.getWorld().getName();
                s.box = true;
                s.minX = l.getBlockX() - r; s.maxX = l.getBlockX() + r;
                s.minY = Math.max(pl.getWorld().getMinHeight(), l.getBlockY() - r); s.maxY = Math.min(pl.getWorld().getMaxHeight(), l.getBlockY() + r);
                s.minZ = l.getBlockZ() - r; s.maxZ = l.getBlockZ() + r;
            }
        }
        return s;
    }
}
