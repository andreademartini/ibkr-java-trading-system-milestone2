package it.andreademartini.trading.strategy;

import it.andreademartini.trading.config.TradingSystemProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MovingAverageCrossoverStrategyTest {

    @Test
    void emitsBuyAndSellOnlyOnCrossings() {
        TradingSystemProperties.StrategyProperties properties =
                new TradingSystemProperties.StrategyProperties();
        properties.setFastPeriod(2);
        properties.setSlowPeriod(3);
        properties.setSignalCooldown(Duration.ZERO);
        properties.setQuantity(1);

        MovingAverageCrossoverStrategy strategy =
                new MovingAverageCrossoverStrategy(properties);

        Instant time = Instant.parse("2026-01-01T10:00:00Z");
        List<TradingSignal> signals = new ArrayList<>();

        double[] prices = {3, 2, 1, 2, 4, 3, 1};
        for (int index = 0; index < prices.length; index++) {
            strategy.onPrice("TEST", prices[index], time.plusSeconds(index))
                    .ifPresent(signals::add);
        }

        assertFalse(signals.isEmpty());
        assertEquals(SignalAction.BUY, signals.getFirst().action());
        assertEquals(SignalAction.SELL, signals.getLast().action());
    }
}
