package it.andreademartini.trading.strategy.joeross;

import java.time.Instant;

public record RossHook(
        double price,
        int barIndex,
        Instant timestamp) {
}
