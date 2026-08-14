package it.andreademartini.trading.web;

import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.config.StrategyType;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.ibkr.IbkrConnectionService;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.marketdata.HistoricalDataService;
import it.andreademartini.trading.marketdata.MarketDataService;
import it.andreademartini.trading.risk.KillSwitchService;
import it.andreademartini.trading.risk.PositionSizingService;
import it.andreademartini.trading.risk.TakeProfitAllocationService;
import it.andreademartini.trading.risk.TradePricingService;
import it.andreademartini.trading.strategy.SignalService;
import it.andreademartini.trading.strategy.StrategyEngine;
import it.andreademartini.trading.strategy.StrategyTradeMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TradingWorkbenchControllerSimulationTest {

    private final TradingSystemProperties properties = new TradingSystemProperties();
    private final TradePricingService tradePricingService = new TradePricingService();
    private final PositionSizingService positionSizingService =
            new PositionSizingService(tradePricingService);
    private final TakeProfitAllocationService takeProfitAllocationService =
            new TakeProfitAllocationService(properties, tradePricingService);
    private final TradingWorkbenchController controller =
            new TradingWorkbenchController(
                    properties,
                    mock(IbkrConnectionService.class),
                    mock(MarketDataService.class),
                    mock(HistoricalDataService.class),
                    mock(ActionProfileService.class),
                    mock(StrategyEngine.class),
                    mock(SignalService.class),
                    mock(KillSwitchService.class),
                    positionSizingService,
                    takeProfitAllocationService);

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

    private TradingWorkbenchController.StrategySimulationRequest request(
            StrategyTradeMode tradeDirection) {
        TradingWorkbenchController.StrategySimulationRequest request =
                new TradingWorkbenchController.StrategySimulationRequest(
                        "SPY",
                        MarketDataProvider.IBKR,
                        StrategyType.MOVING_AVERAGE_CROSSOVER,
                        tradeDirection,
                        bars().stream().map(HistoricalBar::close).toList(),
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

    private List<HistoricalBar> bars() {
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

    private HistoricalBar bar(
            int index,
            double close,
            double high,
            double low) {
        return new HistoricalBar(
                index,
                Instant.parse("2024-01-01T00:00:00Z").plusSeconds(index * 86_400L),
                String.valueOf(index),
                close,
                high,
                low,
                close,
                1_000L);
    }
}
