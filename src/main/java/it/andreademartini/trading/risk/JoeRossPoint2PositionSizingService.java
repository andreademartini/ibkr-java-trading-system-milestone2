package it.andreademartini.trading.risk;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Position sizing used exclusively by JOE_ROSS_123_TTE. */
public final class JoeRossPoint2PositionSizingService {

    private static final MathContext MATH = MathContext.DECIMAL64;

    public JoeRossPoint2PositionSizingService(PositionSizingService ignored) {
        // Kept for source compatibility. Joe Ross sizing must not use stop-risk sizing.
    }

    public JoeRossPoint2PositionSizingResult calculate(
            BigDecimal capital,
            BigDecimal targetProfitPercent,
            BigDecimal entryPrice,
            BigDecimal point2Delta,
            BigDecimal minimumPoint2Distance,
            BigDecimal stopPrice,
            BigDecimal riskPercent,
            BigDecimal transactionCostsPerShare,
            BigDecimal fixedTransactionCosts,
            BigDecimal maxNotional,
            boolean leverageEnabled,
            long maxQuantity) {
        requirePositive(capital, "capital");
        requirePositive(targetProfitPercent, "targetProfitPercent");
        requirePositive(entryPrice, "entryPrice");
        requirePositive(stopPrice, "stopPrice");
        BigDecimal delta = point2Delta == null ? BigDecimal.ZERO : point2Delta.abs();
        if (delta.compareTo(minimumPoint2Distance) <= 0) {
            throw new RiskRejectedException("POINT2_DISTANCE_TOO_SMALL");
        }
        BigDecimal targetAmount = capital.multiply(targetProfitPercent, MATH)
                .divide(BigDecimal.valueOf(100), MATH);
        long targetQuantity = targetAmount.divide(delta, 0, RoundingMode.DOWN)
                .longValue();
        if (targetQuantity <= 0) {
            throw new RiskRejectedException("POINT2_DISTANCE_TOO_SMALL");
        }

        // Operational order limits remain the responsibility of RiskManager. They
        // must never turn the configured 2% into a stop-loss risk cap here.
        long quantity = targetQuantity;
        BigDecimal quantityValue = BigDecimal.valueOf(quantity);
        BigDecimal actualRiskPerShare = entryPrice.subtract(stopPrice).abs();
        BigDecimal actualRisk = actualRiskPerShare.multiply(quantityValue, MATH);
        BigDecimal actualRiskPercent = capital.signum() == 0
                ? BigDecimal.ZERO
                : actualRisk.multiply(BigDecimal.valueOf(100), MATH)
                        .divide(capital, MATH);
        BigDecimal perShareCosts = transactionCostsPerShare == null
                ? BigDecimal.ZERO : transactionCostsPerShare.max(BigDecimal.ZERO);
        BigDecimal fixedCosts = fixedTransactionCosts == null
                ? BigDecimal.ZERO : fixedTransactionCosts.max(BigDecimal.ZERO);
        BigDecimal totalRiskPerShare = actualRiskPerShare.add(perShareCosts, MATH);
        PositionSizingResult point2Sized = new PositionSizingResult(
                targetAmount, actualRiskPerShare, totalRiskPerShare,
                quantity,
                totalRiskPerShare.multiply(quantityValue, MATH).add(fixedCosts, MATH),
                entryPrice.multiply(quantityValue, MATH));
        return new JoeRossPoint2PositionSizingResult(
                point2Sized, targetProfitPercent, targetAmount, delta,
                targetQuantity, delta.multiply(quantityValue, MATH), stopPrice,
                actualRiskPerShare, actualRisk, actualRiskPercent);
    }

    private void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " deve essere positivo");
        }
    }
}
