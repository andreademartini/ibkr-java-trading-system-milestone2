package it.andreademartini.trading.web;

import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.config.StrategyType;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.ibkr.IbkrConnectionService;
import it.andreademartini.trading.marketdata.HistoricalDataService;
import it.andreademartini.trading.marketdata.MarketDataService;
import it.andreademartini.trading.risk.KillSwitchService;
import it.andreademartini.trading.strategy.SignalService;
import it.andreademartini.trading.strategy.StrategyEngine;
import it.andreademartini.trading.strategy.StrategyTradeMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TradingWorkbenchControllerSimulationTest {

    private final TradingSystemProperties properties = new TradingSystemProperties();
    private final TradingWorkbenchController controller =
            new TradingWorkbenchController(
                    properties,
                    mock(IbkrConnectionService.class),
                    mock(MarketDataService.class),
                    mock(HistoricalDataService.class),
                    mock(ActionProfileService.class),
                    mock(StrategyEngine.class),
                    mock(SignalService.class),
                    mock(KillSwitchService.class));

    @Test
    void simulationReversesBetweenLongAndShortPositions() {
        TradingWorkbenchController.StrategySimulationResponse response =
                controller.simulateStrategy(request(StrategyTradeMode.LONG_SHORT));

        assertThat(response.trades())
                .extracting(TradingWorkbenchController.TradeResult::side)
                .containsExactly("LONG", "SHORT");
        assertThat(response.metrics().openPositionQuantity()).isEqualTo(1);
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
        assertThat(response.metrics().openPositionQuantity()).isEqualTo(1);
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
                        List.of(12.0, 11.0, 10.0, 11.0, 12.0,
                                11.0, 10.0, 9.0, 10.0, 11.0),
                        2,
                        3,
                        1L,
                        10_000.0,
                        0.0,
                        0.0,
                        false);
        return request;
    }
}
