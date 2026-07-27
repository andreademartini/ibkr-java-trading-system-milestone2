package it.andreademartini.trading.marketdata;

import java.time.Instant;

public record MarketSnapshot(
        String symbol,
        double bid,
        double ask,
        double last,
        double close,
        double high,
        double low,
        Instant updatedAt) {

    public static MarketSnapshot empty(String symbol) {
        return new MarketSnapshot(symbol, 0, 0, 0, 0, 0, 0, Instant.EPOCH);
    }

    public double referencePriceForBuy() {
        if (ask > 0) {
            return ask;
        }
        if (last > 0) {
            return last;
        }
        return close;
    }

    public double referencePriceForSell() {
        if (bid > 0) {
            return bid;
        }
        if (last > 0) {
            return last;
        }
        return close;
    }
}
