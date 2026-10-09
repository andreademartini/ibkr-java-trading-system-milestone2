package it.andreademartini.trading.web;

import it.andreademartini.trading.backtest.export.BacktestRunRegistry;
import it.andreademartini.trading.config.BacktestProperties;
import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.config.StrategyType;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.ibkr.IbkrConnectionService;
import it.andreademartini.trading.marketdata.HistoricalDataService;
import it.andreademartini.trading.marketdata.MarketDataService;
import it.andreademartini.trading.orders.ProgressiveStopLossService;
import it.andreademartini.trading.risk.KillSwitchService;
import it.andreademartini.trading.risk.PositionSizingService;
import it.andreademartini.trading.risk.TakeProfitAllocationService;
import it.andreademartini.trading.risk.TradePricingService;
import it.andreademartini.trading.strategy.SignalService;
import it.andreademartini.trading.strategy.StrategyEngine;
import it.andreademartini.trading.strategy.StrategyTradeMode;
import it.andreademartini.trading.strategy.joeross.InitialStopMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TradingWorkbenchControllerJoeRoss123TteTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private final TradingSystemProperties properties = new TradingSystemProperties();
    private TradingWorkbenchController controller;

    @BeforeEach
    void setUp() {
        properties.getStrategy().getJoeRoss123Tte()
                .getBollingerPoint1Filter().setEnabled(false);
        properties.getRisk().setMaxOrderValue(20_000);
        properties.getRisk().setMaxPositionValue(20_000);
        properties.getRisk().setMaxLongNotional(20_000);
        TradePricingService pricing = new TradePricingService();
        controller = new TradingWorkbenchController(
                properties,
                new BacktestProperties(),
                mock(IbkrConnectionService.class),
                mock(MarketDataService.class),
                mock(HistoricalDataService.class),
                mock(ActionProfileService.class),
                mock(StrategyEngine.class),
                mock(SignalService.class),
                mock(KillSwitchService.class),
                new PositionSizingService(pricing),
                new TakeProfitAllocationService(properties, pricing),
                new ProgressiveStopLossService(properties),
                new BacktestRunRegistry());
    }

    @Test
    void sizesFromTteRiskAndUsesGeneralProgressiveStop() {
        List<TradingWorkbenchController.SimulationBarRequest> bars = baseBars();
        bars.add(bar(5, 105, 106.02, 101, 105.5));
        bars.add(bar(6, 107, 110.50, 106.50, 110));
        bars.add(bar(7, 106, 107, 105.90, 106));

        TradingWorkbenchController.StrategySimulationResponse response =
                controller.simulateStrategy(request(bars, InitialStopMode.TTE_BAR));

        assertThat(response.trades()).hasSize(1);
        TradingWorkbenchController.TradeResult trade = response.trades().getFirst();
        assertThat(trade.entryPrice()).isEqualTo(106.01);
        assertThat(trade.initialStopPrice()).isEqualTo(99.99);
        assertThat(trade.quantity()).isEqualTo(50);
        assertThat(trade.strategyData())
                .containsEntry("point1Price", "90.0")
                .containsEntry("point2Price", "110.0")
                .containsEntry("point3Price", "100.0")
                .containsEntry("tteBarNumber", "1")
                .containsEntry("roomToPoint2", "3.99")
                .doesNotContainKeys("rossHookPrice", "roomToRossHook");
        assertThat(trade.takeProfits().getFirst().price().doubleValue())
                .isEqualTo(110.0);
        assertThat(trade.strategyData())
                .containsEntry("tp1Source", "POINT2")
                .containsEntry("targetProfitPercent", "2.0")
                .containsEntry("targetProfitAmount", "200.00")
                .containsEntry("distanceToPoint2", "4.0")
                .containsEntry("delta", "4.0")
                .containsEntry("quantityCalculatedFromPoint2", "50")
                .containsEntry("expectedProfitAtPoint2", "200.0")
                .containsEntry("actualStopRiskAmount", "301.00");
        assertThat(trade.takeProfits()).extracting(
                it.andreademartini.trading.orders.TakeProfitAllocation::quantity)
                .containsExactly(30L, 10L, 10L);
        assertThat(trade.events())
                .anyMatch(event -> event.eventType().equals("TP1_FILLED")
                        && event.details().equals("JOE_ROSS_POINT2_TARGET"))
                .anyMatch(event -> event.eventType().equals("STOP MOVED")
                        && event.details().contains("TP1_TO_BREAK_EVEN"))
                .anyMatch(event -> event.eventType().equals("STOP_MOVED_TO_ENTRY"));
        assertThat(response.joeRossEvents())
                .noneMatch(event -> event.type().name().contains("ROSS_HOOK"));
    }

    @Test
    void gapFillsAtOpenAndRecalculatesRiskBasedPositionSize() {
        List<TradingWorkbenchController.SimulationBarRequest> bars = baseBars();
        bars.add(bar(5, 108, 109, 101, 108));
        bars.add(bar(6, 100, 101, 99, 99));

        TradingWorkbenchController.TradeResult trade = controller
                .simulateStrategy(request(bars, InitialStopMode.TTE_BAR))
                .trades().getFirst();

        assertThat(trade.entryPrice()).isEqualTo(108.0);
        assertThat(trade.initialStopPrice()).isEqualTo(99.99);
        assertThat(trade.riskPerShare()).isEqualTo(8.01);
        assertThat(trade.quantity()).isEqualTo(50);
        assertThat(trade.strategyData()).containsEntry("tteEntryPrice", "108.0");
    }

    @Test
    void progressiveStopFollowsPoint2DeltaTargetsAndClosesExactQuantity() {
        List<TradingWorkbenchController.SimulationBarRequest> bars = baseBars();
        bars.add(bar(5, 105, 106.02, 101, 105.5));
        bars.add(bar(6, 108, 110.10, 107, 110));
        bars.add(bar(7, 112, 118.10, 111, 118));
        bars.add(bar(8, 120, 126.10, 119, 126));

        TradingWorkbenchController.TradeResult trade = controller
                .simulateStrategy(request(bars, InitialStopMode.TTE_BAR))
                .trades().getFirst();

        assertThat(trade.takeProfits()).extracting(target -> target.price().doubleValue())
                .containsExactly(110.0, 118.0, 126.0);
        assertThat(trade.events()).extracting(
                TradingWorkbenchController.TradeEventResult::eventType)
                .contains("TP1_FILLED", "STOP_MOVED_TO_ENTRY",
                        "TP2_FILLED", "STOP_MOVED_TO_TP1",
                        "TP3_FILLED", "STOP_CANCELLED", "TRADE_CLOSED");
        assertThat(trade.remainingQuantity()).isZero();
    }

    @Test
    void shortRealExampleUsesP3LowDeltaForQuantityAndTargets() {
        List<TradingWorkbenchController.SimulationBarRequest> bars =
                new ArrayList<>(List.of(
                        bar(0, 28.5, 29.0, 28.0, 28.5),
                        bar(1, 29.5, 31.0, 28.0, 30.0),
                        bar(2, 29.0, 30.0, 28.0, 28.5),
                        bar(3, 28.0, 28.5, 27.70, 27.8),
                        bar(4, 28.0, 28.2, 27.86, 28.1),
                        bar(5, 28.6, 29.03, 28.52, 28.8),
                        bar(6, 28.7, 28.8, 28.50, 28.6),
                        bar(7, 28.2, 28.4, 27.69, 27.8),
                        bar(8, 27.0, 27.2, 26.05, 26.1),
                        bar(9, 25.0, 25.5, 24.40, 24.5)));
        TradingWorkbenchController.StrategySimulationRequest request =
                new TradingWorkbenchController.StrategySimulationRequest(
                        "AAPL", MarketDataProvider.IBKR,
                        StrategyType.JOE_ROSS_123_TTE,
                        StrategyTradeMode.SHORT_ONLY,
                        bars.stream().map(
                                TradingWorkbenchController.SimulationBarRequest::close)
                                .toList(),
                        bars, 2, 3, 1L, 10_000.0, 0.0, 0.0, false,
                        1, 0.01, 3, 1, 1, false, 0.5,
                        false, 1, false, "R", InitialStopMode.TTE_BAR, false);

        TradingWorkbenchController.TradeResult trade = controller
                .simulateStrategy(request).trades().getFirst();
        assertThat(trade.entryPrice()).isEqualTo(28.51);
        assertThat(trade.initialStopPrice()).isEqualTo(29.04);
        assertThat(trade.quantity()).isEqualTo(243);
        assertThat(trade.strategyData())
                .containsEntry("point3BarIndex", "5")
                .containsEntry("delta", "0.82")
                .containsEntry("quantityCalculatedFromPoint2", "243")
                .containsEntry("point3RangeRiskPerShare", "0.51")
                .containsEntry("point3RangeRiskAmount", "123.93")
                .containsEntry("actualOrderRiskPerShare", "0.53")
                .containsEntry("actualOrderRiskAmount", "128.79");
        assertThat(trade.takeProfits()).extracting(
                it.andreademartini.trading.orders.TakeProfitAllocation::price)
                .containsExactly(new java.math.BigDecimal("27.7"),
                        new java.math.BigDecimal("26.06"),
                        new java.math.BigDecimal("24.42"));
        assertThat(trade.takeProfits()).extracting(
                it.andreademartini.trading.orders.TakeProfitAllocation::quantity)
                .containsExactly(145L, 48L, 50L);
    }

    private TradingWorkbenchController.StrategySimulationRequest request(
            List<TradingWorkbenchController.SimulationBarRequest> bars,
            InitialStopMode stopMode) {
        return new TradingWorkbenchController.StrategySimulationRequest(
                "AAPL", MarketDataProvider.IBKR, StrategyType.JOE_ROSS_123_TTE,
                StrategyTradeMode.LONG_ONLY,
                bars.stream().map(TradingWorkbenchController.SimulationBarRequest::close).toList(),
                bars, 2, 3, 1L, 10_000.0, 0.0, 0.0, false,
                1, 0.01, 3, 1, 1, false, 0.5,
                false, 1, false, "R", stopMode, false);
    }

    private List<TradingWorkbenchController.SimulationBarRequest> baseBars() {
        return new ArrayList<>(List.of(
                bar(0, 98, 102, 95, 100),
                bar(1, 96, 99, 90, 94),
                bar(2, 95, 100, 94, 98),
                bar(3, 100, 110, 99, 108),
                bar(4, 104, 106, 100, 102)));
    }

    private TradingWorkbenchController.SimulationBarRequest bar(
            int index,
            double open,
            double high,
            double low,
            double close) {
        return new TradingWorkbenchController.SimulationBarRequest(
                index, START.plus(index, ChronoUnit.DAYS), String.valueOf(index),
                close, open, high, low, close, 0L);
    }
}
