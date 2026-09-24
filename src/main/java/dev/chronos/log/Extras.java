package dev.chronos.log;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Serializzazione dei dati extra di un blocco (contenuto dei contenitori con NBT completo, testo dei cartelli).
 * Formato: [1 byte tipo][payload]. Tipo 1 = inventario, 2 = cartello.
 */
public final class Extras {
    public static final byte INV = 1, SIGN = 2;
    private static final Set<Material> TYPES = EnumSet.noneOf(Material.class);

    static {
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isBlock()) continue;
            if (Tag.SIGNS.isTagged(m) || Tag.ALL_HANGING_SIGNS.isTagged(m) || Tag.SHULKER_BOXES.isTagged(m)) TYPES.add(m);
        }
        TYPES.addAll(List.of(Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.FURNACE,
                Material.BLAST_FURNACE, Material.SMOKER, Material.HOPPER, Material.DROPPER, Material.DISPENSER,
                Material.BREWING_STAND, Material.CRAFTER));
    }

    private Extras() { }

    private static Inventory inv(Container c) {
        // per le casse si usa solo la meta' del blocco, cosi' un doppio baule non viene sovrascritto per intero
        return c instanceof Chest ch ? ch.getBlockInventory() : c.getInventory();
    }

    private static byte[] prepend(byte kind, byte[] payload) {
        byte[] out = new byte[payload.length + 1];
        out[0] = kind;
        System.arraycopy(payload, 0, out, 1, payload.length);
        return out;
    }

    public static byte[] items(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) copy[i] = items[i] == null ? new ItemStack(Material.AIR) : items[i];
        return ItemStack.serializeItemsAsBytes(copy);
    }

    public static ItemStack[] readItems(byte[] raw) {
        try { return ItemStack.deserializeItemsFromBytes(raw); } catch (Exception e) { return new ItemStack[0]; }
    }

    /** Da chiamare sul thread proprietario del blocco. marker=true: un contenitore vuoto restituisce un marcatore (serve all'undo). */
    public static byte[] capture(Block b, boolean marker) {
        if (!TYPES.contains(b.getType())) return null;
        BlockState st = b.getState(false);
        if (st instanceof Container c) {
            ItemStack[] contents = inv(c).getContents();
            boolean any = false;
            for (ItemStack i : contents) if (i != null && !i.getType().isAir()) { any = true; break; }
            if (!any) return marker ? new byte[]{INV} : null;
            return prepend(INV, items(contents));
        }
        if (st instanceof Sign s) {
            StringBuilder sb = new StringBuilder();
            SignSide f = s.getSide(Side.FRONT), bk = s.getSide(Side.BACK);
            sb.append(s.isWaxed()).append(';').append(col(f.getColor())).append(';').append(f.isGlowingText())
              .append(';').append(col(bk.getColor())).append(';').append(bk.isGlowingText());
            for (Component c : f.lines()) sb.append('\n').append(GsonComponentSerializer.gson().serialize(c));
            for (Component c : bk.lines()) sb.append('\n').append(GsonComponentSerializer.gson().serialize(c));
            return prepend(SIGN, sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }

    private static String col(DyeColor c) { return (c == null ? DyeColor.BLACK : c).name(); }

    /** Applica un extra a un blocco (dopo setBlockData). Thread proprietario del blocco. */
    public static void apply(Block b, byte[] extra) {
        if (extra == null || extra.length == 0) return;
        if (extra[0] == INV) {
            if (!(b.getState(false) instanceof Container c)) return;
            Inventory inv = inv(c);
            if (extra.length == 1) { inv.clear(); return; }
            ItemStack[] items = readItems(Arrays.copyOfRange(extra, 1, extra.length));
            inv.setContents(Arrays.copyOf(items, inv.getSize()));
        } else if (extra[0] == SIGN) {
            if (!(b.getState() instanceof Sign s)) return;
            try {
                String[] ls = new String(extra, 1, extra.length - 1, StandardCharsets.UTF_8).split("\n", -1);
                String[] h = ls[0].split(";");
                s.setWaxed(Boolean.parseBoolean(h[0]));
                SignSide f = s.getSide(Side.FRONT), bk = s.getSide(Side.BACK);
                f.setColor(DyeColor.valueOf(h[1]));
                f.setGlowingText(Boolean.parseBoolean(h[2]));
                bk.setColor(DyeColor.valueOf(h[3]));
                bk.setGlowingText(Boolean.parseBoolean(h[4]));
                for (int i = 0; i < 4; i++) {
                    f.line(i, GsonComponentSerializer.gson().deserialize(ls[1 + i]));
                    bk.line(i, GsonComponentSerializer.gson().deserialize(ls[5 + i]));
                }
                s.update(true, false);
            } catch (Exception ignored) { /* dati corrotti: ignora */ }
        }
    }

    /** Aggiunge/rimuove oggetti da un contenitore. amount > 0 aggiunge, < 0 rimuove. */
    public static void delta(Block b, byte[] itemBytes, int amount) {
        if (!(b.getState(false) instanceof Container c)) return;
        ItemStack one = ItemStack.deserializeBytes(itemBytes);
        ItemStack stack = one.clone();
        stack.setAmount(Math.abs(amount));
        if (amount > 0) c.getInventory().addItem(stack);
        else c.getInventory().removeItem(stack);
    }
}
