package it.andreademartini.trading.backtest.export;

import it.andreademartini.trading.marketdata.BarInterval;

import java.time.Instant;

public record BacktestExportBar(
        int index,
        Instant timestamp,
        String label,
        double open,
        double high,
        double low,
        double close,
        long volume,
        BarInterval interval) {
}
