package it.andreademartini.trading.research.pattern;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BearishEngulfingDetectorTest {
    @Test void detectsExactBodyEngulfing() {
        var bars = List.of(DetectorTestSupport.bar(0, 8, 10.2, 7.8, 10),
                DetectorTestSupport.bar(1, 10.5, 10.8, 7.2, 7.5));
        var result = new BearishEngulfingDetector().detect(
                bars, 1, PatternDetectorParameters.defaults()).orElseThrow();
        assertEquals(PatternDirection.SHORT, result.direction());
    }
}
