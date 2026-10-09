package it.andreademartini.trading.research.pattern;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ShootingStarDetectorTest {
    @Test void usesConfiguredShadowRatiosWithoutTrendFilter() {
        var bars = List.of(DetectorTestSupport.bar(0, 10.5, 12, 9.8, 10));
        assertTrue(new ShootingStarDetector().detect(
                bars, 0, PatternDetectorParameters.defaults()).isPresent());
    }
}
