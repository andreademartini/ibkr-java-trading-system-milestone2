package it.andreademartini.trading.backtest.export;

import it.andreademartini.trading.config.StrategyType;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class MovingAverageStrategyExportDataProvider
        implements StrategyExportDataProvider {

    @Override
    public boolean supports(BacktestExportRun run) {
        return run != null
                && run.parameters() != null
                && run.parameters().strategy() == StrategyType.MOVING_AVERAGE_CROSSOVER;
    }

    @Override
    public List<String> columns(BacktestExportRun run) {
        return List.of(
                "MA" + run.parameters().fastPeriod(),
                "MA" + run.parameters().slowPeriod());
    }

    @Override
    public List<Object> values(BacktestExportRun run, BacktestExportBar bar) {
        return Arrays.asList(
                movingAverage(run, bar.index(), run.parameters().fastPeriod()),
                movingAverage(run, bar.index(), run.parameters().slowPeriod()));
    }

    private Double movingAverage(
            BacktestExportRun run,
            int index,
            int period) {
        if (run == null || run.bars().isEmpty() || period <= 0 || index < period - 1) {
            return null;
        }
        double sum = 0.0;
        int counted = 0;
        for (BacktestExportBar bar : run.bars()) {
            if (bar.index() > index) {
                break;
            }
            if (bar.index() > index - period) {
                sum += bar.close();
                counted++;
            }
        }
        return counted == period ? sum / period : null;
    }
}
