package it.andreademartini.trading.orders;

import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.ibkr.IbkrApiClient;
import it.andreademartini.trading.marketdata.MarketDataService;
import it.andreademartini.trading.portfolio.PortfolioService;
import it.andreademartini.trading.risk.KillSwitchService;
import it.andreademartini.trading.risk.RiskManager;
import it.andreademartini.trading.risk.TradeSetupService;
import it.andreademartini.trading.strategy.SignalAction;
import it.andreademartini.trading.strategy.StrategyOrderDetails;
import it.andreademartini.trading.strategy.TradeDirection;
import it.andreademartini.trading.strategy.TradingSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperOrderExecutionServiceTteTest {

    private final TradingSystemProperties properties = new TradingSystemProperties();
    private final MarketDataService marketData = mock(MarketDataService.class);
    private final PortfolioService portfolio = mock(PortfolioService.class);
    private final IbkrOrderFactory orderFactory = mock(IbkrOrderFactory.class);
    private final IbkrApiClient apiClient = mock(IbkrApiClient.class);
    private final OrderTracker orderTracker = mock(OrderTracker.class);
    private final RiskManager riskManager = mock(RiskManager.class);
    private final KillSwitchService killSwitch = mock(KillSwitchService.class);
    private final TradeSetupService tradeSetup = mock(TradeSetupService.class);
    private PaperOrderExecutionService service;

    @BeforeEach
    void setUp() {
        when(portfolio.positionQuantity("AAPL")).thenReturn(BigDecimal.ZERO);
        when(marketData.requireInstrument("AAPL"))
                .thenReturn(new TradingSystemProperties.InstrumentProperties());
        when(orderFactory.create(any(), any(), anyDouble())).thenReturn(List.of());
        service = new PaperOrderExecutionService(
                properties,
                marketData,
                portfolio,
                orderFactory,
                apiClient,
                orderTracker,
                riskManager,
                killSwitch,
                tradeSetup);
    }

    @Test
    void replacesThePreviousUnfilledTteBracketBeforeSubmittingBar2() {
        TradePlan first = plan("TTE-1", 112.01, 106.99);
        TradePlan second = plan("TTE-2", 111.01, 105.99);
        when(tradeSetup.create(any(), any(), any(), any()))
                .thenReturn(first, second);
        when(orderTracker.hasFilledEntryForTrade("TTE-1")).thenReturn(false);
        when(orderTracker.activeOrderIdsForTrade("TTE-1"))
                .thenReturn(List.of(101, 102, 103));

        service.executeSignal(candidate(112.01, 106.99, 1));
        service.executeSignal(candidate(111.01, 105.99, 2));

        verify(apiClient).cancelOrder(101);
        verify(apiClient).cancelOrder(102);
        verify(apiClient).cancelOrder(103);
    }

    @Test
    void neverCancelsProtectiveChildrenAfterTheEntryHasFilled() {
        TradePlan first = plan("TTE-1", 112.01, 106.99);
        when(tradeSetup.create(any(), any(), any(), any())).thenReturn(first);
        service.executeSignal(candidate(112.01, 106.99, 1));
        when(orderTracker.hasFilledEntryForTrade("TTE-1")).thenReturn(true);

        service.executeSignal(new TradingSignal(
                UUID.randomUUID(), "AAPL", SignalAction.HOLD, 100, 0,
                "JOE_ROSS_123_RH_TTE", "expired", Instant.now(),
                StrategyOrderDetails.cancel(12, "expired")));

        verify(orderTracker, never()).activeOrderIdsForTrade("TTE-1");
        verify(apiClient, never()).cancelOrder(101);
    }

    private TradePlan plan(String tradeId, double entry, double stop) {
        TradePlan plan = mock(TradePlan.class);
        when(plan.tradeId()).thenReturn(tradeId);
        when(plan.direction()).thenReturn(TradeDirection.LONG);
        when(plan.entryPrice()).thenReturn(BigDecimal.valueOf(entry));
        when(plan.stopLossPrice()).thenReturn(BigDecimal.valueOf(stop));
        when(plan.takeProfits()).thenReturn(List.of());
        when(plan.quantity()).thenReturn(10L);
        return plan;
    }

    private TradingSignal candidate(double entry, double stop, int barNumber) {
        return new TradingSignal(
                UUID.randomUUID(),
                "AAPL",
                SignalAction.BUY,
                entry,
                1,
                "JOE_ROSS_123_RH_TTE",
                "TTE candidate bar " + barNumber,
                Instant.now(),
                new StrategyOrderDetails(
                        entry,
                        stop,
                        true,
                        false,
                        barNumber,
                        Map.of("strategy", "JOE_ROSS_123_RH_TTE")));
    }
}
