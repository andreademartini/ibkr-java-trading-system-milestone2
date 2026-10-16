package it.andreademartini.trading.backtest.patternv2;

import it.andreademartini.trading.research.pattern.PatternDirection;
import java.math.BigDecimal;
import java.util.*;
import static it.andreademartini.trading.backtest.patternv2.GapExecutionAudit.GapType.*;

/** Descriptive cohorts of actual closed trades; never a counterfactual simulation. */
public final class GapExitAnalysis {
    private GapExitAnalysis() {}
    public record Stats(long totalClosedTrades,long auditedClosedTrades,long unauditedClosedTrades,
            Long gapStopCount,Long gapTargetCount,BigDecimal gapExitRatePercent,
            Long longGapStops,Long longGapTargets,Long shortGapStops,Long shortGapTargets,
            BigDecimal netPnlGapStop,BigDecimal netPnlGapTarget,
            BigDecimal averageGapSizePercent,BigDecimal medianGapSizePercent,BigDecimal maxGapSizePercent,
            BigDecimal averageGapSizeR,BigDecimal maxGapSizeR,Long validGapSizeCount,
            long priceRBelowMinusOneCount,long priceRAboveTargetCount,
            BigDecimal gapCommissions,BigDecimal gapSlippage,BigDecimal gapOtherCosts,
            BigDecimal gapCostsOverAbsoluteGrossPnlPercent) {}
    public record Group(String section,String group,Stats statistics) {}

    public static List<Group> groups(PatternV2Run run){
        return groups(it.andreademartini.trading.backtest.execution.ExecutionRunView.of(run));
    }
    public static List<Group> groups(it.andreademartini.trading.backtest.execution.ExecutionRunView run){
        var closed=run.executions().stream().filter(e->e.trade().closed()).toList();
        Map<String,GapExecutionAudit.TradeAudit> audit=new HashMap<>();
        if(run.executionAudit()!=null)run.executionAudit().trades().forEach(a->audit.put(a.tradeId(),a));
        List<Group> result=new ArrayList<>();
        result.add(group("GENERAL","ALL",closed,audit,run.executionAudit()!=null));
        for(String symbol:new TreeSet<>(run.enabledSymbols()))
            result.add(group("SYMBOL",symbol,closed.stream().filter(e->e.trade().symbol().equals(symbol)).toList(),audit,run.executionAudit()!=null));
        for(int start:new int[]{2010,2014,2018,2022}){
            int end=start==2022?(run.portfolio().actualEndDate()==null?run.requestedEnd().getYear():run.portfolio().actualEndDate().getYear()):start+3;
            result.add(group("PERIOD",start==2022&&end<start?"2022-latest":start+"-"+end,closed.stream().filter(e->e.trade().exitDate().getYear()>=start&&e.trade().exitDate().getYear()<=end).toList(),audit,run.executionAudit()!=null));
        }
        var from=run.portfolio().actualStartDate();var to=run.portfolio().actualEndDate();
        if(from!=null&&to!=null)for(int year=from.getYear();year<=to.getYear();year++){
            final int y=year;result.add(group("YEAR",Integer.toString(y),closed.stream().filter(e->e.trade().exitDate().getYear()==y).toList(),audit,run.executionAudit()!=null));
        }
        return List.copyOf(result);
    }
    private static Group group(String section,String label,List<it.andreademartini.trading.backtest.execution.ExecutionRunView.Execution> entries,Map<String,GapExecutionAudit.TradeAudit> audit,boolean hasAudit){
        long known=entries.stream().filter(e->audit.containsKey(e.trade().tradeId())&&audit.get(e.trade().tradeId()).gapExitType()!=null).count();
        long below=entries.stream().filter(e->e.trade().resultR()!=null&&e.trade().resultR().compareTo(BigDecimal.ONE.negate())<0).count();
        long above=entries.stream().filter(e->e.trade().resultR()!=null&&e.trade().resultR().compareTo(e.trade().targetR())>0).count();
        if(!hasAudit||known!=entries.size())return new Group(section,label,new Stats(entries.size(),known,entries.size()-known,
            null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,below,above,null,null,null,null));
        var gaps=entries.stream().filter(e->audit.get(e.trade().tradeId()).gapExitType()!=NONE).toList();
        long stops=count(gaps,audit,true,null),targets=count(gaps,audit,false,null);
        var percentages=gaps.stream().map(e->audit.get(e.trade().tradeId()).gapSizePercent()).filter(Objects::nonNull).sorted().toList();
        var rs=gaps.stream().map(e->audit.get(e.trade().tradeId()).gapSizeR()).filter(Objects::nonNull).sorted().toList();
        BigDecimal commissions=sum(gaps.stream().map(it.andreademartini.trading.backtest.execution.ExecutionRunView.Execution::commission).toList());
        BigDecimal slippage=sum(gaps.stream().map(it.andreademartini.trading.backtest.execution.ExecutionRunView.Execution::slippageCost).toList());
        BigDecimal other=sum(gaps.stream().map(it.andreademartini.trading.backtest.execution.ExecutionRunView.Execution::transactionCosts).toList());
        BigDecimal grossAbsolute=sum(gaps.stream().map(e->e.trade().grossPnl().abs()).toList());
        return new Group(section,label,new Stats(entries.size(),known,0,stops,targets,
            GapExecutionAudit.ratio(BigDecimal.valueOf(gaps.size()*100L),BigDecimal.valueOf(entries.size())),
            count(gaps,audit,true,PatternDirection.LONG),count(gaps,audit,false,PatternDirection.LONG),
            count(gaps,audit,true,PatternDirection.SHORT),count(gaps,audit,false,PatternDirection.SHORT),
            pnl(gaps,audit,true),pnl(gaps,audit,false),average(percentages),median(percentages),max(percentages),average(rs),max(rs),
            gaps.stream().filter(e->audit.get(e.trade().tradeId()).gapSizePercent()!=null&&audit.get(e.trade().tradeId()).gapSizeR()!=null).count(),
            below,above,commissions,slippage,other,
            GapExecutionAudit.ratio(commissions.add(slippage).add(other).multiply(BigDecimal.valueOf(100)),grossAbsolute)));
    }
    private static long count(List<it.andreademartini.trading.backtest.execution.ExecutionRunView.Execution> values,Map<String,GapExecutionAudit.TradeAudit> audit,boolean stop,PatternDirection direction){
        return values.stream().filter(e->audit.get(e.trade().tradeId()).gapExitType()==(stop?GAP_STOP:GAP_TARGET)&&(direction==null||e.trade().direction()==direction)).count();
    }
    private static BigDecimal pnl(List<it.andreademartini.trading.backtest.execution.ExecutionRunView.Execution> values,Map<String,GapExecutionAudit.TradeAudit> audit,boolean stop){
        return sum(values.stream().filter(e->audit.get(e.trade().tradeId()).gapExitType()==(stop?GAP_STOP:GAP_TARGET)).map(e->e.trade().netPnl()).toList());
    }
    private static BigDecimal sum(List<BigDecimal> values){return values.stream().reduce(BigDecimal.ZERO,BigDecimal::add);}
    private static BigDecimal average(List<BigDecimal> values){return values.isEmpty()?null:GapExecutionAudit.ratio(sum(values),BigDecimal.valueOf(values.size()));}
    private static BigDecimal median(List<BigDecimal> sorted){int n=sorted.size();return n==0?null:n%2==1?sorted.get(n/2):sorted.get(n/2-1).add(sorted.get(n/2)).divide(BigDecimal.valueOf(2));}
    private static BigDecimal max(List<BigDecimal> sorted){return sorted.isEmpty()?null:sorted.getLast();}
}
