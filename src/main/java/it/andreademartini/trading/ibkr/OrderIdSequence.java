package it.andreademartini.trading.ibkr;

import com.ib.client.Contract;
import com.ib.client.Order;
import com.ib.client.OrderState;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class OrderIdSequence implements IbkrEventListener {

    private final AtomicInteger nextOrderId = new AtomicInteger(-1);

    public OrderIdSequence(IbkrEWrapper wrapper) {
        wrapper.addListener(this);
    }

    @Override
    public void onNextValidId(int orderId) {
        nextOrderId.accumulateAndGet(orderId, Math::max);
    }

    @Override
    public void onOpenOrder(
            int orderId,
            Contract contract,
            Order order,
            OrderState orderState) {
        nextOrderId.accumulateAndGet(orderId + 1, Math::max);
    }

    public int nextId() {
        int id = nextOrderId.getAndIncrement();
        if (id < 0) {
            nextOrderId.compareAndSet(id + 1, -1);
            throw new IllegalStateException("nextValidId non ancora ricevuto da IBKR");
        }
        return id;
    }

    public boolean isInitialized() {
        return nextOrderId.get() >= 0;
    }
}
