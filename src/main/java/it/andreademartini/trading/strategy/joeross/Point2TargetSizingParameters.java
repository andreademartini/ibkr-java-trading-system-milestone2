package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.config.TradingSystemProperties;

public record Point2TargetSizingParameters(
        boolean enabled,
        double targetProfitPercent,
        double minimumPoint2Distance) {

    public Point2TargetSizingParameters {
        if (targetProfitPercent <= 0) {
            throw new IllegalArgumentException("targetProfitPercent deve essere positivo");
        }
        if (minimumPoint2Distance < 0) {
            throw new IllegalArgumentException("minimumPoint2Distance non puo' essere negativo");
        }
    }

    public static Point2TargetSizingParameters defaults() {
        return new Point2TargetSizingParameters(true, 2.0, 0.0);
    }

    public static Point2TargetSizingParameters from(
            TradingSystemProperties.Point2TargetSizingProperties source) {
        return new Point2TargetSizingParameters(
                source.isEnabled(),
                source.getTargetProfitPercent(),
                source.getMinimumPoint2Distance());
    }
}
