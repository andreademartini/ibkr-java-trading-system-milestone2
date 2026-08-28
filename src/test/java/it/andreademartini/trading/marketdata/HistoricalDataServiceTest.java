package it.andreademartini.trading.marketdata;

import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.persistence.HistoricalBarEntity;
import it.andreademartini.trading.persistence.HistoricalBarRepository;
import it.andreademartini.trading.persistence.TradingStockEntity;
import it.andreademartini.trading.persistence.TradingStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class HistoricalDataServiceTest {

    private static final String PROVIDER = "IBKR";
    private static final String SYMBOL = "SPY";
    private static final String TIMEFRAME = "DAILY";

    private final HistoricalBarRepository repository =
            mock(HistoricalBarRepository.class);
    private final IbkrHistoricalDataClient ibkrHistoricalDataClient =
            mock(IbkrHistoricalDataClient.class);
    private final TradingStockRepository stockRepository =
            mock(TradingStockRepository.class);
    private final TradingSystemProperties properties = new TradingSystemProperties();
    private final HistoricalDataProperties historicalDataProperties =
            new HistoricalDataProperties();
    private final HistoricalDataService service =
            new HistoricalDataService(
                    repository,
                    ibkrHistoricalDataClient,
                    stockRepository,
                    properties,
                    List.of(),
                    historicalDataProperties);

    @BeforeEach
    void setUp() {
        when(stockRepository.findAllByOrderBySymbolAsc()).thenReturn(List.of());
    }

    @Test
    void returnsStoredBarsWithoutCallingProvider() {
        HistoricalBarEntity entity = entity(bar("2026-08-12", 639.25));
        when(repository.findByProviderAndSymbolAndTimeframeOrderByTimestampAsc(
                PROVIDER,
                SYMBOL,
                TIMEFRAME))
                .thenReturn(List.of(entity));

        HistoricalSeries series = service.loadOrRefresh(
                MarketDataProvider.IBKR,
                SYMBOL,
                TIMEFRAME,
                "compact",
                100,
                false);

        assertThat(series.source()).isEqualTo("DATABASE");
        assertThat(series.bars())
                .extracting(HistoricalBar::label)
                .containsExactly("2026-08-12");
        verifyNoInteractions(ibkrHistoricalDataClient);
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
        when(ibkrHistoricalDataClient.historical(any(), any(), any(), anyInt()))
                .thenReturn(List.of(downloaded));

        HistoricalSeries series = service.loadOrRefresh(
                MarketDataProvider.IBKR,
                SYMBOL,
                TIMEFRAME,
                "compact",
                100,
                false);

        assertThat(series.source()).isEqualTo("IBKR");
        assertThat(series.closePrices()).containsExactly(642.50);
        verify(repository).saveAll(any());
    }

    @Test
    void downloadsWithStockMarketDataConfiguration() {
        HistoricalBar downloaded = bar("2026-08-13", 642.50);
        HistoricalBarEntity saved = entity(downloaded);
        TradingStockEntity stock = new TradingStockEntity(
                "stock-1",
                SYMBOL,
                SYMBOL,
                "STK",
                "LSE",
                "GBP",
                "LSE",
                Instant.parse("2026-08-13T12:00:00Z"));
        when(stockRepository.findAllByOrderBySymbolAsc()).thenReturn(List.of(stock));
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
        when(ibkrHistoricalDataClient.historical(any(), any(), any(), anyInt()))
                .thenReturn(List.of(downloaded));

        service.loadOrRefresh(
                MarketDataProvider.IBKR,
                SYMBOL,
                TIMEFRAME,
                "compact",
                100,
                false);

        ArgumentCaptor<TradingSystemProperties.InstrumentProperties> captor =
                ArgumentCaptor.forClass(TradingSystemProperties.InstrumentProperties.class);
        verify(ibkrHistoricalDataClient)
                .historical(captor.capture(), any(), any(), anyInt());
        TradingSystemProperties.InstrumentProperties instrument = captor.getValue();
        assertThat(instrument.getExchange()).isEqualTo("LSE");
        assertThat(instrument.getPrimaryExchange()).isEqualTo("LSE");
        assertThat(instrument.getCurrency()).isEqualTo("GBP");
    }

    @Test
    void loadUsesDatabaseWhenRequestedDailyRangeIsCovered() {
        HistoricalDataProvider provider = mock(HistoricalDataProvider.class);
        HistoricalDataService stooqService = new HistoricalDataService(
                repository,
                ibkrHistoricalDataClient,
                stockRepository,
                properties,
                List.of(provider),
                historicalDataProperties);
        LocalDate from = LocalDate.of(2026, 8, 19);
        LocalDate to = LocalDate.of(2026, 8, 21);
        when(repository.findByProviderAndSymbolAndIntervalAndDateTimeBetweenOrderByDateTimeAsc(
                "STOOQ",
                "AAPL",
                BarInterval.DAILY,
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay().minusNanos(1)))
                .thenReturn(List.of(
                        entity("STOOQ", "AAPL", "2026-08-19", 226.00),
                        entity("STOOQ", "AAPL", "2026-08-20", 228.40),
                        entity("STOOQ", "AAPL", "2026-08-21", 229.20)));

        List<HistoricalBar> bars = stooqService.load(
                "AAPL",
                from,
                to,
                BarInterval.DAILY);

        assertThat(bars).hasSize(3);
        assertThat(bars)
                .extracting(HistoricalBar::label)
                .containsExactly("2026-08-19", "2026-08-20", "2026-08-21");
        verify(provider, never()).getHistoricalData(any(), any(), any(), any());
    }

    @Test
    void loadCallsProviderWhenABusinessDateIsMissing() {
        HistoricalDataProvider provider = mock(HistoricalDataProvider.class);
        when(provider.getProviderName()).thenReturn("STOOQ");
        when(provider.supports(BarInterval.DAILY)).thenReturn(true);
        HistoricalDataService stooqService = new HistoricalDataService(
                repository,
                ibkrHistoricalDataClient,
                stockRepository,
                properties,
                List.of(provider),
                historicalDataProperties);
        LocalDate from = LocalDate.of(2026, 8, 19);
        LocalDate to = LocalDate.of(2026, 8, 21);
        when(repository.findByProviderAndSymbolAndIntervalAndDateTimeBetweenOrderByDateTimeAsc(
                "STOOQ",
                "AAPL",
                BarInterval.DAILY,
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay().minusNanos(1)))
                .thenReturn(
                        List.of(
                                entity("STOOQ", "AAPL", "2026-08-19", 226.00),
                                entity("STOOQ", "AAPL", "2026-08-21", 229.20)),
                        List.of(
                                entity("STOOQ", "AAPL", "2026-08-19", 226.00),
                                entity("STOOQ", "AAPL", "2026-08-20", 228.40),
                                entity("STOOQ", "AAPL", "2026-08-21", 229.20)));

        List<HistoricalBar> bars = stooqService.load(
                "AAPL",
                from,
                to,
                BarInterval.DAILY);

        assertThat(bars)
                .extracting(HistoricalBar::label)
                .containsExactly("2026-08-19", "2026-08-20", "2026-08-21");
        verify(provider).getHistoricalData("AAPL", from, to, BarInterval.DAILY);
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

    private HistoricalBarEntity entity(
            String provider,
            String symbol,
            String label,
            double close) {
        return new HistoricalBarEntity(
                provider,
                symbol,
                TIMEFRAME,
                new HistoricalBar(
                        0,
                        Instant.parse(label + "T00:00:00Z"),
                        label,
                        close - 1.0,
                        close + 1.0,
                        close - 2.0,
                        close,
                        1_000),
                Instant.parse("2026-08-13T12:00:00Z"));
    }
}
