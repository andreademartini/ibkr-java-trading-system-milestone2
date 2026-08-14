package it.andreademartini.trading.orders;

import it.andreademartini.trading.strategy.TradeDirection;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaperOrderRequest(
        @NotBlank String symbol,
        @NotNull OrderSide side,
        @NotNull PaperOrderType type,
        @Min(1) long quantity,
        @Positive Double limitPrice,
        @Positive Double stopPrice,
        @Positive Double takeProfitPrice,
        @Positive Double trailingPercent,
        String reason,
        TradeDirection direction,
        TradePlan tradePlan) {

    public PaperOrderRequest(
            String symbol,
            OrderSide side,
            PaperOrderType type,
            long quantity,
            Double limitPrice,
            Double stopPrice,
            Double takeProfitPrice,
            Double trailingPercent,
            String reason) {
        this(
                symbol,
                side,
                type,
                quantity,
                limitPrice,
                stopPrice,
                takeProfitPrice,
                trailingPercent,
                reason,
                null,
                null);
    }

    public PaperOrderRequest normalized() {
        return new PaperOrderRequest(
                symbol == null ? "" : symbol.trim().toUpperCase(),
                side,
                type,
                quantity,
                limitPrice,
                stopPrice,
                takeProfitPrice,
                trailingPercent,
                reason == null || reason.isBlank() ? "manual-paper-order" : reason.trim(),
                direction,
                tradePlan
        );
    }

    public TradeDirection resolvedDirection() {
        if (direction != null) {
            return direction;
        }
        return side == OrderSide.SELL
                ? TradeDirection.SHORT
                : TradeDirection.LONG;
    }
}
