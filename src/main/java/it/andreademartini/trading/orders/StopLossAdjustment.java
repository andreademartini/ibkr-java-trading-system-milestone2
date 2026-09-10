package it.andreademartini.trading.orders;

import java.math.BigDecimal;
import java.time.Instant;

public record StopLossAdjustment(
        Long id,
        String tradeId,
        BigDecimal oldStopPrice,
        BigDecimal newStopPrice,
        long oldQuantity,
        long newQuantity,
        StopLossAdjustmentReason reason,
        TakeProfitLevel takeProfitLevel,
        Instant createdAt) {

    public StopLossAdjustment {
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public static StopLossAdjustment initial(
            String tradeId,
            BigDecimal stopPrice,
            long quantity) {
        return new StopLossAdjustment(
                null,
                tradeId,
                null,
                stopPrice,
                0,
                quantity,
                StopLossAdjustmentReason.INITIAL_STOP,
                null,
                Instant.now());
    }
}
