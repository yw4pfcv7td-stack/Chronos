package dev.chronos.listener;

import dev.chronos.Chronos;
import dev.chronos.model.ActionType;
import dev.chronos.model.Actor;
import dev.chronos.model.MessageType;
import dev.chronos.snapshot.SnapshotService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;

public final class PlayerListener implements Listener {
    private final Chronos p;

    public PlayerListener(Chronos p) { this.p = p; }

    private static String cut(String s, int n) { return s.length() > n ? s.substring(0, n) : s; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player pl = e.getPlayer();
        p.updates.notify(pl);
        if (p.cfg.logSessions) p.actions.message(Actor.of(pl), pl.getLocation(), MessageType.LOGIN, "login");
        if (p.cfg.snapLogin) p.snapshots.capture(pl, SnapshotService.LOGIN);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player pl = e.getPlayer();
        if (p.cfg.logSessions) p.actions.message(Actor.of(pl), pl.getLocation(), MessageType.LOGOUT, "logout");
        if (p.cfg.snapLogout) p.snapshots.capture(pl, SnapshotService.LOGOUT);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!p.cfg.logChat) return;
        Player pl = e.getPlayer();
        String text = cut(PlainTextComponentSerializer.plainText().serialize(e.message()), 1000);
        // la posizione va letta sul thread del giocatore (Folia)
        p.sched.entity(pl, () -> p.actions.message(Actor.of(pl), pl.getLocation(), MessageType.CHAT, text));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!p.cfg.logCommands) return;
        String msg = e.getMessage();
        String name = msg.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (p.cfg.ignoredCommands.contains(name)) return;
        Player pl = e.getPlayer();
        p.actions.message(Actor.of(pl), pl.getLocation(), MessageType.COMMAND, cut(msg, 1000));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player v = e.getEntity();
        if (p.cfg.logKills && !p.actions.ignored(v.getWorld())) {
            Player killer = v.getKiller();
            Actor a = killer != null ? Actor.of(killer)
                    : Actor.env(v.getLastDamageCause() != null ? v.getLastDamageCause().getCause().name() : "unknown");
            p.actions.kill(a, v, v.getName());
        }
        // a MONITOR l'inventario e' ancora pieno: e' il momento giusto per lo snapshot
        if (p.cfg.snapDeath) p.snapshots.capture(v, SnapshotService.DEATH);
    }
}
