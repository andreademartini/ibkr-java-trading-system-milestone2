package it.andreademartini.trading.backtest.patternstable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import it.andreademartini.trading.backtest.patternstable.engine.StableSimulation;
import it.andreademartini.trading.backtest.patternv2.*;
import it.andreademartini.trading.backtest.patternv2stable.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.risk.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BacktestRiskParameterTest {
    @TempDir Path temp;
    @Test void acceptsOnlyOneFinitePositivePercentage(){
        assertNull(BacktestRiskParameter.parse(null));assertNull(BacktestRiskParameter.parse(Map.of()));
        for(double risk:new double[]{.1,4,8,100})assertEquals(risk,BacktestRiskParameter.parse(Map.of("riskPercent",risk)));
        for(Object risk:List.of(0,-1,101,Double.NaN,Double.POSITIVE_INFINITY,"8",true))assertThrows(IllegalArgumentException.class,()->BacktestRiskParameter.parse(Map.of("riskPercent",risk)));
        var nullRisk=new HashMap<String,Object>();nullRisk.put("riskPercent",null);assertThrows(IllegalArgumentException.class,()->BacktestRiskParameter.parse(nullRisk));
        assertThrows(IllegalArgumentException.class,()->BacktestRiskParameter.parse(Map.of("riskPercent",8,"initialCapital",20000)));
        assertThrows(IllegalArgumentException.class,()->BacktestRiskParameter.parse(Map.of("slippagePercentPerSide",1)));
    }
    @Test void riskRunsPersistTheirConfigurationWithoutChangingFrozenVersions()throws Exception{
        var history=mock(PatternV2Store.class);var sizing=new PositionSizingService(new TradePricingService());var trading=new TradingSystemProperties();
        var engine=new PatternV2Service(configuration(),trading,null,detectors(),sizing,history);
        var one=new PatternStableService(new PatternStableStore(temp.resolve("v1")),history,configuration(),trading);
        var two=new FrozenPatternV2Service(engine,history,temp.resolve("v2"));
        var bars=Map.of("AAPL",List.of(bar("AAPL",0,100,101,50,51),bar("AAPL",1,50,103,49,101),bar("AAPL",2,100,100.1,99.9,100)));
        var dataset=PatternV2Dataset.capture(bars);when(history.dataset(dataset.id())).thenReturn(dataset);
        var oldOne=new StableSimulation().simulate(one.version("2.0").configuration(),bars,PatternStableService.REFERENCE).withResearch(null,dataset.id(),null);
        var oldTwo=engine.simulate(two.version("1.0").configuration(),bars).withResearch(null,dataset.id(),null);
        when(history.run(PatternStableService.REFERENCE)).thenReturn(oldOne);when(history.run(FrozenPatternV2Service.REFERENCE)).thenReturn(oldTwo);
        var originalOne=one.version();var originalTwo=two.version();
        var a=one.runWithRisk(4);var originalBytes=Files.readAllBytes(temp.resolve("v1/runs/"+a.runId()+".json"));var b=one.runWithRisk(8);
        var c=two.runWithRisk(4);var d=two.runWithRisk(8);
        for(var pair:List.of(List.of(a,b),List.of(c,d))){
            var first=pair.getFirst();var second=pair.getLast();assertNotEquals(first.runId(),second.runId());assertNotEquals(first.configurationHash(),second.configurationHash());
            assertEquals(4,first.simulation().configuration().global().riskPercent());assertEquals(8,second.simulation().configuration().global().riskPercent());
            assertEquals(8,second.simulation().portfolio().settings().riskPercent());assertTrue(second.strategyVersion().endsWith("-risk-test"));
            var expected=BacktestRiskParameter.apply(first.simulation().configuration(),8.0);assertEquals(PatternStableStore.hash(expected),second.configurationHash());
            assertNotNull(second.simulation().executionAudit());
        }
        assertTrue(b.simulation().portfolio().trades().getFirst().quantity()>a.simulation().portfolio().trades().getFirst().quantity());
        assertEquals(originalOne.configurationHash(),one.version().configurationHash());assertEquals(originalTwo.configurationHash(),two.version().configurationHash());
        assertArrayEquals(originalBytes,Files.readAllBytes(temp.resolve("v1/runs/"+a.runId()+".json")));
        assertEquals(8,one.result(b.runId()).simulation().configuration().global().riskPercent());assertEquals(8,two.result(d.runId()).simulation().configuration().global().riskPercent());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->new PatternStableController(one,null,null,null).run(Map.of("initialCapital",10000)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->new FrozenPatternV2Controller(two,null).run(Map.of("riskPercent",0)));
    }
}
