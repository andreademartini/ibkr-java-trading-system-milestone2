package it.andreademartini.trading.backtest.patternstable.engine;
import it.andreademartini.trading.risk.*;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;


public class PositionSizingService {

    private static final MathContext MATH = MathContext.DECIMAL64;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final TradePricingService pricingService;

    public PositionSizingService(TradePricingService pricingService) {
        this.pricingService = pricingService;
    }

    public PositionSizingResult calculate(
            BigDecimal tradingCapital,
            BigDecimal riskPercent,
            BigDecimal entryPrice,
            BigDecimal stopLossPrice,
            BigDecimal transactionCostsPerShare,
            BigDecimal fixedTransactionCosts,
            BigDecimal maxNotional,
            boolean useLeverage,
            long maxQuantityPerOrder) {
        requirePositive(tradingCapital, "tradingCapital");
        requirePositive(riskPercent, "riskPercent");
        requirePositive(entryPrice, "entryPrice");
        requirePositive(maxNotional, "maxNotional");
        if (maxQuantityPerOrder <= 0) {
            throw new IllegalArgumentException("maxQuantityPerOrder deve essere positivo");
        }

        BigDecimal maximumRisk = tradingCapital
                .multiply(riskPercent, MATH)
                .divide(ONE_HUNDRED, MATH);
        BigDecimal perShareRisk = pricingService.calculateRiskPerShare(
                entryPrice,
                stopLossPrice,
                transactionCostsPerShare);
        BigDecimal fixedCosts = fixedTransactionCosts == null
                ? BigDecimal.ZERO
                : fixedTransactionCosts.max(BigDecimal.ZERO);
        BigDecimal notionalLimit = useLeverage
                ? maxNotional
                : maxNotional.min(tradingCapital);
        long quantityByRisk = floorToLong(
                maximumRisk.subtract(fixedCosts, MATH)
                        .divide(perShareRisk, 12, RoundingMode.DOWN));
        long quantityByNotional = floorToLong(
                notionalLimit.divide(entryPrice, 12, RoundingMode.DOWN));
        long quantity = Math.min(
                Math.min(quantityByRisk, quantityByNotional),
                maxQuantityPerOrder);

        while (quantity > 0) {
            BigDecimal effectiveRisk = perShareRisk
                    .multiply(BigDecimal.valueOf(quantity), MATH)
                    .add(fixedCosts, MATH);
            BigDecimal notional = entryPrice
                    .multiply(BigDecimal.valueOf(quantity), MATH);
            if (effectiveRisk.compareTo(maximumRisk) <= 0
                    && notional.compareTo(notionalLimit) <= 0) {
                return new PositionSizingResult(
                        maximumRisk,
                        entryPrice.subtract(stopLossPrice, MATH).abs(MATH),
                        perShareRisk,
                        quantity,
                        effectiveRisk,
                        notional);
            }
            quantity--;
        }

        throw new RiskRejectedException(
                "Position sizing impossibile: rischio per azione o costi troppo elevati");
    }

    private long floorToLong(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return value.setScale(0, RoundingMode.DOWN).longValue();
    }

    private void requirePositive(BigDecimal value, String name) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(name + " deve essere positivo");
        }
    }
}
