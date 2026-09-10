package it.andreademartini.trading.strategy;

import java.util.Map;

public record StrategyOrderDetails(
        Double entryStopPrice,
        Double initialStopPrice,
        boolean rejectGapOverEntry,
        boolean cancelPending,
        int activeAfterBarIndex,
        Map<String, String> metadata) {

    public StrategyOrderDetails {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static StrategyOrderDetails cancel(int barIndex, String reason) {
        return new StrategyOrderDetails(
                null,
                null,
                false,
                true,
                barIndex,
                Map.of("cancelReason", reason == null ? "" : reason));
    }
}
