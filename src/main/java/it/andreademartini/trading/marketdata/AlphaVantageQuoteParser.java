package it.andreademartini.trading.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AlphaVantageQuoteParser {

    private final ObjectMapper objectMapper;

    public AlphaVantageQuoteParser() {
        this.objectMapper = new ObjectMapper();
    }

    public Optional<AlphaVantageQuote> parse(String body, Instant receivedAt) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode quote = root.path("Global Quote");
            if (quote.isMissingNode() || quote.size() == 0) {
                return Optional.empty();
            }

            String symbol = quote.path("01. symbol").asText("");
            double price = parseDouble(quote.path("05. price").asText(""));
            double previousClose = parseDouble(quote.path("08. previous close").asText(""));
            LocalDate tradingDay = parseDate(quote.path("07. latest trading day").asText(""));

            if (symbol.isBlank() || price <= 0) {
                return Optional.empty();
            }

            return Optional.of(new AlphaVantageQuote(
                    symbol.trim().toUpperCase(),
                    price,
                    previousClose,
                    tradingDay,
                    receivedAt));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    public Optional<String> serviceMessage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return firstText(root, "Error Message")
                    .or(() -> firstText(root, "Note"))
                    .or(() -> firstText(root, "Information"));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    public List<HistoricalBar> parseHistorical(
            String body,
            String interval,
            int maxPoints) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String seriesKey = seriesKey(root, interval);
            if (seriesKey == null) {
                return List.of();
            }

            JsonNode series = root.path(seriesKey);
            List<HistoricalBar> bars = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = series.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode value = entry.getValue();
                double close = parseDouble(value.path("4. close").asText(""));
                if (close <= 0) {
                    continue;
                }
                bars.add(new HistoricalBar(
                        0,
                        parseTimestamp(entry.getKey()),
                        entry.getKey(),
                        parseDouble(value.path("1. open").asText("")),
                        parseDouble(value.path("2. high").asText("")),
                        parseDouble(value.path("3. low").asText("")),
                        close,
                        parseLong(value.path("5. volume").asText(""))));
            }

            List<HistoricalBar> sorted = bars.stream()
                    .sorted(Comparator.comparing(HistoricalBar::timestamp))
                    .toList();
            int fromIndex = Math.max(0, sorted.size() - Math.max(1, maxPoints));
            List<HistoricalBar> limited = sorted.subList(fromIndex, sorted.size());
            List<HistoricalBar> indexed = new ArrayList<>(limited.size());
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
            return indexed;
        } catch (IOException exception) {
            return List.of();
        }
    }

    private Optional<String> firstText(JsonNode root, String fieldName) {
        String value = root.path(fieldName).asText("");
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String seriesKey(JsonNode root, String interval) {
        String expected = interval == null || interval.equalsIgnoreCase("DAILY")
                ? "Time Series (Daily)"
                : "Time Series (" + interval + ")";
        if (root.has(expected)) {
            return expected;
        }

        Iterator<Map.Entry<String, JsonNode>> fieldNames = root.properties().iterator();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next().getKey();
            if (fieldName.startsWith("Time Series")) {
                return fieldName;
            }
        }
        return null;
    }

    private Instant parseTimestamp(String value) {
        try {
            if (value.length() == 10) {
                return LocalDate.parse(value)
                        .atStartOfDay()
                        .toInstant(ZoneOffset.UTC);
            }
            return LocalDateTime.parse(value.replace(' ', 'T'))
                    .toInstant(ZoneOffset.UTC);
        } catch (RuntimeException exception) {
            return Instant.EPOCH;
        }
    }
}
