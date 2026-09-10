package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.strategy.TradeDirection;

import java.time.Instant;

public record JoeRossEvent(
        JoeRossEventType type,
        TradeDirection direction,
        int barIndex,
        Instant timestamp,
        double price,
        String details) {
}
