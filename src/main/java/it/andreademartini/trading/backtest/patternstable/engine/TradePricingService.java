package it.andreademartini.trading.backtest.patternstable.engine;
import it.andreademartini.trading.risk.*;

import it.andreademartini.trading.strategy.TradeDirection;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;


public class TradePricingService {

    private static final MathContext MATH = MathContext.DECIMAL64;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public BigDecimal calculateTakeProfitPrice(
            BigDecimal entryPrice,
            BigDecimal profitPercent,
            TradeDirection direction) {
        requirePositive(entryPrice, "entryPrice");
        requirePositive(profitPercent, "profitPercent");

        BigDecimal factor = profitPercent.divide(ONE_HUNDRED, MATH);
        BigDecimal multiplier = direction == TradeDirection.LONG
                ? BigDecimal.ONE.add(factor, MATH)
                : BigDecimal.ONE.subtract(factor, MATH);
        return entryPrice.multiply(multiplier, MATH)
                .setScale(4, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateRiskPerShare(
            BigDecimal entryPrice,
            BigDecimal stopLossPrice,
            BigDecimal transactionCosts) {
        requirePositive(entryPrice, "entryPrice");
        requirePositive(stopLossPrice, "stopLossPrice");
        BigDecimal costs = transactionCosts == null
                ? BigDecimal.ZERO
                : transactionCosts.max(BigDecimal.ZERO);
        return entryPrice.subtract(stopLossPrice, MATH)
                .abs(MATH)
                .add(costs, MATH);
    }

    private void requirePositive(BigDecimal value, String name) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(name + " deve essere positivo");
        }
    }
}
