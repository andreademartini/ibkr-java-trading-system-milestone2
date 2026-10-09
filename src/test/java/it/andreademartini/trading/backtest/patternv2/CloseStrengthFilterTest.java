package it.andreademartini.trading.backtest.patternv2;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.andreademartini.trading.research.pattern.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class CloseStrengthFilterTest {
    @TempDir Path directory;
    PatternRangeFilterTest support;
    @BeforeEach void setup(){support=new PatternRangeFilterTest();support.directory=directory;}
    @ParameterizedTest @CsvSource({"107,LONG,0.70","102,SHORT,0.80","110,LONG,1","100,LONG,0","110,SHORT,0","100,SHORT,1"})
    void formulas(String close,PatternDirection direction,String expected){
        assertEquals(0,bd(expected).compareTo(CloseStrength.value(new PatternRange(bd("110"),bd("100"),bd(close)),direction)));
    }
    @ParameterizedTest @CsvSource({"0.79,true,LONG","0.80,true,LONG","0.81,false,LONG","0.79,true,SHORT","0.80,true,SHORT","0.81,false,SHORT"})
    void inclusiveThresholdBeforeOrder(String strength,boolean accepted,PatternDirection direction) throws Exception {
        var detector=support.signalDetector(direction);var d=new PatternV2Configuration.Direction(true,detector.patternType(),bd("3"));
        var c=support.config().withSymbols(List.of(new PatternV2Configuration.Symbol(true,"AAPL",direction==PatternDirection.LONG?d:null,direction==PatternDirection.SHORT?d:null,null,null,null,null))).withCloseStrengthFilter(true,bd(".80"));
        double close=direction==PatternDirection.LONG?100+10*Double.parseDouble(strength):110-10*Double.parseDouble(strength);
        var bars=Map.of("AAPL",List.of(bar("AAPL",0,105,110,100,close),bar("AAPL",1,105,106,104,105)));
        var r=support.service(List.of(detector)).simulate(c,bars);
        assertEquals(accepted,r.closeStrengthSignals().getFirst().accepted());assertEquals(accepted?1:0,r.portfolio().trades().size());
        if(!accepted){assertEquals("CLOSE_STRENGTH_FILTER",r.closeStrengthSignals().getFirst().rejectReason());assertTrue(r.portfolio().events().stream().noneMatch(e->e.eventType().equals("SIGNAL")||e.eventType().equals("ENTRY")));assertEquals(0,bd("10000").compareTo(r.portfolio().finalCapital()));assertEquals(0,r.costs().total().signum());}
    }
    @ParameterizedTest @MethodSource("it.andreademartini.trading.backtest.patternv2.PatternRangeFilterTest#patterns")
    void singleAndMultiBarAllPatternsNoFutureData(PatternDetector detector,List<it.andreademartini.trading.marketdata.HistoricalBar> pattern,double high,double low,double close) throws Exception {
        var d=new PatternV2Configuration.Direction(true,detector.patternType(),bd("3"));
        var c=support.config().withSymbols(List.of(new PatternV2Configuration.Symbol(true,"AAPL",detector.direction()==PatternDirection.LONG?d:null,detector.direction()==PatternDirection.SHORT?d:null,null,null,null,null)));
        var strategy=new ConfiguredPatternStrategy(c,List.of(detector));var future=new ArrayList<>(pattern);future.add(bar("AAPL",pattern.size(),500,1000,1,999));
        var s=strategy.onClosedBar("AAPL",future,pattern.size()-1).getFirst();var range=new PatternRange(s.patternHigh(),s.patternLow(),s.patternClose());
        assertEquals(detector.direction()==PatternDirection.LONG?(close-low)/(high-low):(high-close)/(high-low),CloseStrength.value(range,detector.direction()).doubleValue(),1e-12);
        assertEquals(strategy.onClosedBar("AAPL",pattern,pattern.size()-1),strategy.onClosedBar("AAPL",future,pattern.size()-1));
    }
    @Test void invalidRangeExplicitAndOffDoesNotReject() throws Exception {
        for(var r:List.of(new PatternRange(bd("100"),bd("100"),bd("100")),new PatternRange(bd("99"),bd("100"),bd("100")),new PatternRange(bd("110"),bd("100"),bd("111")))){
            assertNull(CloseStrength.value(r,PatternDirection.LONG));assertFalse(CloseStrength.accepts(r,PatternDirection.SHORT,bd(".8")));
            var signal=new PatternRangeAnalysis.SignalAudit("AAPL","LONG","HAMMER",START,"timestamp",0,r,false,bd("2.5"),true,null);
            assertEquals(CloseStrengthAnalysis.Status.DISABLED,CloseStrengthAnalysis.audit(signal,bd("3"),support.config()).status());
            assertEquals("INVALID_CLOSE_STRENGTH_DATA",CloseStrengthAnalysis.audit(signal,bd("3"),support.config().withCloseStrengthFilter(true,bd(".8"))).rejectReason());
        }
    }
    @Test void fourIndependentModesAndRangePrimaryReason() throws Exception {
        var c=support.config();var high=new PatternRange(bd("110"),bd("100"),bd("109"));var low=new PatternRange(bd("101"),bd("100"),bd("100.5"));
        for(boolean rangeOn:List.of(false,true))for(boolean closeOn:List.of(false,true))for(var r:List.of(high,low)){
            var cfg=c.withRangeFilter(rangeOn,bd("2.5")).withCloseStrengthFilter(closeOn,bd(".8"));boolean rangeAccepted=!rangeOn||r.reaches(bd("2.5"));
            var signal=new PatternRangeAnalysis.SignalAudit("AAPL","LONG","HAMMER",START,"timestamp",0,r,rangeOn,bd("2.5"),rangeAccepted,rangeAccepted?null:"PATTERN_RANGE_FILTER");
            var audit=CloseStrengthAnalysis.audit(signal,bd("3"),cfg);
            assertEquals(rangeAccepted&&(!closeOn||CloseStrength.accepts(r,PatternDirection.LONG,bd(".8"))),rangeAccepted&&Boolean.TRUE.equals(audit.accepted()));
            if(!rangeAccepted){assertEquals(CloseStrengthAnalysis.Status.SKIPPED_PATTERN_RANGE,audit.status());assertFalse(audit.evaluated());assertEquals("PATTERN_RANGE_FILTER",audit.rejectReason());}
        }
    }
    @Test void offPreservesTradesEquityCostsWithRangeOnAndOffAndFrozenV1() throws Exception {
        support.offPreservesV1ParityAndIgnoresThreshold();var service=support.service(detectors());
        for(boolean range:List.of(false,true)){var c=support.config().withRangeFilter(range,bd("2.5"));var bars=Map.of("AAPL",engulfing("AAPL"));var old=service.simulate(c,bars);var off=service.simulate(c.withCloseStrengthFilter(false,bd("0")),bars);
            assertEquals(old.portfolio().summary(),off.portfolio().summary());assertEquals(old.portfolio().equityCurve(),off.portfolio().equityCurve());assertEquals(old.costs(),off.costs());assertTrue(off.closeStrengthSignals().stream().noneMatch(s->s.rejectReason()!=null&&!s.rejectReason().equals("PATTERN_RANGE_FILTER")));
        }
    }
    @Test void persistencePresetReplayComparisonAndExcel() throws Exception {
        var service=support.service(detectors());var c=support.config();var base=service.runOnSnapshot(c,Map.of("AAPL",engulfing("AAPL")));var store=new PatternV2Store(directory);store.current(c);
        var filtered=c.withName("V2_CLOSE_STRENGTH_080").withCloseStrengthFilter(true,bd(".800"));var preset=service.savePreset(filtered);assertEquals(c,store.current());assertEquals(filtered,store.configuration(preset.id()).configuration());
        var result=service.replay(base.runId(),filtered);assertEquals(filtered,store.run(result.runId()).configuration());assertEquals(base.runId(),result.pairedRunId());assertEquals(17,service.compare(List.of(base.runId(),result.runId())).metrics().size());
        assertThrows(IllegalArgumentException.class,()->service.replay(base.runId(),filtered.withSymbols(List.of())));
        assertThrows(IllegalArgumentException.class,()->PatternV2RunComparison.compare(List.of(base,result.withResearch(result.patternRangeAnalysis(),"different",base.runId()))));
        assertEquals(1,result.closeStrengthAnalysis().statistics().signalsRejected());assertNotNull(result.closeStrengthAnalysis().outcomes().getFirst().baselineTradeId());assertNull(result.closeStrengthAnalysis().outcomes().getFirst().netPnl()); // baseline trade is OPEN
        try(var book=new XSSFWorkbook(new java.io.ByteArrayInputStream(new PatternV2ExcelExporter().export(result)))){
            Set<String> headers=new HashSet<>();book.getSheet("TRADES").getRow(0).forEach(cell->headers.add(cell.toString()));assertTrue(headers.containsAll(List.of("PatternHigh","PatternLow","PatternClose","PatternRange","CloseStrength","CloseStrengthFilterEnabled","MaxCloseStrength","CloseStrengthAccepted")));
            assertNotNull(book.getSheet("CLOSE_STRENGTH_ANALYSIS"));assertEquals(1,book.getSheet("FILTERED_SIGNALS").getLastRowNum());
            Map<String,String> rows=new HashMap<>();for(var row:book.getSheet("CONFIGURATION"))rows.put(row.getCell(0).toString(),row.getCell(1)==null?null:row.getCell(1).toString());assertEquals("TRUE",rows.get("CloseStrengthFilterEnabled"));assertEquals("0.8",rows.get("MaxCloseStrength"));
        }
    }
    @Test void legacyDefaultsAndThresholdValidation() throws Exception {
        ObjectNode json=support.mapper.valueToTree(support.config());json.remove(List.of("closeStrengthFilterEnabled","maxCloseStrength"));var c=support.mapper.treeToValue(json,PatternV2Configuration.class);assertFalse(c.closeStrengthFilterEnabled());assertEquals(bd(".80"),c.maxCloseStrength());
        for(String threshold:List.of("-0.1","1.1"))assertThrows(IllegalArgumentException.class,()->c.withCloseStrengthFilter(false,bd(threshold)).validate(detectors()));
    }
    @ParameterizedTest @CsvSource({"0,0",".2,1",".4,2",".6,3",".7,4",".8,5",".9,6","1,6"})
    void bucketEdges(String value,int index){assertEquals(index,CloseStrengthAnalysis.bucket(bd(value)));}

    @Test void bothEngineFiltersMustPassAndRejectedSignalLeavesExistingPositionUntouched() throws Exception {
        var detector=new PatternDetector(){public PatternType patternType(){return PatternType.HAMMER;}public PatternDirection direction(){return PatternDirection.LONG;}public Optional<PatternDetectionResult> detect(List<it.andreademartini.trading.marketdata.HistoricalBar> bars,int i,PatternDetectorParameters p){return i<2?Optional.of(new PatternDetectionResult(patternType(),direction(),bars.get(i).dateTime().toLocalDate(),i,i,bars.get(i).getClose(),null)):Optional.empty();}};
        var c=support.config().withSymbols(List.of(new PatternV2Configuration.Symbol(true,"AAPL",new PatternV2Configuration.Direction(true,PatternType.HAMMER,bd("3")),null,null,null,null,null)));
        var service=support.service(List.of(detector));var bars=Map.of("AAPL",List.of(bar("AAPL",0,105,110,100,107),bar("AAPL",1,105,110,101,109),bar("AAPL",2,105,110,101,105)));
        var off=service.simulate(c,bars);var on=service.simulate(c.withCloseStrengthFilter(true,bd(".8")),bars);assertEquals(1,on.portfolio().trades().size());assertEquals(off.portfolio().equityCurve(),on.portfolio().equityCurve());assertEquals(off.costs(),on.costs());
        var first=off.executions().getFirst();var filtered=on.executions().getFirst();assertEquals(first.entryPriceAfterSlippage(),filtered.entryPriceAfterSlippage());assertEquals(first.commission(),filtered.commission());assertEquals(first.slippageCost(),filtered.slippageCost());assertEquals(first.trade().quantity(),filtered.trade().quantity());assertEquals(first.trade().stopPrice(),filtered.trade().stopPrice());assertEquals(first.trade().takeProfitPrice(),filtered.trade().takeProfitPrice());assertEquals(CloseStrengthAnalysis.Status.REJECTED,on.closeStrengthSignals().get(1).status());
        var both=service.simulate(c.withRangeFilter(true,bd("20")).withCloseStrengthFilter(true,bd(".8")),bars);assertTrue(both.portfolio().trades().isEmpty());assertEquals(2,both.closeStrengthAnalysis().statistics().signalsRejectedByPatternRange());assertEquals(0,both.closeStrengthAnalysis().statistics().signalsEvaluated());assertEquals(0,both.closeStrengthAnalysis().statistics().signalsRejected());
    }
}
