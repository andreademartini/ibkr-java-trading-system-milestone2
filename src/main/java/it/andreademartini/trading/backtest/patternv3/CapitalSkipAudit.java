package it.andreademartini.trading.backtest.patternv3;

import it.andreademartini.trading.strategy.patternv1.PatternV1Strategy.Signal;
import java.math.BigDecimal;
import java.util.List;

/** Immutable observation; all prices/equity in the decision context are known at that open. */
public record CapitalSkipAudit(String signalId,Signal signal,String signalTimestamp,String decisionTimestamp,
        int entryIndex,BigDecimal equityReferenceAtSignal,String rejectReason,Context context) {
    public record Candidate(int processingOrder,String signalId,String symbol,String direction,String pattern,int signalIndex,String outcome){}
    public record OpenPosition(String tradeId,String symbol,String direction,long quantity,BigDecimal entryPrice,BigDecimal stop,BigDecimal marketPrice,BigDecimal initialRisk){}
    public record Context(BigDecimal availableCash,BigDecimal investedCapital,BigDecimal equity,int openPositions,
            BigDecimal aggregateOpenRisk,List<OpenPosition> positions,List<Candidate> competingSignals,int processingOrder,
            BigDecimal capitalRequired,BigDecimal capitalShortfall){}
    public CapitalSkipAudit withCandidates(List<Candidate> candidates){var c=context;return new CapitalSkipAudit(signalId,signal,signalTimestamp,decisionTimestamp,entryIndex,equityReferenceAtSignal,rejectReason,
            new Context(c.availableCash,c.investedCapital,c.equity,c.openPositions,c.aggregateOpenRisk,c.positions,List.copyOf(candidates),c.processingOrder,c.capitalRequired,c.capitalShortfall));}
}
