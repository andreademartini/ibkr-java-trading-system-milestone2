package it.andreademartini.trading.persistence;

import it.andreademartini.trading.strategy.SignalAction;
import it.andreademartini.trading.strategy.StrategyOrderDetails;
import it.andreademartini.trading.strategy.TradingSignal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TradingAuditServiceTest {

    @Test
    void persistsJoeRossOrderLevelsAndPatternMetadata() {
        TradingSignalRepository signals = mock(TradingSignalRepository.class);
        TradingAuditService service = new TradingAuditService(
                signals,
                mock(OrderEventRepository.class),
                mock(StopLossAdjustmentRepository.class));
        TradingSignal signal = new TradingSignal(
                UUID.randomUUID(),
                "AAPL",
                SignalAction.BUY,
                112.01,
                1,
                "JOE_ROSS_123_RH_TTE",
                "TTE candidate bar 1",
                Instant.parse("2026-01-01T00:00:00Z"),
                new StrategyOrderDetails(
                        112.01,
                        106.99,
                        true,
                        false,
                        8,
                        Map.of(
                                "point1Price", "90.0",
                                "rossHookPrice", "114.0")));

        service.saveSignal(signal);

        ArgumentCaptor<TradingSignalEntity> captured =
                ArgumentCaptor.forClass(TradingSignalEntity.class);
        verify(signals).save(captured.capture());
        TradingSignalEntity entity = captured.getValue();
        assertThat(entity.getEntryStopPrice()).isEqualTo(112.01);
        assertThat(entity.getInitialStopPrice()).isEqualTo(106.99);
        assertThat(entity.getRejectGapOverEntry()).isTrue();
        assertThat(entity.getActiveAfterBarIndex()).isEqualTo(8);
        assertThat(entity.getStrategyMetadataJson())
                .contains("\"point1Price\":\"90.0\"")
                .contains("\"rossHookPrice\":\"114.0\"");
    }
}
