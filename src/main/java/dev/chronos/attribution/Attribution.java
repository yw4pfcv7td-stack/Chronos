package dev.chronos.attribution;

import dev.chronos.model.Actor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Risale alla causa di fuoco, lava/acqua, TNT a catena, cristalli, Wither...
 * Le cause vivono in memoria per poco tempo e si propagano di blocco in blocco / di entita' in entita'.
 */
public final class Attribution {
    private static final long WINDOW = 15_000L;          // raggruppamento nello stesso incidente
    private static final long BLOCK_TTL = 5 * 60_000L;
    private static final long ENTITY_TTL = 10 * 60_000L;
    private static final int MAX_BLOCKS = 300_000;
    private static final BlockFace[] FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private record Explosion(UUID world, double x, double y, double z, Cause cause, long expires) { }

    private final Map<UUID, Map<Long, Cause>> blocks = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Long, Actor>> owners = new ConcurrentHashMap<>();
    private final Map<UUID, Cause> entities = new ConcurrentHashMap<>();
    private final Map<String, Cause> recent = new ConcurrentHashMap<>();
    private final Map<String, Cause> shared = new ConcurrentHashMap<>();
    private final Deque<Explosion> explosions = new ConcurrentLinkedDeque<>();
    private final AtomicInteger blockCount = new AtomicInteger();

    /** Causa di un giocatore; azioni ravvicinate dello stesso tipo finiscono nello stesso incidente. */
    public Cause player(Player pl, String kind) {
        String k = pl.getUniqueId() + ":" + kind;
        Cause c = recent.get(k);
        if (c == null || c.expired()) {
            c = new Cause(Actor.of(pl), kind.toUpperCase(Locale.ROOT), true, WINDOW);
            recent.put(k, c);
        } else {
            c.touch(WINDOW);
        }
        return c;
    }

    /** Causa ambientale. incident=false: causa condivisa senza incidenti (es. fuoco naturale). */
    public Cause env(String kind, boolean incident) {
        String n = kind.toLowerCase(Locale.ROOT);
        if (!incident) {
            return shared.computeIfAbsent(n, k -> new Cause(Actor.env(k), k.toUpperCase(Locale.ROOT), false, Long.MAX_VALUE / 4));
        }
        String k = "env:" + n;
        Cause c = recent.get(k);
        if (c == null || c.expired()) {
            c = new Cause(Actor.env(n), n.toUpperCase(Locale.ROOT), true, WINDOW);
            recent.put(k, c);
        } else {
            c.touch(WINDOW);
        }
        return c;
    }

    // ------- blocchi (fuoco, fluidi) -------
    public void put(Block b, Cause c) {
        if (blockCount.get() > MAX_BLOCKS) return;
        Map<Long, Cause> m = blocks.computeIfAbsent(b.getWorld().getUID(), k -> new ConcurrentHashMap<>());
        if (m.put(b.getBlockKey(), c) == null) blockCount.incrementAndGet();
        c.touch(BLOCK_TTL);
    }

    public Cause get(Block b) {
        Map<Long, Cause> m = blocks.get(b.getWorld().getUID());
        if (m == null) return null;
        long key = b.getBlockKey();
        Cause c = m.get(key);
        if (c == null) return null;
        if (c.expired()) {
            if (m.remove(key, c)) blockCount.decrementAndGet();
            return null;
        }
        return c;
    }

    public Cause nearby(Block b) {
        Cause c = get(b);
        if (c != null) return c;
        for (BlockFace f : FACES) {
            c = get(b.getRelative(f));
            if (c != null) return c;
        }
        return null;
    }

    // ------- proprietari (pistoni) -------
    public void putOwner(Block b, Actor a) {
        owners.computeIfAbsent(b.getWorld().getUID(), k -> new ConcurrentHashMap<>()).put(b.getBlockKey(), a);
    }

    public Actor owner(Block b) {
        Map<Long, Actor> m = owners.get(b.getWorld().getUID());
        return m == null ? null : m.get(b.getBlockKey());
    }

    // ------- entita' (TNT, cristalli, wither) -------
    public void putEntity(Entity e, Cause c) {
        entities.put(e.getUniqueId(), c);
        c.touch(ENTITY_TTL);
    }

    public Cause entity(Entity e) {
        Cause c = entities.get(e.getUniqueId());
        if (c != null && c.expired()) { entities.remove(e.getUniqueId()); return null; }
        return c;
    }

    // ------- esplosioni recenti (per i TNT a catena senza sorgente) -------
    public void explosion(Location l, Cause c) {
        explosions.addLast(new Explosion(l.getWorld().getUID(), l.getX(), l.getY(), l.getZ(), c, System.currentTimeMillis() + 4000));
        c.touch(WINDOW);
    }

    public Cause nearestExplosion(Location l, double radius) {
        long now = System.currentTimeMillis();
        Cause best = null;
        double bd = radius * radius;
        for (Explosion e : explosions) {
            if (e.expires() < now || !e.world().equals(l.getWorld().getUID())) continue;
            double dx = e.x() - l.getX(), dy = e.y() - l.getY(), dz = e.z() - l.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            if (d <= bd) { bd = d; best = e.cause(); }
        }
        return best;
    }

    /** Pulizia periodica (thread asincrono). */
    public void sweep() {
        long now = System.currentTimeMillis();
        for (Map<Long, Cause> m : blocks.values()) {
            m.entrySet().removeIf(en -> {
                boolean dead = en.getValue().expired();
                if (dead) blockCount.decrementAndGet();
                return dead;
            });
        }
        entities.values().removeIf(Cause::expired);
        recent.values().removeIf(Cause::expired);
        while (true) {
            Explosion e = explosions.peekFirst();
            if (e == null || e.expires() >= now) break;
            explosions.pollFirst();
        }
    }
}
