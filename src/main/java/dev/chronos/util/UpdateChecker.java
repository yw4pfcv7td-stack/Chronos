package dev.chronos.util;

import dev.chronos.Chronos;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Controlla su Modrinth se esiste una versione piu' recente (opzionale, config: update-checker). */
public final class UpdateChecker {
    private static final Pattern VERSION = Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");
    private final Chronos p;
    private volatile String latest;

    public UpdateChecker(Chronos p) { this.p = p; }

    public void check() {
        if (!p.cfg.updateCheck || p.cfg.updateSlug.isBlank()) return;
        String current = p.getPluginMeta().getVersion();
        p.sched.async(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.modrinth.com/v2/project/" + p.cfg.updateSlug + "/version"))
                        .header("User-Agent", "Chronos/" + current)
                        .timeout(Duration.ofSeconds(10)).GET().build();
                HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) return;
                Matcher m = VERSION.matcher(res.body());
                if (m.find() && !m.group(1).equals(current)) {
                    latest = m.group(1);
                    p.getLogger().info("A new version is available: " + latest + " (current " + current + ") https://modrinth.com/plugin/" + p.cfg.updateSlug);
                }
            } catch (Exception ignored) { /* niente rete: ignora */ }
        });
    }

    public void notify(Player pl) {
        String l = latest;
        if (l == null || !pl.hasPermission("chronos.admin")) return;
        Msg.send(pl, p.lang.t("update.available", "current", p.getPluginMeta().getVersion(), "latest", l,
                "url", "https://modrinth.com/plugin/" + p.cfg.updateSlug));
    }
}
