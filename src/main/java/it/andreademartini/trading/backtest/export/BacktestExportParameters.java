package it.andreademartini.trading.backtest.export;

import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.config.SameBarExecutionPolicy;
import it.andreademartini.trading.config.StrategyType;
import it.andreademartini.trading.strategy.StrategyTradeMode;

public record BacktestExportParameters(
        MarketDataProvider provider,
        StrategyType strategy,
        StrategyTradeMode tradeDirection,
        int fastPeriod,
        int slowPeriod,
        long quantity,
        double initialCapital,
        double commissionPerOrder,
        double slippagePercent,
        SameBarExecutionPolicy sameBarExecutionPolicy,
        String executionModel) {
}
