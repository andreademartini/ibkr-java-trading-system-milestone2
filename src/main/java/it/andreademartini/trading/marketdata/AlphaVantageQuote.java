package it.andreademartini.trading.marketdata;

import java.time.Instant;
import java.time.LocalDate;

public record AlphaVantageQuote(
        String symbol,
        double price,
        double previousClose,
        LocalDate tradingDay,
        Instant receivedAt) {
}
