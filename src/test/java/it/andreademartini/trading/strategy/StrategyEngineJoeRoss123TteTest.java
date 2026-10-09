package it.andreademartini.trading.strategy;

import it.andreademartini.trading.config.StrategyType;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.HistoricalBar;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StrategyEngineJoeRoss123TteTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void publishesPaperCompatibleCandidateWithoutRossHookMetadata() {
        TradingSystemProperties properties = new TradingSystemProperties();
        properties.getStrategy().setType(StrategyType.JOE_ROSS_123_TTE);
        properties.getStrategy().setEnabled(true);
        properties.getStrategy().setAutoExecuteSignals(true);
        properties.getStrategy().getJoeRoss123Tte()
                .setDirection(StrategyTradeMode.LONG_ONLY);
        properties.getStrategy().getJoeRoss123Tte().getSwing().setPivotStrength(1);
        properties.getStrategy().getJoeRoss123Tte()
                .getBollingerPoint1Filter().setEnabled(false);
        SignalService signals = mock(SignalService.class);
        StrategyEngine engine = new StrategyEngine(properties, signals);

        List<HistoricalBar> bars = List.of(
                bar(0, 98, 102, 95, 100),
                bar(1, 96, 99, 90, 94),
                bar(2, 95, 100, 94, 98),
                bar(3, 100, 110, 99, 108),
                bar(4, 104, 106, 100, 102));
        List<TradingSignal> result = List.of();
        for (HistoricalBar bar : bars) {
            result = engine.onBar("aapl", bar);
        }

        TradingSignal candidate = result.getFirst();
        assertThat(candidate.source()).isEqualTo("JOE_ROSS_123_TTE");
        assertThat(candidate.orderDetails().activeAfterBarIndex()).isEqualTo(4);
        assertThat(candidate.orderDetails().metadata())
                .containsEntry("roomToPoint2", "3.99")
                .doesNotContainKeys("rossHookPrice", "roomToRossHook");
        verify(signals).publish(candidate, true);
    }

    private HistoricalBar bar(
            int index,
            double open,
            double high,
            double low,
            double close) {
        return new HistoricalBar(index, START.plus(index, ChronoUnit.DAYS),
                String.valueOf(index), open, high, low, close, 0);
    }
}
