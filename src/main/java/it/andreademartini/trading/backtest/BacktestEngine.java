package it.andreademartini.trading.backtest;

import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.marketdata.HistoricalDataService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class BacktestEngine {

    private final HistoricalDataService historicalDataService;

    public BacktestEngine(HistoricalDataService historicalDataService) {
        this.historicalDataService = historicalDataService;
    }

    public BacktestResult run(BacktestRequest request) {
        BarInterval interval = request.interval() == null
                ? BarInterval.DAILY
                : request.interval();
        List<HistoricalBar> bars = historicalDataService.load(
                normalizeSymbol(request.symbol()),
                request.from(),
                request.to(),
                interval);
        return new BacktestResult(request, bars);
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol obbligatorio");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
