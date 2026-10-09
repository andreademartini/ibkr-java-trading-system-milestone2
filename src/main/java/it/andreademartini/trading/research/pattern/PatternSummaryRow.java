package it.andreademartini.trading.research.pattern;

import java.math.BigDecimal;
import java.util.List;

public record PatternSummaryRow(
        PatternType pattern,
        PatternDirection direction,
        String symbol,
        BigDecimal targetR,
        PatternMetrics overall,
        PatternMetrics inSample,
        PatternMetrics outOfSample,
        StabilityStatus stabilityStatus,
        List<String> warnings) {
}
