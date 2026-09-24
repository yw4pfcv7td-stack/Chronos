package dev.chronos.command;

import dev.chronos.Chronos;
import dev.chronos.model.ActionRow;
import dev.chronos.model.ActionType;
import dev.chronos.storage.QuerySpec;
import dev.chronos.util.Msg;
import dev.chronos.util.TimeUtil;
import org.bukkit.command.CommandSender;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class Fmt {
    private Fmt() { }

    public static String row(Chronos p, ActionRow r, boolean coords, boolean tpClick) {
        String col = switch (r.type()) {
            case BLOCK_BREAK -> "<red>";
            case BLOCK_PLACE -> "<green>";
            case CONTAINER_ADD -> "<aqua>";
            case CONTAINER_REMOVE -> "<gold>";
            case KILL -> "<dark_red>";
        };
        String what = (r.type().isContainer() || r.amount() > 1 ? r.amount() + "x " : "") + r.material();
        if (r.type() == ActionType.KILL && r.oldData() != null) what += " (" + r.oldData() + ")";
        String s = "<gray>" + p.lang.t("time.ago", "time", TimeUtil.ago(r.ts())) + " <white>" + Msg.esc(r.user()) + " " + col
                + p.lang.raw("verb." + r.type().key) + " <aqua>" + Msg.esc(what);
        if (coords) s += " <dark_gray>(" + r.x() + "," + r.y() + "," + r.z() + ")";
        if (r.incident() > 0) s += " <gold>#" + r.incident();
        if (r.rolledBack()) s += " " + p.lang.raw("row.rolledback");
        String hover = "<gray>" + TimeUtil.fmt(r.ts()) + "\n" + Msg.esc(r.world()) + " " + r.x() + " " + r.y() + " " + r.z();
        s = "<hover:show_text:'" + hover.replace("'", "") + "'>" + s + "</hover>";
        if (tpClick) s = "<click:run_command:'/chronos tp " + r.world() + " " + r.x() + " " + r.y() + " " + r.z() + "'>" + s + "</click>";
        return s;
    }

    /** Cronologia di un singolo blocco, in chat. */
    public static void history(Chronos p, CommandSender to, String world, int x, int y, int z, int page) {
        QuerySpec s = p.queries.blockSpec(world, x, y, z);
        list(p, to, s, page, "/chronos list at:" + x + "," + y + "," + z + " w:" + world,
                p.lang.t("history.title", "x", x, "y", y, "z", z));
    }

    /** Elenco paginato in chat. baseCmd: comando senza p:. title: gia' testo semplice. */
    public static void list(Chronos p, CommandSender to, QuerySpec spec, int page, String baseCmd, String title) {
        int pg = Math.max(1, page);
        int size = p.cfg.pageSize;
        CompletableFuture.supplyAsync(() -> {
            try {
                p.queue.awaitDrain(2000);
                int total = p.queries.count(spec);
                List<ActionRow> rows = p.queries.rows(spec, false, false, size, (pg - 1) * size);
                return new Object[]{total, rows};
            } catch (SQLException e) { throw new RuntimeException(e); }
        }, p.dbPool).whenComplete((res, err) -> {
            if (err != null) {
                p.getLogger().log(Level.SEVERE, "Lookup error", err);
                p.tell(to, p.lang.t("error.db"));
                return;
            }
            @SuppressWarnings("unchecked") List<ActionRow> rows = (List<ActionRow>) res[1];
            int total = (Integer) res[0];
            int pages = Math.max(1, (total + size - 1) / size);
            Runnable r = () -> {
                Msg.raw(to, p.lang.t("list.header", "title", title, "total", total, "page", pg, "pages", pages));
                if (rows.isEmpty()) Msg.raw(to, p.lang.raw("list.empty"));
                boolean tp = to.hasPermission("chronos.teleport");
                for (ActionRow row : rows) Msg.raw(to, row(p, row, true, tp));
                String nav = "";
                if (pg > 1) nav += "<aqua><click:run_command:'" + baseCmd + " p:" + (pg - 1) + "'>" + p.lang.raw("list.prev") + "</click></aqua> ";
                if (pg < pages) nav += "<aqua><click:run_command:'" + baseCmd + " p:" + (pg + 1) + "'>" + p.lang.raw("list.next") + "</click></aqua>";
                if (!nav.isEmpty()) Msg.raw(to, nav);
            };
            if (to instanceof org.bukkit.entity.Player pl) p.sched.entity(pl, r); else p.sched.global(r);
        });
    }
}
