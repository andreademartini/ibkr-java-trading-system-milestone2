package it.andreademartini.trading.backtest.patternv1;

import java.math.BigDecimal;

/** Commission charged separately on each entry/exit execution, in USD. */
public record PatternV1Commission(Model model, BigDecimal fixedPerOrder, BigDecimal perShare,
        BigDecimal minimumPerOrder, BigDecimal maximumPercentOrderValue) {
    public enum Model { FIXED_PER_ORDER, IBKR_US_FIXED }

    public PatternV1Commission {
        model = model == null ? Model.IBKR_US_FIXED : model;
        fixedPerOrder = model == Model.FIXED_PER_ORDER
                ? (fixedPerOrder == null ? BigDecimal.ZERO : fixedPerOrder) : BigDecimal.ZERO;
        perShare = perShare == null ? new BigDecimal("0.005") : perShare;
        minimumPerOrder = minimumPerOrder == null ? BigDecimal.ONE : minimumPerOrder;
        maximumPercentOrderValue = maximumPercentOrderValue == null ? BigDecimal.ONE : maximumPercentOrderValue;
        if (fixedPerOrder.signum() < 0 || perShare.signum() < 0 || minimumPerOrder.signum() < 0
                || maximumPercentOrderValue.signum() < 0 || maximumPercentOrderValue.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("Invalid commission parameters");
    }

    public static PatternV1Commission fixed(BigDecimal fee) {
        return new PatternV1Commission(Model.FIXED_PER_ORDER, fee, null, null, null);
    }

    public static PatternV1Commission ibkr() {
        return new PatternV1Commission(Model.IBKR_US_FIXED, null, null, null, null);
    }

    public BigDecimal charge(long quantity, BigDecimal price) {
        if (quantity <= 0) return BigDecimal.ZERO;
        if (price.signum() <= 0) throw new IllegalArgumentException("Positive execution price required");
        if (model == Model.FIXED_PER_ORDER) return fixedPerOrder;
        var shares = BigDecimal.valueOf(quantity);
        return perShare.multiply(shares).max(minimumPerOrder)
                .min(price.multiply(shares).multiply(maximumPercentOrderValue.movePointLeft(2)));
    }
}
