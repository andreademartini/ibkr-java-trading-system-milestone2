package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.config.TradingSystemProperties;

import java.math.BigDecimal;
import java.util.Objects;

public record BollingerPoint1FilterParameters(
        boolean enabled,
        int period,
        BigDecimal standardDeviationMultiplier,
        BollingerSource source,
        Point1PriceMode point1PriceMode) {

    public BollingerPoint1FilterParameters {
        if (period < 2) {
            throw new IllegalArgumentException("Il periodo Bollinger deve essere almeno 2");
        }
        Objects.requireNonNull(standardDeviationMultiplier,
                "standardDeviationMultiplier");
        if (standardDeviationMultiplier.signum() < 0) {
            throw new IllegalArgumentException(
                    "Il moltiplicatore Bollinger non puo' essere negativo");
        }
        source = source == null ? BollingerSource.CLOSE : source;
        point1PriceMode = point1PriceMode == null
                ? Point1PriceMode.EXTREME : point1PriceMode;
    }

    public static BollingerPoint1FilterParameters defaults() {
        return new BollingerPoint1FilterParameters(
                true, 20, new BigDecimal("2.0"),
                BollingerSource.CLOSE, Point1PriceMode.EXTREME);
    }

    public static BollingerPoint1FilterParameters disabledDefaults() {
        BollingerPoint1FilterParameters defaults = defaults();
        return new BollingerPoint1FilterParameters(
                false, defaults.period(), defaults.standardDeviationMultiplier(),
                defaults.source(), defaults.point1PriceMode());
    }

    public static BollingerPoint1FilterParameters from(
            TradingSystemProperties.BollingerPoint1FilterProperties source) {
        return new BollingerPoint1FilterParameters(
                source.isEnabled(),
                source.getPeriod(),
                BigDecimal.valueOf(source.getStandardDeviation()),
                source.getSource(),
                source.getPoint1PriceMode());
    }
}
