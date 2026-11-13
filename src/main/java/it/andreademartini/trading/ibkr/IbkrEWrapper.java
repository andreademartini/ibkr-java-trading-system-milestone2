package it.andreademartini.trading.ibkr;

import com.ib.client.Bar;
import com.ib.client.Contract;
import com.ib.client.EWrapper;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.Execution;
import com.ib.client.CommissionAndFeesReport;
import com.ib.client.ContractDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Component
public class IbkrEWrapper implements InvocationHandler {

    private static final Logger log = LoggerFactory.getLogger(IbkrEWrapper.class);

    private static final Set<Integer> INFORMATIONAL_CODES = Set.of(
            1101, 1102, 2104, 2106, 2158
    );
    private static final Set<Integer> WARNING_CODES = Set.of(
            162, 10167, 2174
    );

    private final AtomicReference<CompletableFuture<Integer>> nextValidIdFuture =
            new AtomicReference<>(new CompletableFuture<>());

    private final CopyOnWriteArrayList<IbkrEventListener> listeners =
            new CopyOnWriteArrayList<>();

    private volatile Runnable connectionClosedListener = () -> { };

    public EWrapper createProxy() {
        return (EWrapper) Proxy.newProxyInstance(
                EWrapper.class.getClassLoader(),
                new Class<?>[]{EWrapper.class},
                this
        );
    }

    public void prepareForConnection() {
        nextValidIdFuture.set(new CompletableFuture<>());
    }

    public int awaitNextValidId(Duration timeout) throws Exception {
        return nextValidIdFuture.get().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void setConnectionClosedListener(Runnable listener) {
        this.connectionClosedListener = listener == null ? () -> { } : listener;
    }

    public void addListener(IbkrEventListener listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    public void removeListener(IbkrEventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        String methodName = method.getName();
        Object[] safeArgs = args == null ? new Object[0] : args;

        if (method.getDeclaringClass() == Object.class) {
            return handleObjectMethod(proxy, methodName, safeArgs);
        }

        try {
            switch (methodName) {
                case "nextValidId" -> handleNextValidId(safeArgs);
                case "connectAck" -> log.info("Handshake socket IBKR ricevuto");
                case "managedAccounts" -> handleManagedAccounts(safeArgs);
                case "currentTime" -> handleCurrentTime(safeArgs);
                case "tickPrice" -> handleTickPrice(safeArgs);
                case "tickString" -> { if(safeArgs.length>=3)notifyListeners(l->l.onTickString(numberAsInt(safeArgs[0]),numberAsInt(safeArgs[1]),String.valueOf(safeArgs[2]))); }
                case "tickSize" -> handleTickSize(safeArgs);
                case "historicalData" -> handleHistoricalData(safeArgs);
                case "historicalDataEnd" -> handleHistoricalDataEnd(safeArgs);
                case "orderStatus" -> handleOrderStatus(safeArgs);
                case "openOrder" -> handleOpenOrder(safeArgs);
                case "openOrderEnd" -> notifyListeners(IbkrEventListener::onOpenOrderEnd);
                case "completedOrder" -> {
                    if(safeArgs.length>=3&&safeArgs[0] instanceof Contract c&&safeArgs[1] instanceof Order o&&safeArgs[2] instanceof OrderState s)
                        notifyListeners(l->l.onCompletedOrder(c,o,s));
                }
                case "completedOrdersEnd" -> notifyListeners(IbkrEventListener::onCompletedOrdersEnd);
                case "execDetails" -> {
                    if(safeArgs.length>=3&&safeArgs[1] instanceof Contract c&&safeArgs[2] instanceof Execution e)
                        notifyListeners(l->l.onExecution(numberAsInt(safeArgs[0]),c,e));
                }
                case "execDetailsEnd" -> notifyListeners(l->l.onExecutionEnd(numberAsInt(safeArgs[0])));
                case "commissionAndFeesReport" -> {
                    if(safeArgs.length>0&&safeArgs[0] instanceof CommissionAndFeesReport r)notifyListeners(l->l.onCommissionAndFees(r));
                }
                case "contractDetails" -> {
                    if(safeArgs.length>=2&&safeArgs[1] instanceof ContractDetails d)notifyListeners(l->l.onContractDetails(numberAsInt(safeArgs[0]),d));
                }
                case "contractDetailsEnd" -> notifyListeners(l->l.onContractDetailsEnd(numberAsInt(safeArgs[0])));
                case "marketDataType" -> {
                    if(safeArgs.length>=2)notifyListeners(l->l.onMarketDataType(numberAsInt(safeArgs[0]),numberAsInt(safeArgs[1])));
                }
                case "position" -> handlePosition(safeArgs);
                case "positionEnd" -> notifyListeners(IbkrEventListener::onPositionEnd);
                case "accountSummary" -> handleAccountSummary(safeArgs);
                case "accountSummaryEnd" -> handleAccountSummaryEnd(safeArgs);
                case "connectionClosed" -> handleConnectionClosed();
                case "error" -> handleError(safeArgs);
                default -> log.trace("Callback IBKR {}: {}", methodName, Arrays.toString(safeArgs));
            }
        } catch (RuntimeException exception) {
            log.error("Errore durante la gestione del callback IBKR {}", methodName, exception);
        }

        return defaultValue(method.getReturnType());
    }

    private void handleNextValidId(Object[] args) {
        if (args.length == 0 || !(args[0] instanceof Number number)) {
            log.warn("Callback nextValidId ricevuto senza un identificativo valido");
            return;
        }

        int orderId = number.intValue();
        nextValidIdFuture.get().complete(orderId);
        notifyListeners(listener -> listener.onNextValidId(orderId));
        log.info("Connessione IBKR pronta; nextValidId ricevuto: {}", orderId);
    }

    private void handleManagedAccounts(Object[] args) {
        String rawAccounts = args.length == 0 ? "" : String.valueOf(args[0]);
        List<String> accounts = Arrays.stream(rawAccounts.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();

        log.info("Account comunicati da IBKR: {}", accounts);
        notifyListeners(listener -> listener.onManagedAccounts(accounts));
    }

    private void handleCurrentTime(Object[] args) {
        if (args.length > 0 && args[0] instanceof Number number) {
            notifyListeners(l->l.onCurrentTime(number.longValue()));
            log.info("Ora server IBKR: {}", Instant.ofEpochSecond(number.longValue()));
        }
    }

    private void handleTickPrice(Object[] args) {
        if (args.length < 3) {
            return;
        }
        int requestId = numberAsInt(args[0]);
        int field = numberAsInt(args[1]);
        double price = numberAsDouble(args[2]);
        notifyListeners(listener -> listener.onTickPrice(requestId, field, price));
    }

    private void handleTickSize(Object[] args) {
        if (args.length < 3) {
            return;
        }
        int requestId = numberAsInt(args[0]);
        int field = numberAsInt(args[1]);
        Object size = args[2];
        notifyListeners(listener -> listener.onTickSize(requestId, field, size));
    }

    private void handleHistoricalData(Object[] args) {
        if (args.length < 2 || !(args[1] instanceof Bar bar)) {
            log.debug("Callback historicalData con firma inattesa: {}", Arrays.toString(args));
            return;
        }

        int requestId = numberAsInt(args[0]);
        notifyListeners(listener -> listener.onHistoricalData(requestId, bar));
    }

    private void handleHistoricalDataEnd(Object[] args) {
        if (args.length < 3) {
            log.debug("Callback historicalDataEnd con firma inattesa: {}", Arrays.toString(args));
            return;
        }

        int requestId = numberAsInt(args[0]);
        String start = String.valueOf(args[1]);
        String end = String.valueOf(args[2]);
        notifyListeners(listener -> listener.onHistoricalDataEnd(requestId, start, end));
    }

    private void handleOrderStatus(Object[] args) {
        if (args.length < 11) {
            log.debug("Callback orderStatus con firma inattesa: {}", Arrays.toString(args));
            return;
        }

        IbkrOrderStatusUpdate update = new IbkrOrderStatusUpdate(
                numberAsInt(args[0]),
                String.valueOf(args[1]),
                IbkrNumber.toBigDecimal(args[2]),
                IbkrNumber.toBigDecimal(args[3]),
                numberAsDouble(args[4]),
                numberAsInt(args[5]),
                numberAsInt(args[6]),
                numberAsDouble(args[7]),
                numberAsInt(args[8]),
                String.valueOf(args[9]),
                numberAsDouble(args[10])
        );
        notifyListeners(listener -> listener.onOrderStatus(update));
    }

    private void handleOpenOrder(Object[] args) {
        if (args.length < 4
                || !(args[1] instanceof Contract contract)
                || !(args[2] instanceof Order order)
                || !(args[3] instanceof OrderState state)) {
            return;
        }

        int orderId = numberAsInt(args[0]);
        notifyListeners(listener -> listener.onOpenOrder(orderId, contract, order, state));
    }

    private void handlePosition(Object[] args) {
        if (args.length < 4 || !(args[1] instanceof Contract contract)) {
            return;
        }

        String account = String.valueOf(args[0]);
        Object position = args[2];
        double averageCost = numberAsDouble(args[3]);
        notifyListeners(listener ->
                listener.onPosition(account, contract, position, averageCost));
    }

    private void handleAccountSummary(Object[] args) {
        if (args.length < 5) {
            return;
        }

        int requestId = numberAsInt(args[0]);
        String account = String.valueOf(args[1]);
        String tag = String.valueOf(args[2]);
        String value = String.valueOf(args[3]);
        String currency = String.valueOf(args[4]);

        notifyListeners(listener ->
                listener.onAccountSummary(requestId, account, tag, value, currency));
    }

    private void handleAccountSummaryEnd(Object[] args) {
        int requestId = args.length == 0 ? -1 : numberAsInt(args[0]);
        notifyListeners(listener -> listener.onAccountSummaryEnd(requestId));
    }

    private void handleConnectionClosed() {
        log.warn("IBKR ha segnalato la chiusura della connessione");
        notifyListeners(IbkrEventListener::onConnectionClosed);
        connectionClosedListener.run();
    }

    private void handleError(Object[] args) {
        if (args.length == 1 && args[0] instanceof Throwable throwable) {
            log.error("Eccezione interna TWS API", throwable);
            return;
        }

        if (args.length == 1) {
            log.error("Errore TWS API: {}", args[0]);
            return;
        }

        IbkrError error = extractError(args);
        Integer code = error.code();
        int requestId = error.requestId();
        String message = error.message();

        if (code != null && INFORMATIONAL_CODES.contains(code)) {
            log.info("Messaggio IBKR id={} code={}: {}", requestId, code, message);
        } else if (code != null && WARNING_CODES.contains(code)) {
            log.warn("Avviso IBKR id={} code={}: {}", requestId, code, message);
        } else if (code != null && code == 1100) {
            log.warn("Connettività IBKR interrotta id={} code={}: {}",
                    requestId, code, message);
        } else {
            log.error("Errore IBKR id={} code={}: {}", requestId, code, message);
        }

        notifyListeners(listener -> listener.onError(error));
    }

    private void notifyListeners(Consumer<IbkrEventListener> operation) {
        for (IbkrEventListener listener : listeners) {
            try {
                operation.accept(listener);
            } catch (RuntimeException exception) {
                log.error("Listener IBKR {} ha generato un errore",
                        listener.getClass().getSimpleName(), exception);
            }
        }
    }

    private IbkrError extractError(Object[] args) {
        int requestId = args.length > 0 ? numberAsInt(args[0]) : -1;

        if (args.length >= 5 && args[2] instanceof Number code) {
            return new IbkrError(
                    requestId,
                    code.intValue(),
                    String.valueOf(args[3]),
                    args[4] == null ? "" : String.valueOf(args[4]));
        }

        if (args.length >= 3 && args[1] instanceof Number code) {
            return new IbkrError(
                    requestId,
                    code.intValue(),
                    String.valueOf(args[2]),
                    args.length > 3 && args[3] != null ? String.valueOf(args[3]) : "");
        }

        return new IbkrError(requestId, null, Arrays.toString(args), "");
    }

    private int numberAsInt(Object value) {
        return value instanceof Number number ? number.intValue() : -1;
    }

    private double numberAsDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private Object handleObjectMethod(Object proxy, String methodName, Object[] args) {
        return switch (methodName) {
            case "toString" -> "IbkrEWrapperProxy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> args.length > 0 && proxy == args[0];
            default -> null;
        };
    }

    private Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive() || returnType == void.class) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        return null;
    }
}
