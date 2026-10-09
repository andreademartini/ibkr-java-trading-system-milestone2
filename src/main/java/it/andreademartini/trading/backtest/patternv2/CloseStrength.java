package it.andreademartini.trading.backtest.patternv2;

import it.andreademartini.trading.research.pattern.PatternDirection;
import java.math.BigDecimal;
import java.math.MathContext;

/** Uses the already recognized pattern window, never the entry bar. */
public final class CloseStrength {
    private CloseStrength() {}
    public static boolean valid(PatternRange pattern) {
        return pattern.range().signum()>0 && pattern.close().compareTo(pattern.low())>=0
                && pattern.close().compareTo(pattern.high())<=0;
    }
    private static BigDecimal numerator(PatternRange pattern,PatternDirection direction) {
        return direction==PatternDirection.LONG?pattern.close().subtract(pattern.low()):pattern.high().subtract(pattern.close());
    }
    public static BigDecimal value(PatternRange pattern,PatternDirection direction) {
        return valid(pattern)?numerator(pattern,direction).divide(pattern.range(),MathContext.DECIMAL128):null;
    }
    public static boolean accepts(PatternRange pattern,PatternDirection direction,BigDecimal maximum) {
        return valid(pattern) && numerator(pattern,direction).compareTo(maximum.multiply(pattern.range()))<=0;
    }
}
