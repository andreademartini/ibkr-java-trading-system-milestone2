package it.andreademartini.trading.backtest.reboundv3;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import it.andreademartini.trading.risk.*;
import it.andreademartini.trading.backtest.BacktestEngine;
import static org.junit.jupiter.api.Assertions.*;
class V3LocalReportTest {
 @Test void exportActualLocalDataWithoutInventingTicks()throws Exception{
 var json=TrendReboundV3Store.JSON;
 var c=new TrendReboundV3Store().current();
 var folder=Path.of("outputs","trend-pullback-rebound-v3-tick-resolution");Files.createDirectories(folder);
 var store=new TrendReboundV3Store(folder.resolve("store"));
 var service=new TrendReboundV3Service(store,new BacktestEngine(null),new TrendReboundV3Engine(new PositionSizingService(new TradePricingService())));
 var report=service.prevalidate(c);
 if(!report.passed()){
  assertThrows(V3TickSupport.ValidationException.class,()->service.run(c));
  var bytes=new TrendReboundV3ExcelExporter().exportValidation(c,report);Files.write(folder.resolve("V3_MINIMUM_TICK_PREFLIGHT_BLOCKED.xlsx"),bytes);
  try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))){assertNotNull(book.getSheet("MINIMUM_TICK_AUDIT"));assertNotNull(book.getSheet("PORTFOLIO_SUMMARY"));assertEquals(1,book.getSheet("TRADES").getPhysicalNumberOfRows());}
  var summary=new LinkedHashMap<String,Object>();summary.put("backtestExecuted",false);summary.put("configuration",c);summary.put("tickPrevalidation",report);summary.put("performance",null);summary.put("limitation","No trusted instrument/tick/adjustment metadata available. No trading performance fabricated.");
  Files.writeString(folder.resolve("tick-resolution-summary.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(summary));return;
 }
 var run=service.run(c);assertEquals(true,((Map<?,?>)run.analysis().get("reconciliation")).get("passed"));
 Files.write(folder.resolve("TREND_PULLBACK_REBOUND_V3_RESEARCH.xlsx"),new TrendReboundV3ExcelExporter().export(run));
 var summary=new LinkedHashMap<String,Object>();summary.put("runId",run.runId());summary.put("dataset",run.datasetId());summary.put("signals",run.signals().size());summary.put("trades",run.trades().size());
 summary.put("reasons",run.signals().stream().collect(java.util.stream.Collectors.groupingBy(a->Objects.toString(a.reason,"PENDING"),TreeMap::new,java.util.stream.Collectors.counting())));
 summary.put("analysis",run.analysis());summary.put("limitation","No instrument tick metadata available. V3 entries rejected rather than inventing a universal tick.");
 Files.writeString(folder.resolve("summary.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
 }
}
