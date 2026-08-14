package it.andreademartini.trading.risk;

import java.math.BigDecimal;

public record PositionSizingResult(
        BigDecimal maximumRisk,
        BigDecimal priceRisk,
        BigDecimal totalRiskPerShare,
        long quantity,
        BigDecimal effectiveRisk,
        BigDecimal notional) {
}
