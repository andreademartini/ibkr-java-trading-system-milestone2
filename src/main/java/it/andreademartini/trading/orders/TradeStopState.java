package it.andreademartini.trading.orders;

import it.andreademartini.trading.strategy.TradeDirection;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public record TradeStopState(
        String tradeId,
        TradeDirection direction,
        BigDecimal entryPrice,
        BigDecimal initialStopPrice,
        BigDecimal currentStopPrice,
        long initialQuantity,
        long remainingQuantity,
        long currentStopQuantity,
        List<TakeProfitAllocation> takeProfits) {

    public TradeStopState {
        takeProfits = takeProfits == null ? List.of() : List.copyOf(takeProfits);
    }

    public Optional<BigDecimal> takeProfitPrice(TakeProfitLevel level) {
        return takeProfits.stream()
                .filter(target -> target.targetNumber() == level.number())
                .map(TakeProfitAllocation::price)
                .findFirst();
    }
}
