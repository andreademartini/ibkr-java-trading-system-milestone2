package it.andreademartini.trading.marketdata.stooq;

import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.marketdata.PriceAdjustment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class StooqCsvImportService {

    private static final Logger log =
            LoggerFactory.getLogger(StooqCsvImportService.class);

    private final StooqProperties properties;
    private final StooqCsvImporter importer;
    private final StooqSymbolMapper symbolMapper;

    public StooqCsvImportService(
            StooqProperties properties,
            StooqCsvImporter importer,
            StooqSymbolMapper symbolMapper) {
        this.properties = properties;
        this.importer = importer;
        this.symbolMapper = symbolMapper;
    }

    public StooqCsvImportReport importConfiguredFile(
            String requestedFile,
            String symbol,
            BarInterval interval,
            PriceAdjustment priceAdjustment) {
        Path csvFile = resolveConfiguredImportPath(requestedFile);
        if (symbol == null || symbol.isBlank()) {
            return importer.importFile(csvFile, interval, priceAdjustment);
        }
        return importer.importFile(
                symbolMapper.toInternalSymbol(symbol),
                csvFile,
                interval,
                priceAdjustment);
    }

    public StooqCsvImportReport importUploadedFile(
            MultipartFile file,
            BarInterval interval,
            PriceAdjustment priceAdjustment) {
        return importUploadedFile(file, null, interval, priceAdjustment);
    }

    public StooqCsvImportReport importUploadedFile(
            MultipartFile file,
            String symbol,
            BarInterval interval,
            PriceAdjustment priceAdjustment) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File CSV STOOQ vuoto o mancante");
        }

        Path target = resolveUploadTarget(file.getOriginalFilename(), symbol);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Impossibile salvare CSV STOOQ caricato: " + target, exception);
        }

        if (symbol != null && !symbol.isBlank()) {
            return importer.importFile(
                    symbolMapper.toInternalSymbol(symbol),
                    target,
                    interval,
                    priceAdjustment);
        }
        return importer.importFile(target, interval, priceAdjustment);
    }

    public StooqDirectoryImportReport importAllFiles(
            BarInterval interval,
            PriceAdjustment priceAdjustment) {
        Path directory = properties.importDirectoryPath();
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Impossibile creare directory import STOOQ: " + directory, exception);
        }

        List<Path> csvFiles;
        try (var stream = Files.list(directory)) {
            csvFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(this::isCsv)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Impossibile leggere directory import STOOQ: " + directory, exception);
        }

        List<StooqCsvImportReport> imports = new ArrayList<>();
        List<StooqDirectoryImportReport.StooqFileImportError> errors = new ArrayList<>();
        for (Path csvFile : csvFiles) {
            try {
                log.info("STOOQ CSV import started file={}", csvFile.getFileName());
                imports.add(importer.importFile(csvFile, interval, priceAdjustment));
            } catch (RuntimeException exception) {
                errors.add(new StooqDirectoryImportReport.StooqFileImportError(
                        csvFile.toString(),
                        exception.getMessage()));
                log.warn("STOOQ CSV import failed file={} reason={}",
                        csvFile, exception.getMessage());
            }
        }

        int totalRows = imports.stream()
                .mapToInt(StooqCsvImportReport::rowsRead)
                .sum();
        int importedRows = imports.stream()
                .mapToInt(StooqCsvImportReport::rowsInserted)
                .sum();
        int skippedRows = imports.stream()
                .mapToInt(StooqCsvImportReport::duplicates)
                .sum();
        int errorRows = imports.stream()
                .mapToInt(StooqCsvImportReport::rowsRejected)
                .sum();

        return new StooqDirectoryImportReport(
                directory.toString(),
                interval,
                csvFiles.size(),
                imports.size(),
                errors.size(),
                totalRows,
                importedRows,
                skippedRows,
                errorRows,
                imports,
                errors);
    }

    private Path resolveConfiguredImportPath(String requestedFile) {
        if (requestedFile == null || requestedFile.isBlank()) {
            throw new IllegalArgumentException("file obbligatorio");
        }

        Path requestedPath = Path.of(requestedFile.trim());
        if (requestedPath.isAbsolute()) {
            throw new IllegalArgumentException(
                    "Percorsi assoluti non consentiti per import STOOQ");
        }
        if (containsParentTraversal(requestedPath)) {
            throw new IllegalArgumentException("Path traversal non consentito");
        }

        Path importDirectory = properties.importDirectoryPath();
        Path normalizedRequest = requestedPath.normalize();
        Path fromWorkingDirectory = Path.of("")
                .toAbsolutePath()
                .normalize()
                .resolve(normalizedRequest)
                .normalize();
        Path fromImportDirectory = importDirectory
                .resolve(normalizedRequest)
                .normalize();
        Path resolved = fromWorkingDirectory.startsWith(importDirectory)
                ? fromWorkingDirectory
                : fromImportDirectory;
        if (!resolved.startsWith(importDirectory)) {
            throw new IllegalArgumentException(
                    "Il file STOOQ deve trovarsi in " + importDirectory);
        }
        if (!isCsv(resolved)) {
            throw new IllegalArgumentException("Il file STOOQ deve avere estensione .csv");
        }
        return resolved;
    }

    private boolean containsParentTraversal(Path path) {
        for (Path part : path) {
            if ("..".equals(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private Path resolveUploadTarget(String originalFilename, String symbol) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Nome file STOOQ mancante");
        }

        String fileName = Path.of(originalFilename.replace('\\', '/'))
                .getFileName()
                .toString()
                .trim();
        if (fileName.isBlank() || fileName.contains("..") || !isCsv(fileName)) {
            throw new IllegalArgumentException("Nome file STOOQ non valido: " + fileName);
        }

        String targetFileName = symbolMapper.canonicalCsvFileName(fileName, symbol);
        if (targetFileName.contains("/")
                || targetFileName.contains("\\")
                || targetFileName.contains("..")
                || !isCsv(targetFileName)) {
            throw new IllegalArgumentException(
                    "Nome file STOOQ canonico non valido: " + targetFileName);
        }

        Path target = properties.importDirectoryPath().resolve(targetFileName).normalize();
        if (!target.startsWith(properties.importDirectoryPath())) {
            throw new IllegalArgumentException("Nome file STOOQ non sicuro: " + targetFileName);
        }
        return target;
    }

    private boolean isCsv(Path path) {
        return path != null && isCsv(path.getFileName().toString());
    }

    private boolean isCsv(String fileName) {
        return fileName != null
                && fileName.toLowerCase(Locale.ROOT).endsWith(".csv");
    }
}
