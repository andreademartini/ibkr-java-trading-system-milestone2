package it.andreademartini.trading.backtest.patternv2;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import it.andreademartini.trading.research.pattern.*;
import it.andreademartini.trading.strategy.patternv1.PatternV1Strategy.*;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GapExitRulesTest {
    private EntryPlan plan(boolean isLong) {
        var direction=isLong?PatternDirection.LONG:PatternDirection.SHORT;
        var detection=new PatternDetectionResult(PatternType.BULLISH_ENGULFING,direction,START,0,0,bd("100"),Map.of());
        var signal=new Signal("TSLA",detection,bd("3"),bd("98"),bd("102"),bd("100"));
        return new EntryPlan(signal,1,START.plusDays(1),bd("100"),bd(isLong?"98":"102"),bd("2"),bd(isLong?"106":"94"));
    }

    @ParameterizedTest
    @CsvSource({
        "true,95,110,90,STOP,95,true", "true,109,112,90,TARGET,109,true",
        "false,105,110,90,STOP,105,true", "false,91,110,88,TARGET,91,true",
        "true,98,110,90,STOP,98,true", "true,106,110,90,TARGET,106,true",
        "false,102,110,90,STOP,102,true", "false,94,110,90,TARGET,94,true",
        "true,100,107,97,STOP,98,false", "false,100,103,93,STOP,102,false",
        "true,100,101,97,STOP,98,false", "false,100,103,99,STOP,102,false",
        "true,100,107,99,TARGET,106,false", "false,100,101,93,TARGET,94,false",
        "true,100,101,99,TIMEOUT,100,false", "false,100,101,99,TIMEOUT,100,false"
    })
    void bothStableKernelsRespectOpeningChronology(boolean isLong,double open,double high,double low,String reason,String price,boolean atOpen) {
        var p=plan(isLong);var b=bar("TSLA",13,open,high,low,open);
        var actual=PatternV2BarRules.exit(p,b,13,13,SameBarResolution.STOP_FIRST,ExitFillPolicy.OPEN_FIRST);
        var stable=it.andreademartini.trading.backtest.patternstable.engine.PatternV2BarRules.exit(p,b,13,13,SameBarResolution.STOP_FIRST,ExitFillPolicy.OPEN_FIRST);
        var v3=it.andreademartini.trading.backtest.patternv3.PatternV3BarRules.exit(p,b,13,13,SameBarResolution.STOP_FIRST,ExitFillPolicy.OPEN_FIRST);
        assertTrue(actual.completed());assertEquals(reason,actual.reason());assertEquals(0,bd(price).compareTo(actual.price()));assertEquals(atOpen,actual.atOpen());
        assertEquals(actual.reason(),stable.reason());assertEquals(actual.price(),stable.price());assertEquals(actual.atOpen(),stable.atOpen());
        assertEquals(actual.reason(),v3.reason());assertEquals(actual.price(),v3.price());assertEquals(actual.atOpen(),v3.atOpen());
        if(atOpen){
            assertFalse(actual.stop()&&actual.target());
            var excursions=new PatternV2BarRules.Excursions();excursions.measure(p,b,actual);
            var frozen=new it.andreademartini.trading.backtest.patternstable.engine.PatternV2BarRules.Excursions();frozen.measure(p,b,stable);
            assertEquals(excursions.mfe,frozen.mfe);assertEquals(excursions.mae,frozen.mae);
            var favorable=bd(price).subtract(bd("100")).multiply(bd(isLong?"1":"-1"));
            assertEquals(0,favorable.max(bd("0")).compareTo(excursions.mfe));
            assertEquals(0,favorable.negate().max(bd("0")).compareTo(excursions.mae));
            assertTrue(excursions.rawMfe.compareTo(excursions.mfe)>=0);assertTrue(excursions.rawMae.compareTo(excursions.mae)>=0);
        }
    }

    @ParameterizedTest @CsvSource({"true,95,98", "false,105,102"})
    void absentAndExplicitLegacyPolicyKeepLevelFills(boolean isLong,double open,String stop) {
        var p=plan(isLong);var b=bar("TSLA",2,open,110,90,open);
        var legacy=PatternV2BarRules.exit(p,b,2,13,SameBarResolution.STOP_FIRST);
        assertEquals(legacy,PatternV2BarRules.exit(p,b,2,13,SameBarResolution.STOP_FIRST,ExitFillPolicy.LEVEL_ONLY));
        assertEquals(0,bd(stop).compareTo(legacy.price()));assertTrue(legacy.stop()&&legacy.target());assertFalse(legacy.atOpen());
        assertFalse(PatternV2BarRules.exit(p,bar("TSLA",2,100,101,99,100),2,13,SameBarResolution.STOP_FIRST,ExitFillPolicy.OPEN_FIRST).completed());
    }
}
