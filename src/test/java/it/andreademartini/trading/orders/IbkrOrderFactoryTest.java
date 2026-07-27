package it.andreademartini.trading.orders;

import com.ib.client.EWrapper;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.ibkr.IbkrEWrapper;
import it.andreademartini.trading.ibkr.OrderIdSequence;
import it.andreademartini.trading.marketdata.IbkrContractFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IbkrOrderFactoryTest {

    @Test
    void createsBracketWithTrailingStopUsingTransmitChain() {
        TradingSystemProperties properties = new TradingSystemProperties();
        properties.getIbkr().setAccountId("PAPER_ACCOUNT");

        IbkrEWrapper wrapperHandler = new IbkrEWrapper();
        OrderIdSequence sequence = new OrderIdSequence(wrapperHandler);
        EWrapper wrapper = wrapperHandler.createProxy();
        wrapper.nextValidId(100);

        IbkrOrderFactory factory = new IbkrOrderFactory(
                properties,
                sequence,
                new IbkrContractFactory());

        PaperOrderRequest request = new PaperOrderRequest(
                "SPY",
                OrderSide.BUY,
                PaperOrderType.BRACKET_WITH_TRAILING_STOP,
                1,
                null,
                null,
                110.0,
                1.0,
                "test"
        );

        List<IbkrOrderCommand> commands = factory.create(
                request,
                properties.getMarketData().getInstruments().getFirst(),
                100.0
        );

        assertEquals(3, commands.size());
        assertEquals(100, commands.get(0).orderId());
        assertFalse(commands.get(0).order().transmit());
        assertFalse(commands.get(1).order().transmit());
        assertTrue(commands.get(2).order().transmit());
        assertEquals(100, commands.get(1).order().parentId());
        assertEquals(100, commands.get(2).order().parentId());
        assertEquals("TRAIL", commands.get(2).order().orderType());
    }
}
