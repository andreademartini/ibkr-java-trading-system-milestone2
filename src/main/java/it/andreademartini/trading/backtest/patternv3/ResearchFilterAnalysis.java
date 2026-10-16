package it.andreademartini.trading.backtest.patternv3;

import it.andreademartini.trading.backtest.patternv1.PatternV1Result.Trade;
import java.math.*;
import java.util.*;
import java.util.function.*;

/** Descriptive partitions of actual closed trades. Never re-runs sizing or claims portfolio returns. */
public final class ResearchFilterAnalysis {
    public static final String METHODOLOGY="DESCRIPTIVE_ONLY — NOT A PORTFOLIO BACKTEST. Actual closed trades only. Quartiles use linear interpolation on available observations of this run; ties stay together. Net R uses initial riskPerShare * quantity. No hypothetical outcomes for rejected signals.";
    public record Statistics(long trades,long observations,long missing,Long wins,Long losses,Double winRatePercent,Double profitFactorNet,BigDecimal expectancyNetR,BigDecimal netPnl,BigDecimal maxLossNetR,long gapStopCount){}
    public record Group(String section,String stratum,String bucket,String thresholds,Statistics statistics){}
    private record Observation(Trade trade,SignalFilterAudit.Row signal){}
    private ResearchFilterAnalysis(){}
    private static BigDecimal netR(Trade t){var risk=t.riskPerShare().multiply(BigDecimal.valueOf(t.quantity()));return risk.signum()>0?t.netPnl().divide(risk,MathContext.DECIMAL128):null;}
    private static BigDecimal quantile(List<BigDecimal> sorted,double fraction){
        if(sorted.isEmpty())return null;double position=(sorted.size()-1)*fraction;int low=(int)position,high=(int)Math.ceil(position);
        return sorted.get(low).add(sorted.get(high).subtract(sorted.get(low)).multiply(BigDecimal.valueOf(position-low)));
    }
    private static Statistics statistics(List<Observation> rows,long observations,long missing,Set<String> gapStops){
        if(rows.isEmpty())return new Statistics(0,observations,missing,null,null,null,null,null,null,null,0);
        var gain=BigDecimal.ZERO;var loss=BigDecimal.ZERO;var pnl=BigDecimal.ZERO;var rs=new ArrayList<BigDecimal>();BigDecimal worstLoss=null;long wins=0,losses=0,gaps=0;
        for(var row:rows){var t=row.trade();pnl=pnl.add(t.netPnl());if(t.netPnl().signum()>0){wins++;gain=gain.add(t.netPnl());}else if(t.netPnl().signum()<0){losses++;loss=loss.add(t.netPnl().abs());}
            var r=netR(t);if(r!=null){rs.add(r);if(r.signum()<0)worstLoss=worstLoss==null?r:worstLoss.min(r);}if(gapStops.contains(t.tradeId()))gaps++;}
        var average=rs.isEmpty()?null:rs.stream().reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(rs.size()),MathContext.DECIMAL128);
        return new Statistics(rows.size(),observations,missing,wins,losses,wins+losses==0?null:wins*100.0/(wins+losses),loss.signum()==0?null:gain.divide(loss,MathContext.DECIMAL128).doubleValue(),average,pnl,worstLoss,gaps);
    }
    public static List<Group> groups(PatternV3Run run){
        if(run.signalFilterAudit()==null)return List.of();var signals=new HashMap<String,SignalFilterAudit.Row>();for(var s:run.signalFilterAudit())if(s.tradeId()!=null)signals.put(s.tradeId(),s);
        var all=run.portfolio().trades().stream().filter(Trade::closed).map(t->new Observation(t,signals.get(t.tradeId()))).toList();var gaps=new HashSet<String>();if(run.executionAudit()!=null)for(var a:run.executionAudit().trades())if(a.gapExitType().name().equals("GAP_STOP"))gaps.add(a.tradeId());
        var strata=new LinkedHashMap<String,Predicate<Observation>>();strata.put("ALL",r->true);strata.put("LONG",r->r.trade().direction().name().equals("LONG"));strata.put("SHORT",r->r.trade().direction().name().equals("SHORT"));
        for(var symbol:run.configuration().symbols())if(symbol.enabled())strata.put("SYMBOL / "+symbol.symbol(),r->r.trade().symbol().equals(symbol.symbol()));
        for(var pattern:all.stream().map(r->r.trade().pattern().name()).distinct().sorted().toList())strata.put("PATTERN / "+pattern,r->r.trade().pattern().name().equals(pattern));
        for(var period:List.of("2010-2013","2014-2017","2018-2021","2022-END"))strata.put("PERIOD / "+period,r->MarketRegimeAnalysis.period(r.trade().exitDate()).equals(period));
        var result=new ArrayList<Group>();
        var metrics=new LinkedHashMap<String,Function<SignalFilterAudit.Row,BigDecimal>>();metrics.put("CloseStrength",SignalFilterAudit.Row::closeStrength);metrics.put("PatternRangePct",SignalFilterAudit.Row::patternRangePct);metrics.put("RelativeVolume",SignalFilterAudit.Row::relativeVolume);
        for(var metric:metrics.entrySet()){
            var values=all.stream().filter(r->r.signal()!=null).map(r->metric.getValue().apply(r.signal())).filter(Objects::nonNull).sorted().toList();var q1=quantile(values,.25);var q2=quantile(values,.5);var q3=quantile(values,.75);
            String thresholds=metric.getKey().equals("RelativeVolume")?"0.8 / 1.0 / 1.2 / 1.5":"q25="+q1+"; q50="+q2+"; q75="+q3;
            for(var stratum:strata.entrySet()){
                var population=all.stream().filter(stratum.getValue()).toList();
                var available=population.stream().filter(r->r.signal()!=null&&metric.getValue().apply(r.signal())!=null).toList();long missing=population.size()-available.size();
                result.add(new Group(metric.getKey(),stratum.getKey(),"ALL",thresholds,statistics(population,available.size(),missing,gaps)));
                int buckets=metric.getKey().equals("RelativeVolume")?5:4;
                for(int i=0;i<buckets;i++){final int index=i;var selected=available.stream().filter(r->{var v=metric.getValue().apply(r.signal());int bucket;if(metric.getKey().equals("RelativeVolume")){bucket=v.compareTo(new BigDecimal(".8"))<0?0:v.compareTo(BigDecimal.ONE)<0?1:v.compareTo(new BigDecimal("1.2"))<0?2:v.compareTo(new BigDecimal("1.5"))<0?3:4;}else bucket=v.compareTo(q1)<=0?0:v.compareTo(q2)<=0?1:v.compareTo(q3)<=0?2:3;return bucket==index;}).toList();
                    String label=metric.getKey().equals("RelativeVolume")?List.of("<0.8","[0.8,1.0)","[1.0,1.2)","[1.2,1.5)",">=1.5").get(i):"Q"+(i+1);result.add(new Group(metric.getKey(),stratum.getKey(),label,thresholds,statistics(selected,selected.size(),0,gaps)));}
                var missingRows=population.stream().filter(r->r.signal()==null||metric.getValue().apply(r.signal())==null).toList();result.add(new Group(metric.getKey(),stratum.getKey(),"NOT_AVAILABLE",thresholds,statistics(missingRows,0,missingRows.size(),gaps)));
            }
        }
        for(var stratum:strata.entrySet())for(String combination:List.of("CloseStrength","PatternRangePct","CloseStrength + PatternRangePct","RelativeVolume","CloseStrength + PatternRangePct + RelativeVolume")){
            var population=all.stream().filter(stratum.getValue()).toList();var available=population.stream().filter(r->r.signal()!=null&&(!combination.contains("CloseStrength")||r.signal().closeStrength()!=null)&&(!combination.contains("PatternRangePct")||r.signal().patternRangePct()!=null)&&(!combination.contains("RelativeVolume")||r.signal().relativeVolume()!=null)).toList();
            var selected=available.stream().filter(r->{var s=r.signal();boolean close=!combination.contains("CloseStrength")||(s.closeStrengthRule().equals("MIN_GE")?s.closeStrength().compareTo(s.closeStrengthThreshold())>=0:s.closeStrength().compareTo(s.closeStrengthThreshold())<=0);boolean range=!combination.contains("PatternRangePct")||s.patternRangePct().compareTo(s.patternRangeMinPct())>=0&&(s.patternRangeMaxPct()==null||s.patternRangePct().compareTo(s.patternRangeMaxPct())<=0);boolean volume=!combination.contains("RelativeVolume")||s.relativeVolume().compareTo(s.relativeVolumeMin())>=0&&(s.relativeVolumeMax()==null||s.relativeVolume().compareTo(s.relativeVolumeMax())<=0);return close&&range&&volume;}).toList();
            result.add(new Group("COMBINATIONS",stratum.getKey(),combination,"Configured thresholds, evaluated descriptively even when filters OFF; not a re-funded portfolio",statistics(selected,available.size(),population.size()-available.size(),gaps)));
        }
        return List.copyOf(result);
    }
}
