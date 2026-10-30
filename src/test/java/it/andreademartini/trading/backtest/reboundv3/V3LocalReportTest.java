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
 var node=json.valueToTree(TrendReboundV3Configuration.defaults());
 ((com.fasterxml.jackson.databind.node.ObjectNode)node).put("endDate","2026-09-02");
 var c=json.treeToValue(node,TrendReboundV3Configuration.class);
 var folder=Path.of("outputs","trend-pullback-rebound-v3-validation");Files.createDirectories(folder);
 var store=new TrendReboundV3Store(folder.resolve("store"));
 var service=new TrendReboundV3Service(store,new BacktestEngine(null),new TrendReboundV3Engine(new PositionSizingService(new TradePricingService())));
 var run=service.run(c);assertEquals(true,((Map<?,?>)run.analysis().get("reconciliation")).get("passed"));
 Files.write(folder.resolve("TREND_PULLBACK_REBOUND_V3_RESEARCH.xlsx"),new TrendReboundV3ExcelExporter().export(run));
 var summary=new LinkedHashMap<String,Object>();summary.put("runId",run.runId());summary.put("dataset",run.datasetId());summary.put("signals",run.signals().size());summary.put("trades",run.trades().size());
 summary.put("reasons",run.signals().stream().collect(java.util.stream.Collectors.groupingBy(a->Objects.toString(a.reason,"PENDING"),TreeMap::new,java.util.stream.Collectors.counting())));
 summary.put("analysis",run.analysis());summary.put("limitation","No instrument tick metadata available. V3 entries rejected rather than inventing a universal tick.");
 Files.writeString(folder.resolve("summary.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
 }
}
