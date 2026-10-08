package it.andreademartini.trading.backtest.patternv1;

import it.andreademartini.trading.backtest.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.research.pattern.PatternDetector;
import it.andreademartini.trading.risk.PositionSizingService;
import it.andreademartini.trading.strategy.patternv1.PatternV1Properties;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;

@Service
public class PatternV1BacktestService {
    private final BacktestEngine backtestEngine;
    private final HistoricalDataService historicalData;
    private final PatternV1Properties configuration;
    private final TradingSystemProperties trading;
    private final PatternV1PortfolioEngine portfolio;
    private final PatternV1RunStore store;
    public PatternV1BacktestService(BacktestEngine backtestEngine, HistoricalDataService historicalData,
            PatternV1Properties configuration, TradingSystemProperties trading, PositionSizingService sizing,
            List<PatternDetector> detectors, PatternV1RunStore store) {
        this.backtestEngine = backtestEngine; this.historicalData = historicalData;
        this.configuration = configuration; this.trading = trading;
        this.portfolio = new PatternV1PortfolioEngine(sizing, detectors); this.store = store;
    }
    public PatternV1Properties configuration() { return configuration; }
    public PatternV1Result run(PatternV1Request request) {
        if (request == null) request = new PatternV1Request(null, null, null, null, null, null, null);
        configuration.validate();
        if (!configuration.isEnabled()) throw new IllegalArgumentException("PATTERN_V1 disabled");
        if (request.timeframe() != null && request.timeframe() != BarInterval.DAILY) throw new IllegalArgumentException("DAILY only");
        if (request.symbols() != null && request.symbols().stream().anyMatch(s -> s == null || s.isBlank())) {
            throw new IllegalArgumentException("Symbols must be nonblank");
        }
        List<String> symbols = request.symbols() == null || request.symbols().isEmpty()
                ? configuration.getSymbols().keySet().stream().sorted().toList()
                : request.symbols().stream().map(s -> s.trim().toUpperCase(Locale.ROOT)).distinct().sorted().toList();
        LocalDate start = request.startDate() == null ? LocalDate.of(2010, 1, 1) : request.startDate();
        LocalDate end = request.endDate();
        if (end == null) end = symbols.stream().filter(configuration.getSymbols()::containsKey)
                .map(s -> historicalData.status(s, BarInterval.DAILY).lastDate()).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(LocalDate.now().minusDays(1));
        // The current daily candle is never a completed historical signal bar.
        end = end.isBefore(LocalDate.now()) ? end : LocalDate.now().minusDays(1);
        if (end.isBefore(start)) throw new IllegalArgumentException("End date before start date");
        var normalized = new PatternV1Request(symbols, start, end, BarInterval.DAILY, request.initialCapital(),
                request.commissionPerOrder(), request.slippagePercent(), request.maxOrderValue(), request.maxQuantity(), request.commission());
        PatternV1Result result = backtestEngine.run(normalized, configuration, trading.getRisk(), portfolio);
        store.save(result);
        return result;
    }
    static TradingSystemProperties.RiskProperties riskSnapshot(TradingSystemProperties.RiskProperties source) {
        var copy = new TradingSystemProperties.RiskProperties();
        copy.setTradingCapital(source.getTradingCapital()); copy.setRiskPerTradePercent(source.getRiskPerTradePercent());
        copy.setAllowLong(source.isAllowLong()); copy.setAllowShortSelling(source.isAllowShortSelling());
        copy.setUseLeverage(source.isUseLeverage()); copy.setMaxLongNotional(source.getMaxLongNotional());
        copy.setMaxShortNotional(source.getMaxShortNotional()); copy.setMaxOrderValue(source.getMaxOrderValue());
        copy.setMaxPositionValue(source.getMaxPositionValue()); copy.setMaxQuantityPerOrder(source.getMaxQuantityPerOrder());
        copy.setCommissionPerOrder(source.getCommissionPerOrder()); copy.setSlippagePercent(source.getSlippagePercent());
        copy.setTransactionCostsPerShare(source.getTransactionCostsPerShare());
        return copy;
    }
}
