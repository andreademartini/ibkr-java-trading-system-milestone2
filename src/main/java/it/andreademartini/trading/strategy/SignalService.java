package it.andreademartini.trading.strategy;

import it.andreademartini.trading.orders.PaperOrderExecutionService;
import it.andreademartini.trading.persistence.TradingAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.List;

@Service
public class SignalService {

    private static final Logger log = LoggerFactory.getLogger(SignalService.class);
    private static final int MAX_RECENT_SIGNALS = 100;

    private final TradingAuditService auditService;
    private final PaperOrderExecutionService orderExecutionService;
    private final ArrayDeque<TradingSignal> recentSignals = new ArrayDeque<>();

    public SignalService(
            TradingAuditService auditService,
            PaperOrderExecutionService orderExecutionService) {
        this.auditService = auditService;
        this.orderExecutionService = orderExecutionService;
    }

    public synchronized void publish(TradingSignal signal, boolean execute) {
        log.info("SEGNALE {} {} qty={} price={} source={} reason={}",
                signal.action(),
                signal.symbol(),
                signal.quantity(),
                signal.price(),
                signal.source(),
                signal.reason());

        auditService.saveSignal(signal);
        recentSignals.addFirst(signal);
        while (recentSignals.size() > MAX_RECENT_SIGNALS) {
            recentSignals.removeLast();
        }

        boolean pendingOrderCancellation = signal.orderDetails() != null
                && signal.orderDetails().cancelPending();
        if (execute && (signal.action() != SignalAction.HOLD
                || pendingOrderCancellation)) {
            orderExecutionService.executeSignal(signal);
        }
    }

    public synchronized List<TradingSignal> recentSignals() {
        return List.copyOf(recentSignals);
    }
}
