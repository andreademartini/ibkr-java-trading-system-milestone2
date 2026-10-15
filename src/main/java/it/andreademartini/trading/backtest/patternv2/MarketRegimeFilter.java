package it.andreademartini.trading.backtest.patternv2;

import it.andreademartini.trading.strategy.patternv1.PatternV1Strategy.Signal;
import java.time.LocalDate;
import java.util.*;
import static it.andreademartini.trading.backtest.patternv2.MarketRegimeAnalysis.*;
import static it.andreademartini.trading.backtest.patternv2.MarketRegimeIndicators.Value;

/** V2 research only. One causal decision per detected signal, before other filters and sizing. */
public final class MarketRegimeFilter {
    public record Decision(boolean accepted,String trendAlignment,String rejectionReason) {}
    public record SignalAudit(LocalDate signalDate,String symbol,String pattern,String direction,int signalIndex,
            Value indicators,String marketRegime,String trendAlignment,double filterThreshold,boolean enabled,
            boolean accepted,String rejectionReason) {}
    public record Summary(long totalPatternSignals,long signalsEvaluated,long signalsAccepted,long rejectedWithTrend,
            long rejectedInsufficientData,Double acceptanceRate,Double rejectionRate,long tradesExecuted,long capitalSkips,long positionOpenSkips) {}
    public record SymbolComparison(String symbol,String pattern,String direction,long rejectedSignals,Metrics baseline,Metrics filtered) {}
    public record PeriodComparison(String kind,String period,long rejectedSignals,PatternV2Analysis.Period baseline,
            PatternV2Analysis.Period filtered,Double baselineNetExpectancyR,Double filteredNetExpectancyR) {}
    public record Analysis(String baselineRunId,String methodology,Summary summary,List<SignalAudit> signals,List<Group> acceptedGroups,
            List<SymbolComparison> symbols,List<PeriodComparison> periods,List<PatternRangeAnalysis.Metric> comparison) {}
    private MarketRegimeFilter() {}
    static boolean finite(Double v){return v!=null&&Double.isFinite(v);}
    public static boolean available(Value v){return v!=null&&finite(v.adx14())&&finite(v.plusDI14())&&finite(v.minusDI14());}
    public static Decision decide(boolean enabled,double threshold,String direction,Value v) {
        if(!List.of("LONG","SHORT").contains(direction))throw new IllegalArgumentException("Invalid direction");
        if(!Double.isFinite(threshold)||threshold<0||threshold>100)throw new IllegalArgumentException("Invalid ADX threshold");
        if(!available(v))return new Decision(!enabled,"INSUFFICIENT_DATA",enabled?"INSUFFICIENT_REGIME_DATA":null);
        var trend=v.plusDI14()>v.minusDI14()?"UP":v.plusDI14()<v.minusDI14()?"DOWN":"MIXED";
        var alignment=v.adx14()>=threshold?relation(direction,trend):"NEUTRAL_OR_MIXED";
        boolean reject=enabled&&alignment.equals("WITH_TREND");
        return new Decision(!reject,alignment,reject?"MARKET_REGIME_WITH_TREND":null);
    }
    public static SignalAudit audit(Signal signal,Value value,PatternV2Configuration config) {
        var d=signal.detection();var decision=decide(config.marketRegimeFilterEnabled(),config.marketRegimeFilterAdxThreshold(),d.direction().name(),value);
        return new SignalAudit(d.signalDate(),signal.symbol(),d.patternType().name(),d.direction().name(),d.signalBarIndex(),value,
                classify(value,d.direction().name()).marketRegime(),decision.trendAlignment(),config.marketRegimeFilterAdxThreshold(),config.marketRegimeFilterEnabled(),decision.accepted(),decision.rejectionReason());
    }
    static String key(String symbol,String pattern,String direction,int index){return symbol+"|"+pattern+"|"+direction+"|"+index;}
    public static List<TradeRow> tradeRows(PatternV2Run run,List<SignalAudit> signals) {
        Map<String,SignalAudit> indexed=new HashMap<>();signals.forEach(s->indexed.put(key(s.symbol(),s.pattern(),s.direction(),s.signalIndex()),s));
        return run.portfolio().trades().stream().map(t->{
            var s=indexed.get(key(t.symbol(),t.pattern().name(),t.direction().name(),t.signalIndex()));
            var context=classify(s==null?null:s.indicators(),t.direction().name());
            return new TradeRow(t.tradeId(),t.symbol(),t.pattern().name(),t.direction().name(),t.signalDate(),t.entryDate(),t.exitDate(),t.closed(),period(t.signalDate()),context,
                    t.netPnl(),t.grossPnl(),t.resultR(),t.closed()?netR(t):null,t.closed()?outcome(t.netPnl()):"OPEN",t.exitReason(),null,t.holdingBars(),null);
        }).toList();
    }
    private static String category(TradeRow r) {
        var v=r.context().indicators();if(!available(v))return "INSUFFICIENT_DATA";
        if(v.adx14()<20)return "WEAK_TREND";if(v.adx14()<25)return "TRANSITION";
        return r.context().withOrCounterTrend();
    }
    public static Analysis calculate(PatternV2Run run,List<SignalAudit> signals,PatternV2Run baseline) {
        var evaluated=signals.stream().filter(SignalAudit::enabled).toList();
        long rejected=signals.stream().filter(s->!s.accepted()).count(),with=signals.stream().filter(s->"MARKET_REGIME_WITH_TREND".equals(s.rejectionReason())).count();
        long accepted=evaluated.stream().filter(SignalAudit::accepted).count();var p=run.portfolio();
        var summary=new Summary(signals.size(),evaluated.size(),accepted,with,rejected-with,
                evaluated.isEmpty()?null:100.0*accepted/evaluated.size(),evaluated.isEmpty()?null:100.0*rejected/evaluated.size(),p.trades().size(),p.summary().skippedCapital(),
                p.events().stream().filter(e->e.eventType().equals("SIGNAL_SKIPPED_POSITION_ALREADY_OPEN")).count());
        var rows=tradeRows(run,signals);var baseRows=baseline==null?List.<TradeRow>of():tradeRows(baseline,baseline.marketRegimeFilterAnalysis().signals());
        var groups=new ArrayList<Group>();
        for(var category:List.of("WITH_TREND","COUNTER_TREND","NEUTRAL_OR_MIXED","WEAK_TREND","TRANSITION","INSUFFICIENT_DATA"))
            groups.add(new Group(category,null,null,null,category,null,metrics(rows.stream().filter(r->category(r).equals(category)).toList())));
        var symbols=new ArrayList<SymbolComparison>();
        for(var symbol:run.configuration().symbols())if(symbol.enabled())for(var direction:List.of("LONG","SHORT")) {
            var c=direction.equals("LONG")?symbol.longConfig():symbol.shortConfig();if(!c.enabled())continue;
            var actual=rows.stream().filter(r->r.symbol().equals(symbol.symbol())&&r.pattern().equals(c.pattern().name())&&r.direction().equals(direction)).toList();
            var base=baseRows.stream().filter(r->r.symbol().equals(symbol.symbol())&&r.pattern().equals(c.pattern().name())&&r.direction().equals(direction)).toList();
            long count=signals.stream().filter(s->!s.accepted()&&s.symbol().equals(symbol.symbol())&&s.pattern().equals(c.pattern().name())&&s.direction().equals(direction)).count();
            symbols.add(new SymbolComparison(symbol.symbol(),c.pattern().name(),direction,count,baseline==null?null:metrics(base),metrics(actual)));
        }
        var periods=new ArrayList<PeriodComparison>();
        addPeriods(periods,"PERIOD",run.analysis().periods(),baseline==null?List.of():baseline.analysis().periods(),rows,baseRows,signals);
        addPeriods(periods,"YEAR",run.analysis().yearly(),baseline==null?List.of():baseline.analysis().yearly(),rows,baseRows,signals);
        var comparison=baseline==null?List.<PatternRangeAnalysis.Metric>of():new ArrayList<>(PatternV2RunComparison.pair(baseline,run));
        if(baseline!=null)comparison.add(new PatternRangeAnalysis.Metric("Net Expectancy R",metrics(baseRows).expectancyNetR(),metrics(rows).expectancyNetR(),
                metrics(baseRows).expectancyNetR()==null||metrics(rows).expectancyNetR()==null?null:metrics(rows).expectancyNetR()-metrics(baseRows).expectancyNetR()));
        return new Analysis(baseline==null?null:baseline.runId(),"Detected signal -> Market Regime -> Range -> Close Strength -> Bollinger/ATR -> Position availability -> Next open risk/capital/sizing. Evaluated/accepted count only enabled filter decisions; OFF evaluates zero and rejects zero. Accepted does not mean executed. No rejected PnL. Disjoint trade categories: insufficient ADX/DI, ADX<20 weak, 20<=ADX<25 transition, otherwise standard ADX25/DI alignment. Filter alignment uses configured threshold independently. Indicators use configured ADX period; column names retain ADX14 convention. PERIOD/YEAR use continuous portfolio equity and exit-date trade statistics; rejection counts use signal date. INDEPENDENT_PERIOD re-simulates each window with original initial capital and indicator warm-up reset at its first bar. Historical descriptive research, not out-of-sample.",summary,List.copyOf(signals),List.copyOf(groups),List.copyOf(symbols),List.copyOf(periods),List.copyOf(comparison));
    }
    public static Analysis withIndependentPeriods(Analysis a,List<PeriodComparison> independent) {
        var periods=new ArrayList<>(a.periods());periods.addAll(independent);
        return new Analysis(a.baselineRunId(),a.methodology(),a.summary(),a.signals(),a.acceptedGroups(),a.symbols(),List.copyOf(periods),a.comparison());
    }
    public static PeriodComparison independentPeriod(String label,PatternV2Run baseline,PatternV2Run filtered) {
        var signals=filtered.marketRegimeFilterAnalysis().signals();
        return new PeriodComparison("INDEPENDENT_PERIOD",label,signals.stream().filter(s->!s.accepted()).count(),portfolioPeriod(label,baseline),portfolioPeriod(label,filtered),
                metrics(tradeRows(baseline,baseline.marketRegimeFilterAnalysis().signals())).expectancyNetR(),metrics(tradeRows(filtered,signals)).expectancyNetR());
    }
    private static PatternV2Analysis.Period portfolioPeriod(String label,PatternV2Run run) {
        var p=run.portfolio();var m=p.summary();return new PatternV2Analysis.Period(label,p.actualStartDate(),p.actualEndDate(),m.trades(),p.returnPercent(),p.cagr(),p.maxDrawdownPercent(),m.profitFactorNet(),m.expectancyR(),m.winRate(),m.netPnl());
    }
    private static void addPeriods(List<PeriodComparison> result,String kind,List<PatternV2Analysis.Period> actual,List<PatternV2Analysis.Period> base,List<TradeRow> rows,List<TradeRow> baseRows,List<SignalAudit> signals) {
        for(var p:actual){var b=base.stream().filter(v->v.label().equals(p.label())).findFirst().orElse(null);
            long count=signals.stream().filter(s->!s.accepted()&&!s.signalDate().isBefore(p.start())&&!s.signalDate().isAfter(p.end())).count();
            result.add(new PeriodComparison(kind,p.label(),count,b,p,b==null?null:periodNetR(baseRows,b),periodNetR(rows,p)));
        }
    }
    private static Double periodNetR(List<TradeRow> rows,PatternV2Analysis.Period p){return metrics(rows.stream().filter(TradeRow::closed).filter(t->!t.exitDate().isBefore(p.start())&&!t.exitDate().isAfter(p.end())).toList()).expectancyNetR();}
}
