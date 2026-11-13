package it.andreademartini.trading.ibkr;

import com.ib.client.Contract;
import com.ib.client.Bar;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.Execution;
import com.ib.client.CommissionAndFeesReport;
import com.ib.client.ContractDetails;

import java.util.List;

public interface IbkrEventListener {
    default void onCurrentTime(long epochSeconds) {}
    default void onOpenOrderEnd() {}
    default void onCompletedOrder(Contract contract,Order order,OrderState state) {}
    default void onCompletedOrdersEnd() {}
    default void onExecution(int requestId,Contract contract,Execution execution) {}
    default void onExecutionEnd(int requestId) {}
    default void onCommissionAndFees(CommissionAndFeesReport report) {}
    default void onContractDetails(int requestId,ContractDetails details) {}
    default void onContractDetailsEnd(int requestId) {}
    default void onTickString(int requestId,int field,String value) {}
    default void onMarketDataType(int requestId,int type) {}

    default void onNextValidId(int orderId) {
    }

    default void onManagedAccounts(List<String> accountIds) {
    }

    default void onTickPrice(int requestId, int field, double price) {
    }

    default void onTickSize(int requestId, int field, Object size) {
    }

    default void onHistoricalData(int requestId, Bar bar) {
    }

    default void onHistoricalDataEnd(int requestId, String start, String end) {
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
