package dev.chronos.util;

import dev.chronos.Chronos;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Messaggi traducibili: plugins/Chronos/lang/<codice>.yml. Le chiavi mancanti ricadono sull'inglese incluso nel jar.
 * Segnaposto: {nome}. I valori vengono escapati per MiniMessage, tranne se il nome finisce con _raw.
 */
public final class Lang {
    private static final String[] BUNDLED = {"en", "it"};

    private final Map<String, String> primary = new HashMap<>(), fallback = new HashMap<>();

    public Lang(Chronos p, String code) {
        File dir = new File(p.getDataFolder(), "lang");
        dir.mkdirs();
        for (String b : BUNDLED) {
            if (!new File(dir, b + ".yml").exists()) p.saveResource("lang/" + b + ".yml", false);
        }
        InputStream in = p.getResource("lang/en.yml");
        if (in != null) load(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)), fallback);
        File f = new File(dir, code.toLowerCase(Locale.ROOT) + ".yml");
        if (f.exists()) load(YamlConfiguration.loadConfiguration(f), primary);
        else p.getLogger().warning("Language file lang/" + code + ".yml not found, using English.");
        Msg.prefix = raw("prefix");
    }

    private static void load(YamlConfiguration y, Map<String, String> target) {
        for (String k : y.getKeys(true)) {
            if (y.isString(k)) target.put(k, y.getString(k));
        }
    }

    public String raw(String key) {
        String s = primary.get(key);
        if (s == null) s = fallback.get(key);
        return s == null ? key : s;
    }

    public String t(String key, Object... kv) {
        String s = raw(key);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String k = String.valueOf(kv[i]);
            String v = String.valueOf(kv[i + 1]);
            if (!k.endsWith("_raw")) v = Msg.esc(v);
            s = s.replace("{" + k + "}", v);
        }
        return s;
    }
}
