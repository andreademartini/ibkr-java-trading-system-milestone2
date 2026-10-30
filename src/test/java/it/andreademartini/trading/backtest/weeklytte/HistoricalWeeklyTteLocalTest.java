package it.andreademartini.trading.backtest.weeklytte;

import it.andreademartini.trading.risk.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit offline smoke test; artifacts never replace earlier research results. */
@EnabledIfSystemProperty(named="weeklytte.historical.local",matches="true")
class HistoricalWeeklyTteLocalTest {
    @TempDir Path temp;
    @Test void localHistoricalModesAndReplay()throws Exception{
        var store=new WeeklyTteStore(temp);var service=new WeeklyTteService(store,new PositionSizingService(new TradePricingService()));
        var sample=(com.fasterxml.jackson.databind.node.ObjectNode)WeeklyTteStore.JSON.valueToTree(WeeklyTteConfiguration.defaults());
        sample.set("symbols",WeeklyTteStore.JSON.valueToTree(List.of("AAPL","MSFT","NVDA")));
        var c=WeeklyTteStore.JSON.convertValue(sample,WeeklyTteConfiguration.class);var baseline=service.run(c);var modes=service.modes(baseline.runId());
        var output=Path.of("outputs","weekly-untouched-v2-validation");Files.createDirectories(output);
        var summary=new ArrayList<Map<String,Object>>();
        for(var r:modes){
            assertFalse(r.analysis().get("historicalLevels").equals(List.of()));
            assertTrue(r.trades().stream().anyMatch(t->t.direction().equals("LONG")));assertTrue(r.trades().stream().anyMatch(t->t.direction().equals("SHORT")));
            Files.write(output.resolve(r.configuration().mode()+".xlsx"),new WeeklyTteExcelExporter().export(r));
            summary.add(Map.of("mode",r.configuration().mode(),"runId",r.runId(),"configurationHash",r.configurationHash(),"datasetId",r.datasetId(),"performance",r.analysis().get("performance"),"directionPerformance",r.analysis().get("directionPerformance"),"levels",r.analysis().get("historicalLevelCounts")));
        }
        var replay=service.replay(baseline.runId());assertEquals(baseline.equity(),replay.equity());assertEquals(baseline.trades().size(),replay.trades().size());
        Files.write(output.resolve("summary.json"),WeeklyTteStore.JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(summary));
        System.out.println("HISTORICAL_WEEKLY_SMOKE "+WeeklyTteStore.JSON.writeValueAsString(summary));
    }
}
