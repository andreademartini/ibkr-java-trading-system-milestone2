package it.andreademartini.trading.ibkr;

import com.ib.client.Contract;
import com.ib.client.Order;
import com.ib.client.OrderState;

import java.util.List;

public interface IbkrEventListener {

    default void onNextValidId(int orderId) {
    }

    default void onManagedAccounts(List<String> accountIds) {
    }

    default void onTickPrice(int requestId, int field, double price) {
    }

    default void onTickSize(int requestId, int field, Object size) {
    }

    default void onOrderStatus(IbkrOrderStatusUpdate update) {
    }

    default void onOpenOrder(
            int orderId,
            Contract contract,
            Order order,
            OrderState orderState) {
    }

    default void onPosition(
            String account,
            Contract contract,
            Object position,
            double averageCost) {
    }

    default void onPositionEnd() {
    }

    default void onAccountSummary(
            int requestId,
            String account,
            String tag,
            String value,
            String currency) {
    }

    default void onAccountSummaryEnd(int requestId) {
    }

    default void onError(IbkrError error) {
    }

    default void onConnectionClosed() {
    }
}
