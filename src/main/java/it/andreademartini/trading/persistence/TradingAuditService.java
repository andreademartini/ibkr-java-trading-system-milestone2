package it.andreademartini.trading.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.andreademartini.trading.orders.IbkrOrderCommand;
import it.andreademartini.trading.orders.StopLossAdjustment;
import it.andreademartini.trading.strategy.TradingSignal;
import it.andreademartini.trading.strategy.StrategyOrderDetails;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TradingAuditService {

    private final TradingSignalRepository signalRepository;
    private final OrderEventRepository orderEventRepository;
    private final StopLossAdjustmentRepository stopLossAdjustmentRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TradingAuditService(
            TradingSignalRepository signalRepository,
            OrderEventRepository orderEventRepository,
            StopLossAdjustmentRepository stopLossAdjustmentRepository) {
        this.signalRepository = signalRepository;
        this.orderEventRepository = orderEventRepository;
        this.stopLossAdjustmentRepository = stopLossAdjustmentRepository;
    }

    public void saveSignal(TradingSignal signal) {
        StrategyOrderDetails details = signal.orderDetails();
        signalRepository.save(new TradingSignalEntity(
                signal.id().toString(),
                signal.symbol(),
                signal.action().name(),
                signal.price(),
                signal.quantity(),
                signal.source(),
                signal.reason(),
                details == null ? null : details.entryStopPrice(),
                details == null ? null : details.initialStopPrice(),
                details == null ? null : details.rejectGapOverEntry(),
                details == null ? null : details.activeAfterBarIndex(),
                details == null ? null : metadataJson(details),
                signal.occurredAt()
        ));
    }

    private String metadataJson(StrategyOrderDetails details) {
        try {
            return objectMapper.writeValueAsString(details.metadata());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Impossibile serializzare i metadata della strategia", exception);
        }
    }

    public void saveOrderEvent(
            IbkrOrderCommand command,
            String status,
            String message) {
        orderEventRepository.save(new OrderEventEntity(
                command.orderId(),
                command.parentOrderId(),
                command.tradeId(),
                command.role(),
                command.symbol(),
                command.side().name(),
                command.direction() == null ? null : command.direction().name(),
                command.tradeState() == null ? null : command.tradeState().name(),
                command.type().name(),
                command.quantity(),
                status,
                command.referencePrice(),
                command.limitPrice(),
                command.stopPrice(),
                command.trailingPercent(),
                message,
                Instant.now()
        ));
    }

    public void saveStopLossAdjustment(StopLossAdjustment adjustment) {
        if (adjustment == null) {
            return;
        }
        stopLossAdjustmentRepository.save(new StopLossAdjustmentEntity(adjustment));
    }
}
