package it.andreademartini.trading.research.pattern;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PiercingPatternDetectorTest {
    @Test void detectsCloseAbovePreviousBodyMidpoint() {
        var bars = List.of(DetectorTestSupport.bar(0, 10, 10.2, 7.8, 8),
                DetectorTestSupport.bar(1, 7.5, 9.7, 7.2, 9.5));
        assertTrue(new PiercingPatternDetector().detect(
                bars, 1, PatternDetectorParameters.defaults()).isPresent());
    }
}
