package it.andreademartini.trading.persistence;

import it.andreademartini.trading.orders.IbkrOrderCommand;
import it.andreademartini.trading.strategy.TradingSignal;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TradingAuditService {

    private final TradingSignalRepository signalRepository;
    private final OrderEventRepository orderEventRepository;

    public TradingAuditService(
            TradingSignalRepository signalRepository,
            OrderEventRepository orderEventRepository) {
        this.signalRepository = signalRepository;
        this.orderEventRepository = orderEventRepository;
    }

    public void saveSignal(TradingSignal signal) {
        signalRepository.save(new TradingSignalEntity(
                signal.id().toString(),
                signal.symbol(),
                signal.action().name(),
                signal.price(),
                signal.quantity(),
                signal.source(),
                signal.reason(),
                signal.occurredAt()
        ));
    }

    public void saveOrderEvent(
            IbkrOrderCommand command,
            String status,
            String message) {
        orderEventRepository.save(new OrderEventEntity(
                command.orderId(),
                command.parentOrderId(),
                command.symbol(),
                command.side().name(),
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
}
