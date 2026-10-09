package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.marketdata.HistoricalBar;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/** Rolling, causal Bollinger Bands using population standard deviation. */
public final class BollingerBandsCalculator {

    private static final MathContext MATH = MathContext.DECIMAL128;

    private final BollingerPoint1FilterParameters parameters;
    private final Deque<BigDecimal> window = new ArrayDeque<>();
    private final List<BollingerBandsValue> values = new ArrayList<>();
    private BigDecimal sum = BigDecimal.ZERO;
    private BigDecimal squares = BigDecimal.ZERO;

    public BollingerBandsCalculator(BollingerPoint1FilterParameters parameters) {
        this.parameters = parameters;
    }

    public Optional<BollingerBandsValue> onBar(HistoricalBar bar) {
        BigDecimal source = parameters.source().value(bar);
        window.addLast(source);
        sum = sum.add(source, MATH);
        squares = squares.add(source.multiply(source, MATH), MATH);
        if (window.size() > parameters.period()) {
            BigDecimal expired = window.removeFirst();
            sum = sum.subtract(expired, MATH);
            squares = squares.subtract(expired.multiply(expired, MATH), MATH);
        }
        if (window.size() < parameters.period()) {
            return Optional.empty();
        }

        BigDecimal count = BigDecimal.valueOf(parameters.period());
        BigDecimal middle = sum.divide(count, MATH);
        BigDecimal variance = squares.divide(count, MATH)
                .subtract(middle.multiply(middle, MATH), MATH);
        if (variance.signum() < 0) {
            variance = BigDecimal.ZERO;
        }
        BigDecimal deviation = variance.sqrt(MATH)
                .multiply(parameters.standardDeviationMultiplier(), MATH);
        BollingerBandsValue value = new BollingerBandsValue(
                bar.index(), bar.timestamp(), middle,
                middle.add(deviation, MATH), middle.subtract(deviation, MATH),
                parameters.period(), parameters.standardDeviationMultiplier(),
                source, bar.getHigh(), bar.getLow(), bar.getClose());
        values.add(value);
        return Optional.of(value);
    }

    public List<BollingerBandsValue> values() {
        return List.copyOf(values);
    }
}
