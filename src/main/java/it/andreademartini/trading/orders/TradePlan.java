package it.andreademartini.trading.orders;

import it.andreademartini.trading.strategy.TradeDirection;
import it.andreademartini.trading.risk.PositionSizingMode;

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
        List<TakeProfitAllocation> takeProfits,
        PositionSizingMode positionSizingMode) {

    public TradePlan {
        takeProfits = takeProfits == null ? List.of() : List.copyOf(takeProfits);
        positionSizingMode = positionSizingMode == null
                ? PositionSizingMode.DEFAULT_STOP_RISK : positionSizingMode;
    }

    public TradePlan(
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
        this(tradeId, direction, state, tradingCapital, maximumRisk, entryPrice,
                stopLossPrice, priceRisk, totalRiskPerShare,
                transactionCostsPerShare, fixedTransactionCosts, quantity,
                effectiveRisk, notional, takeProfits,
                PositionSizingMode.DEFAULT_STOP_RISK);
    }
}
