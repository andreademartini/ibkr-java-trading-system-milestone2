package it.andreademartini.trading.marketdata;

import java.time.Instant;

public record MarketPriceEvent(
        String symbol,
        MarketPriceType priceType,
        double price,
        MarketSnapshot snapshot,
        Instant occurredAt) {
}
