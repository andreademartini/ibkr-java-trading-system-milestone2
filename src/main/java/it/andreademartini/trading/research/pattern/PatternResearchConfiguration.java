package it.andreademartini.trading.research.pattern;

import it.andreademartini.trading.marketdata.BarInterval;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public record PatternResearchConfiguration(
        LocalDate startDate,
        LocalDate endDate,
        List<String> symbols,
        List<PatternType> patterns,
        ResearchDirection direction,
        List<BigDecimal> targetRs,
        List<Integer> forwardBars,
        int maxHoldingBars,
        SameBarResolution sameBarResolution,
        int inSamplePercent,
        int minimumOccurrences,
        EntryMethod entryMethod,
        StopMethod stopMethod,
        BarInterval timeframe,
        BigDecimal tickSize,
        int stopBufferTicks,
        boolean closeTimeoutAtLastClose,
        PatternDetectorParameters detectorParameters) {

    public static final List<String> DEFAULT_SYMBOLS = List.of(
            "AAPL", "MSFT", "NVDA", "AMZN", "META", "GOOGL", "TSLA",
            "AMD", "AVGO", "NFLX", "JPM", "BAC", "GS", "XOM", "CVX",
            "CAT", "BA", "DIS", "NKE", "KO", "SPY", "QQQ", "IWM",
            "DIA", "XLK", "XLF", "XLE", "SMH", "XLI", "XLY");

    public static final List<PatternType> DEFAULT_PATTERNS = List.of(
            PatternType.BULLISH_ENGULFING,
            PatternType.BEARISH_ENGULFING,
            PatternType.HAMMER,
            PatternType.SHOOTING_STAR,
            PatternType.BULLISH_HARAMI,
            PatternType.BEARISH_HARAMI,
            PatternType.PIERCING_PATTERN,
            PatternType.DARK_CLOUD_COVER);

    public static PatternResearchConfiguration from(PatternResearchRequest request) {
        PatternResearchRequest source = request == null
                ? new PatternResearchRequest(null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null,
                        null, null, null, null)
                : request;
        LocalDate start = source.startDate() == null
                ? LocalDate.of(2010, 1, 1) : source.startDate();
        LocalDate end = source.endDate() == null ? LocalDate.now() : source.endDate();
        if (end.isBefore(start)) throw new IllegalArgumentException("endDate precedente a startDate");
        List<String> symbols = normalizeSymbols(source.symbols());
        List<PatternType> patterns = source.patterns() == null || source.patterns().isEmpty()
                ? DEFAULT_PATTERNS : List.copyOf(new LinkedHashSet<>(source.patterns()));
        List<BigDecimal> targets = source.targetRs() == null || source.targetRs().isEmpty()
                ? List.of(new BigDecimal("0.5"), BigDecimal.ONE,
                        new BigDecimal("1.5"), new BigDecimal("2.0"),
                        new BigDecimal("3.0"), new BigDecimal("4.0"))
                : source.targetRs().stream().distinct().sorted().toList();
        if (targets.stream().anyMatch(value -> value == null || value.signum() <= 0)) {
            throw new IllegalArgumentException("targetRs deve contenere valori positivi");
        }
        List<Integer> horizons = source.forwardBars() == null || source.forwardBars().isEmpty()
                ? List.of(1, 2, 3, 5, 10, 20)
                : source.forwardBars().stream().distinct().sorted().toList();
        if (horizons.stream().anyMatch(value -> value == null || value < 1)) {
            throw new IllegalArgumentException("forwardBars deve contenere interi positivi");
        }
        int holding = source.maxHoldingBars() == null ? 20 : source.maxHoldingBars();
        int split = source.inSamplePercent() == null ? 70 : source.inSamplePercent();
        int minimum = source.minimumOccurrences() == null ? 100 : source.minimumOccurrences();
        int buffer = source.stopBufferTicks() == null ? 0 : source.stopBufferTicks();
        if (holding < 1 || split < 1 || split > 99 || minimum < 1 || buffer < 0) {
            throw new IllegalArgumentException("Configurazione numerica research non valida");
        }
        BigDecimal tick = source.tickSize() == null ? new BigDecimal("0.01") : source.tickSize();
        if (tick.signum() <= 0) throw new IllegalArgumentException("tickSize deve essere positivo");
        return new PatternResearchConfiguration(
                start, end, symbols, patterns,
                source.direction() == null ? ResearchDirection.BOTH : source.direction(),
                targets, horizons, holding,
                source.sameBarResolution() == null
                        ? SameBarResolution.STOP_FIRST : source.sameBarResolution(),
                split, minimum,
                source.entryMethod() == null ? EntryMethod.NEXT_BAR_OPEN : source.entryMethod(),
                source.stopMethod() == null ? StopMethod.PATTERN_EXTREME : source.stopMethod(),
                source.timeframe() == null ? BarInterval.DAILY : source.timeframe(),
                tick, buffer, Boolean.TRUE.equals(source.closeTimeoutAtLastClose()),
                source.detectorParameters() == null
                        ? PatternDetectorParameters.defaults() : source.detectorParameters());
    }

    private static List<String> normalizeSymbols(List<String> symbols) {
        List<String> source = symbols == null || symbols.isEmpty() ? DEFAULT_SYMBOLS : symbols;
        List<String> normalized = source.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Almeno un symbol è obbligatorio");
        return normalized;
    }
}
