package dev.chronos.listener;

import dev.chronos.Chronos;
import dev.chronos.attribution.Cause;
import dev.chronos.log.ActionLogger;
import dev.chronos.model.ActionType;
import dev.chronos.model.Actor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

import java.util.ArrayList;
import java.util.List;

/** Blocchi rotti/piazzati dai giocatori, secchielli, fluidi, fuoco, pistoni, entita' che modificano blocchi. */
public final class BlockListener implements Listener {
    private final Chronos p;

    public BlockListener(Chronos p) { this.p = p; }

    private boolean off(Block b) { return !p.cfg.logBlocks || p.actions.ignored(b.getWorld()); }

    // ---------------- giocatori ----------------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (off(b)) return;
        if (!p.cfg.logCreative && e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        Actor a = Actor.of(e.getPlayer());
        p.actions.breakBlock(a, 0, b);
        p.actions.attached(a, 0, b);
        p.monitor.onBlockBreak(e.getPlayer(), b);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (off(b)) return;
        if (!p.cfg.logCreative && e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        Actor a = Actor.of(e.getPlayer());
        if (e instanceof BlockMultiPlaceEvent m) {
            for (BlockState rs : m.getReplacedBlockStates()) {
                p.actions.place(a, 0, rs.getBlock(), rs.getBlockData().getAsString());
            }
        } else {
            p.actions.place(a, 0, b, e.getBlockReplacedState().getBlockData().getAsString());
            if (ActionLogger.isTall(b.getType()) && b.getBlockData() instanceof Bisected bi && bi.getHalf() == Bisected.Half.BOTTOM) {
                Block up = b.getRelative(BlockFace.UP);
                if (up.getType() == b.getType()) p.actions.place(a, 0, up, ActionLogger.AIR);
            }
        }
        Material t = b.getType();
        if (p.cfg.logPistons && (t == Material.PISTON || t == Material.STICKY_PISTON)) p.attribution.putOwner(b, a);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        Block b = e.getBlock();
        if (off(b)) return;
        Material bucket = e.getBucket();
        String fluid = bucket == Material.WATER_BUCKET ? "water" : bucket == Material.LAVA_BUCKET ? "lava" : null;
        if (fluid == null) return;
        BlockData cur = b.getBlockData();
        String nw;
        if (cur instanceof Waterlogged w && fluid.equals("water")) {
            Waterlogged c = (Waterlogged) w.clone();
            c.setWaterlogged(true);
            nw = c.getAsString();
        } else {
            nw = "minecraft:" + fluid + "[level=0]";
        }
        p.actions.record(Actor.of(e.getPlayer()), 0, ActionType.BLOCK_PLACE, b, fluid, cur.getAsString(), nw, null);
        if (p.cfg.logEnvironment) p.attribution.put(b, p.attribution.player(e.getPlayer(), fluid));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        Block b = e.getBlock();
        if (off(b)) return;
        BlockData cur = b.getBlockData();
        Actor a = Actor.of(e.getPlayer());
        if (cur instanceof Waterlogged w && w.isWaterlogged()) {
            Waterlogged c = (Waterlogged) w.clone();
            c.setWaterlogged(false);
            p.actions.record(a, 0, ActionType.BLOCK_BREAK, b, "water", cur.getAsString(), c.getAsString(), null);
        } else if (b.isLiquid()) {
            p.actions.record(a, 0, ActionType.BLOCK_BREAK, b, ActionLogger.name(b.getType()), cur.getAsString(), ActionLogger.AIR, null);
        }
    }

    // ---------------- fluidi ----------------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (!p.cfg.logEnvironment) return;
        Cause c = p.attribution.get(e.getBlock());
        if (c == null) return;
        Block to = e.getToBlock();
        if (p.actions.ignored(to.getWorld())) return;
        if (!to.getType().isAir() && !to.isLiquid()) {
            p.actions.breakBlock(c.actor(), p.incidents.ensure(c), to);
        }
        p.attribution.put(to, c);
    }

    // ---------------- fuoco ----------------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (!p.cfg.logEnvironment) return;
        Block b = e.getBlock();
        if (p.actions.ignored(b.getWorld())) return;
        Player pl = e.getPlayer();
        Cause c;
        if (pl != null) {
            c = p.attribution.player(pl, "fire");
            if (b.getType().isAir()) {
                p.actions.record(Actor.of(pl), 0, ActionType.BLOCK_PLACE, b, "fire", b.getBlockData().getAsString(),
                        Bukkit.createBlockData(Material.FIRE).getAsString(), null);
            }
        } else {
            Block src = e.getIgnitingBlock();
            c = src != null ? p.attribution.nearby(src) : null;
            Entity ent = e.getIgnitingEntity();
            if (c == null && ent != null) c = p.attribution.entity(ent);
            if (c == null) return;
        }
        p.attribution.put(b, c);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (!p.cfg.logEnvironment) return;
        Block b = e.getBlock();
        if (p.actions.ignored(b.getWorld())) return;
        Block ig = e.getIgnitingBlock();
        Cause c = ig != null ? p.attribution.nearby(ig) : p.attribution.nearby(b);
        if (c == null) {
            p.actions.breakBlock(Actor.env("fire"), 0, b);
        } else {
            p.actions.breakBlock(c.actor(), p.incidents.ensure(c), b);
            p.attribution.put(b, c);
        }
    }

    // ---------------- entita' che modificano blocchi ----------------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        if (!p.cfg.logEnvironment) return;
        Entity en = e.getEntity();
        if (!p.cfg.entityBlockChange.contains(en.getType())) return;
        Block b = e.getBlock();
        if (p.actions.ignored(b.getWorld())) return;
        Cause c = p.attribution.entity(en);
        Actor a = c != null ? c.actor() : Actor.env(en.getType().name());
        long inc = c != null ? p.incidents.ensure(c) : 0;
        BlockData to = e.getBlockData();
        if (to.getMaterial().isAir()) {
            p.actions.breakBlock(a, inc, b);
        } else {
            p.actions.record(a, inc, ActionType.BLOCK_PLACE, b, ActionLogger.name(to.getMaterial()),
                    b.getBlockData().getAsString(), to.getAsString(), null);
        }
    }

    // ---------------- pistoni ----------------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExtend(BlockPistonExtendEvent e) { piston(e.getBlock(), e.getBlocks(), false); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRetract(BlockPistonRetractEvent e) { piston(e.getBlock(), e.getBlocks(), true); }

    private void piston(Block piston, List<Block> moved, boolean retract) {
        if (!p.cfg.logPistons || moved.isEmpty() || p.actions.ignored(piston.getWorld())) return;
        Actor a = p.attribution.owner(piston);
        if (a == null || !(piston.getBlockData() instanceof Directional d)) return;
        BlockFace dir = retract ? d.getFacing().getOppositeFace() : d.getFacing();
        // prima tutte le rimozioni, poi tutti i piazzamenti: cosi' l'ultimo stato di ogni posizione e' corretto
        List<String[]> dest = new ArrayList<>();
        for (Block s : moved) {
            p.actions.record(a, 0, ActionType.BLOCK_BREAK, s, ActionLogger.name(s.getType()), s.getBlockData().getAsString(), ActionLogger.AIR, null);
            Block t = s.getRelative(dir);
            dest.add(new String[]{ActionLogger.name(s.getType()), t.getBlockData().getAsString(), s.getBlockData().getAsString()});
        }
        for (int i = 0; i < moved.size(); i++) {
            Block t = moved.get(i).getRelative(dir);
            String[] x = dest.get(i);
            p.actions.record(a, 0, ActionType.BLOCK_PLACE, t, x[0], x[1], x[2], null);
        }
    }
}
