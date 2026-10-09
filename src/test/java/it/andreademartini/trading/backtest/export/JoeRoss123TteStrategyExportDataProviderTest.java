package it.andreademartini.trading.backtest.export;

import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.config.SameBarExecutionPolicy;
import it.andreademartini.trading.config.StrategyType;
import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.strategy.StrategyTradeMode;
import it.andreademartini.trading.strategy.TradeDirection;
import it.andreademartini.trading.strategy.joeross.JoeRossEvent;
import it.andreademartini.trading.strategy.joeross.JoeRossEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JoeRoss123TteStrategyExportDataProviderTest {

    @Test
    void exportsPoint3TteMarkersWithoutRossHookColumns() {
        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        BacktestExportBar bar = new BacktestExportBar(
                5, time, "5", 104, 106, 100, 102, 1_000, BarInterval.DAILY);
        BacktestExportRun run = new BacktestExportRun(
                "TTE-1", "AAPL", StrategyType.JOE_ROSS_123_TTE.name(),
                TradeDirection.LONG.name(), BarInterval.DAILY, time, time,
                List.of(bar), List.of(), List.of(),
                new BacktestExportMetrics(
                        10_000, 10_000, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0),
                new BacktestExportParameters(
                        MarketDataProvider.IBKR, StrategyType.JOE_ROSS_123_TTE,
                        StrategyTradeMode.LONG_ONLY, 0, 0, 0,
                        10_000, 0, 0, SameBarExecutionPolicy.STOP_FIRST,
                        "causal TTE"),
                List.of(
                        event(JoeRossEventType.JOE_ROSS_POINT3_CORRECTION_STARTED,
                                time, 100),
                        event(JoeRossEventType.TTE_BAR_1_CREATED, time, 106.01),
                        event(JoeRossEventType.TTE_ENTRY_CANDIDATE, time, 106.01),
                        event(JoeRossEventType.INITIAL_STOP_CREATED, time, 99.99)),
                time);

        JoeRoss123TteStrategyExportDataProvider provider =
                new JoeRoss123TteStrategyExportDataProvider();

        assertThat(provider.supports(run)).isTrue();
        assertThat(provider.columns(run))
                .containsExactly("P1", "P2", "P3", "Point3Correction",
                        "TTE1", "TTE2", "TTE3", "TteCandidate", "TteEntry", "Stop")
                .noneMatch(column -> column.toLowerCase().contains("rosshook"));
        assertThat(provider.values(run, bar)).containsExactly(
                null, null, null, 100.0, 106.01, null, null, 106.01, null, 99.99);
    }

    private JoeRossEvent event(JoeRossEventType type, Instant time, double price) {
        return new JoeRossEvent(type, TradeDirection.LONG, 5, time, price, null);
    }
}
