package it.andreademartini.trading.orders;

import it.andreademartini.trading.strategy.TradeDirection;

import java.math.BigDecimal;
import java.util.List;

public record TradePlan(
        String tradeId,
        TradeDirection direction,
        TradeState state,
        BigDecimal tradingCapital,
        BigDecimal maximumRisk,
        BigDecimal entryPrice,
        BigDecimal stopLossPrice,
        BigDecimal priceRisk,
        BigDecimal totalRiskPerShare,
        BigDecimal transactionCostsPerShare,
        BigDecimal fixedTransactionCosts,
        long quantity,
        BigDecimal effectiveRisk,
        BigDecimal notional,
        List<TakeProfitAllocation> takeProfits) {

    public TradePlan {
        takeProfits = takeProfits == null ? List.of() : List.copyOf(takeProfits);
    }
}
