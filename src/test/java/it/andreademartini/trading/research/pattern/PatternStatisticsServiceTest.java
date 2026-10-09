package it.andreademartini.trading.research.pattern;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PatternStatisticsServiceTest {

    @Test
    void aggregatesTradesInsteadOfAveragingSymbolWinRatesAndFlagsStability() {
        List<PatternOccurrence> trades = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            trades.add(trade(index < 2 ? "AAPL" : "MSFT",
                    index % 2 == 0 ? PatternExitReason.TARGET : PatternExitReason.STOP,
                    index < 2 ? DatasetSegment.IN_SAMPLE : DatasetSegment.OUT_OF_SAMPLE));
        }
        PatternSummaryRow aggregate = new PatternStatisticsService()
                .summarize(trades, 2).stream()
                .filter(row -> row.symbol().equals("ALL_SYMBOLS"))
                .findFirst().orElseThrow();
        assertEquals(4, aggregate.overall().occurrences());
        assertEquals(0, aggregate.overall().winRate().compareTo(new BigDecimal("0.50000000")));
        assertEquals(StabilityStatus.STABLE, aggregate.stabilityStatus());
    }

    private PatternOccurrence trade(
            String symbol, PatternExitReason reason, DatasetSegment segment) {
        BigDecimal result = reason == PatternExitReason.TARGET
                ? new BigDecimal("2") : BigDecimal.ONE.negate();
        return new PatternOccurrence(null, "run", symbol,
                PatternType.BULLISH_ENGULFING, PatternDirection.LONG,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2),
                BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("9"),
                BigDecimal.ONE, new BigDecimal("2"), new BigDecimal("12"),
                LocalDate.of(2024, 1, 3), reason == PatternExitReason.TARGET
                        ? new BigDecimal("12") : new BigDecimal("9"),
                reason, result, new BigDecimal("2.1"), new BigDecimal("0.8"),
                false, segment, List.of());
    }
}
