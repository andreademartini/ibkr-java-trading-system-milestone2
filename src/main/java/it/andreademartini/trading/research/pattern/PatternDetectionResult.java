package it.andreademartini.trading.research.pattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record PatternDetectionResult(
        PatternType patternType,
        PatternDirection direction,
        LocalDate signalDate,
        int signalBarIndex,
        int patternStartBarIndex,
        BigDecimal referencePrice,
        Map<String, Object> confidenceMetadata) {
}
