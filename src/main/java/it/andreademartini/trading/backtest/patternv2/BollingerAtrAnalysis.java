package it.andreademartini.trading.backtest.patternv2;

import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.strategy.patternv1.PatternV1Strategy.Signal;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

/** Observed signals and real executed trades only. No inferred rejected-trade returns. */
public final class BollingerAtrAnalysis {
    public enum Status { DISABLED, BLOCKED_BY_OTHER_FILTER, ACCEPTED, REJECTED, INSUFFICIENT_DATA }
    public record SignalAudit(String symbol,String direction,String pattern,BigDecimal targetR,LocalDate signalDate,
            int signalIndex,BollingerAtr.Value indicators,BigDecimal patternHigh,BigDecimal patternLow,
            String extremeTimestamp,BigDecimal extremePrice,BigDecimal distanceAtr,Status status,String rejectReason,String diagnostic) {
        public String key(){return symbol+"|"+direction+"|"+pattern+"|"+signalIndex;}
        public boolean evaluated(){return status==Status.ACCEPTED||status==Status.REJECTED||status==Status.INSUFFICIENT_DATA;}
        public Boolean accepted(){return status==Status.BLOCKED_BY_OTHER_FILTER?null:status==Status.DISABLED||status==Status.ACCEPTED;}
    }
    public record Metrics(long trades,long wins,long losses,Double winRate,Double pfNet,Double expectancyR,Double expectancyNetR,double netPnl,Double averageDistanceAtrWinners,Double averageDistanceAtrLosers) {}
    public record Statistics(boolean enabled,BigDecimal maxDistanceAtr,long totalDetectedSignals,long signalsEvaluated,long signalsAccepted,long signalsRejected,long insufficientData,long blockedByOtherFilters,long unavailableIndicators,Double acceptanceRate,Metrics acceptedTrades) {}
    public record Group(String label,long signals,Metrics metrics) {}
    public record Analysis(String source,Statistics statistics,List<Group> buckets,List<Group> symbols,List<PatternV2Analysis.Period> periods) {}
    public static final List<String> BUCKETS=List.of("< -1.00","[-1.00, -0.50)","[-0.50, 0.00)","[0.00, 0.25)","[0.25, 0.50]","(0.50, 1.00]","> 1.00");
    private BollingerAtrAnalysis() {}
    public static int bucket(BigDecimal d) {
        if(d==null)return -1;return d.compareTo(new BigDecimal("-1"))<0?0:d.compareTo(new BigDecimal("-0.5"))<0?1:d.signum()<0?2:d.compareTo(new BigDecimal("0.25"))<0?3:d.compareTo(new BigDecimal("0.5"))<=0?4:d.compareTo(BigDecimal.ONE)<=0?5:6;
    }
    public static SignalAudit audit(Signal signal,List<HistoricalBar> bars,BollingerAtr.Value value,PatternV2Configuration c,boolean eligible) {
        var detection=signal.detection();boolean longSide=detection.direction().name().equals("LONG");
        int extreme=detection.patternStartBarIndex();for(int i=extreme+1;i<=detection.signalBarIndex();i++)if(longSide?bars.get(i).getLow().compareTo(bars.get(extreme).getLow())<0:bars.get(i).getHigh().compareTo(bars.get(extreme).getHigh())>0)extreme=i;
        var distance=BollingerAtr.distance(detection.direction(),signal.patternHigh(),signal.patternLow(),value);
        Status status=!eligible?Status.BLOCKED_BY_OTHER_FILTER:!c.bollingerAtrFilterEnabled()?Status.DISABLED:distance==null?Status.INSUFFICIENT_DATA:distance.compareTo(c.maxBollingerDistanceAtr())<=0?Status.ACCEPTED:Status.REJECTED;
        String reason=status==Status.REJECTED?"BOLLINGER_ATR_FILTER":status==Status.INSUFFICIENT_DATA?"BOLLINGER_ATR_INSUFFICIENT_DATA":null;
        String diagnostic=distance!=null?null:value.atr()!=null&&value.atr().signum()<=0?"NON_POSITIVE_ATR":"INDICATOR_WARMUP";
        return new SignalAudit(signal.symbol(),detection.direction().name(),detection.patternType().name(),signal.targetR(),detection.signalDate(),detection.signalBarIndex(),value,signal.patternHigh(),signal.patternLow(),bars.get(extreme).timestamp().toString(),longSide?bars.get(extreme).getLow():bars.get(extreme).getHigh(),distance,status,reason,diagnostic);
    }
    public static Analysis calculate(PatternV2Run run) {
        if(run.bollingerAtrSignals()==null)return null;
        var signals=run.bollingerAtrSignals();var trades=run.executions().stream().filter(e->e.trade().closed()).toList();
        long evaluated=signals.stream().filter(SignalAudit::evaluated).count(),accepted=signals.stream().filter(s->s.status()==Status.ACCEPTED||s.status()==Status.DISABLED).count();
        var stats=new Statistics(run.configuration().bollingerAtrFilterEnabled(),run.configuration().maxBollingerDistanceAtr(),signals.size(),evaluated,accepted,
                signals.stream().filter(s->s.status()==Status.REJECTED||s.status()==Status.INSUFFICIENT_DATA).count(),signals.stream().filter(s->s.status()==Status.INSUFFICIENT_DATA).count(),
                signals.stream().filter(s->s.status()==Status.BLOCKED_BY_OTHER_FILTER).count(),signals.stream().filter(s->s.distanceAtr()==null).count(),evaluated==0?null:accepted*100.0/evaluated,metrics(trades));
        List<Group> buckets=new ArrayList<>();for(int i=0;i<BUCKETS.size();i++){final int n=i;buckets.add(new Group(BUCKETS.get(i),signals.stream().filter(s->bucket(s.distanceAtr())==n).count(),metrics(trades.stream().filter(e->e.bollingerAtr()!=null&&bucket(e.bollingerAtr().distanceAtr())==n).toList())));}
        var keys=new TreeSet<String>();signals.forEach(s->keys.add(group(s)));List<Group> groups=new ArrayList<>();for(var key:keys)groups.add(new Group(key,signals.stream().filter(s->group(s).equals(key)).count(),metrics(trades.stream().filter(e->e.bollingerAtr()!=null&&group(e.bollingerAtr()).equals(key)).toList())));
        return new Analysis("ACTUAL RUN "+run.runId()+"; closed executed trades only; rejected signals have no theoretical outcomes. Expectancy R is price R, Net R after costs. ATR uses N complete TRs (N+1 bars), then Wilder; population CLOSE Bollinger includes signal close. No pre-start warm-up. Extreme ties use earliest pattern bar.",stats,List.copyOf(buckets),List.copyOf(groups),run.analysis().periods());
    }
    private static String group(SignalAudit s){return s.symbol()+" / "+s.direction()+" / "+s.pattern()+" / "+s.targetR().stripTrailingZeros().toPlainString();}
    private static Metrics metrics(List<PatternV2Run.Execution> executions) {
        long wins=executions.stream().filter(e->e.trade().netPnl().signum()>0).count(),losses=executions.stream().filter(e->e.trade().netPnl().signum()<0).count();
        double profit=executions.stream().filter(e->e.trade().netPnl().signum()>0).mapToDouble(e->e.trade().netPnl().doubleValue()).sum(),loss=executions.stream().filter(e->e.trade().netPnl().signum()<0).mapToDouble(e->-e.trade().netPnl().doubleValue()).sum();
        return new Metrics(executions.size(),wins,losses,executions.isEmpty()?null:wins*100.0/executions.size(),loss==0?null:profit/loss,
                executions.isEmpty()?null:executions.stream().mapToDouble(e->e.trade().resultR().doubleValue()).average().orElseThrow(),
                executions.isEmpty()?null:executions.stream().mapToDouble(e->e.trade().netPnl().doubleValue()/e.trade().actualRiskAmount().doubleValue()).average().orElseThrow(),profit-loss,average(executions,true),average(executions,false));
    }
    private static Double average(List<PatternV2Run.Execution> executions,boolean winners){var values=executions.stream().filter(e->e.bollingerAtr()!=null&&e.bollingerAtr().distanceAtr()!=null&&(winners?e.trade().netPnl().signum()>0:e.trade().netPnl().signum()<0)).mapToDouble(e->e.bollingerAtr().distanceAtr().doubleValue()).toArray();return values.length==0?null:Arrays.stream(values).average().orElseThrow();}
}
