package it.andreademartini.trading.backtest.patternv3;

/** Signal sampling only. Executions and holding bars always use DAILY sessions. */
public enum SignalTimeframe {
    DAILY, WEEKLY;
    /** Preserve legacy DAILY configuration hashes; WEEKLY is part of the hash. */
    public static final class DefaultDaily {
        @Override public boolean equals(Object value) { return value == DAILY; }
        @Override public int hashCode() { return DAILY.hashCode(); }
    }
}
