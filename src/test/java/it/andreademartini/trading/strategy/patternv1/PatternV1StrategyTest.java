package it.andreademartini.trading.strategy.patternv1;

import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.research.pattern.*;
import it.andreademartini.trading.config.StrategyType;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.strategy.StrategyEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;

class PatternV1StrategyTest {
    private final PatternV1Strategy strategy = new PatternV1Strategy(configuration().getSymbols(), detectors());
    @Test void exactCoreMappingAndDisabledDirections() {
        var config = configuration(); config.validate();
        assertEquals(Set.of("AAPL","GOOGL","NFLX","NVDA","QQQ","SPY","TSLA","XLK","XLY"), config.getSymbols().keySet());
        Map<String, String> mapping = Map.ofEntries(Map.entry("AAPL","BULLISH_ENGULFING:3.0"),Map.entry("GOOGL","BULLISH_HARAMI:1.5"),Map.entry("NFLX","HAMMER:2.0"),
                Map.entry("NVDA","PIERCING_PATTERN:3.0"),Map.entry("QQQ","HAMMER:3.0"),Map.entry("SPY","BULLISH_HARAMI:3.0"),
                Map.entry("TSLA","BULLISH_ENGULFING:1.0"),Map.entry("XLK","HAMMER:4.0"),Map.entry("XLY","BULLISH_ENGULFING:1.5"));
        mapping.forEach((symbol, expected) -> {
            var c = config.getSymbols().get(symbol);
            assertTrue(c.longConfig().enabled());
            assertEquals(expected, c.longConfig().pattern()+":"+c.longConfig().targetR());
            assertEquals(symbol.equals("TSLA"), c.shortConfig().enabled());
        });
        assertEquals(PatternType.SHOOTING_STAR, config.getSymbols().get("TSLA").shortConfig().pattern());
        assertEquals(0, bd("4").compareTo(config.getSymbols().get("TSLA").shortConfig().targetR()));
    }
    @ParameterizedTest @CsvSource({"AAPL,100,98,106","TSLA,250,240,260"})
    void engulfingNextOpenTargets(String symbol, double entry, double stop, double expectedTarget) {
        List<HistoricalBar> bars = symbol.equals("AAPL") ? engulfing(symbol) : List.of(
                bar(symbol,0,249,251,240,245),bar(symbol,1,244,251,241,250),bar(symbol,2,250,255,249,251));
        var signals = strategy.onClosedBar(symbol,bars,1);
        var signal = signals.stream().filter(s->s.detection().direction()==PatternDirection.LONG).findFirst().orElseThrow();
        var plan = strategy.onNextOpen(signal,2,START.plusDays(2),java.math.BigDecimal.valueOf(entry));
        assertPrice(entry,plan.entryPrice());assertPrice(stop,plan.stopPrice());assertPrice(entry-stop,plan.riskPerShare());assertPrice(expectedTarget,plan.takeProfitPrice());
    }
    @Test void googlHarami150148153() {
        var bars=List.of(bar("GOOGL",0,152,153,148,149),bar("GOOGL",1,149.5,152,149,151));
        var signal=strategy.onClosedBar("GOOGL",bars,1).getFirst();
        var plan=strategy.onNextOpen(signal,2,START.plusDays(2),bd("150"));
        assertEquals(PatternType.BULLISH_HARAMI,signal.detection().patternType());
        assertPrice(150,plan.entryPrice());assertPrice(148,plan.stopPrice());assertPrice(2,plan.riskPerShare());assertPrice(153,plan.takeProfitPrice());
    }
    @Test void tslaShort265270245() {
        var signal=strategy.onClosedBar("TSLA",List.of(bar("TSLA",0,266,270,265.8,266.5)),0).getFirst();
        var plan=strategy.onNextOpen(signal,1,START.plusDays(1),bd("265"));
        assertEquals(PatternDirection.SHORT,signal.detection().direction());assertPrice(265,plan.entryPrice());
        assertPrice(270,plan.stopPrice());assertPrice(5,plan.riskPerShare());assertPrice(245,plan.takeProfitPrice());
    }
    @Test void xlkHammer200198208() {
        var signal=strategy.onClosedBar("XLK",List.of(bar("XLK",0,199.5,200.2,198,200)),0).getFirst();
        var plan=strategy.onNextOpen(signal,1,START.plusDays(1),bd("200"));
        assertPrice(200,plan.entryPrice());assertPrice(198,plan.stopPrice());assertPrice(2,plan.riskPerShare());assertPrice(208,plan.takeProfitPrice());
    }
    @ParameterizedTest @CsvSource({"MSFT","BA","BAC","AMD","AMZN","AVGO","CAT","CVX","DIA","DIS","GS","IWM","JPM","KO","META","NKE","SMH","XLE","XLF","XLI","XOM","UNKNOWN"})
    void nonCoreNeverSignals(String symbol) { assertTrue(strategy.onClosedBar(symbol,engulfing(symbol),1).isEmpty()); }
    @Test void noLookAheadAndSignalsSurviveEndOfSeries() {
        var bars=engulfing("AAPL");
        var original=strategy.onClosedBar("AAPL",bars,1);
        var changed=new ArrayList<>(bars);changed.set(2,bar("AAPL",2,100,1000,1,999));
        assertEquals(original,strategy.onClosedBar("AAPL",changed,1));
        assertEquals(original,strategy.onClosedBar("AAPL",bars.subList(0,2),1));
        assertEquals(strategy.onNextOpen(original.getFirst(),2,START.plusDays(2),bd("100")),strategy.onNextOpen(strategy.onClosedBar("AAPL",changed,1).getFirst(),2,START.plusDays(2),bd("100")));
        assertEquals(1,original.getFirst().detection().signalBarIndex());
    }
    @Test void invalidGapAndNonNextBarRejected() {
        var signal=strategy.onClosedBar("AAPL",engulfing("AAPL"),1).getFirst();
        assertThrows(IllegalArgumentException.class,()->strategy.onNextOpen(signal,2,START.plusDays(2),bd("97")));
        assertThrows(IllegalArgumentException.class,()->strategy.onNextOpen(signal,1,START.plusDays(1),bd("100")));
    }
    @Test void dailyOnlyAndMismatchedDirectionRejected() {
        var config=configuration();config.setTimeframe(BarInterval.HOURLY);assertThrows(IllegalArgumentException.class,config::validate);
        var wrong=new SymbolPatternConfiguration(new DirectionPatternConfiguration(true,PatternType.SHOOTING_STAR,bd("4")),null);
        assertThrows(IllegalArgumentException.class,()->new PatternV1Strategy(Map.of("AAPL",wrong),detectors()));
    }
    @Test void operationalFactoryRejectsPatternV1() {
        var config=new TradingSystemProperties();config.getStrategy().setType(StrategyType.PATTERN_V1);
        assertThrows(IllegalArgumentException.class,()->new StrategyEngine(config,null));
    }
    private static void assertPrice(double expected,java.math.BigDecimal actual){assertEquals(0,java.math.BigDecimal.valueOf(expected).compareTo(actual));}
}
