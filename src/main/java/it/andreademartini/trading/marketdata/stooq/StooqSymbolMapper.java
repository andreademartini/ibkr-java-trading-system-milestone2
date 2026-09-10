package it.andreademartini.trading.marketdata.stooq;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Locale;

@Component
public class StooqSymbolMapper {

    private final StooqProperties properties;

    public StooqSymbolMapper(StooqProperties properties) {
        this.properties = properties;
    }

    public String toStooqSymbol(String symbol) {
        String normalized = normalize(symbol);
        if (normalized.contains(".")) {
            return normalized;
        }

        String suffix = properties.getDefaultMarketSuffix();
        if (suffix == null || suffix.isBlank()) {
            return normalized;
        }
        return normalized + "." + suffix.trim().toUpperCase(Locale.ROOT);
    }

    public String toInternalSymbol(String stooqSymbol) {
        String normalized = normalize(stooqSymbol);
        String suffix = properties.getDefaultMarketSuffix();
        if (suffix == null || suffix.isBlank()) {
            return normalized;
        }

        String normalizedSuffix = "." + suffix.trim().toUpperCase(Locale.ROOT);
        if (normalized.endsWith(normalizedSuffix)
                && normalized.length() > normalizedSuffix.length()) {
            return normalized.substring(0, normalized.length() - normalizedSuffix.length());
        }
        return normalized;
    }

    public String toInternalSymbolFromCsvFile(Path csvFile) {
        if (csvFile == null || csvFile.getFileName() == null) {
            throw new IllegalArgumentException("Nome file STOOQ non determinabile");
        }

        String stooqSymbol = toStooqSymbolFromCsvFileName(
                csvFile.getFileName().toString());
        return toInternalSymbol(stooqSymbol);
    }

    public String canonicalCsvFileName(String originalFilename, String symbol) {
        String stooqSymbol = symbol == null || symbol.isBlank()
                ? toStooqSymbolFromCsvFileName(originalFilename)
                : toStooqSymbol(symbol);
        return stooqSymbol + ".csv";
    }

    public String toStooqSymbolFromCsvFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Nome file STOOQ mancante");
        }

        String fileName = Path.of(originalFilename.replace('\\', '/'))
                .getFileName()
                .toString()
                .trim();
        if (fileName.isBlank()
                || fileName.contains("..")
                || !fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException("Nome file STOOQ non valido: " + fileName);
        }

        String stem = fileName.substring(0, fileName.length() - 4);
        String stooqSymbol = stooqSymbolFromStem(stem);
        if (stooqSymbol.isBlank()) {
            throw new IllegalArgumentException("Ticker STOOQ non determinabile dal nome file");
        }
        return stooqSymbol;
    }

    private String stooqSymbolFromStem(String stem) {
        String normalized = normalize(stem);
        if (normalized.contains(".")) {
            return stripTrailingIntervalToken(normalized);
        }

        String[] parts = normalized.split("_");
        if (parts.length >= 3 && isStooqIntervalToken(parts[parts.length - 1])) {
            String symbol = join(parts, 0, parts.length - 2);
            String market = parts[parts.length - 2];
            return symbol + "." + market;
        }
        if (parts.length == 2) {
            return parts[0] + "." + parts[1];
        }
        return toStooqSymbol(normalized);
    }

    private String stripTrailingIntervalToken(String value) {
        String[] parts = value.split("_");
        if (parts.length >= 2 && isStooqIntervalToken(parts[parts.length - 1])) {
            return join(parts, 0, parts.length - 1);
        }
        return value;
    }

    private boolean isStooqIntervalToken(String value) {
        return "D".equals(value)
                || "W".equals(value)
                || "M".equals(value)
                || "H".equals(value)
                || "5".equals(value)
                || "5M".equals(value)
                || "5MIN".equals(value);
    }

    private String join(String[] parts, int fromInclusive, int toExclusive) {
        StringBuilder builder = new StringBuilder();
        for (int index = fromInclusive; index < toExclusive; index++) {
            if (index > fromInclusive) {
                builder.append('_');
            }
            builder.append(parts[index]);
        }
        return builder.toString();
    }

    private String normalize(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol obbligatorio");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
