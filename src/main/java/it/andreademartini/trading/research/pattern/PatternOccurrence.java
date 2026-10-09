package it.andreademartini.trading.research.pattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PatternOccurrence(
        Long id,
        String researchRunId,
        String symbol,
        PatternType pattern,
        PatternDirection direction,
        LocalDate signalDate,
        LocalDate entryDate,
        BigDecimal patternClose,
        BigDecimal entryPrice,
        BigDecimal stopPrice,
        BigDecimal riskPerShare,
        BigDecimal targetR,
        BigDecimal targetPrice,
        LocalDate exitDate,
        BigDecimal exitPrice,
        PatternExitReason exitReason,
        BigDecimal resultR,
        BigDecimal mfe,
        BigDecimal mae,
        boolean sameBarAmbiguous,
        DatasetSegment datasetSegment,
        List<ForwardOutcome> forwardOutcomes) {

    public boolean riskValid() {
        return exitReason != PatternExitReason.INVALID_RISK_DISTANCE;
    }

    public boolean resolvedWinOrLoss() {
        return exitReason == PatternExitReason.TARGET
                || exitReason == PatternExitReason.STOP;
    }
}
