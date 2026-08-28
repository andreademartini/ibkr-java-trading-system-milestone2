package it.andreademartini.trading.ibkr;

import com.ib.client.EWrapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void errorCallbackUsesCurrentIbkrSignature() {
        IbkrEWrapper handler = new IbkrEWrapper();
        AtomicReference<IbkrError> listenerValue = new AtomicReference<>();
        handler.addListener(new IbkrEventListener() {
            @Override
            public void onError(IbkrError error) {
                listenerValue.set(error);
            }
        });

        EWrapper wrapper = handler.createProxy();
        wrapper.error(-1, 1_042_940_729L, 502, "Couldn't connect to TWS", "");

        IbkrError error = listenerValue.get();
        assertEquals(-1, error.requestId());
        assertEquals(502, error.code());
        assertEquals("Couldn't connect to TWS", error.message());
    }
}
