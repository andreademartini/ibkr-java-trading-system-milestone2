package it.andreademartini.trading.web;

import it.andreademartini.trading.config.BacktestProperties;
import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.config.StrategyType;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.backtest.export.BacktestRunRegistry;
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
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TradingWorkbenchControllerSimulationTest {

    private final TradingSystemProperties properties = new TradingSystemProperties();
    private final BacktestProperties backtestProperties = new BacktestProperties();
    private final TradePricingService tradePricingService = new TradePricingService();
    private final PositionSizingService positionSizingService =
            new PositionSizingService(tradePricingService);
    private final TakeProfitAllocationService takeProfitAllocationService =
            new TakeProfitAllocationService(properties, tradePricingService);
    private final ProgressiveStopLossService progressiveStopLossService =
            new ProgressiveStopLossService(properties);
    private final TradingWorkbenchController controller =
            new TradingWorkbenchController(
                    properties,
                    backtestProperties,
                    mock(IbkrConnectionService.class),
                    mock(MarketDataService.class),
                    mock(HistoricalDataService.class),
                    mock(ActionProfileService.class),
                    mock(StrategyEngine.class),
                    mock(SignalService.class),
                    mock(KillSwitchService.class),
                    positionSizingService,
                    takeProfitAllocationService,
                    progressiveStopLossService,
                    new BacktestRunRegistry());

    @Test
    void simulationReversesBetweenLongAndShortPositions() {
        TradingWorkbenchController.StrategySimulationResponse response =
                controller.simulateStrategy(request(StrategyTradeMode.LONG_SHORT));

        assertThat(response.trades())
                .extracting(TradingWorkbenchController.TradeResult::side)
                .containsExactly("LONG", "SHORT");
        assertThat(response.metrics().openPositionQuantity()).isPositive();
        assertThat(response.evaluationParameters().executionModel())
                .contains("Long/short");
    }

    @Test
    void simulationCanBeRestrictedToLongOnly() {
        TradingWorkbenchController.StrategySimulationResponse response =
                controller.simulateStrategy(request(StrategyTradeMode.LONG_ONLY));

        assertThat(response.trades())
                .extracting(TradingWorkbenchController.TradeResult::side)
                .containsExactly("LONG");
        assertThat(response.metrics().openPositionQuantity()).isGreaterThanOrEqualTo(0);
        assertThat(response.evaluationParameters().executionModel())
                .contains("Solo long");
    }

    @Test
    void simulationCanBeRestrictedToShortOnly() {
        TradingWorkbenchController.StrategySimulationResponse response =
                controller.simulateStrategy(request(StrategyTradeMode.SHORT_ONLY));

        assertThat(response.trades())
                .extracting(TradingWorkbenchController.TradeResult::side)
                .containsExactly("SHORT");
        assertThat(response.metrics().openPositionQuantity()).isZero();
        assertThat(response.evaluationParameters().executionModel())
                .contains("Solo short");
    }

    @Test
    void closedTradesExposeEntryAndExitEvents() {
        TradingWorkbenchController.StrategySimulationResponse response =
                controller.simulateStrategy(request(StrategyTradeMode.LONG_SHORT));

        TradingWorkbenchController.TradeResult firstTrade = response.trades().get(0);

        assertThat(firstTrade.events()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(firstTrade.events().get(0).eventType()).isEqualTo("ENTRY");
        assertThat(firstTrade.events().get(0).quantity()).isEqualTo(firstTrade.quantity());
        assertThat(firstTrade.events())
                .anyMatch(event -> event.eventType().startsWith("EXIT - "));
    }

    @Test
    void backtestMovesStopToEntryAfterTp1AndClosesRemainderAtBreakEven() {
        properties.getRisk().setEntryTicket(0.0);
        properties.getRisk().setRiskPerTradePercent(5.0);
        properties.getRisk().setMaxOrderValue(20_000.0);
        properties.getRisk().setMaxPositionValue(20_000.0);
        properties.getRisk().setMaxLongNotional(20_000.0);

        TradingWorkbenchController.StrategySimulationResponse response =
                controller.simulateStrategy(progressiveStopRequest());

        assertThat(response.trades()).hasSize(1);
        TradingWorkbenchController.TradeResult trade = response.trades().getFirst();
        assertThat(trade.events())
                .anyMatch(event -> event.eventType().equals("EXIT - TP1")
                        && event.quantity() == 50
                        && event.price() == 102.0);
        assertThat(trade.events())
                .anyMatch(event -> event.eventType().equals("STOP MOVED")
                        && event.price() == 100.0
                        && event.quantity() == 50
                        && event.details().contains("95.00 -> 100.00")
                        && event.details().contains("TP1_TO_BREAK_EVEN"));
        assertThat(trade.events())
                .anyMatch(event -> event.eventType().equals("EXIT - STOP_LOSS")
                        && event.quantity() == 50
                        && event.price() == 100.0);
        assertThat(trade.netPnl()).isEqualTo(100.0);
    }

    @Test
    void defaultSameBarPolicyExecutesStopBeforeTakeProfit() {
        properties.getRisk().setEntryTicket(0.0);
        properties.getRisk().setRiskPerTradePercent(5.0);
        properties.getRisk().setMaxOrderValue(20_000.0);
        properties.getRisk().setMaxPositionValue(20_000.0);
        properties.getRisk().setMaxLongNotional(20_000.0);

        TradingWorkbenchController.StrategySimulationResponse response =
                controller.simulateStrategy(sameBarStopFirstRequest());

        assertThat(response.evaluationParameters().sameBarExecutionPolicy().name())
                .isEqualTo("STOP_FIRST");
        TradingWorkbenchController.TradeResult trade = response.trades().getFirst();
        assertThat(trade.events())
                .anyMatch(event -> event.eventType().equals("EXIT - STOP_LOSS")
                        && event.quantity() == 100
                        && event.price() == 95.0);
        assertThat(trade.events())
                .noneMatch(event -> event.eventType().equals("EXIT - TP1"));
        assertThat(trade.events())
                .noneMatch(event -> event.eventType().equals("STOP MOVED"));
    }

    @Test
    void simulationRequestDeserializesBarsFromJson() throws Exception {
        String json = """
                {
                  "symbol": "AAPL",
                  "provider": "IBKR",
                  "strategy": "MOVING_AVERAGE_CROSSOVER",
                  "tradeDirection": "LONG_ONLY",
                  "prices": [229.31],
                  "bars": [
                    {
                      "index": 0,
                      "timestamp": "2026-08-25T00:00:00Z",
                      "label": "2026-08-25",
                      "open": 227.92,
                      "high": 230.10,
                      "low": 226.44,
                      "close": 229.31,
                      "volume": 45892100
                    }
                  ],
                  "fastPeriod": 2,
                  "slowPeriod": 3,
                  "quantity": 1,
                  "initialCapital": 10000,
                  "commissionPerOrder": 0,
                  "slippagePercent": 0,
                  "publishSignals": false
                }
                """;

        TradingWorkbenchController.StrategySimulationRequest request =
                new ObjectMapper().readValue(
                        json,
                        TradingWorkbenchController.StrategySimulationRequest.class);

        assertThat(request.bars()).hasSize(1);
        assertThat(request.bars().get(0).closeValue()).isEqualTo(229.31);
    }

    private TradingWorkbenchController.StrategySimulationRequest request(
            StrategyTradeMode tradeDirection) {
        TradingWorkbenchController.StrategySimulationRequest request =
                new TradingWorkbenchController.StrategySimulationRequest(
                        "SPY",
                        MarketDataProvider.IBKR,
                        StrategyType.MOVING_AVERAGE_CROSSOVER,
                        tradeDirection,
                        bars().stream().map(TradingWorkbenchController.SimulationBarRequest::close).toList(),
                        bars(),
                        2,
                        3,
                        1L,
                        10_000.0,
                        0.0,
                        0.0,
                        false);
        return request;
    }

    private TradingWorkbenchController.StrategySimulationRequest progressiveStopRequest() {
        List<TradingWorkbenchController.SimulationBarRequest> bars = List.of(
                bar(0, 100.0, 101.0, 98.0),
                bar(1, 99.0, 100.0, 97.0),
                bar(2, 98.0, 100.0, 95.0),
                bar(3, 101.0, 101.5, 98.5),
                bar(4, 100.0, 100.5, 99.5),
                bar(5, 102.0, 102.5, 100.5),
                bar(6, 100.0, 101.0, 100.0));
        return new TradingWorkbenchController.StrategySimulationRequest(
                "SPY",
                MarketDataProvider.IBKR,
                StrategyType.MOVING_AVERAGE_CROSSOVER,
                StrategyTradeMode.LONG_ONLY,
                bars.stream().map(TradingWorkbenchController.SimulationBarRequest::close).toList(),
                bars,
                2,
                3,
                1L,
                10_000.0,
                0.0,
                0.0,
                false);
    }

    private TradingWorkbenchController.StrategySimulationRequest sameBarStopFirstRequest() {
        List<TradingWorkbenchController.SimulationBarRequest> bars = List.of(
                bar(0, 100.0, 101.0, 98.0),
                bar(1, 99.0, 100.0, 97.0),
                bar(2, 98.0, 100.0, 95.0),
                bar(3, 101.0, 101.5, 98.5),
                bar(4, 100.0, 100.5, 99.5),
                bar(5, 96.0, 102.5, 94.0));
        return new TradingWorkbenchController.StrategySimulationRequest(
                "SPY",
                MarketDataProvider.IBKR,
                StrategyType.MOVING_AVERAGE_CROSSOVER,
                StrategyTradeMode.LONG_ONLY,
                bars.stream().map(TradingWorkbenchController.SimulationBarRequest::close).toList(),
                bars,
                2,
                3,
                1L,
                10_000.0,
                0.0,
                0.0,
                false);
    }

    private List<TradingWorkbenchController.SimulationBarRequest> bars() {
        return List.of(
                bar(0, 12.0, 12.5, 11.5),
                bar(1, 11.0, 11.5, 10.5),
                bar(2, 10.0, 10.5, 9.5),
                bar(3, 11.0, 11.5, 10.5),
                bar(4, 12.0, 12.5, 11.5),
                bar(5, 11.0, 12.0, 10.7),
                bar(6, 10.0, 10.8, 9.8),
                bar(7, 9.0, 9.7, 8.8),
                bar(8, 10.0, 10.6, 9.6),
                bar(9, 11.0, 11.2, 10.2),
                bar(10, 12.0, 12.6, 11.5));
    }

    private TradingWorkbenchController.SimulationBarRequest bar(
            int index,
            double close,
            double high,
            double low) {
        return new TradingWorkbenchController.SimulationBarRequest(
                index,
                Instant.parse("2024-01-01T00:00:00Z").plusSeconds(index * 86_400L),
                String.valueOf(index),
                close,
                close,
                high,
                low,
                close,
                1_000L);
    }
}
