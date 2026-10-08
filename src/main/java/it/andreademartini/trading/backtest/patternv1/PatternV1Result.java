package it.andreademartini.trading.backtest.patternv1;

import it.andreademartini.trading.research.pattern.*;
import it.andreademartini.trading.strategy.patternv1.SymbolPatternConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record PatternV1Result(String backtestId, String strategy, LocalDate startDate, LocalDate endDate,
        LocalDate actualStartDate, LocalDate actualEndDate, BigDecimal initialCapital, BigDecimal finalCapital,
        Double returnPercent, Double cagr, double maxDrawdown, double maxDrawdownPercent,
        double exposure, int openPositions, Metrics summary, Map<PatternDirection, Metrics> directionResults,
        List<SymbolResult> symbolResults, List<Trade> trades, List<EquityPoint> equityCurve,
        List<Event> events, Map<String, SymbolPatternConfiguration> configuration,
        Map<String, List<ChartBar>> priceSeries, RunSettings settings, List<String> warnings,
        int schemaVersion, BigDecimal finalCash, BigDecimal finalAvailableCash,
        BigDecimal finalLongMarketValue, BigDecimal finalShortMarketValue, BigDecimal finalOpenPositionValue,
        double averageGrossExposurePercent, double timeInMarketPercent) {

    public record RunSettings(String timeframe, String entryMethod, String stopMethod, int stopBufferTicks,
            int maxHoldingBars, String sameBarResolution, String timeoutAccounting,
            PatternDetectorParameters detectorParameters, double riskPercent, boolean useLeverage,
            double maxLongNotional, double maxShortNotional, double maxOrderValue, double maxPositionValue,
            long maxQuantity, BigDecimal commissionPerOrder, BigDecimal slippagePercent,
            BigDecimal transactionCostsPerShare, String timeoutExitMode, String holdingBarConvention,
            long minQuantity, BigDecimal minOrderValue, long smallQuantityWarningThreshold,
            String shortAccounting, String exposureDefinition, String timeInMarketDefinition,
            PatternV1Commission commission) {}
    public record Metrics(long trades, long wins, long losses, long timeouts, double grossPnl, double netPnl,
            Double winRate, Double profitFactor, Double expectancyR, Double averageR,
            Double averageWinner, Double averageLoser, Double mfe, Double mae, Double mfeR, Double maeR,
            long ambiguities, long skippedPositionOpen, long skippedCapital,
            long closedTrades, long openTrades, long breakevens, long signalsDetected,
            double realizedGrossPnl, double realizedNetPnl, double unrealizedPnl, double netUnrealizedPnl,
            Double profitFactorR, Double profitFactorNet, Double averageWinnerR, Double averageLoserR,
            Double averageActualRiskPercent, Double averageOrderValue, double maxDrawdown,
            Double weightedExpectancyR) {}
    public record SymbolResult(String symbol, PatternDirection direction, PatternType pattern, BigDecimal targetR,
            Metrics metrics, double maxDrawdown, PatternMetrics researchBaseline, Double resolvedExpectancyR,
            Double resolvedProfitFactorR, List<String> warnings) {}
    public record Trade(String tradeId, int number, String symbol, String strategy, PatternType pattern,
            PatternDirection direction, LocalDate signalDate, LocalDate entryDate, LocalDate exitDate,
            int patternStartIndex, int signalIndex, int entryIndex, int exitIndex,
            BigDecimal patternLow, BigDecimal patternHigh, BigDecimal patternClose,
            BigDecimal entryPrice, BigDecimal stopPrice, BigDecimal takeProfitPrice, BigDecimal targetR,
            BigDecimal riskPerShare, long quantity, BigDecimal exitPrice, String exitReason,
            BigDecimal grossPnl, BigDecimal netPnl, BigDecimal entryCosts, BigDecimal costs, BigDecimal resultR, BigDecimal researchResultR,
            BigDecimal mfe, BigDecimal mae, BigDecimal mfeR, BigDecimal maeR,
            int holdingBars, boolean sameBarAmbiguous, boolean closed,
            long remainingQuantity, long desiredQuantity, long finalQuantity,
            QuantityLimitingReason quantityLimitingReason, List<QuantityLimitingReason> quantityLimitingReasons,
            boolean sizeConstrained, BigDecimal equityAtEntry, BigDecimal availableCashBeforeEntry,
            BigDecimal availableCashAfterEntry, BigDecimal orderValue, BigDecimal desiredRiskAmount,
            BigDecimal desiredRiskPercent, BigDecimal actualRiskAmount, BigDecimal actualRiskPercent,
            BigDecimal realizedPnl, BigDecimal unrealizedPnl, BigDecimal netUnrealizedPnl,
            BigDecimal executedMfeR, BigDecimal executedMaeR, BigDecimal rawBarMfeR, BigDecimal rawBarMaeR,
            LocalDate markDate, BigDecimal markPrice, int markIndex, BigDecimal timeoutExitPrice) {}
    public enum QuantityLimitingReason { NONE, MAX_QUANTITY, MAX_ORDER_VALUE, MAX_POSITION_VALUE,
        INSUFFICIENT_CAPITAL, NO_LEVERAGE, OTHER }
    public record EquityPoint(LocalDate date, BigDecimal equity, BigDecimal drawdown,
                               double drawdownPercent, int openPositions, double grossExposure,
                               BigDecimal cash, BigDecimal availableCash, BigDecimal longMarketValue,
                               BigDecimal shortMarketValue, BigDecimal grossExposureValue, BigDecimal netExposureValue,
                               BigDecimal runningPeak, double grossExposurePercent, boolean inMarket) {}
    public record Event(String eventType, String tradeId, String symbol, PatternType pattern,
            PatternDirection direction, LocalDate date, int index, Map<String, Object> details) {}
    public record ChartBar(int index, String timestamp, String label, double open, double high,
                           double low, double close, long volume) {}
}
