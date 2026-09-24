package dev.chronos.detect;

import dev.chronos.Chronos;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class Discord {
    private final Chronos p;
    private final HttpClient client = HttpClient.newHttpClient();

    public Discord(Chronos p) { this.p = p; }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.toString();
    }

    public void send(String text) {
        if (!p.cfg.discordEnabled || p.cfg.discordUrl.isBlank()) return;
        try {
            String json = "{\"username\":\"" + esc(p.cfg.discordName) + "\",\"content\":\"" + esc(text) + "\"}";
            HttpRequest req = HttpRequest.newBuilder(URI.create(p.cfg.discordUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            client.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            p.getLogger().warning("Webhook Discord non valido o non raggiungibile: " + e.getMessage());
        }
    }
}
