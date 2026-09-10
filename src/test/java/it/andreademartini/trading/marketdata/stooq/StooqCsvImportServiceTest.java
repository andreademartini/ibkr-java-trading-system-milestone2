package it.andreademartini.trading.marketdata.stooq;

import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.marketdata.PriceAdjustment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StooqCsvImportServiceTest {

    @TempDir
    private Path tempDir;

    private StooqProperties properties;
    private StooqCsvImporter importer;
    private StooqCsvImportService service;

    @BeforeEach
    void setUp() {
        properties = new StooqProperties();
        properties.setImportDirectory(tempDir.toString());
        importer = mock(StooqCsvImporter.class);
        service = new StooqCsvImportService(
                properties,
                importer,
                new StooqSymbolMapper(properties));
    }

    @Test
    void rejectsPathTraversalForConfiguredFileImport() {
        assertThatThrownBy(() -> service.importConfiguredFile(
                "../AAPL.US.csv",
                null,
                BarInterval.DAILY,
                PriceAdjustment.RAW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path traversal");
    }

    @Test
    void uploadsCsvOnlyInsideConfiguredImportDirectory() throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "AAPL.US.csv",
                "text/csv",
                csv().getBytes(StandardCharsets.UTF_8));
        Path target = tempDir.resolve("AAPL.US.csv").toAbsolutePath().normalize();
        StooqCsvImportReport expected = report("AAPL", target, 2, 2, 0, 0, 2);
        when(importer.importFile(
                eq(target),
                eq(BarInterval.DAILY),
                eq(PriceAdjustment.RAW)))
                .thenReturn(expected);

        StooqCsvImportReport result = service.importUploadedFile(
                upload,
                BarInterval.DAILY,
                PriceAdjustment.RAW);

        assertThat(result).isSameAs(expected);
        assertThat(Files.readString(target)).contains("Date,Open,High,Low,Close,Volume");
        verify(importer).importFile(target, BarInterval.DAILY, PriceAdjustment.RAW);
    }

    @Test
    void uploadedStooqBrowserFileIsSavedWithCanonicalName() throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "aapl_us_d.csv",
                "text/csv",
                csv().getBytes(StandardCharsets.UTF_8));
        Path target = tempDir.resolve("AAPL.US.csv").toAbsolutePath().normalize();
        StooqCsvImportReport expected = report("AAPL", target, 2, 2, 0, 0, 2);
        when(importer.importFile(
                eq("AAPL"),
                eq(target),
                eq(BarInterval.DAILY),
                eq(PriceAdjustment.RAW)))
                .thenReturn(expected);

        StooqCsvImportReport result = service.importUploadedFile(
                upload,
                "AAPL",
                BarInterval.DAILY,
                PriceAdjustment.RAW);

        assertThat(result).isSameAs(expected);
        assertThat(Files.exists(tempDir.resolve("aapl_us_d.csv"))).isFalse();
        assertThat(Files.readString(target)).contains("Date,Open,High,Low,Close,Volume");
        verify(importer).importFile("AAPL", target, BarInterval.DAILY, PriceAdjustment.RAW);
    }

    @Test
    void importsRelativeSubPathInsideConfiguredDirectory() throws Exception {
        Path dailyDirectory = tempDir.resolve("daily");
        Files.createDirectories(dailyDirectory);
        Path target = dailyDirectory.resolve("AAPL.US.csv").toAbsolutePath().normalize();
        Files.writeString(target, csv(), StandardCharsets.UTF_8);
        StooqCsvImportReport expected = report("AAPL", target, 2, 2, 0, 0, 2);
        when(importer.importFile(
                eq(target),
                eq(BarInterval.DAILY),
                eq(PriceAdjustment.RAW)))
                .thenReturn(expected);

        StooqCsvImportReport result = service.importConfiguredFile(
                "daily/AAPL.US.csv",
                null,
                BarInterval.DAILY,
                PriceAdjustment.RAW);

        assertThat(result).isSameAs(expected);
        verify(importer).importFile(target, BarInterval.DAILY, PriceAdjustment.RAW);
    }

    @Test
    void importAllFilesContinuesWhenOneCsvFails() throws Exception {
        Path aapl = tempDir.resolve("AAPL.US.csv").toAbsolutePath().normalize();
        Path msft = tempDir.resolve("MSFT.US.csv").toAbsolutePath().normalize();
        Files.writeString(aapl, csv(), StandardCharsets.UTF_8);
        Files.writeString(msft, csv(), StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("README.txt"), "ignored", StandardCharsets.UTF_8);

        when(importer.importFile(aapl, BarInterval.DAILY, PriceAdjustment.RAW))
                .thenReturn(report("AAPL", aapl, 2, 2, 0, 0, 2));
        when(importer.importFile(msft, BarInterval.DAILY, PriceAdjustment.RAW))
                .thenThrow(new IllegalArgumentException("header non valido"));

        StooqDirectoryImportReport result = service.importAllFiles(
                BarInterval.DAILY,
                PriceAdjustment.RAW);

        assertThat(result.filesFound()).isEqualTo(2);
        assertThat(result.filesImported()).isEqualTo(1);
        assertThat(result.filesFailed()).isEqualTo(1);
        assertThat(result.importedRows()).isEqualTo(2);
        assertThat(result.errors())
                .extracting(StooqDirectoryImportReport.StooqFileImportError::message)
                .containsExactly("header non valido");
    }

    private String csv() {
        return """
                Date,Open,High,Low,Close,Volume
                2026-08-25,227.92,230.10,226.44,229.31,45892100
                2026-08-26,229.44,231.17,228.80,230.52,42123400
                """;
    }

    private StooqCsvImportReport report(
            String symbol,
            Path file,
            int rowsRead,
            int rowsValid,
            int rowsRejected,
            int duplicates,
            int rowsInserted) {
        return new StooqCsvImportReport(
                symbol,
                file.toString(),
                BarInterval.DAILY,
                PriceAdjustment.RAW,
                rowsRead,
                rowsValid,
                rowsRejected,
                duplicates,
                rowsInserted,
                LocalDate.of(2026, 8, 25),
                LocalDate.of(2026, 8, 26),
                "IMPORT COMPLETED");
    }
}
