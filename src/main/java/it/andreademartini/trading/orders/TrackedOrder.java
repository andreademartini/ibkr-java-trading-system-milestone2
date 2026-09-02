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
        BigDecimal limitPrice,
        BigDecimal stopPrice,
        Double trailingPercent,
        Instant updatedAt) {

    public boolean isActive() {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        return !normalized.equals("FILLED")
                && !normalized.equals("CANCELLED")
                && !normalized.equals("APICANCELLED")
                && !normalized.equals("INACTIVE");
    }

    public boolean isFilled() {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        return normalized.equals("FILLED")
                || remaining.compareTo(BigDecimal.ZERO) <= 0
                && filled.compareTo(BigDecimal.ZERO) > 0;
    }

    public TakeProfitStatus takeProfitStatus() {
        if (TakeProfitLevel.fromRole(role).isEmpty()) {
            return null;
        }
        if (isFilled()) {
            return TakeProfitStatus.FILLED;
        }
        if (filled.compareTo(BigDecimal.ZERO) > 0) {
            return TakeProfitStatus.PARTIALLY_FILLED;
        }
        return TakeProfitStatus.OPEN;
    }

    public TrackedOrder withQuantity(
            long updatedQuantity,
            BigDecimal updatedRemaining) {
        return new TrackedOrder(
                orderId,
                parentOrderId,
                tradeId,
                role,
                symbol,
                side,
                type,
                direction,
                tradeState,
                updatedQuantity,
                status,
                filled,
                updatedRemaining,
                averageFillPrice,
                limitPrice,
                stopPrice,
                trailingPercent,
                Instant.now());
    }

    public TrackedOrder withStopPriceAndQuantity(
            BigDecimal updatedStopPrice,
            long updatedQuantity,
            BigDecimal updatedRemaining) {
        return new TrackedOrder(
                orderId,
                parentOrderId,
                tradeId,
                role,
                symbol,
                side,
                type,
                direction,
                tradeState,
                updatedQuantity,
                status,
                filled,
                updatedRemaining,
                averageFillPrice,
                limitPrice,
                updatedStopPrice,
                trailingPercent,
                Instant.now());
    }

    public TrackedOrder withStatus(String updatedStatus) {
        return new TrackedOrder(
                orderId,
                parentOrderId,
                tradeId,
                role,
                symbol,
                side,
                type,
                direction,
                tradeState,
                quantity,
                updatedStatus,
                filled,
                remaining,
                averageFillPrice,
                limitPrice,
                stopPrice,
                trailingPercent,
                Instant.now());
    }

    public TrackedOrder withTradeState(TradeState updatedTradeState) {
        return new TrackedOrder(
                orderId,
                parentOrderId,
                tradeId,
                role,
                symbol,
                side,
                type,
                direction,
                updatedTradeState,
                quantity,
                status,
                filled,
                remaining,
                averageFillPrice,
                limitPrice,
                stopPrice,
                trailingPercent,
                Instant.now());
    }
}
