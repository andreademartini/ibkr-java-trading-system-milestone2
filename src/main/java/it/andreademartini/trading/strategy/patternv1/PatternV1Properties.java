package it.andreademartini.trading.strategy.patternv1;

import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.research.pattern.SameBarResolution;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties("pattern-v1")
public class PatternV1Properties {
    private boolean enabled = true;
    private BarInterval timeframe = BarInterval.DAILY;
    private int maxHoldingBars = 20;
    private SameBarResolution sameBarResolution = SameBarResolution.STOP_FIRST;
    private int stopBufferTicks = 0;
    public enum TimeoutExitMode { CLOSE_AT_MAX_HOLDING_BAR }
    private TimeoutExitMode timeoutExitMode = TimeoutExitMode.CLOSE_AT_MAX_HOLDING_BAR;
    private long minQuantity = 1;
    private java.math.BigDecimal minOrderValue = java.math.BigDecimal.ZERO;
    private long smallQuantityWarningThreshold = 4;
    private Map<String, SymbolPatternConfiguration> symbols = new LinkedHashMap<>();

    public void validate() {
        if (timeframe != BarInterval.DAILY || maxHoldingBars != 20
                || sameBarResolution != SameBarResolution.STOP_FIRST || stopBufferTicks != 0
                || timeoutExitMode != TimeoutExitMode.CLOSE_AT_MAX_HOLDING_BAR
                || minQuantity < 1 || minOrderValue == null || minOrderValue.signum() < 0
                || smallQuantityWarningThreshold < 0) {
            throw new IllegalArgumentException("PATTERN_V1 requires DAILY, 20 bars, STOP_FIRST, zero buffer");
        }
    }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public BarInterval getTimeframe() { return timeframe; }
    public void setTimeframe(BarInterval timeframe) { this.timeframe = timeframe; }
    public int getMaxHoldingBars() { return maxHoldingBars; }
    public void setMaxHoldingBars(int maxHoldingBars) { this.maxHoldingBars = maxHoldingBars; }
    public SameBarResolution getSameBarResolution() { return sameBarResolution; }
    public void setSameBarResolution(SameBarResolution sameBarResolution) { this.sameBarResolution = sameBarResolution; }
    public int getStopBufferTicks() { return stopBufferTicks; }
    public TimeoutExitMode getTimeoutExitMode() { return timeoutExitMode; }
    public void setTimeoutExitMode(TimeoutExitMode mode) { this.timeoutExitMode = mode; }
    public long getMinQuantity() { return minQuantity; }
    public void setMinQuantity(long value) { minQuantity = value; }
    public java.math.BigDecimal getMinOrderValue() { return minOrderValue; }
    public void setMinOrderValue(java.math.BigDecimal value) { minOrderValue = value; }
    public long getSmallQuantityWarningThreshold() { return smallQuantityWarningThreshold; }
    public void setSmallQuantityWarningThreshold(long value) { smallQuantityWarningThreshold = value; }
    public void setStopBufferTicks(int stopBufferTicks) { this.stopBufferTicks = stopBufferTicks; }
    public Map<String, SymbolPatternConfiguration> getSymbols() { return symbols; }
    public void setSymbols(Map<String, SymbolPatternConfiguration> symbols) {
        this.symbols = new LinkedHashMap<>();
        symbols.forEach((key, value) -> this.symbols.put(key.trim().toUpperCase(java.util.Locale.ROOT), value));
    }
}
