package it.andreademartini.trading.research.pattern;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PatternNoLookAheadTest {
    @Test void changingFutureBarsCannotChangeCurrentDetection() {
        var first = DetectorTestSupport.bar(0, 10, 10.2, 7.8, 8);
        var signal = DetectorTestSupport.bar(1, 7.5, 10.8, 7.2, 10.5);
        var detector = new BullishEngulfingDetector();
        var baseline = detector.detect(List.of(first, signal,
                        DetectorTestSupport.bar(2, 10, 11, 9, 10)), 1,
                PatternDetectorParameters.defaults());
        var changed = detector.detect(List.of(first, signal,
                        DetectorTestSupport.bar(2, 1000, 2000, 1, 1500)), 1,
                PatternDetectorParameters.defaults());
        assertEquals(baseline, changed);
        assertTrue(baseline.isPresent());
    }
}
