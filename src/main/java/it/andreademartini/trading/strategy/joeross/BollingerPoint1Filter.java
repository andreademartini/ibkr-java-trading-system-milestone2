package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.strategy.TradeDirection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public final class BollingerPoint1Filter {

    public static final String INSIDE_REASON = "POINT1_INSIDE_BOLLINGER_BANDS";
    public static final String HISTORY_REASON = "INSUFFICIENT_BOLLINGER_HISTORY";

    private final BollingerPoint1FilterParameters parameters;

    public BollingerPoint1Filter(BollingerPoint1FilterParameters parameters) {
        this.parameters = parameters;
    }

    public BollingerPoint1Check evaluate(
            TradeDirection direction,
            SwingPoint candidate,
            Optional<BollingerBandsValue> bands) {
        if (!parameters.enabled()) {
            return disabled(direction, candidate, bands.orElse(null));
        }
        if (bands.isEmpty()) {
            return new BollingerPoint1Check(
                    direction, candidate.barIndex(), candidate.timestamp(),
                    BigDecimal.valueOf(candidate.price()), null, null, null,
                    parameters.period(), parameters.standardDeviationMultiplier(),
                    false, null, true, parameters.point1PriceMode(), HISTORY_REASON);
        }

        BollingerBandsValue value = bands.orElseThrow();
        BigDecimal point1Price = price(direction, value);
        BigDecimal externalBand = direction == TradeDirection.LONG
                ? value.lower() : value.upper();
        boolean valid = direction == TradeDirection.LONG
                ? point1Price.compareTo(externalBand) <= 0
                : point1Price.compareTo(externalBand) >= 0;
        return new BollingerPoint1Check(
                direction, candidate.barIndex(), candidate.timestamp(), point1Price,
                value.middle(), value.upper(), value.lower(), value.period(),
                value.standardDeviationMultiplier(), valid,
                point1Price.subtract(externalBand), true,
                parameters.point1PriceMode(), valid ? null : INSIDE_REASON);
    }

    private BigDecimal price(TradeDirection direction, BollingerBandsValue bands) {
        if (parameters.point1PriceMode() == Point1PriceMode.CLOSE) {
            return bands.close();
        }
        return direction == TradeDirection.LONG ? bands.low() : bands.high();
    }

    private BollingerPoint1Check disabled(
            TradeDirection direction,
            SwingPoint candidate,
            BollingerBandsValue bands) {
        BigDecimal price = bands == null
                ? BigDecimal.valueOf(candidate.price()) : price(direction, bands);
        BigDecimal external = bands == null ? null
                : direction == TradeDirection.LONG ? bands.lower() : bands.upper();
        return new BollingerPoint1Check(
                direction, candidate.barIndex(), candidate.timestamp(), price,
                bands == null ? null : bands.middle(),
                bands == null ? null : bands.upper(),
                bands == null ? null : bands.lower(),
                parameters.period(), parameters.standardDeviationMultiplier(), true,
                external == null ? null : price.subtract(external), false,
                parameters.point1PriceMode(), "FILTER_DISABLED");
    }
}
