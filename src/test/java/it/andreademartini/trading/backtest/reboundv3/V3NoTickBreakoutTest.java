package it.andreademartini.trading.backtest.reboundv3;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import static org.junit.jupiter.api.Assertions.*;

class V3NoTickBreakoutTest {
 static TrendReboundV3Run run(double open,double high,double low,double close,Object... changes){
  var bars=TrendReboundV3EngineTest.series("TEST");bars.set(10,TrendReboundV3EngineTest.bar("TEST",10,open,high,low,close));
  var c=TrendReboundV3EngineTest.config(changes).asNoTickEntry();var input=Map.of("TEST",bars);
  return TrendReboundV3EngineTest.engine().simulate(c,input,it.andreademartini.trading.backtest.patternv2.PatternV2Dataset.capture(input).id(),it.andreademartini.trading.backtest.weeklytte.WeeklyTteStore.hash(c));
 }
 @Test void currentDefaultsRequireNeitherTicksNorResearchAcknowledgement(){
  var c=TrendReboundV3Configuration.defaults();c.validate();assertTrue(c.noTickEntry());assertEquals(0,c.entryBufferTicks());assertEquals("NONE",c.entryBufferMode());assertEquals("NOT_REQUIRED",c.minimumTickResolutionPolicy());assertTrue(c.minimumTickSizes().isEmpty());assertFalse(c.researchDatasetGridAcknowledged());
 }
 @Test void breakEntersAtThirdHighWithSlippageOnceAndOriginalStop(){
  var r=run(106.5,112,106,111,"slippagePercentPerSide",.03);var a=r.signals().getFirst();var t=r.trades().getFirst();
  assertEquals(108,t.entryBasePrice());assertEquals(108*1.0003,t.entryExecutionPrice(),1e-10);assertEquals(t.entryExecutionPrice()-1.5*t.atrAtSignal(),t.initialStop(),1e-10);assertEquals(108.0,((Number)a.diagnostics.get("BuyStopTrigger")).doubleValue());assertNull(a.diagnostics.get("MinimumTick"));assertEquals("NOT_REQUIRED",a.diagnostics.get("TickResolutionStatus"));assertFalse(r.analysis().containsKey("minimumTickPrevalidation"));assertEquals(true,((Map<?,?>)r.analysis().get("reconciliation")).get("passed"));
 }
 @Test void touchingHighDoesNotBreakIt(){
  for(double high:List.of(107.99,108.0,108.001)){var r=run(106.5,high,106,107);var a=r.signals().getFirst();assertEquals(high>108,a.tradeId!=null);assertEquals(high>108,a.diagnostics.get("EntryTriggerReached"));if(high<=108)assertEquals("ENTRY_NOT_TRIGGERED",a.reason);}
 }
 @Test void openAtOrAboveHighStillRejects(){
  for(double open:List.of(108.0,108.5)){var a=run(open,112,106,111).signals().getFirst();assertNull(a.tradeId);assertEquals("ENTRY_REJECTED_OPEN_AT_OR_ABOVE_PREVIOUS_HIGH",a.reason);}
 }
 @Test void fifthBarCannotTriggerExpiredSignal(){
  var bars=TrendReboundV3EngineTest.series("TEST");bars.set(10,TrendReboundV3EngineTest.bar("TEST",10,106.5,108,106,107));bars.set(11,TrendReboundV3EngineTest.bar("TEST",11,107,150,106,149));var input=Map.of("TEST",bars);var c=TrendReboundV3EngineTest.config().asNoTickEntry();
  var r=TrendReboundV3EngineTest.engine().simulate(c,input,it.andreademartini.trading.backtest.patternv2.PatternV2Dataset.capture(input).id(),it.andreademartini.trading.backtest.weeklytte.WeeklyTteStore.hash(c));assertNull(r.signals().getFirst().tradeId);assertEquals(TrendReboundV3EngineTest.day(10),r.signals().getFirst().attemptDate);
 }
 @Test void intrabarConservatismAndRecoveryTimingRemainUnchanged(){
  var stopped=run(106.5,120,80,111);assertEquals("STOP_LOSS",stopped.trades().getFirst().exitReason());assertNotNull(stopped.signals().getFirst().diagnostics.get("IntrabarAmbiguity"));
  var recovered=run(106.5,112,106,111);assertEquals("RECOVERY",recovered.trades().getFirst().exitReason());assertEquals(TrendReboundV3EngineTest.day(11),recovered.trades().getFirst().exitDate());
  var timeout=run(106.5,109,106,106,"stopLossAtrMultiplier",5);assertEquals("TIMEOUT",timeout.trades().getFirst().exitReason());assertEquals(5,timeout.trades().getFirst().holdingBars());
 }
 @Test void excelKeepsProfitSpaceAndSummaryWithoutTickAudit()throws Exception{
  var r=run(106.5,112,106,111);try(var book=new XSSFWorkbook(new ByteArrayInputStream(new TrendReboundV3ExcelExporter().export(r)))){assertNull(book.getSheet("MINIMUM_TICK_AUDIT"));for(String name:List.of("TRADES","ENTRY_SIGNAL_AUDIT","PROFIT_SPACE_ANALYSIS","PROFIT_SPACE_BY_SYMBOL","PORTFOLIO_SUMMARY"))assertNotNull(book.getSheet(name));}
 }
}
