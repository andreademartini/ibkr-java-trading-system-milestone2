package it.andreademartini.trading.web;

import it.andreademartini.trading.config.BacktestProperties;
import it.andreademartini.trading.config.MarketDataMode;
import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.config.SameBarExecutionPolicy;
import it.andreademartini.trading.config.StrategyType;
import it.andreademartini.trading.config.TradingMode;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.backtest.export.BacktestExportBar;
import it.andreademartini.trading.backtest.export.BacktestExportMetrics;
import it.andreademartini.trading.backtest.export.BacktestExportParameters;
import it.andreademartini.trading.backtest.export.BacktestExportRun;
import it.andreademartini.trading.backtest.export.BacktestExportTrade;
import it.andreademartini.trading.backtest.export.BacktestExportTradeEvent;
import it.andreademartini.trading.backtest.export.BacktestRunRegistry;
import it.andreademartini.trading.ibkr.IbkrConnectionService;
import it.andreademartini.trading.ibkr.IbkrConnectionTestResult;
import it.andreademartini.trading.marketdata.HistoricalDataService;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.marketdata.HistoricalSeries;
import it.andreademartini.trading.marketdata.MarketDataService;
import it.andreademartini.trading.orders.ProgressiveStopLossService;
import it.andreademartini.trading.orders.StopAdjustmentResult;
import it.andreademartini.trading.risk.KillSwitchService;
import it.andreademartini.trading.risk.PositionSizingResult;
import it.andreademartini.trading.risk.PositionSizingService;
import it.andreademartini.trading.risk.RiskRejectedException;
import it.andreademartini.trading.risk.TakeProfitAllocationService;
import it.andreademartini.trading.orders.TakeProfitAllocation;
import it.andreademartini.trading.orders.TakeProfitLevel;
import it.andreademartini.trading.orders.TradeStopState;
import it.andreademartini.trading.strategy.ManualOnlyStrategy;
import it.andreademartini.trading.strategy.MovingAverageCrossoverStrategy;
import it.andreademartini.trading.strategy.SignalAction;
import it.andreademartini.trading.strategy.SignalService;
import it.andreademartini.trading.strategy.StrategyTradeMode;
import it.andreademartini.trading.strategy.TradeDirection;
import it.andreademartini.trading.strategy.StrategyEngine;
import it.andreademartini.trading.strategy.TradingSignal;
import it.andreademartini.trading.strategy.TradingStrategy;
import it.andreademartini.trading.strategy.StrategyOrderDetails;
import it.andreademartini.trading.strategy.joeross.JoeRoss123RossHookTteStrategy;
import it.andreademartini.trading.strategy.joeross.JoeRossBarResult;
import it.andreademartini.trading.strategy.joeross.JoeRossEvent;
import it.andreademartini.trading.strategy.joeross.JoeRossPatternStatistics;
import it.andreademartini.trading.strategy.joeross.JoeRossParameters;
import it.andreademartini.trading.strategy.joeross.JoeRossSignalAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/workbench")
public class TradingWorkbenchController {

    private static final MathContext MATH = MathContext.DECIMAL64;

    private final TradingSystemProperties properties;
    private final BacktestProperties backtestProperties;
    private final IbkrConnectionService connectionService;
    private final MarketDataService marketDataService;
    private final HistoricalDataService historicalDataService;
    private final ActionProfileService actionProfileService;
    private final StrategyEngine strategyEngine;
    private final SignalService signalService;
    private final KillSwitchService killSwitchService;
    private final PositionSizingService positionSizingService;
    private final TakeProfitAllocationService takeProfitAllocationService;
    private final ProgressiveStopLossService progressiveStopLossService;
    private final BacktestRunRegistry backtestRunRegistry;

    @Autowired
    public TradingWorkbenchController(
            TradingSystemProperties properties,
            BacktestProperties backtestProperties,
            IbkrConnectionService connectionService,
            MarketDataService marketDataService,
            HistoricalDataService historicalDataService,
            ActionProfileService actionProfileService,
            StrategyEngine strategyEngine,
            SignalService signalService,
            KillSwitchService killSwitchService,
            PositionSizingService positionSizingService,
            TakeProfitAllocationService takeProfitAllocationService,
            ProgressiveStopLossService progressiveStopLossService,
            BacktestRunRegistry backtestRunRegistry) {
        this.properties = properties;
        this.backtestProperties = backtestProperties;
        this.connectionService = connectionService;
        this.marketDataService = marketDataService;
        this.historicalDataService = historicalDataService;
        this.actionProfileService = actionProfileService;
        this.strategyEngine = strategyEngine;
        this.signalService = signalService;
        this.killSwitchService = killSwitchService;
        this.positionSizingService = positionSizingService;
        this.takeProfitAllocationService = takeProfitAllocationService;
        this.progressiveStopLossService = progressiveStopLossService;
        this.backtestRunRegistry = backtestRunRegistry;
    }

    public TradingWorkbenchController(
            TradingSystemProperties properties,
            BacktestProperties backtestProperties,
            IbkrConnectionService connectionService,
            MarketDataService marketDataService,
            HistoricalDataService historicalDataService,
            ActionProfileService actionProfileService,
            StrategyEngine strategyEngine,
            SignalService signalService,
            KillSwitchService killSwitchService,
            PositionSizingService positionSizingService,
            TakeProfitAllocationService takeProfitAllocationService,
            ProgressiveStopLossService progressiveStopLossService) {
        this(
                properties,
                backtestProperties,
                connectionService,
                marketDataService,
                historicalDataService,
                actionProfileService,
                strategyEngine,
                signalService,
                killSwitchService,
                positionSizingService,
                takeProfitAllocationService,
                progressiveStopLossService,
                new BacktestRunRegistry());
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
        if (request.ibkr() != null) {
            applyIbkrConfig(request.ibkr());
        }
        if (request.strategy() != null) {
            applyStrategyConfig(request.strategy());
        }
        if (request.runtime() != null) {
            applyRuntimeConfig(request.runtime());
        }
        if (request.risk() != null) {
            applyRiskConfig(request.risk());
        }
        if (request.takeProfit() != null) {
            applyTakeProfitConfig(request.takeProfit());
        }
        if (request.orders() != null) {
            applyOrdersConfig(request.orders());
        }
        strategyEngine.reset();
        return configResponse();
    }

    @PostMapping("/market-data/restart")
    public Map<String, Object> restartMarketData() {
        marketDataService.restart();
        return Map.of(
                "provider", MarketDataProvider.IBKR,
                "marketData", marketDataService.snapshots());
    }

    @PostMapping("/ibkr/reconnect")
    public Map<String, Object> reconnectIbkr() {
        connectionService.disconnect();
        connectionService.connect();
        marketDataService.restart();
        return Map.of(
                "connectionState", connectionService.getState(),
                "marketData", marketDataService.snapshots());
    }

    @PostMapping("/ibkr/test")
    public IbkrConnectionTestResult testIbkrConnection() {
        return connectionService.testConnection();
    }

    @PostMapping("/historical/download")
    public HistoricalDownloadResponse downloadHistorical(
            @RequestBody HistoricalDownloadRequest request) {
        MarketDataProvider provider = MarketDataProvider.IBKR;

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
        List<PricePoint> priceSeries = simulationPriceSeries(request);

        TradingSystemProperties.StrategyProperties strategyProperties =
                simulationProperties(request);
        TradingStrategy strategy = createStrategy(request.strategy(), strategyProperties);

        JoeRossAnalysis joeRossAnalysis = strategy instanceof JoeRoss123RossHookTteStrategy joeRoss
                ? analyzeJoeRoss(symbol, joeRoss, priceSeries, strategyProperties.getQuantity())
                : JoeRossAnalysis.empty();
        List<TradingSignal> signals = new ArrayList<>(joeRossAnalysis.signals());
        if (!(strategy instanceof JoeRoss123RossHookTteStrategy)) {
            for (PricePoint point : priceSeries) {
                strategy.onPrice(symbol, point.close(), point.timestamp())
                        .ifPresent(signals::add);
            }
        }

        BacktestResult backtest = backtest(request, strategyProperties, priceSeries, signals);
        String backtestId = UUID.randomUUID().toString();
        backtestRunRegistry.register(exportRun(
                backtestId,
                request,
                priceSeries,
                signals,
                backtest,
                joeRossAnalysis.events()));

        boolean publishSignals = Boolean.TRUE.equals(request.publishSignals());
        if (publishSignals) {
            for (TradingSignal signal : signals) {
                signalService.publish(signal, false);
            }
        }

        return new StrategySimulationResponse(
                backtestId,
                request.provider() == null
                        ? properties.getMarketData().getProvider()
                        : request.provider(),
                request.strategy() == null
                        ? properties.getStrategy().getType()
                        : request.strategy(),
                symbol,
                priceSeries.size(),
                priceSeries.get(priceSeries.size() - 1).close(),
                signals,
                priceSeries,
                backtest.equityCurve(),
                backtest.trades(),
                backtest.metrics(),
                backtest.evaluationParameters(),
                publishSignals,
                joeRossAnalysis.events(),
                joeRossAnalysis.statistics());
    }

    private JoeRossAnalysis analyzeJoeRoss(
            String symbol,
            JoeRoss123RossHookTteStrategy strategy,
            List<PricePoint> priceSeries,
            long displayQuantity) {
        List<TradingSignal> signals = new ArrayList<>();
        List<JoeRossEvent> events = new ArrayList<>();
        for (PricePoint point : priceSeries) {
            HistoricalBar bar = new HistoricalBar(
                    point.index(),
                    point.timestamp(),
                    point.label(),
                    point.open(),
                    point.high(),
                    point.low(),
                    point.close(),
                    point.volume());
            JoeRossBarResult result = strategy.onBar(bar);
            events.addAll(result.events());
            signals.addAll(JoeRossSignalAdapter.toSignals(
                    symbol, bar, result, displayQuantity));
        }
        return new JoeRossAnalysis(signals, events, strategy.statistics());
    }

    private BacktestExportRun exportRun(
            String backtestId,
            StrategySimulationRequest request,
            List<PricePoint> priceSeries,
            List<TradingSignal> signals,
            BacktestResult backtest,
            List<JoeRossEvent> joeRossEvents) {
        BacktestExportParameters parameters = exportParameters(
                backtest.evaluationParameters());
        List<BacktestExportBar> bars = priceSeries.stream()
                .map(point -> new BacktestExportBar(
                        point.index(),
                        point.timestamp(),
                        point.label(),
                        point.open(),
                        point.high(),
                        point.low(),
                        point.close(),
                        point.volume(),
                        intervalFromRequest(request)))
                .toList();
        List<BacktestExportTrade> trades = backtest.trades().stream()
                .map(trade -> exportTrade(request, trade))
                .toList();

        Instant startAt = priceSeries.isEmpty()
                ? null
                : priceSeries.getFirst().timestamp();
        Instant endAt = priceSeries.isEmpty()
                ? null
                : priceSeries.getLast().timestamp();
        return new BacktestExportRun(
                backtestId,
                normalizeSymbol(request.symbol()),
                parameters.strategy().name(),
                parameters.tradeDirection().name(),
                intervalFromRequest(request),
                startAt,
                endAt,
                bars,
                signals,
                trades,
                exportMetrics(backtest.metrics()),
                parameters,
                joeRossEvents,
                Instant.now());
    }

    private BacktestExportParameters exportParameters(
            EvaluationParameters parameters) {
        return new BacktestExportParameters(
                parameters.provider(),
                parameters.strategy(),
                parameters.tradeDirection(),
                parameters.fastPeriod(),
                parameters.slowPeriod(),
                parameters.quantity(),
                parameters.initialCapital(),
                parameters.commissionPerOrder(),
                parameters.slippagePercent(),
                parameters.sameBarExecutionPolicy(),
                parameters.executionModel());
    }

    private BacktestExportMetrics exportMetrics(PerformanceMetrics metrics) {
        return new BacktestExportMetrics(
                metrics.initialCapital(),
                metrics.finalEquity(),
                metrics.netProfit(),
                metrics.totalReturnPercent(),
                metrics.maxDrawdownPercent(),
                metrics.tradeCount(),
                metrics.winningTrades(),
                metrics.losingTrades(),
                metrics.winRatePercent(),
                metrics.grossProfit(),
                metrics.grossLoss(),
                metrics.profitFactor(),
                metrics.averageTrade(),
                metrics.exposurePercent(),
                metrics.openPositionQuantity(),
                metrics.openPositionMarketValue(),
                metrics.buyAndHoldReturnPercent());
    }

    private BacktestExportTrade exportTrade(
            StrategySimulationRequest request,
            TradeResult trade) {
        double commission = trade.grossPnl() - trade.netPnl();
        Double rMultiple = trade.effectiveRisk() == 0.0
                ? null
                : trade.netPnl() / trade.effectiveRisk();
        String strategyName = (request.strategy() == null
                ? properties.getStrategy().getType()
                : request.strategy()).name();
        return new BacktestExportTrade(
                trade.number(),
                tradeId(trade.number()),
                normalizeSymbol(request.symbol()),
                strategyName,
                trade.direction(),
                trade.signalAt(),
                trade.signalPrice(),
                trade.signalSource(),
                trade.signalReason(),
                trade.entryIndex(),
                trade.exitIndex(),
                trade.entryAt(),
                trade.exitAt(),
                trade.quantity(),
                trade.remainingQuantity(),
                trade.entryPrice(),
                trade.exitPrice(),
                trade.initialStopPrice(),
                trade.finalStopPrice(),
                trade.tradingCapital(),
                trade.riskPercent(),
                trade.maximumRisk(),
                trade.riskPerShare(),
                trade.effectiveRisk(),
                trade.capitalRequired(),
                trade.grossPnl(),
                commission,
                null,
                trade.netPnl(),
                trade.returnPercent(),
                rMultiple,
                trade.exitReason(),
                trade.tradeStatus(),
                Math.max(0, trade.exitIndex() - trade.entryIndex() + 1),
                trade.mfe(),
                trade.mae(),
                trade.mfeR(),
                trade.maeR(),
                trade.strategyData(),
                trade.takeProfits(),
                trade.events().stream()
                        .map(this::exportTradeEvent)
                        .toList());
    }

    private BacktestExportTradeEvent exportTradeEvent(TradeEventResult event) {
        return new BacktestExportTradeEvent(
                event.eventType(),
                event.index(),
                event.occurredAt(),
                event.price(),
                event.quantity(),
                event.remainingQuantity(),
                event.currentStopPrice(),
                event.grossPnl(),
                event.netPnl(),
                event.returnPercent(),
                event.details());
    }

    private String tradeId(int tradeNumber) {
        return "T" + String.format(Locale.ROOT, "%03d", tradeNumber);
    }

    private it.andreademartini.trading.marketdata.BarInterval intervalFromRequest(
            StrategySimulationRequest request) {
        return request.bars() == null || request.bars().isEmpty()
                ? it.andreademartini.trading.marketdata.BarInterval.DAILY
                : it.andreademartini.trading.marketdata.BarInterval.DAILY;
    }

    private void applyMarketDataConfig(MarketDataConfigRequest request) {
        TradingSystemProperties.MarketDataProperties marketData =
                properties.getMarketData();

        if (request.enabled() != null) {
            marketData.setEnabled(request.enabled());
        }
        if (request.provider() != null) {
            marketData.setProvider(MarketDataProvider.IBKR);
        }
        if (request.mode() != null) {
            marketData.setMode(request.mode());
        }
    }

    private void applyIbkrConfig(IbkrConfigRequest request) {
        TradingSystemProperties.IbkrProperties ibkr = properties.getIbkr();
        if (request.host() != null && !request.host().isBlank()) {
            ibkr.setHost(request.host().trim());
        }
        if (request.port() != null) {
            ibkr.setPort(Math.max(1, Math.min(65_535, request.port())));
        }
        if (request.clientId() != null) {
            ibkr.setClientId(Math.max(0, request.clientId()));
        }
        if (request.accountId() != null) {
            ibkr.setAccountId(request.accountId().trim());
        }
        if (request.autoConnect() != null) {
            ibkr.setAutoConnect(request.autoConnect());
        }
        if (request.allowCustomPaperPort() != null) {
            ibkr.setAllowCustomPaperPort(request.allowCustomPaperPort());
        }
        if (request.historicalDataTimeoutSeconds() != null) {
            ibkr.setHistoricalDataTimeout(Duration.ofSeconds(
                    Math.max(5, request.historicalDataTimeoutSeconds())));
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
    }

    private void applyRuntimeConfig(RuntimeConfigRequest request) {
        if (request.mode() == TradingMode.LIVE) {
            throw new IllegalArgumentException(
                    "Questa versione consente solo BACKTEST o PAPER");
        }
        if (request.mode() != null) {
            properties.setMode(request.mode());
        }

        if (Boolean.TRUE.equals(request.liveTradingEnabled())) {
            throw new IllegalArgumentException(
                    "LIVE e' bloccato in questa versione del progetto");
        }
        properties.setLiveTradingEnabled(false);

        if (request.paperAccountConfirmation() != null) {
            properties.setPaperAccountConfirmation(
                    request.paperAccountConfirmation().trim());
        }
        if (request.orderSubmissionEnabled() != null) {
            properties.setOrderSubmissionEnabled(request.orderSubmissionEnabled());
        }
        if (properties.isOrderSubmissionEnabled()) {
            if (properties.getMode() != TradingMode.PAPER) {
                throw new IllegalArgumentException(
                        "Gli ordini IBKR paper sono consentiti solo in modalita PAPER");
            }
            if (!TradingSystemProperties.PAPER_CONFIRMATION_PHRASE.equals(
                    properties.getPaperAccountConfirmation())) {
                throw new IllegalArgumentException(
                        "Per abilitare gli ordini devi inserire la frase di conferma paper");
            }
            if (properties.getIbkr().getAccountId() == null
                    || properties.getIbkr().getAccountId().isBlank()) {
                throw new IllegalArgumentException(
                        "Account paper IBKR obbligatorio prima di abilitare gli ordini");
            }
        }
    }

    private void applyRiskConfig(RiskConfigRequest request) {
        TradingSystemProperties.RiskProperties risk = properties.getRisk();
        if (request.tradingCapital() != null) {
            risk.setTradingCapital(Math.max(0.01, request.tradingCapital()));
        }
        if (request.riskPerTradePercent() != null) {
            risk.setRiskPerTradePercent(Math.max(0.01, request.riskPerTradePercent()));
        }
        if (request.maxOrderValue() != null) {
            risk.setMaxOrderValue(Math.max(0.01, request.maxOrderValue()));
        }
        if (request.maxPositionValue() != null) {
            risk.setMaxPositionValue(Math.max(0.01, request.maxPositionValue()));
        }
        if (request.maxLongNotional() != null) {
            risk.setMaxLongNotional(Math.max(0.01, request.maxLongNotional()));
        }
        if (request.maxShortNotional() != null) {
            risk.setMaxShortNotional(Math.max(0.01, request.maxShortNotional()));
        }
        if (request.maxQuantityPerOrder() != null) {
            risk.setMaxQuantityPerOrder(Math.max(1, request.maxQuantityPerOrder()));
        }
        if (request.maxDailyOrders() != null) {
            risk.setMaxDailyOrders(Math.max(1, request.maxDailyOrders()));
        }
        if (request.maxOpenOrders() != null) {
            risk.setMaxOpenOrders(Math.max(1, request.maxOpenOrders()));
        }
        if (request.maxDailyLoss() != null) {
            risk.setMaxDailyLoss(Math.max(0.01, request.maxDailyLoss()));
        }
        if (request.allowLong() != null) {
            risk.setAllowLong(request.allowLong());
        }
        if (request.allowShortSelling() != null) {
            risk.setAllowShortSelling(request.allowShortSelling());
        }
        if (request.useLeverage() != null) {
            risk.setUseLeverage(request.useLeverage());
        }
        if (request.entryTicket() != null) {
            risk.setEntryTicket(Math.max(0.0, request.entryTicket()));
        }
        if (request.commissionPerOrder() != null) {
            risk.setCommissionPerOrder(Math.max(0.0, request.commissionPerOrder()));
        }
        if (request.slippagePercent() != null) {
            risk.setSlippagePercent(Math.max(0.0, request.slippagePercent()));
        }
        if (request.transactionCostsPerShare() != null) {
            risk.setTransactionCostsPerShare(
                    Math.max(0.0, request.transactionCostsPerShare()));
        }
    }

    private void applyTakeProfitConfig(TakeProfitConfigRequest request) {
        TradingSystemProperties.TakeProfitProperties takeProfit =
                properties.getTakeProfit();
        if (request.enabled() != null) {
            takeProfit.setEnabled(request.enabled());
        }
        applyTakeProfitTarget(takeProfit.getTarget1(), request.target1());
        applyTakeProfitTarget(takeProfit.getTarget2(), request.target2());
        applyTakeProfitTarget(takeProfit.getTarget3(), request.target3());
    }

    private void applyTakeProfitTarget(
            TradingSystemProperties.TargetProperties target,
            TakeProfitTargetConfigRequest request) {
        if (request == null) {
            return;
        }
        if (request.profitPercent() != null) {
            target.setProfitPercent(Math.max(0.01, request.profitPercent()));
        }
        if (request.positionPercent() != null) {
            target.setPositionPercent(Math.max(0.0, request.positionPercent()));
        }
    }

    private void applyOrdersConfig(OrdersConfigRequest request) {
        TradingSystemProperties.OrderProperties orders = properties.getOrders();
        if (request.entryTimeInForce() != null
                && !request.entryTimeInForce().isBlank()) {
            orders.setEntryTimeInForce(request.entryTimeInForce()
                    .trim()
                    .toUpperCase(Locale.ROOT));
        }
        if (request.protectiveTimeInForce() != null
                && !request.protectiveTimeInForce().isBlank()) {
            orders.setProtectiveTimeInForce(request.protectiveTimeInForce()
                    .trim()
                    .toUpperCase(Locale.ROOT));
        }
        if (request.outsideRegularTradingHours() != null) {
            orders.setOutsideRegularTradingHours(
                    request.outsideRegularTradingHours());
        }
        if (request.interOrderDelayMillis() != null) {
            orders.setInterOrderDelay(Duration.ofMillis(
                    Math.max(0, request.interOrderDelayMillis())));
        }
    }

    private List<PricePoint> simulationPriceSeries(StrategySimulationRequest request) {
        if (request.bars() != null && !request.bars().isEmpty()) {
            List<PricePoint> priceSeries = new ArrayList<>(request.bars().size());
            for (int index = 0; index < request.bars().size(); index++) {
                SimulationBarRequest bar = request.bars().get(index);
                validateSimulationBar(bar);
                Instant timestamp = bar.timestamp() == null
                        ? Instant.now().minus(Duration.ofDays(request.bars().size() - index))
                        : bar.timestamp();
                String label = bar.label() == null || bar.label().isBlank()
                        ? String.valueOf(index)
                        : bar.label();
                priceSeries.add(new PricePoint(
                        index,
                        timestamp,
                        label,
                        bar.closeValue(),
                        bar.openValue(),
                        bar.highValue(),
                        bar.lowValue(),
                        bar.closeValue(),
                        Math.max(0L, bar.volumeValue())));
            }
            return priceSeries;
        }

        if (request.prices() == null || request.prices().isEmpty()) {
            throw new IllegalArgumentException(
                    "Inserire almeno una barra OHLC o un prezzo close");
        }

        List<PricePoint> priceSeries = new ArrayList<>(request.prices().size());
        Instant start = Instant.now().minus(Duration.ofDays(request.prices().size()));
        for (int index = 0; index < request.prices().size(); index++) {
            double price = request.prices().get(index);
            if (price <= 0) {
                throw new IllegalArgumentException("Tutti i prezzi devono essere positivi");
            }
            Instant timestamp = start.plus(Duration.ofDays(index));
            priceSeries.add(new PricePoint(
                    index,
                    timestamp,
                    String.valueOf(index),
                    price,
                    price,
                    price,
                    price,
                    price,
                    0L));
        }
        return priceSeries;
    }

    private void validateSimulationBar(SimulationBarRequest bar) {
        if (bar == null) {
            throw new IllegalArgumentException("Barra OHLC non valida");
        }
        if (bar.openValue() <= 0 || bar.highValue() <= 0
                || bar.lowValue() <= 0 || bar.closeValue() <= 0) {
            throw new IllegalArgumentException(
                    "Open, high, low e close devono essere positivi");
        }
        if (bar.highValue() < Math.max(bar.openValue(), bar.closeValue())) {
            throw new IllegalArgumentException(
                    "High deve essere maggiore o uguale a open/close");
        }
        if (bar.lowValue() > Math.min(bar.openValue(), bar.closeValue())) {
            throw new IllegalArgumentException(
                    "Low deve essere minore o uguale a open/close");
        }
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
        TradingSystemProperties.JoeRossProperties sourceJoe =
                source.getJoeRoss123RhTte();
        TradingSystemProperties.JoeRossProperties joe =
                strategy.getJoeRoss123RhTte();
        joe.setEnabled(sourceJoe.isEnabled());
        joe.setDirection(request.tradeDirection() == null
                ? sourceJoe.getDirection() : request.tradeDirection());
        joe.setMinimumTick(request.minimumTick() == null
                ? sourceJoe.getMinimumTick() : request.minimumTick());
        joe.getSwing().setPivotStrength(request.pivotStrength() == null
                ? sourceJoe.getSwing().getPivotStrength() : request.pivotStrength());
        joe.getRossHook().setRequire123(true);
        joe.getTte().setEnabled(true);
        joe.getTte().setMaxBarsAfterRossHook(request.tteMaxBars() == null
                ? sourceJoe.getTte().getMaxBarsAfterRossHook() : request.tteMaxBars());
        joe.getTte().setEntryBufferTicks(request.entryBufferTicks() == null
                ? sourceJoe.getTte().getEntryBufferTicks() : request.entryBufferTicks());
        joe.getTte().setStopBufferTicks(request.stopBufferTicks() == null
                ? sourceJoe.getTte().getStopBufferTicks() : request.stopBufferTicks());
        joe.getTte().getMinimumRoom().setEnabled(request.minimumRoomEnabled() == null
                ? sourceJoe.getTte().getMinimumRoom().isEnabled()
                : request.minimumRoomEnabled());
        joe.getTte().getMinimumRoom().setType("R");
        joe.getTte().getMinimumRoom().setValue(request.minimumRoomValue() == null
                ? sourceJoe.getTte().getMinimumRoom().getValue()
                : request.minimumRoomValue());
        joe.getTte().setAllowAfterThreeBarsOnDoubleTriple(
                request.allowDoubleTriple() == null
                        ? sourceJoe.getTte().isAllowAfterThreeBarsOnDoubleTriple()
                        : request.allowDoubleTriple());
        joe.getTte().setDoubleTripleToleranceTicks(
                request.doubleTripleToleranceTicks() == null
                        ? sourceJoe.getTte().getDoubleTripleToleranceTicks()
                        : request.doubleTripleToleranceTicks());
        joe.getTte().setRejectGapOverEntry(request.rejectGapOverEntry() == null
                ? sourceJoe.getTte().isRejectGapOverEntry()
                : request.rejectGapOverEntry());
        joe.getTte().setFallbackToRossHookBreakout(false);
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
            case JOE_ROSS_123_RH_TTE -> new JoeRoss123RossHookTteStrategy(
                    JoeRossParameters.from(strategyProperties.getJoeRoss123RhTte()),
                    strategyProperties.getJoeRoss123RhTte().getDirection());
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
        StrategyTradeMode tradeDirection = request.tradeDirection() == null
                ? StrategyTradeMode.LONG_SHORT
                : request.tradeDirection();

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
        BacktestAccount account = new BacktestAccount(initialCapital);
        BacktestPosition position = null;
        BacktestPendingEntry pendingEntry = null;
        int signalIndex = 0;
        int barsInPosition = 0;
        double peakEquity = initialCapital;
        double maxDrawdownPercent = 0.0;

        for (int index = 0; index < priceSeries.size(); index++) {
            PricePoint point = priceSeries.get(index);

            if (position != null) {
                position.updateExcursions(point);
                position = processProtectiveExits(
                        position,
                        point,
                        account,
                        commissionPerOrder,
                        slippagePercent,
                        trades);
            }

            if (pendingEntry != null
                    && hasPendingCancellationAt(signals, signalIndex, point.timestamp())) {
                pendingEntry = null;
            }

            if (position == null && pendingEntry != null
                    && entryHit(pendingEntry, point)) {
                position = openPosition(
                        pendingEntry,
                        point,
                        account,
                        commissionPerOrder,
                        slippagePercent);
                if (position != null) {
                    position.updateExcursions(point);
                    if (position.isJoeRossTrade()) {
                        position = processProtectiveExits(
                                position,
                                point,
                                account,
                                commissionPerOrder,
                                slippagePercent,
                                trades);
                    }
                }
                pendingEntry = null;
            }

            while (signalIndex < signals.size()
                    && !signals.get(signalIndex).occurredAt().isAfter(point.timestamp())) {
                TradingSignal signal = signals.get(signalIndex++);

                if (signal.orderDetails() != null
                        && signal.orderDetails().cancelPending()) {
                    pendingEntry = null;
                    continue;
                }

                if (position != null && isOppositeSignal(position, signal)) {
                    double fillPrice = applySlippage(
                            point.close(),
                            slippagePercent,
                            signal.action() == SignalAction.BUY);
                    closePositionQuantity(
                            position,
                            point,
                            account,
                            position.remainingQuantity,
                            fillPrice,
                            commissionPerOrder,
                            "SIGNAL");
                    recordClosedTrade(position, trades);
                    position = null;
                    pendingEntry = null;
                }

                TradeDirection signalDirection = signalDirection(signal);
                if (position == null
                        && signalDirection != null
                        && tradeDirectionAllows(tradeDirection, signalDirection)) {
                    pendingEntry = createPendingEntry(
                            signalDirection,
                            signal,
                            previousPoint(priceSeries, index),
                            point,
                            currentEquity(account, position, point.close()),
                            commissionPerOrder,
                            slippagePercent);
                }
            }

            if (position != null) {
                barsInPosition++;
            }

            double equity = currentEquity(account, position, point.close());
            peakEquity = Math.max(peakEquity, equity);
            if (peakEquity > 0) {
                double drawdown = (peakEquity - equity) / peakEquity * 100.0;
                maxDrawdownPercent = Math.max(maxDrawdownPercent, drawdown);
            }
            equityCurve.add(new EquityPoint(
                    point.index(),
                    point.timestamp(),
                    equity,
                    account.cash,
                    signedQuantity(position),
                    point.close()));
        }

        double finalPrice = priceSeries.get(priceSeries.size() - 1).close();
        long positionQuantity = signedQuantity(position);
        double openPositionMarketValue = positionMarketValue(position, finalPrice);
        double finalEquity = account.cash + openPositionMarketValue;
        PerformanceMetrics metrics = metrics(
                initialCapital,
                finalEquity,
                maxDrawdownPercent,
                trades,
                positionQuantity,
                openPositionMarketValue,
                barsInPosition,
                priceSeries.size(),
                priceSeries.get(0).close(),
                finalPrice);

        EvaluationParameters parameters = new EvaluationParameters(
                request.provider() == null
                        ? properties.getMarketData().getProvider()
                        : request.provider(),
                request.strategy() == null
                        ? properties.getStrategy().getType()
                        : request.strategy(),
                tradeDirection,
                strategyProperties.getFastPeriod(),
                strategyProperties.getSlowPeriod(),
                strategyProperties.getQuantity(),
                initialCapital,
                commissionPerOrder,
                slippagePercent,
                backtestProperties.getSameBarExecutionPolicy(),
                executionModel(tradeDirection));

        return new BacktestResult(equityCurve, trades, metrics, parameters);
    }

    private BacktestPosition processProtectiveExits(
            BacktestPosition position,
            PricePoint point,
            BacktestAccount account,
            double commissionPerOrder,
            double slippagePercent,
            List<TradeResult> trades) {
        SameBarExecutionPolicy sameBarPolicy =
                backtestProperties.getSameBarExecutionPolicy();
        if (sameBarPolicy == SameBarExecutionPolicy.TAKE_PROFIT_FIRST) {
            position = processTakeProfitExits(
                    position,
                    point,
                    account,
                    commissionPerOrder,
                    slippagePercent,
                    trades);
            if (position == null) {
                return null;
            }
            return processStopLossExit(
                    position,
                    point,
                    account,
                    commissionPerOrder,
                    slippagePercent,
                    trades);
        }

        position = processStopLossExit(
                position,
                point,
                account,
                commissionPerOrder,
                slippagePercent,
                trades);
        if (position == null) {
            return null;
        }
        return processTakeProfitExits(
                position,
                point,
                account,
                commissionPerOrder,
                slippagePercent,
                trades);
    }

    private BacktestPosition processStopLossExit(
            BacktestPosition position,
            PricePoint point,
            BacktestAccount account,
            double commissionPerOrder,
            double slippagePercent,
            List<TradeResult> trades) {
        if (!stopLossHit(position, point)) {
            return position;
        }
        double fillPrice = stopLossFillPrice(position, point, slippagePercent);
        closePositionQuantity(
                position,
                point,
                account,
                position.remainingQuantity,
                fillPrice,
                commissionPerOrder,
                "STOP_LOSS");
        recordClosedTrade(position, trades);
        return null;
    }

    private BacktestPosition processTakeProfitExits(
            BacktestPosition position,
            PricePoint point,
            BacktestAccount account,
            double commissionPerOrder,
            double slippagePercent,
            List<TradeResult> trades) {
        while (position.remainingQuantity > 0
                && position.nextTakeProfitIndex < position.takeProfits.size()) {
            TakeProfitAllocation target =
                    position.takeProfits.get(position.nextTakeProfitIndex);
            if (target.quantity() <= 0) {
                position.nextTakeProfitIndex++;
                continue;
            }
            if (!takeProfitHit(position, point, target)) {
                break;
            }
            long quantity = Math.min(target.quantity(), position.remainingQuantity);
            double fillPrice = takeProfitFillPrice(
                    position,
                    point,
                    target,
                    slippagePercent);
            closePositionQuantity(
                    position,
                    point,
                    account,
                    quantity,
                    fillPrice,
                    commissionPerOrder,
                    "TP" + target.targetNumber());
            applyBacktestProgressiveStop(position, target, quantity, true, point);
            position.nextTakeProfitIndex++;
        }

        if (position.remainingQuantity == 0) {
            recordClosedTrade(position, trades);
            return null;
        }
        return position;
    }

    private void applyBacktestProgressiveStop(
            BacktestPosition position,
            TakeProfitAllocation target,
            long filledQuantity,
            boolean takeProfitFullyFilled,
            PricePoint point) {
        StopAdjustmentResult result =
                progressiveStopLossService.onTakeProfitExecution(
                        tradeStopState(position),
                        TakeProfitLevel.fromNumber(target.targetNumber()),
                        BigDecimal.valueOf(filledQuantity),
                        takeProfitFullyFilled);

        if (!result.hasPersistableAdjustment()) {
            return;
        }

        if (result.stopCancelled()) {
            position.activeStopQuantity = 0;
            position.events.add(new TradeEventResult(
                    "STOP CANCELLED",
                    point.index(),
                    point.timestamp(),
                    result.oldStopPrice() == null
                            ? 0.0
                            : result.oldStopPrice().doubleValue(),
                    0,
                    0,
                    null,
                    0.0,
                    0.0,
                    0.0,
                    "remainingQuantity = 0 | " + result.reason()));
            return;
        }

        if (result.stopPriceRejected()) {
            position.activeStopQuantity = result.newQuantity();
            position.events.add(new TradeEventResult(
                    "STOP UPDATE REJECTED",
                    point.index(),
                    point.timestamp(),
                    position.currentStopPrice,
                    result.newQuantity(),
                    position.remainingQuantity,
                    position.currentStopPrice,
                    0.0,
                    0.0,
                    0.0,
                    result.rejectionReason()));
            return;
        }

        double oldStopPrice = position.currentStopPrice;
        position.currentStopPrice = result.newStopPrice().doubleValue();
        position.activeStopQuantity = result.newQuantity();
        position.events.add(new TradeEventResult(
                result.stopPriceChanged() ? "STOP MOVED" : "STOP QUANTITY UPDATED",
                point.index(),
                point.timestamp(),
                position.currentStopPrice,
                position.activeStopQuantity,
                position.remainingQuantity,
                position.currentStopPrice,
                0.0,
                0.0,
                0.0,
                formatPrice(oldStopPrice) + " -> "
                        + formatPrice(position.currentStopPrice)
                        + " | " + result.reason()));
    }

    private TradeStopState tradeStopState(BacktestPosition position) {
        return new TradeStopState(
                "BT-" + position.entryIndex,
                position.direction,
                BigDecimal.valueOf(position.entryPrice),
                BigDecimal.valueOf(position.initialStopPrice),
                BigDecimal.valueOf(position.currentStopPrice),
                position.quantity,
                position.remainingQuantity,
                position.activeStopQuantity,
                position.takeProfits);
    }

    private BacktestPendingEntry createPendingEntry(
            TradeDirection direction,
            TradingSignal signal,
            PricePoint referencePoint,
            PricePoint activationPoint,
            double tradingCapital,
            double commissionPerOrder,
            double slippagePercent) {
        StrategyOrderDetails orderDetails = signal == null ? null : signal.orderDetails();
        if ((referencePoint == null && orderDetails == null) || tradingCapital <= 0) {
            return null;
        }

        TradingSystemProperties.RiskProperties risk = properties.getRisk();
        if (direction == TradeDirection.LONG && !risk.isAllowLong()) {
            return null;
        }
        if (direction == TradeDirection.SHORT && !risk.isAllowShortSelling()) {
            return null;
        }

        BigDecimal entryPrice;
        BigDecimal stopLossPrice;
        if (orderDetails != null
                && orderDetails.entryStopPrice() != null
                && orderDetails.initialStopPrice() != null) {
            entryPrice = money(orderDetails.entryStopPrice());
            stopLossPrice = money(orderDetails.initialStopPrice());
        } else {
            BigDecimal ticket = money(risk.getEntryTicket());
            BigDecimal high = money(referencePoint.high());
            BigDecimal low = money(referencePoint.low());
            entryPrice = direction == TradeDirection.LONG
                    ? high.add(ticket, MATH)
                    : low.subtract(ticket, MATH);
            stopLossPrice = direction == TradeDirection.LONG
                    ? low
                    : high.add(ticket, MATH);
        }
        if (entryPrice.compareTo(BigDecimal.ZERO) <= 0
                || stopLossPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (direction == TradeDirection.LONG
                && entryPrice.compareTo(stopLossPrice) <= 0) {
            return null;
        }
        if (direction == TradeDirection.SHORT
                && entryPrice.compareTo(stopLossPrice) >= 0) {
            return null;
        }

        BigDecimal transactionCostsPerShare = simulationTransactionCostsPerShare(
                entryPrice,
                stopLossPrice,
                slippagePercent);
        BigDecimal fixedTransactionCosts = money(commissionPerOrder)
                .multiply(BigDecimal.valueOf(2), MATH);

        try {
            PositionSizingResult sizing = positionSizingService.calculate(
                    money(tradingCapital),
                    money(risk.getRiskPerTradePercent()),
                    entryPrice,
                    stopLossPrice,
                    transactionCostsPerShare,
                    fixedTransactionCosts,
                    maxNotional(direction, risk),
                    risk.isUseLeverage(),
                    risk.getMaxQuantityPerOrder());
            List<TakeProfitAllocation> takeProfits =
                    takeProfitAllocationService.allocate(
                            sizing.quantity(),
                            entryPrice,
                            direction);
            return new BacktestPendingEntry(
                    direction,
                    signal == null ? null : signal.occurredAt(),
                    signal == null ? 0.0 : signal.price(),
                    signal == null ? null : signal.source(),
                    signal == null ? null : signal.reason(),
                    entryPrice,
                    stopLossPrice,
                    money(tradingCapital),
                    money(risk.getRiskPerTradePercent()),
                    sizing.maximumRisk(),
                    sizing.totalRiskPerShare(),
                    sizing.effectiveRisk(),
                    sizing.notional(),
                    sizing.quantity(),
                    takeProfits,
                    orderDetails != null && orderDetails.rejectGapOverEntry(),
                    orderDetails == null
                            ? activationPoint.index()
                            : orderDetails.activeAfterBarIndex(),
                    orderDetails == null ? Map.of() : orderDetails.metadata());
        } catch (IllegalArgumentException | RiskRejectedException rejected) {
            return null;
        }
    }

    private BigDecimal simulationTransactionCostsPerShare(
            BigDecimal entryPrice,
            BigDecimal stopLossPrice,
            double slippagePercent) {
        BigDecimal configured = money(properties.getRisk().getTransactionCostsPerShare());
        BigDecimal slippageFactor = money(slippagePercent)
                .divide(BigDecimal.valueOf(100), MATH);
        BigDecimal entrySlippage = entryPrice.multiply(slippageFactor, MATH);
        BigDecimal stopSlippage = stopLossPrice.multiply(slippageFactor, MATH);
        return configured.add(entrySlippage, MATH).add(stopSlippage, MATH);
    }

    private BigDecimal maxNotional(
            TradeDirection direction,
            TradingSystemProperties.RiskProperties risk) {
        BigDecimal maxNotional = direction == TradeDirection.LONG
                ? money(risk.getMaxLongNotional())
                : money(risk.getMaxShortNotional());
        return maxNotional
                .min(money(risk.getMaxOrderValue()))
                .min(money(risk.getMaxPositionValue()));
    }

    private BacktestPosition openPosition(
            BacktestPendingEntry pendingEntry,
            PricePoint point,
            BacktestAccount account,
            double commissionPerOrder,
            double slippagePercent) {
        double fillPrice = entryFillPrice(pendingEntry, point, slippagePercent);
        long quantity = pendingEntry.quantity();
        if (pendingEntry.direction() == TradeDirection.LONG) {
            if (!properties.getRisk().isUseLeverage()
                    && fillPrice * quantity + commissionPerOrder > account.cash) {
                return null;
            }
            account.cash -= fillPrice * quantity + commissionPerOrder;
        } else {
            account.cash += fillPrice * quantity - commissionPerOrder;
        }
        Map<String, String> strategyMetadata = new LinkedHashMap<>(
                pendingEntry.strategyMetadata());
        if (!strategyMetadata.isEmpty()) {
            strategyMetadata.put("quantity", String.valueOf(quantity));
            strategyMetadata.put("setupStatus", "TRADE_OPEN");
        }
        return new BacktestPosition(
                pendingEntry.direction(),
                pendingEntry.signalAt(),
                pendingEntry.signalPrice(),
                pendingEntry.signalSource(),
                pendingEntry.signalReason(),
                point.index(),
                point.timestamp(),
                quantity,
                fillPrice,
                pendingEntry.stopLossPrice().doubleValue(),
                pendingEntry.tradingCapital().doubleValue(),
                pendingEntry.riskPercent().doubleValue(),
                pendingEntry.maximumRisk().doubleValue(),
                pendingEntry.riskPerShare().doubleValue(),
                pendingEntry.effectiveRisk().doubleValue(),
                pendingEntry.capitalRequired().doubleValue(),
                pendingEntry.takeProfits(),
                commissionPerOrder,
                strategyMetadata);
    }

    private void closePositionQuantity(
            BacktestPosition position,
            PricePoint point,
            BacktestAccount account,
            long requestedQuantity,
            double fillPrice,
            double commissionPerOrder,
            String exitReason) {
        long quantity = Math.min(requestedQuantity, position.remainingQuantity);
        if (quantity <= 0) {
            return;
        }

        double grossPnl;
        if (position.direction == TradeDirection.LONG) {
            account.cash += fillPrice * quantity - commissionPerOrder;
            grossPnl = (fillPrice - position.entryPrice) * quantity;
        } else {
            account.cash -= fillPrice * quantity + commissionPerOrder;
            grossPnl = (position.entryPrice - fillPrice) * quantity;
        }

        position.remainingQuantity -= quantity;
        position.exitedQuantity += quantity;
        position.exitNotional += fillPrice * quantity;
        position.grossPnl += grossPnl;
        position.netPnl += grossPnl - commissionPerOrder;
        position.exitIndex = point.index();
        position.exitAt = point.timestamp();
        position.exitReason = exitReason;
        double netPnl = grossPnl - commissionPerOrder;
        position.events.add(new TradeEventResult(
                exitEventType(exitReason),
                point.index(),
                point.timestamp(),
                fillPrice,
                quantity,
                position.remainingQuantity,
                position.remainingQuantity == 0 ? null : position.currentStopPrice,
                grossPnl,
                netPnl,
                eventReturnPercent(netPnl, position.entryPrice, quantity),
                exitReason));
    }

    private void recordClosedTrade(
            BacktestPosition position,
            List<TradeResult> trades) {
        double exitPrice = position.exitedQuantity == 0
                ? 0.0
                : position.exitNotional / position.exitedQuantity;
        double denominator = position.entryPrice * position.quantity;
        trades.add(new TradeResult(
                trades.size() + 1,
                position.direction.name(),
                position.direction,
                position.entryIndex,
                position.exitIndex,
                position.signalAt,
                position.signalPrice,
                position.signalSource,
                position.signalReason,
                position.entryAt,
                position.exitAt,
                position.quantity,
                position.remainingQuantity,
                position.entryPrice,
                exitPrice,
                position.initialStopPrice,
                position.activeStopQuantity <= 0 || position.remainingQuantity == 0
                        ? null
                        : position.currentStopPrice,
                position.tradingCapital,
                position.riskPercent,
                position.maximumRisk,
                position.riskPerShare,
                position.effectiveRisk,
                position.capitalRequired,
                position.grossPnl,
                position.netPnl,
                denominator == 0.0 ? 0.0 : position.netPnl / denominator * 100.0,
                position.exitReason,
                "CLOSED",
                position.maximumFavorableExcursion,
                position.maximumAdverseExcursion,
                position.riskPerShare == 0.0
                        ? null : position.maximumFavorableExcursion / position.riskPerShare,
                position.riskPerShare == 0.0
                        ? null : position.maximumAdverseExcursion / position.riskPerShare,
                position.strategyMetadata,
                position.takeProfits,
                position.events));
    }

    private String exitEventType(String exitReason) {
        if (exitReason == null || exitReason.isBlank()) {
            return "EXIT";
        }
        return "EXIT - " + exitReason.trim().toUpperCase(Locale.ROOT);
    }

    private static double eventReturnPercent(double netPnl, double entryPrice, long quantity) {
        double denominator = entryPrice * quantity;
        return denominator == 0.0 ? 0.0 : netPnl / denominator * 100.0;
    }

    private String formatPrice(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private boolean entryHit(BacktestPendingEntry pendingEntry, PricePoint point) {
        if (point.index() <= pendingEntry.activeAfterBarIndex()) {
            return false;
        }
        if (pendingEntry.rejectGapOverEntry()) {
            double entry = pendingEntry.entryPrice().doubleValue();
            if ((pendingEntry.direction() == TradeDirection.LONG && point.open() > entry)
                    || (pendingEntry.direction() == TradeDirection.SHORT
                    && point.open() < entry)) {
                return false;
            }
        }
        double entryPrice = pendingEntry.entryPrice().doubleValue();
        return pendingEntry.direction() == TradeDirection.LONG
                ? point.high() >= entryPrice
                : point.low() <= entryPrice;
    }

    private boolean hasPendingCancellationAt(
            List<TradingSignal> signals,
            int fromIndex,
            Instant timestamp) {
        for (int index = fromIndex; index < signals.size(); index++) {
            TradingSignal signal = signals.get(index);
            if (signal.occurredAt().isAfter(timestamp)) {
                return false;
            }
            if (signal.occurredAt().equals(timestamp)
                    && signal.orderDetails() != null
                    && signal.orderDetails().cancelPending()) {
                return true;
            }
        }
        return false;
    }

    private boolean stopLossHit(BacktestPosition position, PricePoint point) {
        return position.direction == TradeDirection.LONG
                ? point.low() <= position.currentStopPrice
                : point.high() >= position.currentStopPrice;
    }

    private boolean takeProfitHit(
            BacktestPosition position,
            PricePoint point,
            TakeProfitAllocation target) {
        double targetPrice = target.price().doubleValue();
        return position.direction == TradeDirection.LONG
                ? point.high() >= targetPrice
                : point.low() <= targetPrice;
    }

    private double entryFillPrice(
            BacktestPendingEntry pendingEntry,
            PricePoint point,
            double slippagePercent) {
        double trigger = pendingEntry.entryPrice().doubleValue();
        double rawPrice = trigger;
        if (pendingEntry.direction() == TradeDirection.LONG && point.open() > trigger) {
            rawPrice = point.open();
        }
        if (pendingEntry.direction() == TradeDirection.SHORT && point.open() < trigger) {
            rawPrice = point.open();
        }
        return applySlippage(
                rawPrice,
                slippagePercent,
                pendingEntry.direction() == TradeDirection.LONG);
    }

    private double stopLossFillPrice(
            BacktestPosition position,
            PricePoint point,
            double slippagePercent) {
        double rawPrice = position.currentStopPrice;
        if (position.direction == TradeDirection.LONG && point.open() < rawPrice) {
            rawPrice = point.open();
        }
        if (position.direction == TradeDirection.SHORT && point.open() > rawPrice) {
            rawPrice = point.open();
        }
        return applySlippage(rawPrice, slippagePercent, position.direction == TradeDirection.SHORT);
    }

    private double takeProfitFillPrice(
            BacktestPosition position,
            PricePoint point,
            TakeProfitAllocation target,
            double slippagePercent) {
        double rawPrice = target.price().doubleValue();
        if (position.direction == TradeDirection.LONG && point.open() > rawPrice) {
            rawPrice = point.open();
        }
        if (position.direction == TradeDirection.SHORT && point.open() < rawPrice) {
            rawPrice = point.open();
        }
        return applySlippage(rawPrice, slippagePercent, position.direction == TradeDirection.SHORT);
    }

    private boolean isOppositeSignal(
            BacktestPosition position,
            TradingSignal signal) {
        return position.direction == TradeDirection.LONG
                ? signal.action() == SignalAction.SELL
                : signal.action() == SignalAction.BUY;
    }

    private TradeDirection signalDirection(TradingSignal signal) {
        return switch (signal.action()) {
            case BUY -> TradeDirection.LONG;
            case SELL -> TradeDirection.SHORT;
            case HOLD -> null;
        };
    }

    private boolean tradeDirectionAllows(
            StrategyTradeMode tradeMode,
            TradeDirection direction) {
        return direction == TradeDirection.LONG
                ? tradeMode.allowsLong()
                : tradeMode.allowsShort();
    }

    private PricePoint previousPoint(List<PricePoint> priceSeries, int index) {
        return index <= 0 ? null : priceSeries.get(index - 1);
    }

    private double currentEquity(
            BacktestAccount account,
            BacktestPosition position,
            double price) {
        return account.cash + positionMarketValue(position, price);
    }

    private double positionMarketValue(BacktestPosition position, double price) {
        return signedQuantity(position) * price;
    }

    private long signedQuantity(BacktestPosition position) {
        if (position == null) {
            return 0L;
        }
        return position.direction == TradeDirection.LONG
                ? position.remainingQuantity
                : -position.remainingQuantity;
    }

    private BigDecimal money(double value) {
        return BigDecimal.valueOf(value);
    }

    private String executionModel(StrategyTradeMode tradeDirection) {
        return switch (tradeDirection) {
            case LONG_ONLY -> "Solo long OHLC: BUY crea piano risk/TP, entry sopra high precedente, SELL chiude long";
            case SHORT_ONLY -> "Solo short OHLC: SELL crea piano risk/TP, entry sotto low precedente, BUY chiude short";
            case LONG_SHORT -> "Long/short OHLC: BUY/SELL creano piani risk/TP con entry, stop e target su barre high/low";
        };
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
        TradingSystemProperties.IbkrProperties ibkr = properties.getIbkr();
        TradingSystemProperties.StrategyProperties strategy = properties.getStrategy();
        TradingSystemProperties.RiskProperties risk = properties.getRisk();
        TradingSystemProperties.TakeProfitProperties takeProfit =
                properties.getTakeProfit();
        TradingSystemProperties.OrderProperties orders = properties.getOrders();

        return new WorkbenchConfigResponse(
                Arrays.asList(MarketDataProvider.values()),
                Arrays.asList(MarketDataMode.values()),
                Arrays.asList(StrategyType.values()),
                Arrays.asList(TradingMode.values()),
                new IbkrConfigResponse(
                        ibkr.getHost(),
                        ibkr.getPort(),
                        ibkr.getClientId(),
                        ibkr.getAccountId(),
                        ibkr.isAutoConnect(),
                        ibkr.isAllowCustomPaperPort(),
                        ibkr.getHistoricalDataTimeout().toSeconds()),
                new MarketDataConfigResponse(
                        marketData.isEnabled(),
                        MarketDataProvider.IBKR,
                        marketData.getMode()),
                new StrategyConfigResponse(
                        strategy.getType(),
                        strategy.isEnabled(),
                        strategy.isAutoExecuteSignals(),
                        strategy.getFastPeriod(),
                        strategy.getSlowPeriod(),
                        strategy.getQuantity(),
                        strategy.getSignalCooldown().toSeconds()),
                new RuntimeConfigResponse(
                        properties.getMode(),
                        properties.isLiveTradingEnabled(),
                        properties.isOrderSubmissionEnabled(),
                        properties.getPaperAccountConfirmation(),
                        TradingSystemProperties.PAPER_CONFIRMATION_PHRASE),
                new RiskConfigResponse(
                        risk.getTradingCapital(),
                        risk.getRiskPerTradePercent(),
                        risk.getMaxOrderValue(),
                        risk.getMaxPositionValue(),
                        risk.getMaxLongNotional(),
                        risk.getMaxShortNotional(),
                        risk.getMaxQuantityPerOrder(),
                        risk.getMaxDailyOrders(),
                        risk.getMaxOpenOrders(),
                        risk.getMaxDailyLoss(),
                        risk.isAllowLong(),
                        risk.isAllowShortSelling(),
                        risk.isUseLeverage(),
                        risk.getEntryTicket(),
                        risk.getCommissionPerOrder(),
                        risk.getSlippagePercent(),
                        risk.getTransactionCostsPerShare()),
                new TakeProfitConfigResponse(
                        takeProfit.isEnabled(),
                        takeProfitTargetResponse(takeProfit.getTarget1()),
                        takeProfitTargetResponse(takeProfit.getTarget2()),
                        takeProfitTargetResponse(takeProfit.getTarget3())),
                new OrdersConfigResponse(
                        orders.getEntryTimeInForce(),
                        orders.getProtectiveTimeInForce(),
                        orders.isOutsideRegularTradingHours(),
                        orders.getInterOrderDelay().toMillis()),
                killSwitchService.isActive());
    }

    private TakeProfitTargetConfigResponse takeProfitTargetResponse(
            TradingSystemProperties.TargetProperties target) {
        return new TakeProfitTargetConfigResponse(
                target.getProfitPercent(),
                target.getPositionPercent());
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
            IbkrConfigRequest ibkr,
            MarketDataConfigRequest marketData,
            StrategyConfigRequest strategy,
            RuntimeConfigRequest runtime,
            RiskConfigRequest risk,
            TakeProfitConfigRequest takeProfit,
            OrdersConfigRequest orders) {
    }

    public record IbkrConfigRequest(
            String host,
            Integer port,
            Integer clientId,
            String accountId,
            Boolean autoConnect,
            Boolean allowCustomPaperPort,
            Long historicalDataTimeoutSeconds) {
    }

    public record MarketDataConfigRequest(
            Boolean enabled,
            MarketDataProvider provider,
            MarketDataMode mode) {
    }

    public record StrategyConfigRequest(
            StrategyType type,
            Boolean enabled,
            Boolean autoExecuteSignals,
            Integer fastPeriod,
            Integer slowPeriod,
            Long quantity,
            Long signalCooldownSeconds) {
    }

    public record RuntimeConfigRequest(
            TradingMode mode,
            Boolean liveTradingEnabled,
            Boolean orderSubmissionEnabled,
            String paperAccountConfirmation) {
    }

    public record RiskConfigRequest(
            Double tradingCapital,
            Double riskPerTradePercent,
            Double maxOrderValue,
            Double maxPositionValue,
            Double maxLongNotional,
            Double maxShortNotional,
            Long maxQuantityPerOrder,
            Integer maxDailyOrders,
            Integer maxOpenOrders,
            Double maxDailyLoss,
            Boolean allowLong,
            Boolean allowShortSelling,
            Boolean useLeverage,
            Double entryTicket,
            Double commissionPerOrder,
            Double slippagePercent,
            Double transactionCostsPerShare) {
    }

    public record TakeProfitConfigRequest(
            Boolean enabled,
            TakeProfitTargetConfigRequest target1,
            TakeProfitTargetConfigRequest target2,
            TakeProfitTargetConfigRequest target3) {
    }

    public record TakeProfitTargetConfigRequest(
            Double profitPercent,
            Double positionPercent) {
    }

    public record OrdersConfigRequest(
            String entryTimeInForce,
            String protectiveTimeInForce,
            Boolean outsideRegularTradingHours,
            Long interOrderDelayMillis) {
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
            StrategyTradeMode tradeDirection,
            List<Double> prices,
            List<SimulationBarRequest> bars,
            Integer fastPeriod,
            Integer slowPeriod,
            Long quantity,
            Double initialCapital,
            Double commissionPerOrder,
            Double slippagePercent,
            Boolean publishSignals,
            Integer pivotStrength,
            Double minimumTick,
            Integer tteMaxBars,
            Integer entryBufferTicks,
            Integer stopBufferTicks,
            Boolean minimumRoomEnabled,
            Double minimumRoomValue,
            Boolean allowDoubleTriple,
            Integer doubleTripleToleranceTicks,
            Boolean rejectGapOverEntry) {

        public StrategySimulationRequest(
                String symbol,
                MarketDataProvider provider,
                StrategyType strategy,
                StrategyTradeMode tradeDirection,
                List<Double> prices,
                List<SimulationBarRequest> bars,
                Integer fastPeriod,
                Integer slowPeriod,
                Long quantity,
                Double initialCapital,
                Double commissionPerOrder,
                Double slippagePercent,
                Boolean publishSignals) {
            this(symbol, provider, strategy, tradeDirection, prices, bars,
                    fastPeriod, slowPeriod, quantity, initialCapital,
                    commissionPerOrder, slippagePercent, publishSignals,
                    null, null, null, null, null, null, null, null, null, null);
        }
    }

    public record SimulationBarRequest(
            Integer index,
            Instant timestamp,
            String label,
            Double price,
            Double open,
            Double high,
            Double low,
            Double close,
            Long volume) {

        public double openValue() {
            return requiredPositive(open, "open");
        }

        public double highValue() {
            return requiredPositive(high, "high");
        }

        public double lowValue() {
            return requiredPositive(low, "low");
        }

        public double closeValue() {
            if (close != null) {
                return requiredPositive(close, "close");
            }
            return requiredPositive(price, "price");
        }

        public long volumeValue() {
            return volume == null ? 0L : volume;
        }

        private static double requiredPositive(Double value, String field) {
            if (value == null || value <= 0) {
                throw new IllegalArgumentException(field + " deve essere positivo");
            }
            return value;
        }
    }

    public record StrategySimulationResponse(
            String backtestId,
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
            boolean publishedSignals,
            List<JoeRossEvent> joeRossEvents,
            JoeRossPatternStatistics joeRossStatistics) {

        public StrategySimulationResponse {
            joeRossEvents = joeRossEvents == null ? List.of() : List.copyOf(joeRossEvents);
        }

        public StrategySimulationResponse(
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
            this(
                    null,
                    provider,
                    strategy,
                    symbol,
                    inputCount,
                    lastPrice,
                    signals,
                    priceSeries,
                    equityCurve,
                    trades,
                    metrics,
                    evaluationParameters,
                    publishedSignals,
                    List.of(),
                    null);
        }
    }

    public record PricePoint(
            int index,
            Instant timestamp,
            String label,
            double price,
            double open,
            double high,
            double low,
            double close,
            long volume) {
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
            TradeDirection direction,
            int entryIndex,
            int exitIndex,
            Instant signalAt,
            double signalPrice,
            String signalSource,
            String signalReason,
            Instant entryAt,
            Instant exitAt,
            long quantity,
            long remainingQuantity,
            double entryPrice,
            double exitPrice,
            double initialStopPrice,
            Double finalStopPrice,
            double tradingCapital,
            double riskPercent,
            double maximumRisk,
            double riskPerShare,
            double effectiveRisk,
            double capitalRequired,
            double grossPnl,
            double netPnl,
            double returnPercent,
            String exitReason,
            String tradeStatus,
            double mfe,
            double mae,
            Double mfeR,
            Double maeR,
            Map<String, String> strategyData,
            List<TakeProfitAllocation> takeProfits,
            List<TradeEventResult> events) {

        public TradeResult(
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
                double returnPercent,
                String exitReason,
                List<TradeEventResult> events) {
            this(
                    number,
                    side,
                    parseDirection(side),
                    entryIndex,
                    exitIndex,
                    null,
                    0.0,
                    null,
                    null,
                    entryAt,
                    exitAt,
                    quantity,
                    0L,
                    entryPrice,
                    exitPrice,
                    0.0,
                    null,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    entryPrice * quantity,
                    grossPnl,
                    netPnl,
                    returnPercent,
                    exitReason,
                    exitAt == null ? "OPEN" : "CLOSED",
                    0.0,
                    0.0,
                    null,
                    null,
                    Map.of(),
                    List.of(),
                    events);
        }

        public TradeResult {
            takeProfits = takeProfits == null ? List.of() : List.copyOf(takeProfits);
            events = events == null ? List.of() : List.copyOf(events);
            strategyData = strategyData == null ? Map.of() : Map.copyOf(strategyData);
        }

        private static TradeDirection parseDirection(String side) {
            if (side == null || side.isBlank()) {
                return TradeDirection.LONG;
            }
            return side.trim().equalsIgnoreCase("SHORT")
                    ? TradeDirection.SHORT
                    : TradeDirection.LONG;
        }
    }

    public record TradeEventResult(
            String eventType,
            int index,
            Instant occurredAt,
            double price,
            long quantity,
            long remainingQuantity,
            Double currentStopPrice,
            double grossPnl,
            double netPnl,
            double returnPercent,
            String details) {

        public TradeEventResult(
                String eventType,
                int index,
                Instant occurredAt,
                double price,
                long quantity,
                double grossPnl,
                double netPnl,
                double returnPercent,
                String details) {
            this(
                    eventType,
                    index,
                    occurredAt,
                    price,
                    quantity,
                    quantity,
                    null,
                    grossPnl,
                    netPnl,
                    returnPercent,
                    details);
        }
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
            StrategyTradeMode tradeDirection,
            int fastPeriod,
            int slowPeriod,
            long quantity,
            double initialCapital,
            double commissionPerOrder,
            double slippagePercent,
            SameBarExecutionPolicy sameBarExecutionPolicy,
            String executionModel) {
    }

    private record BacktestResult(
            List<EquityPoint> equityCurve,
            List<TradeResult> trades,
            PerformanceMetrics metrics,
            EvaluationParameters evaluationParameters) {
    }

    private record JoeRossAnalysis(
            List<TradingSignal> signals,
            List<JoeRossEvent> events,
            JoeRossPatternStatistics statistics) {

        private JoeRossAnalysis {
            signals = signals == null ? List.of() : List.copyOf(signals);
            events = events == null ? List.of() : List.copyOf(events);
        }

        private static JoeRossAnalysis empty() {
            return new JoeRossAnalysis(List.of(), List.of(), null);
        }
    }

    private record BacktestPendingEntry(
            TradeDirection direction,
            Instant signalAt,
            double signalPrice,
            String signalSource,
            String signalReason,
            BigDecimal entryPrice,
            BigDecimal stopLossPrice,
            BigDecimal tradingCapital,
            BigDecimal riskPercent,
            BigDecimal maximumRisk,
            BigDecimal riskPerShare,
            BigDecimal effectiveRisk,
            BigDecimal capitalRequired,
            long quantity,
            List<TakeProfitAllocation> takeProfits,
            boolean rejectGapOverEntry,
            int activeAfterBarIndex,
            Map<String, String> strategyMetadata) {

        private BacktestPendingEntry {
            takeProfits = takeProfits == null ? List.of() : List.copyOf(takeProfits);
            strategyMetadata = strategyMetadata == null
                    ? Map.of() : Map.copyOf(strategyMetadata);
        }
    }

    private static final class BacktestAccount {
        private double cash;

        private BacktestAccount(double cash) {
            this.cash = cash;
        }
    }

    private static final class BacktestPosition {
        private final TradeDirection direction;
        private final Instant signalAt;
        private final double signalPrice;
        private final String signalSource;
        private final String signalReason;
        private final int entryIndex;
        private final Instant entryAt;
        private final long quantity;
        private final double entryPrice;
        private final double initialStopPrice;
        private final double tradingCapital;
        private final double riskPercent;
        private final double maximumRisk;
        private final double riskPerShare;
        private final double effectiveRisk;
        private final double capitalRequired;
        private final List<TakeProfitAllocation> takeProfits;
        private final Map<String, String> strategyMetadata;
        private long remainingQuantity;
        private double currentStopPrice;
        private long activeStopQuantity;
        private int nextTakeProfitIndex;
        private long exitedQuantity;
        private double exitNotional;
        private double grossPnl;
        private double netPnl;
        private int exitIndex;
        private Instant exitAt;
        private String exitReason;
        private double maximumFavorableExcursion;
        private double maximumAdverseExcursion;
        private final List<TradeEventResult> events = new ArrayList<>();

        private BacktestPosition(
                TradeDirection direction,
                Instant signalAt,
                double signalPrice,
                String signalSource,
                String signalReason,
                int entryIndex,
                Instant entryAt,
                long quantity,
                double entryPrice,
                double stopLossPrice,
                double tradingCapital,
                double riskPercent,
                double maximumRisk,
                double riskPerShare,
                double effectiveRisk,
                double capitalRequired,
                List<TakeProfitAllocation> takeProfits,
                double entryCommission,
                Map<String, String> strategyMetadata) {
            this.direction = direction;
            this.signalAt = signalAt;
            this.signalPrice = signalPrice;
            this.signalSource = signalSource;
            this.signalReason = signalReason;
            this.entryIndex = entryIndex;
            this.entryAt = entryAt;
            this.quantity = quantity;
            this.remainingQuantity = quantity;
            this.entryPrice = entryPrice;
            this.initialStopPrice = stopLossPrice;
            this.tradingCapital = tradingCapital;
            this.riskPercent = riskPercent;
            this.maximumRisk = maximumRisk;
            this.riskPerShare = riskPerShare;
            this.effectiveRisk = effectiveRisk;
            this.capitalRequired = capitalRequired;
            this.currentStopPrice = stopLossPrice;
            this.activeStopQuantity = quantity;
            this.takeProfits = takeProfits == null ? List.of() : List.copyOf(takeProfits);
            this.strategyMetadata = strategyMetadata == null
                    ? Map.of() : Map.copyOf(strategyMetadata);
            this.netPnl = -entryCommission;
            addJoeRossSetupEvents();
            this.events.add(new TradeEventResult(
                    "ENTRY",
                    entryIndex,
                    entryAt,
                    entryPrice,
                    quantity,
                    quantity,
                    initialStopPrice,
                    0.0,
                    -entryCommission,
                    eventReturnPercent(-entryCommission, entryPrice, quantity),
                    null));
            this.events.add(new TradeEventResult(
                    "INITIAL STOP",
                    entryIndex,
                    entryAt,
                    stopLossPrice,
                    quantity,
                    quantity,
                    stopLossPrice,
                    0.0,
                    0.0,
                    0.0,
                    "INITIAL_STOP"));
            if (isJoeRossTrade()) {
                this.events.add(new TradeEventResult(
                        "TRADE_OPENED",
                        entryIndex,
                        entryAt,
                        entryPrice,
                        quantity,
                        quantity,
                        stopLossPrice,
                        0.0,
                        -entryCommission,
                        eventReturnPercent(-entryCommission, entryPrice, quantity),
                        "JOE_ROSS_123_RH_TTE"));
            }
        }

        private void updateExcursions(PricePoint point) {
            double favorable = direction == TradeDirection.LONG
                    ? point.high() - entryPrice
                    : entryPrice - point.low();
            double adverse = direction == TradeDirection.LONG
                    ? entryPrice - point.low()
                    : point.high() - entryPrice;
            maximumFavorableExcursion = Math.max(
                    maximumFavorableExcursion, Math.max(0.0, favorable));
            maximumAdverseExcursion = Math.max(
                    maximumAdverseExcursion, Math.max(0.0, adverse));
        }

        private boolean isJoeRossTrade() {
            return StrategyType.JOE_ROSS_123_RH_TTE.name().equals(
                    strategyMetadata.get("strategy"));
        }

        private void addJoeRossSetupEvents() {
            if (!isJoeRossTrade()) {
                return;
            }
            addSetupEvent("JOE_ROSS_POINT1_FOUND", "point1BarIndex",
                    "point1Time", "point1Price", "P1");
            addSetupEvent("JOE_ROSS_POINT2_FOUND", "point2BarIndex",
                    "point2Time", "point2Price", "P2");
            addSetupEvent("JOE_ROSS_POINT3_FOUND", "point3BarIndex",
                    "point3Time", "point3Price", "P3");
            addSetupEvent("JOE_ROSS_123_COMPLETED", "point3BarIndex",
                    "point3Time", "point3Price", "1-2-3");
            addSetupEvent("POINT2_BREAKOUT", "point2BreakoutBarIndex",
                    "point2BreakoutTime", "point2BreakoutPrice", "P2 breakout");
            addSetupEvent("ROSS_HOOK_CREATED", "rossHookBarIndex",
                    "rossHookTime", "rossHookPrice", "RH");
            String number = strategyMetadata.getOrDefault("tteBarNumber", "1");
            addSetupEvent("TTE_BAR_" + number + "_CREATED", "tteBarIndex",
                    "tteBarTime", "tteEntryPrice", "TTE" + number);
            addSetupEvent("TTE_ENTRY_CANDIDATE", "tteBarIndex",
                    "tteBarTime", "tteEntryPrice", "stop="
                            + strategyMetadata.get("initialStopPrice"));
            events.add(new TradeEventResult(
                    "TTE_TRIGGERED",
                    entryIndex,
                    entryAt,
                    entryPrice,
                    quantity,
                    quantity,
                    initialStopPrice,
                    0.0,
                    0.0,
                    0.0,
                    "tteBar=" + number));
        }

        private void addSetupEvent(
                String eventType,
                String indexKey,
                String timeKey,
                String priceKey,
                String details) {
            String indexValue = strategyMetadata.get(indexKey);
            String timeValue = strategyMetadata.get(timeKey);
            String priceValue = strategyMetadata.get(priceKey);
            if (indexValue == null || timeValue == null || priceValue == null) {
                return;
            }
            events.add(new TradeEventResult(
                    eventType,
                    Integer.parseInt(indexValue),
                    Instant.parse(timeValue),
                    Double.parseDouble(priceValue),
                    0,
                    quantity,
                    null,
                    0.0,
                    0.0,
                    0.0,
                    details));
        }
    }

    public record WorkbenchConfigResponse(
            List<MarketDataProvider> providerOptions,
            List<MarketDataMode> modeOptions,
            List<StrategyType> strategyOptions,
            List<TradingMode> tradingModeOptions,
            IbkrConfigResponse ibkr,
            MarketDataConfigResponse marketData,
            StrategyConfigResponse strategy,
            RuntimeConfigResponse runtime,
            RiskConfigResponse risk,
            TakeProfitConfigResponse takeProfit,
            OrdersConfigResponse orders,
            boolean killSwitchActive) {
    }

    public record IbkrConfigResponse(
            String host,
            int port,
            int clientId,
            String accountId,
            boolean autoConnect,
            boolean allowCustomPaperPort,
            long historicalDataTimeoutSeconds) {
    }

    public record MarketDataConfigResponse(
            boolean enabled,
            MarketDataProvider provider,
            MarketDataMode mode) {
    }

    public record StrategyConfigResponse(
            StrategyType type,
            boolean enabled,
            boolean autoExecuteSignals,
            int fastPeriod,
            int slowPeriod,
            long quantity,
            long signalCooldownSeconds) {
    }

    public record RuntimeConfigResponse(
            TradingMode mode,
            boolean liveTradingEnabled,
            boolean orderSubmissionEnabled,
            String paperAccountConfirmation,
            String requiredPaperConfirmation) {
    }

    public record RiskConfigResponse(
            double tradingCapital,
            double riskPerTradePercent,
            double maxOrderValue,
            double maxPositionValue,
            double maxLongNotional,
            double maxShortNotional,
            long maxQuantityPerOrder,
            int maxDailyOrders,
            int maxOpenOrders,
            double maxDailyLoss,
            boolean allowLong,
            boolean allowShortSelling,
            boolean useLeverage,
            double entryTicket,
            double commissionPerOrder,
            double slippagePercent,
            double transactionCostsPerShare) {
    }

    public record TakeProfitConfigResponse(
            boolean enabled,
            TakeProfitTargetConfigResponse target1,
            TakeProfitTargetConfigResponse target2,
            TakeProfitTargetConfigResponse target3) {
    }

    public record TakeProfitTargetConfigResponse(
            double profitPercent,
            double positionPercent) {
    }

    public record OrdersConfigResponse(
            String entryTimeInForce,
            String protectiveTimeInForce,
            boolean outsideRegularTradingHours,
            long interOrderDelayMillis) {
    }
}
