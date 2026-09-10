package it.andreademartini.trading.strategy;

import it.andreademartini.trading.orders.PaperOrderExecutionService;
import it.andreademartini.trading.persistence.TradingAuditService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SignalServiceTest {

    @Test
    void forwardsHoldSignalWhenItCancelsAPendingStrategyOrder() {
        TradingAuditService audit = mock(TradingAuditService.class);
        PaperOrderExecutionService execution = mock(PaperOrderExecutionService.class);
        SignalService service = new SignalService(audit, execution);
        TradingSignal cancellation = signal(StrategyOrderDetails.cancel(12, "expired"));

        service.publish(cancellation, true);

        verify(execution).executeSignal(cancellation);
    }

    @Test
    void doesNotExecuteAnOrdinaryHoldSignal() {
        PaperOrderExecutionService execution = mock(PaperOrderExecutionService.class);
        SignalService service = new SignalService(
                mock(TradingAuditService.class), execution);
        TradingSignal hold = signal(new StrategyOrderDetails(
                null, null, false, false, 12, Map.of()));

        service.publish(hold, true);

        verify(execution, never()).executeSignal(hold);
    }

    private TradingSignal signal(StrategyOrderDetails details) {
        return new TradingSignal(
                UUID.randomUUID(),
                "AAPL",
                SignalAction.HOLD,
                100,
                0,
                "JOE_ROSS_123_RH_TTE",
                "test",
                Instant.parse("2026-01-01T00:00:00Z"),
                details);
    }
}
