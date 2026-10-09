package it.andreademartini.trading.research.pattern;

import it.andreademartini.trading.marketdata.HistoricalBar;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

final class PatternDetectorSupport {

    private PatternDetectorSupport() {
    }

    static boolean validIndex(List<HistoricalBar> bars, int index, int requiredBars) {
        return bars != null && index >= requiredBars - 1 && index < bars.size();
    }

    static boolean bullish(HistoricalBar bar) {
        return bar.getClose().compareTo(bar.getOpen()) > 0;
    }

    static boolean bearish(HistoricalBar bar) {
        return bar.getClose().compareTo(bar.getOpen()) < 0;
    }

    static Optional<PatternDetectionResult> result(
            boolean detected,
            PatternType type,
            PatternDirection direction,
            List<HistoricalBar> bars,
            int index,
            int patternBars) {
        if (!detected) {
            return Optional.empty();
        }
        HistoricalBar bar = bars.get(index);
        return Optional.of(new PatternDetectionResult(
                type,
                direction,
                bar.dateTime().toLocalDate(),
                index,
                index - patternBars + 1,
                bar.getClose(),
                null));
    }

    static BigDecimal body(HistoricalBar bar) {
        return bar.getClose().subtract(bar.getOpen()).abs();
    }

    static BigDecimal upperShadow(HistoricalBar bar) {
        return bar.getHigh().subtract(bar.getOpen().max(bar.getClose()));
    }

    static BigDecimal lowerShadow(HistoricalBar bar) {
        return bar.getOpen().min(bar.getClose()).subtract(bar.getLow());
    }
}
