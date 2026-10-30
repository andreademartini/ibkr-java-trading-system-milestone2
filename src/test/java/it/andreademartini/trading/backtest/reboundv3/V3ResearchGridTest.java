package it.andreademartini.trading.backtest.reboundv3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import it.andreademartini.trading.backtest.*;
import it.andreademartini.trading.backtest.patternv2.PatternV2Dataset;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.marketdata.ticks.MinimumTickResolver;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayInputStream;

class V3ResearchGridTest {
 @TempDir Path temp;
 static TrendReboundV3Configuration config(boolean accepted){
  var json=TrendReboundV3Store.JSON;var node=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(TrendReboundV3EngineTest.config());
  node.put("name","SYNTHETIC explicit research grid");node.put("minimumTickResolutionPolicy","RESEARCH_DATASET_GRID");node.put("researchDatasetGridAcknowledged",accepted);
  node.set("minimumTickSizes",json.valueToTree(Map.of()));node.set("minimumTickMetadata",json.valueToTree(MinimumTickResolver.Catalog.empty()));
  return json.convertValue(node,TrendReboundV3Configuration.class);
 }
 static TrendReboundV3Run simulate(TrendReboundV3Configuration c,Map<String,List<HistoricalBar>> input){return TrendReboundV3EngineTest.engine().simulate(c,input,PatternV2Dataset.capture(input).id(),it.andreademartini.trading.backtest.weeklytte.WeeklyTteStore.hash(c));}
 static List<HistoricalBar> data(){
  var bars=TrendReboundV3EngineTest.series("TEST");
  bars.set(9,TrendReboundV3EngineTest.bar("TEST",9,107.5,108.005,106,107));
  bars.set(10,TrendReboundV3EngineTest.bar("TEST",10,106.5,112,106,111));return bars;
 }
 @Test void researchIsOptInAndDefaultStillStrict(){
  assertEquals("NOT_REQUIRED",TrendReboundV3Configuration.defaults().minimumTickResolutionPolicy());assertTrue(TrendReboundV3Configuration.defaults().noTickEntry());
  assertFalse(TrendReboundV3Configuration.defaults().researchDatasetGridAcknowledged());
  assertThrows(IllegalArgumentException.class,()->config(false).validate());config(true).validate();
 }
 @Test void acceptedResearchRunsWithoutFakeInstrumentMetadataAndReconciles(){
  var input=Map.of("TEST",data());var c=config(true);var report=V3TickSupport.prevalidate(c,input,PatternV2Dataset.capture(input).id());assertTrue(report.passed());
  assertTrue(report.rows().stream().allMatch(r->"RESEARCH_ASSUMPTION".equals(r.get("ResolutionStatus"))));
  var run=simulate(c,input);var trade=run.trades().getFirst();assertEquals(108.006,trade.entryBasePrice(),1e-10);
  assertEquals(trade.entryExecutionPrice()-1.5*trade.atrAtSignal(),trade.initialStop(),1e-10);
  assertEquals("CSV_DECIMAL_GRID_NOT_MARKET_TICK",run.signals().getFirst().diagnostics.get("MinimumTickSource"));
  assertEquals(true,((Map<?,?>)run.analysis().get("reconciliation")).get("passed"));assertTrue(run.warnings().stream().anyMatch(w->w.contains("NOT a verified market tick")));
 }
 @Test void futurePricePrecisionCannotContaminateEarlierTrigger(){
  var bars=data();var input=Map.of("TEST",bars);var before=TrendReboundV3EngineTest.simulate(config(true),input);
  bars.set(11,TrendReboundV3EngineTest.bar("TEST",11,105.5,900.123456789,104,105));var after=TrendReboundV3EngineTest.simulate(config(true),Map.of("TEST",bars));
  assertEquals(before.trades().getFirst().entryBasePrice(),after.trades().getFirst().entryBasePrice());
  assertEquals(0,new BigDecimal("0.001").compareTo((BigDecimal)after.signals().getFirst().diagnostics.get("MinimumTick")));
 }
 @Test void researchSnapshotReplaysAndExcelLabelsAssumptions()throws Exception{
  var store=new TrendReboundV3Store(temp);var service=new TrendReboundV3Service(store,new BacktestEngine(null),TrendReboundV3EngineTest.engine());
  var run=service.onSnapshot(config(true),PatternV2Dataset.capture(Map.of("TEST",data())));assertEquals(run.trades(),service.replay(run.runId()).trades());
  try(var book=new XSSFWorkbook(new ByteArrayInputStream(new TrendReboundV3ExcelExporter().export(run)))){assertNotNull(book.getSheet("MINIMUM_TICK_AUDIT"));assertNotNull(book.getSheet("PORTFOLIO_SUMMARY"));assertTrue(book.getSheet("NOTES").getRow(1).getCell(0).getStringCellValue().contains("NOT a verified market tick"));}
 }
}
