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
    private final ProgressiveStopLossService progressiveStopLossService;

    public OrderTracker(
            TradingAuditService auditService,
            IbkrApiClient apiClient,
            ProgressiveStopLossService progressiveStopLossService,
            IbkrEWrapper wrapper) {
        this.auditService = auditService;
        this.apiClient = apiClient;
        this.progressiveStopLossService = progressiveStopLossService;
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
                bigDecimal(command.limitPrice()),
                bigDecimal(command.stopPrice()),
                command.trailingPercent(),
                Instant.now()
        ));
        auditService.saveOrderEvent(command, "SUBMITTED_TO_TWS", command.role());
        if (isStopRole(command.role()) && hasTradeId(command.tradeId())) {
            auditService.saveStopLossAdjustment(StopLossAdjustment.initial(
                    command.tradeId(),
                    bigDecimal(command.stopPrice()),
                    command.quantity()));
        }
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
                previous.limitPrice(),
                previous.stopPrice(),
                previous.trailingPercent(),
                Instant.now()
        );
        orders.put(update.orderId(), updated);
        reconcileTrade(previous, updated);

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
                    previous.limitPrice(),
                    previous.stopPrice(),
                    previous.trailingPercent(),
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
                nullablePrice(order.lmtPrice()),
                nullablePrice(order.auxPrice()),
                nullablePercent(order.trailingPercent()),
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

    private void reconcileTrade(TrackedOrder previous, TrackedOrder updated) {
        if (!hasTradeId(updated.tradeId())) {
            return;
        }

        BigDecimal entryFilled = sumFilled(updated.tradeId(), this::isEntryRole);
        if (entryFilled.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal exitFilled = sumFilled(updated.tradeId(), this::isProtectiveRole);
        BigDecimal remainingPosition = entryFilled.subtract(exitFilled);
        if (remainingPosition.compareTo(BigDecimal.ZERO) <= 0) {
            TakeProfitLevel.fromRole(updated.role())
                    .ifPresent(level -> recordClosedPositionStopCancellation(
                            updated.tradeId(),
                            level,
                            updated.filled().subtract(previous.filled())
                                    .max(BigDecimal.ZERO),
                            updated.isFilled()));
            cancelActiveProtectiveOrders(updated.tradeId());
            markTradeClosed(updated.tradeId());
            return;
        }

        resizeActiveTakeProfits(updated.tradeId(), entryFilled);
        Optional<TakeProfitLevel> takeProfitLevel =
                TakeProfitLevel.fromRole(updated.role());
        if (takeProfitLevel.isPresent()
                && hasNewTakeProfitExecution(previous, updated)) {
            applyProgressiveStopAfterTakeProfit(
                    previous,
                    updated,
                    takeProfitLevel.get(),
                    remainingPosition);
            return;
        }

        resizeActiveStops(updated.tradeId(), remainingPosition);
    }

    private boolean hasNewTakeProfitExecution(
            TrackedOrder previous,
            TrackedOrder updated) {
        return updated.filled().compareTo(previous.filled()) > 0
                || (!previous.isFilled() && updated.isFilled());
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

        singleActiveStopOrder(tradeId)
                .filter(order -> order.quantity() != quantity)
                .ifPresent(order -> resizeOrder(order, quantity));
    }

    private void applyProgressiveStopAfterTakeProfit(
            TrackedOrder previous,
            TrackedOrder updated,
            TakeProfitLevel takeProfitLevel,
            BigDecimal remainingPosition) {
        Optional<TrackedOrder> stopOrder = singleActiveStopOrder(updated.tradeId());
        if (stopOrder.isEmpty()) {
            log.warn("Nessuno stop attivo trovato per trade {} dopo fill {}",
                    updated.tradeId(), updated.role());
            return;
        }

        TrackedOrder stop = stopOrder.get();
        StopAdjustmentResult result =
                progressiveStopLossService.onTakeProfitExecution(
                        tradeStopState(updated.tradeId(), stop, remainingPosition),
                        takeProfitLevel,
                        updated.filled().subtract(previous.filled()).max(BigDecimal.ZERO),
                        updated.isFilled());
        applyStopAdjustment(stop, result);
    }

    private void recordClosedPositionStopCancellation(
            String tradeId,
            TakeProfitLevel takeProfitLevel,
            BigDecimal filledQuantity,
            boolean takeProfitFullyFilled) {
        Optional<TrackedOrder> stopOrder = singleActiveStopOrder(tradeId);
        if (stopOrder.isEmpty()) {
            return;
        }
        StopAdjustmentResult result =
                progressiveStopLossService.onTakeProfitExecution(
                        tradeStopState(tradeId, stopOrder.get(), BigDecimal.ZERO),
                        takeProfitLevel,
                        filledQuantity,
                        takeProfitFullyFilled);
        persistStopAdjustment(result);
    }

    private Optional<TrackedOrder> singleActiveStopOrder(String tradeId) {
        List<TrackedOrder> activeStops = orders.values().stream()
                .filter(order -> tradeId.equals(order.tradeId()))
                .filter(TrackedOrder::isActive)
                .filter(order -> isStopRole(order.role()))
                .sorted(Comparator.comparingInt(TrackedOrder::orderId))
                .toList();
        if (activeStops.isEmpty()) {
            return Optional.empty();
        }
        TrackedOrder primary = activeStops.getFirst();
        activeStops.stream()
                .skip(1)
                .forEach(this::cancelDuplicateStopOrder);
        return Optional.of(primary);
    }

    private void cancelDuplicateStopOrder(TrackedOrder order) {
        try {
            apiClient.cancelOrder(order.orderId());
            orders.put(order.orderId(), order.withStatus("CANCELLED"));
            log.error("Stop duplicato cancellato orderId={} trade={}",
                    order.orderId(), order.tradeId());
        } catch (RuntimeException exception) {
            log.error("Cancellazione stop duplicato fallita orderId={} trade={}",
                    order.orderId(), order.tradeId(), exception);
            throw exception;
        }
    }

    private TradeStopState tradeStopState(
            String tradeId,
            TrackedOrder stopOrder,
            BigDecimal remainingPosition) {
        Optional<TrackedOrder> entryOrder = orders.values().stream()
                .filter(order -> tradeId.equals(order.tradeId()))
                .filter(order -> isEntryRole(order.role()))
                .findFirst();
        BigDecimal entryPrice = entryPrice(entryOrder.orElse(null));
        BigDecimal currentStopPrice = stopPrice(stopOrder);
        long initialQuantity = entryOrder
                .map(TrackedOrder::quantity)
                .orElse(Math.max(stopOrder.quantity(), remainingPosition.longValue()));
        BigDecimal initialStopPrice = initialStopPrice(tradeId, currentStopPrice);

        return new TradeStopState(
                tradeId,
                stopOrder.direction(),
                entryPrice,
                initialStopPrice,
                currentStopPrice,
                initialQuantity,
                remainingPosition.max(BigDecimal.ZERO).longValue(),
                stopOrder.quantity(),
                takeProfitAllocations(tradeId));
    }

    private BigDecimal entryPrice(TrackedOrder entryOrder) {
        if (entryOrder == null) {
            return BigDecimal.ZERO;
        }
        if (entryOrder.averageFillPrice() > 0) {
            return BigDecimal.valueOf(entryOrder.averageFillPrice());
        }
        IbkrOrderCommand command = commands.get(entryOrder.orderId());
        if (command == null) {
            return entryOrder.stopPrice() == null
                    ? BigDecimal.ZERO
                    : entryOrder.stopPrice();
        }
        if (command.referencePrice() != null) {
            return BigDecimal.valueOf(command.referencePrice());
        }
        if (command.stopPrice() != null) {
            return BigDecimal.valueOf(command.stopPrice());
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal initialStopPrice(
            String tradeId,
            BigDecimal fallbackStopPrice) {
        return orders.values().stream()
                .filter(order -> tradeId.equals(order.tradeId()))
                .filter(order -> "STOP_LOSS".equals(order.role()))
                .findFirst()
                .map(order -> commands.get(order.orderId()))
                .map(IbkrOrderCommand::stopPrice)
                .map(BigDecimal::valueOf)
                .orElse(fallbackStopPrice);
    }

    private BigDecimal stopPrice(TrackedOrder stopOrder) {
        if (stopOrder.stopPrice() != null) {
            return stopOrder.stopPrice();
        }
        IbkrOrderCommand command = commands.get(stopOrder.orderId());
        if (command != null && command.stopPrice() != null) {
            return BigDecimal.valueOf(command.stopPrice());
        }
        return BigDecimal.ZERO;
    }

    private List<TakeProfitAllocation> takeProfitAllocations(String tradeId) {
        return orders.values().stream()
                .filter(order -> tradeId.equals(order.tradeId()))
                .filter(order -> TakeProfitLevel.fromRole(order.role()).isPresent())
                .sorted(Comparator.comparing(TrackedOrder::role))
                .map(order -> new TakeProfitAllocation(
                        TakeProfitLevel.fromRole(order.role()).orElseThrow().number(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        order.quantity(),
                        limitPrice(order)))
                .toList();
    }

    private BigDecimal limitPrice(TrackedOrder order) {
        if (order.limitPrice() != null) {
            return order.limitPrice();
        }
        IbkrOrderCommand command = commands.get(order.orderId());
        if (command != null && command.limitPrice() != null) {
            return BigDecimal.valueOf(command.limitPrice());
        }
        return BigDecimal.ZERO;
    }

    private void applyStopAdjustment(
            TrackedOrder stopOrder,
            StopAdjustmentResult result) {
        persistStopAdjustment(result);
        if (!result.requiresStopOrderModification()) {
            return;
        }

        modifyStopOrder(stopOrder, result.newQuantity(), result.newStopPrice());
    }

    private void modifyStopOrder(
            TrackedOrder trackedOrder,
            long quantity,
            BigDecimal stopPrice) {
        IbkrOrderCommand command = commands.get(trackedOrder.orderId());
        if (command == null) {
            return;
        }

        command.order().totalQuantity(Decimal.get(quantity));
        if (stopPrice != null && isPlainStopOrder(trackedOrder)) {
            command.order().auxPrice(stopPrice.doubleValue());
        }
        apiClient.placeOrder(command.orderId(), command.contract(), command.order());

        Double commandStopPrice = stopPrice == null ? null : stopPrice.doubleValue();
        commands.put(
                trackedOrder.orderId(),
                command.withQuantityAndStopPrice(quantity, commandStopPrice));
        orders.put(
                trackedOrder.orderId(),
                trackedOrder.withStopPriceAndQuantity(
                        stopPrice,
                        quantity,
                        BigDecimal.valueOf(quantity)
                                .subtract(trackedOrder.filled())
                                .max(BigDecimal.ZERO)));
        auditService.saveOrderEvent(
                commands.get(trackedOrder.orderId()),
                "STOP_ADJUSTED",
                "qty=" + quantity + ", stopPrice=" + stopPrice);
    }

    private void persistStopAdjustment(StopAdjustmentResult result) {
        if (result != null && result.hasPersistableAdjustment()) {
            auditService.saveStopLossAdjustment(result.adjustment());
        }
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
                    orders.put(takeProfit.orderId(), takeProfit.withStatus("CANCELLED"));
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
        orders.put(
                trackedOrder.orderId(),
                trackedOrder.withQuantity(
                        quantity,
                        BigDecimal.valueOf(quantity)
                                .subtract(trackedOrder.filled())
                                .max(BigDecimal.ZERO)));
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
                    orders.put(order.orderId(), order.withStatus("CANCELLED"));
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
                        order.limitPrice(),
                        order.stopPrice(),
                        order.trailingPercent(),
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

    private boolean isPlainStopOrder(TrackedOrder trackedOrder) {
        return "STOP_LOSS".equals(trackedOrder.role())
                && trackedOrder.type() == PaperOrderType.STOP;
    }

    private boolean hasTradeId(String tradeId) {
        return tradeId != null && !tradeId.isBlank();
    }

    private BigDecimal bigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private BigDecimal nullablePrice(double value) {
        return Double.isFinite(value) && value > 0 && value < Double.MAX_VALUE / 2
                ? BigDecimal.valueOf(value)
                : null;
    }

    private Double nullablePercent(double value) {
        return Double.isFinite(value) && value > 0 && value < Double.MAX_VALUE / 2
                ? value
                : null;
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
