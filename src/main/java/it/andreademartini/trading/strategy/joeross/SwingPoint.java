package it.andreademartini.trading.strategy.joeross;

import java.time.Instant;

public record SwingPoint(
        SwingType type,
        int barIndex,
        Instant timestamp,
        double price,
        int confirmationBarIndex,
        Instant confirmationTimestamp) {
}
