package it.andreademartini.trading.backtest.export;

import java.util.List;

public interface StrategyExportDataProvider {

    boolean supports(BacktestExportRun run);

    List<String> columns(BacktestExportRun run);

    List<Object> values(BacktestExportRun run, BacktestExportBar bar);
}
