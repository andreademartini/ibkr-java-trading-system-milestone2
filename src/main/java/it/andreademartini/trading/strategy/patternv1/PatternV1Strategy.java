package it.andreademartini.trading.strategy.patternv1;

import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.research.pattern.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.*;

/** Closed-bar signals and next-open orders. Never connected to SignalService or PAPER. */
public final class PatternV1Strategy {
    private static final MathContext MATH = new MathContext(16, java.math.RoundingMode.HALF_UP);
    private final Map<String, SymbolPatternConfiguration> configurations;
    private final Map<PatternType, PatternDetector> detectors = new EnumMap<>(PatternType.class);

    public PatternV1Strategy(Map<String, SymbolPatternConfiguration> configurations, List<PatternDetector> detectors) {
        this.configurations = Map.copyOf(configurations);
        detectors.forEach(detector -> this.detectors.put(detector.patternType(), detector));
        configurations.values().forEach(symbol -> {
            for (PatternDirection direction : PatternDirection.values()) {
                var config = symbol.direction(direction);
                if (config != null && config.enabled()) {
                    var detector = this.detectors.get(config.pattern());
                    if (detector == null || detector.direction() != direction) {
                        throw new IllegalArgumentException("Detector/direction mismatch: " + config.pattern());
                    }
                }
            }
        });
    }

    public List<Signal> onClosedBar(String symbol, List<HistoricalBar> history, int index) {
        var configuration = configurations.get(symbol.trim().toUpperCase(Locale.ROOT));
        if (configuration == null) return List.of();
        // Physically exclude future bars even when the caller holds a complete historical series.
        List<HistoricalBar> closed = history.subList(0, index + 1);
        if (closed.get(index).interval() != BarInterval.DAILY) throw new IllegalArgumentException("DAILY only");
        List<Signal> signals = new ArrayList<>();
        for (PatternDirection direction : PatternDirection.values()) {
            var config = configuration.direction(direction);
            if (config == null || !config.enabled()) continue;
            detectors.get(config.pattern()).detect(closed, index, PatternDetectorParameters.defaults())
                    .ifPresent(detection -> {
                        BigDecimal low = closed.get(detection.patternStartBarIndex()).getLow();
                        BigDecimal high = closed.get(detection.patternStartBarIndex()).getHigh();
                        for (int i = detection.patternStartBarIndex() + 1; i <= index; i++) {
                            low = low.min(closed.get(i).getLow());
                            high = high.max(closed.get(i).getHigh());
                        }
                        signals.add(new Signal(symbol, detection, config.targetR(), low, high, closed.get(index).getClose()));
                    });
        }
        return List.copyOf(signals);
    }

    /** Only open/date/index are supplied: today's high/low/close cannot affect entry. */
    public EntryPlan onNextOpen(Signal signal, int index, LocalDate date, BigDecimal open) {
        if (index != signal.detection().signalBarIndex() + 1 || !date.isAfter(signal.detection().signalDate())) {
            throw new IllegalArgumentException("Entry requires the next bar");
        }
        boolean isLong = signal.detection().direction() == PatternDirection.LONG;
        BigDecimal stop = isLong ? signal.patternLow() : signal.patternHigh();
        BigDecimal risk = isLong ? open.subtract(stop, MATH) : stop.subtract(open, MATH);
        if (open.signum() <= 0 || stop.signum() <= 0 || risk.signum() <= 0) {
            throw new IllegalArgumentException("INVALID_RISK_DISTANCE");
        }
        BigDecimal target = isLong ? open.add(risk.multiply(signal.targetR(), MATH), MATH)
                : open.subtract(risk.multiply(signal.targetR(), MATH), MATH);
        if (target.signum() <= 0) throw new IllegalArgumentException("INVALID_TARGET_PRICE");
        return new EntryPlan(signal, index, date, open, stop, risk, target);
    }

    public record Signal(String symbol, PatternDetectionResult detection, BigDecimal targetR,
                         BigDecimal patternLow, BigDecimal patternHigh, BigDecimal patternClose) {}
    public record EntryPlan(Signal signal, int entryIndex, LocalDate entryDate, BigDecimal entryPrice,
                            BigDecimal stopPrice, BigDecimal riskPerShare, BigDecimal takeProfitPrice) {}
}
