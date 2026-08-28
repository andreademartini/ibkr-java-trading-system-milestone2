package it.andreademartini.trading.marketdata;

import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.persistence.HistoricalBarEntity;
import it.andreademartini.trading.persistence.HistoricalBarRepository;
import it.andreademartini.trading.persistence.TradingStockEntity;
import it.andreademartini.trading.persistence.TradingStockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HistoricalDataService {

    private final HistoricalBarRepository repository;
    private final IbkrHistoricalDataClient ibkrHistoricalDataClient;
    private final TradingStockRepository stockRepository;
    private final TradingSystemProperties properties;
    private final List<HistoricalDataProvider> historicalDataProviders;
    private final HistoricalDataProperties historicalDataProperties;

    @Autowired
    public HistoricalDataService(
            HistoricalBarRepository repository,
            IbkrHistoricalDataClient ibkrHistoricalDataClient,
            TradingStockRepository stockRepository,
            TradingSystemProperties properties,
            List<HistoricalDataProvider> historicalDataProviders,
            HistoricalDataProperties historicalDataProperties) {
        this.repository = repository;
        this.ibkrHistoricalDataClient = ibkrHistoricalDataClient;
        this.stockRepository = stockRepository;
        this.properties = properties;
        this.historicalDataProviders = historicalDataProviders == null
                ? List.of()
                : List.copyOf(historicalDataProviders);
        this.historicalDataProperties = historicalDataProperties == null
                ? new HistoricalDataProperties()
                : historicalDataProperties;
    }

    HistoricalDataService(
            HistoricalBarRepository repository,
            IbkrHistoricalDataClient ibkrHistoricalDataClient,
            TradingStockRepository stockRepository,
            TradingSystemProperties properties) {
        this(
                repository,
                ibkrHistoricalDataClient,
                stockRepository,
                properties,
                List.of(),
                new HistoricalDataProperties());
    }

    @Transactional
    public HistoricalSeries loadOrRefresh(
            MarketDataProvider provider,
            String symbol,
            String timeframe,
            String outputSize,
            int maxPoints,
            boolean refresh) {
        MarketDataProvider resolvedProvider = MarketDataProvider.IBKR;
        String providerName = resolvedProvider.name();
        StoredHistoricalSeries stored = loadStored(
                providerName,
                symbol,
                timeframe,
                maxPoints);
        if (!refresh && !stored.bars().isEmpty()) {
            return new HistoricalSeries(
                    resolvedProvider,
                    symbol,
                    timeframe,
                    outputSize,
                    stored.bars(),
                    "DATABASE",
                    stored.loadedAt(),
                    null);
        }

        try {
            List<HistoricalBar> downloaded = ibkrHistoricalDataClient.historical(
                    instrument(symbol),
                    timeframe,
                    outputSize,
                    maxPoints);
            saveDownloaded(providerName, symbol, timeframe, downloaded);
            StoredHistoricalSeries updated = loadStored(
                    providerName,
                    symbol,
                    timeframe,
                    maxPoints);
            return new HistoricalSeries(
                    resolvedProvider,
                    symbol,
                    timeframe,
                    outputSize,
                    updated.bars(),
                    "IBKR",
                    updated.loadedAt(),
                    null);
        } catch (RuntimeException exception) {
            if (!stored.bars().isEmpty()) {
                return new HistoricalSeries(
                        resolvedProvider,
                        symbol,
                        timeframe,
                        outputSize,
                        stored.bars(),
                        "DATABASE",
                        stored.loadedAt(),
                        "Aggiornamento fallito: " + exception.getMessage());
            }
            throw exception;
        }
    }

    @Transactional
    public List<HistoricalBar> load(
            String symbol,
            LocalDate from,
            LocalDate to,
            BarInterval interval) {
        String normalizedSymbol = normalizeSymbol(symbol);
        BarInterval resolvedInterval = interval == null ? BarInterval.DAILY : interval;
        validateDateRange(from, to);

        String providerName = historicalProviderName();
        List<HistoricalBar> stored = loadStored(
                providerName,
                normalizedSymbol,
                resolvedInterval,
                from,
                to);
        if (historicalDataProperties.isCacheEnabled()
                && hasRequestedCoverage(stored, from, to, resolvedInterval)) {
            return stored;
        }

        provider(providerName)
                .filter(provider -> provider.supports(resolvedInterval))
                .ifPresent(provider -> provider.getHistoricalData(
                        normalizedSymbol,
                        from,
                        to,
                        resolvedInterval));

        return loadStored(
                providerName,
                normalizedSymbol,
                resolvedInterval,
                from,
                to);
    }

    @Transactional(readOnly = true)
    public Optional<HistoricalBar> latestStoredPreviousDailyBar(String symbol) {
        String normalized = normalizeSymbol(symbol);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        Optional<HistoricalBar> configuredProviderBar = latestStoredPreviousDailyBar(
                historicalProviderName(),
                normalized,
                today);
        if (configuredProviderBar.isPresent()) {
            return configuredProviderBar;
        }

        List<HistoricalBarEntity> bars = repository
                .findByProviderAndSymbolAndTimeframeOrderByTimestampAsc(
                        MarketDataProvider.IBKR.name(),
                        normalized,
                        "DAILY");
        for (int index = bars.size() - 1; index >= 0; index--) {
            HistoricalBar bar = bars.get(index).toHistoricalBar(0);
            LocalDate barDate = bar.timestamp().atZone(ZoneOffset.UTC).toLocalDate();
            if (barDate.isBefore(today)) {
                return Optional.of(bar);
            }
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public HistoricalDataStatus status(String symbol, BarInterval interval) {
        String normalizedSymbol = normalizeSymbol(symbol);
        BarInterval resolvedInterval = interval == null ? BarInterval.DAILY : interval;
        String providerName = historicalProviderName();

        LocalDate firstDate = repository
                .findFirstByProviderAndSymbolAndIntervalOrderByDateTimeAsc(
                        providerName,
                        normalizedSymbol,
                        resolvedInterval)
                .map(HistoricalBarEntity::getDateTime)
                .map(LocalDateTime::toLocalDate)
                .orElse(null);
        LocalDate lastDate = repository
                .findFirstByProviderAndSymbolAndIntervalOrderByDateTimeDesc(
                        providerName,
                        normalizedSymbol,
                        resolvedInterval)
                .map(HistoricalBarEntity::getDateTime)
                .map(LocalDateTime::toLocalDate)
                .orElse(null);
        long bars = repository.countByProviderAndSymbolAndInterval(
                providerName,
                normalizedSymbol,
                resolvedInterval);

        return new HistoricalDataStatus(
                normalizedSymbol,
                providerName,
                resolvedInterval,
                firstDate,
                lastDate,
                bars);
    }

    private Optional<HistoricalBar> latestStoredPreviousDailyBar(
            String provider,
            String symbol,
            LocalDate today) {
        List<HistoricalBarEntity> bars = repository
                .findByProviderAndSymbolAndIntervalAndDateTimeBetweenOrderByDateTimeAsc(
                        provider,
                        symbol,
                        BarInterval.DAILY,
                        LocalDate.of(1900, 1, 1).atStartOfDay(),
                        today.minusDays(1).plusDays(1).atStartOfDay().minusNanos(1));
        if (bars.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(bars.get(bars.size() - 1).toHistoricalBar(0));
    }

    private List<HistoricalBar> loadStored(
            String provider,
            String symbol,
            BarInterval interval,
            LocalDate from,
            LocalDate to) {
        List<HistoricalBarEntity> entities = repository
                .findByProviderAndSymbolAndIntervalAndDateTimeBetweenOrderByDateTimeAsc(
                        provider,
                        symbol,
                        interval,
                        from.atStartOfDay(),
                        to.plusDays(1).atStartOfDay().minusNanos(1));
        return indexed(entities);
    }

    private List<HistoricalBar> indexed(List<HistoricalBarEntity> entities) {
        List<HistoricalBar> bars = new ArrayList<>(entities.size());
        for (int index = 0; index < entities.size(); index++) {
            bars.add(entities.get(index).toHistoricalBar(index));
        }
        return List.copyOf(bars);
    }

    private boolean hasRequestedCoverage(
            List<HistoricalBar> bars,
            LocalDate from,
            LocalDate to,
            BarInterval interval) {
        if (bars.isEmpty()) {
            return false;
        }
        if (interval != BarInterval.DAILY) {
            return true;
        }

        Map<LocalDate, HistoricalBar> byDate = bars.stream()
                .collect(Collectors.toMap(
                        bar -> bar.dateTime().toLocalDate(),
                        Function.identity(),
                        (first, ignored) -> first));

        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            if (isWeekday(cursor) && !byDate.containsKey(cursor)) {
                return false;
            }
            cursor = cursor.plusDays(1);
        }
        return true;
    }

    private boolean isWeekday(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    private Optional<HistoricalDataProvider> provider(String providerName) {
        return historicalDataProviders.stream()
                .filter(provider -> provider.getProviderName().equalsIgnoreCase(providerName))
                .findFirst();
    }

    private String historicalProviderName() {
        return historicalDataProperties.getProvider().trim().toUpperCase(Locale.ROOT);
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from e to obbligatori");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from non puo' essere successivo a to");
        }
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol obbligatorio");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private TradingSystemProperties.InstrumentProperties instrument(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        return configuredStockInstruments().stream()
                .filter(instrument -> normalized.equals(
                        instrument.getSymbol().trim().toUpperCase(Locale.ROOT)))
                .findFirst()
                .or(() -> properties.getMarketData().getInstruments().stream()
                        .filter(instrument -> normalized.equals(
                                instrument.getSymbol().trim().toUpperCase(Locale.ROOT)))
                        .findFirst())
                .orElseGet(() -> {
                    TradingSystemProperties.InstrumentProperties instrument =
                            new TradingSystemProperties.InstrumentProperties();
                    instrument.setSymbol(normalized);
                    return instrument;
                });
    }

    private List<TradingSystemProperties.InstrumentProperties> configuredStockInstruments() {
        List<TradingStockEntity> stocks = stockRepository.findAllByOrderBySymbolAsc();
        if (stocks == null || stocks.isEmpty()) {
            return List.of();
        }
        return stocks.stream()
                .map(this::instrumentFromStock)
                .toList();
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

    private StoredHistoricalSeries loadStored(
            String provider,
            String symbol,
            String timeframe,
            int maxPoints) {
        List<HistoricalBarEntity> all = repository
                .findByProviderAndSymbolAndTimeframeOrderByTimestampAsc(
                        provider,
                        symbol,
                        timeframe);
        if (all.isEmpty()) {
            return new StoredHistoricalSeries(List.of(), null);
        }

        int fromIndex = Math.max(0, all.size() - Math.max(1, maxPoints));
        List<HistoricalBarEntity> limited = all.subList(fromIndex, all.size());
        List<HistoricalBar> bars = new ArrayList<>(limited.size());
        for (int index = 0; index < limited.size(); index++) {
            bars.add(limited.get(index).toHistoricalBar(index));
        }

        Instant loadedAt = limited.stream()
                .map(HistoricalBarEntity::getFetchedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new StoredHistoricalSeries(bars, loadedAt);
    }

    private void saveDownloaded(
            String provider,
            String symbol,
            String timeframe,
            List<HistoricalBar> bars) {
        if (bars.isEmpty()) {
            return;
        }

        Instant fetchedAt = Instant.now();
        List<String> labels = bars.stream()
                .map(HistoricalBar::label)
                .toList();
        Map<String, HistoricalBarEntity> existing = repository
                .findByProviderAndSymbolAndTimeframeAndBarLabelIn(
                        provider,
                        symbol,
                        timeframe,
                        labels)
                .stream()
                .collect(Collectors.toMap(
                        HistoricalBarEntity::getBarLabel,
                        Function.identity()));

        List<HistoricalBarEntity> changed = new ArrayList<>(bars.size());
        for (HistoricalBar bar : bars) {
            HistoricalBarEntity entity = existing.get(bar.label());
            if (entity == null) {
                entity = new HistoricalBarEntity(
                        provider,
                        symbol,
                        timeframe,
                        bar,
                        fetchedAt);
            } else {
                entity.updateFrom(bar, fetchedAt);
            }
            changed.add(entity);
        }
        repository.saveAll(changed);
    }

    private record StoredHistoricalSeries(
            List<HistoricalBar> bars,
            Instant loadedAt) {
    }

    public record HistoricalDataStatus(
            String symbol,
            String provider,
            BarInterval interval,
            LocalDate firstDate,
            LocalDate lastDate,
            long bars) {
    }
}
