package it.andreademartini.trading.backtest.export;

import it.andreademartini.trading.config.StrategyType;
import it.andreademartini.trading.strategy.joeross.JoeRossEvent;
import it.andreademartini.trading.strategy.joeross.JoeRossEventType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Arrays;

@Component
public class JoeRossStrategyExportDataProvider
        implements StrategyExportDataProvider {

    private static final List<String> COLUMNS = List.of(
            "JoeRossP1",
            "JoeRossP2",
            "JoeRossP3",
            "Point2Breakout",
            "RossHook",
            "TteCandidate",
            "TteEntry");

    @Override
    public boolean supports(BacktestExportRun run) {
        return run != null
                && run.parameters() != null
                && run.parameters().strategy() == StrategyType.JOE_ROSS_123_RH_TTE;
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
                price(run, bar, JoeRossEventType.JOE_ROSS_POINT3_FOUND),
                price(run, bar, JoeRossEventType.POINT2_BREAKOUT),
                price(run, bar, JoeRossEventType.ROSS_HOOK_CREATED),
                price(run, bar, JoeRossEventType.TTE_ENTRY_CANDIDATE),
                price(run, bar, JoeRossEventType.TTE_TRIGGERED));
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
}
