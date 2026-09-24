package dev.chronos.listener;

import dev.chronos.Chronos;
import dev.chronos.model.ActionType;
import dev.chronos.model.Actor;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Registra i prelievi/inserimenti confrontando il contenuto all'apertura e alla chiusura. */
public final class ContainerListener implements Listener {
    private record Session(Location loc, ItemStack[] before) { }

    private final Chronos p;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public ContainerListener(Chronos p) { this.p = p; }

    private static Location locationOf(Inventory inv) {
        InventoryHolder h = inv.getHolder(false);
        if (h instanceof DoubleChest dc) {
            InventoryHolder left = dc.getLeftSide();
            if (left instanceof BlockState bs) return bs.getLocation();
            return null;
        }
        if (h instanceof Container c) return c.getLocation();
        return null;
    }

    private static ItemStack[] clone(ItemStack[] src) {
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) out[i] = src[i] == null ? null : src[i].clone();
        return out;
    }

    private static Map<ItemStack, Integer> tally(ItemStack[] items) {
        Map<ItemStack, Integer> m = new HashMap<>();
        for (ItemStack i : items) {
            if (i == null || i.getType().isAir()) continue;
            m.merge(i.asOne(), i.getAmount(), Integer::sum);
        }
        return m;
    }

    private static int stacks(ItemStack[] items) {
        int n = 0;
        for (ItemStack i : items) if (i != null && !i.getType().isAir()) n++;
        return n;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent e) {
        if (!p.cfg.logContainers || !(e.getPlayer() instanceof Player pl)) return;
        Location loc = locationOf(e.getInventory());
        if (loc == null || p.actions.ignored(loc.getWorld())) return;
        sessions.put(pl.getUniqueId(), new Session(loc, clone(e.getInventory().getContents())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player pl)) return;
        Session s = sessions.remove(pl.getUniqueId());
        if (s == null) return;
        ItemStack[] afterItems = e.getInventory().getContents();
        Map<ItemStack, Integer> before = tally(s.before()), after = tally(afterItems);
        Set<ItemStack> keys = new HashSet<>(before.keySet());
        keys.addAll(after.keySet());
        Actor a = Actor.of(pl);
        int removed = 0;
        for (ItemStack k : keys) {
            int d = after.getOrDefault(k, 0) - before.getOrDefault(k, 0);
            if (d > 0) p.actions.container(a, s.loc(), ActionType.CONTAINER_ADD, k, d);
            else if (d < 0) {
                p.actions.container(a, s.loc(), ActionType.CONTAINER_REMOVE, k, -d);
                removed += -d;
            }
        }
        if (removed > 0) p.monitor.onContainer(pl, s.loc(), removed, stacks(s.before()), stacks(afterItems));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { sessions.remove(e.getPlayer().getUniqueId()); }
}
