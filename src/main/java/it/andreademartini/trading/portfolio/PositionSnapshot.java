package it.andreademartini.trading.portfolio;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionSnapshot(
        String account,
        String symbol,
        BigDecimal quantity,
        double averageCost,
        Instant updatedAt) {
}
