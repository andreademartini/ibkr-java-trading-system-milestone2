package it.andreademartini.trading.marketdata;

import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.persistence.HistoricalBarEntity;
import it.andreademartini.trading.persistence.HistoricalBarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HistoricalDataService {

    private final HistoricalBarRepository repository;
    private final AlphaVantageMarketDataClient alphaVantageClient;

    public HistoricalDataService(
            HistoricalBarRepository repository,
            AlphaVantageMarketDataClient alphaVantageClient) {
        this.repository = repository;
        this.alphaVantageClient = alphaVantageClient;
    }

    @Transactional
    public HistoricalSeries loadOrRefresh(
            MarketDataProvider provider,
            String symbol,
            String timeframe,
            String outputSize,
            int maxPoints,
            boolean refresh) {
        if (provider != MarketDataProvider.ALPHA_VANTAGE) {
            throw new IllegalArgumentException(
                    "Download storico supportato solo con provider ALPHA_VANTAGE");
        }

        String providerName = provider.name();
        StoredHistoricalSeries stored = loadStored(
                providerName,
                symbol,
                timeframe,
                maxPoints);
        if (!refresh && !stored.bars().isEmpty()) {
            return new HistoricalSeries(
                    provider,
                    symbol,
                    timeframe,
                    outputSize,
                    stored.bars(),
                    "DATABASE",
                    stored.loadedAt(),
                    null);
        }

        try {
            List<HistoricalBar> downloaded = alphaVantageClient.historical(
                    symbol,
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
                    provider,
                    symbol,
                    timeframe,
                    outputSize,
                    updated.bars(),
                    "ALPHA_VANTAGE",
                    updated.loadedAt(),
                    null);
        } catch (RuntimeException exception) {
            if (!stored.bars().isEmpty()) {
                return new HistoricalSeries(
                        provider,
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
}
