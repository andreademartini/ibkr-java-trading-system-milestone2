package it.andreademartini.trading.orders;

import java.math.BigDecimal;
import java.time.Instant;

public record TrackedOrder(
        int orderId,
        Integer parentOrderId,
        String role,
        String symbol,
        OrderSide side,
        PaperOrderType type,
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
