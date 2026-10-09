package it.andreademartini.trading.research.pattern;

import it.andreademartini.trading.marketdata.HistoricalBar;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class PatternOutcomeAnalyzer {

    private static final MathContext MATH = new MathContext(16, RoundingMode.HALF_UP);

    public List<PatternOccurrence> analyze(
            String runId,
            String symbol,
            List<HistoricalBar> bars,
            PatternDetectionResult detection,
            DatasetSegment segment,
            PatternResearchConfiguration configuration) {
        int entryIndex = detection.signalBarIndex() + 1;
        if (entryIndex >= bars.size()) return List.of();
        HistoricalBar entryBar = bars.get(entryIndex);
        BigDecimal entry = entryBar.getOpen();
        BigDecimal patternClose = bars.get(detection.signalBarIndex()).getClose();
        BigDecimal extreme = patternExtreme(bars, detection);
        BigDecimal buffer = configuration.tickSize()
                .multiply(BigDecimal.valueOf(configuration.stopBufferTicks()), MATH);
        BigDecimal stop = detection.direction() == PatternDirection.LONG
                ? extreme.subtract(buffer, MATH) : extreme.add(buffer, MATH);
        return analyzeWithPrices(runId, symbol, bars, detection, segment,
                configuration, entryIndex, entry, stop, patternClose);
    }

    public List<PatternOccurrence> analyzeWithPrices(
            String runId,
            String symbol,
            List<HistoricalBar> bars,
            PatternDetectionResult detection,
            DatasetSegment segment,
            PatternResearchConfiguration configuration,
            int entryIndex,
            BigDecimal entry,
            BigDecimal stop,
            BigDecimal patternClose) {
        BigDecimal risk = detection.direction() == PatternDirection.LONG
                ? entry.subtract(stop, MATH) : stop.subtract(entry, MATH);
        List<ForwardOutcome> forward = forwardOutcomes(
                bars, detection, entryIndex, entry, patternClose,
                configuration.forwardBars());
        List<PatternOccurrence> outcomes = new ArrayList<>();
        for (BigDecimal targetR : configuration.targetRs()) {
            if (risk.signum() <= 0) {
                outcomes.add(baseOccurrence(runId, symbol, detection, segment,
                        bars.get(entryIndex), patternClose, entry, stop, risk,
                        targetR, null, null, null,
                        PatternExitReason.INVALID_RISK_DISTANCE, null,
                        null, null, false, forward));
                continue;
            }
            BigDecimal target = detection.direction() == PatternDirection.LONG
                    ? entry.add(risk.multiply(targetR, MATH), MATH)
                    : entry.subtract(risk.multiply(targetR, MATH), MATH);
            outcomes.add(simulateTarget(runId, symbol, bars, detection, segment,
                    configuration, entryIndex, entry, stop, risk, targetR,
                    target, patternClose, forward));
        }
        return List.copyOf(outcomes);
    }

    private PatternOccurrence simulateTarget(
            String runId, String symbol, List<HistoricalBar> bars,
            PatternDetectionResult detection, DatasetSegment segment,
            PatternResearchConfiguration configuration, int entryIndex,
            BigDecimal entry, BigDecimal stop, BigDecimal risk,
            BigDecimal targetR, BigDecimal target, BigDecimal patternClose,
            List<ForwardOutcome> forward) {
        BigDecimal maxFavorable = BigDecimal.ZERO;
        BigDecimal maxAdverse = BigDecimal.ZERO;
        int lastIndex = Math.min(bars.size() - 1,
                entryIndex + configuration.maxHoldingBars() - 1);
        for (int index = entryIndex; index <= lastIndex; index++) {
            HistoricalBar bar = bars.get(index);
            BigDecimal favorable = detection.direction() == PatternDirection.LONG
                    ? bar.getHigh().subtract(entry, MATH)
                    : entry.subtract(bar.getLow(), MATH);
            BigDecimal adverse = detection.direction() == PatternDirection.LONG
                    ? entry.subtract(bar.getLow(), MATH)
                    : bar.getHigh().subtract(entry, MATH);
            maxFavorable = maxFavorable.max(favorable.max(BigDecimal.ZERO));
            maxAdverse = maxAdverse.max(adverse.max(BigDecimal.ZERO));
            boolean stopHit = detection.direction() == PatternDirection.LONG
                    ? bar.getLow().compareTo(stop) <= 0
                    : bar.getHigh().compareTo(stop) >= 0;
            boolean targetHit = detection.direction() == PatternDirection.LONG
                    ? bar.getHigh().compareTo(target) >= 0
                    : bar.getLow().compareTo(target) <= 0;
            boolean ambiguous = stopHit && targetHit;
            if (stopHit || targetHit) {
                boolean stopWins = stopHit && (!targetHit
                        || configuration.sameBarResolution() == SameBarResolution.STOP_FIRST);
                return baseOccurrence(runId, symbol, detection, segment, bars.get(entryIndex),
                        patternClose, entry, stop, risk, targetR, target,
                        bar.dateTime().toLocalDate(), stopWins ? stop : target,
                        stopWins ? PatternExitReason.STOP : PatternExitReason.TARGET,
                        stopWins ? BigDecimal.ONE.negate() : targetR,
                        divide(maxFavorable, risk), divide(maxAdverse, risk),
                        ambiguous, forward);
            }
        }
        boolean completeWindow = lastIndex - entryIndex + 1 >= configuration.maxHoldingBars();
        HistoricalBar finalBar = bars.get(lastIndex);
        PatternExitReason reason = completeWindow
                ? PatternExitReason.TIMEOUT : PatternExitReason.INSUFFICIENT_FUTURE_DATA;
        BigDecimal timeoutR = null;
        BigDecimal exitPrice = null;
        LocalDate exitDate = finalBar.dateTime().toLocalDate();
        if (completeWindow && configuration.closeTimeoutAtLastClose()) {
            exitPrice = finalBar.getClose();
            BigDecimal pnl = detection.direction() == PatternDirection.LONG
                    ? exitPrice.subtract(entry, MATH) : entry.subtract(exitPrice, MATH);
            timeoutR = divide(pnl, risk);
        }
        return baseOccurrence(runId, symbol, detection, segment, bars.get(entryIndex),
                patternClose, entry, stop, risk, targetR, target, exitDate, exitPrice,
                reason, timeoutR, divide(maxFavorable, risk), divide(maxAdverse, risk),
                false, forward);
    }

    private List<ForwardOutcome> forwardOutcomes(
            List<HistoricalBar> bars, PatternDetectionResult detection,
            int entryIndex, BigDecimal entry, BigDecimal patternClose,
            List<Integer> horizons) {
        List<ForwardOutcome> outcomes = new ArrayList<>();
        for (int horizon : horizons) {
            int endIndex = detection.signalBarIndex() + horizon;
            if (endIndex >= bars.size()) continue;
            BigDecimal highest = null;
            BigDecimal lowest = null;
            for (int index = entryIndex; index <= endIndex; index++) {
                HistoricalBar bar = bars.get(index);
                highest = highest == null ? bar.getHigh() : highest.max(bar.getHigh());
                lowest = lowest == null ? bar.getLow() : lowest.min(bar.getLow());
            }
            BigDecimal close = bars.get(endIndex).getClose();
            BigDecimal rawFromClose = divide(close.subtract(patternClose, MATH), patternClose);
            BigDecimal directedClose = detection.direction() == PatternDirection.LONG
                    ? divide(close.subtract(entry, MATH), entry)
                    : divide(entry.subtract(close, MATH), entry);
            BigDecimal mfe = detection.direction() == PatternDirection.LONG
                    ? divide(highest.subtract(entry, MATH), entry)
                    : divide(entry.subtract(lowest, MATH), entry);
            BigDecimal mae = detection.direction() == PatternDirection.LONG
                    ? divide(entry.subtract(lowest, MATH), entry)
                    : divide(highest.subtract(entry, MATH), entry);
            outcomes.add(new ForwardOutcome(horizon, rawFromClose, mfe, mae,
                    highest, lowest, directedClose));
        }
        return List.copyOf(outcomes);
    }

    private BigDecimal patternExtreme(
            List<HistoricalBar> bars, PatternDetectionResult detection) {
        BigDecimal extreme = detection.direction() == PatternDirection.LONG
                ? bars.get(detection.patternStartBarIndex()).getLow()
                : bars.get(detection.patternStartBarIndex()).getHigh();
        for (int index = detection.patternStartBarIndex() + 1;
             index <= detection.signalBarIndex(); index++) {
            extreme = detection.direction() == PatternDirection.LONG
                    ? extreme.min(bars.get(index).getLow())
                    : extreme.max(bars.get(index).getHigh());
        }
        return extreme;
    }

    private PatternOccurrence baseOccurrence(
            String runId, String symbol, PatternDetectionResult detection,
            DatasetSegment segment, HistoricalBar entryBar,
            BigDecimal patternClose, BigDecimal entry, BigDecimal stop,
            BigDecimal risk, BigDecimal targetR, BigDecimal target,
            LocalDate exitDate, BigDecimal exitPrice, PatternExitReason reason,
            BigDecimal resultR, BigDecimal mfe, BigDecimal mae,
            boolean ambiguous, List<ForwardOutcome> forward) {
        return new PatternOccurrence(null, runId, symbol, detection.patternType(),
                detection.direction(), detection.signalDate(),
                entryBar.dateTime().toLocalDate(), patternClose, entry, stop,
                risk, targetR, target, exitDate, exitPrice, reason, resultR,
                mfe, mae, ambiguous, segment, forward);
    }

    private static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) return null;
        return numerator.divide(denominator, 12, RoundingMode.HALF_UP);
    }
}
