package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.strategy.TradeDirection;

import java.math.BigDecimal;
import java.time.Instant;

public record BollingerPoint1Check(
        TradeDirection direction,
        int barIndex,
        Instant timestamp,
        BigDecimal point1Price,
        BigDecimal bollingerMiddle,
        BigDecimal bollingerUpper,
        BigDecimal bollingerLower,
        int bollingerPeriod,
        BigDecimal bollingerStdDevMultiplier,
        boolean bollingerPoint1Valid,
        BigDecimal bollingerDistance,
        boolean filterEnabled,
        Point1PriceMode point1PriceMode,
        String reason) {
}
