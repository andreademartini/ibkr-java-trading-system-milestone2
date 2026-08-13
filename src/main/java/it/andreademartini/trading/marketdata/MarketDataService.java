package it.andreademartini.trading.marketdata;

import com.ib.client.Contract;
import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.ibkr.IbkrApiClient;
import it.andreademartini.trading.ibkr.IbkrEWrapper;
import it.andreademartini.trading.ibkr.IbkrEventListener;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MarketDataService implements IbkrEventListener {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    private final TradingSystemProperties properties;
    private final IbkrApiClient apiClient;
    private final AlphaVantageMarketDataClient alphaVantageClient;
    private final IbkrContractFactory contractFactory;
    private final ApplicationEventPublisher eventPublisher;

    private final AtomicInteger requestIdSequence = new AtomicInteger(1_000);
    private final Map<Integer, String> symbolByRequestId = new ConcurrentHashMap<>();
    private final Map<String, Integer> requestIdBySymbol = new ConcurrentHashMap<>();
    private final Map<String, MarketSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<String, TradingSystemProperties.InstrumentProperties> instruments =
            new ConcurrentHashMap<>();
    private final ScheduledExecutorService alphaVantageExecutor =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "alpha-vantage-market-data");
                thread.setDaemon(true);
                return thread;
            });
    private ScheduledFuture<?> alphaVantagePolling;
    private MarketDataProvider activeProvider;

    public MarketDataService(
            TradingSystemProperties properties,
            IbkrApiClient apiClient,
            AlphaVantageMarketDataClient alphaVantageClient,
            IbkrContractFactory contractFactory,
            ApplicationEventPublisher eventPublisher,
            IbkrEWrapper wrapper) {
        this.properties = properties;
        this.apiClient = apiClient;
        this.alphaVantageClient = alphaVantageClient;
        this.contractFactory = contractFactory;
        this.eventPublisher = eventPublisher;
        wrapper.addListener(this);

        for (TradingSystemProperties.InstrumentProperties instrument
                : properties.getMarketData().getInstruments()) {
            instruments.put(normalize(instrument.getSymbol()), instrument);
        }
    }

    public synchronized void start() {
        if (!properties.getMarketData().isEnabled()) {
            log.warn("Market data disabilitato");
            return;
        }

        MarketDataProvider provider = properties.getMarketData().getProvider();
        if (activeProvider == provider) {
            return;
        }

        stopActiveProvider();
        activeProvider = provider;

        if (provider == MarketDataProvider.MANUAL) {
            startManualProvider();
            return;
        }

        if (provider == MarketDataProvider.ALPHA_VANTAGE) {
            startAlphaVantagePolling();
            return;
        }

        apiClient.requestMarketDataType(
                properties.getMarketData().getMode().getIbkrCode());

        for (TradingSystemProperties.InstrumentProperties instrument
                : properties.getMarketData().getInstruments()) {
            subscribe(instrument);
        }
    }

    public synchronized void restart() {
        stopActiveProvider();
        start();
    }

    public int subscribe(TradingSystemProperties.InstrumentProperties instrument) {
        String symbol = normalize(instrument.getSymbol());
        Integer existing = requestIdBySymbol.get(symbol);
        if (existing != null) {
            return existing;
        }

        int requestId = requestIdSequence.getAndIncrement();
        Contract contract = contractFactory.create(instrument);

        instruments.put(symbol, instrument);
        snapshots.putIfAbsent(symbol, MarketSnapshot.empty(symbol));
        symbolByRequestId.put(requestId, symbol);
        requestIdBySymbol.put(symbol, requestId);

        MarketDataProvider provider = properties.getMarketData().getProvider();
        if (provider == MarketDataProvider.ALPHA_VANTAGE
                || provider == MarketDataProvider.MANUAL) {
            log.info("Sottoscrizione market data {} registrata: {} reqId={}",
                    provider, symbol, requestId);
            return requestId;
        }

        apiClient.requestMarketData(requestId, contract);
        log.info("Sottoscrizione market data richiesta: {} reqId={} mode={}",
                symbol, requestId, properties.getMarketData().getMode());
        return requestId;
    }

    public MarketSnapshot publishManualPrice(String symbol, double price) {
        if (price <= 0) {
            throw new IllegalArgumentException("Il prezzo manuale deve essere positivo");
        }

        String normalizedSymbol = normalize(symbol);
        Instant now = Instant.now();
        MarketSnapshot updated = snapshots.compute(normalizedSymbol, (key, previous) -> {
            MarketSnapshot value = previous == null
                    ? MarketSnapshot.empty(normalizedSymbol)
                    : previous;
            return new MarketSnapshot(
                    normalizedSymbol,
                    value.bid(),
                    value.ask(),
                    price,
                    price,
                    Math.max(value.high(), price),
                    value.low() <= 0 ? price : Math.min(value.low(), price),
                    now);
        });

        eventPublisher.publishEvent(
                new MarketPriceEvent(
                        normalizedSymbol,
                        MarketPriceType.LAST,
                        price,
                        updated,
                        now));
        log.info("Market data manuale {} LAST={}", normalizedSymbol, price);
        return updated;
    }

    @Override
    public void onTickPrice(int requestId, int field, double price) {
        if (price <= 0) {
            return;
        }

        String symbol = symbolByRequestId.get(requestId);
        if (symbol == null) {
            return;
        }

        MarketPriceType type = mapPriceType(field);
        if (type == MarketPriceType.UNKNOWN) {
            return;
        }

        Instant now = Instant.now();
        MarketSnapshot updated = snapshots.compute(symbol, (key, previous) -> {
            MarketSnapshot value = previous == null
                    ? MarketSnapshot.empty(symbol)
                    : previous;
            return switch (type) {
                case BID -> new MarketSnapshot(symbol, price, value.ask(), value.last(),
                        value.close(), value.high(), value.low(), now);
                case ASK -> new MarketSnapshot(symbol, value.bid(), price, value.last(),
                        value.close(), value.high(), value.low(), now);
                case LAST -> new MarketSnapshot(symbol, value.bid(), value.ask(), price,
                        value.close(), value.high(), value.low(), now);
                case CLOSE -> new MarketSnapshot(symbol, value.bid(), value.ask(), value.last(),
                        price, value.high(), value.low(), now);
                case HIGH -> new MarketSnapshot(symbol, value.bid(), value.ask(), value.last(),
                        value.close(), price, value.low(), now);
                case LOW -> new MarketSnapshot(symbol, value.bid(), value.ask(), value.last(),
                        value.close(), value.high(), price, now);
                default -> value;
            };
        });

        eventPublisher.publishEvent(
                new MarketPriceEvent(symbol, type, price, updated, now));

        log.trace("Market data {} {}={}", symbol, type, price);
    }

    public Optional<MarketSnapshot> snapshot(String symbol) {
        return Optional.ofNullable(snapshots.get(normalize(symbol)));
    }

    public Collection<MarketSnapshot> snapshots() {
        return snapshots.values().stream()
                .sorted(Comparator.comparing(MarketSnapshot::symbol))
                .toList();
    }

    public TradingSystemProperties.InstrumentProperties requireInstrument(String symbol) {
        TradingSystemProperties.InstrumentProperties instrument =
                instruments.get(normalize(symbol));
        if (instrument == null) {
            throw new IllegalArgumentException(
                    "Strumento non configurato in trading.market-data.instruments: " + symbol);
        }
        return instrument;
    }

    @PreDestroy
    void stop() {
        stopActiveProvider();
        alphaVantageExecutor.shutdownNow();
    }

    private synchronized void stopActiveProvider() {
        if (alphaVantagePolling != null) {
            alphaVantagePolling.cancel(true);
            alphaVantagePolling = null;
        }

        if (activeProvider == MarketDataProvider.IBKR) {
            for (Integer requestId : requestIdBySymbol.values()) {
                try {
                    apiClient.cancelMarketData(requestId);
                } catch (RuntimeException exception) {
                    log.debug("Impossibile cancellare market data reqId={}: {}",
                            requestId, exception.getMessage());
                }
            }
        }

        symbolByRequestId.clear();
        requestIdBySymbol.clear();
        activeProvider = null;
    }

    private MarketPriceType mapPriceType(int field) {
        return switch (field) {
            case 1, 66 -> MarketPriceType.BID;
            case 2, 67 -> MarketPriceType.ASK;
            case 4, 68 -> MarketPriceType.LAST;
            case 6, 72 -> MarketPriceType.HIGH;
            case 7, 73 -> MarketPriceType.LOW;
            case 9, 75 -> MarketPriceType.CLOSE;
            default -> MarketPriceType.UNKNOWN;
        };
    }

    private void startAlphaVantagePolling() {
        String apiKey = properties.getMarketData().getAlphaVantage().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Alpha Vantage disabilitato: configurare ALPHA_VANTAGE_API_KEY");
            return;
        }

        for (TradingSystemProperties.InstrumentProperties instrument
                : properties.getMarketData().getInstruments()) {
            subscribe(instrument);
        }

        long intervalMillis = Math.max(
                Duration.ofHours(1).toMillis(),
                properties.getMarketData().getAlphaVantage()
                        .getPollingInterval()
                        .toMillis());
        alphaVantagePolling = alphaVantageExecutor.scheduleWithFixedDelay(
                this::pollAlphaVantage,
                0,
                intervalMillis,
                TimeUnit.MILLISECONDS);
        log.info("Polling Alpha Vantage avviato ogni {} ms", intervalMillis);
    }

    private void startManualProvider() {
        for (TradingSystemProperties.InstrumentProperties instrument
                : properties.getMarketData().getInstruments()) {
            subscribe(instrument);
        }
        log.info("Market data manuale avviato");
    }

    private void pollAlphaVantage() {
        for (String symbol : instruments.keySet()) {
            try {
                alphaVantageClient.quote(symbol)
                        .ifPresent(this::applyAlphaVantageQuote);
            } catch (RuntimeException exception) {
                log.warn("Polling Alpha Vantage fallito per {}: {}",
                        symbol, exception.getMessage());
            }
        }
    }

    private void applyAlphaVantageQuote(AlphaVantageQuote quote) {
        String symbol = normalize(quote.symbol());
        Instant now = quote.receivedAt();
        MarketSnapshot updated = snapshots.compute(symbol, (key, previous) -> {
            MarketSnapshot value = previous == null
                    ? MarketSnapshot.empty(symbol)
                    : previous;
            return new MarketSnapshot(
                    symbol,
                    value.bid(),
                    value.ask(),
                    quote.price(),
                    quote.previousClose(),
                    value.high(),
                    value.low(),
                    now);
        });

        eventPublisher.publishEvent(
                new MarketPriceEvent(symbol, MarketPriceType.LAST, quote.price(), updated, now));
        log.debug("Market data Alpha Vantage {} LAST={}", symbol, quote.price());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
