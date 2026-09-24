package dev.chronos.listener;

import dev.chronos.Chronos;
import dev.chronos.attribution.Cause;
import dev.chronos.model.Actor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.*;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Esplosioni, TNT a catena, cristalli, Wither e uccisioni. */
public final class EnvironmentListener implements Listener {
    private final Chronos p;

    public EnvironmentListener(Chronos p) { this.p = p; }

    private static Player playerOf(Entity damager) {
        if (damager instanceof Player pl) return pl;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player pl) return pl;
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent e) {
        if (!p.cfg.logEnvironment || !(e.getEntity() instanceof TNTPrimed t)) return;
        Cause c = null;
        Entity src = t.getSource();
        if (src != null) {
            c = p.attribution.entity(src);
            if (c == null && src instanceof Player pl) c = p.attribution.player(pl, "tnt");
        }
        if (c == null) c = p.attribution.nearestExplosion(t.getLocation(), 10);
        if (c != null) p.attribution.putEntity(t, c);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreature(CreatureSpawnEvent e) {
        if (!p.cfg.logEnvironment || e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.BUILD_WITHER) return;
        for (Player pl : e.getLocation().getNearbyPlayers(20)) {
            p.attribution.putEntity(e.getEntity(), p.attribution.player(pl, "wither"));
            break;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!p.cfg.logEnvironment) return;
        Entity v = e.getEntity();
        if (!(v instanceof EnderCrystal) && !(v instanceof Wither)) return;
        Player pl = playerOf(e.getDamager());
        if (pl != null) p.attribution.putEntity(v, p.attribution.player(pl, v.getType().name()));
    }

    private Cause resolve(Entity en) {
        if (en != null) {
            Cause c = p.attribution.entity(en);
            if (c != null) return c;
            if (en instanceof Projectile pr) {
                ProjectileSource s = pr.getShooter();
                if (s instanceof Player pl) return p.attribution.player(pl, en.getType().name());
                if (s instanceof Entity se) {
                    c = p.attribution.entity(se);
                    if (c != null) return c;
                }
            }
            return p.attribution.env(en.getType().name().toLowerCase(Locale.ROOT), true);
        }
        return p.attribution.env("explosion", true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (!p.cfg.logEnvironment) return;
        Location loc = e.getLocation();
        if (p.actions.ignored(loc.getWorld())) return;
        Cause c = resolve(e.getEntity());
        p.attribution.explosion(loc, c);
        logBlocks(c, e.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!p.cfg.logEnvironment || p.actions.ignored(e.getBlock().getWorld())) return;
        Cause c = p.attribution.env("block_explosion", true);
        p.attribution.explosion(e.getBlock().getLocation(), c);
        logBlocks(c, e.blockList());
    }

    private void logBlocks(Cause c, List<Block> blocks) {
        if (blocks.isEmpty()) return;
        long inc = p.incidents.ensure(c);
        Actor a = c.actor();
        Set<Long> inList = new HashSet<>();
        for (Block b : blocks) inList.add(b.getBlockKey());
        boolean attached = blocks.size() <= 2000;
        for (Block b : blocks) p.actions.breakBlock(a, inc, b);
        if (attached) {
            // torce, cartelli, piante appesi ai blocchi distrutti (non compaiono nella lista dell'esplosione)
            for (Block b : blocks) {
                if (!inList.contains(b.getBlockKey())) continue;
                p.actions.attached(a, inc, b);
            }
        }
    }

    // ---------------- uccisioni ----------------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        if (!p.cfg.logKills) return;
        LivingEntity v = e.getEntity();
        if (v instanceof Player || p.actions.ignored(v.getWorld())) return;
        Player killer = v.getKiller();
        if (killer == null) return;
        boolean interesting = p.cfg.killsAll || v instanceof Animals || v instanceof Villager || v instanceof Tameable
                || v.customName() != null;
        if (!interesting) return;
        p.actions.kill(Actor.of(killer), v, null);
    }
}
