package it.andreademartini.trading.marketdata;

import java.util.Locale;

public enum BarInterval {
    DAILY("DAILY", "daily"),
    HOURLY("HOURLY", "hourly"),
    MINUTES_5("5MIN", "5min");

    private final String timeframe;
    private final String directoryName;

    BarInterval(String timeframe, String directoryName) {
        this.timeframe = timeframe;
        this.directoryName = directoryName;
    }

    public String timeframe() {
        return timeframe;
    }

    public String directoryName() {
        return directoryName;
    }

    public static BarInterval fromTimeframe(String value) {
        if (value == null || value.isBlank()) {
            return DAILY;
        }
        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "");
        return switch (normalized) {
            case "DAILY", "DAY", "1D" -> DAILY;
            case "HOURLY", "HOUR", "1H", "60MIN", "60MINS", "1HOUR" -> HOURLY;
            case "MINUTES_5", "5MIN", "5MINS", "5MINUTES", "5M" -> MINUTES_5;
            default -> throw new IllegalArgumentException(
                    "Intervallo storico non supportato: " + value);
        };
    }
}
