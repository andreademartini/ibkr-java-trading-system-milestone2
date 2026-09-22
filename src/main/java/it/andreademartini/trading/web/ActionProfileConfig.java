package it.andreademartini.trading.web;

import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.config.StrategyType;
import it.andreademartini.trading.strategy.StrategyTradeMode;
import it.andreademartini.trading.strategy.joeross.InitialStopMode;
import it.andreademartini.trading.strategy.joeross.BollingerSource;
import it.andreademartini.trading.strategy.joeross.Point1PriceMode;

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
        Boolean rejectGapOverEntry,
        InitialStopMode initialStopMode,
        Boolean fallbackToPoint2Breakout,
        Boolean bollingerPoint1FilterEnabled,
        Integer bollingerPeriod,
        Double bollingerStandardDeviation,
        BollingerSource bollingerSource,
        Point1PriceMode point1PriceMode,
        Boolean point2TargetSizingEnabled,
        Double point2TargetProfitPercent,
        Double minimumPoint2Distance,
        Double tp1PositionPercent,
        Double tp2PositionPercent,
        Double tp3PositionPercent) {
}
