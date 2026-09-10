package it.andreademartini.trading.marketdata;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeeklyLevelServiceTest {

    private final HistoricalDataService historicalDataService =
            mock(HistoricalDataService.class);
    private final WeeklyLevelService service =
            new WeeklyLevelService(historicalDataService);

    @Test
    void calculatesPreviousWeekHighLowOpenAndCloseFromDailyBars() {
        LocalDate currentMonday = LocalDate.of(2026, 8, 24);
        when(historicalDataService.load(
                "AAPL",
                LocalDate.of(2026, 8, 17),
                LocalDate.of(2026, 8, 23),
                BarInterval.DAILY))
                .thenReturn(List.of(
                        bar("2026-08-17", "220.00", "225.00", "219.00", "224.00"),
                        bar("2026-08-18", "224.00", "228.50", "223.50", "227.00"),
                        bar("2026-08-19", "227.00", "229.00", "226.00", "228.00"),
                        bar("2026-08-20", "228.00", "230.10", "227.00", "229.00"),
                        bar("2026-08-21", "229.00", "230.00", "224.80", "226.50")));

        Optional<WeeklyLevel> level = service.previousWeekLevels("AAPL", currentMonday);

        assertThat(level).isPresent();
        assertThat(level.get().previousWeekOpen()).isEqualByComparingTo("220.00");
        assertThat(level.get().previousWeekHigh()).isEqualByComparingTo("230.10");
        assertThat(level.get().previousWeekLow()).isEqualByComparingTo("219.00");
        assertThat(level.get().previousWeekClose()).isEqualByComparingTo("226.50");
    }

    private HistoricalBar bar(
            String date,
            String open,
            String high,
            String low,
            String close) {
        return new HistoricalBar(
                "AAPL",
                LocalDate.parse(date).atStartOfDay(),
                new BigDecimal(open),
                new BigDecimal(high),
                new BigDecimal(low),
                new BigDecimal(close),
                1_000L,
                BarInterval.DAILY,
                "STOOQ",
                PriceAdjustment.RAW);
    }
}
