package it.andreademartini.trading.marketdata;

import com.ib.client.Bar;
import com.ib.client.Contract;
import it.andreademartini.trading.config.MarketDataMode;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.ibkr.IbkrApiClient;
import it.andreademartini.trading.ibkr.IbkrEWrapper;
import it.andreademartini.trading.ibkr.IbkrError;
import it.andreademartini.trading.ibkr.IbkrEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class IbkrHistoricalDataClient implements IbkrEventListener {

    private static final Logger log =
            LoggerFactory.getLogger(IbkrHistoricalDataClient.class);

    private static final Set<Integer> INFORMATIONAL_CODES = Set.of(
            1101, 1102, 2104, 2106, 2158, 2174
    );
    private static final DateTimeFormatter IBKR_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
    private static final DateTimeFormatter IBKR_END_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss")
                    .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter LABEL_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Duration DELAYED_HISTORICAL_LAG = Duration.ofMinutes(30);

    private final TradingSystemProperties properties;
    private final IbkrApiClient apiClient;
    private final IbkrContractFactory contractFactory;
    private final AtomicInteger requestIdSequence = new AtomicInteger(20_000);
    private final ConcurrentHashMap<Integer, PendingHistoricalRequest> pendingRequests =
            new ConcurrentHashMap<>();

    public IbkrHistoricalDataClient(
            TradingSystemProperties properties,
            IbkrApiClient apiClient,
            IbkrContractFactory contractFactory,
            IbkrEWrapper wrapper) {
        this.properties = properties;
        this.apiClient = apiClient;
        this.contractFactory = contractFactory;
        wrapper.addListener(this);
    }

    public List<HistoricalBar> historical(
            TradingSystemProperties.InstrumentProperties instrument,
            String timeframe,
            String outputSize,
            int maxPoints) {
        return historical(instrument,timeframe,outputSize,maxPoints,false);
    }
    /** Paper callers qualify completed bars against the exchange calendar, including today's close. */
    public List<HistoricalBar> paperDaily(TradingSystemProperties.InstrumentProperties instrument,int maxPoints){
        return historical(instrument,"DAILY","compact",maxPoints,true);
    }
    private List<HistoricalBar> historical(TradingSystemProperties.InstrumentProperties instrument,String timeframe,String outputSize,int maxPoints,boolean includeToday){
        String normalizedTimeframe = normalizeTimeframe(timeframe);
        int limit = Math.max(1, Math.min(5_000, maxPoints));
        int requestId = requestIdSequence.getAndIncrement();
        PendingHistoricalRequest pending = new PendingHistoricalRequest(
                normalizedTimeframe,
                limit,
                new CopyOnWriteArrayList<>(),
                new CompletableFuture<>());
        pendingRequests.put(requestId, pending);

        String duration = duration(normalizedTimeframe, outputSize, limit);
        String barSize = barSize(normalizedTimeframe);
        String endDateTime = includeToday?"":historicalEndDateTime(normalizedTimeframe);
        Contract contract = contractFactory.create(instrument);

        try {
            apiClient.requestMarketDataType(
                    properties.getMarketData().getMode().getIbkrCode());
            apiClient.requestHistoricalData(
                    requestId,
                    contract,
                    endDateTime,
                    duration,
                    barSize,
                    "TRADES",
                    1,
                    2,
                    false);
            log.info("Richiesta storico IBKR {} reqId={} endDateTime={} duration={} barSize={} mode={}",
                    contract.symbol(),
                    requestId,
                    endDateTime.isBlank() ? "now" : endDateTime,
                    duration,
                    barSize,
                    properties.getMarketData().getMode());
            return pending.future().get(
                    properties.getIbkr().getHistoricalDataTimeout().toMillis(),
                    TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            cancelQuietly(requestId);
            throw new IllegalStateException("Richiesta storico IBKR interrotta", exception);
        } catch (TimeoutException exception) {
            cancelQuietly(requestId);
            throw new IllegalStateException(
                    "Timeout storico IBKR dopo "
                            + properties.getIbkr().getHistoricalDataTimeout(),
                    exception);
        } catch (ExecutionException exception) {
            cancelQuietly(requestId);
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Errore storico IBKR", cause);
        } finally {
            pendingRequests.remove(requestId);
        }
    }

    @Override
    public void onHistoricalData(int requestId, Bar bar) {
        PendingHistoricalRequest pending = pendingRequests.get(requestId);
        if (pending == null) {
            return;
        }
        pending.bars().add(toHistoricalBar(pending.timeframe(), bar));
    }

    @Override
    public void onHistoricalDataEnd(int requestId, String start, String end) {
        PendingHistoricalRequest pending = pendingRequests.get(requestId);
        if (pending == null) {
            return;
        }
        List<HistoricalBar> bars = pending.bars().stream()
                .sorted(Comparator.comparing(HistoricalBar::timestamp))
                .toList();
        int fromIndex = Math.max(0, bars.size() - pending.maxPoints());
        List<HistoricalBar> limited = bars.subList(fromIndex, bars.size());
        List<HistoricalBar> indexed = new java.util.ArrayList<>(limited.size());
        for (int index = 0; index < limited.size(); index++) {
            HistoricalBar bar = limited.get(index);
            indexed.add(new HistoricalBar(
                    index,
                    bar.timestamp(),
                    bar.label(),
                    bar.open(),
                    bar.high(),
                    bar.low(),
                    bar.close(),
                    bar.volume()));
        }
        pending.future().complete(List.copyOf(indexed));
        log.info("Storico IBKR completato reqId={} start={} end={} barre={}",
                requestId, start, end, indexed.size());
    }

    @Override
    public void onError(IbkrError error) {
        if (error.requestId() < 0) {
            return;
        }
        PendingHistoricalRequest pending = pendingRequests.get(error.requestId());
        if (pending == null || INFORMATIONAL_CODES.contains(error.code())) {
            return;
        }
        if (Integer.valueOf(2188).equals(error.code())) {
            pending.future().completeExceptionally(new IbkrMarketDataPermissionException(
                    "IBKR non consente lo storico API per "
                            + "questo strumento senza una sottoscrizione market data "
                            + "abilitata per API. Per AAPL servono permessi dati USA/NASDAQ "
                            + "sull'utente collegato a TWS/IB Gateway, oppure dati storici "
                            + "gia' salvati nel database."));
            return;
        }
        pending.future().completeExceptionally(new IllegalStateException(
                "Errore storico IBKR code=" + error.code() + ": " + error.message()));
    }

    private HistoricalBar toHistoricalBar(String timeframe, Bar bar) {
        Instant timestamp = parseTimestamp(bar.time());
        boolean daily = "DAILY".equals(timeframe);
        return new HistoricalBar(
                0,
                timestamp,
                label(timestamp, daily),
                bar.open(),
                bar.high(),
                bar.low(),
                bar.close(),
                bar.volume() == null ? 0L : bar.volume().longValue());
    }

    private Instant parseTimestamp(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.matches("\\d{8}")) {
            return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant();
        }
        if (value.matches("\\d+")) {
            return Instant.ofEpochSecond(Long.parseLong(value));
        }

        String normalized = value.replaceAll("\\s+", " ");
        String[] parts = normalized.split(" ");
        if (parts.length >= 2 && parts[0].matches("\\d{8}")) {
            return LocalDateTime.parse(parts[0] + " " + parts[1], IBKR_DATE_TIME)
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
        }

        throw new IllegalArgumentException("Timestamp storico IBKR non riconosciuto: " + value);
    }

    private String label(Instant timestamp, boolean daily) {
        if (daily) {
            return DateTimeFormatter.ISO_LOCAL_DATE
                    .withZone(ZoneOffset.UTC)
                    .format(timestamp);
        }
        return LABEL_DATE_TIME
                .withZone(ZoneId.systemDefault())
                .format(timestamp);
    }

    private String normalizeTimeframe(String value) {
        if (value == null || value.isBlank()) {
            return "DAILY";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String barSize(String timeframe) {
        return switch (timeframe) {
            case "1MIN" -> "1 min";
            case "5MIN" -> "5 mins";
            case "15MIN" -> "15 mins";
            case "30MIN" -> "30 mins";
            case "60MIN" -> "1 hour";
            case "DAILY" -> "1 day";
            default -> throw new IllegalArgumentException("Timeframe storico non supportato: "
                    + timeframe);
        };
    }

    private String historicalEndDateTime(String timeframe) {
        MarketDataMode mode = properties.getMarketData().getMode();
        if (mode != MarketDataMode.DELAYED && mode != MarketDataMode.DELAYED_FROZEN) {
            return "";
        }

        if ("DAILY".equals(timeframe)) {
            return IBKR_END_DATE_TIME.format(LocalDate
                    .now(ZoneOffset.UTC)
                    .minusDays(1)
                    .atTime(23, 59, 59)
                    .toInstant(ZoneOffset.UTC));
        }

        return IBKR_END_DATE_TIME.format(Instant.now().minus(DELAYED_HISTORICAL_LAG));
    }

    private String duration(String timeframe, String outputSize, int maxPoints) {
        boolean full = "full".equalsIgnoreCase(outputSize);
        if ("DAILY".equals(timeframe)) {
            return full ? "5 Y" : Math.max(1, (int) Math.ceil(maxPoints / 252.0)) + " Y";
        }
        if (full) {
            return switch (timeframe) {
                case "1MIN" -> "1 W";
                case "5MIN", "15MIN", "30MIN" -> "1 M";
                case "60MIN" -> "3 M";
                default -> "1 M";
            };
        }

        int days = Math.max(1, (int) Math.ceil(maxPoints * barSeconds(timeframe) / 86_400.0));
        if ("1MIN".equals(timeframe)) {
            days = Math.min(days, 7);
        }
        if (days <= 6) {
            return days + " D";
        }
        if (days <= 28) {
            return Math.max(1, (int) Math.ceil(days / 7.0)) + " W";
        }
        return Math.max(1, (int) Math.ceil(days / 30.0)) + " M";
    }

    private int barSeconds(String timeframe) {
        return switch (timeframe) {
            case "1MIN" -> 60;
            case "5MIN" -> 300;
            case "15MIN" -> 900;
            case "30MIN" -> 1_800;
            case "60MIN" -> 3_600;
            default -> 86_400;
        };
    }

    private void cancelQuietly(int requestId) {
        try {
            apiClient.cancelHistoricalData(requestId);
        } catch (RuntimeException exception) {
            log.debug("Impossibile cancellare storico IBKR reqId={}: {}",
                    requestId, exception.getMessage());
        }
    }

    private record PendingHistoricalRequest(
            String timeframe,
            int maxPoints,
            CopyOnWriteArrayList<HistoricalBar> bars,
            CompletableFuture<List<HistoricalBar>> future) {
    }
}
