package it.andreademartini.trading.research.pattern;

import java.math.BigDecimal;

public record PatternMetrics(
        long occurrences,
        long validTrades,
        long wins,
        long losses,
        long timeouts,
        BigDecimal winRate,
        BigDecimal profitFactor,
        BigDecimal averageR,
        BigDecimal medianR,
        BigDecimal expectancyR,
        BigDecimal averageWinnerR,
        BigDecimal averageLoserR,
        int maximumConsecutiveWins,
        int maximumConsecutiveLosses,
        BigDecimal mfeAverage,
        BigDecimal mfeMedian,
        BigDecimal maeAverage,
        BigDecimal maeMedian,
        BigDecimal maximumDrawdown,
        long sameBarAmbiguities) {
}
