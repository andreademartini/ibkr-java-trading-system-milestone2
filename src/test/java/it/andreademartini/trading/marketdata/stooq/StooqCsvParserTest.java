package it.andreademartini.trading.marketdata.stooq;

import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.marketdata.PriceAdjustment;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StooqCsvParserTest {

    private final StooqCsvParser parser = new StooqCsvParser();

    @Test
    void parsesAlternativeStooqHeadersAndIgnoresExtraColumns() throws Exception {
        StooqCsvParseResult result = parser.parse(
                resource("aapl-valid-extra-columns.csv"),
                "AAPL",
                BarInterval.DAILY,
                PriceAdjustment.RAW,
                true);

        assertThat(result.rowsRead()).isEqualTo(2);
        assertThat(result.rowsValid()).isEqualTo(2);
        assertThat(result.rowsRejected()).isZero();
        assertThat(result.bars())
                .extracting(HistoricalBar::label)
                .containsExactly("2026-08-20", "2026-08-21");
        assertThat(result.bars().get(0).getOpen()).isEqualByComparingTo("226.10");
        assertThat(result.bars().get(0).getVolume()).isEqualTo(41_234_567L);
    }

    @Test
    void rejectsInvalidDateInvalidOhlcAndNegativeVolumeWithoutStoppingImport()
            throws Exception {
        StooqCsvParseResult result = parser.parse(
                resource("aapl-invalid-rows.csv"),
                "AAPL",
                BarInterval.DAILY,
                PriceAdjustment.RAW,
                true);

        assertThat(result.rowsRead()).isEqualTo(4);
        assertThat(result.rowsValid()).isEqualTo(1);
        assertThat(result.rowsRejected()).isEqualTo(3);
        assertThat(result.bars().get(0).dateTime().toLocalDate())
                .isEqualTo(LocalDate.of(2026, 8, 24));
    }

    @Test
    void canRejectZeroVolumeWhenConfigured() throws Exception {
        StooqCsvParseResult result = parser.parse(
                resource("aapl-invalid-rows.csv"),
                "AAPL",
                BarInterval.DAILY,
                PriceAdjustment.RAW,
                false);

        assertThat(result.rowsValid()).isZero();
        assertThat(result.rowsRejected()).isEqualTo(4);
    }

    @Test
    void emptyFileDoesNotProduceBars() throws Exception {
        StooqCsvParseResult result = parser.parse(
                resource("empty.csv"),
                "AAPL",
                BarInterval.DAILY,
                PriceAdjustment.RAW,
                true);

        assertThat(result.rowsRead()).isZero();
        assertThat(result.bars()).isEmpty();
    }

    @Test
    void invalidHeaderIsRejected() {
        assertThatThrownBy(() -> parser.parse(
                resource("invalid-header.csv"),
                "AAPL",
                BarInterval.DAILY,
                PriceAdjustment.RAW,
                true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VOLUME");
    }

    private Path resource(String fileName) throws Exception {
        return Path.of(getClass()
                .getResource("/stooq/" + fileName)
                .toURI());
    }
}
