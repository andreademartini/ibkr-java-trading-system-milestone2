package it.andreademartini.trading.backtest.patternv2;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.risk.*;
import it.andreademartini.trading.research.pattern.*;
import it.andreademartini.trading.marketdata.*;
import java.math.*;
import java.util.*;
import java.nio.file.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

class BollingerAtrTest {
    @TempDir Path directory;
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    PatternV2Service service(){return new PatternV2Service(configuration(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),new PatternV2Store(directory));}
    PatternV2Configuration config(boolean enabled,int bb,int atr,BigDecimal distance) throws Exception {
        ObjectNode json=mapper.valueToTree(service().baseline());var g=(ObjectNode)json.get("global");g.put("startDate",START.toString());g.put("endDate",START.plusDays(40).toString());
        return mapper.treeToValue(json,PatternV2Configuration.class).withBollingerAtr(enabled,bb,bd("2"),atr,distance);
    }
    @Test void populationBollingerAndWilderSeedAndRecurrence() throws Exception {
        var indicator=new BollingerAtr(config(false,3,2,bd("0.5")));
        assertNull(indicator.onBar(bar("AAPL",0,1,1.5,.5,1)).atr());assertNull(indicator.onBar(bar("AAPL",1,2,2.5,1.5,2)).middle());
        var value=indicator.onBar(bar("AAPL",2,3,3.5,2.5,3));assertEquals(2,value.middle().doubleValue(),1e-12);assertEquals(2+2*Math.sqrt(2.0/3),value.upper().doubleValue(),1e-12);assertEquals(2-2*Math.sqrt(2.0/3),value.lower().doubleValue(),1e-12);assertEquals(1.5,value.atr().doubleValue(),1e-12);
        assertEquals(2.75,indicator.onBar(bar("AAPL",3,4,7,3,6)).atr().doubleValue(),1e-12);
    }
    @Test void atr14UsesFourteenCompleteTrueRangesAndGaps() throws Exception {
        var indicator=new BollingerAtr(config(false,2,14,bd(".5")));assertNull(indicator.onBar(bar("AAPL",0,100,101,99,100)).atr());
        for(int i=1;i<14;i++)assertNull(indicator.onBar(bar("AAPL",i,101,102,100,101)).atr());
        assertEquals(2,indicator.onBar(bar("AAPL",14,101,102,100,101)).atr().doubleValue(),1e-12);
        assertEquals(32.0/14,indicator.onBar(bar("AAPL",15,104,107,103,105)).atr().doubleValue(),1e-12);
        assertEquals(4,BollingerAtr.trueRange(bar("AAPL",0,13,14,12,13),bd("10")).intValue());
    }
    @ParameterizedTest @CsvSource({"LONG,.49,true","LONG,.50,true","LONG,.51,false","LONG,-.25,true","SHORT,.49,true","SHORT,.50,true","SHORT,.51,false","SHORT,-.25,true"})
    void signedDistanceAndInclusiveThreshold(String direction,String distance,boolean accepted) throws Exception {
        var value=new BollingerAtr.Value(bd("125"),bd("150"),bd("100"),bd("4"));var d=bd(distance);var dir=PatternDirection.valueOf(direction);
        var actual=BollingerAtr.distance(dir,bd("150").subtract(d.multiply(bd("4"))),bd("100").add(d.multiply(bd("4"))),value);
        assertEquals(0,d.compareTo(actual));assertEquals(accepted,actual.compareTo(bd(".50"))<=0);
    }
    @Test void exactBucketsAreExclusiveAtEveryBoundary(){String[] values={"-1.0001","-1","-.5","0",".25",".5",".50000000000000000001","1","1.0001"};int[] expected={0,1,2,3,4,4,5,5,6};for(int i=0;i<values.length;i++)assertEquals(expected[i],BollingerAtrAnalysis.bucket(bd(values[i])));}
    @Test void oneAndTwoBarExtremesAndCausalHistory() throws Exception {
        var c=config(true,2,1,bd(".50"));var strategy=new ConfiguredPatternStrategy(c,detectors());var bars=engulfing("AAPL");var signal=strategy.onClosedBar("AAPL",bars,1).getFirst();
        var values=new BollingerAtr.Value(bd("100"),bd("102"),bd("98"),bd("4"));var audit=BollingerAtrAnalysis.audit(signal,bars,values,c,true);
        assertEquals(98,audit.patternLow().doubleValue());assertEquals(bars.getFirst().timestamp().toString(),audit.extremeTimestamp());assertEquals(BollingerAtrAnalysis.Status.ACCEPTED,audit.status());
        var shortBars=List.of(bar("TSLA",0,266,270,265.8,266.5));var single=strategy.onClosedBar("TSLA",shortBars,0).getFirst();assertEquals(270,BollingerAtrAnalysis.audit(single,shortBars,values,c,true).extremePrice().doubleValue());
        var prefix=new BollingerAtr(c);var full=new BollingerAtr(c);for(int i=0;i<2;i++)assertEquals(prefix.onBar(bars.get(i)),full.onBar(bars.get(i)));full.onBar(bar("AAPL",2,999,1000,900,950));assertEquals(audit,BollingerAtrAnalysis.audit(signal,bars,values,c,true));
    }
    @Test void enabledWarmupRejectsButOffNeverChangesPortfolio() throws Exception {
        var legacy=service().baseline();var c=config(false,20,14,bd(".5"));var off=service().simulate(c,Map.of("AAPL",engulfing("AAPL")),"same");var on=service().simulate(c.withBollingerAtr(true,20,bd("2"),14,bd(".5")),Map.of("AAPL",engulfing("AAPL")),"same");
        assertEquals(1,off.portfolio().trades().size());assertEquals(0,on.portfolio().trades().size());assertEquals("BOLLINGER_ATR_INSUFFICIENT_DATA",on.bollingerAtrSignals().getFirst().rejectReason());assertTrue(on.portfolio().events().stream().anyMatch(e->e.eventType().equals("BOLLINGER_ATR_INSUFFICIENT_DATA")));
        assertNull(BollingerAtr.distance(PatternDirection.LONG,bd("10"),bd("5"),new BollingerAtr.Value(bd("7"),bd("8"),bd("6"),BigDecimal.ZERO)));
        var wide=service().simulate(c.withBollingerAtr(true,2,bd("2"),1,bd("100")),Map.of("AAPL",engulfing("AAPL")),"same");assertEquals(off.portfolio(),wide.portfolio());assertEquals(off.costs(),wide.costs());
    }
    @Test void filtersIndependentLegacyPersistenceAndExcel() throws Exception {
        var c=config(true,2,1,bd(".5"));var input=Map.of("AAPL",engulfing("AAPL"));var on=service().runOnSnapshot(c,input);
        var blocked=service().simulate(c.withRangeFilter(true,bd("100")),input);assertEquals(BollingerAtrAnalysis.Status.BLOCKED_BY_OTHER_FILTER,blocked.bollingerAtrSignals().getFirst().status());assertEquals(0,blocked.bollingerAtrAnalysis().statistics().signalsRejected());
        var closeBlocked=service().simulate(c.withCloseStrengthFilter(true,BigDecimal.ZERO),input);assertEquals(BollingerAtrAnalysis.Status.BLOCKED_BY_OTHER_FILTER,closeBlocked.bollingerAtrSignals().getFirst().status());
        var store=new PatternV2Store(directory);assertEquals(c,store.run(on.runId()).configuration());var saved=service().savePreset(c);assertEquals(c,store.configuration(saved.id()).configuration());
        var json=(ObjectNode)mapper.valueToTree(c);json.remove(List.of("bollingerAtrFilterEnabled","bollingerPeriod","bollingerStdDev","atrPeriod","maxBollingerDistanceAtr"));var legacy=mapper.treeToValue(json,PatternV2Configuration.class);assertFalse(legacy.bollingerAtrFilterEnabled());assertEquals(20,legacy.bollingerPeriod());
        try(var book=new XSSFWorkbook(new java.io.ByteArrayInputStream(new PatternV2ExcelExporter().export(on)))){assertNotNull(book.getSheet("BOLLINGER_ATR_ANALYSIS"));assertNotNull(book.getSheet("BOLLINGER_ATR_SIGNALS"));boolean column=false;for(var cell:book.getSheet("TRADES").getRow(0))column|=cell.toString().equals("BollingerDistanceATR");assertTrue(column);}
        assertThrows(IllegalArgumentException.class,()->config(true,1,14,bd(".5")).validate(detectors()));
    }
}
