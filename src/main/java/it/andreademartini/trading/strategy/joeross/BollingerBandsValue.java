package it.andreademartini.trading.strategy.joeross;

import java.math.BigDecimal;
import java.time.Instant;

public record BollingerBandsValue(
        int barIndex,
        Instant timestamp,
        BigDecimal middle,
        BigDecimal upper,
        BigDecimal lower,
        int period,
        BigDecimal standardDeviationMultiplier,
        BigDecimal sourcePrice,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close) {
}
