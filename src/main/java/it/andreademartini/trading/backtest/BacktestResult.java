package it.andreademartini.trading.backtest;

import it.andreademartini.trading.marketdata.HistoricalBar;

import java.util.List;

public record BacktestResult(
        BacktestRequest request,
        List<HistoricalBar> bars) {

    public BacktestResult {
        bars = bars == null ? List.of() : List.copyOf(bars);
    }
}
