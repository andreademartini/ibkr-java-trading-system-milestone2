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

class StrategyEngineJoeRossTest {

    private static final Instant START = Instant.parse("2025-01-01T00:00:00Z");

    @Test
    void convertsClosedBarsToPaperCompatibleStopEntrySignals() {
        TradingSystemProperties properties = properties();
        SignalService signalService = mock(SignalService.class);
        StrategyEngine engine = new StrategyEngine(properties, signalService);

        List<HistoricalBar> bars = longBarsThroughFirstTteCandidate();
        for (int index = 0; index < bars.size() - 1; index++) {
            assertThat(engine.onBar("aapl", bars.get(index))).isEmpty();
        }
        List<TradingSignal> signals = engine.onBar("aapl", bars.getLast());

        assertThat(signals).hasSize(1);
        TradingSignal candidate = signals.getFirst();
        assertThat(candidate.symbol()).isEqualTo("AAPL");
        assertThat(candidate.action()).isEqualTo(SignalAction.BUY);
        assertThat(candidate.price()).isEqualTo(112.01);
        assertThat(candidate.orderDetails().entryStopPrice()).isEqualTo(112.01);
        assertThat(candidate.orderDetails().initialStopPrice()).isEqualTo(106.99);
        assertThat(candidate.orderDetails().activeAfterBarIndex()).isEqualTo(8);
        verify(signalService).publish(candidate, true);
    }

    private TradingSystemProperties properties() {
        TradingSystemProperties properties = new TradingSystemProperties();
        properties.getStrategy().setType(StrategyType.JOE_ROSS_123_RH_TTE);
        properties.getStrategy().setEnabled(true);
        properties.getStrategy().setAutoExecuteSignals(true);
        properties.getStrategy().setQuantity(1);
        TradingSystemProperties.JoeRossProperties joe =
                properties.getStrategy().getJoeRoss123RhTte();
        joe.setDirection(StrategyTradeMode.LONG_ONLY);
        joe.setMinimumTick(0.01);
        joe.getSwing().setPivotStrength(1);
        joe.getTte().getMinimumRoom().setEnabled(false);
        return properties;
    }

    private List<HistoricalBar> longBarsThroughFirstTteCandidate() {
        return List.of(
                bar(0, 98, 102, 95, 100),
                bar(1, 96, 99, 90, 94),
                bar(2, 95, 100, 94, 98),
                bar(3, 100, 110, 99, 108),
                bar(4, 106, 107, 103, 104),
                bar(5, 104, 106, 100, 102),
                // Confirms P3; its P2 violation is deliberately not actionable.
                bar(6, 105, 111, 102, 110),
                bar(7, 110, 114, 108, 113),
                bar(8, 112, 112, 107, 108));
    }

    private HistoricalBar bar(
            int index, double open, double high, double low, double close) {
        return new HistoricalBar(
                index,
                START.plus(index, ChronoUnit.DAYS),
                String.valueOf(index),
                open,
                high,
                low,
                close,
                0);
    }
}
