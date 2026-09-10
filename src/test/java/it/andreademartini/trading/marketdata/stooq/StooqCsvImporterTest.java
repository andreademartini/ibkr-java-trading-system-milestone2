package it.andreademartini.trading.marketdata.stooq;

import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.marketdata.PriceAdjustment;
import it.andreademartini.trading.persistence.HistoricalBarEntity;
import it.andreademartini.trading.persistence.HistoricalBarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stooq-importer-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "trading.mode=BACKTEST",
        "trading.ibkr.auto-connect=false",
        "logging.level.it.andreademartini.trading=warn"
})
class StooqCsvImporterTest {

    @Autowired
    private HistoricalBarRepository repository;

    @Autowired
    private StooqCsvImporter importer;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void importsDeduplicatesSortsAndStoresBars() throws Exception {
        StooqCsvImportReport report = importer.importFile(
                "AAPL",
                resource("aapl-duplicates-unsorted.csv"),
                BarInterval.DAILY,
                PriceAdjustment.RAW);

        assertThat(report.rowsRead()).isEqualTo(4);
        assertThat(report.rowsValid()).isEqualTo(4);
        assertThat(report.rowsRejected()).isZero();
        assertThat(report.duplicates()).isEqualTo(1);
        assertThat(report.rowsInserted()).isEqualTo(3);
        assertThat(report.from()).isEqualTo(LocalDate.of(2026, 8, 19));
        assertThat(report.to()).isEqualTo(LocalDate.of(2026, 8, 21));

        List<HistoricalBarEntity> bars = repository
                .findByProviderAndSymbolAndIntervalAndDateTimeBetweenOrderByDateTimeAsc(
                        StooqCsvImporter.PROVIDER,
                        "AAPL",
                        BarInterval.DAILY,
                        LocalDate.of(2026, 8, 19).atStartOfDay(),
                        LocalDate.of(2026, 8, 21).plusDays(1).atStartOfDay().minusNanos(1));

        assertThat(bars)
                .extracting(entity -> entity.getDateTime().toLocalDate())
                .containsExactly(
                        LocalDate.of(2026, 8, 19),
                        LocalDate.of(2026, 8, 20),
                        LocalDate.of(2026, 8, 21));
        assertThat(bars.get(0).getPriceAdjustment()).isEqualTo(PriceAdjustment.RAW);
    }

    @Test
    void secondImportSkipsRowsAlreadyInDatabase() throws Exception {
        Path file = resource("aapl-duplicates-unsorted.csv");

        importer.importFile("AAPL", file, BarInterval.DAILY, PriceAdjustment.RAW);
        StooqCsvImportReport secondReport = importer.importFile(
                "AAPL",
                file,
                BarInterval.DAILY,
                PriceAdjustment.RAW);

        assertThat(secondReport.rowsInserted()).isZero();
        assertThat(secondReport.duplicates()).isEqualTo(4);
    }

    @Test
    void derivesInternalSymbolFromStooqFileName() throws Exception {
        StooqCsvImportReport report = importer.importFile(
                resource("AAPL.US.csv"),
                BarInterval.DAILY,
                PriceAdjustment.RAW);

        assertThat(report.symbol()).isEqualTo("AAPL");
        assertThat(report.rowsInserted()).isEqualTo(2);
    }

    private Path resource(String fileName) throws Exception {
        return Path.of(getClass()
                .getResource("/stooq/" + fileName)
                .toURI());
    }
}
