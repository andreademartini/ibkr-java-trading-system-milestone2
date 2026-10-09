package it.andreademartini.trading.research.pattern;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PatternTimeoutTest {
    @Test
    void recordsTimeoutSeparatelyAfterConfiguredHoldingPeriod() {
        var bars = List.of(
                DetectorTestSupport.bar(0, 10, 10.2, 9, 9.5),
                DetectorTestSupport.bar(1, 9.2, 10.5, 9, 10.2),
                DetectorTestSupport.bar(2, 10, 10.3, 9.8, 10.1),
                DetectorTestSupport.bar(3, 10.1, 10.4, 9.9, 10.2));
        var detection = new PatternDetectionResult(PatternType.BULLISH_ENGULFING,
                PatternDirection.LONG, LocalDate.of(2024, 1, 2), 1, 0,
                new BigDecimal("10.2"), null);
        var request = new PatternResearchRequest(null, null, List.of("TEST"),
                List.of(PatternType.BULLISH_ENGULFING), ResearchDirection.LONG,
                List.of(new BigDecimal("4")), List.of(1, 2), 2,
                SameBarResolution.STOP_FIRST, 70, 1, null, null, null,
                new BigDecimal("0.01"), 0, false, null);
        PatternOccurrence result = new PatternOutcomeAnalyzer().analyze(
                "run", "TEST", bars, detection, DatasetSegment.IN_SAMPLE,
                PatternResearchConfiguration.from(request)).getFirst();
        assertEquals(PatternExitReason.TIMEOUT, result.exitReason());
        assertNull(result.resultR());
    }
}
