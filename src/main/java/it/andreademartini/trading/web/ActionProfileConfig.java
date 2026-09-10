package it.andreademartini.trading.web;

import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.config.StrategyType;
import it.andreademartini.trading.strategy.StrategyTradeMode;

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
        StrategyTradeMode tradeDirection,
        String timeframe,
        String outputSize,
        Integer maxPoints,
        Integer pivotStrength,
        Double minimumTick,
        Integer tteMaxBars,
        Integer entryBufferTicks,
        Integer stopBufferTicks,
        Boolean minimumRoomEnabled,
        String minimumRoomType,
        Double minimumRoomValue,
        Boolean allowDoubleTriple,
        Integer doubleTripleToleranceTicks,
        Boolean rejectGapOverEntry) {
}
