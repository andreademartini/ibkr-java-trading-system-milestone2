package it.andreademartini.trading.research.pattern;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DarkCloudCoverDetectorTest {
    @Test void detectsCloseBelowPreviousBodyMidpoint() {
        var bars = List.of(DetectorTestSupport.bar(0, 8, 10.2, 7.8, 10),
                DetectorTestSupport.bar(1, 10.5, 10.8, 8.3, 8.5));
        assertTrue(new DarkCloudCoverDetector().detect(
                bars, 1, PatternDetectorParameters.defaults()).isPresent());
    }
}
