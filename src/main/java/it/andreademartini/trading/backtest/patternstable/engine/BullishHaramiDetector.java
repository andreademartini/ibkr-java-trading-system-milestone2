package it.andreademartini.trading.backtest.patternstable.engine;
import it.andreademartini.trading.research.pattern.*;

import it.andreademartini.trading.marketdata.HistoricalBar;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;


public final class BullishHaramiDetector implements PatternDetector {
    public PatternType patternType() { return PatternType.BULLISH_HARAMI; }
    public PatternDirection direction() { return PatternDirection.LONG; }

    public Optional<PatternDetectionResult> detect(
            List<HistoricalBar> bars, int index, PatternDetectorParameters parameters) {
        if (!PatternDetectorSupport.validIndex(bars, index, 2)) return Optional.empty();
        HistoricalBar previous = bars.get(index - 1);
        HistoricalBar current = bars.get(index);
        boolean detected = PatternDetectorSupport.bearish(previous)
                && PatternDetectorSupport.bullish(current)
                && current.getOpen().compareTo(previous.getClose()) >= 0
                && current.getClose().compareTo(previous.getOpen()) <= 0;
        return PatternDetectorSupport.result(detected, patternType(), direction(), bars, index, 2);
    }
}
