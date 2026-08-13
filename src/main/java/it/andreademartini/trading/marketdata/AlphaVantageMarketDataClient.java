package it.andreademartini.trading.marketdata;

import it.andreademartini.trading.config.TradingSystemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class AlphaVantageMarketDataClient {

    private static final Logger log =
            LoggerFactory.getLogger(AlphaVantageMarketDataClient.class);

    private final TradingSystemProperties properties;
    private final AlphaVantageQuoteParser parser;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public AlphaVantageMarketDataClient(
            TradingSystemProperties properties,
            AlphaVantageQuoteParser parser) {
        this.properties = properties;
        this.parser = parser;
    }

    public Optional<AlphaVantageQuote> quote(String symbol) {
        TradingSystemProperties.AlphaVantageProperties alpha =
                properties.getMarketData().getAlphaVantage();

        URI uri = UriComponentsBuilder
                .fromUriString(alpha.getBaseUrl())
                .queryParam("function", "GLOBAL_QUOTE")
                .queryParam("symbol", symbol)
                .queryParam("apikey", alpha.getApiKey())
                .build()
                .toUri();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .header("Accept", "application/json")
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Alpha Vantage HTTP {} per {}", response.statusCode(), symbol);
                return Optional.empty();
            }
            return parser.parse(response.body(), Instant.now());
        } catch (IOException exception) {
            log.warn("Errore rete Alpha Vantage per {}: {}", symbol, exception.getMessage());
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    public List<HistoricalBar> historical(
            String symbol,
            String timeframe,
            String outputSize,
            int maxPoints) {
        TradingSystemProperties.AlphaVantageProperties alpha =
                properties.getMarketData().getAlphaVantage();
        if (alpha.getApiKey() == null || alpha.getApiKey().isBlank()) {
            throw new IllegalStateException("Configurare ALPHA_VANTAGE_API_KEY");
        }

        String normalizedTimeframe = normalizeTimeframe(timeframe);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(alpha.getBaseUrl())
                .queryParam("symbol", symbol)
                .queryParam("outputsize", normalizeOutputSize(outputSize))
                .queryParam("apikey", alpha.getApiKey());

        if ("DAILY".equals(normalizedTimeframe)) {
            builder.queryParam("function", "TIME_SERIES_DAILY");
        } else {
            builder.queryParam("function", "TIME_SERIES_INTRADAY")
                    .queryParam("interval", normalizedTimeframe);
        }

        HttpRequest request = HttpRequest.newBuilder(builder.build().toUri())
                .GET()
                .header("Accept", "application/json")
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Alpha Vantage HTTP " + response.statusCode() + " per " + symbol);
            }
            Optional<String> serviceMessage = parser.serviceMessage(response.body());
            if (serviceMessage.isPresent()) {
                throw new IllegalStateException(
                        "Alpha Vantage: " + sanitizeServiceMessage(
                                serviceMessage.get(),
                                alpha.getApiKey()));
            }
            List<HistoricalBar> bars =
                    parser.parseHistorical(response.body(), normalizedTimeframe, maxPoints);
            if (bars.isEmpty()) {
                throw new IllegalStateException(
                        "Alpha Vantage non ha restituito una serie storica valida. "
                                + "Controllare API key, simbolo e limiti del piano gratuito.");
            }
            return bars;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Errore rete Alpha Vantage per " + symbol + ": " + exception.getMessage(),
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Download storico Alpha Vantage interrotto", exception);
        }
    }

    private String normalizeTimeframe(String timeframe) {
        if (timeframe == null || timeframe.isBlank()
                || timeframe.equalsIgnoreCase("DAILY")) {
            return "DAILY";
        }
        return switch (timeframe.trim()) {
            case "1min", "5min", "15min", "30min", "60min" -> timeframe.trim();
            default -> throw new IllegalArgumentException(
                    "Timeframe Alpha Vantage non supportato: " + timeframe);
        };
    }

    private String normalizeOutputSize(String outputSize) {
        if (outputSize == null || outputSize.isBlank()) {
            return "compact";
        }
        String normalized = outputSize.trim().toLowerCase();
        if (!normalized.equals("compact") && !normalized.equals("full")) {
            throw new IllegalArgumentException("outputSize deve essere compact o full");
        }
        return normalized;
    }

    private String sanitizeServiceMessage(String message, String apiKey) {
        String sanitized = message;
        if (apiKey != null && !apiKey.isBlank()) {
            sanitized = sanitized.replace(apiKey, "********");
        }

        String normalized = sanitized.toLowerCase();
        if (normalized.contains("standard api rate limit")
                && normalized.contains("25 requests per day")) {
            return "limite giornaliero raggiunto: il piano gratuito consente "
                    + "25 richieste al giorno per API key. Riprova domani "
                    + "o riduci le richieste automatiche.";
        }

        return sanitized;
    }
}
