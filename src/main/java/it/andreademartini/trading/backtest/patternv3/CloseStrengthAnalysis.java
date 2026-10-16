package it.andreademartini.trading.backtest.patternv3;

import static it.andreademartini.trading.backtest.patternv1.PatternV1Result.*;
import it.andreademartini.trading.backtest.patternv1.PatternV1MetricsCalculator;
import it.andreademartini.trading.research.pattern.PatternDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Actual portfolio outcomes and baseline trade outcomes remain explicitly separate. */
public final class CloseStrengthAnalysis {
    public enum Status { DISABLED, SKIPPED_PATTERN_RANGE, ACCEPTED, REJECTED, INVALID_DATA }
    public record SignalAudit(String symbol,String direction,String pattern,BigDecimal targetR,
            LocalDate signalDate,String signalTimestamp,int signalIndex,PatternRange range,BigDecimal closeStrength,
            boolean filterEnabled,BigDecimal maxCloseStrength,Status status,String rejectReason) {
        public String key(){return symbol+"|"+direction+"|"+pattern+"|"+signalTimestamp;}
        public boolean evaluated(){return status==Status.ACCEPTED||status==Status.REJECTED||status==Status.INVALID_DATA;}
        public Boolean accepted(){return status==Status.SKIPPED_PATTERN_RANGE?null:status==Status.DISABLED||status==Status.ACCEPTED;}
    }
    public record Statistics(boolean filterEnabled,BigDecimal maxCloseStrength,long totalDetectedSignals,long signalsEvaluated,
            long signalsAccepted,long signalsRejected,long invalidSignals,long signalsRejectedByPatternRange,long otherSkips,
            Double acceptanceRatePercent,Double averageCloseStrengthWinners,Double averageCloseStrengthLosers) {}
    public record Bucket(String bucket,long signals,long invalidSignals,Metrics metrics) {}
    public record Symbol(String symbol,String direction,String pattern,BigDecimal targetR,
            Double averageCloseStrengthWinners,Double averageCloseStrengthLosers,long signalsRejectedByCloseStrength,
            long invalidSignals,long tradesAccepted,Metrics metrics) {}
    public record Outcome(SignalAudit signal,String baselineTradeId,String baselineStatus,Double resultR,Double netPnl) {}
    public record Analysis(Statistics statistics,String baselineRunId,List<Bucket> actualBuckets,List<Bucket> baselineBuckets,
            List<Symbol> symbols,List<Outcome> outcomes,List<PatternRangeAnalysis.Metric> comparison,
            List<PatternV3Analysis.Period> baselinePeriods,List<PatternV3Analysis.Period> baselineYearly) {}
    public static final List<String> BUCKETS=List.of("0.00 - 0.20","0.20 - 0.40","0.40 - 0.60","0.60 - 0.70","0.70 - 0.80","0.80 - 0.90","0.90 - 1.00");
    private static final List<BigDecimal> LIMITS=List.of("0.20","0.40","0.60","0.70","0.80","0.90").stream().map(BigDecimal::new).toList();
    private CloseStrengthAnalysis() {}
    public static int bucket(BigDecimal value) {
        if(value==null||value.signum()<0||value.compareTo(BigDecimal.ONE)>0)return -1;
        for(int i=0;i<LIMITS.size();i++)if(value.compareTo(LIMITS.get(i))<0)return i;return LIMITS.size();
    }
    public static SignalAudit audit(PatternRangeAnalysis.SignalAudit range,BigDecimal targetR,PatternV3Configuration config) {
        var direction=PatternDirection.valueOf(range.direction());var value=CloseStrength.value(range.range(),direction);
        Status status=!range.accepted()?Status.SKIPPED_PATTERN_RANGE:!config.closeStrengthFilterEnabled()?Status.DISABLED:
                value==null?Status.INVALID_DATA:(config.closeStrengthMin()==null?CloseStrength.accepts(range.range(),direction,config.maxCloseStrength()):CloseStrength.acceptsMinimum(range.range(),direction,config.closeStrengthMin()))?Status.ACCEPTED:Status.REJECTED;
        String reason=status==Status.SKIPPED_PATTERN_RANGE?"PATTERN_RANGE_FILTER":status==Status.REJECTED?"CLOSE_STRENGTH_FILTER":status==Status.INVALID_DATA?"INVALID_CLOSE_STRENGTH_DATA":null;
        return new SignalAudit(range.symbol(),range.direction(),range.pattern(),targetR,range.signalDate(),range.signalTimestamp(),range.signalIndex(),range.range(),value,config.closeStrengthFilterEnabled(),config.maxCloseStrength(),status,reason);
    }
    public static BigDecimal value(Trade trade){return CloseStrength.value(new PatternRange(trade.patternHigh(),trade.patternLow(),trade.patternClose()),trade.direction());}
    public static Analysis calculate(PatternV3Run actual,PatternV3Run baseline) {
        var signals=actual.closeStrengthSignals();var trades=actual.portfolio().trades();
        long evaluated=signals.stream().filter(SignalAudit::evaluated).count(),accepted=count(signals,Status.ACCEPTED),rejected=count(signals,Status.REJECTED),invalid=count(signals,Status.INVALID_DATA);
        long otherSkips=actual.portfolio().events().stream().filter(e->e.eventType().startsWith("SIGNAL_SKIPPED")).count();
        var stats=new Statistics(actual.configuration().closeStrengthFilterEnabled(),actual.configuration().maxCloseStrength(),signals.size(),evaluated,accepted,rejected,invalid,
                actual.rangeSignals().stream().filter(s->!s.accepted()).count(),otherSkips,evaluated==0?null:accepted*100.0/evaluated,
                average(trades,1),average(trades,-1));
        List<Symbol> symbols=new ArrayList<>();
        for(var group:actual.analysis().symbolResults()) {
            var subset=signals.stream().filter(s->s.symbol().equals(group.symbol())&&s.direction().equals(group.direction())&&s.pattern().equals(group.pattern())&&s.targetR().compareTo(BigDecimal.valueOf(group.targetR()))==0).toList();
            var matched=trades.stream().filter(t->t.symbol().equals(group.symbol())&&t.direction().name().equals(group.direction())&&t.pattern().name().equals(group.pattern())&&t.targetR().compareTo(BigDecimal.valueOf(group.targetR()))==0).toList();
            symbols.add(new Symbol(group.symbol(),group.direction(),group.pattern(),BigDecimal.valueOf(group.targetR()),average(matched,1),average(matched,-1),count(subset,Status.REJECTED),count(subset,Status.INVALID_DATA),matched.size(),closedMetrics(matched)));
        }
        var base=baseline!=null?baseline:!actual.configuration().patternRangeFilterEnabled()&&!actual.configuration().closeStrengthFilterEnabled()?actual:null;
        var baseTrades=base==null?Map.<String,Trade>of():tradeMap(base);Map<String,String> skips=new HashMap<>();
        if(base!=null)for(var e:base.portfolio().events())if(e.eventType().startsWith("SIGNAL_SKIPPED"))skips.putIfAbsent(e.symbol()+"|"+e.direction()+"|"+e.pattern()+"|"+e.details().get("signalIndex"),e.eventType());
        List<Outcome> outcomes=new ArrayList<>();
        for(var signal:signals) {
            var t=baseTrades.get(signal.key());var status=base==null?"BASELINE_NOT_AVAILABLE":t!=null?t.closed()?t.exitReason():"OPEN":
                    skips.getOrDefault(signal.symbol()+"|"+signal.direction()+"|"+signal.pattern()+"|"+signal.signalIndex(),"NO_BASELINE_TRADE");
            outcomes.add(new Outcome(signal,t==null?null:t.tradeId(),status,t==null||!t.closed()||t.resultR()==null?null:t.resultR().doubleValue(),t==null||!t.closed()?null:t.netPnl().doubleValue()));
        }
        return new Analysis(stats,base==null?null:base.runId(),buckets(actual),base==null?List.of():buckets(base),List.copyOf(symbols),List.copyOf(outcomes),
                baseline==null?List.of():PatternV3RunComparison.pair(baseline,actual),baseline==null?List.of():baseline.analysis().periods(),baseline==null?List.of():baseline.analysis().yearly());
    }
    private static long count(List<SignalAudit> signals,Status status){return signals.stream().filter(s->s.status()==status).count();}
    private static Double average(List<Trade> trades,int sign) {
        return trades.stream().filter(Trade::closed).filter(t->t.netPnl().signum()==sign).map(CloseStrengthAnalysis::value).filter(Objects::nonNull).mapToDouble(BigDecimal::doubleValue).average().stream().boxed().findFirst().orElse(null);
    }
    private static Metrics closedMetrics(List<Trade> trades){return PatternV1MetricsCalculator.calculate(trades.stream().filter(Trade::closed).toList(),List.of(),0);}
    private static Map<String,Trade> tradeMap(PatternV3Run run) {
        Map<String,Trade> map=new HashMap<>();for(var t:run.portfolio().trades())map.put(t.symbol()+"|"+t.direction()+"|"+t.pattern()+"|"+run.portfolio().priceSeries().get(t.symbol()).get(t.signalIndex()).timestamp(),t);return map;
    }
    private static List<Bucket> buckets(PatternV3Run run) {
        List<Bucket> result=new ArrayList<>();
        for(int i=0;i<BUCKETS.size();i++) {
            final int index=i;var trades=run.portfolio().trades().stream().filter(t->bucket(value(t))==index).toList();
            long signals=run.rangeSignals().stream().filter(s->bucket(CloseStrength.value(s.range(),PatternDirection.valueOf(s.direction())))==index).count();
            result.add(new Bucket(BUCKETS.get(i),signals,0,closedMetrics(trades)));
        }
        long invalid=run.rangeSignals().stream().filter(s->!CloseStrength.valid(s.range())).count();
        if(invalid>0)result.add(new Bucket("INVALID / N/D",invalid,invalid,closedMetrics(run.portfolio().trades().stream().filter(t->value(t)==null).toList())));
        return List.copyOf(result);
    }
}
