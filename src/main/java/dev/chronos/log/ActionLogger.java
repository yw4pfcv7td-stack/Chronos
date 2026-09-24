package dev.chronos.log;

import dev.chronos.Chronos;
import dev.chronos.model.ActionType;
import dev.chronos.model.Actor;
import dev.chronos.model.LogRecord;
import dev.chronos.model.MessageType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Set;

/** Punto unico di accesso per accodare azioni: i listener chiamano solo questa classe. */
public final class ActionLogger {
    public static final String AIR = "minecraft:air";

    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
    private static final Set<Material> WALL_ATTACHED = Set.of(Material.WALL_TORCH, Material.REDSTONE_WALL_TORCH,
            Material.SOUL_WALL_TORCH, Material.LEVER, Material.LADDER, Material.TRIPWIRE_HOOK);

    private final Chronos p;

    public ActionLogger(Chronos p) { this.p = p; }

    public boolean ignored(World w) { return p.cfg.ignoredWorlds.contains(w.getName().toLowerCase(Locale.ROOT)); }

    public static String name(Material m) { return m.name().toLowerCase(Locale.ROOT); }

    public void record(Actor a, long inc, ActionType t, Block b, String mat, String oldD, String newD, byte[] extra) {
        record(a, inc, t, b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), mat, oldD, newD, extra, 1);
    }

    public void record(Actor a, long inc, ActionType t, String world, int x, int y, int z, String mat,
                       String oldD, String newD, byte[] extra, int amount) {
        if (p.cfg.ignoredWorlds.contains(world.toLowerCase(Locale.ROOT))) return;
        if (p.cfg.ignoredPlayers.contains(a.name().toLowerCase(Locale.ROOT))) return;
        if (t.isBlock() && p.cfg.ignoredBlocks.contains(mat)) return;
        p.queue.add(new LogRecord.Action(System.currentTimeMillis(), a.key(), a.name(), world, x, y, z, t, mat,
                oldD, newD, extra, inc, amount));
    }

    /** Blocco rotto: cattura stato completo (e contenuto per contenitori/cartelli). */
    public void breakBlock(Actor a, long inc, Block b) {
        Material m = b.getType();
        if (m.isAir()) return;
        byte[] extra = p.cfg.itemData ? Extras.capture(b, false) : null;
        record(a, inc, ActionType.BLOCK_BREAK, b, name(m), b.getBlockData().getAsString(), AIR, extra);
    }

    /** Blocco piazzato: b contiene gia' il nuovo stato. */
    public void place(Actor a, long inc, Block b, String oldData) {
        record(a, inc, ActionType.BLOCK_PLACE, b, name(b.getType()), oldData, b.getBlockData().getAsString(), null);
    }

    public void container(Actor a, Location loc, ActionType t, ItemStack one, int amount) {
        byte[] bytes = p.cfg.itemData ? one.serializeAsBytes() : null;
        record(a, 0, t, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                name(one.getType()), null, null, bytes, amount);
    }

    public void kill(Actor a, Entity victim, String victimName) {
        Location l = victim.getLocation();
        record(a, 0, ActionType.KILL, l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(),
                victim.getType().name().toLowerCase(Locale.ROOT), victimName, null, null, 1);
    }

    public void message(Actor a, Location l, MessageType t, String text) {
        if (ignored(l.getWorld())) return;
        p.queue.add(new LogRecord.Message(System.currentTimeMillis(), a.key(), a.name(), l.getWorld().getName(),
                l.getBlockX(), l.getBlockY(), l.getBlockZ(), t, text));
    }

    /** Blocchi che cadono/si rompono insieme a b (torce, cartelli, piante, altra meta' di porte...). */
    public void attached(Actor a, long inc, Block b) {
        for (BlockFace f : SIDES) {
            Block n = b.getRelative(f);
            if (WALL_ATTACHED.contains(n.getType()) || Tag.WALL_SIGNS.isTagged(n.getType()) || Tag.BUTTONS.isTagged(n.getType())) {
                if (n.getBlockData() instanceof Directional d && d.getFacing() == f) breakBlock(a, inc, n);
            }
        }
        Block up = b.getRelative(BlockFace.UP);
        Material um = up.getType();
        if (!um.isAir() && !up.isLiquid() && (!um.isSolid() || isTall(um))) {
            breakBlock(a, inc, up);
        }
        if (isTall(b.getType()) && b.getBlockData() instanceof Bisected bi) {
            Block other = bi.getHalf() == Bisected.Half.TOP ? b.getRelative(BlockFace.DOWN) : b.getRelative(BlockFace.UP);
            if (other.getType() == b.getType()) breakBlock(a, inc, other);
        }
    }

    public static boolean isTall(Material m) {
        return Tag.DOORS.isTagged(m) || m == Material.SUNFLOWER || m == Material.LILAC || m == Material.ROSE_BUSH || m == Material.PEONY || m == Material.TALL_GRASS || m == Material.LARGE_FERN || m == Material.TALL_SEAGRASS || m == Material.PITCHER_PLANT;
    }
}
