package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.strategy.TradeDirection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BollingerPoint1FilterTest {

    private static final Instant TIME = Instant.parse("2026-01-20T00:00:00Z");

    @Test
    void longExtremeBelowLowerBandIsValid() {
        assertThat(check(TradeDirection.LONG, Point1PriceMode.EXTREME,
                "99.50", "101", "100", "120").bollingerPoint1Valid()).isTrue();
    }

    @Test
    void longExtremeExactlyOnLowerBandIsValid() {
        BollingerPoint1Check result = check(TradeDirection.LONG,
                Point1PriceMode.EXTREME, "100.0000", "101", "100.0000", "120");

        assertThat(result.bollingerPoint1Valid()).isTrue();
        assertThat(result.bollingerDistance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void longExtremeInsideLowerBandIsRejected() {
        BollingerPoint1Check result = check(TradeDirection.LONG,
                Point1PriceMode.EXTREME, "100.0000000000000001", "101",
                "100.0000000000000000", "120");

        assertThat(result.bollingerPoint1Valid()).isFalse();
        assertThat(result.reason()).isEqualTo(
                BollingerPoint1Filter.INSIDE_REASON);
    }

    @Test
    void shortExtremeAboveUpperBandIsValid() {
        assertThat(check(TradeDirection.SHORT, Point1PriceMode.EXTREME,
                "121", "119", "100", "120").bollingerPoint1Valid()).isTrue();
    }

    @Test
    void shortExtremeExactlyOnUpperBandIsValid() {
        BollingerPoint1Check result = check(TradeDirection.SHORT,
                Point1PriceMode.EXTREME, "120.00", "119", "100", "120.000");

        assertThat(result.bollingerPoint1Valid()).isTrue();
        assertThat(result.bollingerDistance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shortExtremeInsideUpperBandIsRejected() {
        assertThat(check(TradeDirection.SHORT, Point1PriceMode.EXTREME,
                "119.5", "119", "100", "120").bollingerPoint1Valid()).isFalse();
    }

    @Test
    void closeModeUsesCloseInsteadOfExtreme() {
        BollingerPoint1Check extreme = check(TradeDirection.LONG,
                Point1PriceMode.EXTREME, "99", "101", "100", "120");
        BollingerPoint1Check close = check(TradeDirection.LONG,
                Point1PriceMode.CLOSE, "99", "101", "100", "120");

        assertThat(extreme.bollingerPoint1Valid()).isTrue();
        assertThat(close.bollingerPoint1Valid()).isFalse();
        assertThat(close.point1Price()).isEqualByComparingTo("101");
    }

    @Test
    void disabledFilterAcceptsCandidateWithoutHistory() {
        BollingerPoint1Filter filter = new BollingerPoint1Filter(
                BollingerPoint1FilterParameters.disabledDefaults());

        BollingerPoint1Check result = filter.evaluate(
                TradeDirection.LONG, swing(SwingType.LOW, 99), Optional.empty());

        assertThat(result.bollingerPoint1Valid()).isTrue();
        assertThat(result.filterEnabled()).isFalse();
        assertThat(result.reason()).isEqualTo("FILTER_DISABLED");
    }

    @Test
    void enabledFilterRejectsInsufficientHistory() {
        BollingerPoint1Filter filter = new BollingerPoint1Filter(parameters(
                true, 20, Point1PriceMode.EXTREME));

        BollingerPoint1Check result = filter.evaluate(
                TradeDirection.LONG, swing(SwingType.LOW, 99), Optional.empty());

        assertThat(result.bollingerPoint1Valid()).isFalse();
        assertThat(result.reason()).isEqualTo(
                BollingerPoint1Filter.HISTORY_REASON);
        assertThat(result.bollingerPeriod()).isEqualTo(20);
    }

    @Test
    void calculatorUsesExactlyTwentyBarsAndNeverFutureBars() {
        BollingerBandsCalculator calculator = new BollingerBandsCalculator(
                parameters(true, 20, Point1PriceMode.EXTREME));
        for (int index = 0; index < 19; index++) {
            assertThat(calculator.onBar(bar(index, 100))).isEmpty();
        }
        BollingerBandsValue atPoint1 = calculator.onBar(bar(19, 100)).orElseThrow();
        assertThat(atPoint1.middle()).isEqualByComparingTo("100");
        assertThat(atPoint1.upper()).isEqualByComparingTo("100");
        assertThat(atPoint1.lower()).isEqualByComparingTo("100");

        calculator.onBar(bar(20, 1000));

        assertThat(atPoint1.middle()).isEqualByComparingTo("100");
        assertThat(calculator.values().getFirst()).isSameAs(atPoint1);
        assertThat(calculator.values().getLast().middle())
                .isGreaterThan(atPoint1.middle());
    }

    @Test
    void calculatorUsesConfiguredSourceAndDefaultsToClose() {
        BollingerPoint1FilterParameters defaults =
                BollingerPoint1FilterParameters.defaults();
        assertThat(defaults.source()).isEqualTo(BollingerSource.CLOSE);

        BollingerBandsCalculator highCalculator = new BollingerBandsCalculator(
                new BollingerPoint1FilterParameters(
                        true, 2, BigDecimal.ZERO, BollingerSource.HIGH,
                        Point1PriceMode.EXTREME));
        highCalculator.onBar(new HistoricalBar(
                0, TIME, "0", 10, 20, 5, 11, 0));
        BollingerBandsValue value = highCalculator.onBar(new HistoricalBar(
                1, TIME.plus(1, ChronoUnit.DAYS), "1", 12, 30, 6, 13, 0))
                .orElseThrow();

        assertThat(value.middle()).isEqualByComparingTo("25");
    }

    private BollingerPoint1Check check(
            TradeDirection direction,
            Point1PriceMode mode,
            String extreme,
            String close,
            String lower,
            String upper) {
        BollingerBandsValue bands = new BollingerBandsValue(
                19, TIME, new BigDecimal("110"), new BigDecimal(upper),
                new BigDecimal(lower), 20, new BigDecimal("2.0"),
                new BigDecimal(close), new BigDecimal(extreme),
                new BigDecimal(extreme), new BigDecimal(close));
        return new BollingerPoint1Filter(parameters(true, 20, mode)).evaluate(
                direction,
                swing(direction == TradeDirection.LONG ? SwingType.LOW : SwingType.HIGH,
                        Double.parseDouble(extreme)),
                Optional.of(bands));
    }

    private BollingerPoint1FilterParameters parameters(
            boolean enabled,
            int period,
            Point1PriceMode mode) {
        return new BollingerPoint1FilterParameters(
                enabled, period, new BigDecimal("2.0"), BollingerSource.CLOSE, mode);
    }

    private SwingPoint swing(SwingType type, double price) {
        return new SwingPoint(type, 19, TIME, price, 20,
                TIME.plus(1, ChronoUnit.DAYS));
    }

    private HistoricalBar bar(int index, double close) {
        return new HistoricalBar(index, TIME.plus(index, ChronoUnit.DAYS),
                String.valueOf(index), close, close, close, close, 0);
    }
}
