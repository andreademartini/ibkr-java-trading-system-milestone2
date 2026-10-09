package it.andreademartini.trading.research.pattern;

import it.andreademartini.trading.marketdata.HistoricalBar;

import java.util.List;
import java.util.Optional;

public interface PatternDetector {

    PatternType patternType();

    PatternDirection direction();

    Optional<PatternDetectionResult> detect(
            List<HistoricalBar> bars,
            int currentIndex,
            PatternDetectorParameters parameters);
}
