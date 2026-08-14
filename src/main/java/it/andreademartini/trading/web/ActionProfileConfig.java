package it.andreademartini.trading.web;

import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.config.StrategyType;
import it.andreademartini.trading.strategy.TradeDirection;

public record ActionProfileConfig(
        String id,
        String name,
        String symbol,
        MarketDataProvider provider,
        StrategyType strategy,
        Integer fastPeriod,
        Integer slowPeriod,
        Long quantity,
        Double initialCapital,
        Double commissionPerOrder,
        Double slippagePercent,
        TradeDirection tradeDirection,
        String timeframe,
        String outputSize,
        Integer maxPoints) {
}
