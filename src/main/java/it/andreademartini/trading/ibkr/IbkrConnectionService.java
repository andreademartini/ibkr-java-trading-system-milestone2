package it.andreademartini.trading.ibkr;

import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReader;
import com.ib.client.EReaderSignal;
import com.ib.client.EWrapper;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.risk.TradingSafetyGuard;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class IbkrConnectionService {

    private static final Logger log = LoggerFactory.getLogger(IbkrConnectionService.class);

    private final TradingSystemProperties properties;
    private final TradingSafetyGuard safetyGuard;
    private final IbkrEWrapper wrapperHandler;
    private final Object connectionMonitor = new Object();

    private final AtomicReference<IbkrConnectionState> state =
            new AtomicReference<>(IbkrConnectionState.DISCONNECTED);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);

    private final ScheduledExecutorService reconnectExecutor =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "ibkr-reconnect");
                thread.setDaemon(false);
                return thread;
            });

    private volatile EClientSocket clientSocket;
    private volatile EReaderSignal readerSignal;
    private volatile EReader reader;
    private volatile Thread messageProcessorThread;
    private volatile ScheduledFuture<?> reconnectFuture;
    private volatile boolean shutdownRequested;

    public IbkrConnectionService(
            TradingSystemProperties properties,
            TradingSafetyGuard safetyGuard,
            IbkrEWrapper wrapperHandler) {
        this.properties = properties;
        this.safetyGuard = safetyGuard;
        this.wrapperHandler = wrapperHandler;
        this.wrapperHandler.setConnectionClosedListener(this::onConnectionClosed);
    }

    public void connect() {
        safetyGuard.assertIbkrConnectionAllowed();

        synchronized (connectionMonitor) {
            if (state.get() == IbkrConnectionState.READY
                    || state.get() == IbkrConnectionState.CONNECTING
                    || state.get() == IbkrConnectionState.SOCKET_CONNECTED) {
                log.debug("Connessione IBKR già attiva o in corso: {}", state.get());
                return;
            }

            shutdownRequested = false;
            cancelPendingReconnect();
            state.set(IbkrConnectionState.CONNECTING);

            TradingSystemProperties.IbkrProperties ibkr = properties.getIbkr();
            log.info("Connessione a IBKR host={} port={} clientId={} mode=PAPER",
                    ibkr.getHost(), ibkr.getPort(), ibkr.getClientId());

            wrapperHandler.prepareForConnection();
            readerSignal = new EJavaSignal();
            EWrapper wrapperProxy = wrapperHandler.createProxy();
            clientSocket = new EClientSocket(wrapperProxy, readerSignal);

            try {
                clientSocket.eConnect(ibkr.getHost(), ibkr.getPort(), ibkr.getClientId());

                if (!clientSocket.isConnected()) {
                    throw new IbkrConnectionException(
                            "Socket non connesso. Controllare TWS/IB Gateway, API e porta");
                }

                state.set(IbkrConnectionState.SOCKET_CONNECTED);

                reader = new EReader(clientSocket, readerSignal);
                reader.start();
                startMessageProcessor();
            } catch (Exception exception) {
                cleanupConnectionResources();
                state.set(IbkrConnectionState.DISCONNECTED);
                scheduleReconnectIfConfigured();

                if (exception instanceof IbkrConnectionException connectionException) {
                    throw connectionException;
                }
                throw new IbkrConnectionException("Errore durante la connessione a IBKR", exception);
            }
        }

        awaitConnectionReady();
    }

    private void awaitConnectionReady() {
        Duration timeout = properties.getIbkr().getConnectionTimeout();

        try {
            int nextValidId = wrapperHandler.awaitNextValidId(timeout);
            state.set(IbkrConnectionState.READY);
            reconnectAttempts.set(0);
            log.info("IBKR READY. nextValidId={}", nextValidId);
        } catch (Exception exception) {
            cleanupConnectionResources();
            state.set(IbkrConnectionState.DISCONNECTED);
            scheduleReconnectIfConfigured();
            throw new IbkrConnectionException(
                    "Timeout in attesa di nextValidId dopo " + timeout, exception);
        }
    }

    private void startMessageProcessor() {
        messageProcessorThread = Thread.ofPlatform()
                .name("ibkr-message-processor")
                .daemon(false)
                .start(() -> {
                    log.debug("Thread di elaborazione messaggi IBKR avviato");
                    try {
                        while (!Thread.currentThread().isInterrupted()
                                && clientSocket != null
                                && clientSocket.isConnected()) {
                            readerSignal.waitForSignal();
                            reader.processMsgs();
                        }
                    } catch (Exception exception) {
                        if (!shutdownRequested) {
                            log.error("Errore nel thread messaggi IBKR", exception);
                            onConnectionClosed();
                        }
                    } finally {
                        log.debug("Thread di elaborazione messaggi IBKR terminato");
                    }
                });
    }

    private void onConnectionClosed() {
        if (shutdownRequested) {
            return;
        }

        IbkrConnectionState previous = state.getAndSet(IbkrConnectionState.DISCONNECTED);
        if (previous != IbkrConnectionState.DISCONNECTED
                && previous != IbkrConnectionState.RECONNECT_WAIT) {
            log.warn("Connessione IBKR chiusa; stato precedente={}", previous);
        }
        cleanupConnectionResources();
        scheduleReconnectIfConfigured();
    }

    private void scheduleReconnectIfConfigured() {
        TradingSystemProperties.ReconnectProperties reconnect =
                properties.getIbkr().getReconnect();

        if (shutdownRequested || !reconnect.isEnabled()) {
            return;
        }

        int attempt = reconnectAttempts.incrementAndGet();
        if (attempt > reconnect.getMaxAttempts()) {
            log.error("Numero massimo di tentativi di riconnessione IBKR raggiunto: {}",
                    reconnect.getMaxAttempts());
            return;
        }

        if (!reconnectScheduled.compareAndSet(false, true)) {
            return;
        }

        state.set(IbkrConnectionState.RECONNECT_WAIT);
        long delayMillis = reconnect.getDelay().toMillis();
        log.warn("Nuovo tentativo di connessione IBKR {}/{} tra {}",
                attempt, reconnect.getMaxAttempts(), reconnect.getDelay());

        reconnectFuture = reconnectExecutor.schedule(() -> {
            reconnectScheduled.set(false);
            try {
                connect();
            } catch (IbkrConnectionException exception) {
                log.error("Tentativo di riconnessione IBKR fallito: {}", exception.getMessage());
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void cancelPendingReconnect() {
        ScheduledFuture<?> future = reconnectFuture;
        if (future != null && !future.isDone()) {
            future.cancel(false);
        }
        reconnectScheduled.set(false);
    }

    private void cleanupConnectionResources() {
        EClientSocket socket = clientSocket;
        if (socket != null && socket.isConnected()) {
            socket.eDisconnect();
        }

        Thread processor = messageProcessorThread;
        if (processor != null && processor != Thread.currentThread()) {
            processor.interrupt();
        }

        clientSocket = null;
        readerSignal = null;
        reader = null;
        messageProcessorThread = null;
    }

    public EClientSocket requireClientSocket() {
        EClientSocket socket = clientSocket;
        if (state.get() != IbkrConnectionState.READY
                || socket == null
                || !socket.isConnected()) {
            throw new IllegalStateException("IBKR non è nello stato READY");
        }
        return socket;
    }

    public boolean isReady() {
        return state.get() == IbkrConnectionState.READY;
    }

    public IbkrConnectionState getState() {
        return state.get();
    }

    public void disconnect() {
        synchronized (connectionMonitor) {
            shutdownRequested = true;
            cancelPendingReconnect();
            cleanupConnectionResources();
            state.set(IbkrConnectionState.DISCONNECTED);
            log.info("Connessione IBKR terminata");
        }
    }

    @PreDestroy
    void shutdown() {
        disconnect();
        reconnectExecutor.shutdownNow();
    }
}
