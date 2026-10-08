package it.andreademartini.trading.backtest.patternv1;

import it.andreademartini.trading.config.TradingSystemProperties.RiskProperties;
import it.andreademartini.trading.risk.PositionSizingService;
import it.andreademartini.trading.risk.RiskRejectedException;
import it.andreademartini.trading.strategy.patternv1.PatternV1Properties;
import it.andreademartini.trading.strategy.patternv1.PatternV1Strategy.EntryPlan;
import it.andreademartini.trading.research.pattern.PatternDirection;
import static it.andreademartini.trading.backtest.patternv1.PatternV1Result.QuantityLimitingReason.*;
import it.andreademartini.trading.backtest.patternv1.PatternV1Result.QuantityLimitingReason;
import java.math.*;
import java.util.*;
import java.util.function.LongPredicate;

/** Audits the standard sizing service; no changes to sizing of other strategies. */
public final class PatternV1PositionSizing {
    private final PositionSizingService standard;
    public PatternV1PositionSizing(PositionSizingService standard) { this.standard = standard; }
    public record Decision(long desiredQuantity, long quantity, long finalPossibleQuantity,
            QuantityLimitingReason reason, List<QuantityLimitingReason> reasons, boolean sizeConstrained,
            BigDecimal equity, BigDecimal availableBefore, BigDecimal availableAfter,
            BigDecimal orderValue, BigDecimal desiredRiskAmount, BigDecimal desiredRiskPercent,
            BigDecimal actualRiskAmount, BigDecimal actualRiskPercent, BigDecimal entryCosts,
            BigDecimal requiredCapital, boolean capitalConstrained, boolean minimumRejected) {}

    public Decision calculate(EntryPlan plan, BigDecimal equity, BigDecimal available,
            RiskProperties risk, PatternV1Properties config, BigDecimal commission, BigDecimal slippage) {
        return calculate(plan, equity, available, risk, config, PatternV1Commission.fixed(commission), slippage);
    }

    public Decision calculate(EntryPlan plan, BigDecimal equity, BigDecimal available,
            RiskProperties risk, PatternV1Properties config, PatternV1Commission commission, BigDecimal slippage) {
        BigDecimal riskPercent = bd(risk.getRiskPerTradePercent());
        if (equity.signum() <= 0 || riskPercent.signum() <= 0) throw new IllegalArgumentException("Positive equity/risk required");
        BigDecimal budget = equity.multiply(riskPercent).divide(bd(100), MathContext.DECIMAL64);
        long desired = floor(budget, plan.riskPerShare());
        long quantity = desired;
        List<QuantityLimitingReason> reasons = new ArrayList<>();
        long bound = risk.getMaxQuantityPerOrder();
        quantity = limit(quantity, bound, MAX_QUANTITY, reasons);
        quantity = limit(quantity, floor(bd(risk.getMaxOrderValue()), plan.entryPrice()), MAX_ORDER_VALUE, reasons);
        quantity = limit(quantity, floor(bd(risk.getMaxPositionValue()), plan.entryPrice()), MAX_POSITION_VALUE, reasons);
        boolean isLong = plan.signal().detection().direction() == PatternDirection.LONG;
        BigDecimal directionalLimit = bd(isLong ? risk.getMaxLongNotional() : risk.getMaxShortNotional());
        quantity = limit(quantity, floor(directionalLimit, plan.entryPrice()), OTHER, reasons);
        BigDecimal perShareEntryCost = plan.entryPrice().multiply(slippage).divide(bd(100), MathContext.DECIMAL64)
                .add(bd(risk.getTransactionCostsPerShare()));
        BigDecimal perShareRoundTripCost = plan.entryPrice().add(plan.stopPrice()).multiply(slippage)
                .divide(bd(100), MathContext.DECIMAL64).add(bd(risk.getTransactionCostsPerShare()));
        BigDecimal limit = directionalLimit.min(bd(risk.getMaxOrderValue())).min(bd(risk.getMaxPositionValue()));
        long standardQuantity;
        try {
            standardQuantity = standard.calculate(equity, riskPercent, plan.entryPrice(), plan.stopPrice(),
                    perShareRoundTripCost, commission.fixedPerOrder().multiply(bd(2)), limit, risk.isUseLeverage(),
                    risk.getMaxQuantityPerOrder()).quantity();
        } catch (RiskRejectedException | IllegalArgumentException rejected) { standardQuantity = 0; }
        quantity = limit(quantity, standardQuantity, OTHER, reasons);
        if (commission.model() == PatternV1Commission.Model.IBKR_US_FIXED) {
            long costQuantity = affordableQuantity(quantity, q -> plan.riskPerShare().add(perShareRoundTripCost).multiply(bd(q))
                    .add(commission.charge(q, plan.entryPrice())).add(commission.charge(q, plan.stopPrice())).compareTo(budget) <= 0);
            quantity = limit(quantity, costQuantity, OTHER, reasons);
        }
        // Both LONG purchases and 100% initial SHORT margin require notional + entry costs.
        BigDecimal required = plan.entryPrice().add(perShareEntryCost).multiply(bd(quantity))
                .add(commission.charge(quantity, plan.entryPrice()));
        boolean capitalConstrained = false;
        if (!risk.isUseLeverage()) {
            long cashQuantity = affordableQuantity(quantity, q -> plan.entryPrice().add(perShareEntryCost).multiply(bd(q))
                    .add(commission.charge(q, plan.entryPrice())).compareTo(available) <= 0);
            capitalConstrained = cashQuantity < quantity;
            quantity = limit(quantity, cashQuantity, INSUFFICIENT_CAPITAL, reasons);
            if (capitalConstrained) reasons.add(NO_LEVERAGE);
        }
        long possible = quantity;
        BigDecimal orderValue = plan.entryPrice().multiply(bd(quantity));
        boolean minimumRejected = quantity > 0 && (quantity < config.getMinQuantity()
                || orderValue.compareTo(config.getMinOrderValue()) < 0);
        if (minimumRejected) { quantity = 0; reasons.add(OTHER); orderValue = BigDecimal.ZERO; }
        BigDecimal entryCosts = commission.charge(quantity, plan.entryPrice()).add(perShareEntryCost.multiply(bd(quantity)));
        BigDecimal actualRisk = plan.riskPerShare().multiply(bd(quantity));
        BigDecimal actualPercent = actualRisk.multiply(bd(100)).divide(equity, 12, RoundingMode.HALF_UP);
        if (actualRisk.signum() < 0 || actualRisk.compareTo(budget) > 0) throw new IllegalStateException("Actual risk exceeds budget");
        var distinct = reasons.stream().distinct().toList();
        var reason = capitalConstrained ? INSUFFICIENT_CAPITAL : reasons.isEmpty() ? NONE : reasons.getLast();
        return new Decision(desired, quantity, possible, reason, distinct, quantity < desired, equity,
                available, available.subtract(orderValue).subtract(entryCosts), orderValue, budget, riskPercent,
                actualRisk, actualPercent, entryCosts, required, capitalConstrained, minimumRejected);
    }
    private static long affordableQuantity(long upper, LongPredicate fits) {
        long low = 0, high = upper;
        while (low < high) {
            long distance = high - low;
            long mid = low + distance / 2 + distance % 2;
            if (fits.test(mid)) low = mid;
            else high = mid - 1;
        }
        return low;
    }
    private static long limit(long quantity, long maximum, QuantityLimitingReason reason, List<QuantityLimitingReason> reasons) {
        if (maximum < quantity) { reasons.add(reason); return Math.max(0, maximum); }
        return quantity;
    }
    private static long floor(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.signum() <= 0) throw new IllegalArgumentException("Positive price/limit required");
        if (numerator.signum() <= 0) return 0;
        return numerator.divide(denominator, 0, RoundingMode.DOWN).min(BigDecimal.valueOf(Long.MAX_VALUE)).longValueExact();
    }
    private static BigDecimal bd(double value) { return BigDecimal.valueOf(value); }
    private static BigDecimal bd(long value) { return BigDecimal.valueOf(value); }
}
