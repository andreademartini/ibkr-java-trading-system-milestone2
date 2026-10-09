package it.andreademartini.trading.research.pattern;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PatternOutcomeAnalyzerTest {
    @Test void entersAtNextBarOpenAndResolvesAmbiguityStopFirst() {
        var bars = List.of(
                DetectorTestSupport.bar(0, 10, 10.2, 8, 8.5),
                DetectorTestSupport.bar(1, 8.2, 10.5, 8, 10.2),
                DetectorTestSupport.bar(2, 11, 15, 7, 12));
        var detection = new PatternDetectionResult(PatternType.BULLISH_ENGULFING,
                PatternDirection.LONG, LocalDate.of(2024, 1, 2), 1, 0,
                new BigDecimal("10.2"), null);
        var request = new PatternResearchRequest(null, null, List.of("TEST"),
                List.of(PatternType.BULLISH_ENGULFING), ResearchDirection.LONG,
                List.of(BigDecimal.ONE), List.of(1), 1,
                SameBarResolution.STOP_FIRST, 70, 1, null, null, null,
                new BigDecimal("0.01"), 0, false, null);
        var result = new PatternOutcomeAnalyzer().analyze("run", "TEST", bars,
                detection, DatasetSegment.IN_SAMPLE,
                PatternResearchConfiguration.from(request)).getFirst();
        assertEquals(0, result.entryPrice().compareTo(new BigDecimal("11.0")));
        assertEquals(PatternExitReason.STOP, result.exitReason());
        assertTrue(result.sameBarAmbiguous());
        assertEquals(0, result.resultR().compareTo(BigDecimal.ONE.negate()));
    }
}
