package it.andreademartini.trading.research.pattern;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BullishEngulfingDetectorTest {
    @Test void detectsExactBodyEngulfing() {
        var bars = List.of(DetectorTestSupport.bar(0, 10, 10.2, 7.8, 8),
                DetectorTestSupport.bar(1, 7.5, 10.8, 7.2, 10.5));
        var result = new BullishEngulfingDetector().detect(
                bars, 1, PatternDetectorParameters.defaults()).orElseThrow();
        assertEquals(PatternType.BULLISH_ENGULFING, result.patternType());
        assertEquals(PatternDirection.LONG, result.direction());
        assertNull(result.confidenceMetadata());
    }
}
