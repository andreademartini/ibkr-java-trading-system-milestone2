package it.andreademartini.trading.research.pattern;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HammerDetectorTest {
    @Test void usesConfiguredShadowRatiosWithoutTrendFilter() {
        var bars = List.of(DetectorTestSupport.bar(0, 10, 10.8, 8.5, 10.5));
        assertTrue(new HammerDetector().detect(
                bars, 0, PatternDetectorParameters.defaults()).isPresent());
    }
}
