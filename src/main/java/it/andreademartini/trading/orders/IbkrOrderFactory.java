package it.andreademartini.trading.orders;

import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.Order;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.ibkr.OrderIdSequence;
import it.andreademartini.trading.marketdata.IbkrContractFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class IbkrOrderFactory {

    private final TradingSystemProperties properties;
    private final OrderIdSequence orderIdSequence;
    private final IbkrContractFactory contractFactory;

    public IbkrOrderFactory(
            TradingSystemProperties properties,
            OrderIdSequence orderIdSequence,
            IbkrContractFactory contractFactory) {
        this.properties = properties;
        this.orderIdSequence = orderIdSequence;
        this.contractFactory = contractFactory;
    }

    public List<IbkrOrderCommand> create(
            PaperOrderRequest request,
            TradingSystemProperties.InstrumentProperties instrument,
            double referencePrice) {
        Contract contract = contractFactory.create(instrument);

        return switch (request.type()) {
            case MARKET -> List.of(singleMarket(request, contract, referencePrice));
            case LIMIT -> List.of(singleLimit(request, contract, referencePrice));
            case STOP -> List.of(singleStop(request, contract, referencePrice));
            case TRAILING_STOP ->
                    List.of(singleTrailingStop(request, contract, referencePrice));
            case BRACKET -> bracketWithStop(request, contract, referencePrice);
            case BRACKET_WITH_TRAILING_STOP ->
                    bracketWithTrailingStop(request, contract, referencePrice);
        };
    }

    private IbkrOrderCommand singleMarket(
            PaperOrderRequest request,
            Contract contract,
            double referencePrice) {
        int orderId = orderIdSequence.nextId();
        Order order = baseOrder(
                orderId,
                request.side(),
                request.quantity(),
                properties.getOrders().getEntryTimeInForce(),
                request.reason());
        order.orderType("MKT");
        order.transmit(true);

        return command(orderId, null, "SINGLE", request, PaperOrderType.MARKET,
                referencePrice, null, null, null, contract, order);
    }

    private IbkrOrderCommand singleLimit(
            PaperOrderRequest request,
            Contract contract,
            double referencePrice) {
        require(request.limitPrice(), "limitPrice è obbligatorio per LIMIT");
        int orderId = orderIdSequence.nextId();
        Order order = baseOrder(
                orderId,
                request.side(),
                request.quantity(),
                properties.getOrders().getEntryTimeInForce(),
                request.reason());
        order.orderType("LMT");
        order.lmtPrice(request.limitPrice());
        order.transmit(true);

        return command(orderId, null, "SINGLE", request, PaperOrderType.LIMIT,
                referencePrice, request.limitPrice(), null, null, contract, order);
    }

    private IbkrOrderCommand singleStop(
            PaperOrderRequest request,
            Contract contract,
            double referencePrice) {
        require(request.stopPrice(), "stopPrice è obbligatorio per STOP");
        int orderId = orderIdSequence.nextId();
        Order order = baseOrder(
                orderId,
                request.side(),
                request.quantity(),
                properties.getOrders().getProtectiveTimeInForce(),
                request.reason());
        order.orderType("STP");
        order.auxPrice(request.stopPrice());
        order.transmit(true);

        return command(orderId, null, "SINGLE", request, PaperOrderType.STOP,
                referencePrice, null, request.stopPrice(), null, contract, order);
    }

    private IbkrOrderCommand singleTrailingStop(
            PaperOrderRequest request,
            Contract contract,
            double referencePrice) {
        require(request.trailingPercent(),
                "trailingPercent è obbligatorio per TRAILING_STOP");
        requirePositive(referencePrice, "Prezzo di riferimento non disponibile");

        int orderId = orderIdSequence.nextId();
        double initialStop = initialTrailingStop(
                request.side(), referencePrice, request.trailingPercent());

        Order order = baseOrder(
                orderId,
                request.side(),
                request.quantity(),
                properties.getOrders().getProtectiveTimeInForce(),
                request.reason());
        order.orderType("TRAIL");
        order.trailingPercent(request.trailingPercent());
        order.trailStopPrice(initialStop);
        order.transmit(true);

        return command(orderId, null, "TRAILING_STOP", request,
                PaperOrderType.TRAILING_STOP, referencePrice,
                null, initialStop, request.trailingPercent(), contract, order);
    }

    private List<IbkrOrderCommand> bracketWithStop(
            PaperOrderRequest request,
            Contract contract,
            double referencePrice) {
        require(request.takeProfitPrice(),
                "takeProfitPrice è obbligatorio per BRACKET");
        require(request.stopPrice(),
                "stopPrice è obbligatorio per BRACKET");

        int parentId = orderIdSequence.nextId();
        int takeProfitId = orderIdSequence.nextId();
        int stopId = orderIdSequence.nextId();

        Order parent = entryOrder(parentId, request);
        parent.transmit(false);

        Order takeProfit = baseOrder(
                takeProfitId,
                request.side().opposite(),
                request.quantity(),
                properties.getOrders().getProtectiveTimeInForce(),
                request.reason());
        takeProfit.orderType("LMT");
        takeProfit.lmtPrice(request.takeProfitPrice());
        takeProfit.parentId(parentId);
        takeProfit.transmit(false);

        Order stopLoss = baseOrder(
                stopId,
                request.side().opposite(),
                request.quantity(),
                properties.getOrders().getProtectiveTimeInForce(),
                request.reason());
        stopLoss.orderType("STP");
        stopLoss.auxPrice(request.stopPrice());
        stopLoss.parentId(parentId);
        stopLoss.transmit(true);

        List<IbkrOrderCommand> commands = new ArrayList<>();
        commands.add(command(parentId, null, "ENTRY", request, request.type(),
                referencePrice, request.limitPrice(), null, null, contract, parent));
        commands.add(command(takeProfitId, parentId, "TAKE_PROFIT", request,
                PaperOrderType.LIMIT, referencePrice, request.takeProfitPrice(),
                null, null, contract, takeProfit));
        commands.add(command(stopId, parentId, "STOP_LOSS", request,
                PaperOrderType.STOP, referencePrice, null, request.stopPrice(),
                null, contract, stopLoss));
        return List.copyOf(commands);
    }

    private List<IbkrOrderCommand> bracketWithTrailingStop(
            PaperOrderRequest request,
            Contract contract,
            double referencePrice) {
        require(request.takeProfitPrice(),
                "takeProfitPrice è obbligatorio per BRACKET_WITH_TRAILING_STOP");
        require(request.trailingPercent(),
                "trailingPercent è obbligatorio per BRACKET_WITH_TRAILING_STOP");
        requirePositive(referencePrice, "Prezzo di riferimento non disponibile");

        int parentId = orderIdSequence.nextId();
        int takeProfitId = orderIdSequence.nextId();
        int trailingStopId = orderIdSequence.nextId();
        OrderSide exitSide = request.side().opposite();
        double initialStop = initialTrailingStop(
                exitSide, referencePrice, request.trailingPercent());

        Order parent = entryOrder(parentId, request);
        parent.transmit(false);

        Order takeProfit = baseOrder(
                takeProfitId,
                exitSide,
                request.quantity(),
                properties.getOrders().getProtectiveTimeInForce(),
                request.reason());
        takeProfit.orderType("LMT");
        takeProfit.lmtPrice(request.takeProfitPrice());
        takeProfit.parentId(parentId);
        takeProfit.transmit(false);

        Order trailingStop = baseOrder(
                trailingStopId,
                exitSide,
                request.quantity(),
                properties.getOrders().getProtectiveTimeInForce(),
                request.reason());
        trailingStop.orderType("TRAIL");
        trailingStop.trailingPercent(request.trailingPercent());
        trailingStop.trailStopPrice(initialStop);
        trailingStop.parentId(parentId);
        trailingStop.transmit(true);

        List<IbkrOrderCommand> commands = new ArrayList<>();
        commands.add(command(parentId, null, "ENTRY", request, request.type(),
                referencePrice, request.limitPrice(), null, null, contract, parent));
        commands.add(command(takeProfitId, parentId, "TAKE_PROFIT", request,
                PaperOrderType.LIMIT, referencePrice, request.takeProfitPrice(),
                null, null, contract, takeProfit));
        commands.add(command(trailingStopId, parentId, "TRAILING_STOP", request,
                PaperOrderType.TRAILING_STOP, referencePrice, null, initialStop,
                request.trailingPercent(), contract, trailingStop));
        return List.copyOf(commands);
    }

    private Order entryOrder(int orderId, PaperOrderRequest request) {
        Order order = baseOrder(
                orderId,
                request.side(),
                request.quantity(),
                properties.getOrders().getEntryTimeInForce(),
                request.reason());

        if (request.limitPrice() != null) {
            order.orderType("LMT");
            order.lmtPrice(request.limitPrice());
        } else {
            order.orderType("MKT");
        }
        return order;
    }

    private Order baseOrder(
            int orderId,
            OrderSide side,
            long quantity,
            String timeInForce,
            String reason) {
        Order order = new Order();
        order.orderId(orderId);
        order.action(side.name());
        order.totalQuantity(Decimal.get(quantity));
        order.account(properties.getIbkr().getAccountId().trim());
        order.tif(timeInForce);
        order.outsideRth(properties.getOrders().isOutsideRegularTradingHours());
        order.orderRef(orderReference(reason));
        return order;
    }

    private IbkrOrderCommand command(
            int orderId,
            Integer parentId,
            String role,
            PaperOrderRequest sourceRequest,
            PaperOrderType actualType,
            Double referencePrice,
            Double limitPrice,
            Double stopPrice,
            Double trailingPercent,
            Contract contract,
            Order order) {
        OrderSide actualSide = parentId == null
                ? sourceRequest.side()
                : sourceRequest.side().opposite();

        return new IbkrOrderCommand(
                orderId,
                parentId,
                role,
                sourceRequest.symbol(),
                actualSide,
                actualType,
                sourceRequest.quantity(),
                referencePrice,
                limitPrice,
                stopPrice,
                trailingPercent,
                contract,
                order
        );
    }

    private double initialTrailingStop(
            OrderSide orderSide,
            double referencePrice,
            double trailingPercent) {
        double factor = trailingPercent / 100.0;
        return orderSide == OrderSide.SELL
                ? referencePrice * (1.0 - factor)
                : referencePrice * (1.0 + factor);
    }

    private String orderReference(String reason) {
        String prefix = reason == null || reason.isBlank()
                ? "paper-java"
                : reason.replaceAll("[^A-Za-z0-9_-]", "_");
        if (prefix.length() > 20) {
            prefix = prefix.substring(0, 20);
        }
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void require(Double value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requirePositive(double value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
