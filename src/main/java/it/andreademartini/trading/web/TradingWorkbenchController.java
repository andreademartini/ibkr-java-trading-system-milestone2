package it.andreademartini.trading.web;

import it.andreademartini.trading.config.MarketDataMode;
import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.config.StrategyType;
import it.andreademartini.trading.config.TradingMode;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.ibkr.IbkrConnectionService;
import it.andreademartini.trading.ibkr.IbkrConnectionTestResult;
import it.andreademartini.trading.marketdata.HistoricalDataService;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.marketdata.HistoricalSeries;
import it.andreademartini.trading.marketdata.MarketDataService;
import it.andreademartini.trading.risk.KillSwitchService;
import it.andreademartini.trading.risk.PositionSizingResult;
import it.andreademartini.trading.risk.PositionSizingService;
import it.andreademartini.trading.risk.RiskRejectedException;
import it.andreademartini.trading.risk.TakeProfitAllocationService;
import it.andreademartini.trading.orders.TakeProfitAllocation;
import it.andreademartini.trading.strategy.ManualOnlyStrategy;
import it.andreademartini.trading.strategy.MovingAverageCrossoverStrategy;
import it.andreademartini.trading.strategy.SignalAction;
import it.andreademartini.trading.strategy.SignalService;
import it.andreademartini.trading.strategy.StrategyTradeMode;
import it.andreademartini.trading.strategy.TradeDirection;
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

import java.math.BigDecimal;
import java.math.MathContext;
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

    private static final MathContext MATH = MathContext.DECIMAL64;

    private final TradingSystemProperties properties;
    private final IbkrConnectionService connectionService;
    private final MarketDataService marketDataService;
    private final HistoricalDataService historicalDataService;
    private final ActionProfileService actionProfileService;
    private final StrategyEngine strategyEngine;
    private final SignalService signalService;
    private final KillSwitchService killSwitchService;
    private final PositionSizingService positionSizingService;
    private final TakeProfitAllocationService takeProfitAllocationService;

    public TradingWorkbenchController(
            TradingSystemProperties properties,
            IbkrConnectionService connectionService,
            MarketDataService marketDataService,
            HistoricalDataService historicalDataService,
            ActionProfileService actionProfileService,
            StrategyEngine strategyEngine,
            SignalService signalService,
            KillSwitchService killSwitchService,
            PositionSizingService positionSizingService,
            TakeProfitAllocationService takeProfitAllocationService) {
        this.properties = properties;
        this.connectionService = connectionService;
        this.marketDataService = marketDataService;
        this.historicalDataService = historicalDataService;
        this.actionProfileService = actionProfileService;
        this.strategyEngine = strategyEngine;
        this.signalService = signalService;
        this.killSwitchService = killSwitchService;
        this.positionSizingService = positionSizingService;
        this.takeProfitAllocationService = takeProfitAllocationService;
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

        List<TradingSignal> signals = new ArrayList<>();
        for (PricePoint point : priceSeries) {
            strategy.onPrice(symbol, point.close(), point.timestamp())
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
                priceSeries.size(),
                priceSeries.get(priceSeries.size() - 1).close(),
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
                position = processProtectiveExits(
                        position,
                        point,
                        account,
                        commissionPerOrder,
                        slippagePercent,
                        trades);
            }

            if (position == null && pendingEntry != null
                    && entryHit(pendingEntry, point)) {
                position = openPosition(
                        pendingEntry,
                        point,
                        account,
                        commissionPerOrder,
                        slippagePercent);
                pendingEntry = null;
            }

            while (signalIndex < signals.size()
                    && !signals.get(signalIndex).occurredAt().isAfter(point.timestamp())) {
                TradingSignal signal = signals.get(signalIndex++);

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
                            previousPoint(priceSeries, index),
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
        if (stopLossHit(position, point)) {
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
            position.nextTakeProfitIndex++;
        }

        if (position.remainingQuantity == 0) {
            recordClosedTrade(position, trades);
            return null;
        }
        return position;
    }

    private BacktestPendingEntry createPendingEntry(
            TradeDirection direction,
            PricePoint referencePoint,
            double tradingCapital,
            double commissionPerOrder,
            double slippagePercent) {
        if (referencePoint == null || tradingCapital <= 0) {
            return null;
        }

        TradingSystemProperties.RiskProperties risk = properties.getRisk();
        if (direction == TradeDirection.LONG && !risk.isAllowLong()) {
            return null;
        }
        if (direction == TradeDirection.SHORT && !risk.isAllowShortSelling()) {
            return null;
        }

        BigDecimal ticket = money(risk.getEntryTicket());
        BigDecimal high = money(referencePoint.high());
        BigDecimal low = money(referencePoint.low());
        BigDecimal entryPrice = direction == TradeDirection.LONG
                ? high.add(ticket, MATH)
                : low.subtract(ticket, MATH);
        BigDecimal stopLossPrice = direction == TradeDirection.LONG
                ? low
                : high.add(ticket, MATH);
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
                    entryPrice,
                    stopLossPrice,
                    sizing.quantity(),
                    takeProfits);
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
        return new BacktestPosition(
                pendingEntry.direction(),
                point.index(),
                point.timestamp(),
                quantity,
                fillPrice,
                pendingEntry.stopLossPrice().doubleValue(),
                pendingEntry.takeProfits(),
                commissionPerOrder);
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
                grossPnl,
                netPnl,
                eventReturnPercent(netPnl, position.entryPrice, quantity)));
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
                position.entryIndex,
                position.exitIndex,
                position.entryAt,
                position.exitAt,
                position.quantity,
                position.entryPrice,
                exitPrice,
                position.grossPnl,
                position.netPnl,
                denominator == 0.0 ? 0.0 : position.netPnl / denominator * 100.0,
                position.exitReason,
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

    private boolean entryHit(BacktestPendingEntry pendingEntry, PricePoint point) {
        double entryPrice = pendingEntry.entryPrice().doubleValue();
        return pendingEntry.direction() == TradeDirection.LONG
                ? point.high() >= entryPrice
                : point.low() <= entryPrice;
    }

    private boolean stopLossHit(BacktestPosition position, PricePoint point) {
        return position.direction == TradeDirection.LONG
                ? point.low() <= position.stopLossPrice
                : point.high() >= position.stopLossPrice;
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
        double rawPrice = position.stopLossPrice;
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
            Boolean publishSignals) {
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

        public TradeResult {
            events = events == null ? List.of() : List.copyOf(events);
        }
    }

    public record TradeEventResult(
            String eventType,
            int index,
            Instant occurredAt,
            double price,
            long quantity,
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
            StrategyTradeMode tradeDirection,
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

    private record BacktestPendingEntry(
            TradeDirection direction,
            BigDecimal entryPrice,
            BigDecimal stopLossPrice,
            long quantity,
            List<TakeProfitAllocation> takeProfits) {

        private BacktestPendingEntry {
            takeProfits = takeProfits == null ? List.of() : List.copyOf(takeProfits);
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
        private final int entryIndex;
        private final Instant entryAt;
        private final long quantity;
        private final double entryPrice;
        private final double stopLossPrice;
        private final List<TakeProfitAllocation> takeProfits;
        private long remainingQuantity;
        private int nextTakeProfitIndex;
        private long exitedQuantity;
        private double exitNotional;
        private double grossPnl;
        private double netPnl;
        private int exitIndex;
        private Instant exitAt;
        private String exitReason;
        private final List<TradeEventResult> events = new ArrayList<>();

        private BacktestPosition(
                TradeDirection direction,
                int entryIndex,
                Instant entryAt,
                long quantity,
                double entryPrice,
                double stopLossPrice,
                List<TakeProfitAllocation> takeProfits,
                double entryCommission) {
            this.direction = direction;
            this.entryIndex = entryIndex;
            this.entryAt = entryAt;
            this.quantity = quantity;
            this.remainingQuantity = quantity;
            this.entryPrice = entryPrice;
            this.stopLossPrice = stopLossPrice;
            this.takeProfits = takeProfits == null ? List.of() : List.copyOf(takeProfits);
            this.netPnl = -entryCommission;
            this.events.add(new TradeEventResult(
                    "ENTRY",
                    entryIndex,
                    entryAt,
                    entryPrice,
                    quantity,
                    0.0,
                    -entryCommission,
                    eventReturnPercent(-entryCommission, entryPrice, quantity)));
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
