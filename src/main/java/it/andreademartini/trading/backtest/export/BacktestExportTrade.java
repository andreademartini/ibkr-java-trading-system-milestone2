package it.andreademartini.trading.backtest.export;

import it.andreademartini.trading.orders.TakeProfitAllocation;
import it.andreademartini.trading.strategy.TradeDirection;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record BacktestExportTrade(
        int number,
        String tradeId,
        String symbol,
        String strategy,
        TradeDirection direction,
        Instant signalAt,
        double signalPrice,
        String signalSource,
        String signalReason,
        int entryIndex,
        int exitIndex,
        Instant entryAt,
        Instant exitAt,
        long initialQuantity,
        long remainingQuantity,
        double entryPrice,
        double exitPrice,
        double initialStopPrice,
        Double finalStopPrice,
        double tradingCapital,
        double riskPercent,
        double maximumRisk,
        double riskPerShare,
        double effectiveRisk,
        double capitalRequired,
        double grossPnl,
        double commission,
        Double slippage,
        double netPnl,
        double returnPercent,
        Double rMultiple,
        String exitReason,
        String tradeStatus,
        int durationBars,
        double mfe,
        double mae,
        Double mfeR,
        Double maeR,
        Map<String, String> strategyData,
        List<TakeProfitAllocation> takeProfits,
        List<BacktestExportTradeEvent> events) {

    public BacktestExportTrade {
        takeProfits = takeProfits == null ? List.of() : List.copyOf(takeProfits);
        events = events == null ? List.of() : List.copyOf(events);
        strategyData = strategyData == null ? Map.of() : Map.copyOf(strategyData);
    }

    public BacktestExportTrade(
            int number,
            String tradeId,
            String symbol,
            String strategy,
            TradeDirection direction,
            Instant signalAt,
            double signalPrice,
            String signalSource,
            String signalReason,
            int entryIndex,
            int exitIndex,
            Instant entryAt,
            Instant exitAt,
            long initialQuantity,
            long remainingQuantity,
            double entryPrice,
            double exitPrice,
            double initialStopPrice,
            Double finalStopPrice,
            double tradingCapital,
            double riskPercent,
            double maximumRisk,
            double riskPerShare,
            double effectiveRisk,
            double capitalRequired,
            double grossPnl,
            double commission,
            Double slippage,
            double netPnl,
            double returnPercent,
            Double rMultiple,
            String exitReason,
            String tradeStatus,
            int durationBars,
            List<TakeProfitAllocation> takeProfits,
            List<BacktestExportTradeEvent> events) {
        this(number, tradeId, symbol, strategy, direction, signalAt, signalPrice,
                signalSource, signalReason, entryIndex, exitIndex, entryAt, exitAt,
                initialQuantity, remainingQuantity, entryPrice, exitPrice,
                initialStopPrice, finalStopPrice, tradingCapital, riskPercent,
                maximumRisk, riskPerShare, effectiveRisk, capitalRequired,
                grossPnl, commission, slippage, netPnl, returnPercent, rMultiple,
                exitReason, tradeStatus, durationBars, 0.0, 0.0, null, null,
                Map.of(), takeProfits, events);
    }
}
