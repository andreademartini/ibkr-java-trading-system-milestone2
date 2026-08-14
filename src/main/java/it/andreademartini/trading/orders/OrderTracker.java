package it.andreademartini.trading.orders;

import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.Types;
import it.andreademartini.trading.ibkr.IbkrApiClient;
import it.andreademartini.trading.ibkr.IbkrEWrapper;
import it.andreademartini.trading.ibkr.IbkrError;
import it.andreademartini.trading.ibkr.IbkrEventListener;
import it.andreademartini.trading.ibkr.IbkrNumber;
import it.andreademartini.trading.ibkr.IbkrOrderStatusUpdate;
import it.andreademartini.trading.persistence.TradingAuditService;
import it.andreademartini.trading.strategy.TradeDirection;
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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderTracker implements IbkrEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderTracker.class);

    private final Map<Integer, TrackedOrder> orders = new ConcurrentHashMap<>();
    private final Map<Integer, IbkrOrderCommand> commands = new ConcurrentHashMap<>();
    private final TradingAuditService auditService;
    private final IbkrApiClient apiClient;

    public OrderTracker(
            TradingAuditService auditService,
            IbkrApiClient apiClient,
            IbkrEWrapper wrapper) {
        this.auditService = auditService;
        this.apiClient = apiClient;
        wrapper.addListener(this);
    }

    public void registerSubmitted(IbkrOrderCommand command) {
        commands.put(command.orderId(), command);
        orders.put(command.orderId(), new TrackedOrder(
                command.orderId(),
                command.parentOrderId(),
                command.tradeId(),
                command.role(),
                command.symbol(),
                command.side(),
                command.type(),
                command.direction(),
                command.tradeState(),
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
                previous.tradeId(),
                previous.role(),
                previous.symbol(),
                previous.side(),
                previous.type(),
                previous.direction(),
                tradeStateForUpdate(previous, update),
                previous.quantity(),
                update.status(),
                update.filled(),
                update.remaining(),
                update.averageFillPrice(),
                Instant.now()
        );
        orders.put(update.orderId(), updated);
        reconcileTrade(updated);

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
                    previous.tradeId(),
                    previous.role(),
                    previous.symbol(),
                    previous.side(),
                    previous.type(),
                    previous.direction(),
                    previous.tradeState(),
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
                null,
                "DISCOVERED",
                symbol,
                side,
                type,
                side == OrderSide.SELL ? TradeDirection.SHORT : TradeDirection.LONG,
                null,
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

    public List<Integer> activeProtectiveOrderIdsForSymbol(String symbol) {
        String normalized = normalize(symbol);
        return orders.values().stream()
                .filter(TrackedOrder::isActive)
                .filter(order -> normalize(order.symbol()).equals(normalized))
                .filter(order -> isProtectiveRole(order.role()))
                .map(TrackedOrder::orderId)
                .toList();
    }

    private void reconcileTrade(TrackedOrder updated) {
        if (updated.tradeId() == null || updated.tradeId().isBlank()) {
            return;
        }

        BigDecimal entryFilled = sumFilled(updated.tradeId(), this::isEntryRole);
        if (entryFilled.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal exitFilled = sumFilled(updated.tradeId(), this::isProtectiveRole);
        BigDecimal remainingPosition = entryFilled.subtract(exitFilled);
        if (remainingPosition.compareTo(BigDecimal.ZERO) <= 0) {
            cancelActiveProtectiveOrders(updated.tradeId());
            markTradeClosed(updated.tradeId());
            return;
        }

        resizeActiveTakeProfits(updated.tradeId(), entryFilled);
        resizeActiveStops(updated.tradeId(), remainingPosition);
    }

    private BigDecimal sumFilled(
            String tradeId,
            java.util.function.Predicate<String> roleFilter) {
        return orders.values().stream()
                .filter(order -> tradeId.equals(order.tradeId()))
                .filter(order -> roleFilter.test(order.role()))
                .map(TrackedOrder::filled)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void resizeActiveStops(
            String tradeId,
            BigDecimal remainingPosition) {
        long quantity = remainingPosition.max(BigDecimal.ZERO).longValue();
        if (quantity <= 0) {
            cancelActiveProtectiveOrders(tradeId);
            return;
        }

        orders.values().stream()
                .filter(order -> tradeId.equals(order.tradeId()))
                .filter(TrackedOrder::isActive)
                .filter(order -> isStopRole(order.role()))
                .filter(order -> order.quantity() != quantity)
                .forEach(order -> resizeOrder(order, quantity));
    }

    private void resizeActiveTakeProfits(
            String tradeId,
            BigDecimal entryFilled) {
        Optional<TrackedOrder> entryOrder = orders.values().stream()
                .filter(order -> tradeId.equals(order.tradeId()))
                .filter(order -> isEntryRole(order.role()))
                .findFirst();
        if (entryOrder.isEmpty() || entryOrder.get().quantity() <= 0) {
            return;
        }

        long filledEntryQuantity = entryFilled.max(BigDecimal.ZERO).longValue();
        if (filledEntryQuantity <= 0) {
            return;
        }

        List<TrackedOrder> takeProfits = orders.values().stream()
                .filter(order -> tradeId.equals(order.tradeId()))
                .filter(order -> order.role() != null
                        && order.role().startsWith("TAKE_PROFIT"))
                .sorted(Comparator.comparing(TrackedOrder::role))
                .toList();
        if (takeProfits.isEmpty()) {
            return;
        }

        long originalEntryQuantity = entryOrder.get().quantity();
        long allocated = 0;
        for (int index = 0; index < takeProfits.size(); index++) {
            TrackedOrder takeProfit = takeProfits.get(index);
            IbkrOrderCommand command = commands.get(takeProfit.orderId());
            long originalTargetQuantity = command == null
                    ? takeProfit.quantity()
                    : command.quantity();
            boolean last = index == takeProfits.size() - 1;
            long desiredTotal = last
                    ? Math.max(0, filledEntryQuantity - allocated)
                    : Math.floorDiv(
                            originalTargetQuantity * filledEntryQuantity,
                            originalEntryQuantity);
            long alreadyFilled = takeProfit.filled().max(BigDecimal.ZERO).longValue();
            desiredTotal = Math.max(desiredTotal, alreadyFilled);
            allocated += desiredTotal;

            if (takeProfit.isActive() && takeProfit.quantity() != desiredTotal) {
                if (desiredTotal <= 0) {
                    apiClient.cancelOrder(takeProfit.orderId());
                    continue;
                }
                resizeOrder(takeProfit, desiredTotal);
            }
        }
    }

    private void resizeOrder(TrackedOrder trackedOrder, long quantity) {
        IbkrOrderCommand command = commands.get(trackedOrder.orderId());
        if (command == null) {
            return;
        }

        command.order().totalQuantity(Decimal.get(quantity));
        apiClient.placeOrder(command.orderId(), command.contract(), command.order());
        orders.put(trackedOrder.orderId(), new TrackedOrder(
                trackedOrder.orderId(),
                trackedOrder.parentOrderId(),
                trackedOrder.tradeId(),
                trackedOrder.role(),
                trackedOrder.symbol(),
                trackedOrder.side(),
                trackedOrder.type(),
                trackedOrder.direction(),
                trackedOrder.tradeState(),
                quantity,
                trackedOrder.status(),
                trackedOrder.filled(),
                BigDecimal.valueOf(quantity)
                        .subtract(trackedOrder.filled())
                        .max(BigDecimal.ZERO),
                trackedOrder.averageFillPrice(),
                Instant.now()));
        log.warn("Ordine protettivo {} ridimensionato a qty={} per trade {}",
                trackedOrder.orderId(), quantity, trackedOrder.tradeId());
    }

    private void cancelActiveProtectiveOrders(String tradeId) {
        orders.values().stream()
                .filter(order -> tradeId.equals(order.tradeId()))
                .filter(TrackedOrder::isActive)
                .filter(order -> isProtectiveRole(order.role()))
                .forEach(order -> {
                    apiClient.cancelOrder(order.orderId());
                    log.warn("Cancellazione protezione residua orderId={} trade={}",
                            order.orderId(), tradeId);
                });
    }

    private void markTradeClosed(String tradeId) {
        orders.values().stream()
                .filter(order -> tradeId.equals(order.tradeId()))
                .forEach(order -> orders.put(order.orderId(), new TrackedOrder(
                        order.orderId(),
                        order.parentOrderId(),
                        order.tradeId(),
                        order.role(),
                        order.symbol(),
                        order.side(),
                        order.type(),
                        order.direction(),
                        TradeState.CLOSED,
                        order.quantity(),
                        order.status(),
                        order.filled(),
                        order.remaining(),
                        order.averageFillPrice(),
                        Instant.now())));
    }

    private TradeState tradeStateForUpdate(
            TrackedOrder previous,
            IbkrOrderStatusUpdate update) {
        if (update.filled().compareTo(BigDecimal.ZERO) <= 0) {
            return previous.tradeState();
        }
        if (isEntryRole(previous.role())) {
            return previous.direction() == TradeDirection.SHORT
                    ? TradeState.OPEN_SHORT
                    : TradeState.OPEN_LONG;
        }
        if (isProtectiveRole(previous.role())) {
            return previous.direction() == TradeDirection.SHORT
                    ? TradeState.CLOSING_SHORT
                    : TradeState.CLOSING_LONG;
        }
        return previous.tradeState();
    }

    private boolean isEntryRole(String role) {
        return "ENTRY".equals(role);
    }

    private boolean isProtectiveRole(String role) {
        return role != null
                && (role.startsWith("TAKE_PROFIT")
                || role.equals("STOP_LOSS")
                || role.equals("TRAILING_STOP"));
    }

    private boolean isStopRole(String role) {
        return "STOP_LOSS".equals(role)
                || "TRAILING_STOP".equals(role);
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
