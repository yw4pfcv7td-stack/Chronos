package dev.chronos.rollback;

import dev.chronos.Chronos;
import dev.chronos.util.Msg;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Selections {
    public static final class Sel {
        public String world;
        public int[] p1, p2;
    }

    private final Chronos plugin;
    private final Map<UUID, Sel> map = new ConcurrentHashMap<>();

    public Selections(Chronos plugin) { this.plugin = plugin; }

    public void set(Player pl, int which, Block b) {
        Sel s = map.computeIfAbsent(pl.getUniqueId(), k -> new Sel());
        if (s.world != null && !s.world.equals(b.getWorld().getName())) { s.p1 = null; s.p2 = null; }
        s.world = b.getWorld().getName();
        int[] pos = {b.getX(), b.getY(), b.getZ()};
        if (which == 1) s.p1 = pos; else s.p2 = pos;
        Msg.send(pl, plugin.lang.t("sel.set", "n", which, "pos", pos[0] + ", " + pos[1] + ", " + pos[2]));
    }

    public Sel get(Player pl) {
        Sel s = map.get(pl.getUniqueId());
        return (s == null || s.p1 == null || s.p2 == null) ? null : s;
    }

    public void clear(Player pl) { map.remove(pl.getUniqueId()); }
}
