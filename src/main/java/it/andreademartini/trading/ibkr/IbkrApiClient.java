package it.andreademartini.trading.ibkr;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;
import com.ib.client.Order;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

@Service
public class IbkrApiClient {

    private final IbkrConnectionService connectionService;

    public IbkrApiClient(IbkrConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    public void requestMarketDataType(int marketDataType) {
        connectionService.requireClientSocket().reqMarketDataType(marketDataType);
    }

    public void requestMarketData(int requestId, Contract contract) {
        connectionService.requireClientSocket()
                .reqMktData(requestId, contract, "", false, false, List.of());
    }

    public void cancelMarketData(int requestId) {
        connectionService.requireClientSocket().cancelMktData(requestId);
    }

    public void requestHistoricalData(
            int requestId,
            Contract contract,
            String endDateTime,
            String duration,
            String barSize,
            String whatToShow,
            int useRegularTradingHours,
            int formatDate,
            boolean keepUpToDate) {
        connectionService.requireClientSocket()
                .reqHistoricalData(
                        requestId,
                        contract,
                        endDateTime,
                        duration,
                        barSize,
                        whatToShow,
                        useRegularTradingHours,
                        formatDate,
                        keepUpToDate,
                        List.of());
    }

    public void cancelHistoricalData(int requestId) {
        connectionService.requireClientSocket().cancelHistoricalData(requestId);
    }

    public void requestPositions() {
        connectionService.requireClientSocket().reqPositions();
    }

    public void requestOpenOrders() {
        connectionService.requireClientSocket().reqOpenOrders();
    }

    public void requestAccountSummary(int requestId, String tags) {
        connectionService.requireClientSocket()
                .reqAccountSummary(requestId, "All", tags);
    }

    public void placeOrder(int orderId, Contract contract, Order order) {
        connectionService.requireClientSocket().placeOrder(orderId, contract, order);
    }

    /**
     * La firma cancelOrder è cambiata tra versioni della TWS API.
     * La reflection supporta sia cancelOrder(int) sia cancelOrder(int, OrderCancel).
     */
    public void cancelOrder(int orderId) {
        EClientSocket client = connectionService.requireClientSocket();

        try {
            for (Method method : client.getClass().getMethods()) {
                if (!method.getName().equals("cancelOrder")) {
                    continue;
                }

                if (method.getParameterCount() == 1) {
                    method.invoke(client, orderId);
                    return;
                }

                if (method.getParameterCount() == 2) {
                    Class<?> cancelType = method.getParameterTypes()[1];
                    Constructor<?> constructor = cancelType.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    Object cancelOptions = constructor.newInstance();
                    method.invoke(client, orderId, cancelOptions);
                    return;
                }
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Impossibile invocare cancelOrder sulla versione TWS API installata",
                    exception);
        }

        throw new IllegalStateException(
                "Metodo cancelOrder non trovato nella versione TWS API installata");
    }
}
