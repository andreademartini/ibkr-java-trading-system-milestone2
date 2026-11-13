package it.andreademartini.trading.marketdata;

import com.ib.client.Contract;
import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.ibkr.IbkrApiClient;
import it.andreademartini.trading.ibkr.IbkrEWrapper;
import it.andreademartini.trading.ibkr.IbkrEventListener;
import it.andreademartini.trading.persistence.TradingStockEntity;
import it.andreademartini.trading.persistence.TradingStockRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MarketDataService implements IbkrEventListener {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    private final TradingSystemProperties properties;
    private final IbkrApiClient apiClient;
    private final IbkrContractFactory contractFactory;
    private final TradingStockRepository stockRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final AtomicInteger requestIdSequence = new AtomicInteger(1_000);
    private final Map<Integer, String> symbolByRequestId = new ConcurrentHashMap<>();
    private final Map<String, Integer> requestIdBySymbol = new ConcurrentHashMap<>();
    private final Map<String, MarketSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<String, TradingSystemProperties.InstrumentProperties> instruments =
            new ConcurrentHashMap<>();
    private MarketDataProvider activeProvider;
    @org.springframework.beans.factory.annotation.Autowired private it.andreademartini.trading.monitor.ExecutionMonitor executionMonitor;

    public MarketDataService(
            TradingSystemProperties properties,
            IbkrApiClient apiClient,
            IbkrContractFactory contractFactory,
            TradingStockRepository stockRepository,
            ApplicationEventPublisher eventPublisher,
            IbkrEWrapper wrapper) {
        this.properties = properties;
        this.apiClient = apiClient;
        this.contractFactory = contractFactory;
        this.stockRepository = stockRepository;
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
        activeProvider = MarketDataProvider.IBKR;

        apiClient.requestMarketDataType(
                properties.getMarketData().getMode().getIbkrCode());

        for (TradingSystemProperties.InstrumentProperties instrument : configuredInstruments()) {
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

        if(executionMonitor!=null)executionMonitor.register(requestId,contract);
        apiClient.requestMarketData(requestId, contract);
        log.info("Sottoscrizione market data richiesta: {} reqId={} mode={}",
                symbol, requestId, properties.getMarketData().getMode());
        return requestId;
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
        String normalized = normalize(symbol);
        TradingSystemProperties.InstrumentProperties configured = configuredInstruments().stream()
                .filter(item -> normalize(item.getSymbol()).equals(normalized))
                .findFirst()
                .orElse(null);
        if (configured != null) {
            instruments.put(normalized, configured);
            return configured;
        }

        TradingSystemProperties.InstrumentProperties instrument = instruments.get(normalized);
        if (instrument == null) {
            throw new IllegalArgumentException("Stock non configurata: " + symbol);
        }
        return instrument;
    }

    @PreDestroy
    void stop() {
        stopActiveProvider();
    }

    private synchronized void stopActiveProvider() {
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

    private List<TradingSystemProperties.InstrumentProperties> configuredInstruments() {
        List<TradingStockEntity> stocks = stockRepository.findAllByOrderBySymbolAsc();
        if (stocks != null && !stocks.isEmpty()) {
            return stocks.stream()
                    .map(this::instrumentFromStock)
                    .toList();
        }
        return properties.getMarketData().getInstruments();
    }

    private TradingSystemProperties.InstrumentProperties instrumentFromStock(
            TradingStockEntity stock) {
        TradingSystemProperties.InstrumentProperties instrument =
                new TradingSystemProperties.InstrumentProperties();
        instrument.setSymbol(stock.getSymbol());
        instrument.setSecurityType(stock.getSecurityType());
        instrument.setExchange(stock.getExchange());
        instrument.setCurrency(stock.getCurrency());
        instrument.setPrimaryExchange(stock.getPrimaryExchange());
        return instrument;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
