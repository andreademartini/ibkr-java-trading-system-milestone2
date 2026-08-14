package it.andreademartini.trading.orders;

import it.andreademartini.trading.strategy.TradeDirection;

import java.math.BigDecimal;
import java.time.Instant;

public record TrackedOrder(
        int orderId,
        Integer parentOrderId,
        String tradeId,
        String role,
        String symbol,
        OrderSide side,
        PaperOrderType type,
        TradeDirection direction,
        TradeState tradeState,
        long quantity,
        String status,
        BigDecimal filled,
        BigDecimal remaining,
        double averageFillPrice,
        Instant updatedAt) {

    public boolean isActive() {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        return !normalized.equals("FILLED")
                && !normalized.equals("CANCELLED")
                && !normalized.equals("APICANCELLED")
                && !normalized.equals("INACTIVE");
    }
}
