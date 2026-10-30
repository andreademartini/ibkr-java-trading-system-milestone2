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
 var c=new TrendReboundV3Store().current().asNoTickEntry();
 var folder=Path.of("outputs","trend-pullback-rebound-v3-no-tick");Files.createDirectories(folder);
 var store=new TrendReboundV3Store(folder.resolve("store"));
 var service=new TrendReboundV3Service(store,new BacktestEngine(null),new TrendReboundV3Engine(new PositionSizingService(new TradePricingService())));
 var run=service.run(c);assertEquals(true,((Map<?,?>)run.analysis().get("reconciliation")).get("passed"));
 assertTrue(run.configuration().noTickEntry());assertTrue(run.signals().stream().noneMatch(a->"MINIMUM_TICK_UNAVAILABLE".equals(a.reason)));
 Files.write(folder.resolve("TREND_PULLBACK_REBOUND_V3_RESEARCH.xlsx"),new TrendReboundV3ExcelExporter().export(run));
 var summary=new LinkedHashMap<String,Object>();summary.put("configuration",run.configuration());summary.put("backtestExecuted",true);summary.put("runId",run.runId());summary.put("dataset",run.datasetId());summary.put("signals",run.signals().size());summary.put("trades",run.trades().size());
 summary.put("reasons",run.signals().stream().collect(java.util.stream.Collectors.groupingBy(a->Objects.toString(a.reason,"PENDING"),TreeMap::new,java.util.stream.Collectors.counting())));
 summary.put("analysis",run.analysis());summary.put("limitation","No tick buffer: High4 must strictly exceed High3; base entry equals High3 plus existing slippage. DAILY intrabar ambiguity remains conservative.");
 Files.writeString(folder.resolve("summary.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
 }
}
