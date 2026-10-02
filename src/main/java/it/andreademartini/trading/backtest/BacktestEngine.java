package it.andreademartini.trading.backtest;

import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.marketdata.HistoricalDataService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import it.andreademartini.trading.backtest.patternv1.PatternV1Request;
import it.andreademartini.trading.backtest.patternv1.PatternV1Result;
import it.andreademartini.trading.backtest.patternv1.PatternV1PortfolioEngine;
import it.andreademartini.trading.strategy.patternv1.PatternV1Properties;
import it.andreademartini.trading.config.TradingSystemProperties;

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

    /** Separate multi-symbol execution path; existing single-symbol loaders are unchanged. */
    public PatternV1Result run(PatternV1Request request, PatternV1Properties configuration,
            TradingSystemProperties.RiskProperties risk, PatternV1PortfolioEngine executionEngine) {
        configuration.validate();
        if (!configuration.isEnabled()) throw new IllegalArgumentException("PATTERN_V1 disabled");
        if (request.timeframe() != BarInterval.DAILY || request.startDate() == null || request.endDate() == null) {
            throw new IllegalArgumentException("PATTERN_V1 requires normalized DAILY date range");
        }
        if (request.endDate().isBefore(request.startDate())) throw new IllegalArgumentException("Invalid date range");
        Map<String, List<HistoricalBar>> bars = new TreeMap<>();
        for (String value : request.symbols()) {
            String symbol = normalizeSymbol(value);
            bars.put(symbol, configuration.getSymbols().containsKey(symbol)
                    ? run(new BacktestRequest(symbol, request.startDate(), request.endDate(), BarInterval.DAILY)).bars()
                    : List.of());
        }
        return executionEngine.run(request, configuration, risk, bars);
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol obbligatorio");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
