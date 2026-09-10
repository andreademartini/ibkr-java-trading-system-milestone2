package it.andreademartini.trading.marketdata.stooq;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StooqSymbolMapperTest {

    @Test
    void appendsConfiguredMarketSuffix() {
        StooqProperties properties = new StooqProperties();
        properties.setDefaultMarketSuffix("US");
        StooqSymbolMapper mapper = new StooqSymbolMapper(properties);

        assertThat(mapper.toStooqSymbol("aapl")).isEqualTo("AAPL.US");
    }

    @Test
    void doesNotAppendSuffixWhenSymbolAlreadyHasOne() {
        StooqProperties properties = new StooqProperties();
        StooqSymbolMapper mapper = new StooqSymbolMapper(properties);

        assertThat(mapper.toStooqSymbol("msft.us")).isEqualTo("MSFT.US");
    }

    @Test
    void suffixIsConfigurable() {
        StooqProperties properties = new StooqProperties();
        properties.setDefaultMarketSuffix("PL");
        StooqSymbolMapper mapper = new StooqSymbolMapper(properties);

        assertThat(mapper.toStooqSymbol("pkn")).isEqualTo("PKN.PL");
    }

    @Test
    void convertsStooqFileNameToInternalSymbol() {
        StooqProperties properties = new StooqProperties();
        properties.setDefaultMarketSuffix("US");
        StooqSymbolMapper mapper = new StooqSymbolMapper(properties);

        assertThat(mapper.toInternalSymbolFromCsvFile(Path.of("AAPL.US.csv")))
                .isEqualTo("AAPL");
    }

    @Test
    void keepsDifferentMarketSuffixWhenConvertingToInternalSymbol() {
        StooqProperties properties = new StooqProperties();
        properties.setDefaultMarketSuffix("US");
        StooqSymbolMapper mapper = new StooqSymbolMapper(properties);

        assertThat(mapper.toInternalSymbolFromCsvFile(Path.of("PKN.PL.csv")))
                .isEqualTo("PKN.PL");
    }

    @Test
    void convertsStooqDownloadedDailyFileNameToSymbol() {
        StooqProperties properties = new StooqProperties();
        properties.setDefaultMarketSuffix("US");
        StooqSymbolMapper mapper = new StooqSymbolMapper(properties);

        assertThat(mapper.toStooqSymbolFromCsvFileName("aapl_us_d.csv"))
                .isEqualTo("AAPL.US");
        assertThat(mapper.toInternalSymbolFromCsvFile(Path.of("aapl_us_d.csv")))
                .isEqualTo("AAPL");
        assertThat(mapper.canonicalCsvFileName("aapl_us_d.csv", null))
                .isEqualTo("AAPL.US.csv");
    }

    @Test
    void canonicalCsvFileNamePrefersSelectedInternalSymbol() {
        StooqProperties properties = new StooqProperties();
        properties.setDefaultMarketSuffix("US");
        StooqSymbolMapper mapper = new StooqSymbolMapper(properties);

        assertThat(mapper.canonicalCsvFileName("aapl_us_d.csv", "AAPL"))
                .isEqualTo("AAPL.US.csv");
    }
}
