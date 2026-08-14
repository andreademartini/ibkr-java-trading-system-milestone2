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
import it.andreademartini.trading.strategy.ManualOnlyStrategy;
import it.andreademartini.trading.strategy.MovingAverageCrossoverStrategy;
import it.andreademartini.trading.strategy.SignalAction;
import it.andreademartini.trading.strategy.SignalService;
import it.andreademartini.trading.strategy.StrategyTradeMode;
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
    private final IbkrConnectionService connectionService;
    private final MarketDataService marketDataService;
    private final HistoricalDataService historicalDataService;
    private final ActionProfileService actionProfileService;
    private final StrategyEngine strategyEngine;
    private final SignalService signalService;
    private final KillSwitchService killSwitchService;

    public TradingWorkbenchController(
            TradingSystemProperties properties,
            IbkrConnectionService connectionService,
            MarketDataService marketDataService,
            HistoricalDataService historicalDataService,
            ActionProfileService actionProfileService,
            StrategyEngine strategyEngine,
            SignalService signalService,
            KillSwitchService killSwitchService) {
        this.properties = properties;
        this.connectionService = connectionService;
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
                if (signal.action() == SignalAction.BUY && positionQuantity < 0) {
                    long quantity = Math.abs(positionQuantity);
                    double fillPrice = applySlippage(signal.price(), slippagePercent, true);
                    cash -= fillPrice * quantity + commissionPerOrder;
                    double grossPnl = (averageEntryPrice - fillPrice) * quantity;
                    double netPnl = grossPnl - entryCommission - commissionPerOrder;
                    trades.add(new TradeResult(
                            trades.size() + 1,
                            "SHORT",
                            entryIndex,
                            point.index(),
                            entryAt,
                            signal.occurredAt(),
                            quantity,
                            averageEntryPrice,
                            fillPrice,
                            grossPnl,
                            netPnl,
                            netPnl / (averageEntryPrice * quantity) * 100.0));
                    positionQuantity = 0;
                    averageEntryPrice = 0.0;
                    entryAt = null;
                    entryIndex = -1;
                    entryCommission = 0.0;
                }

                if (signal.action() == SignalAction.BUY
                        && positionQuantity == 0
                        && tradeDirection.allowsLong()) {
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

                if (signal.action() == SignalAction.SELL
                        && positionQuantity == 0
                        && tradeDirection.allowsShort()) {
                    long quantity = Math.max(1, signal.quantity());
                    double fillPrice = applySlippage(signal.price(), slippagePercent, false);
                    cash += fillPrice * quantity - commissionPerOrder;
                    positionQuantity = -quantity;
                    averageEntryPrice = fillPrice;
                    entryAt = signal.occurredAt();
                    entryIndex = point.index();
                    entryCommission = commissionPerOrder;
                }
            }

            if (positionQuantity != 0) {
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

    private String executionModel(StrategyTradeMode tradeDirection) {
        return switch (tradeDirection) {
            case LONG_ONLY -> "Solo long: BUY apre long, SELL chiude long; non apre short";
            case SHORT_ONLY -> "Solo short: SELL apre short, BUY chiude short; non apre long";
            case LONG_SHORT -> "Long/short: BUY apre o gira long, SELL apre o gira short; eventuale posizione aperta resta valorizzata a mercato";
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
