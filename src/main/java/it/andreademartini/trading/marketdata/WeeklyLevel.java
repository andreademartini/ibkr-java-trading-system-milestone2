package it.andreademartini.trading.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeeklyLevel(
        String symbol,
        LocalDate weekStart,
        LocalDate previousWeekStart,
        LocalDate previousWeekEnd,
        BigDecimal previousWeekOpen,
        BigDecimal previousWeekHigh,
        BigDecimal previousWeekLow,
        BigDecimal previousWeekClose) {
}
