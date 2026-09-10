package it.andreademartini.trading.marketdata;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class WeeklyLevelService {

    private final HistoricalDataService historicalDataService;

    public WeeklyLevelService(HistoricalDataService historicalDataService) {
        this.historicalDataService = historicalDataService;
    }

    public Optional<WeeklyLevel> previousWeekLevels(String symbol, LocalDate date) {
        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate previousWeekStart = weekStart.minusWeeks(1);
        LocalDate previousWeekEnd = weekStart.minusDays(1);
        List<HistoricalBar> bars = historicalDataService.load(
                symbol,
                previousWeekStart,
                previousWeekEnd,
                BarInterval.DAILY);
        return previousWeekLevels(symbol, weekStart, previousWeekStart, previousWeekEnd, bars);
    }

    public List<WeeklyLevel> buildWeeklyLevels(
            String symbol,
            LocalDate from,
            LocalDate to) {
        List<HistoricalBar> bars = historicalDataService.load(symbol, from, to, BarInterval.DAILY);
        return bars.stream()
                .map(bar -> bar.dateTime().toLocalDate()
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                .distinct()
                .map(weekStart -> previousWeekLevels(
                        symbol,
                        weekStart,
                        weekStart.minusWeeks(1),
                        weekStart.minusDays(1),
                        bars))
                .flatMap(Optional::stream)
                .toList();
    }

    public Optional<WeeklyLevel> previousWeekLevels(
            String symbol,
            LocalDate weekStart,
            List<HistoricalBar> dailyBars) {
        LocalDate normalizedWeekStart =
                weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return previousWeekLevels(
                symbol,
                normalizedWeekStart,
                normalizedWeekStart.minusWeeks(1),
                normalizedWeekStart.minusDays(1),
                dailyBars);
    }

    private Optional<WeeklyLevel> previousWeekLevels(
            String symbol,
            LocalDate weekStart,
            LocalDate previousWeekStart,
            LocalDate previousWeekEnd,
            List<HistoricalBar> dailyBars) {
        List<HistoricalBar> previousWeekBars = dailyBars.stream()
                .filter(bar -> {
                    LocalDate date = bar.dateTime().toLocalDate();
                    return !date.isBefore(previousWeekStart) && !date.isAfter(previousWeekEnd);
                })
                .sorted(Comparator.comparing(HistoricalBar::dateTime))
                .toList();
        if (previousWeekBars.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal high = previousWeekBars.stream()
                .map(HistoricalBar::getHigh)
                .max(BigDecimal::compareTo)
                .orElseThrow();
        BigDecimal low = previousWeekBars.stream()
                .map(HistoricalBar::getLow)
                .min(BigDecimal::compareTo)
                .orElseThrow();

        return Optional.of(new WeeklyLevel(
                symbol,
                weekStart,
                previousWeekStart,
                previousWeekEnd,
                previousWeekBars.get(0).getOpen(),
                high,
                low,
                previousWeekBars.get(previousWeekBars.size() - 1).getClose()));
    }
}
