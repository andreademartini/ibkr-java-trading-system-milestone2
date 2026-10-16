package it.andreademartini.trading.backtest.patternv2stable;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import it.andreademartini.trading.backtest.patternstable.*;
import it.andreademartini.trading.backtest.patternv1.PatternV1Result.Trade;
import it.andreademartini.trading.backtest.patternv2.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.risk.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/** Read original archives, write simulations only into a temporary store and a separate evidence report. */
@EnabledIfSystemProperty(named="gapDatasetComparison",matches="true")
class GapStableDatasetComparisonTest {
    @TempDir Path temp;
    private String key(Trade t){return t.symbol()+"|"+t.direction()+"|"+t.signalDate();}
    private Object comparison(PatternStableService.Run old,PatternStableService.Run corrected){
        var before=old.simulation().portfolio();var after=corrected.simulation().portfolio();
        Map<String,Trade> previous=new HashMap<>();before.trades().forEach(t->previous.put(key(t),t));
        var changes=new ArrayList<Map<String,Object>>();
        for(var t:after.trades()){
            var prior=previous.get(key(t));
            if(prior!=null&&t.closed()&&prior.closed()&&(t.exitPrice().compareTo(prior.exitPrice())!=0||!t.exitReason().equals(prior.exitReason())))
                changes.add(Map.of("signal",key(t),"oldExit",prior.exitPrice(),"newExit",t.exitPrice(),"oldReason",prior.exitReason(),"newReason",t.exitReason()));
        }
        assertFalse(changes.isEmpty());
        for(var event:after.events())if(event.eventType().equals("EXIT_EXECUTION")&&Boolean.TRUE.equals(event.details().get("atOpen"))){
            var t=after.trades().stream().filter(v->v.tradeId().equals(event.tradeId())).findFirst().orElseThrow();
            assertEquals(0,t.exitPrice().compareTo(java.math.BigDecimal.valueOf(after.priceSeries().get(t.symbol()).get(t.exitIndex()).open())));assertFalse(t.sameBarAmbiguous());
        }
        return Map.of("oldVersion",old.strategyVersion(),"newVersion",corrected.strategyVersion(),"datasetId",old.datasetId(),
            "oldFinalEquity",before.finalCapital(),"newFinalEquity",after.finalCapital(),"oldSummary",before.summary(),"newSummary",after.summary(),
            "oldCosts",old.simulation().costs(),"newCosts",corrected.simulation().costs(),"changedCommonExits",changes);
    }
    @Test void compareBothStableVersionsOnTheirExactArchivedDatasets() throws Exception {
        var references=new PatternV2Store();
        var v1=new PatternStableService(new PatternStableStore(temp.resolve("v1")),references,configuration(),new TradingSystemProperties());
        var engine=new PatternV2Service(configuration(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),references);
        var v2=new FrozenPatternV2Service(engine,references,temp.resolve("v2"));
        var protectedPaths=List.of(Path.of("data/pattern-v2/current.json"),Path.of("data/pattern-v2/runs/"+PatternStableService.REFERENCE+".json"),Path.of("data/pattern-v2/runs/"+FrozenPatternV2Service.REFERENCE+".json"));
        Map<Path,byte[]> originals=new HashMap<>();for(var path:protectedPaths)originals.put(path,Files.readAllBytes(path));
        var a=v1.run("2.1");var b=v1.run("2.2");var c=v2.run("1.0");var d=v2.run("1.1");
        var source=references.run(FrozenPatternV2Service.REFERENCE);
        assertEquals(source.portfolio().finalCapital(),c.simulation().portfolio().finalCapital());assertEquals(source.portfolio().summary(),c.simulation().portfolio().summary());assertEquals(source.costs(),c.simulation().costs());
        var normalized=PatternStableStore.JSON.writeValueAsString(source.portfolio()).replace(source.runId(),c.runId());
        var expected=PatternStableStore.JSON.readTree(normalized);
        var actual=PatternStableStore.JSON.readTree(PatternStableStore.JSON.writeValueAsString(c.simulation().portfolio()));
        expected.fieldNames().forEachRemaining(field->assertTrue(expected.path(field).equals(actual.path(field)),"Legacy V2 parity: "+field));
        var report=Map.of("V1",comparison(a,b),"V2",comparison(c,d),"historicalArchivesUnchanged",true);
        for(var path:protectedPaths)assertArrayEquals(originals.get(path),Files.readAllBytes(path));
        var out=Path.of("outputs/gap-execution-evidence");Files.createDirectories(out);
        PatternStableStore.JSON.writerWithDefaultPrettyPrinter().writeValue(out.resolve("comparison.json").toFile(),report);
        Files.write(out.resolve("PATTERN_V1_STABLE_2_2.xlsx"),new PatternStableController(v1,null,null,new PatternV2ExcelExporter()).export(b));
        Files.write(out.resolve("PATTERN_V2_STABLE_1_1.xlsx"),new FrozenPatternV2Controller(v2,new PatternV2ExcelExporter()).export(d.runId()).getBody());
    }
}
