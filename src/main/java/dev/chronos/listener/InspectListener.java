package dev.chronos.listener;

import dev.chronos.Chronos;
import dev.chronos.command.Fmt;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Inspector (click su un blocco = cronologia) e bacchetta di selezione. */
public final class InspectListener implements Listener {
    private final Chronos p;
    private final Set<UUID> inspecting = ConcurrentHashMap.newKeySet();
    private final NamespacedKey wandKey;

    public InspectListener(Chronos p) {
        this.p = p;
        this.wandKey = new NamespacedKey(p, "wand");
    }

    public boolean toggle(UUID id) {
        if (!inspecting.remove(id)) { inspecting.add(id); return true; }
        return false;
    }

    public ItemStack wand() {
        Material m = Material.matchMaterial(p.cfg.wandMaterial);
        ItemStack it = new ItemStack(m == null ? Material.BLAZE_ROD : m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(dev.chronos.util.Msg.item(p.lang.raw("wand.name")));
        meta.lore(java.util.List.of(
                dev.chronos.util.Msg.item(p.lang.raw("wand.lore1")),
                dev.chronos.util.Msg.item(p.lang.raw("wand.lore2")),
                dev.chronos.util.Msg.item(p.lang.raw("wand.lore3"))));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isWand(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.LEFT_CLICK_BLOCK && a != Action.RIGHT_CLICK_BLOCK) return;
        Player pl = e.getPlayer();
        Block b = e.getClickedBlock();
        if (b == null) return;
        if (isWand(pl.getInventory().getItemInMainHand()) && pl.hasPermission("chronos.rollback")) {
            e.setCancelled(true);
            p.selections.set(pl, a == Action.LEFT_CLICK_BLOCK ? 1 : 2, b);
            return;
        }
        if (inspecting.contains(pl.getUniqueId()) && pl.hasPermission("chronos.inspect")) {
            e.setCancelled(true);
            Block target = a == Action.LEFT_CLICK_BLOCK || b.getState(false) instanceof Container ? b : b.getRelative(e.getBlockFace());
            Fmt.history(p, pl, target.getWorld().getName(), target.getX(), target.getY(), target.getZ(), 1);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { inspecting.remove(e.getPlayer().getUniqueId()); }
}
