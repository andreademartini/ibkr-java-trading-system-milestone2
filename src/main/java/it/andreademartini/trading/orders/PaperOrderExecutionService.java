package it.andreademartini.trading.orders;

import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.ibkr.IbkrApiClient;
import it.andreademartini.trading.marketdata.MarketDataService;
import it.andreademartini.trading.marketdata.MarketSnapshot;
import it.andreademartini.trading.portfolio.PortfolioService;
import it.andreademartini.trading.risk.KillSwitchService;
import it.andreademartini.trading.risk.RiskManager;
import it.andreademartini.trading.risk.TradeSetupService;
import it.andreademartini.trading.strategy.SignalAction;
import it.andreademartini.trading.strategy.TradeDirection;
import it.andreademartini.trading.strategy.TradingSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class PaperOrderExecutionService {

    private static final Logger log =
            LoggerFactory.getLogger(PaperOrderExecutionService.class);

    private final TradingSystemProperties properties;
    private final MarketDataService marketDataService;
    private final PortfolioService portfolioService;
    private final IbkrOrderFactory orderFactory;
    private final IbkrApiClient apiClient;
    private final OrderTracker orderTracker;
    private final RiskManager riskManager;
    private final KillSwitchService killSwitch;
    private final TradeSetupService tradeSetupService;

    public PaperOrderExecutionService(
            TradingSystemProperties properties,
            MarketDataService marketDataService,
            PortfolioService portfolioService,
            IbkrOrderFactory orderFactory,
            IbkrApiClient apiClient,
            OrderTracker orderTracker,
            RiskManager riskManager,
            KillSwitchService killSwitch,
            TradeSetupService tradeSetupService) {
        this.properties = properties;
        this.marketDataService = marketDataService;
        this.portfolioService = portfolioService;
        this.orderFactory = orderFactory;
        this.apiClient = apiClient;
        this.orderTracker = orderTracker;
        this.riskManager = riskManager;
        this.killSwitch = killSwitch;
        this.tradeSetupService = tradeSetupService;
    }

    public synchronized OrderSubmissionResult submit(PaperOrderRequest rawRequest) {
        PaperOrderRequest request = rawRequest.normalized();
        double referencePrice = resolveReferencePrice(request);

        riskManager.validate(request, referencePrice);
        logApprovedTradePlan(request.tradePlan(), request.symbol());

        TradingSystemProperties.InstrumentProperties instrument =
                marketDataService.requireInstrument(request.symbol());
        List<IbkrOrderCommand> commands =
                orderFactory.create(request, instrument, referencePrice);

        List<Integer> submittedOrderIds = new ArrayList<>();
        try {
            for (int index = 0; index < commands.size(); index++) {
                IbkrOrderCommand command = commands.get(index);
                apiClient.placeOrder(
                        command.orderId(),
                        command.contract(),
                        command.order());
                orderTracker.registerSubmitted(command);
                submittedOrderIds.add(command.orderId());

                log.warn("ORDINE PAPER INVIATO id={} parent={} role={} {} {} qty={}",
                        command.orderId(),
                        command.parentOrderId(),
                        command.role(),
                        command.side(),
                        command.symbol(),
                        command.quantity());

                if (index < commands.size() - 1) {
                    pauseBetweenAttachedOrders();
                }
            }

            riskManager.recordAcceptedAction();
            return new OrderSubmissionResult(
                    request.symbol(),
                    request.type(),
                    List.copyOf(submittedOrderIds),
                    "SUBMITTED_TO_TWS_PAPER",
                    Instant.now()
            );
        } catch (RuntimeException exception) {
            cancelPartiallySubmittedOrders(submittedOrderIds);
            killSwitch.activate(
                    "Errore durante invio gruppo ordini per " + request.symbol());
            throw new IllegalStateException(
                    "Invio ordine paper non completato; kill switch attivato",
                    exception);
        }
    }

    public void executeSignal(TradingSignal signal) {
        BigDecimal position = portfolioService.positionQuantity(signal.symbol());

        if (signal.action() == SignalAction.BUY) {
            if (position.compareTo(BigDecimal.ZERO) > 0) {
                log.info("Segnale BUY ignorato: posizione long già presente su {}",
                        signal.symbol());
                return;
            }
            if (position.compareTo(BigDecimal.ZERO) < 0) {
                log.warn("Segnale BUY chiude prima lo short su {}; "
                        + "nessuna inversione automatica nello stesso tick",
                        signal.symbol());
                closePosition(signal.symbol(), "signal-buy-close-short");
                return;
            }

            submit(createAutomaticEntry(signal, TradeDirection.LONG));
            return;
        }

        if (signal.action() == SignalAction.SELL) {
            if (position.compareTo(BigDecimal.ZERO) > 0) {
                closePosition(signal.symbol(), "signal-sell-close-long");
                return;
            }

            if (position.compareTo(BigDecimal.ZERO) < 0) {
                log.info("Segnale SELL ignorato: posizione short già presente su {}",
                        signal.symbol());
                return;
            }

            if (!properties.getRisk().isAllowShortSelling()) {
                log.info("Segnale SELL registrato ma non eseguito: short selling disabilitato");
                return;
            }

            submit(createAutomaticEntry(signal, TradeDirection.SHORT));
        }
    }

    public synchronized OrderSubmissionResult closePosition(
            String symbol,
            String reason) {
        BigDecimal quantity = portfolioService.positionQuantity(symbol);
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException(
                    "Nessuna posizione aperta su " + symbol);
        }

        cancelProtectiveOrders(symbol);

        long absoluteQuantity;
        try {
            absoluteQuantity = quantity.abs().longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    "La posizione non è una quantità intera supportata da questa milestone: "
                            + quantity,
                    exception);
        }

        TradeDirection direction = quantity.compareTo(BigDecimal.ZERO) > 0
                ? TradeDirection.LONG
                : TradeDirection.SHORT;
        OrderSide closeSide = direction == TradeDirection.LONG
                ? OrderSide.SELL
                : OrderSide.BUY;

        return submit(new PaperOrderRequest(
                symbol,
                closeSide,
                PaperOrderType.MARKET,
                absoluteQuantity,
                null,
                null,
                null,
                null,
                reason,
                direction,
                null
        ));
    }

    public void cancelProtectiveOrders(String symbol) {
        List<Integer> orderIds = orderTracker.activeProtectiveOrderIdsForSymbol(symbol);
        for (Integer orderId : orderIds) {
            try {
                apiClient.cancelOrder(orderId);
                log.warn("Richiesta cancellazione ordine paper {} per {}", orderId, symbol);
            } catch (RuntimeException exception) {
                killSwitch.activate(
                        "Impossibile cancellare ordine " + orderId + " per " + symbol);
                throw exception;
            }
        }
    }

    private PaperOrderRequest createAutomaticEntry(
            TradingSignal signal,
            TradeDirection direction) {
        TradePlan plan = tradeSetupService.create(signal.symbol(), direction);
        OrderSide side = direction == TradeDirection.LONG
                ? OrderSide.BUY
                : OrderSide.SELL;
        Double firstTakeProfit = plan.takeProfits().isEmpty()
                ? null
                : plan.takeProfits().getFirst().price().doubleValue();

        return new PaperOrderRequest(
                signal.symbol(),
                side,
                PaperOrderType.BRACKET,
                plan.quantity(),
                null,
                plan.stopLossPrice().doubleValue(),
                firstTakeProfit,
                null,
                "auto-" + signal.source(),
                direction,
                plan
        );
    }

    private double resolveReferencePrice(PaperOrderRequest request) {
        if (request.tradePlan() != null) {
            return request.tradePlan().entryPrice().doubleValue();
        }
        MarketSnapshot snapshot = marketDataService.snapshot(request.symbol())
                .orElse(MarketSnapshot.empty(request.symbol()));

        double marketPrice = request.side() == OrderSide.BUY
                ? snapshot.referencePriceForBuy()
                : snapshot.referencePriceForSell();

        if (marketPrice > 0) {
            return marketPrice;
        }
        if (request.limitPrice() != null) {
            return request.limitPrice();
        }
        if (request.stopPrice() != null) {
            return request.stopPrice();
        }
        return 0.0;
    }

    private void logApprovedTradePlan(TradePlan plan, String symbol) {
        if (plan == null) {
            return;
        }
        log.info("""
                ========== TRADE SETUP ==========
                Direction: {}
                Symbol: {}
                Capital: {}
                Maximum Risk: {}
                Entry: {}
                Stop: {}
                Risk Per Share: {}
                Quantity: {}
                Effective Risk: {}
                TP1: {}
                TP2: {}
                TP3: {}
                ORDER APPROVED
                """,
                plan.direction(),
                symbol,
                plan.tradingCapital(),
                plan.maximumRisk(),
                plan.entryPrice(),
                plan.stopLossPrice(),
                plan.totalRiskPerShare(),
                plan.quantity(),
                plan.effectiveRisk(),
                takeProfitPrice(plan, 1),
                takeProfitPrice(plan, 2),
                takeProfitPrice(plan, 3));
    }

    private Object takeProfitPrice(TradePlan plan, int targetNumber) {
        return plan.takeProfits().stream()
                .filter(target -> target.targetNumber() == targetNumber)
                .map(target -> (Object) target.price())
                .findFirst()
                .orElse("-");
    }

    private void pauseBetweenAttachedOrders() {
        long delayMillis = properties.getOrders().getInterOrderDelay().toMillis();
        if (delayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Thread interrotto durante l'invio del gruppo ordini", exception);
        }
    }

    private void cancelPartiallySubmittedOrders(List<Integer> orderIds) {
        for (Integer orderId : orderIds) {
            try {
                apiClient.cancelOrder(orderId);
            } catch (RuntimeException cancellationError) {
                log.error("Cancellazione di emergenza fallita per ordine {}",
                        orderId, cancellationError);
            }
        }
    }
}
