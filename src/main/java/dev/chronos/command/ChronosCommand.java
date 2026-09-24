package dev.chronos.command;

import dev.chronos.Chronos;
import dev.chronos.ChronosConfig;
import dev.chronos.gui.LookupGui;
import dev.chronos.model.IncidentInfo;
import dev.chronos.rollback.RollbackService;
import dev.chronos.snapshot.SnapshotService;
import dev.chronos.storage.QuerySpec;
import dev.chronos.util.Lang;
import dev.chronos.util.Msg;
import dev.chronos.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class ChronosCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("help", "inspect", "lookup", "list", "rollback", "restore", "confirm",
            "cancel", "undo", "redo", "wand", "incidents", "snapshot", "alerts", "status", "purge", "reload");
    private static final List<String> KEYS = List.of("u:", "xu:", "b:", "xb:", "t:", "r:", "a:", "i:", "w:", "at:", "now");

    private final Chronos p;

    public ChronosCommand(Chronos p) { this.p = p; }

    private boolean need(CommandSender s, String perm) {
        if (s.hasPermission(perm)) return true;
        Msg.send(s, p.lang.t("error.perm", "perm", perm));
        return false;
    }

    private Player playerOnly(CommandSender s) {
        if (s instanceof Player pl) return pl;
        Msg.send(s, p.lang.t("error.player_only"));
        return null;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length == 0) { help(s); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> rest = Arrays.asList(args).subList(1, args.length);
        switch (sub) {
            case "help", "?" -> help(s);
            case "inspect", "i" -> {
                Player pl = playerOnly(s);
                if (pl == null || !need(s, "chronos.inspect")) return true;
                boolean on = p.inspector.toggle(pl.getUniqueId());
                Msg.send(s, p.lang.t(on ? "inspect.on" : "inspect.off"));
            }
            case "lookup", "l" -> {
                if (!need(s, "chronos.lookup")) return true;
                QuerySpec spec = parse(s, rest);
                if (spec == null) return true;
                if (s instanceof Player pl && p.cfg.lookupGui) LookupGui.open(p, pl, spec);
                else Fmt.list(p, s, spec, spec.page, "/chronos list " + strip(rest), p.lang.raw("list.title"));
            }
            case "list" -> {
                if (!need(s, "chronos.lookup")) return true;
                QuerySpec spec = parse(s, rest);
                if (spec == null) return true;
                Fmt.list(p, s, spec, spec.page, "/chronos list " + strip(rest), p.lang.raw("list.title"));
            }
            case "rollback", "rb", "restore", "rs" -> {
                if (!need(s, "chronos.rollback")) return true;
                QuerySpec spec = parse(s, rest);
                if (spec == null) return true;
                p.rollback.begin(s, spec, sub.startsWith("rs") || sub.equals("restore"));
            }
            case "confirm" -> { Player pl = playerOnly(s); if (pl != null && need(s, "chronos.rollback")) p.preview.confirm(pl); }
            case "cancel" -> { Player pl = playerOnly(s); if (pl != null) p.preview.cancel(pl, false); }
            case "undo" -> { if (need(s, "chronos.rollback")) p.rollback.undo(s); }
            case "redo" -> { if (need(s, "chronos.rollback")) p.rollback.redo(s); }
            case "wand" -> {
                Player pl = playerOnly(s);
                if (pl == null || !need(s, "chronos.rollback")) return true;
                pl.getInventory().addItem(p.inspector.wand());
                Msg.send(s, p.lang.t("wand.given"));
            }
            case "incidents" -> { if (need(s, "chronos.lookup")) incidents(s, rest); }
            case "snapshot", "snap" -> { if (need(s, "chronos.snapshot")) snapshot(s, rest); }
            case "alerts" -> {
                Player pl = playerOnly(s);
                if (pl == null || !need(s, "chronos.alerts")) return true;
                Msg.send(s, p.lang.t(p.monitor.toggleAlerts(pl.getUniqueId()) ? "alerts.on" : "alerts.off"));
            }
            case "status" -> {
                if (!need(s, "chronos.admin")) return true;
                Msg.send(s, p.lang.t("status", "db", p.db.type, "queue", p.queue.size(), "written", p.queue.written(),
                        "dropped", p.queue.dropped(), "undo", p.undo.undoSize(RollbackService.owner(s))));
            }
            case "purge" -> {
                if (!need(s, "chronos.admin")) return true;
                if (rest.isEmpty()) { Msg.send(s, p.lang.t("purge.usage")); return true; }
                long ms = TimeUtil.parse(rest.get(0));
                if (ms < 0) { Msg.send(s, p.lang.t("param.time", "value", rest.get(0))); return true; }
                Msg.send(s, p.lang.t("purge.started"));
                p.sched.async(() -> {
                    p.retention.run(ms, ms);
                    p.tell(s, p.lang.t("purge.done"));
                });
            }
            case "reload" -> {
                if (!need(s, "chronos.admin")) return true;
                p.reloadConfig();
                p.cfg = new ChronosConfig(p.getConfig());
                p.lang = new Lang(p, p.cfg.language);
                Msg.send(s, p.lang.t("reload.done"));
            }
            case "tp" -> {
                Player pl = playerOnly(s);
                if (pl == null || !need(s, "chronos.teleport") || rest.size() < 4) return true;
                World w = Bukkit.getWorld(rest.get(0));
                if (w == null) return true;
                try {
                    pl.teleportAsync(new Location(w, Integer.parseInt(rest.get(1)) + 0.5, Integer.parseInt(rest.get(2)) + 1, Integer.parseInt(rest.get(3)) + 0.5));
                } catch (NumberFormatException ignored) { }
            }
            default -> Msg.send(s, p.lang.t("error.unknown"));
        }
        return true;
    }

    /** Toglie il token p: per costruire i link di paginazione. */
    private static String strip(List<String> tokens) {
        return tokens.stream().filter(t -> !t.toLowerCase(Locale.ROOT).startsWith("p:")).collect(Collectors.joining(" "));
    }

    private QuerySpec parse(CommandSender s, List<String> tokens) {
        try {
            return ParamParser.parse(p, s, tokens);
        } catch (IllegalArgumentException e) {
            Msg.send(s, "<red>" + Msg.esc(e.getMessage()));
            return null;
        }
    }

    private void incidents(CommandSender s, List<String> rest) {
        QuerySpec spec = parse(s, rest);
        if (spec == null) return;
        p.dbPool.execute(() -> {
            try {
                p.queue.awaitDrain(2000);
                List<IncidentInfo> list = p.queries.incidents(spec, 15);
                Runnable r = () -> {
                    Msg.raw(s, p.lang.raw("incidents.header"));
                    if (list.isEmpty()) Msg.raw(s, p.lang.raw("incidents.none"));
                    for (IncidentInfo i : list) {
                        Msg.raw(s, p.lang.t("incidents.row", "id", i.id(), "kind", i.kind(), "user", i.user(), "actions", i.actions(), "ago", TimeUtil.ago(i.ts()))
                                + " <aqua><click:run_command:'/chronos lookup i:" + i.id() + "'>" + p.lang.raw("incidents.view") + "</click></aqua> "
                                + "<red><click:run_command:'/chronos rollback i:" + i.id() + "'><hover:show_text:'" + p.lang.raw("incidents.rollback_hover")
                                + "'>" + p.lang.raw("incidents.rollback") + "</hover></click></red>");
                    }
                };
                if (s instanceof Player pl) p.sched.entity(pl, r); else p.sched.global(r);
            } catch (Exception e) {
                p.getLogger().log(Level.SEVERE, "Incidents error", e);
            }
        });
    }

    private void snapshot(CommandSender s, List<String> a) {
        if (a.isEmpty()) { Msg.send(s, p.lang.t("snap.usage")); return; }
        String op = a.get(0).toLowerCase(Locale.ROOT);
        if (a.size() < 2) { Msg.send(s, p.lang.t("snap.need_player")); return; }
        String name = a.get(1);
        switch (op) {
            case "list" -> p.snapshots.list(name, 12).whenComplete((list, err) -> {
                if (err != null) { p.tell(s, p.lang.t("error.db")); return; }
                Runnable r = () -> {
                    Msg.raw(s, p.lang.t("snap.list_header", "player", name));
                    if (list.isEmpty()) Msg.raw(s, p.lang.raw("snap.list_none"));
                    for (SnapshotService.Info i : list) {
                        Msg.raw(s, p.lang.t("snap.list_row", "id", i.id(), "reason", p.lang.raw("snap.reason_" + i.reason()), "ago", TimeUtil.ago(i.ts()),
                                "level", i.level(), "world", i.world(), "x", i.x(), "y", i.y(), "z", i.z())
                                + " <green><click:suggest_command:'/chronos snapshot restore " + Msg.esc(name) + " " + i.id() + " all'>"
                                + p.lang.raw("snap.restore_button") + "</click>");
                    }
                };
                if (s instanceof Player pl) p.sched.entity(pl, r); else p.sched.global(r);
            });
            case "take" -> {
                Player t = Bukkit.getPlayerExact(name);
                if (t == null) { Msg.send(s, p.lang.t("snap.offline")); return; }
                p.sched.entity(t, () -> p.snapshots.capture(t, SnapshotService.MANUAL));
                Msg.send(s, p.lang.t("snap.queued"));
            }
            case "restore" -> {
                Player t = Bukkit.getPlayerExact(name);
                if (t == null) { Msg.send(s, p.lang.t("snap.offline_restore")); return; }
                String sel = a.size() > 2 ? a.get(2) : "last";
                Set<String> parts = new HashSet<>();
                String what = a.size() > 3 ? a.get(3).toLowerCase(Locale.ROOT) : "all";
                if (what.equals("all")) parts.addAll(List.of("inv", "ender", "xp")); else parts.add(what);
                p.snapshots.find(name, sel).whenComplete((d, err) -> {
                    if (err != null) { p.tell(s, p.lang.t("error.db")); return; }
                    if (d == null) { p.tell(s, p.lang.t("snap.notfound")); return; }
                    p.sched.entity(t, () -> p.snapshots.restore(t, d, parts, s));
                });
            }
            default -> Msg.send(s, p.lang.t("snap.unknown_op"));
        }
    }

    private void help(CommandSender s) {
        for (String line : p.lang.raw("help").split("\n")) Msg.raw(s, line);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length == 1) return SUBS.stream().filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        String sub = args[0].toLowerCase(Locale.ROOT);
        String cur = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (List.of("lookup", "l", "list", "rollback", "rb", "restore", "rs", "incidents").contains(sub)) {
            if (cur.startsWith("u:") || cur.startsWith("xu:")) {
                String pre = cur.substring(0, cur.indexOf(':') + 1);
                return Bukkit.getOnlinePlayers().stream().map(x -> pre + x.getName()).filter(x -> x.toLowerCase(Locale.ROOT).startsWith(cur)).toList();
            }
            if (cur.startsWith("a:")) return List.of("a:break", "a:place", "a:add", "a:remove", "a:kill", "a:block", "a:container");
            if (cur.startsWith("r:")) return List.of("r:10", "r:30", "r:100", "r:sel");
            if (cur.startsWith("t:")) return List.of("t:30m", "t:1h", "t:6h", "t:1d", "t:7d");
            return KEYS.stream().filter(k -> k.startsWith(cur)).toList();
        }
        if (List.of("snapshot", "snap").contains(sub)) {
            if (args.length == 2) return List.of("list", "take", "restore");
            if (args.length == 3) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            if (args.length == 4) return List.of("last", "death");
            if (args.length == 5) return List.of("all", "inv", "ender", "xp");
        }
        return List.of();
    }
}
