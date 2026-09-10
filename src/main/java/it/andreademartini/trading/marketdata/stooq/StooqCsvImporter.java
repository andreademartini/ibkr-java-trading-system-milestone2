package it.andreademartini.trading.marketdata.stooq;

import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.marketdata.PriceAdjustment;
import it.andreademartini.trading.persistence.HistoricalBarEntity;
import it.andreademartini.trading.persistence.HistoricalBarRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StooqCsvImporter {

    public static final String PROVIDER = "STOOQ";

    private static final Logger log = LoggerFactory.getLogger(StooqCsvImporter.class);

    private final HistoricalBarRepository repository;
    private final StooqCsvParser parser;
    private final StooqProperties properties;
    private final StooqSymbolMapper symbolMapper;

    public StooqCsvImporter(
            HistoricalBarRepository repository,
            StooqCsvParser parser,
            StooqProperties properties,
            StooqSymbolMapper symbolMapper) {
        this.repository = repository;
        this.parser = parser;
        this.properties = properties;
        this.symbolMapper = symbolMapper;
    }

    @Transactional
    public StooqCsvImportReport importFile(
            Path file,
            BarInterval interval) {
        return importFile(file, interval, properties.getPriceAdjustment());
    }

    @Transactional
    public StooqCsvImportReport importFile(
            Path file,
            BarInterval interval,
            PriceAdjustment priceAdjustment) {
        return importFile(
                symbolMapper.toInternalSymbolFromCsvFile(file),
                file,
                interval,
                priceAdjustment);
    }

    @Transactional
    public StooqCsvImportReport importFile(
            String symbol,
            Path file,
            BarInterval interval) {
        return importFile(symbol, file, interval, properties.getPriceAdjustment());
    }

    @Transactional
    public StooqCsvImportReport importFile(
            String symbol,
            Path file,
            BarInterval interval,
            PriceAdjustment priceAdjustment) {
        String normalizedSymbol = normalizeSymbol(symbol);
        Path normalizedFile = file.toAbsolutePath().normalize();
        if (!normalizedFile.getFileName().toString()
                .toLowerCase(Locale.ROOT)
                .endsWith(".csv")) {
            throw new IllegalArgumentException("Il file STOOQ deve avere estensione .csv");
        }
        if (!Files.exists(normalizedFile)) {
            throw new IllegalArgumentException("File STOOQ non trovato: " + normalizedFile);
        }

        StooqCsvParseResult parsed;
        try {
            parsed = parser.parse(
                    normalizedFile,
                    normalizedSymbol,
                    interval,
                    priceAdjustment,
                    properties.isAcceptZeroVolume());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Impossibile leggere CSV STOOQ: " + normalizedFile, exception);
        }

        DeduplicatedBars deduplicated = deduplicate(parsed.bars());
        List<HistoricalBar> ordered = deduplicated.bars().stream()
                .sorted(Comparator.comparing(HistoricalBar::dateTime))
                .toList();

        LocalDate from = ordered.isEmpty()
                ? null
                : ordered.get(0).dateTime().toLocalDate();
        LocalDate to = ordered.isEmpty()
                ? null
                : ordered.get(ordered.size() - 1).dateTime().toLocalDate();

        Set<LocalDateTime> existingDateTimes = existingDateTimes(
                normalizedSymbol,
                interval,
                from,
                to);

        List<HistoricalBarEntity> toInsert = new ArrayList<>();
        int existingDuplicates = 0;
        for (HistoricalBar bar : ordered) {
            if (existingDateTimes.contains(bar.dateTime())) {
                existingDuplicates++;
            } else {
                toInsert.add(new HistoricalBarEntity(bar));
            }
        }

        repository.saveAll(toInsert);

        StooqCsvImportReport report = new StooqCsvImportReport(
                normalizedSymbol,
                normalizedFile.toString(),
                interval,
                priceAdjustment,
                parsed.rowsRead(),
                parsed.rowsValid(),
                parsed.rowsRejected(),
                deduplicated.duplicates() + existingDuplicates,
                toInsert.size(),
                from,
                to,
                "IMPORT COMPLETED");
        logReport(report);
        return report;
    }

    private Set<LocalDateTime> existingDateTimes(
            String symbol,
            BarInterval interval,
            LocalDate from,
            LocalDate to) {
        if (from == null || to == null) {
            return Set.of();
        }
        return repository
                .findByProviderAndSymbolAndIntervalAndDateTimeBetweenOrderByDateTimeAsc(
                        PROVIDER,
                        symbol,
                        interval,
                        from.atStartOfDay(),
                        to.plusDays(1).atStartOfDay().minusNanos(1))
                .stream()
                .map(HistoricalBarEntity::getDateTime)
                .collect(Collectors.toSet());
    }

    private DeduplicatedBars deduplicate(List<HistoricalBar> bars) {
        Map<LocalDateTime, HistoricalBar> unique = new LinkedHashMap<>();
        int duplicates = 0;
        for (HistoricalBar bar : bars) {
            if (unique.containsKey(bar.dateTime())) {
                duplicates++;
            } else {
                unique.put(bar.dateTime(), bar);
            }
        }
        return new DeduplicatedBars(List.copyOf(unique.values()), duplicates);
    }

    private void logReport(StooqCsvImportReport report) {
        log.info("""
                ========== STOOQ IMPORT ==========
                Symbol: {}
                File: {}

                Rows read:     {}
                Rows valid:    {}
                Rows rejected: {}
                Duplicates:    {}
                Rows inserted: {}

                From: {}
                To:   {}

                {}
                """,
                report.symbol(),
                report.file(),
                report.rowsRead(),
                report.rowsValid(),
                report.rowsRejected(),
                report.duplicates(),
                report.rowsInserted(),
                report.from(),
                report.to(),
                report.status());
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol obbligatorio");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private record DeduplicatedBars(
            List<HistoricalBar> bars,
            int duplicates) {
    }
}
