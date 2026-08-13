package it.andreademartini.trading.marketdata;

import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.persistence.HistoricalBarEntity;
import it.andreademartini.trading.persistence.HistoricalBarRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class HistoricalDataServiceTest {

    private static final String PROVIDER = "ALPHA_VANTAGE";
    private static final String SYMBOL = "SPY";
    private static final String TIMEFRAME = "DAILY";

    private final HistoricalBarRepository repository =
            mock(HistoricalBarRepository.class);
    private final AlphaVantageMarketDataClient alphaVantageClient =
            mock(AlphaVantageMarketDataClient.class);
    private final HistoricalDataService service =
            new HistoricalDataService(repository, alphaVantageClient);

    @Test
    void returnsStoredBarsWithoutCallingProvider() {
        HistoricalBarEntity entity = entity(bar("2026-08-12", 639.25));
        when(repository.findByProviderAndSymbolAndTimeframeOrderByTimestampAsc(
                PROVIDER,
                SYMBOL,
                TIMEFRAME))
                .thenReturn(List.of(entity));

        HistoricalSeries series = service.loadOrRefresh(
                MarketDataProvider.ALPHA_VANTAGE,
                SYMBOL,
                TIMEFRAME,
                "compact",
                100,
                false);

        assertThat(series.source()).isEqualTo("DATABASE");
        assertThat(series.bars())
                .extracting(HistoricalBar::label)
                .containsExactly("2026-08-12");
        verifyNoInteractions(alphaVantageClient);
    }

    @Test
    void downloadsAndStoresBarsWhenCacheIsEmpty() {
        HistoricalBar downloaded = bar("2026-08-13", 642.50);
        HistoricalBarEntity saved = entity(downloaded);
        when(repository.findByProviderAndSymbolAndTimeframeOrderByTimestampAsc(
                PROVIDER,
                SYMBOL,
                TIMEFRAME))
                .thenReturn(List.of(), List.of(saved));
        when(repository.findByProviderAndSymbolAndTimeframeAndBarLabelIn(
                PROVIDER,
                SYMBOL,
                TIMEFRAME,
                List.of("2026-08-13")))
                .thenReturn(List.of());
        when(alphaVantageClient.historical(SYMBOL, TIMEFRAME, "compact", 100))
                .thenReturn(List.of(downloaded));

        HistoricalSeries series = service.loadOrRefresh(
                MarketDataProvider.ALPHA_VANTAGE,
                SYMBOL,
                TIMEFRAME,
                "compact",
                100,
                false);

        assertThat(series.source()).isEqualTo("ALPHA_VANTAGE");
        assertThat(series.closePrices()).containsExactly(642.50);
        verify(repository).saveAll(any());
    }

    private HistoricalBarEntity entity(HistoricalBar bar) {
        return new HistoricalBarEntity(
                PROVIDER,
                SYMBOL,
                TIMEFRAME,
                bar,
                Instant.parse("2026-08-13T12:00:00Z"));
    }

    private HistoricalBar bar(String label, double close) {
        return new HistoricalBar(
                0,
                Instant.parse(label + "T00:00:00Z"),
                label,
                close - 1.0,
                close + 1.0,
                close - 2.0,
                close,
                1_000);
    }
}
