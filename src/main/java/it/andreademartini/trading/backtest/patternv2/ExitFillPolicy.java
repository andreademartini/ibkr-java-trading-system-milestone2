package it.andreademartini.trading.backtest.patternv2;

/** Missing policy in archived configurations retains the original level-only fills. */
public enum ExitFillPolicy {
    LEVEL_ONLY,
    OPEN_FIRST
}
