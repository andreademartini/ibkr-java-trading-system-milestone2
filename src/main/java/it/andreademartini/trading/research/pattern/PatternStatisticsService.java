package it.andreademartini.trading.research.pattern;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

@Service
public class PatternStatisticsService {

    private static final int SCALE = 8;

    public List<PatternSummaryRow> summarize(
            List<PatternOccurrence> occurrences, int minimumOccurrences) {
        List<PatternSummaryRow> rows = new ArrayList<>();
        rows.addAll(groupAndSummarize(occurrences, minimumOccurrences, false));
        rows.addAll(groupAndSummarize(occurrences, minimumOccurrences, true));
        return rows.stream().sorted(Comparator
                .comparing((PatternSummaryRow row) -> !"ALL_SYMBOLS".equals(row.symbol()))
                .thenComparing(row -> row.pattern().name())
                .thenComparing(row -> row.direction().name())
                .thenComparing(PatternSummaryRow::symbol)
                .thenComparing(PatternSummaryRow::targetR)).toList();
    }

    private List<PatternSummaryRow> groupAndSummarize(
            List<PatternOccurrence> source,
            int minimumOccurrences,
            boolean bySymbol) {
        Map<GroupKey, List<PatternOccurrence>> groups = new LinkedHashMap<>();
        for (PatternOccurrence occurrence : source) {
            GroupKey key = new GroupKey(occurrence.pattern(), occurrence.direction(),
                    bySymbol ? occurrence.symbol() : "ALL_SYMBOLS", occurrence.targetR());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(occurrence);
        }
        return groups.entrySet().stream().map(entry -> {
            GroupKey key = entry.getKey();
            List<PatternOccurrence> values = entry.getValue();
            PatternMetrics overall = metrics(values, ignored -> true);
            PatternMetrics inSample = metrics(values,
                    value -> value.datasetSegment() == DatasetSegment.IN_SAMPLE);
            PatternMetrics outOfSample = metrics(values,
                    value -> value.datasetSegment() == DatasetSegment.OUT_OF_SAMPLE);
            List<String> warnings = new ArrayList<>();
            if (overall.occurrences() < minimumOccurrences) warnings.add("LOW_SAMPLE_SIZE");
            StabilityStatus stability = stability(
                    inSample, outOfSample, minimumOccurrences);
            if (highWinButNotProfitable(overall)) {
                warnings.add("HIGH_WIN_RATE_WITHOUT_POSITIVE_EDGE");
            }
            return new PatternSummaryRow(key.pattern(), key.direction(), key.symbol(),
                    key.targetR(), overall, inSample, outOfSample, stability,
                    List.copyOf(warnings));
        }).toList();
    }

    public PatternMetrics metrics(
            List<PatternOccurrence> source,
            Predicate<PatternOccurrence> filter) {
        List<PatternOccurrence> values = source.stream().filter(filter)
                .sorted(Comparator.comparing(PatternOccurrence::signalDate)
                        .thenComparing(value -> value.id() == null ? 0L : value.id()))
                .toList();
        long occurrences = values.size();
        long validTrades = values.stream().filter(PatternOccurrence::riskValid).count();
        List<BigDecimal> winners = values.stream()
                .filter(value -> value.exitReason() == PatternExitReason.TARGET)
                .map(PatternOccurrence::resultR).filter(Objects::nonNull).toList();
        List<BigDecimal> losers = values.stream()
                .filter(value -> value.exitReason() == PatternExitReason.STOP)
                .map(PatternOccurrence::resultR).filter(Objects::nonNull).toList();
        long wins = winners.size();
        long losses = losers.size();
        long timeouts = values.stream()
                .filter(value -> value.exitReason() == PatternExitReason.TIMEOUT).count();
        BigDecimal winRate = wins + losses == 0 ? null
                : divide(BigDecimal.valueOf(wins), BigDecimal.valueOf(wins + losses));
        BigDecimal grossProfit = sum(winners);
        BigDecimal grossLoss = sum(losers).abs();
        BigDecimal profitFactor = grossLoss.signum() == 0
                ? null : divide(grossProfit, grossLoss);
        List<BigDecimal> results = values.stream().map(PatternOccurrence::resultR)
                .filter(Objects::nonNull).toList();
        BigDecimal averageWinner = average(winners);
        BigDecimal averageLoser = average(losers);
        BigDecimal expectancy = winRate == null || averageWinner == null
                || averageLoser == null ? null
                : winRate.multiply(averageWinner)
                        .add(BigDecimal.ONE.subtract(winRate).multiply(averageLoser))
                        .setScale(SCALE, RoundingMode.HALF_UP);
        List<BigDecimal> mfeValues = values.stream().map(PatternOccurrence::mfe)
                .filter(Objects::nonNull).toList();
        List<BigDecimal> maeValues = values.stream().map(PatternOccurrence::mae)
                .filter(Objects::nonNull).toList();
        int[] streaks = streaks(values);
        return new PatternMetrics(occurrences, validTrades, wins, losses, timeouts,
                winRate, profitFactor, average(results), median(results), expectancy,
                averageWinner, averageLoser, streaks[0], streaks[1],
                average(mfeValues), median(mfeValues), average(maeValues),
                median(maeValues), maximumDrawdown(results),
                values.stream().filter(PatternOccurrence::sameBarAmbiguous).count());
    }

    private StabilityStatus stability(
            PatternMetrics inSample,
            PatternMetrics outOfSample,
            int minimumOccurrences) {
        if (inSample.occurrences() < minimumOccurrences
                || outOfSample.occurrences() < minimumOccurrences) {
            return StabilityStatus.INSUFFICIENT_DATA;
        }
        if (positive(inSample.expectancyR()) && positive(outOfSample.expectancyR())
                && aboveOne(inSample.profitFactor())
                && aboveOne(outOfSample.profitFactor())) {
            return StabilityStatus.STABLE;
        }
        return StabilityStatus.DEGRADED;
    }

    private boolean highWinButNotProfitable(PatternMetrics value) {
        return value.winRate() != null
                && value.winRate().compareTo(new BigDecimal("0.60")) >= 0
                && (!aboveOne(value.profitFactor()) || !positive(value.expectancyR()));
    }

    private boolean aboveOne(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ONE) > 0;
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private int[] streaks(List<PatternOccurrence> values) {
        int maxWins = 0, maxLosses = 0, currentWins = 0, currentLosses = 0;
        for (PatternOccurrence value : values) {
            if (value.exitReason() == PatternExitReason.TARGET) {
                currentWins++;
                currentLosses = 0;
                maxWins = Math.max(maxWins, currentWins);
            } else if (value.exitReason() == PatternExitReason.STOP) {
                currentLosses++;
                currentWins = 0;
                maxLosses = Math.max(maxLosses, currentLosses);
            }
        }
        return new int[]{maxWins, maxLosses};
    }

    private BigDecimal maximumDrawdown(List<BigDecimal> results) {
        BigDecimal equity = BigDecimal.ZERO;
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal maximum = BigDecimal.ZERO;
        for (BigDecimal result : results) {
            equity = equity.add(result);
            peak = peak.max(equity);
            maximum = maximum.max(peak.subtract(equity));
        }
        return maximum.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal average(List<BigDecimal> values) {
        return values.isEmpty() ? null
                : divide(sum(values), BigDecimal.valueOf(values.size()));
    }

    private BigDecimal median(List<BigDecimal> values) {
        if (values.isEmpty()) return null;
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle)
                : divide(sorted.get(middle - 1).add(sorted.get(middle)),
                        BigDecimal.valueOf(2));
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
    }

    private record GroupKey(
            PatternType pattern,
            PatternDirection direction,
            String symbol,
            BigDecimal targetR) {
    }
}
