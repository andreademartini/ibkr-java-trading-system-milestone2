package it.andreademartini.trading.backtest.patternv3;

import it.andreademartini.trading.backtest.patternv1.PatternV1MetricsCalculator;
import it.andreademartini.trading.backtest.patternv1.PatternV1Result;
import static it.andreademartini.trading.backtest.patternv1.PatternV1Result.*;
import it.andreademartini.trading.research.pattern.PatternDirection;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** Analysis never changes a run configuration or re-selects setups. */
public final class PatternV3Analysis {
    public record Group(String key,String symbol,String direction,String pattern,Double targetR,Metrics metrics,Double contributionPercent) {}
    public record Period(String label,LocalDate start,LocalDate end,long trades,Double returnPercent,Double cagr,
            double maxDrawdownPercent,Double pfNet,Double expectancyR,Double winRate, Double netPnl) {}
    public record Analysis(Metrics summary,List<Group> symbolResults,List<Group> byTargetR,List<Group> byPattern,
            List<Group> bySymbol,List<Period> periods,List<Period> yearly) {}
    private PatternV3Analysis() {}
    public static Analysis calculate(PatternV1Result run,Double minTarget,Double exactTarget) {
        var trades=run.trades().stream().filter(t->minTarget==null||t.targetR().doubleValue()>=minTarget)
                .filter(t->exactTarget==null||t.targetR().compareTo(java.math.BigDecimal.valueOf(exactTarget))==0).toList();
        var summary=PatternV1MetricsCalculator.calculate(trades,List.of(),run.maxDrawdown());
        var marks=new HashMap<String,NavigableMap<String,Double>>();var times=new TreeSet<String>();
        run.priceSeries().forEach((symbol,bars)->{var series=new TreeMap<String,Double>();bars.forEach(b->{series.put(b.timestamp(),b.close());times.add(b.timestamp());});marks.put(symbol,series);});
        var timestamps=List.copyOf(times);
        var rows=group(run,trades,t->t.symbol()+"|"+t.direction()+"|"+t.pattern()+"|"+t.targetR().stripTrailingZeros(),true,marks,timestamps)
                .stream().filter(r->minTarget==null||r.targetR()>=minTarget).filter(r->exactTarget==null||r.targetR().doubleValue()==exactTarget).toList();
        var target=group(run,trades,t->t.targetR().stripTrailingZeros().toPlainString(),false,marks,timestamps);
        var pattern=group(run,trades,t->t.pattern()+"|"+t.direction(),false,marks,timestamps);
        var symbol=group(run,trades,Trade::symbol,false,marks,timestamps);
        return new Analysis(summary,rows,target,pattern,symbol,periods(run,trades,false),periods(run,trades,true));
    }
    private static List<Group> group(PatternV1Result run,List<Trade> all,Function<Trade,String> key,boolean full,
            Map<String,NavigableMap<String,Double>> marks,List<String> timestamps) {
        Map<String,List<Trade>> grouped=new TreeMap<>();all.forEach(t->grouped.computeIfAbsent(key.apply(t),v->new ArrayList<>()).add(t));
        // Keep configured combinations with no trades visible in the detailed table.
        if(full) for(var s:run.symbolResults()) grouped.putIfAbsent(s.symbol()+"|"+s.direction()+"|"+s.pattern()+"|"+s.targetR().stripTrailingZeros(),new ArrayList<>());
        double total=run.summary().netPnl();List<Group> rows=new ArrayList<>();
        for(var e:grouped.entrySet()) {
            var list=e.getValue();Trade t=list.isEmpty()?null:list.getFirst();
            String[] parts=e.getKey().split("\\|");
            var metrics=PatternV1MetricsCalculator.calculate(list,List.of(),drawdown(run,list,marks,timestamps));
            rows.add(new Group(e.getKey(),full?parts[0]:t==null?null:t.symbol(),full?parts[1]:t==null?null:t.direction().name(),
                    full?parts[2]:t==null?null:t.pattern().name(),full?Double.valueOf(parts[3]):t==null?null:t.targetR().doubleValue(),
                    metrics,total==0?null:metrics.netPnl()/total*100));
        }
        return List.copyOf(rows);
    }
    private static double drawdown(PatternV1Result run,List<Trade> trades,Map<String,NavigableMap<String,Double>> marks,List<String> timestamps) {
        double peak=0,dd=0;
        // PnL attribution at each portfolio mark; no hypothetical re-funded symbol portfolios.
        for(int i=0;i<run.equityCurve().size();i++) {
            String time=timestamps.get(i);
            double value=0;
            for(var t:trades) {
                var bars=run.priceSeries().get(t.symbol());
                if(time.compareTo(bars.get(t.entryIndex()).timestamp())<0)continue;
                if(t.closed()&&time.compareTo(bars.get(t.exitIndex()).timestamp())>=0) value+=t.netPnl().doubleValue();
                else {
                    var close=marks.get(t.symbol()).floorEntry(time);
                    if(close!=null)value+=(t.direction()==PatternDirection.LONG?close.getValue()-t.entryPrice().doubleValue():t.entryPrice().doubleValue()-close.getValue())*t.quantity()-t.entryCosts().doubleValue();
                }
            }
            peak=Math.max(peak,value);dd=Math.max(dd,peak-value);
        }
        return dd;
    }
    private static List<Period> periods(PatternV1Result run,List<Trade> trades,boolean yearly) {
        if(run.actualStartDate()==null||run.actualEndDate()==null)return List.of();
        List<Period> result=new ArrayList<>();
        int first=yearly?run.actualStartDate().getYear():Math.min(2010,run.actualStartDate().getYear());
        int last=run.actualEndDate().getYear();
        for(int year=first;year<=last;) {
            int end=yearly?year:year<2022?Math.min(year+3,2021):last;
            LocalDate from=LocalDate.of(year,1,1).isBefore(run.actualStartDate())?run.actualStartDate():LocalDate.of(year,1,1);
            LocalDate to=LocalDate.of(end,12,31).isAfter(run.actualEndDate())?run.actualEndDate():LocalDate.of(end,12,31);
            var points=run.equityCurve().stream().filter(p->!p.date().isBefore(from)&&!p.date().isAfter(to)).toList();
            if(!points.isEmpty()) {
                double start=run.equityCurve().stream().filter(p->p.date().isBefore(from)).reduce((a,b)->b).map(p->p.equity().doubleValue()).orElse(run.initialCapital().doubleValue());
                double finish=points.getLast().equity().doubleValue(),peak=start,dd=0;
                for(var p:points) {peak=Math.max(peak,p.equity().doubleValue());if(peak>0)dd=Math.max(dd,(peak-p.equity().doubleValue())/peak*100);}
                var closed=trades.stream().filter(Trade::closed).filter(t->!t.exitDate().isBefore(from)&&!t.exitDate().isAfter(to)).toList();
                var m=PatternV1MetricsCalculator.calculate(closed,List.of(),0);
                result.add(new Period(yearly?""+year:year+"–"+end,from,to,m.trades(),start>0?(finish/start-1)*100:null,
                        PatternV1MetricsCalculator.cagr(start,finish,from,to),dd,m.profitFactorNet(),m.expectancyR(),m.winRate(),finish-start));
            }
            year=end+1;
        }
        return List.copyOf(result);
    }
}
