package it.andreademartini.trading.backtest.patternv2stable;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import it.andreademartini.trading.backtest.patternv2.*;
import it.andreademartini.trading.backtest.patternv3.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.risk.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class StrategySeparationTest {
    @TempDir Path root;
    @Test void frozenV2ExactlyMatchesRequestedReferenceAndHasNoOverrides(){
        var stable=new FrozenPatternV2Service(null,null,root);var v=stable.version();var c=v.configuration();var g=c.global();
        assertEquals("PATTERN_V2",v.strategyId());assertEquals("1.0",v.strategyVersion());assertEquals(13,g.maxHoldingBars());assertTrue(c.marketRegimeFilterEnabled());assertEquals(14,c.marketRegimeFilterAdxPeriod());assertEquals(25,c.marketRegimeFilterAdxThreshold());assertFalse(g.useLeverage());assertEquals(10000,g.initialCapital());assertEquals(30,g.maxOrderValuePercent());assertEquals(2,g.riskPercent());assertEquals(PatternV2Configuration.OrderValueMode.CURRENT_EQUITY_PERCENT,g.maxOrderValueMode());assertFalse(c.patternRangeFilterEnabled());assertFalse(c.closeStrengthFilterEnabled());assertFalse(c.bollingerAtrFilterEnabled());
        var controller=new FrozenPatternV2Controller(stable,null);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->controller.run(Map.of("riskPercent",3)));
    }
    @Test void researchV3PersistsOnlyItsOwnConfigurationAndRun() throws Exception {
        var frozen=configuration();var before=it.andreademartini.trading.backtest.patternstable.PatternStableStore.hash(frozen);
        var store=new PatternV3Store(root.resolve("v3"));var svc=new PatternV3Service(frozen,new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),store);
        var c=svc.baseline();assertNotNull(c.marketRegimeFilterMode());c=c.withGlobal(c.global().withDates(START,START.plusDays(30))).withName("Independent V3").withMarketRegimeFilter(false,7,30);
        var saved=svc.save(c);assertEquals(c,store.configuration(saved.id()).configuration());var run=svc.runOnSnapshot(c,Map.of("AAPL",engulfing("AAPL")));
        assertEquals("PATTERN_V3",run.strategy());assertTrue(run.portfolio().trades().stream().allMatch(t->t.strategy().equals("PATTERN_V3")));assertEquals(run.runId(),store.run(run.runId()).runId());assertEquals(before,it.andreademartini.trading.backtest.patternstable.PatternStableStore.hash(frozen));assertFalse(Files.exists(root.resolve("v2")));
        var mapper=it.andreademartini.trading.backtest.patternstable.PatternStableStore.JSON;var snapshot=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.valueToTree(run);snapshot.put("strategyVersion","3.1-research");var restored=mapper.treeToValue(snapshot,PatternV3Run.class);assertEquals("3.1-research",restored.withResearch(restored.patternRangeAnalysis(),restored.datasetId(),restored.pairedRunId()).strategyVersion());((com.fasterxml.jackson.databind.node.ObjectNode)snapshot.path("configuration").path("global")).put("riskPercent",99);assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,()->mapper.treeToValue(snapshot,PatternV3Run.class));
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(new PatternV3ExcelExporter().export(run)))){assertNotNull(book.getSheet("TRADES"));assertNotNull(book.getSheet("CONFIGURATION"));assertNotNull(book.getSheet("MARKET_REGIME_FILTER_SUMMARY"));}
    }
}
