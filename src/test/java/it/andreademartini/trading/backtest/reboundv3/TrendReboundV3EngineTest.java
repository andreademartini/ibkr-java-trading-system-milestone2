package it.andreademartini.trading.backtest.reboundv3;

import it.andreademartini.trading.backtest.*;
import it.andreademartini.trading.backtest.patternv1.PatternV1Commission;
import it.andreademartini.trading.backtest.patternv2.*;
import it.andreademartini.trading.backtest.weeklytte.WeeklyTteStore;
import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.risk.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TrendReboundV3EngineTest {
    @TempDir Path temp;
    static final LocalDate FIRST=LocalDate.of(2024,1,2);
    static LocalDate day(int index){var d=FIRST;for(int i=0;i<index;i++)d=TrendReboundV3Engine.nextSession(d);return d;}
    static TrendReboundV3Configuration config(Object... changes){ObjectNode n=TrendReboundV3Store.JSON.valueToTree(TrendReboundV3Configuration.defaults());n.put("startDate",day(8).toString());n.put("endDate",day(17).toString());n.set("symbols",TrendReboundV3Store.JSON.valueToTree(List.of("TEST")));n.put("trendSmaPeriod",8);n.put("recoverySmaPeriod",3);n.put("atrPeriod",3);n.set("minimumTickSizes",TrendReboundV3Store.JSON.valueToTree(Map.of("TEST",0.01)));n.put("prePullbackHighLookbackBars",3);n.put("slippagePercentPerSide",0);n.set("commission",TrendReboundV3Store.JSON.valueToTree(PatternV1Commission.fixed(BigDecimal.ZERO)));n.set("splitCosts",TrendReboundV3Store.JSON.valueToTree(new PatternV2SplitCosts(List.of())));for(int i=0;i<changes.length;i+=2)n.set(changes[i].toString(),TrendReboundV3Store.JSON.valueToTree(changes[i+1]));return TrendReboundV3Store.JSON.convertValue(n,TrendReboundV3Configuration.class);}
    static HistoricalBar bar(String symbol,int index,double open,double high,double low,double close){return new HistoricalBar(symbol,day(index).atTime(16,0),BigDecimal.valueOf(open),BigDecimal.valueOf(high),BigDecimal.valueOf(low),BigDecimal.valueOf(close),1000,BarInterval.DAILY,"TEST",PriceAdjustment.ADJUSTED);}
    static List<HistoricalBar> series(String symbol){double[] closes={90,91,92,93,94,95,110,109,108,107,106,105,104,103,102,101,100,99};var bars=new ArrayList<HistoricalBar>();for(int i=0;i<closes.length;i++){double close=closes[i];bars.add(bar(symbol,i,close+0.5,close+1,close-1,close));}return bars;}
    static TrendReboundV3Engine engine(){return new TrendReboundV3Engine(new PositionSizingService(new TradePricingService()));}
    static TrendReboundV3Run simulate(TrendReboundV3Configuration c,Map<String,List<HistoricalBar>> input){return engine().simulate(c,input,PatternV2Dataset.capture(input).id(),WeeklyTteStore.hash(c));}
    static TrendReboundV3Run run(Object... changes){return simulate(config(changes),Map.of("TEST",series("TEST")));}

 @Test void breakoutAndProfitSpace()throws Exception{
 var bars=series("TEST");bars.set(10,bar("TEST",10,106.5,112,106,111));
 var r=simulate(config("slippagePercentPerSide",0.03),Map.of("TEST",bars));var a=r.signals().getFirst();var t=r.trades().getFirst();
 assertEquals(day(9),a.signalDate);assertEquals(List.of(110.0,109.0,108.0,107.0),a.closes);
 assertEquals(108.01,t.entryBasePrice(),1e-9);assertEquals(108.01*1.0003,t.entryExecutionPrice(),1e-9);
 assertEquals(t.entryExecutionPrice()-1.5*a.atr,t.initialStop(),1e-9);
 assertEquals(111.0,a.diagnostics.get("PrePullbackHigh"));assertEquals(day(6),a.diagnostics.get("PrePullbackHighDate"));
 assertEquals((111-t.entryExecutionPrice())/a.atr,((Number)a.diagnostics.get("ProfitSpaceATR")).doubleValue(),1e-9);
 assertEquals(t.entryExecutionPrice()+1.5*a.atr,((Number)a.diagnostics.get("TheoreticalTakeProfit1_5ATR")).doubleValue(),1e-9);
 assertEquals(true,((Map<?,?>)r.analysis().get("reconciliation")).get("passed"));
 assertEquals("RECOVERY",t.exitReason());assertEquals(day(11),t.exitDate());
 try(var book=new XSSFWorkbook(new ByteArrayInputStream(new TrendReboundV3ExcelExporter().export(r)))){for(String name:List.of("ENTRY_SIGNAL_AUDIT","PROFIT_SPACE_ANALYSIS","PROFIT_SPACE_BY_SYMBOL","TRADES","CONFIGURATION"))assertNotNull(book.getSheet(name));assertEquals(r.signals().size()+1,book.getSheet("ENTRY_SIGNAL_AUDIT").getPhysicalNumberOfRows());}
 }
 @Test void equalAndHigherOpenRejected(){for(double open:List.of(108.0,108.5)){var bars=series("TEST");bars.set(10,bar("TEST",10,open,112,106,107));var r=simulate(config(),Map.of("TEST",bars));assertEquals("ENTRY_REJECTED_OPEN_AT_OR_ABOVE_PREVIOUS_HIGH",r.signals().getFirst().reason);}}
 @Test void fourthBarOnlyAndTickIsInstrumentSpecific(){var bars=series("TEST");bars.set(10,bar("TEST",10,107,108.02,106,107));bars.set(11,bar("TEST",11,107,120,106,119));var r=simulate(config("minimumTickSizes",Map.of("TEST",0.05)),Map.of("TEST",bars));assertEquals("ENTRY_NOT_TRIGGERED",r.signals().getFirst().reason);assertNull(r.signals().getFirst().tradeId);}
 @Test void conservativeAmbiguousStop(){var bars=series("TEST");bars.set(10,bar("TEST",10,106,120,80,110));var r=simulate(config(),Map.of("TEST",bars));var t=r.trades().getFirst();assertEquals("STOP_LOSS",t.exitReason());assertEquals(t.entryDate(),t.exitDate());assertEquals(t.initialStop(),t.exitBasePrice());assertNotNull(r.signals().getFirst().diagnostics.get("IntrabarAmbiguity"));assertEquals(true,r.signals().getFirst().diagnostics.get("TargetTouchExecutionAmbiguous"));}
 @Test void causalAtrAndSmaAndNoOperationalTargets(){var bars=series("TEST");bars.set(10,bar("TEST",10,106,112,106,111));var r=simulate(config(),Map.of("TEST",bars));var prefix=simulate(config("endDate",day(9)),Map.of("TEST",bars));assertEquals(prefix.signals().getFirst().atr,r.signals().getFirst().atr);assertEquals(prefix.signals().getFirst().trendSma,r.signals().getFirst().trendSma);var other=simulate(config("analysisTakeProfitAtrMultiplier",100),Map.of("TEST",bars));assertEquals(r.trades().getFirst().netPnL(),other.trades().getFirst().netPnL());}
 @Test void missingTickDoesNotInventFill(){var r=run("minimumTickSizes",Map.of());assertTrue(r.trades().isEmpty());assertEquals("MINIMUM_TICK_UNAVAILABLE",r.signals().getFirst().reason);}
 @Test void replayDeterministic(){var store=new TrendReboundV3Store(temp);var service=new TrendReboundV3Service(store,new BacktestEngine(null),engine());var bars=series("TEST");bars.set(10,bar("TEST",10,106,112,106,111));var r=service.onSnapshot(config(),PatternV2Dataset.capture(Map.of("TEST",bars)));assertEquals(r.trades(),service.replay(r.runId()).trades());}
 @Test void timeoutCountsEntrySessionAsOne(){var bars=series("TEST");bars.set(10,bar("TEST",10,106,109,106,106));var t=simulate(config("stopLossAtrMultiplier",5),Map.of("TEST",bars)).trades().getFirst();assertEquals(5,t.holdingBars());assertEquals(day(14),t.exitDate());assertEquals("TIMEOUT",t.exitReason());}
 @Test void insufficientFundsRejectAndDoNotDefer(){var bars=series("TEST");bars.set(10,bar("TEST",10,106,112,106,111));var r=simulate(config("initialCapital",1),Map.of("TEST",bars));assertEquals("INSUFFICIENT_CAPITAL_OR_RISK_BUDGET",r.signals().getFirst().reason);assertNull(r.signals().getFirst().tradeId);}
 @Test void unavailablePreHighRemainsMissing(){var bars=series("TEST");bars.set(10,bar("TEST",10,106,112,106,111));var r=simulate(config("prePullbackHighLookbackBars",100),Map.of("TEST",bars));assertEquals("PRE_PULLBACK_HIGH_UNAVAILABLE",r.signals().getFirst().diagnostics.get("ProfitSpaceStatus"));assertFalse(r.signals().getFirst().diagnostics.containsKey("HasRoomForTakeProfit1_5ATR"));}
 @Test void ascendingCandleBodiesStillAllowDecliningCloses(){var bars=series("TEST");for(int i=7;i<=9;i++){var b=bars.get(i);bars.set(i,bar("TEST",i,b.close()-.5,b.high(),b.low(),b.close()));}var r=simulate(config(),Map.of("TEST",bars));assertEquals(day(9),r.signals().getFirst().signalDate);}
 @Test void smaFilterExcludesBelowTrend(){var bars=series("TEST");var r=simulate(config("trendSmaPeriod",4),Map.of("TEST",bars));assertTrue(r.signals().isEmpty());}
 @Test void noBrokerDependency(){for(var cls:List.of(TrendReboundV3Engine.class,TrendReboundV3Service.class)){for(var field:cls.getDeclaredFields())assertFalse(field.getType().getName().toLowerCase().matches(".*(ibkr|orderservice|paper).*"));}}
}
