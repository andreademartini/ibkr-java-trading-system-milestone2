package it.andreademartini.trading.backtest.patternv1;

import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.risk.*;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class PatternV1OrderValueTest {
    private final PatternV1PortfolioEngine engine = new PatternV1PortfolioEngine(
            new PositionSizingService(new TradePricingService()), detectors());

    @ParameterizedTest @ValueSource(ints = {10000, 20000, 30000})
    void orderCapUsesRequestedInitialCapitalAndLeavesSharedSettingsUntouched(int capital) {
        var risk = new TradingSystemProperties.RiskProperties();
        risk.setMaxOrderValue(123);
        risk.setMaxLongNotional(capital);
        risk.setMaxPositionValue(capital);
        var request = new PatternV1Request(null, START, START.plusDays(100), BarInterval.DAILY,
                BigDecimal.valueOf(capital), BigDecimal.ZERO, BigDecimal.ZERO);
        var result = engine.run(request, configuration(), risk, Map.of("AAPL", engulfing("AAPL")));
        assertEquals(capital * .60, result.settings().maxOrderValue());
        assertEquals(1_000_000L, result.settings().maxQuantity());
        var trade = result.trades().getFirst();
        assertEquals(capital * 3 / 500, trade.quantity());
        assertEquals(0, BigDecimal.valueOf(capital).multiply(bd("0.60")).compareTo(trade.orderValue()));
        assertEquals(123, risk.getMaxOrderValue());
        assertEquals(1000, risk.getMaxQuantityPerOrder());
    }

    @Test void omittedInitialCapitalUsesConfiguredTradingCapital() {
        var risk = new TradingSystemProperties.RiskProperties();
        risk.setTradingCapital(20000);
        var request = new PatternV1Request(null, START, START.plusDays(100), BarInterval.DAILY,
                null, BigDecimal.ZERO, BigDecimal.ZERO);
        var result = engine.run(request, configuration(), risk, Map.of("AAPL", engulfing("AAPL")));
        assertEquals(12000, result.settings().maxOrderValue());
        assertEquals(100, result.trades().getFirst().quantity());
    }

    @Test void requestedOrderCapLimitsSizingWithoutChangingSharedSettings() {
        var risk = new TradingSystemProperties.RiskProperties();
        var request = new PatternV1Request(null, START, START.plusDays(100), BarInterval.DAILY,
                bd("10000"), BigDecimal.ZERO, BigDecimal.ZERO, 2500.0, 1000L);
        var result = engine.run(request, configuration(), risk, Map.of("AAPL", engulfing("AAPL")));
        assertEquals(2500, result.settings().maxOrderValue());
        assertEquals(25, result.trades().getFirst().quantity());
        assertEquals(5000, risk.getMaxOrderValue());
    }

    @Test void requestedQuantityCapLimitsSizingWithoutChangingSharedSettings() {
        var risk = new TradingSystemProperties.RiskProperties();
        var request = new PatternV1Request(null, START, START.plusDays(100), BarInterval.DAILY,
                bd("10000"), BigDecimal.ZERO, BigDecimal.ZERO, 7500.0, 10L);
        var result = engine.run(request, configuration(), risk, Map.of("AAPL", engulfing("AAPL")));
        assertEquals(10, result.settings().maxQuantity());
        assertEquals(10, result.trades().getFirst().quantity());
        assertEquals(1000, risk.getMaxQuantityPerOrder());
    }

    @Test void invalidLimitsAreRejected() {
        for (Double cap : new Double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            var request = new PatternV1Request(null, START, START.plusDays(100), BarInterval.DAILY,
                    bd("10000"), BigDecimal.ZERO, BigDecimal.ZERO, cap, null);
            assertThrows(IllegalArgumentException.class, () -> engine.run(request, configuration(),
                    new TradingSystemProperties.RiskProperties(), Map.of("AAPL", engulfing("AAPL"))));
        }
        var request = new PatternV1Request(null, START, START.plusDays(100), BarInterval.DAILY,
                bd("10000"), BigDecimal.ZERO, BigDecimal.ZERO, null, 0L);
        assertThrows(IllegalArgumentException.class, () -> engine.run(request, configuration(),
                new TradingSystemProperties.RiskProperties(), Map.of("AAPL", engulfing("AAPL"))));
    }
}
