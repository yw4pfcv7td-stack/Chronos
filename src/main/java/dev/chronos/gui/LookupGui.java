package dev.chronos.gui;

import dev.chronos.Chronos;
import dev.chronos.command.Fmt;
import dev.chronos.model.ActionRow;
import dev.chronos.model.ActionType;
import dev.chronos.storage.QuerySpec;
import dev.chronos.util.Msg;
import dev.chronos.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/** Lookup con menu paginato e filtri a click. */
public final class LookupGui implements InventoryHolder {
    private static final int SIZE = 45;
    private static final ActionType[] CYCLE = {null, ActionType.BLOCK_BREAK, ActionType.BLOCK_PLACE,
            ActionType.CONTAINER_ADD, ActionType.CONTAINER_REMOVE, ActionType.KILL};
    private static final long[] TIMES = {3_600_000L, 6 * 3_600_000L, 86_400_000L, 7 * 86_400_000L, 30 * 86_400_000L, 0};

    private final Chronos p;
    private final Player player;
    private final QuerySpec spec;
    private final Inventory inv;
    private int page = 0, total = 0, timeIdx = -1;
    private List<ActionRow> rows = List.of();

    private LookupGui(Chronos p, Player player, QuerySpec spec) {
        this.p = p;
        this.player = player;
        this.spec = spec;
        this.inv = Bukkit.createInventory(this, 54, Msg.c(p.lang.raw("gui.title")));
    }

    public static void open(Chronos p, Player pl, QuerySpec spec) {
        LookupGui g = new LookupGui(p, pl, spec.copy());
        g.load(true);
    }

    @Override public Inventory getInventory() { return inv; }

    private void load(boolean openNow) {
        CompletableFuture.supplyAsync(() -> {
            try {
                p.queue.awaitDrain(2000);
                int t = p.queries.count(spec);
                return new Object[]{t, p.queries.rows(spec, false, false, SIZE, page * SIZE)};
            } catch (SQLException e) { throw new RuntimeException(e); }
        }, p.dbPool).whenComplete((res, err) -> {
            if (err != null) {
                p.getLogger().log(Level.SEVERE, "Lookup GUI error", err);
                p.tell(player, p.lang.t("error.db"));
                return;
            }
            @SuppressWarnings("unchecked") List<ActionRow> r = (List<ActionRow>) res[1];
            total = (Integer) res[0];
            rows = r;
            p.sched.entity(player, () -> {
                render();
                if (openNow) player.openInventory(inv);
            });
        });
    }

    private ItemStack named(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.item(name));
        List<net.kyori.adventure.text.Component> l = new ArrayList<>();
        for (String s : lore) l.add(Msg.item(s));
        meta.lore(l);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack icon(ActionRow r) {
        Material m = Material.matchMaterial(r.material());
        if (r.type() == ActionType.KILL) m = Material.IRON_SWORD;
        else if (r.material().equals("water")) m = Material.WATER_BUCKET;
        else if (r.material().equals("lava")) m = Material.LAVA_BUCKET;
        else if (r.material().equals("fire")) m = Material.FLINT_AND_STEEL;
        if (m == null || !m.isItem() || m.isAir()) m = Material.PAPER;
        String col = switch (r.type()) {
            case BLOCK_BREAK -> "<red>";
            case BLOCK_PLACE -> "<green>";
            case CONTAINER_ADD -> "<aqua>";
            case CONTAINER_REMOVE -> "<gold>";
            case KILL -> "<dark_red>";
        };
        List<String> lore = new ArrayList<>();
        lore.add(p.lang.t("gui.lore_player", "v", r.user()));
        lore.add(p.lang.t("gui.lore_when", "ago", TimeUtil.ago(r.ts()), "date", TimeUtil.fmt(r.ts())));
        lore.add(p.lang.t("gui.lore_pos", "world", r.world(), "x", r.x(), "y", r.y(), "z", r.z()));
        if (r.amount() > 1) lore.add(p.lang.t("gui.lore_amount", "v", r.amount()));
        if (r.incident() > 0) lore.add(p.lang.t("gui.lore_incident", "v", r.incident()));
        if (r.rolledBack()) lore.add(p.lang.raw("gui.lore_rolledback"));
        lore.add("");
        lore.add(p.lang.raw("gui.click_tp"));
        lore.add(p.lang.raw("gui.click_user"));
        lore.add(p.lang.raw("gui.click_block"));
        if (r.incident() > 0) lore.add(p.lang.raw("gui.click_incident"));
        return named(m, col + p.lang.raw("verb." + r.type().key) + " <aqua>" + Msg.esc(r.material()), lore.toArray(new String[0]));
    }

    private void render() {
        int pages = Math.max(1, (total + SIZE - 1) / SIZE);
        inv.clear();
        for (int i = 0; i < rows.size() && i < SIZE; i++) inv.setItem(i, icon(rows.get(i)));
        for (int i = 45; i < 54; i++) inv.setItem(i, named(Material.GRAY_STAINED_GLASS_PANE, " "));
        if (page > 0) inv.setItem(45, named(Material.ARROW, p.lang.raw("gui.prev")));
        ActionType cur = spec.types.size() == 1 ? spec.types.iterator().next() : null;
        inv.setItem(46, named(Material.HOPPER, p.lang.t("gui.filter_action", "v", cur == null ? p.lang.raw("gui.all") : p.lang.raw("verb." + cur.key)),
                p.lang.raw("gui.click_change")));
        String timeLabel = timeIdx >= 0 ? p.lang.raw("gui.time_" + timeIdx) : (spec.since >= 0 ? p.lang.raw("gui.custom") : p.lang.raw("gui.time_5"));
        inv.setItem(47, named(Material.CLOCK, p.lang.t("gui.filter_time", "v", timeLabel), p.lang.raw("gui.click_change")));
        inv.setItem(48, named(Material.BARRIER, p.lang.raw("gui.reset"),
                p.lang.t("gui.reset_users", "v", spec.users.isEmpty() ? "-" : String.join(",", spec.users)),
                p.lang.t("gui.reset_blocks", "v", spec.blocks.isEmpty() ? "-" : String.join(",", spec.blocks)),
                p.lang.t("gui.reset_incident", "v", spec.incident >= 0 ? "#" + spec.incident : "-")));
        inv.setItem(49, named(Material.PAPER, p.lang.t("gui.page", "n", page + 1, "total", pages)));
        inv.setItem(50, named(Material.TNT_MINECART, p.lang.raw("gui.rollback"), p.lang.raw("gui.rollback_hint")));
        inv.setItem(51, named(Material.EMERALD, p.lang.raw("gui.restore"), p.lang.raw("gui.restore_hint")));
        inv.setItem(52, named(Material.RED_STAINED_GLASS_PANE, p.lang.raw("gui.close")));
        if (page + 1 < pages) inv.setItem(53, named(Material.ARROW, p.lang.raw("gui.next")));
    }

    public void click(InventoryClickEvent e) {
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        if (slot < SIZE) {
            if (slot >= rows.size()) return;
            ActionRow r = rows.get(slot);
            switch (e.getClick()) {
                case LEFT -> {
                    if (!player.hasPermission("chronos.teleport")) return;
                    World w = Bukkit.getWorld(r.world());
                    if (w != null) { player.closeInventory(); player.teleportAsync(new Location(w, r.x() + 0.5, r.y() + 1, r.z() + 0.5)); }
                }
                case SHIFT_LEFT -> { spec.users.clear(); spec.users.add(r.user().toLowerCase(Locale.ROOT)); page = 0; load(false); }
                case RIGHT -> { spec.blocks.clear(); spec.blocks.add(r.material()); page = 0; load(false); }
                case SHIFT_RIGHT -> { if (r.incident() > 0) { spec.incident = r.incident(); page = 0; load(false); } }
                default -> { }
            }
            return;
        }
        switch (slot) {
            case 45 -> { if (page > 0) { page--; load(false); } }
            case 53 -> { if ((page + 1) * SIZE < total) { page++; load(false); } }
            case 46 -> {
                ActionType cur = spec.types.size() == 1 ? spec.types.iterator().next() : null;
                int idx = 0;
                for (int i = 0; i < CYCLE.length; i++) if (CYCLE[i] == cur) idx = i;
                ActionType next = CYCLE[(idx + 1) % CYCLE.length];
                spec.types.clear();
                if (next != null) spec.types.add(next);
                page = 0;
                load(false);
            }
            case 47 -> {
                timeIdx = (timeIdx + 1) % TIMES.length;
                spec.since = TIMES[timeIdx] == 0 ? -1 : System.currentTimeMillis() - TIMES[timeIdx];
                page = 0;
                load(false);
            }
            case 48 -> {
                spec.users.clear(); spec.xUsers.clear(); spec.blocks.clear(); spec.xBlocks.clear();
                spec.types.clear(); spec.incident = -1; page = 0;
                load(false);
            }
            case 50, 51 -> {
                if (!player.hasPermission("chronos.rollback")) { Msg.send(player, p.lang.t("error.perm", "perm", "chronos.rollback")); return; }
                player.closeInventory();
                p.rollback.begin(player, spec.copy(), slot == 51);
            }
            case 52 -> player.closeInventory();
            default -> { }
        }
    }
}
