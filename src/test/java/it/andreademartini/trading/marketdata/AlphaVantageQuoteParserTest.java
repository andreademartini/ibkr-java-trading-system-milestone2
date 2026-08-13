package it.andreademartini.trading.marketdata;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AlphaVantageQuoteParserTest {

    private final AlphaVantageQuoteParser parser =
            new AlphaVantageQuoteParser();

    @Test
    void parsesGlobalQuote() {
        String body = """
                {
                  "Global Quote": {
                    "01. symbol": "SPY",
                    "05. price": "640.1200",
                    "07. latest trading day": "2026-08-12",
                    "08. previous close": "638.5000"
                  }
                }
                """;

        Optional<AlphaVantageQuote> quote =
                parser.parse(body, Instant.parse("2026-08-13T10:15:30Z"));

        assertThat(quote).hasValueSatisfying(value -> {
            assertThat(value.symbol()).isEqualTo("SPY");
            assertThat(value.price()).isEqualTo(640.12);
            assertThat(value.previousClose()).isEqualTo(638.5);
            assertThat(value.tradingDay()).isEqualTo(LocalDate.parse("2026-08-12"));
        });
    }

    @Test
    void ignoresRateLimitOrErrorResponses() {
        String body = """
                {
                  "Note": "Thank you for using Alpha Vantage."
                }
                """;

        assertThat(parser.parse(body, Instant.now())).isEmpty();
    }

    @Test
    void extractsAlphaVantageServiceMessages() {
        String body = """
                {
                  "Information": "Daily request limit reached."
                }
                """;

        assertThat(parser.serviceMessage(body))
                .contains("Daily request limit reached.");
    }

    @Test
    void parsesDailyHistoricalBarsInChronologicalOrder() {
        String body = """
                {
                  "Meta Data": {
                    "2. Symbol": "SPY"
                  },
                  "Time Series (Daily)": {
                    "2026-08-13": {
                      "1. open": "641.00",
                      "2. high": "643.00",
                      "3. low": "640.00",
                      "4. close": "642.50",
                      "5. volume": "1200"
                    },
                    "2026-08-12": {
                      "1. open": "638.00",
                      "2. high": "640.00",
                      "3. low": "637.00",
                      "4. close": "639.25",
                      "5. volume": "900"
                    }
                  }
                }
                """;

        assertThat(parser.parseHistorical(body, "DAILY", 10))
                .extracting(HistoricalBar::label)
                .containsExactly("2026-08-12", "2026-08-13");
        assertThat(parser.parseHistorical(body, "DAILY", 10).get(1).close())
                .isEqualTo(642.5);
    }
}
