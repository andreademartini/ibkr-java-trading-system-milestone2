package it.andreademartini.trading.strategy.patternv1;

import it.andreademartini.trading.research.pattern.PatternType;
import java.math.BigDecimal;

public record DirectionPatternConfiguration(boolean enabled, PatternType pattern, BigDecimal targetR) {
    public DirectionPatternConfiguration {
        if (enabled && (pattern == null || targetR == null || targetR.signum() <= 0)) {
            throw new IllegalArgumentException("Enabled pattern requires pattern and positive targetR");
        }
    }
}
