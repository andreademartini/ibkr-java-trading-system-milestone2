package it.andreademartini.trading.backtest.patternstable;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import it.andreademartini.trading.backtest.patternstable.engine.StableSimulation;
import it.andreademartini.trading.backtest.patternv2.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.risk.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;

class PatternStableTest {
    @TempDir Path temp;
    PatternStableService service(){return new PatternStableService(new PatternStableStore(temp),new PatternV2Store(temp.resolve("v2")),configuration(),new TradingSystemProperties());}
    @Test void officialConfigurationIsFrozenIndependentAndSafe(){
        var version=service().version();var c=version.configuration();var g=c.global();
        assertEquals("2.3",version.strategyVersion());assertEquals("PATTERN_V1_STABLE",version.strategyId());
        assertFalse(g.useLeverage());assertEquals(4,g.riskPercent());assertEquals(30,g.maxOrderValuePercent());assertEquals(100,g.maxPositionValuePercent());
        assertFalse(c.patternRangeFilterEnabled());assertFalse(c.closeStrengthFilterEnabled());assertFalse(c.bollingerAtrFilterEnabled());
        assertFalse(version.safety().orderSubmissionEnabled());assertFalse(version.safety().liveTradingEnabled());assertFalse(version.safety().autoExecuteSignals());assertFalse(version.safety().paperTradingEnabled());
        var modified=c.withName("research").withRangeFilter(true,new java.math.BigDecimal("4"));
        assertNotEquals(c,modified);assertEquals(version.configurationHash(),service().version().configurationHash());
        assertEquals(13,g.maxHoldingBars());assertEquals(9,c.symbols().size());assertEquals("IBKR_US_FIXED",g.commission().model().name());
    }
    @Test void archivesCannotBeOverwritten(){var store=new PatternStableStore(temp);store.immutable("versions/x.json",Map.of("x",1));store.immutable("versions/x.json",Map.of("x",1));assertThrows(IllegalStateException.class,()->store.immutable("versions/x.json",Map.of("x",2)));assertThrows(IllegalArgumentException.class,()->store.immutable("../../escape",Map.of()));}
    @Test void timeoutRevisionPreservesOriginalAndExitsOnThirteenthEntryInclusiveBar(){
        var original=service().version("2.0");var revised=service().version("2.1");
        var expected=PatternStableStore.JSON.valueToTree(original.configuration());
        ((com.fasterxml.jackson.databind.node.ObjectNode)expected.path("global")).put("maxHoldingBars",13);
        assertEquals(PatternStableStore.hash(expected),revised.configurationHash());
        assertNotEquals(original.configurationHash(),revised.configurationHash());
        var bars=new ArrayList<>(engulfing("AAPL"));
        // Keep every bar inside stop/target so only the timeout can close this trade.
        for(int i=2;i<23;i++){
            var quiet=bar("AAPL",i,100,101,99,100);
            if(i==2)bars.set(i,quiet);else bars.add(quiet);
        }
        var revisedTrade=new StableSimulation().simulate(revised.configuration(),Map.of("AAPL",bars),UUID.randomUUID().toString()).portfolio().trades().getFirst();
        var originalTrade=new StableSimulation().simulate(original.configuration(),Map.of("AAPL",bars),UUID.randomUUID().toString()).portfolio().trades().getFirst();
        assertEquals("TIMEOUT",revisedTrade.exitReason());assertEquals(13,revisedTrade.holdingBars());
        assertEquals(revisedTrade.entryDate().plusDays(12),revisedTrade.exitDate());
        assertEquals("TIMEOUT",originalTrade.exitReason());assertEquals(20,originalTrade.holdingBars());
        assertEquals(originalTrade.entryDate().plusDays(19),originalTrade.exitDate());
    }
    @Test void missingReferencePreventsPromotion(){assertThrows(NoSuchElementException.class,()->service().promote());assertFalse(Files.exists(temp.resolve("versions/2.3.json")));}
    @Test void financialPathMatchesV2OnSyntheticBarsWithoutUsingItsServiceOrStore(){
        var config=service().version().configuration();var bars=Map.of("AAPL",engulfing("AAPL"));String id=UUID.randomUUID().toString();
        var stable=new StableSimulation().simulate(config,bars,id);
        var research=new PatternV2Service(configuration(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),new PatternV2Store(temp)).simulate(config,bars);
        assertEquals(research.portfolio().finalCapital(),stable.portfolio().finalCapital());assertEquals(research.portfolio().summary(),stable.portfolio().summary());assertEquals(research.costs(),stable.costs());
        assertEquals("PATTERN_V1",stable.portfolio().strategy());assertEquals("PATTERN_V1",stable.portfolio().trades().getFirst().strategy());
        assertFalse(Files.exists(temp.resolve("current.json")));assertEquals(1,stable.portfolio().trades().size());
    }
    @Test void officialApiRejectsOverrides(){var controller=new PatternStableController(service(),null,null,null);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->controller.run(Map.of("initialCapital",20000)));}
}
