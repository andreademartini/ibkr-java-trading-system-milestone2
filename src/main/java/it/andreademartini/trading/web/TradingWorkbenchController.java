package it.andreademartini.trading.web;

import it.andreademartini.trading.config.MarketDataMode;
import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.config.StrategyType;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.HistoricalDataService;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.marketdata.HistoricalSeries;
import it.andreademartini.trading.marketdata.MarketDataService;
import it.andreademartini.trading.marketdata.MarketSnapshot;
import it.andreademartini.trading.risk.KillSwitchService;
import it.andreademartini.trading.strategy.ManualOnlyStrategy;
import it.andreademartini.trading.strategy.MovingAverageCrossoverStrategy;
import it.andreademartini.trading.strategy.SignalAction;
import it.andreademartini.trading.strategy.SignalService;
import it.andreademartini.trading.strategy.StrategyEngine;
import it.andreademartini.trading.strategy.TradingSignal;
import it.andreademartini.trading.strategy.TradingStrategy;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/workbench")
public class TradingWorkbenchController {

    private final TradingSystemProperties properties;
    private final MarketDataService marketDataService;
    private final HistoricalDataService historicalDataService;
    private final ActionProfileService actionProfileService;
    private final StrategyEngine strategyEngine;
    private final SignalService signalService;
    private final KillSwitchService killSwitchService;

    public TradingWorkbenchController(
            TradingSystemProperties properties,
            MarketDataService marketDataService,
            HistoricalDataService historicalDataService,
            ActionProfileService actionProfileService,
            StrategyEngine strategyEngine,
            SignalService signalService,
            KillSwitchService killSwitchService) {
        this.properties = properties;
        this.marketDataService = marketDataService;
        this.historicalDataService = historicalDataService;
        this.actionProfileService = actionProfileService;
        this.strategyEngine = strategyEngine;
        this.signalService = signalService;
        this.killSwitchService = killSwitchService;
    }

    @GetMapping("/config")
    public WorkbenchConfigResponse config() {
        return configResponse();
    }

    @PutMapping("/config")
    public WorkbenchConfigResponse updateConfig(
            @RequestBody WorkbenchConfigRequest request) {
        if (request.marketData() != null) {
            applyMarketDataConfig(request.marketData());
        }
        if (request.strategy() != null) {
            applyStrategyConfig(request.strategy());
        }
        strategyEngine.reset();
        return configResponse();
    }

    @PostMapping("/market-data/restart")
    public Map<String, Object> restartMarketData() {
        marketDataService.restart();
        return Map.of(
                "provider", properties.getMarketData().getProvider(),
                "marketData", marketDataService.snapshots());
    }

    @PostMapping("/market-data/manual-price")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MarketSnapshot publishManualPrice(
            @RequestBody ManualPriceRequest request) {
        return marketDataService.publishManualPrice(
                normalizeSymbol(request.symbol()),
                request.price());
    }

    @PostMapping("/historical/download")
    public HistoricalDownloadResponse downloadHistorical(
            @RequestBody HistoricalDownloadRequest request) {
        MarketDataProvider provider = request.provider() == null
                ? properties.getMarketData().getProvider()
                : request.provider();
        if (provider != MarketDataProvider.ALPHA_VANTAGE) {
            throw new IllegalArgumentException(
                    "Download storico supportato solo con provider ALPHA_VANTAGE");
        }

        String symbol = normalizeSymbol(request.symbol());
        String timeframe = normalizeTimeframe(request.timeframe());
        String outputSize = normalizeOutputSize(request.outputSize());
        int maxPoints = request.maxPoints() == null
                ? 100
                : Math.max(1, Math.min(5_000, request.maxPoints()));

        HistoricalSeries series = historicalDataService.loadOrRefresh(
                provider,
                symbol,
                timeframe,
                outputSize,
                maxPoints,
                Boolean.TRUE.equals(request.refresh()));

        return new HistoricalDownloadResponse(
                series.provider(),
                series.symbol(),
                series.timeframe(),
                series.outputSize(),
                series.bars(),
                series.closePrices(),
                series.source(),
                series.loadedAt(),
                series.message());
    }

    @GetMapping("/action-profiles")
    public ActionProfilesResponse actionProfiles() {
        return actionProfileService.response();
    }

    @PutMapping("/action-profiles")
    public ActionProfilesResponse updateActionProfiles(
            @RequestBody ActionProfilesRequest request) {
        return actionProfileService.save(request);
    }

    @PostMapping("/actions/manual-signal")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TradingSignal publishManualSignal(
            @RequestBody WorkbenchManualSignalRequest request) {
        TradingSignal signal = TradingSignal.create(
                normalizeSymbol(request.symbol()),
                request.action(),
                request.price(),
                request.quantity(),
                source("UI", request.provider(), request.strategy()),
                blankToDefault(request.reason(), "manual-workbench")
        );
        signalService.publish(signal, request.execute());
        return signal;
    }

    @PostMapping("/strategy/reset")
    public Map<String, Object> resetStrategy() {
        strategyEngine.reset();
        return Map.of("strategy", properties.getStrategy().getType());
    }

    @PostMapping("/strategy/simulate")
    public StrategySimulationResponse simulateStrategy(
            @RequestBody StrategySimulationRequest request) {
        String symbol = normalizeSymbol(request.symbol());
        if (request.prices() == null || request.prices().isEmpty()) {
            throw new IllegalArgumentException("Inserire almeno un prezzo");
        }

        TradingSystemProperties.StrategyProperties strategyProperties =
                simulationProperties(request);
        TradingStrategy strategy = createStrategy(request.strategy(), strategyProperties);

        List<TradingSignal> signals = new ArrayList<>();
        List<PricePoint> priceSeries = new ArrayList<>();
        Instant start = Instant.now().minus(Duration.ofDays(request.prices().size()));
        for (int index = 0; index < request.prices().size(); index++) {
            double price = request.prices().get(index);
            if (price <= 0) {
                throw new IllegalArgumentException("Tutti i prezzi devono essere positivi");
            }
            Instant timestamp = start.plus(Duration.ofDays(index));
            priceSeries.add(new PricePoint(index, timestamp, price));
            strategy.onPrice(symbol, price, timestamp)
                    .ifPresent(signals::add);
        }

        BacktestResult backtest = backtest(request, strategyProperties, priceSeries, signals);

        boolean publishSignals = Boolean.TRUE.equals(request.publishSignals());
        if (publishSignals) {
            for (TradingSignal signal : signals) {
                signalService.publish(signal, false);
            }
        }

        return new StrategySimulationResponse(
                request.provider() == null
                        ? properties.getMarketData().getProvider()
                        : request.provider(),
                request.strategy() == null
                        ? properties.getStrategy().getType()
                        : request.strategy(),
                symbol,
                request.prices().size(),
                request.prices().get(request.prices().size() - 1),
                signals,
                priceSeries,
                backtest.equityCurve(),
                backtest.trades(),
                backtest.metrics(),
                backtest.evaluationParameters(),
                publishSignals);
    }

    private void applyMarketDataConfig(MarketDataConfigRequest request) {
        TradingSystemProperties.MarketDataProperties marketData =
                properties.getMarketData();

        if (request.enabled() != null) {
            marketData.setEnabled(request.enabled());
        }
        if (request.provider() != null) {
            marketData.setProvider(request.provider());
        }
        if (request.mode() != null) {
            marketData.setMode(request.mode());
        }
        if (request.alphaVantage() != null) {
            TradingSystemProperties.AlphaVantageProperties alpha =
                    marketData.getAlphaVantage();
            if (request.alphaVantage().apiKey() != null
                    && !request.alphaVantage().apiKey().isBlank()) {
                alpha.setApiKey(request.alphaVantage().apiKey().trim());
            }
            if (request.alphaVantage().pollingIntervalSeconds() != null) {
                alpha.setPollingInterval(Duration.ofSeconds(
                        Math.max(3_600, request.alphaVantage().pollingIntervalSeconds())));
            }
        }
        if (request.instruments() != null && !request.instruments().isEmpty()) {
            marketData.setInstruments(request.instruments().stream()
                    .map(this::instrumentProperties)
                    .toList());
        }
    }

    private void applyStrategyConfig(StrategyConfigRequest request) {
        TradingSystemProperties.StrategyProperties strategy = properties.getStrategy();
        if (request.type() != null) {
            strategy.setType(request.type());
        }
        if (request.enabled() != null) {
            strategy.setEnabled(request.enabled());
        }
        if (request.autoExecuteSignals() != null) {
            strategy.setAutoExecuteSignals(request.autoExecuteSignals());
        }
        if (request.fastPeriod() != null) {
            strategy.setFastPeriod(request.fastPeriod());
        }
        if (request.slowPeriod() != null) {
            strategy.setSlowPeriod(request.slowPeriod());
        }
        if (strategy.getFastPeriod() >= strategy.getSlowPeriod()) {
            throw new IllegalArgumentException("slowPeriod deve essere maggiore di fastPeriod");
        }
        if (request.quantity() != null) {
            strategy.setQuantity(request.quantity());
        }
        if (request.signalCooldownSeconds() != null) {
            strategy.setSignalCooldown(Duration.ofSeconds(
                    Math.max(0, request.signalCooldownSeconds())));
        }
        if (request.takeProfitPercent() != null) {
            strategy.setTakeProfitPercent(request.takeProfitPercent());
        }
        if (request.trailingStopPercent() != null) {
            strategy.setTrailingStopPercent(request.trailingStopPercent());
        }
    }

    private TradingSystemProperties.InstrumentProperties instrumentProperties(
            InstrumentConfigRequest request) {
        TradingSystemProperties.InstrumentProperties instrument =
                new TradingSystemProperties.InstrumentProperties();
        instrument.setSymbol(normalizeSymbol(request.symbol()));
        instrument.setSecurityType(blankToDefault(request.securityType(), "STK"));
        instrument.setExchange(blankToDefault(request.exchange(), "SMART"));
        instrument.setCurrency(blankToDefault(request.currency(), "USD"));
        instrument.setPrimaryExchange(blankToDefault(request.primaryExchange(), "ARCA"));
        return instrument;
    }

    private TradingSystemProperties.StrategyProperties simulationProperties(
            StrategySimulationRequest request) {
        TradingSystemProperties.StrategyProperties source = properties.getStrategy();
        TradingSystemProperties.StrategyProperties strategy =
                new TradingSystemProperties.StrategyProperties();
        strategy.setType(request.strategy() == null ? source.getType() : request.strategy());
        strategy.setEnabled(true);
        strategy.setAutoExecuteSignals(false);
        strategy.setFastPeriod(request.fastPeriod() == null
                ? source.getFastPeriod()
                : request.fastPeriod());
        strategy.setSlowPeriod(request.slowPeriod() == null
                ? source.getSlowPeriod()
                : request.slowPeriod());
        if (strategy.getFastPeriod() >= strategy.getSlowPeriod()) {
            throw new IllegalArgumentException("slowPeriod deve essere maggiore di fastPeriod");
        }
        strategy.setQuantity(request.quantity() == null
                ? source.getQuantity()
                : request.quantity());
        strategy.setSignalCooldown(source.getSignalCooldown());
        strategy.setTakeProfitPercent(source.getTakeProfitPercent());
        strategy.setTrailingStopPercent(source.getTrailingStopPercent());
        return strategy;
    }

    private TradingStrategy createStrategy(
            StrategyType type,
            TradingSystemProperties.StrategyProperties strategyProperties) {
        StrategyType resolved = type == null ? properties.getStrategy().getType() : type;
        return switch (resolved) {
            case MANUAL_ONLY -> new ManualOnlyStrategy();
            case MOVING_AVERAGE_CROSSOVER ->
                    new MovingAverageCrossoverStrategy(strategyProperties);
        };
    }

    private BacktestResult backtest(
            StrategySimulationRequest request,
            TradingSystemProperties.StrategyProperties strategyProperties,
            List<PricePoint> priceSeries,
            List<TradingSignal> signals) {
        double initialCapital = request.initialCapital() == null
                ? 10_000.0
                : request.initialCapital();
        double commissionPerOrder = request.commissionPerOrder() == null
                ? 0.0
                : request.commissionPerOrder();
        double slippagePercent = request.slippagePercent() == null
                ? 0.0
                : request.slippagePercent();

        if (initialCapital <= 0) {
            throw new IllegalArgumentException("initialCapital deve essere positivo");
        }
        if (commissionPerOrder < 0) {
            throw new IllegalArgumentException("commissionPerOrder non puo' essere negativa");
        }
        if (slippagePercent < 0) {
            throw new IllegalArgumentException("slippagePercent non puo' essere negativo");
        }

        List<EquityPoint> equityCurve = new ArrayList<>();
        List<TradeResult> trades = new ArrayList<>();
        double cash = initialCapital;
        long positionQuantity = 0;
        double averageEntryPrice = 0.0;
        Instant entryAt = null;
        int entryIndex = -1;
        double entryCommission = 0.0;
        int signalIndex = 0;
        int barsInPosition = 0;
        double peakEquity = initialCapital;
        double maxDrawdownPercent = 0.0;

        for (PricePoint point : priceSeries) {
            while (signalIndex < signals.size()
                    && !signals.get(signalIndex).occurredAt().isAfter(point.timestamp())) {
                TradingSignal signal = signals.get(signalIndex++);
                if (signal.action() == SignalAction.BUY && positionQuantity == 0) {
                    long quantity = Math.max(1, signal.quantity());
                    double fillPrice = applySlippage(signal.price(), slippagePercent, true);
                    double cost = fillPrice * quantity + commissionPerOrder;
                    if (cost <= cash) {
                        cash -= cost;
                        positionQuantity = quantity;
                        averageEntryPrice = fillPrice;
                        entryAt = signal.occurredAt();
                        entryIndex = point.index();
                        entryCommission = commissionPerOrder;
                    }
                } else if (signal.action() == SignalAction.SELL && positionQuantity > 0) {
                    double fillPrice = applySlippage(signal.price(), slippagePercent, false);
                    double proceeds = fillPrice * positionQuantity - commissionPerOrder;
                    cash += proceeds;
                    double grossPnl = (fillPrice - averageEntryPrice) * positionQuantity;
                    double netPnl = grossPnl - entryCommission - commissionPerOrder;
                    trades.add(new TradeResult(
                            trades.size() + 1,
                            "LONG",
                            entryIndex,
                            point.index(),
                            entryAt,
                            signal.occurredAt(),
                            positionQuantity,
                            averageEntryPrice,
                            fillPrice,
                            grossPnl,
                            netPnl,
                            netPnl / (averageEntryPrice * positionQuantity) * 100.0));
                    positionQuantity = 0;
                    averageEntryPrice = 0.0;
                    entryAt = null;
                    entryIndex = -1;
                    entryCommission = 0.0;
                }
            }

            if (positionQuantity > 0) {
                barsInPosition++;
            }

            double equity = cash + positionQuantity * point.price();
            peakEquity = Math.max(peakEquity, equity);
            if (peakEquity > 0) {
                double drawdown = (peakEquity - equity) / peakEquity * 100.0;
                maxDrawdownPercent = Math.max(maxDrawdownPercent, drawdown);
            }
            equityCurve.add(new EquityPoint(
                    point.index(),
                    point.timestamp(),
                    equity,
                    cash,
                    positionQuantity,
                    point.price()));
        }

        double finalPrice = priceSeries.get(priceSeries.size() - 1).price();
        double finalEquity = cash + positionQuantity * finalPrice;
        PerformanceMetrics metrics = metrics(
                initialCapital,
                finalEquity,
                maxDrawdownPercent,
                trades,
                positionQuantity,
                positionQuantity * finalPrice,
                barsInPosition,
                priceSeries.size(),
                priceSeries.get(0).price(),
                finalPrice);

        EvaluationParameters parameters = new EvaluationParameters(
                request.provider() == null
                        ? properties.getMarketData().getProvider()
                        : request.provider(),
                request.strategy() == null
                        ? properties.getStrategy().getType()
                        : request.strategy(),
                strategyProperties.getFastPeriod(),
                strategyProperties.getSlowPeriod(),
                strategyProperties.getQuantity(),
                initialCapital,
                commissionPerOrder,
                slippagePercent,
                "Long-only: BUY apre una posizione, SELL chiude la posizione; eventuale posizione aperta resta valorizzata a mercato");

        return new BacktestResult(equityCurve, trades, metrics, parameters);
    }

    private double applySlippage(double price, double slippagePercent, boolean buy) {
        double factor = slippagePercent / 100.0;
        return buy ? price * (1.0 + factor) : price * (1.0 - factor);
    }

    private PerformanceMetrics metrics(
            double initialCapital,
            double finalEquity,
            double maxDrawdownPercent,
            List<TradeResult> trades,
            long openPositionQuantity,
            double openPositionMarketValue,
            int barsInPosition,
            int totalBars,
            double firstPrice,
            double finalPrice) {
        double grossProfit = trades.stream()
                .mapToDouble(TradeResult::netPnl)
                .filter(value -> value > 0)
                .sum();
        double grossLoss = trades.stream()
                .mapToDouble(TradeResult::netPnl)
                .filter(value -> value < 0)
                .sum();
        long winners = trades.stream()
                .filter(trade -> trade.netPnl() > 0)
                .count();
        long losers = trades.stream()
                .filter(trade -> trade.netPnl() < 0)
                .count();
        double netProfit = finalEquity - initialCapital;
        double tradeCount = trades.size();
        double exposurePercent = totalBars == 0
                ? 0.0
                : barsInPosition * 100.0 / totalBars;
        double buyAndHoldReturnPercent = firstPrice <= 0
                ? 0.0
                : (finalPrice - firstPrice) / firstPrice * 100.0;

        return new PerformanceMetrics(
                initialCapital,
                finalEquity,
                netProfit,
                netProfit / initialCapital * 100.0,
                maxDrawdownPercent,
                trades.size(),
                winners,
                losers,
                tradeCount == 0 ? 0.0 : winners * 100.0 / tradeCount,
                grossProfit,
                grossLoss,
                grossLoss == 0.0
                        ? 0.0
                        : grossProfit / Math.abs(grossLoss),
                tradeCount == 0 ? 0.0 : trades.stream()
                        .mapToDouble(TradeResult::netPnl)
                        .average()
                        .orElse(0.0),
                exposurePercent,
                openPositionQuantity,
                openPositionMarketValue,
                buyAndHoldReturnPercent);
    }

    private WorkbenchConfigResponse configResponse() {
        TradingSystemProperties.MarketDataProperties marketData =
                properties.getMarketData();
        TradingSystemProperties.AlphaVantageProperties alpha =
                marketData.getAlphaVantage();
        TradingSystemProperties.StrategyProperties strategy = properties.getStrategy();

        return new WorkbenchConfigResponse(
                Arrays.asList(MarketDataProvider.values()),
                Arrays.asList(MarketDataMode.values()),
                Arrays.asList(StrategyType.values()),
                new MarketDataConfigResponse(
                        marketData.isEnabled(),
                        marketData.getProvider(),
                        marketData.getMode(),
                        new AlphaVantageConfigResponse(
                                alpha.getApiKey() != null && !alpha.getApiKey().isBlank(),
                                alpha.getPollingInterval().toSeconds()),
                        marketData.getInstruments().stream()
                                .map(InstrumentConfigResponse::from)
                                .toList()),
                new StrategyConfigResponse(
                        strategy.getType(),
                        strategy.isEnabled(),
                        strategy.isAutoExecuteSignals(),
                        strategy.getFastPeriod(),
                        strategy.getSlowPeriod(),
                        strategy.getQuantity(),
                        strategy.getSignalCooldown().toSeconds(),
                        strategy.getTakeProfitPercent(),
                        strategy.getTrailingStopPercent()),
                killSwitchService.isActive());
    }

    private String source(String prefix, MarketDataProvider provider, StrategyType strategy) {
        MarketDataProvider resolvedProvider = provider == null
                ? properties.getMarketData().getProvider()
                : provider;
        StrategyType resolvedStrategy = strategy == null
                ? properties.getStrategy().getType()
                : strategy;
        return prefix + "_" + resolvedProvider + "_" + resolvedStrategy;
    }

    private String normalizeSymbol(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("symbol obbligatorio");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeTimeframe(String value) {
        if (value == null || value.isBlank()) {
            return "DAILY";
        }
        return value.trim();
    }

    private String normalizeOutputSize(String value) {
        if (value == null || value.isBlank()) {
            return "compact";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record WorkbenchConfigRequest(
            MarketDataConfigRequest marketData,
            StrategyConfigRequest strategy) {
    }

    public record MarketDataConfigRequest(
            Boolean enabled,
            MarketDataProvider provider,
            MarketDataMode mode,
            AlphaVantageConfigRequest alphaVantage,
            List<InstrumentConfigRequest> instruments) {
    }

    public record AlphaVantageConfigRequest(
            String apiKey,
            Long pollingIntervalSeconds) {
    }

    public record InstrumentConfigRequest(
            String symbol,
            String securityType,
            String exchange,
            String currency,
            String primaryExchange) {
    }

    public record StrategyConfigRequest(
            StrategyType type,
            Boolean enabled,
            Boolean autoExecuteSignals,
            Integer fastPeriod,
            Integer slowPeriod,
            Long quantity,
            Long signalCooldownSeconds,
            Double takeProfitPercent,
            Double trailingStopPercent) {
    }

    public record ManualPriceRequest(
            String symbol,
            double price,
            MarketDataProvider provider,
            StrategyType strategy) {
    }

    public record WorkbenchManualSignalRequest(
            String symbol,
            SignalAction action,
            double price,
            long quantity,
            String reason,
            boolean execute,
            MarketDataProvider provider,
            StrategyType strategy) {
    }

    public record HistoricalDownloadRequest(
            String symbol,
            MarketDataProvider provider,
            String timeframe,
            String outputSize,
            Integer maxPoints,
            Boolean refresh) {
    }

    public record HistoricalDownloadResponse(
            MarketDataProvider provider,
            String symbol,
            String timeframe,
            String outputSize,
            List<HistoricalBar> bars,
            List<Double> closePrices,
            String source,
            Instant loadedAt,
            String message) {
    }

    public record StrategySimulationRequest(
            String symbol,
            MarketDataProvider provider,
            StrategyType strategy,
            List<Double> prices,
            Integer fastPeriod,
            Integer slowPeriod,
            Long quantity,
            Double initialCapital,
            Double commissionPerOrder,
            Double slippagePercent,
            Boolean publishSignals) {
    }

    public record StrategySimulationResponse(
            MarketDataProvider provider,
            StrategyType strategy,
            String symbol,
            int inputCount,
            double lastPrice,
            List<TradingSignal> signals,
            List<PricePoint> priceSeries,
            List<EquityPoint> equityCurve,
            List<TradeResult> trades,
            PerformanceMetrics metrics,
            EvaluationParameters evaluationParameters,
            boolean publishedSignals) {
    }

    public record PricePoint(
            int index,
            Instant timestamp,
            double price) {
    }

    public record EquityPoint(
            int index,
            Instant timestamp,
            double equity,
            double cash,
            long positionQuantity,
            double price) {
    }

    public record TradeResult(
            int number,
            String side,
            int entryIndex,
            int exitIndex,
            Instant entryAt,
            Instant exitAt,
            long quantity,
            double entryPrice,
            double exitPrice,
            double grossPnl,
            double netPnl,
            double returnPercent) {
    }

    public record PerformanceMetrics(
            double initialCapital,
            double finalEquity,
            double netProfit,
            double totalReturnPercent,
            double maxDrawdownPercent,
            int tradeCount,
            long winningTrades,
            long losingTrades,
            double winRatePercent,
            double grossProfit,
            double grossLoss,
            double profitFactor,
            double averageTrade,
            double exposurePercent,
            long openPositionQuantity,
            double openPositionMarketValue,
            double buyAndHoldReturnPercent) {
    }

    public record EvaluationParameters(
            MarketDataProvider provider,
            StrategyType strategy,
            int fastPeriod,
            int slowPeriod,
            long quantity,
            double initialCapital,
            double commissionPerOrder,
            double slippagePercent,
            String executionModel) {
    }

    private record BacktestResult(
            List<EquityPoint> equityCurve,
            List<TradeResult> trades,
            PerformanceMetrics metrics,
            EvaluationParameters evaluationParameters) {
    }

    public record WorkbenchConfigResponse(
            List<MarketDataProvider> providerOptions,
            List<MarketDataMode> modeOptions,
            List<StrategyType> strategyOptions,
            MarketDataConfigResponse marketData,
            StrategyConfigResponse strategy,
            boolean killSwitchActive) {
    }

    public record MarketDataConfigResponse(
            boolean enabled,
            MarketDataProvider provider,
            MarketDataMode mode,
            AlphaVantageConfigResponse alphaVantage,
            List<InstrumentConfigResponse> instruments) {
    }

    public record AlphaVantageConfigResponse(
            boolean apiKeyConfigured,
            long pollingIntervalSeconds) {
    }

    public record InstrumentConfigResponse(
            String symbol,
            String securityType,
            String exchange,
            String currency,
            String primaryExchange) {

        static InstrumentConfigResponse from(
                TradingSystemProperties.InstrumentProperties instrument) {
            return new InstrumentConfigResponse(
                    instrument.getSymbol(),
                    instrument.getSecurityType(),
                    instrument.getExchange(),
                    instrument.getCurrency(),
                    instrument.getPrimaryExchange());
        }
    }

    public record StrategyConfigResponse(
            StrategyType type,
            boolean enabled,
            boolean autoExecuteSignals,
            int fastPeriod,
            int slowPeriod,
            long quantity,
            long signalCooldownSeconds,
            double takeProfitPercent,
            double trailingStopPercent) {
    }
}
