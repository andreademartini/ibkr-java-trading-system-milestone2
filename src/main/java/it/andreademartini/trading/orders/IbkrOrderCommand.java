package it.andreademartini.trading.orders;

import com.ib.client.Contract;
import com.ib.client.Order;

public record IbkrOrderCommand(
        int orderId,
        Integer parentOrderId,
        String role,
        String symbol,
        OrderSide side,
        PaperOrderType type,
        long quantity,
        Double referencePrice,
        Double limitPrice,
        Double stopPrice,
        Double trailingPercent,
        Contract contract,
        Order order) {
}
