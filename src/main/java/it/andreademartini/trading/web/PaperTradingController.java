package it.andreademartini.trading.web;

import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.ibkr.IbkrConnectionService;
import it.andreademartini.trading.ibkr.PaperAccountVerifier;
import it.andreademartini.trading.marketdata.MarketDataService;
import it.andreademartini.trading.orders.OrderSubmissionResult;
import it.andreademartini.trading.orders.OrderTracker;
import it.andreademartini.trading.orders.PaperOrderExecutionService;
import it.andreademartini.trading.orders.PaperOrderRequest;
import it.andreademartini.trading.portfolio.PortfolioService;
import it.andreademartini.trading.risk.KillSwitchService;
import it.andreademartini.trading.risk.RiskManager;
import it.andreademartini.trading.strategy.SignalService;
import it.andreademartini.trading.strategy.TradingSignal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/paper")
public class PaperTradingController {

    private final TradingSystemProperties properties;
    private final IbkrConnectionService connectionService;
    private final PaperAccountVerifier accountVerifier;
    private final MarketDataService marketDataService;
    private final PortfolioService portfolioService;
    private final OrderTracker orderTracker;
    private final PaperOrderExecutionService orderExecutionService;
    private final SignalService signalService;
    private final KillSwitchService killSwitchService;
    private final RiskManager riskManager;

    public PaperTradingController(
            TradingSystemProperties properties,
            IbkrConnectionService connectionService,
            PaperAccountVerifier accountVerifier,
            MarketDataService marketDataService,
            PortfolioService portfolioService,
            OrderTracker orderTracker,
            PaperOrderExecutionService orderExecutionService,
            SignalService signalService,
            KillSwitchService killSwitchService,
            RiskManager riskManager) {
        this.properties = properties;
        this.connectionService = connectionService;
        this.accountVerifier = accountVerifier;
        this.marketDataService = marketDataService;
        this.portfolioService = portfolioService;
        this.orderTracker = orderTracker;
        this.orderExecutionService = orderExecutionService;
        this.signalService = signalService;
        this.killSwitchService = killSwitchService;
        this.riskManager = riskManager;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", properties.getMode());
        result.put("connectionState", connectionService.getState());
        result.put("marketDataProvider", properties.getMarketData().getProvider());
        result.put("paperAccountVerified", accountVerifier.isVerified());
        result.put("orderSubmissionEnabled", properties.isOrderSubmissionEnabled());
        result.put("autoExecuteSignals",
                properties.getStrategy().isAutoExecuteSignals());
        result.put("killSwitchActive", killSwitchService.isActive());
        result.put("acceptedActionsToday", riskManager.acceptedActionsToday());
        result.put("activeOrderCount", orderTracker.activeOrderCount());
        result.put("marketData", marketDataService.snapshots());
        result.put("positions", portfolioService.positions());
        result.put("accountSummary", portfolioService.accountSummary());
        return result;
    }

    @GetMapping("/signals")
    public Object signals() {
        return signalService.recentSignals();
    }

    @PostMapping("/signals")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TradingSignal publishManualSignal(
            @Valid @RequestBody ManualSignalRequest request) {
        TradingSignal signal = TradingSignal.create(
                request.symbol().trim().toUpperCase(),
                request.action(),
                request.price(),
                request.quantity(),
                "MANUAL_REST",
                request.reason() == null || request.reason().isBlank()
                        ? "manual-signal"
                        : request.reason()
        );
        signalService.publish(signal, request.execute());
        return signal;
    }

    @GetMapping("/orders")
    public Object orders() {
        return orderTracker.orders();
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderSubmissionResult submitOrder(
            @Valid @RequestBody PaperOrderRequest request) {
        return orderExecutionService.submit(request);
    }

    @GetMapping("/positions")
    public Object positions() {
        return portfolioService.positions();
    }

    @PostMapping("/positions/{symbol}/close")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderSubmissionResult closePosition(@PathVariable String symbol) {
        return orderExecutionService.closePosition(
                symbol.trim().toUpperCase(),
                "manual-close-position");
    }

    @PostMapping("/kill-switch/activate")
    public Map<String, Object> activateKillSwitch() {
        killSwitchService.activate("Attivazione manuale REST");
        return Map.of("killSwitchActive", true);
    }

    @PostMapping("/kill-switch/deactivate")
    public Map<String, Object> deactivateKillSwitch() {
        killSwitchService.deactivate();
        return Map.of("killSwitchActive", killSwitchService.isActive());
    }
}
