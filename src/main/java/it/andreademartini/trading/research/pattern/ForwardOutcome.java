package it.andreademartini.trading.research.pattern;

import java.math.BigDecimal;

public record ForwardOutcome(
        int forwardBars,
        BigDecimal returnFromClose,
        BigDecimal mfe,
        BigDecimal mae,
        BigDecimal highestHigh,
        BigDecimal lowestLow,
        BigDecimal closeReturn) {
}
