package it.andreademartini.trading.research.pattern;

import it.andreademartini.trading.marketdata.HistoricalBar;

import java.time.Instant;

final class DetectorTestSupport {
    private DetectorTestSupport() { }

    static HistoricalBar bar(int index, double open, double high, double low, double close) {
        return new HistoricalBar(index,
                Instant.parse("2024-01-01T00:00:00Z").plusSeconds(index * 86400L),
                "2024-01-" + String.format("%02d", index + 1),
                open, high, low, close, 1000);
    }
}
