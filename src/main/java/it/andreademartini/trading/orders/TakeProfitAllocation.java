package it.andreademartini.trading.orders;

import java.math.BigDecimal;

public record TakeProfitAllocation(
        int targetNumber,
        BigDecimal profitPercent,
        BigDecimal positionPercent,
        long quantity,
        BigDecimal price) {
}
