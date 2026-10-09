package it.andreademartini.trading.backtest.patternv2;

import java.math.BigDecimal;
import java.math.MathContext;

/** Signal-bar information only; never consumes an entry or future bar. */
public record PatternRange(BigDecimal high, BigDecimal low, BigDecimal close) {
    @com.fasterxml.jackson.annotation.JsonProperty(value="patternRange",access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public BigDecimal range() { return high.subtract(low); }
    @com.fasterxml.jackson.annotation.JsonProperty(value="patternRangePct",access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public BigDecimal percent() { return range().multiply(BigDecimal.valueOf(100)).divide(close, MathContext.DECIMAL128); }
    public boolean reaches(BigDecimal minimum) {
        return range().multiply(BigDecimal.valueOf(100)).compareTo(minimum.multiply(close)) >= 0;
    }
}
