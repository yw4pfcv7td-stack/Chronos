package dev.chronos.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

public final class Msg {
    public static final MiniMessage MM = MiniMessage.miniMessage();
    /** Impostato da Lang all'avvio (chiave "prefix"). */
    public static volatile String prefix = "<dark_gray>[<gold>Chronos<dark_gray>]</dark_gray> <gray>";

    private Msg() { }

    public static Component c(String mini) { return MM.deserialize(mini); }

    /** Componente per nome/lore di item (senza corsivo). */
    public static Component item(String mini) { return MM.deserialize(mini).decoration(TextDecoration.ITALIC, false); }

    public static void send(CommandSender to, String mini) { to.sendMessage(MM.deserialize(prefix + mini)); }

    public static void raw(CommandSender to, String mini) { to.sendMessage(MM.deserialize(mini)); }

    public static String esc(String s) { return s == null ? "" : MM.escapeTags(s); }
}
