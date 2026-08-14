package it.andreademartini.trading.orders;

import com.ib.client.EWrapper;
import com.ib.client.OrderType;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.ibkr.IbkrEWrapper;
import it.andreademartini.trading.ibkr.OrderIdSequence;
import it.andreademartini.trading.marketdata.IbkrContractFactory;
import it.andreademartini.trading.strategy.TradeDirection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
        assertEquals(OrderType.TRAIL, commands.get(2).order().orderType());
    }

    @Test
    void createsShortTradePlanWithBuyProtectiveOrders() {
        TradingSystemProperties properties = new TradingSystemProperties();
        properties.getIbkr().setAccountId("PAPER_ACCOUNT");

        IbkrEWrapper wrapperHandler = new IbkrEWrapper();
        OrderIdSequence sequence = new OrderIdSequence(wrapperHandler);
        EWrapper wrapper = wrapperHandler.createProxy();
        wrapper.nextValidId(200);

        IbkrOrderFactory factory = new IbkrOrderFactory(
                properties,
                sequence,
                new IbkrContractFactory());

        TradePlan plan = new TradePlan(
                "trade-short-1",
                TradeDirection.SHORT,
                TradeState.OPENING_SHORT,
                BigDecimal.valueOf(10_000),
                BigDecimal.valueOf(200),
                BigDecimal.valueOf(95),
                BigDecimal.valueOf(110),
                BigDecimal.valueOf(15),
                BigDecimal.valueOf(15),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                13,
                BigDecimal.valueOf(195),
                BigDecimal.valueOf(1235),
                List.of(
                        new TakeProfitAllocation(
                                1,
                                BigDecimal.valueOf(2),
                                BigDecimal.valueOf(50),
                                6,
                                BigDecimal.valueOf(93.10)),
                        new TakeProfitAllocation(
                                2,
                                BigDecimal.valueOf(4),
                                BigDecimal.valueOf(30),
                                3,
                                BigDecimal.valueOf(91.20)),
                        new TakeProfitAllocation(
                                3,
                                BigDecimal.valueOf(8),
                                BigDecimal.valueOf(20),
                                4,
                                BigDecimal.valueOf(87.40))));
        PaperOrderRequest request = new PaperOrderRequest(
                "SPY",
                OrderSide.SELL,
                PaperOrderType.BRACKET,
                13,
                null,
                110.0,
                93.10,
                null,
                "test",
                TradeDirection.SHORT,
                plan);

        List<IbkrOrderCommand> commands = factory.create(
                request,
                properties.getMarketData().getInstruments().getFirst(),
                95.0
        );

        assertEquals(5, commands.size());
        assertEquals(OrderSide.SELL, commands.get(0).side());
        assertEquals(OrderType.STP, commands.get(0).order().orderType());
        assertFalse(commands.get(0).order().transmit());
        assertEquals(95.0, commands.get(0).order().auxPrice());

        assertEquals(OrderSide.BUY, commands.get(1).side());
        assertEquals(OrderSide.BUY, commands.get(2).side());
        assertEquals(OrderSide.BUY, commands.get(3).side());
        assertEquals(OrderSide.BUY, commands.get(4).side());
        assertEquals(OrderType.LMT, commands.get(1).order().orderType());
        assertEquals(OrderType.LMT, commands.get(2).order().orderType());
        assertEquals(OrderType.LMT, commands.get(3).order().orderType());
        assertEquals(OrderType.STP, commands.get(4).order().orderType());
        assertTrue(commands.get(4).order().transmit());
        assertEquals(110.0, commands.get(4).order().auxPrice());
        assertEquals(TradeState.OPENING_SHORT, commands.get(0).tradeState());
        assertEquals(TradeState.CLOSING_SHORT, commands.get(4).tradeState());
    }
}
