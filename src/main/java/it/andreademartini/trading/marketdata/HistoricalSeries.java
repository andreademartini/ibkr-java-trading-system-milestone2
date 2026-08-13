package it.andreademartini.trading.marketdata;

import it.andreademartini.trading.config.MarketDataProvider;

import java.time.Instant;
import java.util.List;

public record HistoricalSeries(
        MarketDataProvider provider,
        String symbol,
        String timeframe,
        String outputSize,
        List<HistoricalBar> bars,
        String source,
        Instant loadedAt,
        String message) {

    public List<Double> closePrices() {
        return bars.stream()
                .map(HistoricalBar::close)
                .toList();
    }
}
