package it.andreademartini.trading.strategy;

public enum TradeDirection {
    LONG_ONLY,
    SHORT_ONLY,
    LONG_SHORT;

    public boolean allowsLong() {
        return this == LONG_ONLY || this == LONG_SHORT;
    }

    public boolean allowsShort() {
        return this == SHORT_ONLY || this == LONG_SHORT;
    }
}
