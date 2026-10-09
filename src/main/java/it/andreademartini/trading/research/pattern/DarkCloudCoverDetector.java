package it.andreademartini.trading.research.pattern;

import it.andreademartini.trading.marketdata.HistoricalBar;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Component
public final class DarkCloudCoverDetector implements PatternDetector {
    public PatternType patternType() { return PatternType.DARK_CLOUD_COVER; }
    public PatternDirection direction() { return PatternDirection.SHORT; }

    public Optional<PatternDetectionResult> detect(
            List<HistoricalBar> bars, int index, PatternDetectorParameters parameters) {
        if (!PatternDetectorSupport.validIndex(bars, index, 2)) return Optional.empty();
        HistoricalBar previous = bars.get(index - 1);
        HistoricalBar current = bars.get(index);
        BigDecimal midpoint = previous.getOpen().add(previous.getClose())
                .divide(BigDecimal.valueOf(2), 12, RoundingMode.HALF_UP);
        boolean gapValid = !parameters.darkCloudGapRequired()
                || current.getOpen().compareTo(previous.getHigh()) > 0;
        boolean detected = PatternDetectorSupport.bullish(previous)
                && PatternDetectorSupport.bearish(current)
                && current.getClose().compareTo(midpoint) < 0
                && current.getClose().compareTo(previous.getOpen()) > 0
                && gapValid;
        return PatternDetectorSupport.result(detected, patternType(), direction(), bars, index, 2);
    }
}
