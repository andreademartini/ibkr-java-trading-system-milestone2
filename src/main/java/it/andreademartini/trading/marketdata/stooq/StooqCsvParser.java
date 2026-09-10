package it.andreademartini.trading.marketdata.stooq;

import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.marketdata.PriceAdjustment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class StooqCsvParser {

    private static final Logger log = LoggerFactory.getLogger(StooqCsvParser.class);

    private static final String PROVIDER = "STOOQ";

    public StooqCsvParseResult parse(
            Path file,
            String symbol,
            BarInterval interval,
            PriceAdjustment priceAdjustment,
            boolean acceptZeroVolume) throws IOException {
        if (interval != BarInterval.DAILY) {
            throw new IllegalArgumentException(
                    "STOOQ CSV supporta completamente solo DAILY in questa milestone");
        }

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String header = nextNonBlankLine(reader);
            if (header == null) {
                return new StooqCsvParseResult(List.of(), 0, 0, 0);
            }

            Map<String, Integer> columns = columns(header);
            validateRequiredColumns(columns);

            List<HistoricalBar> bars = new ArrayList<>();
            int rowsRead = 0;
            int rowsRejected = 0;

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                rowsRead++;
                try {
                    bars.add(parseBar(
                            line,
                            columns,
                            symbol,
                            interval,
                            priceAdjustment,
                            acceptZeroVolume));
                } catch (RuntimeException exception) {
                    rowsRejected++;
                    log.warn("Riga STOOQ scartata file={} row={} reason={}",
                            file, rowsRead, exception.getMessage());
                }
            }

            return new StooqCsvParseResult(
                    bars,
                    rowsRead,
                    bars.size(),
                    rowsRejected);
        }
    }

    private String nextNonBlankLine(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) {
                return line;
            }
        }
        return null;
    }

    private Map<String, Integer> columns(String header) {
        List<String> names = splitCsvLine(header);
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < names.size(); index++) {
            String normalized = normalizeHeader(names.get(index));
            if (!normalized.isBlank()) {
                columns.putIfAbsent(normalized, index);
            }
        }
        return columns;
    }

    private void validateRequiredColumns(Map<String, Integer> columns) {
        for (String required : List.of("DATE", "OPEN", "HIGH", "LOW", "CLOSE", "VOLUME")) {
            if (!columns.containsKey(required)) {
                throw new IllegalArgumentException(
                        "Colonna STOOQ obbligatoria mancante: " + required);
            }
        }
    }

    private HistoricalBar parseBar(
            String line,
            Map<String, Integer> columns,
            String symbol,
            BarInterval interval,
            PriceAdjustment priceAdjustment,
            boolean acceptZeroVolume) {
        List<String> values = splitCsvLine(line);

        LocalDate date = LocalDate.parse(value(values, columns, "DATE"));
        BigDecimal open = decimal(values, columns, "OPEN");
        BigDecimal high = decimal(values, columns, "HIGH");
        BigDecimal low = decimal(values, columns, "LOW");
        BigDecimal close = decimal(values, columns, "CLOSE");
        long volume = volume(values, columns, "VOLUME");

        validate(open, high, low, close, volume, acceptZeroVolume);

        return new HistoricalBar(
                normalizeSymbol(symbol),
                date.atStartOfDay(),
                open,
                high,
                low,
                close,
                volume,
                interval,
                PROVIDER,
                priceAdjustment);
    }

    private String value(List<String> values, Map<String, Integer> columns, String name) {
        int index = columns.get(name);
        if (index >= values.size()) {
            throw new IllegalArgumentException("Valore mancante per " + name);
        }
        String value = values.get(index).trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Valore vuoto per " + name);
        }
        return value;
    }

    private BigDecimal decimal(
            List<String> values,
            Map<String, Integer> columns,
            String name) {
        return new BigDecimal(value(values, columns, name));
    }

    private long volume(
            List<String> values,
            Map<String, Integer> columns,
            String name) {
        return new BigDecimal(value(values, columns, name)).longValueExact();
    }

    private void validate(
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume,
            boolean acceptZeroVolume) {
        if (open.signum() <= 0
                || high.signum() <= 0
                || low.signum() <= 0
                || close.signum() <= 0) {
            throw new IllegalArgumentException("OHLC deve essere maggiore di zero");
        }
        if (high.compareTo(low) < 0) {
            throw new IllegalArgumentException("high minore di low");
        }
        if (high.compareTo(open) < 0 || high.compareTo(close) < 0) {
            throw new IllegalArgumentException("high minore di open/close");
        }
        if (low.compareTo(open) > 0 || low.compareTo(close) > 0) {
            throw new IllegalArgumentException("low maggiore di open/close");
        }
        if (volume < 0) {
            throw new IllegalArgumentException("volume negativo");
        }
        if (!acceptZeroVolume && volume == 0) {
            throw new IllegalArgumentException("volume zero non accettato");
        }
    }

    private List<String> splitCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        values.add(current.toString());
        return values;
    }

    private String normalizeHeader(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("<") && normalized.endsWith(">") && normalized.length() > 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        normalized = normalized
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");
        if ("VOL".equals(normalized)) {
            return "VOLUME";
        }
        return normalized;
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol obbligatorio");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
