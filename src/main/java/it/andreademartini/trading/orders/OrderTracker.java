package it.andreademartini.trading.orders;

import com.ib.client.Contract;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.Types;
import it.andreademartini.trading.ibkr.IbkrEWrapper;
import it.andreademartini.trading.ibkr.IbkrError;
import it.andreademartini.trading.ibkr.IbkrEventListener;
import it.andreademartini.trading.ibkr.IbkrNumber;
import it.andreademartini.trading.ibkr.IbkrOrderStatusUpdate;
import it.andreademartini.trading.persistence.TradingAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderTracker implements IbkrEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderTracker.class);

    private final Map<Integer, TrackedOrder> orders = new ConcurrentHashMap<>();
    private final Map<Integer, IbkrOrderCommand> commands = new ConcurrentHashMap<>();
    private final TradingAuditService auditService;

    public OrderTracker(
            TradingAuditService auditService,
            IbkrEWrapper wrapper) {
        this.auditService = auditService;
        wrapper.addListener(this);
    }

    public void registerSubmitted(IbkrOrderCommand command) {
        commands.put(command.orderId(), command);
        orders.put(command.orderId(), new TrackedOrder(
                command.orderId(),
                command.parentOrderId(),
                command.role(),
                command.symbol(),
                command.side(),
                command.type(),
                command.quantity(),
                "SUBMITTED_TO_TWS",
                BigDecimal.ZERO,
                BigDecimal.valueOf(command.quantity()),
                0.0,
                Instant.now()
        ));
        auditService.saveOrderEvent(command, "SUBMITTED_TO_TWS", command.role());
    }

    @Override
    public void onOrderStatus(IbkrOrderStatusUpdate update) {
        TrackedOrder previous = orders.get(update.orderId());
        if (previous == null) {
            log.debug("orderStatus per ordine non ancora tracciato: {}", update.orderId());
            return;
        }

        TrackedOrder updated = new TrackedOrder(
                previous.orderId(),
                previous.parentOrderId(),
                previous.role(),
                previous.symbol(),
                previous.side(),
                previous.type(),
                previous.quantity(),
                update.status(),
                update.filled(),
                update.remaining(),
                update.averageFillPrice(),
                Instant.now()
        );
        orders.put(update.orderId(), updated);

        IbkrOrderCommand command = commands.get(update.orderId());
        if (command != null) {
            auditService.saveOrderEvent(
                    command,
                    update.status(),
                    "filled=" + update.filled()
                            + ", remaining=" + update.remaining()
                            + ", avgFillPrice=" + update.averageFillPrice());
        }

        log.info("Ordine {} {} status={} filled={} remaining={} avgFill={}",
                update.orderId(),
                previous.symbol(),
                update.status(),
                update.filled(),
                update.remaining(),
                update.averageFillPrice());
    }

    @Override
    public void onError(IbkrError error) {
        IbkrOrderCommand command = commands.get(error.requestId());
        if (command == null) {
            return;
        }

        TrackedOrder previous = orders.get(error.requestId());
        if (previous != null) {
            orders.put(error.requestId(), new TrackedOrder(
                    previous.orderId(),
                    previous.parentOrderId(),
                    previous.role(),
                    previous.symbol(),
                    previous.side(),
                    previous.type(),
                    previous.quantity(),
                    "ERROR_" + error.code(),
                    previous.filled(),
                    previous.remaining(),
                    previous.averageFillPrice(),
                    Instant.now()
            ));
        }

        auditService.saveOrderEvent(
                command,
                "ERROR_" + error.code(),
                error.message()
                        + (error.advancedRejectJson() == null
                        || error.advancedRejectJson().isBlank()
                        ? ""
                        : " advancedReject=" + error.advancedRejectJson()));
    }

    @Override
    public void onOpenOrder(
            int orderId,
            Contract contract,
            Order order,
            OrderState orderState) {
        if (orders.containsKey(orderId)) {
            return;
        }

        String symbol = normalize(contract.symbol());
        OrderSide side = order.action() == Types.Action.SELL
                ? OrderSide.SELL
                : OrderSide.BUY;
        long quantity = IbkrNumber.toBigDecimal(order.totalQuantity()).longValue();
        String rawType = order.orderType() == null
                ? ""
                : order.orderType().getApiString();
        PaperOrderType type = mapType(rawType);
        String status = orderState.status() == null
                ? "OPEN_ORDER"
                : orderState.status().toString();

        orders.put(orderId, new TrackedOrder(
                orderId,
                order.parentId() == 0 ? null : order.parentId(),
                "DISCOVERED",
                symbol,
                side,
                type,
                quantity,
                status,
                BigDecimal.ZERO,
                BigDecimal.valueOf(quantity),
                0.0,
                Instant.now()
        ));
    }

    public Collection<TrackedOrder> orders() {
        return orders.values().stream()
                .sorted(Comparator.comparingInt(TrackedOrder::orderId).reversed())
                .toList();
    }

    public int activeOrderCount() {
        return (int) orders.values().stream()
                .filter(TrackedOrder::isActive)
                .count();
    }

    public List<Integer> activeOrderIdsForSymbol(String symbol) {
        String normalized = normalize(symbol);
        return orders.values().stream()
                .filter(TrackedOrder::isActive)
                .filter(order -> normalize(order.symbol()).equals(normalized))
                .map(TrackedOrder::orderId)
                .toList();
    }

    private PaperOrderType mapType(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MKT" -> PaperOrderType.MARKET;
            case "LMT" -> PaperOrderType.LIMIT;
            case "STP", "STP LMT" -> PaperOrderType.STOP;
            case "TRAIL", "TRAIL LIMIT" -> PaperOrderType.TRAILING_STOP;
            default -> PaperOrderType.MARKET;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
