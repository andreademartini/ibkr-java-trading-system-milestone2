package it.andreademartini.trading.risk;

import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.ibkr.IbkrConnectionService;
import it.andreademartini.trading.ibkr.PaperAccountVerifier;
import it.andreademartini.trading.marketdata.MarketDataService;
import it.andreademartini.trading.orders.OrderSide;
import it.andreademartini.trading.orders.OrderTracker;
import it.andreademartini.trading.orders.PaperOrderRequest;
import it.andreademartini.trading.orders.PaperOrderType;
import it.andreademartini.trading.portfolio.PortfolioService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class RiskManager {

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

        int additionalOrders = isBracket(request.type()) ? 3 : 1;
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

        if (!risk.isAllowShortSelling()
                && projectedQuantity.compareTo(BigDecimal.ZERO) < 0) {
            reject("Vendita allo scoperto disabilitata");
        }

        double projectedPositionValue =
                projectedQuantity.abs().doubleValue() * referencePrice;
        if (projectedPositionValue > risk.getMaxPositionValue()) {
            reject("Valore posizione previsto " + projectedPositionValue
                    + " superiore al massimo " + risk.getMaxPositionValue());
        }

        validateProtectivePrices(request, referencePrice);
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
