package it.andreademartini.trading.backtest.export;

public record BacktestExportMetrics(
        double initialCapital,
        double finalEquity,
        double netProfit,
        double totalReturnPercent,
        double maxDrawdownPercent,
        int tradeCount,
        long winningTrades,
        long losingTrades,
        double winRatePercent,
        double grossProfit,
        double grossLoss,
        double profitFactor,
        double averageTrade,
        double exposurePercent,
        long openPositionQuantity,
        double openPositionMarketValue,
        double buyAndHoldReturnPercent) {
}
