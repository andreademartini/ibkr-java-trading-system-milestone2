package it.andreademartini.trading.config;

public enum StrategyType {
    MANUAL_ONLY,
    MOVING_AVERAGE_CROSSOVER,
    /** Simulation only; rejected by operational strategy factories. */
    PATTERN_V1,
    JOE_ROSS_123_TTE,
    /** Kept only so persisted profiles created before the TTE-on-P3 migration still load. */
    @Deprecated
    JOE_ROSS_123_RH_TTE
}
