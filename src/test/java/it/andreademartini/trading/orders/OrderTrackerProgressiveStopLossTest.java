package it.andreademartini.trading.orders;

import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.Order;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.ibkr.IbkrApiClient;
import it.andreademartini.trading.ibkr.IbkrEWrapper;
import it.andreademartini.trading.ibkr.IbkrOrderStatusUpdate;
import it.andreademartini.trading.persistence.TradingAuditService;
import it.andreademartini.trading.strategy.TradeDirection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderTrackerProgressiveStopLossTest {

    private final TradingSystemProperties properties = new TradingSystemProperties();
    private final IbkrApiClient apiClient = mock(IbkrApiClient.class);
    private final TradingAuditService auditService = mock(TradingAuditService.class);
    private final OrderTracker tracker = new OrderTracker(
            auditService,
            apiClient,
            new ProgressiveStopLossService(properties),
            new IbkrEWrapper());

    @Test
    void paperPartialThenFullTakeProfitUpdatesStopQuantityThenMovesStop() {
        registerLongBracket(List.of(
                command(1, null, "ENTRY", OrderSide.BUY, PaperOrderType.STOP, 100, null, 100.0),
                command(2, 1, "TAKE_PROFIT_1", OrderSide.SELL, PaperOrderType.LIMIT, 50, 102.0, null),
                command(5, 1, "STOP_LOSS", OrderSide.SELL, PaperOrderType.STOP, 100, null, 95.0)));

        tracker.onOrderStatus(status(1, "Filled", 100, 0, 100.0));
        tracker.onOrderStatus(status(2, "Submitted", 30, 20, 102.0));

        TrackedOrder stopAfterPartial = order(5);
        assertThat(stopAfterPartial.quantity()).isEqualTo(70);
        assertThat(stopAfterPartial.stopPrice()).isEqualByComparingTo("95");

        tracker.onOrderStatus(status(2, "Filled", 50, 0, 102.0));

        TrackedOrder stopAfterFull = order(5);
        assertThat(stopAfterFull.quantity()).isEqualTo(50);
        assertThat(stopAfterFull.stopPrice()).isEqualByComparingTo("100");
        assertThat(activeStops()).hasSize(1);
    }

    @Test
    void paperTradeClosedByFinalTakeProfitLeavesNoActiveExitOrders() {
        registerLongBracket(List.of(
                command(1, null, "ENTRY", OrderSide.BUY, PaperOrderType.STOP, 100, null, 100.0),
                command(2, 1, "TAKE_PROFIT_1", OrderSide.SELL, PaperOrderType.LIMIT, 50, 102.0, null),
                command(3, 1, "TAKE_PROFIT_2", OrderSide.SELL, PaperOrderType.LIMIT, 30, 104.0, null),
                command(4, 1, "TAKE_PROFIT_3", OrderSide.SELL, PaperOrderType.LIMIT, 20, 108.0, null),
                command(5, 1, "STOP_LOSS", OrderSide.SELL, PaperOrderType.STOP, 100, null, 95.0)));

        tracker.onOrderStatus(status(1, "Filled", 100, 0, 100.0));
        tracker.onOrderStatus(status(2, "Filled", 50, 0, 102.0));
        tracker.onOrderStatus(status(3, "Filled", 30, 0, 104.0));
        tracker.onOrderStatus(status(4, "Filled", 20, 0, 108.0));

        assertThat(tracker.activeProtectiveOrderIdsForSymbol("SPY")).isEmpty();
        assertThat(tracker.orders())
                .allMatch(order -> !"STOP_LOSS".equals(order.role())
                        || !order.isActive());
        verify(apiClient).cancelOrder(5);
    }

    @Test
    void duplicateActiveStopIsCancelledBeforeProgressiveAdjustment() {
        registerLongBracket(List.of(
                command(1, null, "ENTRY", OrderSide.BUY, PaperOrderType.STOP, 100, null, 100.0),
                command(2, 1, "TAKE_PROFIT_1", OrderSide.SELL, PaperOrderType.LIMIT, 50, 102.0, null),
                command(5, 1, "STOP_LOSS", OrderSide.SELL, PaperOrderType.STOP, 100, null, 95.0),
                command(6, 1, "STOP_LOSS", OrderSide.SELL, PaperOrderType.STOP, 100, null, 94.0)));

        tracker.onOrderStatus(status(1, "Filled", 100, 0, 100.0));
        tracker.onOrderStatus(status(2, "Filled", 50, 0, 102.0));

        assertThat(activeStops()).hasSize(1);
        assertThat(order(5).stopPrice()).isEqualByComparingTo("100");
        assertThat(order(6).isActive()).isFalse();
        verify(apiClient).cancelOrder(6);
    }

    private void registerLongBracket(List<IbkrOrderCommand> commands) {
        commands.forEach(tracker::registerSubmitted);
    }

    private IbkrOrderCommand command(
            int orderId,
            Integer parentOrderId,
            String role,
            OrderSide side,
            PaperOrderType type,
            long quantity,
            Double limitPrice,
            Double stopPrice) {
        Order order = new Order();
        order.orderId(orderId);
        order.action(side.name());
        order.totalQuantity(Decimal.get(quantity));
        if (limitPrice != null) {
            order.orderType("LMT");
            order.lmtPrice(limitPrice);
        } else {
            order.orderType("STP");
            order.auxPrice(stopPrice);
        }

        Contract contract = new Contract();
        contract.symbol("SPY");

        return new IbkrOrderCommand(
                orderId,
                parentOrderId,
                "T001",
                role,
                "SPY",
                side,
                type,
                TradeDirection.LONG,
                "ENTRY".equals(role) ? TradeState.OPENING_LONG : TradeState.CLOSING_LONG,
                quantity,
                "ENTRY".equals(role) ? stopPrice : 100.0,
                limitPrice,
                stopPrice,
                null,
                contract,
                order);
    }

    private IbkrOrderStatusUpdate status(
            int orderId,
            String status,
            long filled,
            long remaining,
            double averageFillPrice) {
        return new IbkrOrderStatusUpdate(
                orderId,
                status,
                BigDecimal.valueOf(filled),
                BigDecimal.valueOf(remaining),
                averageFillPrice,
                0,
                0,
                averageFillPrice,
                0,
                "",
                0.0);
    }

    private TrackedOrder order(int orderId) {
        return tracker.orders().stream()
                .filter(order -> order.orderId() == orderId)
                .findFirst()
                .orElseThrow();
    }

    private Collection<TrackedOrder> activeStops() {
        return tracker.orders().stream()
                .filter(order -> "STOP_LOSS".equals(order.role()))
                .filter(TrackedOrder::isActive)
                .toList();
    }
}
