package it.andreademartini.trading.marketdata;

import java.time.Instant;

public record HistoricalBar(
        int index,
        Instant timestamp,
        String label,
        double open,
        double high,
        double low,
        double close,
        long volume) {
}
