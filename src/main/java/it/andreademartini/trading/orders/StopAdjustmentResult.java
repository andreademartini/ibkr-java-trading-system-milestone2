package it.andreademartini.trading.orders;

import java.math.BigDecimal;
import java.time.Instant;

public record StopAdjustmentResult(
        String tradeId,
        boolean stopPriceChanged,
        boolean stopQuantityChanged,
        boolean stopCancelled,
        boolean tradeClosed,
        boolean stopPriceRejected,
        BigDecimal oldStopPrice,
        BigDecimal newStopPrice,
        long oldQuantity,
        long newQuantity,
        StopLossAdjustmentReason reason,
        TakeProfitLevel takeProfitLevel,
        String rejectionReason,
        Instant createdAt) {

    public StopAdjustmentResult {
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public static StopAdjustmentResult noChange(
            String tradeId,
            BigDecimal stopPrice,
            long quantity,
            TakeProfitLevel takeProfitLevel) {
        return new StopAdjustmentResult(
                tradeId,
                false,
                false,
                false,
                false,
                false,
                stopPrice,
                stopPrice,
                quantity,
                quantity,
                null,
                takeProfitLevel,
                null,
                Instant.now());
    }

    public static StopAdjustmentResult changed(
            String tradeId,
            BigDecimal oldStopPrice,
            BigDecimal newStopPrice,
            long oldQuantity,
            long newQuantity,
            StopLossAdjustmentReason reason,
            TakeProfitLevel takeProfitLevel) {
        return new StopAdjustmentResult(
                tradeId,
                compare(oldStopPrice, newStopPrice) != 0,
                oldQuantity != newQuantity,
                false,
                false,
                false,
                oldStopPrice,
                newStopPrice,
                oldQuantity,
                newQuantity,
                reason,
                takeProfitLevel,
                null,
                Instant.now());
    }

    public static StopAdjustmentResult cancelled(
            String tradeId,
            BigDecimal oldStopPrice,
            long oldQuantity,
            TakeProfitLevel takeProfitLevel) {
        return new StopAdjustmentResult(
                tradeId,
                oldStopPrice != null,
                oldQuantity != 0,
                true,
                true,
                false,
                oldStopPrice,
                null,
                oldQuantity,
                0,
                StopLossAdjustmentReason.POSITION_CLOSED,
                takeProfitLevel,
                null,
                Instant.now());
    }

    public static StopAdjustmentResult rejected(
            String tradeId,
            BigDecimal currentStopPrice,
            BigDecimal rejectedStopPrice,
            long oldQuantity,
            long newQuantity,
            StopLossAdjustmentReason reason,
            TakeProfitLevel takeProfitLevel,
            String rejectionReason) {
        return new StopAdjustmentResult(
                tradeId,
                false,
                oldQuantity != newQuantity,
                false,
                false,
                true,
                currentStopPrice,
                currentStopPrice,
                oldQuantity,
                newQuantity,
                reason,
                takeProfitLevel,
                rejectionReason,
                Instant.now());
    }

    public boolean requiresStopOrderModification() {
        return !stopCancelled && (stopPriceChanged || stopQuantityChanged);
    }

    public boolean hasPersistableAdjustment() {
        return reason != null
                && (stopPriceChanged || stopQuantityChanged || stopCancelled);
    }

    public StopLossAdjustment adjustment() {
        if (!hasPersistableAdjustment()) {
            return null;
        }
        return new StopLossAdjustment(
                null,
                tradeId,
                oldStopPrice,
                newStopPrice,
                oldQuantity,
                newQuantity,
                reason,
                takeProfitLevel,
                createdAt);
    }

    private static int compare(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null || right == null) {
            return -1;
        }
        return left.compareTo(right);
    }
}
