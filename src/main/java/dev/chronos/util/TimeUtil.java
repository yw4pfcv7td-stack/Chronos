package dev.chronos.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtil {
    private static final Pattern P = Pattern.compile("(\\d+)([wdhms])");

    private TimeUtil() { }

    /** "1w2d3h4m5s" -> millisecondi, oppure -1 se non valido. */
    public static long parse(String s) {
        String in = s.toLowerCase(Locale.ROOT);
        Matcher m = P.matcher(in);
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) return -1;
            end = m.end();
            long n = Long.parseLong(m.group(1));
            total += n * switch (m.group(2).charAt(0)) {
                case 'w' -> 604_800_000L;
                case 'd' -> 86_400_000L;
                case 'h' -> 3_600_000L;
                case 'm' -> 60_000L;
                default -> 1_000L;
            };
        }
        return (end == in.length() && total > 0) ? total : -1;
    }

    public static String ago(long ts) {
        long s = Math.max(0, System.currentTimeMillis() - ts) / 1000;
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + (s % 60) + "s";
        return s + "s";
    }

    public static String fmt(long ts) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(ts));
    }
}
