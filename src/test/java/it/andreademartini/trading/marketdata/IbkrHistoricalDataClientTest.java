package it.andreademartini.trading.marketdata;

import com.ib.client.Contract;
import it.andreademartini.trading.config.MarketDataMode;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.ibkr.IbkrApiClient;
import it.andreademartini.trading.ibkr.IbkrEWrapper;
import it.andreademartini.trading.ibkr.IbkrError;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class IbkrHistoricalDataClientTest {

    private static final DateTimeFormatter IBKR_END_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss");

    @Test
    void delayedHistoricalRequestEndsBeforeRealtimeWindow() {
        TradingSystemProperties properties = new TradingSystemProperties();
        properties.getMarketData().setMode(MarketDataMode.DELAYED);
        properties.getIbkr().setHistoricalDataTimeout(Duration.ofSeconds(1));
        IbkrApiClient apiClient = mock(IbkrApiClient.class);
        IbkrHistoricalDataClient client = new IbkrHistoricalDataClient(
                properties,
                apiClient,
                new IbkrContractFactory(),
                new IbkrEWrapper());

        doAnswer(invocation -> {
            int requestId = invocation.getArgument(0);
            client.onError(new IbkrError(requestId, 2174, "Timezone warning", ""));
            client.onHistoricalDataEnd(requestId, "", "");
            return null;
        }).when(apiClient).requestHistoricalData(
                anyInt(),
                any(Contract.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyInt(),
                anyInt(),
                anyBoolean());

        List<HistoricalBar> bars = client.historical(
                properties.getMarketData().getInstruments().getFirst(),
                "1min",
                "compact",
                10);

        assertThat(bars).isEmpty();
        verify(apiClient).requestMarketDataType(MarketDataMode.DELAYED.getIbkrCode());

        ArgumentCaptor<String> endDateTime = ArgumentCaptor.forClass(String.class);
        verify(apiClient).requestHistoricalData(
                anyInt(),
                any(Contract.class),
                endDateTime.capture(),
                anyString(),
                anyString(),
                eq("TRADES"),
                eq(1),
                eq(2),
                eq(false));

        Instant requestedEnd = LocalDateTime
                .parse(endDateTime.getValue(), IBKR_END_DATE_TIME)
                .atZone(ZoneOffset.UTC)
                .toInstant();
        assertThat(requestedEnd).isBefore(Instant.now().minus(Duration.ofMinutes(15)));
    }

    @Test
    void delayedDailyHistoricalRequestEndsOnPreviousUtcDay() {
        TradingSystemProperties properties = new TradingSystemProperties();
        properties.getMarketData().setMode(MarketDataMode.DELAYED);
        properties.getIbkr().setHistoricalDataTimeout(Duration.ofSeconds(1));
        IbkrApiClient apiClient = mock(IbkrApiClient.class);
        IbkrHistoricalDataClient client = new IbkrHistoricalDataClient(
                properties,
                apiClient,
                new IbkrContractFactory(),
                new IbkrEWrapper());

        doAnswer(invocation -> {
            client.onHistoricalDataEnd(invocation.getArgument(0), "", "");
            return null;
        }).when(apiClient).requestHistoricalData(
                anyInt(),
                any(Contract.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyInt(),
                anyInt(),
                anyBoolean());

        List<HistoricalBar> bars = client.historical(
                properties.getMarketData().getInstruments().getFirst(),
                "DAILY",
                "compact",
                100);

        assertThat(bars).isEmpty();

        ArgumentCaptor<String> endDateTime = ArgumentCaptor.forClass(String.class);
        verify(apiClient).requestHistoricalData(
                anyInt(),
                any(Contract.class),
                endDateTime.capture(),
                anyString(),
                eq("1 day"),
                eq("TRADES"),
                eq(1),
                eq(2),
                eq(false));

        Instant requestedEnd = LocalDateTime
                .parse(endDateTime.getValue(), IBKR_END_DATE_TIME)
                .atZone(ZoneOffset.UTC)
                .toInstant();
        Instant todayStart = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
        assertThat(requestedEnd).isBefore(todayStart);
    }

    @Test
    void historicalPermissionErrorExplainsMissingIbkrMarketDataSubscription() {
        TradingSystemProperties properties = new TradingSystemProperties();
        properties.getMarketData().setMode(MarketDataMode.DELAYED);
        properties.getIbkr().setHistoricalDataTimeout(Duration.ofSeconds(1));
        IbkrApiClient apiClient = mock(IbkrApiClient.class);
        IbkrHistoricalDataClient client = new IbkrHistoricalDataClient(
                properties,
                apiClient,
                new IbkrContractFactory(),
                new IbkrEWrapper());

        doAnswer(invocation -> {
            int requestId = invocation.getArgument(0);
            client.onError(new IbkrError(
                    requestId,
                    2188,
                    "Up-to-the-second historical data requires additional subscription for the API.",
                    ""));
            return null;
        }).when(apiClient).requestHistoricalData(
                anyInt(),
                any(Contract.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyInt(),
                anyInt(),
                anyBoolean());

        assertThatThrownBy(() -> client.historical(
                properties.getMarketData().getInstruments().getFirst(),
                "DAILY",
                "compact",
                100))
                .isInstanceOf(IbkrMarketDataPermissionException.class)
                .hasMessageContaining("sottoscrizione market data");
    }
}
