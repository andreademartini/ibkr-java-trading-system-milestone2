package it.andreademartini.trading.strategy;

import java.time.Instant;
import java.util.UUID;

public record TradingSignal(
        UUID id,
        String symbol,
        SignalAction action,
        double price,
        long quantity,
        String source,
        String reason,
        Instant occurredAt,
        StrategyOrderDetails orderDetails) {

    public TradingSignal(
            UUID id,
            String symbol,
            SignalAction action,
            double price,
            long quantity,
            String source,
            String reason,
            Instant occurredAt) {
        this(id, symbol, action, price, quantity, source, reason, occurredAt, null);
    }

    public static TradingSignal create(
            String symbol,
            SignalAction action,
            double price,
            long quantity,
            String source,
            String reason) {
        return new TradingSignal(
                UUID.randomUUID(),
                symbol,
                action,
                price,
                quantity,
                source,
                reason,
                Instant.now(),
                null
        );
    }
}
