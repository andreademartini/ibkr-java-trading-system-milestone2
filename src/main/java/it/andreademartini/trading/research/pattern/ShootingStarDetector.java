package it.andreademartini.trading.research.pattern;

import it.andreademartini.trading.marketdata.HistoricalBar;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public final class ShootingStarDetector implements PatternDetector {
    public PatternType patternType() { return PatternType.SHOOTING_STAR; }
    public PatternDirection direction() { return PatternDirection.SHORT; }

    public Optional<PatternDetectionResult> detect(
            List<HistoricalBar> bars, int index, PatternDetectorParameters parameters) {
        if (!PatternDetectorSupport.validIndex(bars, index, 1)) return Optional.empty();
        HistoricalBar bar = bars.get(index);
        BigDecimal body = PatternDetectorSupport.body(bar);
        boolean detected = body.signum() > 0
                && PatternDetectorSupport.upperShadow(bar).compareTo(
                        body.multiply(parameters.shootingStarMinUpperShadowBodyRatio())) >= 0
                && PatternDetectorSupport.lowerShadow(bar).compareTo(
                        body.multiply(parameters.shootingStarMaxLowerShadowBodyRatio())) <= 0;
        return PatternDetectorSupport.result(detected, patternType(), direction(), bars, index, 1);
    }
}
