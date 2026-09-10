package it.andreademartini.trading.backtest.export;

import java.time.Instant;

public record BacktestExportTradeEvent(
        String eventType,
        int index,
        Instant occurredAt,
        double price,
        long quantity,
        long remainingQuantity,
        Double currentStopPrice,
        double grossPnl,
        double netPnl,
        double returnPercent,
        String notes) {
}
