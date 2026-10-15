package it.andreademartini.trading.backtest.patternv3;

import it.andreademartini.trading.backtest.patternv1.*;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.research.pattern.PatternDirection;
import it.andreademartini.trading.risk.PositionSizingService;
import it.andreademartini.trading.strategy.patternv1.PatternV1Properties;
import java.math.*;
import java.time.*;
import java.util.*;

/** Research only. No cash ledger, aggregate portfolio, broker or order execution dependency. */
public final class CapitalSkipShadowAnalysis {
    private static final MathContext MATH=MathContext.DECIMAL64;
    public enum Status { COMPLETED, INCOMPLETE_DATA, NOT_EXECUTABLE }
    public record ShadowTrade(String signalId,String symbol,String direction,String pattern,BigDecimal targetR,
            LocalDate signalDate,int signalBar,String signalTimestamp,LocalDate entryDate,String entryTimestamp,
            BigDecimal entryPrice,BigDecimal entryPriceAfterSlippage,BigDecimal stop,BigDecimal target,
            LocalDate exitDate,String exitTimestamp,BigDecimal exitPrice,BigDecimal exitPriceAfterSlippage,
            String exitReason,int holdingBars,BigDecimal equityReferenceAtSignal,BigDecimal riskBudget,
            long desiredQuantity,long shadowQuantity,BigDecimal shadowOrderValue,BigDecimal actualShadowRisk,
            BigDecimal actualShadowRiskPercent,String quantityLimitingReason,BigDecimal entryCommission,
            BigDecimal exitCommission,BigDecimal totalCommission,BigDecimal slippageCost,BigDecimal transactionCosts,
            BigDecimal grossPnl,BigDecimal netPnl,BigDecimal grossResultR,BigDecimal netResultR,
            BigDecimal mfeR,BigDecimal maeR,boolean sameBarAmbiguous,Status shadowStatus,String statusReason){}
    public record Metrics(long trades,long wins,long losses,long breakevens,Double winRate,Double pfGross,Double pfNet,
            Double grossExpectancyR,Double netExpectancyR,Double averageWinnerNetR,Double averageLoserNetR,
            Double averageNetPnl,Double totalNetPnl,Double averageActualRiskPercent,Double averageHoldingBars,Double timeoutPercent){}
    public record Comparison(String metric,Number executed,Number shadow){}
    public record Symbol(String symbol,String direction,String pattern,BigDecimal targetR,long executedTrades,long skippedCapitalSignals,long incompleteTrades,long notExecutableTrades,Metrics shadow){}
    public record Period(String period,long skippedSignals,long completedShadowTrades,long incompleteTrades,long notExecutableTrades,Metrics shadow){}
    public record Analysis(String runId,String datasetId,Instant timestamp,PatternV3Configuration configuration,
            long totalCapitalSkips,long completedShadowTrades,long incompleteShadowTrades,long notExecutableTrades,
            Metrics executed,Metrics shadow,List<Comparison> comparison,String classification,String classificationReason,
            List<Symbol> symbols,List<Period> periods,List<ShadowTrade> trades,List<CapitalSkipAudit> contexts,String methodology){}
    private record Observation(String symbol,String direction,String pattern,BigDecimal targetR,LocalDate signalDate,
            BigDecimal gross,BigDecimal net,BigDecimal risk,BigDecimal riskPercent,int holding,String reason){}
    private final PatternV1PositionSizing sizing;
    public CapitalSkipShadowAnalysis(PositionSizingService sizing){this.sizing=new PatternV1PositionSizing(sizing);}
    public Analysis analyze(PatternV3Run run,Map<String,List<HistoricalBar>> input){
        if(run.capitalSkipAudits()==null)throw new IllegalArgumentException("Legacy run has no capital skip context; replay its saved dataset first");
        if(run.datasetId()==null||!run.datasetId().equals(PatternV3Dataset.capture(input).id()))throw new IllegalArgumentException("Shadow analysis requires the exact run dataset");
        var c=run.configuration();var g=c.global();Map<String,List<HistoricalBar>> bars=new TreeMap<>();
        input.forEach((symbol,list)->bars.put(symbol,list.stream().filter(b->!b.dateTime().toLocalDate().isBefore(g.startDate())&&!b.dateTime().toLocalDate().isAfter(g.endDate())).sorted(Comparator.comparing(HistoricalBar::dateTime)).toList()));
        long count=run.portfolio().events().stream().filter(e->e.eventType().equals("SIGNAL_SKIPPED_CAPITAL")).count();
        if(count!=run.capitalSkipAudits().size())throw new IllegalStateException("Capital skip event/audit mismatch");
        List<ShadowTrade> trades=new ArrayList<>();for(var audit:run.capitalSkipAudits()){if(!"INSUFFICIENT_CAPITAL".equals(audit.rejectReason()))throw new IllegalArgumentException("Only capital skips are supported");trades.add(simulate(c,audit,bars.getOrDefault(audit.signal().symbol(),List.of())));}
        var executed=run.portfolio().trades().stream().filter(PatternV1Result.Trade::closed).map(t->new Observation(t.symbol(),t.direction().name(),t.pattern().name(),t.targetR(),t.signalDate(),t.grossPnl(),t.netPnl(),t.actualRiskAmount(),t.actualRiskPercent(),t.holdingBars(),t.exitReason())).toList();
        var completed=trades.stream().filter(t->t.shadowStatus()==Status.COMPLETED).map(CapitalSkipShadowAnalysis::observation).toList();var em=metrics(executed);var sm=metrics(completed);
        Set<String> keys=new TreeSet<>();executed.forEach(o->keys.add(key(o.symbol,o.direction,o.pattern,o.targetR)));trades.forEach(t->keys.add(key(t.symbol,t.direction,t.pattern,t.targetR)));
        List<Symbol> symbols=new ArrayList<>();for(var key:keys){var parts=key.split("\\|");var subset=trades.stream().filter(t->key(t.symbol,t.direction,t.pattern,t.targetR).equals(key)).toList();symbols.add(new Symbol(parts[0],parts[1],parts[2],new BigDecimal(parts[3]),executed.stream().filter(o->key(o.symbol,o.direction,o.pattern,o.targetR).equals(key)).count(),subset.size(),count(subset,Status.INCOMPLETE_DATA),count(subset,Status.NOT_EXECUTABLE),metrics(subset.stream().filter(t->t.shadowStatus==Status.COMPLETED).map(CapitalSkipShadowAnalysis::observation).toList())));}
        List<Period> periods=new ArrayList<>();for(int index=0;index<4;index++){final int bucket=index;var subset=trades.stream().filter(t->period(t.signalDate)==bucket).toList();periods.add(new Period(List.of("2010–2013","2014–2017","2018–2021","2022–end").get(index),subset.size(),count(subset,Status.COMPLETED),count(subset,Status.INCOMPLETE_DATA),count(subset,Status.NOT_EXECUTABLE),metrics(subset.stream().filter(t->t.shadowStatus==Status.COMPLETED).map(CapitalSkipShadowAnalysis::observation).toList())));}
        String classification=classify(em,sm);String reason=em.trades<30||sm.trades<30?"Fewer than 30 completed trades in at least one group":em.pfNet==null||sm.pfNet==null?"Undefined PF Net":classification.equals("Inconclusive")?"PF Net, Net Expectancy R and Win Rate are not strictly concordant":"Descriptive concordance of PF Net, Net Expectancy R and Win Rate; not statistical significance";
        return new Analysis(run.runId(),run.datasetId(),Instant.now(),c,count,completed.size(),count(trades,Status.INCOMPLETE_DATA),count(trades,Status.NOT_EXECUTABLE),em,sm,comparison(em,sm),classification,reason,List.copyOf(symbols),List.copyOf(periods),List.copyOf(trades),run.capitalSkipAudits(),"Independent theoretical observations, potentially correlated with executed trades. Closed/completed outcomes only; periods by signal date. Net R denominator = initial price risk × quantity. Winner/loser R are NET R. No shadow portfolio CAGR or equity curve. Competition lists are pending entry candidates, including candidates subsequently rejected for other reasons. Aggregate open risk = sum of initial price risks, excluding costs. Invested capital = gross market value at decision open.");
    }
    public ShadowTrade simulate(PatternV3Configuration c,CapitalSkipAudit a,List<HistoricalBar> bars){
        var signal=a.signal();var d=signal.detection();boolean isLong=d.direction()==PatternDirection.LONG;var g=c.global();
        var symbol=c.symbols().stream().filter(s->s.symbol().equals(signal.symbol())).findFirst().orElseThrow();var split=c.splitCosts()==null?new PatternV3SplitCosts(List.of()):c.splitCosts();
        if(a.entryIndex()<0||a.entryIndex()>=bars.size()||!bars.get(a.entryIndex()).timestamp().toString().equals(a.decisionTimestamp()))return unavailable(a,Status.INCOMPLETE_DATA,"ENTRY_BAR_UNAVAILABLE");
        var entryBar=bars.get(a.entryIndex());var strategy=new ConfiguredPatternStrategy(c,List.of());it.andreademartini.trading.strategy.patternv1.PatternV1Strategy.EntryPlan plan;
        try{plan=strategy.onNextOpen(signal,a.entryIndex(),entryBar.dateTime().toLocalDate(),entryBar.getOpen());}catch(IllegalArgumentException invalid){return unavailable(a,Status.NOT_EXECUTABLE,invalid.getMessage());}
        if(a.equityReferenceAtSignal()==null||a.equityReferenceAtSignal().signum()<=0)return unavailable(a,Status.NOT_EXECUTABLE,"INVALID_REFERENCE_EQUITY");
        var risk=PatternPortfolioSimulator.riskFor(c,symbol,a.equityReferenceAtSignal());var properties=new PatternV1Properties();properties.setMinQuantity(g.minQuantity());properties.setMinOrderValue(BigDecimal.valueOf(g.minOrderValue()));
        var commission=g.commissionEnabled()?g.commission():PatternV1Commission.fixed(BigDecimal.ZERO);var slip=BigDecimal.valueOf(g.slippageEnabled()?g.slippagePercentPerSide():0);var entryFactor=split.factor(signal.symbol(),entryBar);
        var entryFee=PatternV3SplitCosts.commission(commission,entryFactor);risk.setTransactionCostsPerShare(PatternV3SplitCosts.perShare(BigDecimal.valueOf(g.transactionCostsPerShare()),entryFactor).doubleValue());
        var budget=a.equityReferenceAtSignal().multiply(BigDecimal.valueOf(risk.getRiskPerTradePercent())).divide(BigDecimal.valueOf(100),MATH);
        long upper=budget.divide(plan.riskPerShare(),0,RoundingMode.DOWN).min(BigDecimal.valueOf(Long.MAX_VALUE)).longValue();
        var virtualCash=plan.entryPrice().add(plan.entryPrice().multiply(slip).divide(BigDecimal.valueOf(100),MATH)).add(BigDecimal.valueOf(risk.getTransactionCostsPerShare())).multiply(BigDecimal.valueOf(upper)).add(entryFee.charge(upper,plan.entryPrice()));
        var decision=sizing.calculate(plan,a.equityReferenceAtSignal(),virtualCash,risk,properties,entryFee,slip);
        var q=BigDecimal.valueOf(decision.quantity());var entryCommission=entryFee.charge(decision.quantity(),plan.entryPrice());var entrySlip=plan.entryPrice().multiply(slip).divide(BigDecimal.valueOf(100),MATH);
        var entryAfter=isLong?plan.entryPrice().add(entrySlip):plan.entryPrice().subtract(entrySlip);
        if(decision.quantity()==0)return trade(a,plan,null,null,null,0,decision,entryAfter,null,entryCommission,null,null,null,null,null,null,null,false,Status.NOT_EXECUTABLE,"THEORETICAL_SIZE_REJECTED: "+decision.reason());
        var excursions=new PatternV3BarRules.Excursions();PatternV3BarRules.Exit exit=null;HistoricalBar exitBar=null;int holding=0;
        for(int index=a.entryIndex();index<bars.size();index++){var bar=bars.get(index);holding++;var candidate=PatternV3BarRules.exit(plan,bar,holding,symbol.holding(g),g.sameBarPolicy());excursions.measure(plan,bar,candidate.exitOnStop(),candidate.target()&&!candidate.exitOnStop());if(candidate.completed()){exit=candidate;exitBar=bar;break;}}
        if(exit==null)return trade(a,plan,null,null,null,holding,decision,entryAfter,null,entryCommission,null,null,null,null,null,null,null,false,Status.INCOMPLETE_DATA,"DATA_END_BEFORE_EXIT");
        var exitFee=PatternV3SplitCosts.commission(commission,split.factor(signal.symbol(),exitBar)).charge(decision.quantity(),exit.price());var exitSlip=exit.price().multiply(slip).divide(BigDecimal.valueOf(100),MATH);var exitAfter=isLong?exit.price().subtract(exitSlip):exit.price().add(exitSlip);
        var slipCost=entrySlip.add(exitSlip).multiply(q);var extra=BigDecimal.valueOf(risk.getTransactionCostsPerShare()).add(BigDecimal.valueOf(PatternV3SplitCosts.perShare(BigDecimal.valueOf(g.transactionCostsPerShare()),split.factor(signal.symbol(),exitBar)).doubleValue())).multiply(q);
        var gross=(isLong?exit.price().subtract(plan.entryPrice()):plan.entryPrice().subtract(exit.price())).multiply(q);var totalFee=entryCommission.add(exitFee);var net=gross.subtract(slipCost).subtract(totalFee).subtract(extra);
        return trade(a,plan,exitBar,exit.price(),exit.reason(),holding,decision,entryAfter,exitAfter,entryCommission,exitFee,slipCost,extra,gross,net,divide(excursions.mfe,plan.riskPerShare()),divide(excursions.mae,plan.riskPerShare()),exit.stop()&&exit.target(),Status.COMPLETED,null);
    }
    private ShadowTrade unavailable(CapitalSkipAudit a,Status status,String reason){return trade(a,null,null,null,null,0,null,null,null,null,null,null,null,null,null,null,null,false,status,reason);}
    private ShadowTrade trade(CapitalSkipAudit a,it.andreademartini.trading.strategy.patternv1.PatternV1Strategy.EntryPlan p,HistoricalBar exitBar,BigDecimal exit,String reason,int holding,PatternV1PositionSizing.Decision size,BigDecimal entryAfter,BigDecimal exitAfter,BigDecimal entryFee,BigDecimal exitFee,BigDecimal slip,BigDecimal extra,BigDecimal gross,BigDecimal net,BigDecimal mfe,BigDecimal mae,boolean ambiguous,Status status,String statusReason){
        var s=a.signal();var d=s.detection();var risk=size==null?null:size.actualRiskAmount();return new ShadowTrade(a.signalId(),s.symbol(),d.direction().name(),d.patternType().name(),s.targetR(),d.signalDate(),d.signalBarIndex(),a.signalTimestamp(),p==null?null:p.entryDate(),p==null?null:a.decisionTimestamp(),p==null?null:p.entryPrice(),entryAfter,p==null?null:p.stopPrice(),p==null?null:p.takeProfitPrice(),exitBar==null?null:exitBar.dateTime().toLocalDate(),exitBar==null?null:exitBar.timestamp().toString(),exit,exitAfter,reason,holding,a.equityReferenceAtSignal(),size==null?null:size.desiredRiskAmount(),size==null?0:size.desiredQuantity(),size==null?0:size.quantity(),size==null?null:size.orderValue(),risk,size==null?null:size.actualRiskPercent(),size==null?null:size.reason().name(),entryFee,exitFee,exitFee==null?null:entryFee.add(exitFee),slip,extra,gross,net,gross==null?null:divide(gross,risk),net==null?null:divide(net,risk),mfe,mae,ambiguous,status,statusReason);
    }
    private static BigDecimal divide(BigDecimal n,BigDecimal d){return n.divide(d,12,RoundingMode.HALF_UP);}
    private static Observation observation(ShadowTrade t){return new Observation(t.symbol,t.direction,t.pattern,t.targetR,t.signalDate,t.grossPnl,t.netPnl,t.actualShadowRisk,t.actualShadowRiskPercent,t.holdingBars,t.exitReason);}
    private static String key(String s,String d,String p,BigDecimal r){return s+"|"+d+"|"+p+"|"+r.stripTrailingZeros().toPlainString();}
    private static int period(LocalDate d){int year=d.getYear();return year<2010?-1:year<=2013?0:year<=2017?1:year<=2021?2:3;}
    private static long count(List<ShadowTrade> trades,Status status){return trades.stream().filter(t->t.shadowStatus==status).count();}
    private static Double mean(List<Double> values){return values.isEmpty()?null:values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();}
    private static Double pf(List<Observation> trades,boolean net){double win=0,loss=0;for(var t:trades){double v=(net?t.net:t.gross).doubleValue();if(v>0)win+=v;else loss-=v;}return loss==0?null:win/loss;}
    private static Metrics metrics(List<Observation> trades){
        long wins=trades.stream().filter(t->t.net.signum()>0).count(),losses=trades.stream().filter(t->t.net.signum()<0).count();var netRs=trades.stream().map(t->divide(t.net,t.risk).doubleValue()).toList();
        return new Metrics(trades.size(),wins,losses,trades.size()-wins-losses,trades.isEmpty()?null:wins/(double)trades.size(),pf(trades,false),pf(trades,true),mean(trades.stream().map(t->divide(t.gross,t.risk).doubleValue()).toList()),mean(netRs),mean(trades.stream().filter(t->t.net.signum()>0).map(t->divide(t.net,t.risk).doubleValue()).toList()),mean(trades.stream().filter(t->t.net.signum()<0).map(t->divide(t.net,t.risk).doubleValue()).toList()),mean(trades.stream().map(t->t.net.doubleValue()).toList()),trades.isEmpty()?null:trades.stream().mapToDouble(t->t.net.doubleValue()).sum(),mean(trades.stream().map(t->t.riskPercent.doubleValue()).toList()),mean(trades.stream().map(t->(double)t.holding).toList()),trades.isEmpty()?null:trades.stream().filter(t->"TIMEOUT".equals(t.reason)).count()*100.0/trades.size());
    }
    static String classify(Metrics e,Metrics s){if(e.trades<30||s.trades<30||e.pfNet==null||s.pfNet==null)return "Inconclusive";if(s.pfNet>e.pfNet&&s.netExpectancyR>e.netExpectancyR&&s.winRate>e.winRate)return "Shadow signals outperform executed trades";if(s.pfNet<e.pfNet&&s.netExpectancyR<e.netExpectancyR&&s.winRate<e.winRate)return "Shadow signals underperform executed trades";return "Inconclusive";}
    private static List<Comparison> comparison(Metrics e,Metrics s){return List.of(new Comparison("Number of Trades",e.trades,s.trades),new Comparison("Wins",e.wins,s.wins),new Comparison("Losses",e.losses,s.losses),new Comparison("Win Rate %",e.winRate==null?null:e.winRate*100,s.winRate==null?null:s.winRate*100),new Comparison("PF Gross",e.pfGross,s.pfGross),new Comparison("PF Net",e.pfNet,s.pfNet),new Comparison("Gross Expectancy R",e.grossExpectancyR,s.grossExpectancyR),new Comparison("Net Expectancy R",e.netExpectancyR,s.netExpectancyR),new Comparison("Average Winner NET R",e.averageWinnerNetR,s.averageWinnerNetR),new Comparison("Average Loser NET R",e.averageLoserNetR,s.averageLoserNetR),new Comparison("Average Net PnL per Trade",e.averageNetPnl,s.averageNetPnl),new Comparison("Average Actual Risk %",e.averageActualRiskPercent,s.averageActualRiskPercent),new Comparison("Average Holding Bars",e.averageHoldingBars,s.averageHoldingBars),new Comparison("Timeout %",e.timeoutPercent,s.timeoutPercent));}
}
