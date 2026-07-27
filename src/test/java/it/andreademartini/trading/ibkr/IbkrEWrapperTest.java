package it.andreademartini.trading.ibkr;

import com.ib.client.EWrapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IbkrEWrapperTest {

    @Test
    void nextValidIdMarksConnectionReadyAndNotifiesListeners() throws Exception {
        IbkrEWrapper handler = new IbkrEWrapper();
        AtomicInteger listenerValue = new AtomicInteger(-1);
        handler.addListener(new IbkrEventListener() {
            @Override
            public void onNextValidId(int orderId) {
                listenerValue.set(orderId);
            }
        });

        handler.prepareForConnection();
        EWrapper wrapper = handler.createProxy();
        wrapper.nextValidId(1234);

        assertEquals(1234, handler.awaitNextValidId(Duration.ofSeconds(1)));
        assertEquals(1234, listenerValue.get());
    }
}
