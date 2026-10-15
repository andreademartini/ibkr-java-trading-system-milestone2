package it.andreademartini.trading.backtest.patternv2;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.risk.*;
import java.util.*;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

class MarketRegimeFilterTest {
    @TempDir Path directory;
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    MarketRegimeIndicators.Value value(Double adx,Double plus,Double minus){return new MarketRegimeIndicators.Value(adx,plus,minus,null,null,null,null,100.0);}
    PatternV2Configuration config() throws Exception {var c=PatternV2Configuration.baseline(configuration(),new TradingSystemProperties().getRisk());ObjectNode node=mapper.valueToTree(c);var g=(ObjectNode)node.get("global");g.put("startDate",START.toString());g.put("endDate",START.plusDays(400).toString());return mapper.treeToValue(node,PatternV2Configuration.class);}
    PatternV2Service service(){return new PatternV2Service(configuration(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),new PatternV2Store(directory));}
    @ParameterizedTest @CsvSource({"LONG,30,28,12,false","LONG,30,10,31,true","LONG,20,28,12,true","SHORT,30,11,26,false","SHORT,30,28,12,true","SHORT,20,11,26,true","LONG,24.99,28,12,true","LONG,25,28,12,false","LONG,25.01,28,12,false","SHORT,25,20,20,true"})
    void rule(String direction,double adx,double plus,double minus,boolean accepted){var d=MarketRegimeFilter.decide(true,25,direction,value(adx,plus,minus));assertEquals(accepted,d.accepted());assertEquals(accepted?null:"MARKET_REGIME_WITH_TREND",d.rejectionReason());assertTrue(MarketRegimeFilter.decide(false,25,direction,value(adx,plus,minus)).accepted());}
    @Test void nullAndNonFiniteAreConservativeAndDoNotWaitForEmas(){for(var v:Arrays.asList(null,value(null,20.0,10.0),value(30.0,null,10.0),value(30.0,20.0,null),value(Double.NaN,20.0,10.0),value(30.0,Double.POSITIVE_INFINITY,10.0),value(30.0,20.0,Double.NEGATIVE_INFINITY))){var on=MarketRegimeFilter.decide(true,25,"LONG",v);assertFalse(on.accepted());assertEquals("INSUFFICIENT_REGIME_DATA",on.rejectionReason());assertEquals("INSUFFICIENT_DATA",on.trendAlignment());assertTrue(MarketRegimeFilter.decide(false,25,"LONG",v).accepted());}assertFalse(MarketRegimeFilter.decide(true,25,"LONG",value(30.0,20.0,10.0)).accepted());}
    @Test void configurablePeriodUsesSameWilderStreamAndCausalWarmup() throws Exception {var c=config();var a=new MarketRegimeIndicators(c,2);var b=new MarketRegimeIndicators(c,14);MarketRegimeIndicators.Value atSignal=null;for(int n=0;n<35;n++){var bar=bar("AAPL",n,100+n,101+n,99+n,100+n);var shortValue=a.onBar(bar);var normal=b.onBar(bar);if(n<3)assertNull(shortValue.adx14());if(n==3)assertEquals(100,shortValue.adx14(),1e-10);if(n<27)assertNull(normal.adx14());if(n==27){assertEquals(100,normal.adx14(),1e-10);atSignal=normal;}}b.onBar(bar("AAPL",35,100,1000,1,900));assertEquals(100,atSignal.adx14(),1e-10);}
    Map<String,List<HistoricalBar>> trendBars(){var bars=new ArrayList<HistoricalBar>();for(int n=0;n<40;n++)bars.add(bar("AAPL",n,100+n,101+n,99+n,100+n));bars.add(bar("AAPL",40,140,141,138,139));bars.add(bar("AAPL",41,138.5,144,138,143));bars.add(bar("AAPL",42,143,150,142,149));return Map.of("AAPL",bars);}
    @Test void rejectedBeforeOtherFiltersAndSizingAndAnalysisIndependent() throws Exception {var c=config().withMarketRegimeFilter(true,14,25);var on=service().simulate(c,trendBars(),"same");var analysis=service().simulate(c.withMarketRegimeAnalysis(true),trendBars(),"same");assertEquals(mapper.valueToTree(on.portfolio()),mapper.valueToTree(analysis.portfolio()));assertEquals(on.marketRegimeFilterAnalysis(),analysis.marketRegimeFilterAnalysis());assertTrue(on.portfolio().trades().isEmpty());var a=on.marketRegimeFilterAnalysis();assertEquals(1,a.summary().totalPatternSignals());assertEquals(1,a.summary().rejectedWithTrend());assertEquals(0,a.summary().rejectedInsufficientData());assertEquals(0,a.summary().capitalSkips());assertEquals(0,a.summary().positionOpenSkips());assertTrue(on.rangeSignals().isEmpty());assertTrue(on.capitalSkipAudits().isEmpty());assertEquals(10000,on.portfolio().finalCapital().doubleValue());var off=service().simulate(c.withMarketRegimeFilter(false,14,25),trendBars());assertEquals(1,off.portfolio().trades().size());assertEquals(0,off.marketRegimeFilterAnalysis().summary().signalsEvaluated());assertEquals(0,off.marketRegimeFilterAnalysis().summary().rejectedWithTrend());assertEquals(c.global(),off.configuration().global());assertEquals(c.symbols(),off.configuration().symbols());}
    @Test void insufficiencySeparateAndLegacyPersistenceAndExport() throws Exception {var c=config();var node=(ObjectNode)mapper.valueToTree(c);for(var field:List.of("marketRegimeFilterEnabled","marketRegimeFilterAdxPeriod","marketRegimeFilterAdxThreshold","marketRegimeFilterMode"))node.remove(field);var legacy=mapper.treeToValue(node,PatternV2Configuration.class);assertFalse(legacy.marketRegimeFilterEnabled());assertEquals(14,legacy.marketRegimeFilterAdxPeriod());assertEquals(25,legacy.marketRegimeFilterAdxThreshold());var off=service().simulate(legacy,Map.of("AAPL",engulfing("AAPL")),"same");var explicit=service().simulate(c.withMarketRegimeFilter(false,14,25),Map.of("AAPL",engulfing("AAPL")),"same");assertEquals(mapper.valueToTree(off.portfolio()),mapper.valueToTree(explicit.portfolio()));assertEquals(off.executions(),explicit.executions());var on=service().runOnSnapshot(c.withMarketRegimeFilter(true,14,25),Map.of("AAPL",engulfing("AAPL")));assertEquals(1,on.marketRegimeFilterAnalysis().summary().rejectedInsufficientData());assertEquals(0,on.marketRegimeFilterAnalysis().summary().rejectedWithTrend());var stored=new PatternV2Store(directory).run(on.runId());assertTrue(stored.configuration().marketRegimeFilterEnabled());assertEquals(on.marketRegimeFilterAnalysis(),stored.marketRegimeFilterAnalysis());assertNotNull(on.pairedRunId());try(var book=new XSSFWorkbook(new java.io.ByteArrayInputStream(new PatternV2ExcelExporter().export(on)))){for(var name:List.of("TRADES","CONFIGURATION","MARKET_REGIME_FILTER_SUMMARY","MARKET_REGIME_REJECTIONS","MARKET_REGIME_FILTER_SYMBOLS","MARKET_REGIME_FILTER_PERIODS","MARKET_REGIME_FILTER_COMPARISON"))assertNotNull(book.getSheet(name),name);assertEquals(1,book.getSheet("MARKET_REGIME_REJECTIONS").getLastRowNum());}}
    @Test void validatesAndCopiesSettingsWithoutMutatingV1() throws Exception {var c=config().withMarketRegimeFilter(true,2,0);assertTrue(c.withName("x").withGlobal(c.global()).withSymbols(c.symbols()).withRangeFilter(false,bd("2.5")).withCloseStrengthFilter(false,bd(".8")).withCapitalSkipShadowAnalysis(false).withBollingerAtr(false,20,bd("2"),14,bd(".5")).withMarketRegimeAnalysis(false).withSplitCosts(c.splitCosts()).marketRegimeFilterEnabled());for(double threshold:new double[]{-1,101,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->c.withMarketRegimeFilter(true,14,threshold));assertThrows(IllegalArgumentException.class,()->c.withMarketRegimeFilter(true,1,25));var frozen=configuration();var before=mapper.valueToTree(frozen);var svc=new PatternV2Service(frozen,new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),new PatternV2Store(directory));svc.simulate(c,trendBars());assertEquals(before,mapper.valueToTree(frozen));assertTrue(Arrays.stream(PatternV2Service.class.getDeclaredFields()).noneMatch(f->f.getType().getName().contains("Broker")));}
    @Test void rejectsExplicitNullsStringsAndFractionalPeriodButKeepsFrozenJsonCompatible() throws Exception {
        var c=config();var original=(ObjectNode)mapper.valueToTree(c);
        assertFalse(original.has("marketRegimeFilterEnabled"));
        var frozenTree=original.deepCopy();frozenTree.remove("marketRegimeAnalysisEnabled");
        assertNotNull(mapper.treeToValue(frozenTree,it.andreademartini.trading.backtest.patternstable.engine.PatternV2Configuration.class));
        for(var field:List.of("marketRegimeFilterEnabled","marketRegimeFilterAdxPeriod","marketRegimeFilterAdxThreshold","marketRegimeFilterMode")){
            var node=original.deepCopy();node.putNull(field);assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,()->mapper.treeToValue(node,PatternV2Configuration.class));
        }
        for(var field:List.of("marketRegimeFilterAdxPeriod","marketRegimeFilterAdxThreshold")){var node=original.deepCopy();node.put(field,"14");assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,()->mapper.treeToValue(node,PatternV2Configuration.class));}
        var fractional=original.deepCopy();fractional.put("marketRegimeFilterAdxPeriod",14.5);assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,()->mapper.treeToValue(fractional,PatternV2Configuration.class));
        var saved=new PatternV2Store(directory).saveConfiguration(c.withMarketRegimeFilter(true,7,31),false);assertEquals(7,new PatternV2Store(directory).configuration(saved.id()).configuration().marketRegimeFilterAdxPeriod());
    }
    @Test void releasedCapitalAdmitsLaterCounterTrendAndRecomputesSkipsAndEquity() throws Exception {
        var node=(ObjectNode)mapper.valueToTree(config());var g=(ObjectNode)node.get("global");g.put("riskPercent",100).put("maxOrderValuePercent",100);
        var c=mapper.treeToValue(node,PatternV2Configuration.class);var a=new ArrayList<>(trendBars().get("AAPL"));a.set(42,bar("AAPL",42,143,144,142,143));
        var t=new ArrayList<HistoricalBar>();for(int n=0;n<40;n++)t.add(bar("TSLA",n,200-n,201-n,199-n,200-n));
        t.add(bar("TSLA",40,160,161,158,159));t.add(bar("TSLA",41,158.5,164,158,163));t.add(bar("TSLA",42,163,164,162,163));
        var input=Map.of("AAPL",(List<HistoricalBar>)a,"TSLA",(List<HistoricalBar>)t);
        var off=service().simulate(c,input);var on=service().simulate(c.withMarketRegimeFilter(true,14,25),input);
        assertEquals(1,off.portfolio().summary().skippedCapital());assertEquals(0,on.portfolio().summary().skippedCapital());
        assertEquals("AAPL",off.portfolio().trades().getFirst().symbol());assertEquals("TSLA",on.portfolio().trades().getFirst().symbol());
        assertTrue(on.marketRegimeFilterAnalysis().signals().stream().anyMatch(s->s.symbol().equals("TSLA")&&s.accepted()&&s.trendAlignment().equals("COUNTER_TREND")));
        assertEquals(10000-on.costs().total().doubleValue(),on.portfolio().finalCapital().doubleValue(),1e-8);
        var admitted=on.portfolio().trades().getFirst();assertEquals(163,admitted.entryPrice().doubleValue());assertEquals(158,admitted.stopPrice().doubleValue());assertEquals(c.symbols().stream().filter(s->s.symbol().equals("TSLA")).findFirst().orElseThrow().longConfig().targetR(),admitted.targetR());
    }
}
