package it.andreademartini.trading.backtest.export;

import it.andreademartini.trading.config.StrategyType;
import it.andreademartini.trading.strategy.joeross.JoeRossEvent;
import it.andreademartini.trading.strategy.joeross.JoeRossEventType;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class JoeRoss123TteStrategyExportDataProvider
        implements StrategyExportDataProvider {

    private static final List<String> COLUMNS = List.of(
            "P1", "P2", "P3", "Point3Correction",
            "TTE1", "TTE2", "TTE3", "TteCandidate", "TteEntry", "Stop");

    @Override
    public boolean supports(BacktestExportRun run) {
        return run != null && run.parameters() != null
                && run.parameters().strategy() == StrategyType.JOE_ROSS_123_TTE;
    }

    @Override
    public List<String> columns(BacktestExportRun run) {
        return COLUMNS;
    }

    @Override
    public List<Object> values(BacktestExportRun run, BacktestExportBar bar) {
        return Arrays.asList(
                price(run, bar, JoeRossEventType.JOE_ROSS_POINT1_FOUND),
                price(run, bar, JoeRossEventType.JOE_ROSS_POINT2_FOUND),
                price(run, bar, JoeRossEventType.POINT3_CONFIRMED),
                price(run, bar, JoeRossEventType.JOE_ROSS_POINT3_CORRECTION_STARTED),
                price(run, bar, JoeRossEventType.TTE_BAR_1_CREATED),
                price(run, bar, JoeRossEventType.TTE_BAR_2_CREATED),
                price(run, bar, JoeRossEventType.TTE_BAR_3_CREATED),
                priceAny(run, bar, JoeRossEventType.TTE_ENTRY_CANDIDATE,
                        JoeRossEventType.TTE_CANDIDATE_UPDATED),
                price(run, bar, JoeRossEventType.TTE_TRIGGERED),
                price(run, bar, JoeRossEventType.INITIAL_STOP_CREATED));
    }

    private Double price(
            BacktestExportRun run,
            BacktestExportBar bar,
            JoeRossEventType type) {
        return run.strategyEvents().stream()
                .filter(event -> event.barIndex() == bar.index() && event.type() == type)
                .map(JoeRossEvent::price)
                .findFirst()
                .orElse(null);
    }

    private Double priceAny(
            BacktestExportRun run,
            BacktestExportBar bar,
            JoeRossEventType first,
            JoeRossEventType second) {
        return run.strategyEvents().stream()
                .filter(event -> event.barIndex() == bar.index())
                .filter(event -> event.type() == first || event.type() == second)
                .map(JoeRossEvent::price)
                .findFirst()
                .orElse(null);
    }
}
