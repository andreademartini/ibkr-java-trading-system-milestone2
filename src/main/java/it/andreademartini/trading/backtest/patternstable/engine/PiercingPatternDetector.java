package it.andreademartini.trading.backtest.patternstable.engine;
import it.andreademartini.trading.research.pattern.*;

import it.andreademartini.trading.marketdata.HistoricalBar;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;


public final class PiercingPatternDetector implements PatternDetector {
    public PatternType patternType() { return PatternType.PIERCING_PATTERN; }
    public PatternDirection direction() { return PatternDirection.LONG; }

    public Optional<PatternDetectionResult> detect(
            List<HistoricalBar> bars, int index, PatternDetectorParameters parameters) {
        if (!PatternDetectorSupport.validIndex(bars, index, 2)) return Optional.empty();
        HistoricalBar previous = bars.get(index - 1);
        HistoricalBar current = bars.get(index);
        BigDecimal midpoint = previous.getOpen().add(previous.getClose())
                .divide(BigDecimal.valueOf(2), 12, RoundingMode.HALF_UP);
        boolean gapValid = !parameters.piercingGapRequired()
                || current.getOpen().compareTo(previous.getLow()) < 0;
        boolean detected = PatternDetectorSupport.bearish(previous)
                && PatternDetectorSupport.bullish(current)
                && current.getClose().compareTo(midpoint) > 0
                && current.getClose().compareTo(previous.getOpen()) < 0
                && gapValid;
        return PatternDetectorSupport.result(detected, patternType(), direction(), bars, index, 2);
    }
}
