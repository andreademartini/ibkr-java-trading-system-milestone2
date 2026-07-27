package it.andreademartini.trading.orders;

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
        String reason) {

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
                reason == null || reason.isBlank() ? "manual-paper-order" : reason.trim()
        );
    }
}
