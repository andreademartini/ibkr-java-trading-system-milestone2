package it.andreademartini.trading.backtest.patternv3;

import it.andreademartini.trading.backtest.patternv1.PatternV1MetricsCalculator;
import it.andreademartini.trading.backtest.patternv1.PatternV1Result;
import static it.andreademartini.trading.backtest.patternv1.PatternV1Result.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;

/** Baseline cohorts and actual filtered portfolio are deliberately separate datasets. */
public final class PatternRangeAnalysis {
    public record SignalAudit(String symbol,String direction,String pattern,LocalDate signalDate,String signalTimestamp,
            int signalIndex,PatternRange range,boolean filterEnabled,BigDecimal minimumPatternRangePct,
            boolean accepted,String rejectReason) {
        public String key() {return symbol+"|"+direction+"|"+pattern+"|"+signalTimestamp;}
    }
    public record Statistics(boolean enabled,BigDecimal minimumPatternRangePct,long signalsBeforeFilter,
            long signalsAccepted,long signalsRejected,Double acceptanceRatePercent) {}
    public record Row(String group,long signals,long trades,long wins,long losses,Double winRate,Double pfNet,
            Double expectancyR,Double netPnl,long openTrades) {}
    public record SymbolRange(String symbol,String direction,String pattern,Double averagePatternRangePct,
            Double averagePatternRangePctWinners,Double averagePatternRangePctLosers,long signalsRejectedByPatternRange,
            long acceptedSignals,Double pfNetAccepted,Double expectancyAccepted) {}
    public record Outcome(SignalAudit signal,String baselineTradeId,String status,Double resultR,Double netPnl) {}
    public record Metric(String metric,Double baseline,Double filtered,Double difference) {}
    public record Analysis(Statistics statistics,List<Row> groups,List<Row> baselineBuckets,List<Row> filteredBuckets,
            List<SymbolRange> symbols,List<Outcome> baselineOutcomes,List<Metric> comparison,
            List<PatternV3Analysis.Period> baselinePeriods,List<PatternV3Analysis.Period> baselineYearly) {}
    public static final List<String> BUCKETS=List.of("< 1.0%","1.0 - 1.5%","1.5 - 2.0%","2.0 - 2.5%","2.5 - 3.0%","3.0 - 4.0%","4.0 - 5.0%",">= 5.0%");
    private static final List<BigDecimal> LIMITS=List.of("1","1.5","2","2.5","3","4","5").stream().map(BigDecimal::new).toList();
    private PatternRangeAnalysis() {}
    public static int bucket(PatternRange range) {for(int i=0;i<LIMITS.size();i++)if(!range.reaches(LIMITS.get(i)))return i;return LIMITS.size();}
    public static Statistics statistics(PatternV3Configuration c,List<SignalAudit> signals) {
        long accepted=signals.stream().filter(SignalAudit::accepted).count();
        return new Statistics(c.patternRangeFilterEnabled(),c.minimumPatternRangePct(),signals.size(),accepted,signals.size()-accepted,
                signals.isEmpty()?null:accepted*100.0/signals.size());
    }
    public static Analysis calculate(PatternV3Run actual,PatternV3Run baseline) {
        var signals=actual.rangeSignals();var threshold=actual.configuration().minimumPatternRangePct();
        Predicate<SignalAudit> reaches=s->s.range().reaches(threshold);
        var base=baseline==null?actual:baseline;
        var baseSignals=base.rangeSignals();var baseTrades=tradeMap(base);var actualTrades=tradeMap(actual);
        List<Row> groups=List.of(row("ALL / BASELINE",baseSignals,baseTrades),
                row("ACCEPTED BY FILTER",baseSignals.stream().filter(reaches).toList(),baseTrades),
                row("REJECTED BY FILTER",baseSignals.stream().filter(reaches.negate()).toList(),baseTrades));
        List<SymbolRange> symbols=new ArrayList<>();
        for(var symbol:actual.configuration().symbols())if(symbol.enabled())for(var direction:List.of("LONG","SHORT")) {
            var d=direction.equals("LONG")?symbol.longConfig():symbol.shortConfig();if(!d.enabled())continue;
            var subset=signals.stream().filter(s->s.symbol().equals(symbol.symbol())&&s.direction().equals(direction)).toList();
            var trades=actual.portfolio().trades().stream().filter(t->t.symbol().equals(symbol.symbol())&&t.direction().name().equals(direction)).toList();
            var m=PatternV1MetricsCalculator.calculate(trades.stream().filter(Trade::closed).toList(),List.of(),0);
            symbols.add(new SymbolRange(symbol.symbol(),direction,d.pattern().name(),averageSignals(subset),
                    averageTrades(trades.stream().filter(Trade::closed).filter(t->t.netPnl().signum()>0).toList()),
                    averageTrades(trades.stream().filter(Trade::closed).filter(t->t.netPnl().signum()<0).toList()),
                    subset.stream().filter(s->!s.accepted()).count(),subset.stream().filter(SignalAudit::accepted).count(),m.profitFactorNet(),m.expectancyR()));
        }
        Map<String,String> skips=new HashMap<>();
        for(var e:base.portfolio().events())if(e.eventType().startsWith("SIGNAL_SKIPPED"))
            skips.putIfAbsent(e.symbol()+"|"+e.direction()+"|"+e.pattern()+"|"+e.details().get("signalIndex"),e.eventType());
        List<Outcome> outcomes=new ArrayList<>();
        for(var s:signals) {
            var t=baseline==null&&actual.configuration().patternRangeFilterEnabled()?null:baseTrades.get(s.key());
            String status=t==null?"NO_BASELINE_TRADE":t.closed()?t.exitReason():"OPEN";
            if(t==null)status=skips.getOrDefault(s.symbol()+"|"+s.direction()+"|"+s.pattern()+"|"+s.signalIndex(),status);
            if(baseline==null&&actual.configuration().patternRangeFilterEnabled())status="BASELINE_NOT_AVAILABLE";
            outcomes.add(new Outcome(s,t==null?null:t.tradeId(),status,t==null||!t.closed()?null:t.resultR().doubleValue(),t==null||!t.closed()?null:t.netPnl().doubleValue()));
        }
        return new Analysis(statistics(actual.configuration(),signals),baseline==null&&actual.configuration().patternRangeFilterEnabled()?List.of():groups,
                baseline==null&&actual.configuration().patternRangeFilterEnabled()?List.of():buckets(baseSignals,baseTrades),buckets(signals,actualTrades),
                List.copyOf(symbols),List.copyOf(outcomes),baseline==null?List.of():compare(baseline,actual),
                baseline==null?List.of():baseline.analysis().periods(),baseline==null?List.of():baseline.analysis().yearly());
    }
    private static Map<String,Trade> tradeMap(PatternV3Run run) {
        Map<String,Trade> result=new HashMap<>();
        for(var t:run.portfolio().trades())result.put(t.symbol()+"|"+t.direction()+"|"+t.pattern()+"|"+run.portfolio().priceSeries().get(t.symbol()).get(t.signalIndex()).timestamp(),t);
        return result;
    }
    private static List<Row> buckets(List<SignalAudit> signals,Map<String,Trade> trades) {
        List<Row> rows=new ArrayList<>();for(int i=0;i<BUCKETS.size();i++){final int b=i;rows.add(row(BUCKETS.get(i),signals.stream().filter(s->bucket(s.range())==b).toList(),trades));}return List.copyOf(rows);
    }
    private static Row row(String label,List<SignalAudit> signals,Map<String,Trade> trades) {
        var matched=signals.stream().map(s->trades.get(s.key())).filter(Objects::nonNull).toList();
        var closed=matched.stream().filter(Trade::closed).toList();var m=PatternV1MetricsCalculator.calculate(closed,List.of(),0);
        return new Row(label,signals.size(),matched.size(),m.wins(),m.losses(),m.winRate(),m.profitFactorNet(),m.expectancyR(),closed.isEmpty()?null:m.netPnl(),matched.size()-closed.size());
    }
    private static Double averageSignals(List<SignalAudit> signals){return signals.isEmpty()?null:signals.stream().mapToDouble(s->s.range().percent().doubleValue()).average().orElseThrow();}
    private static Double averageTrades(List<Trade> trades){return trades.isEmpty()?null:trades.stream().mapToDouble(t->new PatternRange(t.patternHigh(),t.patternLow(),t.patternClose()).percent().doubleValue()).average().orElseThrow();}
    public static Map<String,Double> metrics(PatternV3Run run) {
        var p=run.portfolio();var m=p.summary();Map<String,Double> values=new LinkedHashMap<>();
        values.put("Final Equity",p.finalCapital().doubleValue());values.put("Net PnL",m.netPnl());values.put("CAGR",p.cagr());values.put("Max Drawdown %",p.maxDrawdownPercent());
        values.put("CAGR / Max Drawdown",p.cagr()==null||p.maxDrawdownPercent()==0?null:p.cagr()/p.maxDrawdownPercent());
        values.put("Trades",(double)m.trades());values.put("Win Rate",m.winRate()==null?null:m.winRate()*100);values.put("PF Net",m.profitFactorNet());values.put("Expectancy R",m.expectancyR());
        values.put("Average Winner R",m.averageWinnerR());values.put("Average Loser R",m.averageLoserR());values.put("Average Actual Risk %",m.averageActualRiskPercent());
        values.put("Exposure %",p.averageGrossExposurePercent());values.put("Capital Skips",(double)m.skippedCapital());
        values.put("Total Commission",run.costs().commissions().doubleValue());values.put("Total Slippage",run.costs().slippage().doubleValue());values.put("Total Costs",run.costs().total().doubleValue());return values;
    }
    private static List<Metric> compare(PatternV3Run base,PatternV3Run actual) {
        var a=metrics(base);var b=metrics(actual);return a.entrySet().stream().map(e->new Metric(e.getKey(),e.getValue(),b.get(e.getKey()),
                e.getValue()==null||b.get(e.getKey())==null?null:b.get(e.getKey())-e.getValue())).toList();
    }
}
