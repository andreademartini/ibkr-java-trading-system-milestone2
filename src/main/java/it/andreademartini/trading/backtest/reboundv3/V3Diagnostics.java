package it.andreademartini.trading.backtest.reboundv3;

import java.util.*;
import java.time.*;
import java.io.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.backtest.rebound.*;
import static it.andreademartini.trading.backtest.reboundv3.TrendReboundV3Run.*;

/** Causal chart references and retrospective DAILY upper bounds, never execution targets. */
final class V3Diagnostics {
    static void signal(TrendReboundV3Configuration c,Signal a,Map<LocalDate,HistoricalBar> data,HistoricalBar b){
        var d=a.diagnostics;d.put("SignalDate",a.signalDate);d.put("ATR14AtSignal",a.atr);
        d.put("ThirdBarOpen",b.open());d.put("ThirdBarHigh",b.high());d.put("ThirdBarLow",b.low());d.put("ThirdBarClose",b.close());
        if(!c.enableProfitSpaceAnalysis())return;
        var end=a.signalDate;
        for(int i=0;i<3;i++){do{end=end.minusDays(1);}while(!it.andreademartini.trading.backtest.patternv3.ResearchSessionCalendar.isSession(end));}
        HistoricalBar highest=null;
        for(int i=0;i<c.prePullbackHighLookbackBars();i++){
            var x=data.get(end);if(x==null){d.put("ProfitSpaceStatus","PRE_PULLBACK_HIGH_UNAVAILABLE");return;}
            if(highest==null||x.high()>highest.high())highest=x;
            do{end=end.minusDays(1);}while(!it.andreademartini.trading.backtest.patternv3.ResearchSessionCalendar.isSession(end));
        }
        d.put("PrePullbackHigh",highest.high());d.put("PrePullbackHighDate",highest.dateTime().toLocalDate());d.put("ProfitSpaceStatus","AVAILABLE");
    }
    static void enrich(TrendReboundV3Configuration c,List<Signal> signals,List<TrendReboundRun.Trade> trades,Map<String,Map<LocalDate,HistoricalBar>> data){
        for(var a:signals){
            var fourth=data.get(a.symbol).get(a.plannedEntryDate);var d=a.diagnostics;
            var third=data.get(a.symbol).get(a.signalDate);
            if(fourth!=null){d.put("FourthBarDate",a.plannedEntryDate);d.put("FourthBarOpen",fourth.open());d.put("FourthBarHigh",fourth.high());d.put("FourthBarLow",fourth.low());d.put("FourthBarClose",fourth.close());d.put("EntryOpenConditionPassed",fourth.open()<third.high());}
            var tick=c.minimumTickSizes().get(a.symbol);
            if(tick!=null){double trigger=java.math.BigDecimal.valueOf(third.high()).add(java.math.BigDecimal.valueOf(tick).multiply(java.math.BigDecimal.valueOf(c.entryBufferTicks()))).doubleValue();d.put("BuyStopTrigger",trigger);if(fourth!=null)d.put("EntryTriggerReached",fourth.high()>=trigger);}
        }
        for(var t:trades){
            var a=signals.stream().filter(s->s.signalId.equals(t.signalId())).findFirst().orElseThrow();var d=a.diagnostics;
            double target=t.entryExecutionPrice()+c.analysisTakeProfitAtrMultiplier()*a.atr;
            d.put("EntryBasePrice",t.entryBasePrice());d.put("EntryExecutionPrice",t.entryExecutionPrice());d.put("TheoreticalTakeProfit1_5ATR",target);
            Double previous=d.get("PrePullbackHigh") instanceof Number n?n.doubleValue():null;
            if(previous!=null){double space=previous-t.entryExecutionPrice();d.put("ProfitSpaceToPreviousHigh",space);d.put("ProfitSpaceATR",space/a.atr);d.put("HasRoomForTakeProfit1_5ATR",previous>=target);d.put("PotentialRewardRiskToPreviousHigh",space/(t.entryExecutionPrice()-t.initialStop()));}
            double high=t.entryExecutionPrice();boolean ambiguous=false;
            LocalDate targetDate=null,previousDate=null;
            for(var e:new TreeMap<>(data.get(t.symbol())).entrySet()){
                var day=e.getKey();var b=e.getValue();if(day.isBefore(t.entryDate())||(t.exitDate()!=null&&day.isAfter(t.exitDate())))continue;
                boolean exit=day.equals(t.exitDate()),openingExit=exit&&(t.exitReason().equals("RECOVERY")||t.exitReason().equals("GAP_STOP"));
                double observed=openingExit?b.open():b.high();
                boolean uncertain=exit&&t.exitReason().equals("STOP_LOSS");
                if(uncertain)ambiguous=true;
                high=Math.max(high,observed);
                if(observed>=target&&targetDate==null)targetDate=day;
                if(previous!=null&&observed>=previous&&previousDate==null)previousDate=day;
            }
            d.put("HighestHighDuringTrade",high);d.put("MaxFavorableExcursion",high-t.entryExecutionPrice());d.put("MaxFavorableExcursionATR",(high-t.entryExecutionPrice())/a.atr);
            d.put("TheoreticalATRTargetTouched",targetDate!=null);d.put("FirstATRTargetTouchDate",targetDate);d.put("PreviousHighTouchedAfterEntry",previous==null?null:previousDate!=null);d.put("FirstPreviousHighTouchDate",previousDate);
            d.put("MFEIsDailyUpperBound",ambiguous||t.dataStatus().equals("DATA_GAP_UNRESOLVED"));
            d.put("TargetTouchExecutionAmbiguous",ambiguous&&targetDate!=null&&targetDate.equals(t.exitDate()));
            d.put("ExitReason",t.exitReason());d.put("NetPnL",t.netPnL());d.put("NetR",t.netR());
        }
    }
    static Map<String,Object> stats(TrendReboundV3Run r,List<TrendReboundRun.Trade> trades){
        var m=new LinkedHashMap<>(TrendReboundAnalysis.metrics(trades));m.put("StatisticsType","DESCRIPTIVE_SUBSET_NOT_RESIMULATED_PORTFOLIO");
        var closed=trades.stream().filter(t->t.exitDate()!=null).toList();
        for(String reason:List.of("RECOVERY","STOP_LOSS","TIMEOUT"))m.put(reason+"Percent",closed.isEmpty()?null:100.0*closed.stream().filter(t->reason.equals(t.exitReason())||(reason.equals("STOP_LOSS")&&t.exitReason().equals("GAP_STOP"))).count()/closed.size());
        m.put("AverageMFE",trades.stream().mapToDouble(t->((Number)diag(r,t).get("MaxFavorableExcursion")).doubleValue()).average().orElse(Double.NaN));
        m.put("MFEAtLeast1_5ATRPercent",trades.isEmpty()?null:100.0*trades.stream().filter(t->((Number)diag(r,t).get("MaxFavorableExcursionATR")).doubleValue()>=r.configuration().analysisTakeProfitAtrMultiplier()).count()/trades.size());
        m.put("MFENote","DAILY upper bounds on stop bars; target touch does not establish realizable profit");return m;
    }
    static Map<String,Object> diag(TrendReboundV3Run r,TrendReboundRun.Trade t){return r.signals().stream().filter(s->s.signalId.equals(t.signalId())).findFirst().orElseThrow().diagnostics;}
    static void sheets(SXSSFWorkbook book,TrendReboundV3Run r)throws IOException{
        if(r.analysis().get("entryModeComparison") instanceof List<?> comparison)TrendReboundV3ExcelExporter.table(book,"ENTRY_MODE_COMPARISON",(List<Map<String,Object>>)(List<?>)comparison,List.of("EntryMode","SignalsDetected","TradesExecuted","TradesRejected"));
        var audit=new ArrayList<Map<String,Object>>();
        for(var a:r.signals()){var m=new LinkedHashMap<String,Object>(a.diagnostics);m.put("SignalId",a.signalId);m.put("Symbol",a.symbol);m.put("TradeExecuted","EXECUTED".equals(a.status));m.put("TradeId",a.tradeId);m.put("RejectionReason",a.reason);m.put("OpenConditionPassed",m.get("EntryOpenConditionPassed"));m.put("TriggerReached",m.get("EntryTriggerReached"));audit.add(m);}
        TrendReboundV3ExcelExporter.table(book,"ENTRY_SIGNAL_AUDIT",audit,List.of("SignalId","Symbol","SignalDate","ThirdBarHigh","FourthBarOpen","FourthBarHigh","BuyStopTrigger","OpenConditionPassed","TriggerReached","TradeExecuted","TradeId","RejectionReason"));
        double[] edges={Double.NEGATIVE_INFINITY,0,.5,1,1.5,2,3,Double.POSITIVE_INFINITY};
        var bins=new ArrayList<Map<String,Object>>();
        for(int i=0;i<edges.length-1;i++){double lo=edges[i],hi=edges[i+1];var xs=r.trades().stream().filter(t->diag(r,t).get("ProfitSpaceATR") instanceof Number n&&n.doubleValue()>=lo&&n.doubleValue()<hi).toList();var m=stats(r,xs);m.put("Band","["+lo+", "+hi+")");bins.add(m);}
        var missing=r.trades().stream().filter(t->!diag(r,t).containsKey("ProfitSpaceATR")).toList();var miss=stats(r,missing);miss.put("Band","UNAVAILABLE");bins.add(miss);
        TrendReboundV3ExcelExporter.table(book,"PROFIT_SPACE_ANALYSIS",bins,List.of("Band","closedTrades","winRatePercent","profitFactor","netPnL","expectancyR"));
        var symbols=new ArrayList<Map<String,Object>>();
        for(String symbol:r.configuration().symbols()){
            var ts=r.trades().stream().filter(t->t.symbol().equals(symbol)).toList();var m=new LinkedHashMap<String,Object>();m.put("Symbol",symbol);m.put("TotalTrades",ts.size());
            for(boolean room:List.of(true,false)){var xs=ts.stream().filter(t->Boolean.valueOf(room).equals(diag(r,t).get("HasRoomForTakeProfit1_5ATR"))).toList();String suffix=room?"WithRoom":"WithoutRoom";var sm=stats(r,xs);m.put(room?"TradesWithRoomFor1_5ATR":"TradesWithoutRoomFor1_5ATR",xs.size());m.put("WinRate"+suffix,sm.get("winRatePercent"));m.put("ProfitFactor"+suffix,sm.get("profitFactor"));m.put("NetPnL"+suffix,sm.get("netPnL"));}
            m.put("AverageProfitSpaceATR",ts.stream().filter(t->diag(r,t).get("ProfitSpaceATR") instanceof Number).mapToDouble(t->((Number)diag(r,t).get("ProfitSpaceATR")).doubleValue()).average().orElse(Double.NaN));m.put("AverageNetR",TrendReboundAnalysis.metrics(ts).get("expectancyR"));symbols.add(m);
        }
        TrendReboundV3ExcelExporter.table(book,"PROFIT_SPACE_BY_SYMBOL",symbols,List.of("Symbol","TotalTrades"));
    }
}
