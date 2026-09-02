package it.andreademartini.trading.orders;

import com.ib.client.Contract;
import com.ib.client.Order;
import it.andreademartini.trading.strategy.TradeDirection;

public record IbkrOrderCommand(
        int orderId,
        Integer parentOrderId,
        String tradeId,
        String role,
        String symbol,
        OrderSide side,
        PaperOrderType type,
        TradeDirection direction,
        TradeState tradeState,
        long quantity,
        Double referencePrice,
        Double limitPrice,
        Double stopPrice,
        Double trailingPercent,
        Contract contract,
        Order order) {

    public IbkrOrderCommand withQuantityAndStopPrice(
            long updatedQuantity,
            Double updatedStopPrice) {
        return new IbkrOrderCommand(
                orderId,
                parentOrderId,
                tradeId,
                role,
                symbol,
                side,
                type,
                direction,
                tradeState,
                updatedQuantity,
                referencePrice,
                limitPrice,
                updatedStopPrice,
                trailingPercent,
                contract,
                order);
    }
}
