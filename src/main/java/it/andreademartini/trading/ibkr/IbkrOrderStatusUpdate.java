package it.andreademartini.trading.ibkr;

import java.math.BigDecimal;

public record IbkrOrderStatusUpdate(
        int orderId,
        String status,
        BigDecimal filled,
        BigDecimal remaining,
        double averageFillPrice,
        int permanentId,
        int parentId,
        double lastFillPrice,
        int clientId,
        String whyHeld,
        double marketCapPrice) {
}
