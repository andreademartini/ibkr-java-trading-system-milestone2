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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TradingWorkbenchControllerJoeRossTest {

    private static final Instant START = Instant.parse("2025-01-01T00:00:00Z");

    private final TradingSystemProperties properties = new TradingSystemProperties();
    private TradingWorkbenchController controller;

    @BeforeEach
    void setUp() {
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
    void sizesJoeRossTradeFromTteRiskAndExportsPatternMetadata() {
        List<TradingWorkbenchController.SimulationBarRequest> bars = baseBars();
        bars.add(bar(9, 110, 112.02, 109, 111));
        bars.add(bar(10, 108, 109, 106, 107));

        TradingWorkbenchController.StrategySimulationResponse response =
                controller.simulateStrategy(request(bars));

        assertThat(response.trades()).hasSize(1);
        TradingWorkbenchController.TradeResult trade = response.trades().getFirst();
        assertThat(trade.entryPrice()).isEqualTo(112.01);
        assertThat(trade.initialStopPrice()).isEqualTo(106.99);
        assertThat(trade.riskPerShare()).isEqualTo(5.02);
        assertThat(trade.quantity()).isEqualTo(39);
        assertThat(trade.strategyData())
                .containsEntry("point1Price", "90.0")
                .containsEntry("rossHookPrice", "114.0")
                .containsEntry("tteBarNumber", "1")
                .containsEntry("quantity", "39");
        assertThat(trade.mfeR()).isNotNull();
        assertThat(trade.maeR()).isNotNull();
    }

    @Test
    void appliesStopFirstWhenEntryAndInitialStopAreBothTouchedOnTriggerBar() {
        List<TradingWorkbenchController.SimulationBarRequest> bars = baseBars();
        bars.add(bar(9, 110, 113, 106, 107));

        TradingWorkbenchController.StrategySimulationResponse response =
                controller.simulateStrategy(request(bars));

        assertThat(response.trades()).hasSize(1);
        assertThat(response.trades().getFirst().events())
                .anyMatch(event -> event.eventType().equals("EXIT - STOP_LOSS")
                        && event.index() == 9);
        assertThat(response.trades().getFirst().events())
                .noneMatch(event -> event.eventType().equals("EXIT - TP1"));
    }

    @Test
    void remainsCompatibleWithTakeProfitAndProgressiveStop() {
        List<TradingWorkbenchController.SimulationBarRequest> bars = baseBars();
        bars.add(bar(9, 110, 112.02, 109, 111));
        bars.add(bar(10, 113, 114.30, 112.20, 114));
        bars.add(bar(11, 112.10, 113, 112.00, 112.10));

        TradingWorkbenchController.StrategySimulationResponse response =
                controller.simulateStrategy(request(bars));

        TradingWorkbenchController.TradeResult trade = response.trades().getFirst();
        assertThat(trade.events())
                .anyMatch(event -> event.eventType().equals("EXIT - TP1"));
        assertThat(trade.events())
                .anyMatch(event -> event.eventType().equals("STOP MOVED")
                        && event.details().contains("TP1_TO_BREAK_EVEN"));
        assertThat(trade.events())
                .anyMatch(event -> event.eventType().equals("EXIT - STOP_LOSS")
                        && event.price() == 112.01);
    }

    private TradingWorkbenchController.StrategySimulationRequest request(
            List<TradingWorkbenchController.SimulationBarRequest> bars) {
        return new TradingWorkbenchController.StrategySimulationRequest(
                "AAPL",
                MarketDataProvider.IBKR,
                StrategyType.JOE_ROSS_123_RH_TTE,
                StrategyTradeMode.LONG_ONLY,
                bars.stream().map(TradingWorkbenchController.SimulationBarRequest::close).toList(),
                bars,
                2,
                3,
                1L,
                10_000.0,
                0.0,
                0.0,
                false,
                1,
                0.01,
                3,
                1,
                1,
                false,
                0.5,
                false,
                1,
                true);
    }

    private List<TradingWorkbenchController.SimulationBarRequest> baseBars() {
        return new ArrayList<>(List.of(
                bar(0, 98, 102, 95, 100),
                bar(1, 96, 99, 90, 94),
                bar(2, 95, 100, 94, 98),
                bar(3, 100, 110, 99, 108),
                bar(4, 106, 107, 103, 104),
                bar(5, 104, 106, 100, 102),
                bar(6, 105, 111, 102, 110),
                bar(7, 110, 114, 108, 113),
                bar(8, 112, 112, 107, 108)));
    }

    private TradingWorkbenchController.SimulationBarRequest bar(
            int index,
            double open,
            double high,
            double low,
            double close) {
        return new TradingWorkbenchController.SimulationBarRequest(
                index,
                START.plus(index, ChronoUnit.DAYS),
                String.valueOf(index),
                close,
                open,
                high,
                low,
                close,
                0L);
    }
}
