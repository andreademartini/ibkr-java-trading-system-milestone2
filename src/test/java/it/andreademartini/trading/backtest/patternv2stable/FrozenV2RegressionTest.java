package it.andreademartini.trading.backtest.patternv2stable;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import it.andreademartini.trading.backtest.patternv2.*;
import it.andreademartini.trading.backtest.patternstable.PatternStableStore;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.risk.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfSystemProperty(named="reorganizationRegression",matches="true")
class FrozenV2RegressionTest {
    @TempDir Path root;
    @Test void exactArchivedPortfolioRegressionWithoutBrokerOrHistoricalWrites() throws Exception {
        var sourcePath=Path.of("data/pattern-v2/runs/"+FrozenPatternV2Service.REFERENCE+".json");var before=Files.readAllBytes(sourcePath);var current=Files.readAllBytes(Path.of("data/pattern-v2/current.json"));var v1=Files.readAllBytes(Path.of("src/main/resources/pattern-stable/2.1.json"));
        var history=new PatternV2Store();var original=history.run(FrozenPatternV2Service.REFERENCE);var svc=new PatternV2Service(configuration(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),history);
        var frozen=new FrozenPatternV2Service(svc,history,root);var run=frozen.run();assertEquals("PATTERN_V2",run.strategyId());assertEquals("1.0",run.strategyVersion());
        assertEquals(original.portfolio().finalCapital(),run.simulation().portfolio().finalCapital());assertEquals(original.portfolio().summary(),run.simulation().portfolio().summary());assertEquals(original.portfolio().equityCurve(),run.simulation().portfolio().equityCurve());assertEquals(original.costs(),run.simulation().costs());
        var expected=PatternStableStore.JSON.writeValueAsString(original.portfolio()).replace(original.runId(),run.runId());assertEquals(PatternStableStore.JSON.readTree(expected),PatternStableStore.JSON.readTree(PatternStableStore.JSON.writeValueAsString(run.simulation().portfolio())));
        assertEquals(run.configurationHash(),frozen.result(run.runId()).configurationHash());assertArrayEquals(before,Files.readAllBytes(sourcePath));assertArrayEquals(current,Files.readAllBytes(Path.of("data/pattern-v2/current.json")));assertArrayEquals(v1,Files.readAllBytes(Path.of("src/main/resources/pattern-stable/2.1.json")));
        var out=Path.of("outputs/strategy-reorganization-evidence");Files.createDirectories(out);var controller=new FrozenPatternV2Controller(frozen,new PatternV2ExcelExporter());Files.write(out.resolve("PATTERN_V2_STABLE_1_0.xlsx"),controller.export(run.runId()).getBody());PatternStableStore.JSON.writerWithDefaultPrettyPrinter().writeValue(out.resolve("frozen-v2-regression.json").toFile(),java.util.Map.of("referenceRunId",original.runId(),"newRunId",run.runId(),"configurationHash",run.configurationHash(),"datasetId",run.datasetId(),"entirePortfolioParityExceptIdentity",true,"sourceAndCurrentAndV1Unchanged",true,"finalEquity",run.simulation().portfolio().finalCapital(),"summary",run.simulation().portfolio().summary()));
    }
}
