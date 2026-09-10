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

class JoeRossStrategyExportDataProviderTest {

    @Test
    void associatesJoeRossEventsWithTheirHistoricalBarsAndColumns() {
        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        BacktestExportBar bar = new BacktestExportBar(
                4, time, "4", 100, 110, 95, 105, 0, BarInterval.DAILY);
        BacktestExportRun run = new BacktestExportRun(
                "JR-1", "AAPL", StrategyType.JOE_ROSS_123_RH_TTE.name(),
                TradeDirection.LONG.name(), BarInterval.DAILY, time, time,
                List.of(bar), List.of(), List.of(),
                new BacktestExportMetrics(
                        10_000, 10_000, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0),
                new BacktestExportParameters(
                        MarketDataProvider.IBKR,
                        StrategyType.JOE_ROSS_123_RH_TTE,
                        StrategyTradeMode.LONG_ONLY,
                        0, 0, 0, 10_000, 0, 0,
                        SameBarExecutionPolicy.STOP_FIRST,
                        "Joe Ross OHLC"),
                List.of(
                        new JoeRossEvent(JoeRossEventType.JOE_ROSS_POINT1_FOUND,
                                TradeDirection.LONG, 4, time, 95, null),
                        new JoeRossEvent(JoeRossEventType.TTE_ENTRY_CANDIDATE,
                                TradeDirection.LONG, 4, time, 108.01, null)),
                time);
        JoeRossStrategyExportDataProvider provider =
                new JoeRossStrategyExportDataProvider();

        assertThat(provider.supports(run)).isTrue();
        assertThat(provider.columns(run)).containsExactly(
                "JoeRossP1", "JoeRossP2", "JoeRossP3", "Point2Breakout",
                "RossHook", "TteCandidate", "TteEntry");
        assertThat(provider.values(run, bar)).containsExactly(
                95.0, null, null, null, null, 108.01, null);
    }
}
