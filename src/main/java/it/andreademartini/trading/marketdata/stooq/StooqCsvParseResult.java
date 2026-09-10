package it.andreademartini.trading.marketdata.stooq;

import it.andreademartini.trading.marketdata.HistoricalBar;

import java.util.List;

public record StooqCsvParseResult(
        List<HistoricalBar> bars,
        int rowsRead,
        int rowsValid,
        int rowsRejected) {

    public StooqCsvParseResult {
        bars = bars == null ? List.of() : List.copyOf(bars);
    }
}
