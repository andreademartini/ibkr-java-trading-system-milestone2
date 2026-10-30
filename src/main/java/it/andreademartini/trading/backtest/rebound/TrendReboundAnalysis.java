package it.andreademartini.trading.backtest.rebound;

import java.util.*;
import java.time.temporal.ChronoUnit;
import static it.andreademartini.trading.backtest.rebound.TrendReboundRun.*;

public final class TrendReboundAnalysis {
    private TrendReboundAnalysis(){}
    public static Map<String,Object> metrics(List<Trade> trades){
        var xs=trades.stream().filter(t->t.exitDate()!=null).toList();double gain=xs.stream().mapToDouble(Trade::netPnL).filter(n->n>0).sum(),loss=-xs.stream().mapToDouble(Trade::netPnL).filter(n->n<0).sum();
        var result=new LinkedHashMap<String,Object>();result.put("closedTrades",xs.size());result.put("netPnL",gain-loss);result.put("winRatePercent",xs.isEmpty()?null:100.0*xs.stream().filter(t->t.netPnL()>0).count()/xs.size());result.put("profitFactor",loss>0?gain/loss:null);result.put("expectancyR",xs.isEmpty()?null:xs.stream().mapToDouble(Trade::netR).average().orElseThrow());return result;
    }
    public static Map<String,Object> calculate(TrendReboundConfiguration c,List<Trade> trades,List<Equity> curve){
        var performance=metrics(trades);double finalEquity=curve.getLast().equity(),closed=trades.stream().filter(t->t.exitDate()!=null).mapToDouble(Trade::netPnL).sum(),open=trades.stream().filter(t->t.exitDate()==null).mapToDouble(t->t.unrealizedNetPnL()==null?0:t.unrealizedNetPnL()).sum();
        performance.put("initialCapital",c.initialCapital());performance.put("finalEquity",finalEquity);performance.put("portfolioNetPnL",finalEquity-c.initialCapital());performance.put("unrealizedNetPnL",open);performance.put("openPositions",trades.stream().filter(t->t.exitDate()==null).count());performance.put("maxDrawdownPercent",curve.stream().mapToDouble(Equity::drawdownPercent).max().orElseThrow());performance.put("effectiveStartDate",curve.getFirst().date());performance.put("effectiveEndDate",curve.getLast().date());
        double years=ChronoUnit.DAYS.between(curve.getFirst().date(),curve.getLast().date())/365.25;performance.put("cagrPercent",years>0&&finalEquity>0?100*(Math.pow(finalEquity/c.initialCapital(),1/years)-1):null);
        performance.put("commissions",trades.stream().mapToDouble(t->t.entryCommission()+t.exitCommission()).sum());performance.put("slippageCosts",trades.stream().mapToDouble(Trade::slippageCosts).sum());performance.put("meanExposurePercent",curve.stream().mapToDouble(Equity::exposurePercent).average().orElseThrow());
        var yearsRows=new ArrayList<Map<String,Object>>();var yearGroups=new TreeMap<Integer,List<Equity>>();curve.forEach(e->yearGroups.computeIfAbsent(e.date().getYear(),k->new ArrayList<>()).add(e));double previous=c.initialCapital();
        for(var group:yearGroups.entrySet()){var row=new LinkedHashMap<String,Object>();row.put("year",group.getKey());row.put("openingEquity",previous);double ending=group.getValue().getLast().equity();row.put("closingEquity",ending);row.put("portfolioNetPnL",ending-previous);row.put("returnPercent",previous>0?(ending/previous-1)*100:null);row.put("closedTradeMetrics",metrics(trades.stream().filter(t->t.exitDate()!=null&&t.exitDate().getYear()==group.getKey()).toList()));yearsRows.add(row);previous=ending;}
        double delta=finalEquity-c.initialCapital()-closed-open;var result=new LinkedHashMap<String,Object>();result.put("performance",performance);result.put("annualResults",yearsRows);result.put("reconciliation",Map.of("initialCapital",c.initialCapital(),"closedTradeNetPnL",closed,"openPositionNetPnL",open,"finalEquity",finalEquity,"difference",delta,"passed",Math.abs(delta)<Math.max(1e-7,Math.abs(finalEquity)*1e-10)));result.put("undefinedMetrics","N/A: no closed trades, no losing trades (profit factor), or zero date span (CAGR); never fabricated as zero.");return result;
    }
}
