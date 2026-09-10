package it.andreademartini.trading.risk;

import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.HistoricalDataService;
import it.andreademartini.trading.orders.TakeProfitAllocation;
import it.andreademartini.trading.orders.TradePlan;
import it.andreademartini.trading.orders.TradeState;
import it.andreademartini.trading.strategy.TradeDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.UUID;

@Service
public class TradeSetupService {

    private static final Logger log = LoggerFactory.getLogger(TradeSetupService.class);
    private static final MathContext MATH = MathContext.DECIMAL64;

    private final TradingSystemProperties properties;
    private final HistoricalDataService historicalDataService;
    private final PositionSizingService positionSizingService;
    private final TakeProfitAllocationService takeProfitAllocationService;

    public TradeSetupService(
            TradingSystemProperties properties,
            HistoricalDataService historicalDataService,
            PositionSizingService positionSizingService,
            TakeProfitAllocationService takeProfitAllocationService) {
        this.properties = properties;
        this.historicalDataService = historicalDataService;
        this.positionSizingService = positionSizingService;
        this.takeProfitAllocationService = takeProfitAllocationService;
    }

    public TradePlan create(String symbol, TradeDirection direction) {
        TradingSystemProperties.RiskProperties risk = properties.getRisk();
        PriceRange range = referenceRange(symbol);
        BigDecimal ticket = money(risk.getEntryTicket());
        BigDecimal high = money(range.high());
        BigDecimal low = money(range.low());

        BigDecimal entryPrice = direction == TradeDirection.LONG
                ? high.add(ticket, MATH)
                : low.subtract(ticket, MATH);
        BigDecimal stopLossPrice = direction == TradeDirection.LONG
                ? low
                : high.add(ticket, MATH);

        BigDecimal transactionCostsPerShare =
                transactionCostsPerShare(entryPrice, stopLossPrice, risk);
        BigDecimal fixedTransactionCosts =
                money(risk.getCommissionPerOrder()).multiply(BigDecimal.valueOf(2), MATH);
        BigDecimal maxNotional = direction == TradeDirection.LONG
                ? money(risk.getMaxLongNotional())
                : money(risk.getMaxShortNotional());
        maxNotional = maxNotional
                .min(money(risk.getMaxOrderValue()))
                .min(money(risk.getMaxPositionValue()));

        PositionSizingResult sizing = positionSizingService.calculate(
                money(risk.getTradingCapital()),
                money(risk.getRiskPerTradePercent()),
                entryPrice,
                stopLossPrice,
                transactionCostsPerShare,
                fixedTransactionCosts,
                maxNotional,
                risk.isUseLeverage(),
                risk.getMaxQuantityPerOrder());
        List<TakeProfitAllocation> takeProfits = takeProfitAllocationService.allocate(
                sizing.quantity(),
                entryPrice,
                direction);

        TradePlan plan = new TradePlan(
                UUID.randomUUID().toString(),
                direction,
                direction == TradeDirection.LONG
                        ? TradeState.OPENING_LONG
                        : TradeState.OPENING_SHORT,
                money(risk.getTradingCapital()),
                sizing.maximumRisk(),
                entryPrice,
                stopLossPrice,
                sizing.priceRisk(),
                sizing.totalRiskPerShare(),
                transactionCostsPerShare,
                fixedTransactionCosts,
                sizing.quantity(),
                sizing.effectiveRisk(),
                sizing.notional(),
                takeProfits);

        logTradeSetup(symbol, plan);
        return plan;
    }

    /** Builds the protected plan from a strategy-owned stop-entry and stop loss. */
    public TradePlan create(
            String symbol,
            TradeDirection direction,
            BigDecimal entryPrice,
            BigDecimal stopLossPrice) {
        TradingSystemProperties.RiskProperties risk = properties.getRisk();
        if (entryPrice == null || stopLossPrice == null
                || entryPrice.compareTo(BigDecimal.ZERO) <= 0
                || stopLossPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RiskRejectedException("Entry e stop della strategia devono essere positivi");
        }
        if ((direction == TradeDirection.LONG
                && entryPrice.compareTo(stopLossPrice) <= 0)
                || (direction == TradeDirection.SHORT
                && entryPrice.compareTo(stopLossPrice) >= 0)) {
            throw new RiskRejectedException("Entry/stop incompatibili con " + direction);
        }

        BigDecimal transactionCostsPerShare =
                transactionCostsPerShare(entryPrice, stopLossPrice, risk);
        BigDecimal fixedTransactionCosts =
                money(risk.getCommissionPerOrder()).multiply(BigDecimal.valueOf(2), MATH);
        BigDecimal maxNotional = direction == TradeDirection.LONG
                ? money(risk.getMaxLongNotional())
                : money(risk.getMaxShortNotional());
        maxNotional = maxNotional
                .min(money(risk.getMaxOrderValue()))
                .min(money(risk.getMaxPositionValue()));

        PositionSizingResult sizing = positionSizingService.calculate(
                money(risk.getTradingCapital()),
                money(risk.getRiskPerTradePercent()),
                entryPrice,
                stopLossPrice,
                transactionCostsPerShare,
                fixedTransactionCosts,
                maxNotional,
                risk.isUseLeverage(),
                risk.getMaxQuantityPerOrder());
        List<TakeProfitAllocation> takeProfits = takeProfitAllocationService.allocate(
                sizing.quantity(), entryPrice, direction);
        TradePlan plan = new TradePlan(
                UUID.randomUUID().toString(),
                direction,
                direction == TradeDirection.LONG
                        ? TradeState.OPENING_LONG : TradeState.OPENING_SHORT,
                money(risk.getTradingCapital()),
                sizing.maximumRisk(),
                entryPrice,
                stopLossPrice,
                sizing.priceRisk(),
                sizing.totalRiskPerShare(),
                transactionCostsPerShare,
                fixedTransactionCosts,
                sizing.quantity(),
                sizing.effectiveRisk(),
                sizing.notional(),
                takeProfits);
        logTradeSetup(symbol, plan);
        return plan;
    }

    private PriceRange referenceRange(String symbol) {
        return historicalDataService.latestStoredPreviousDailyBar(symbol)
                .filter(bar -> bar.high() > 0 && bar.low() > 0 && bar.high() > bar.low())
                .map(bar -> new PriceRange(bar.high(), bar.low()))
                .orElseThrow(() -> new RiskRejectedException(
                        "Barra daily precedente non disponibile per " + symbol
                                + ": importa o scarica lo storico daily prima di eseguire il trade"));
    }

    private BigDecimal transactionCostsPerShare(
            BigDecimal entryPrice,
            BigDecimal stopLossPrice,
            TradingSystemProperties.RiskProperties risk) {
        BigDecimal configured = money(risk.getTransactionCostsPerShare());
        BigDecimal slippageFactor = money(risk.getSlippagePercent())
                .divide(BigDecimal.valueOf(100), MATH);
        BigDecimal entrySlippage = entryPrice.multiply(slippageFactor, MATH);
        BigDecimal stopSlippage = stopLossPrice.multiply(slippageFactor, MATH);
        return configured.add(entrySlippage, MATH).add(stopSlippage, MATH);
    }

    private BigDecimal money(double value) {
        return BigDecimal.valueOf(value);
    }

    private void logTradeSetup(String symbol, TradePlan plan) {
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
                TRADE PLAN CALCULATED
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
                takeProfitPrice(plan.takeProfits(), 1),
                takeProfitPrice(plan.takeProfits(), 2),
                takeProfitPrice(plan.takeProfits(), 3));
    }

    private BigDecimal takeProfitPrice(
            List<TakeProfitAllocation> allocations,
            int targetNumber) {
        return allocations.stream()
                .filter(target -> target.targetNumber() == targetNumber)
                .map(TakeProfitAllocation::price)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private record PriceRange(
            double high,
            double low) {
    }
}
