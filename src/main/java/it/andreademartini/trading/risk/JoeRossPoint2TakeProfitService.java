package it.andreademartini.trading.risk;

import it.andreademartini.trading.orders.TakeProfitAllocation;
import it.andreademartini.trading.strategy.TradeDirection;
import it.andreademartini.trading.strategy.joeross.JoeRossTakeProfitParameters;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Dedicated 60/20/20 Point-2 targets used only by JOE_ROSS_123_TTE. */
public final class JoeRossPoint2TakeProfitService {

    private static final MathContext MATH = MathContext.DECIMAL64;

    public JoeRossPoint2TakeProfitService(TakeProfitAllocationService ignored) {
        // Source-compatible constructor. Generic target rules stay untouched.
    }

    public List<TakeProfitAllocation> allocate(
            long quantity,
            BigDecimal entryPrice,
            BigDecimal point2Price,
            BigDecimal point2Delta,
            TradeDirection direction,
            JoeRossTakeProfitParameters parameters) {
        if (quantity <= 0 || entryPrice.signum() <= 0 || point2Delta.signum() <= 0) {
            throw new RiskRejectedException("POINT2_DISTANCE_TOO_SMALL");
        }
        boolean favorable = direction == TradeDirection.LONG
                ? point2Price.compareTo(entryPrice) > 0
                : point2Price.compareTo(entryPrice) < 0;
        if (!favorable) {
            throw new RiskRejectedException("POINT2_DISTANCE_TOO_SMALL");
        }

        long tp1Quantity = percentQuantity(
                quantity, parameters.tp1PositionPercent());
        long tp2Quantity = percentQuantity(
                quantity, parameters.tp2PositionPercent());
        long tp3Quantity = quantity - tp1Quantity - tp2Quantity;
        if (tp3Quantity < 0) {
            throw new IllegalArgumentException(
                    "Le quantita' dei take profit superano la posizione iniziale");
        }

        BigDecimal directionSign = direction == TradeDirection.LONG
                ? BigDecimal.ONE : BigDecimal.ONE.negate();
        BigDecimal tp2Price = point2Price.add(point2Delta
                .multiply(BigDecimal.valueOf(2), MATH)
                .multiply(directionSign, MATH), MATH);
        BigDecimal tp3Price = point2Price.add(point2Delta
                .multiply(BigDecimal.valueOf(4), MATH)
                .multiply(directionSign, MATH), MATH);

        List<TakeProfitAllocation> result = new ArrayList<>(3);
        add(result, 1, parameters.tp1PositionPercent(), tp1Quantity,
                point2Price, entryPrice);
        add(result, 2, parameters.tp2PositionPercent(), tp2Quantity,
                tp2Price, entryPrice);
        add(result, 3, parameters.tp3PositionPercent(), tp3Quantity,
                tp3Price, entryPrice);
        return List.copyOf(result);
    }

    /** Compatibility overload with the Joe Ross defaults. */
    public List<TakeProfitAllocation> allocate(
            long quantity,
            BigDecimal entryPrice,
            BigDecimal point2Price,
            TradeDirection direction) {
        return allocate(quantity, entryPrice, point2Price,
                entryPrice.subtract(point2Price).abs(), direction,
                JoeRossTakeProfitParameters.defaults());
    }

    private long percentQuantity(long quantity, double percent) {
        return BigDecimal.valueOf(quantity)
                .multiply(BigDecimal.valueOf(percent), MATH)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR)
                .longValue();
    }

    private void add(
            List<TakeProfitAllocation> result,
            int number,
            double positionPercent,
            long quantity,
            BigDecimal price,
            BigDecimal entryPrice) {
        if (quantity <= 0) {
            return;
        }
        BigDecimal profitPercent = price.subtract(entryPrice).abs()
                .multiply(BigDecimal.valueOf(100), MATH)
                .divide(entryPrice, MATH);
        result.add(new TakeProfitAllocation(number, profitPercent,
                BigDecimal.valueOf(positionPercent), quantity, price));
    }
}
