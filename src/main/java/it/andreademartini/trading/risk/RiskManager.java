package it.andreademartini.trading.risk;

import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.ibkr.IbkrConnectionService;
import it.andreademartini.trading.ibkr.PaperAccountVerifier;
import it.andreademartini.trading.marketdata.MarketDataService;
import it.andreademartini.trading.orders.OrderSide;
import it.andreademartini.trading.orders.OrderTracker;
import it.andreademartini.trading.orders.PaperOrderRequest;
import it.andreademartini.trading.orders.PaperOrderType;
import it.andreademartini.trading.orders.TakeProfitAllocation;
import it.andreademartini.trading.orders.TradePlan;
import it.andreademartini.trading.strategy.TradeDirection;
import it.andreademartini.trading.portfolio.PortfolioService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;

@Service
public class RiskManager {

    private static final MathContext MATH = MathContext.DECIMAL64;

    private final TradingSystemProperties properties;
    private final TradingSafetyGuard safetyGuard;
    private final KillSwitchService killSwitch;
    private final PaperAccountVerifier accountVerifier;
    private final IbkrConnectionService connectionService;
    private final PortfolioService portfolioService;
    private final OrderTracker orderTracker;
    private final MarketDataService marketDataService;

    private LocalDate counterDate = LocalDate.now();
    private int acceptedActionsToday;

    public RiskManager(
            TradingSystemProperties properties,
            TradingSafetyGuard safetyGuard,
            KillSwitchService killSwitch,
            PaperAccountVerifier accountVerifier,
            IbkrConnectionService connectionService,
            PortfolioService portfolioService,
            OrderTracker orderTracker,
            MarketDataService marketDataService) {
        this.properties = properties;
        this.safetyGuard = safetyGuard;
        this.killSwitch = killSwitch;
        this.accountVerifier = accountVerifier;
        this.connectionService = connectionService;
        this.portfolioService = portfolioService;
        this.orderTracker = orderTracker;
        this.marketDataService = marketDataService;
    }

    public synchronized void validate(PaperOrderRequest request, double referencePrice) {
        resetDailyCounterIfNeeded();
        safetyGuard.assertPaperOrderConfiguration();
        accountVerifier.assertVerified();

        if (!connectionService.isReady()) {
            reject("Connessione IBKR non pronta");
        }

        marketDataService.requireInstrument(request.symbol());

        if (request.quantity() <= 0) {
            reject("La quantità deve essere maggiore di zero");
        }

        if (referencePrice <= 0) {
            reject("Prezzo di riferimento non disponibile per " + request.symbol());
        }

        TradingSystemProperties.RiskProperties risk = properties.getRisk();
        BigDecimal currentQuantity =
                portfolioService.positionQuantity(request.symbol());
        BigDecimal signedOrderQuantity = BigDecimal.valueOf(
                request.side() == OrderSide.BUY
                        ? request.quantity()
                        : -request.quantity());
        BigDecimal projectedQuantity = currentQuantity.add(signedOrderQuantity);
        boolean riskReducing = isRiskReducing(currentQuantity, projectedQuantity);
        if (!riskReducing && request.tradePlan() == null) {
            reject("Gli ordini di ingresso richiedono un TradePlan calcolato da PositionSizingService");
        }

        /*
         * Il kill switch blocca nuove esposizioni ma lascia sempre possibile
         * una chiusura che riduce la posizione esistente.
         */
        if (killSwitch.isActive() && !riskReducing) {
            reject("Kill switch attivo: sono consentiti solo ordini di riduzione posizione");
        }

        if (!riskReducing && request.quantity() > risk.getMaxQuantityPerOrder()) {
            reject("Quantità " + request.quantity()
                    + " superiore al massimo " + risk.getMaxQuantityPerOrder());
        }

        double estimatedValue = referencePrice * request.quantity();
        if (!riskReducing && estimatedValue > risk.getMaxOrderValue()) {
            reject("Valore ordine stimato " + estimatedValue
                    + " superiore al massimo " + risk.getMaxOrderValue());
        }

        int additionalOrders = expectedOrderCount(request);
        if (!riskReducing
                && orderTracker.activeOrderCount() + additionalOrders
                > risk.getMaxOpenOrders()) {
            reject("Numero massimo di ordini aperti superato");
        }

        if (!riskReducing && acceptedActionsToday >= risk.getMaxDailyOrders()) {
            reject("Numero massimo di azioni d'ordine giornaliere raggiunto");
        }

        double realizedPnl = portfolioService.realizedPnl();
        if (!riskReducing && realizedPnl <= -risk.getMaxDailyLoss()) {
            reject("Perdita giornaliera massima raggiunta: realizedPnL=" + realizedPnl);
        }

        if (!riskReducing
                && !risk.isAllowShortSelling()
                && projectedQuantity.compareTo(BigDecimal.ZERO) < 0) {
            reject("Vendita allo scoperto disabilitata");
        }

        if (!riskReducing
                && !risk.isAllowLong()
                && projectedQuantity.compareTo(BigDecimal.ZERO) > 0) {
            reject("Operazioni long disabilitate");
        }

        double projectedPositionValue =
                projectedQuantity.abs().doubleValue() * referencePrice;
        if (!riskReducing && projectedPositionValue > risk.getMaxPositionValue()) {
            reject("Valore posizione previsto " + projectedPositionValue
                    + " superiore al massimo " + risk.getMaxPositionValue());
        }

        if (request.tradePlan() != null) {
            validateTradePlan(request);
        } else {
            validateProtectivePrices(request, referencePrice);
        }
    }

    public synchronized void recordAcceptedAction() {
        resetDailyCounterIfNeeded();
        acceptedActionsToday++;
    }

    public synchronized int acceptedActionsToday() {
        resetDailyCounterIfNeeded();
        return acceptedActionsToday;
    }

    private boolean isRiskReducing(
            BigDecimal currentQuantity,
            BigDecimal projectedQuantity) {
        if (currentQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        boolean doesNotCrossThroughZero =
                currentQuantity.signum() == projectedQuantity.signum()
                        || projectedQuantity.compareTo(BigDecimal.ZERO) == 0;

        return doesNotCrossThroughZero
                && projectedQuantity.abs().compareTo(currentQuantity.abs()) < 0;
    }

    private void validateProtectivePrices(
            PaperOrderRequest request,
            double referencePrice) {
        if (request.trailingPercent() != null
                && (request.trailingPercent() <= 0
                || request.trailingPercent() > 50)) {
            reject("trailingPercent deve essere maggiore di 0 e non superiore a 50");
        }

        if (request.type() == PaperOrderType.BRACKET) {
            if (request.takeProfitPrice() == null || request.stopPrice() == null) {
                reject("BRACKET richiede takeProfitPrice e stopPrice");
            }
            validateBracketDirections(
                    request.side(),
                    referencePrice,
                    request.takeProfitPrice(),
                    request.stopPrice());
        }

        if (request.type() == PaperOrderType.BRACKET_WITH_TRAILING_STOP) {
            if (request.takeProfitPrice() == null
                    || request.trailingPercent() == null) {
                reject("BRACKET_WITH_TRAILING_STOP richiede "
                        + "takeProfitPrice e trailingPercent");
            }
            if (request.side() == OrderSide.BUY
                    && request.takeProfitPrice() <= referencePrice) {
                reject("Per un BUY il take profit deve essere sopra il prezzo corrente");
            }
            if (request.side() == OrderSide.SELL
                    && request.takeProfitPrice() >= referencePrice) {
                reject("Per uno short SELL il take profit deve essere sotto il prezzo corrente");
            }
        }
    }

    private void validateTradePlan(PaperOrderRequest request) {
        TradePlan plan = request.tradePlan();
        if (plan.quantity() <= 0 || request.quantity() != plan.quantity()) {
            reject("Quantita' piano trade non coerente con l'ordine");
        }
        if (request.direction() != null && request.direction() != plan.direction()) {
            reject("Direzione ordine non coerente con il TradePlan");
        }

        TradingSystemProperties.RiskProperties risk = properties.getRisk();
        if (plan.direction() == TradeDirection.LONG) {
            validateLongPlan(request, plan, risk);
        } else {
            validateShortPlan(request, plan, risk);
        }

        BigDecimal configuredMaximumRisk = BigDecimal.valueOf(risk.getTradingCapital())
                .multiply(BigDecimal.valueOf(risk.getRiskPerTradePercent()), MATH)
                .divide(BigDecimal.valueOf(100), MATH);
        boolean stopRiskSized = plan.positionSizingMode()
                != PositionSizingMode.JOE_ROSS_POINT2_DELTA;
        if (stopRiskSized && (plan.maximumRisk().compareTo(configuredMaximumRisk) > 0
                || plan.effectiveRisk().compareTo(configuredMaximumRisk) > 0)) {
            reject("Rischio effettivo superiore al rischio massimo configurato");
        }

        BigDecimal notionalLimit = plan.direction() == TradeDirection.LONG
                ? BigDecimal.valueOf(risk.getMaxLongNotional())
                : BigDecimal.valueOf(risk.getMaxShortNotional());
        if (!risk.isUseLeverage()) {
            notionalLimit = notionalLimit.min(BigDecimal.valueOf(risk.getTradingCapital()));
        }
        if (plan.notional().compareTo(notionalLimit) > 0) {
            reject("Nozionale " + plan.notional()
                    + " superiore al limite " + notionalLimit);
        }

        if (plan.effectiveRisk().compareTo(BigDecimal.ZERO) <= 0
                || plan.totalRiskPerShare().compareTo(BigDecimal.ZERO) <= 0) {
            reject("Rischio trade non valido");
        }

        validateTakeProfitQuantity(plan);
    }

    private void validateLongPlan(
            PaperOrderRequest request,
            TradePlan plan,
            TradingSystemProperties.RiskProperties risk) {
        if (!risk.isAllowLong()) {
            reject("Operazioni long disabilitate");
        }
        if (request.side() != OrderSide.BUY) {
            reject("Un ingresso LONG deve usare BUY");
        }
        if (plan.entryPrice().compareTo(plan.stopLossPrice()) <= 0) {
            reject("Per un LONG entryPrice deve essere maggiore dello stop loss");
        }

        BigDecimal previous = plan.entryPrice();
        for (TakeProfitAllocation target : plan.takeProfits()) {
            if (target.quantity() == 0) {
                continue;
            }
            if (target.price().compareTo(previous) <= 0) {
                reject("Per un LONG i take profit devono salire sopra l'entry");
            }
            previous = target.price();
        }
    }

    private void validateShortPlan(
            PaperOrderRequest request,
            TradePlan plan,
            TradingSystemProperties.RiskProperties risk) {
        if (!risk.isAllowShortSelling()) {
            reject("Vendita allo scoperto disabilitata");
        }
        if (request.side() != OrderSide.SELL) {
            reject("Un ingresso SHORT deve usare SELL");
        }
        if (plan.entryPrice().compareTo(plan.stopLossPrice()) >= 0) {
            reject("Per uno SHORT entryPrice deve essere minore dello stop loss");
        }

        BigDecimal previous = plan.entryPrice();
        for (TakeProfitAllocation target : plan.takeProfits()) {
            if (target.quantity() == 0) {
                continue;
            }
            if (target.price().compareTo(previous) >= 0) {
                reject("Per uno SHORT i take profit devono scendere sotto l'entry");
            }
            previous = target.price();
        }
    }

    private void validateTakeProfitQuantity(TradePlan plan) {
        if (!properties.getTakeProfit().isEnabled()) {
            return;
        }
        long totalTakeProfitQuantity = plan.takeProfits().stream()
                .mapToLong(TakeProfitAllocation::quantity)
                .sum();
        if (totalTakeProfitQuantity != plan.quantity()) {
            reject("La somma quantita' take profit deve essere uguale alla posizione");
        }
    }

    private void validateBracketDirections(
            OrderSide side,
            double referencePrice,
            double takeProfit,
            double stopLoss) {
        if (side == OrderSide.BUY) {
            if (takeProfit <= referencePrice) {
                reject("Per un BUY il take profit deve essere sopra il prezzo corrente");
            }
            if (stopLoss >= referencePrice) {
                reject("Per un BUY lo stop loss deve essere sotto il prezzo corrente");
            }
        } else {
            if (takeProfit >= referencePrice) {
                reject("Per uno short SELL il take profit deve essere sotto il prezzo corrente");
            }
            if (stopLoss <= referencePrice) {
                reject("Per uno short SELL lo stop loss deve essere sopra il prezzo corrente");
            }
        }
    }

    private boolean isBracket(PaperOrderType type) {
        return type == PaperOrderType.BRACKET
                || type == PaperOrderType.BRACKET_WITH_TRAILING_STOP;
    }

    private int expectedOrderCount(PaperOrderRequest request) {
        if (request.tradePlan() != null) {
            long takeProfitOrders = request.tradePlan().takeProfits().stream()
                    .filter(target -> target.quantity() > 0)
                    .count();
            return 2 + (int) takeProfitOrders;
        }
        return isBracket(request.type()) ? 3 : 1;
    }

    private void resetDailyCounterIfNeeded() {
        LocalDate today = LocalDate.now();
        if (!today.equals(counterDate)) {
            counterDate = today;
            acceptedActionsToday = 0;
        }
    }

    private void reject(String reason) {
        throw new RiskRejectedException(reason);
    }
}
