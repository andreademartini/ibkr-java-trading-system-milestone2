package it.andreademartini.trading.backtest.patternv2;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.andreademartini.trading.backtest.patternv1.PatternV1PortfolioEngine;
import it.andreademartini.trading.backtest.patternv1.PatternV1Request;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.research.pattern.*;
import it.andreademartini.trading.risk.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class PatternRangeFilterTest {
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final PositionSizingService sizing=new PositionSizingService(new TradePricingService());
    final TradingSystemProperties trading=new TradingSystemProperties();
    @TempDir Path directory;
    PatternV2Configuration config() throws Exception {
        var json=(ObjectNode)mapper.valueToTree(PatternV2Configuration.baseline(configuration(),trading.getRisk()));
        ((ObjectNode)json.get("global")).put("startDate",START.toString()).put("endDate",START.plusDays(30).toString());
        return mapper.treeToValue(json,PatternV2Configuration.class);
    }
    PatternV2Service service(List<PatternDetector> detectors){return new PatternV2Service(configuration(),trading,null,detectors,sizing,new PatternV2Store(directory));}
    @Test void formulaExampleIsPositiveAndDirectionIndependent() {
        var r=new PatternRange(bd("105"),bd("101"),bd("104"));
        assertEquals(3.846153846153846,r.percent().doubleValue(),1e-12);assertEquals(bd("4"),r.range());
        assertTrue(r.percent().signum()>0);
    }
    static List<Arguments> patterns() {
        return List.of(
            Arguments.of(new HammerDetector(),List.of(bar("AAPL",0,10,10.8,8.5,10.5)),10.8,8.5,10.5),
            Arguments.of(new ShootingStarDetector(),List.of(bar("AAPL",0,10.5,12,9.8,10)),12,9.8,10),
            Arguments.of(new BullishEngulfingDetector(),List.of(bar("AAPL",0,10,10.2,7.8,8),bar("AAPL",1,7.5,10.8,7.2,10.5)),10.8,7.2,10.5),
            Arguments.of(new BearishEngulfingDetector(),List.of(bar("AAPL",0,8,10.2,7.8,10),bar("AAPL",1,10.5,10.8,7.2,7.5)),10.8,7.2,7.5),
            Arguments.of(new BullishHaramiDetector(),List.of(bar("AAPL",0,10,10.2,7.8,8),bar("AAPL",1,8.5,9.7,8.3,9.5)),10.2,7.8,9.5),
            Arguments.of(new BearishHaramiDetector(),List.of(bar("AAPL",0,8,10.2,7.8,10),bar("AAPL",1,9.5,9.7,8.3,8.5)),10.2,7.8,8.5),
            Arguments.of(new PiercingPatternDetector(),List.of(bar("AAPL",0,10,10.2,7.8,8),bar("AAPL",1,7.5,9.7,7.2,9.5)),10.2,7.2,9.5),
            Arguments.of(new DarkCloudCoverDetector(),List.of(bar("AAPL",0,8,10.2,7.8,10),bar("AAPL",1,10.5,10.8,8.3,8.5)),10.8,7.8,8.5));
    }
    @ParameterizedTest @MethodSource("patterns")
    void allDetectorsUseTheirOwnPatternWindowWithoutLookAhead(PatternDetector detector,List<HistoricalBar> pattern,double high,double low,double close) throws Exception {
        var direction=new PatternV2Configuration.Direction(true,detector.patternType(),bd("3"));
        var disabled=new PatternV2Configuration.Direction(false,null,null);
        var symbol=new PatternV2Configuration.Symbol(true,"AAPL",detector.direction()==PatternDirection.LONG?direction:disabled,detector.direction()==PatternDirection.SHORT?direction:disabled,null,null,null,null);
        var c=config().withSymbols(List.of(symbol));var strategy=new ConfiguredPatternStrategy(c,List.of(detector));
        var future=new ArrayList<>(pattern);future.add(bar("AAPL",pattern.size(),100,1000,1,500));
        var s=strategy.onClosedBar("AAPL",future,pattern.size()-1).getFirst();
        assertEquals(0,BigDecimal.valueOf(high).compareTo(s.patternHigh()));assertEquals(0,BigDecimal.valueOf(low).compareTo(s.patternLow()));assertEquals(0,BigDecimal.valueOf(close).compareTo(s.patternClose()));
        assertEquals(strategy.onClosedBar("AAPL",pattern,pattern.size()-1),strategy.onClosedBar("AAPL",future,pattern.size()-1));
        var expected=(high-low)/close*100;
        assertEquals(expected,new PatternRange(s.patternHigh(),s.patternLow(),s.patternClose()).percent().doubleValue(),1e-12);
    }
    PatternDetector signalDetector(PatternDirection direction) {
        return new PatternDetector() {
            public PatternType patternType(){return direction==PatternDirection.LONG?PatternType.HAMMER:PatternType.SHOOTING_STAR;}
            public PatternDirection direction(){return direction;}
            public Optional<PatternDetectionResult> detect(List<HistoricalBar> bars,int index,PatternDetectorParameters params){return index==0?Optional.of(new PatternDetectionResult(patternType(),direction,bars.get(0).dateTime().toLocalDate(),0,0,bars.get(0).getClose(),null)):Optional.empty();}
        };
    }
    @ParameterizedTest @CsvSource({"2.49,false,LONG","2.50,true,LONG","3.00,true,LONG","2.49,false,SHORT","2.50,true,SHORT","3.00,true,SHORT"})
    void cutoffBeforeEntryAndIdenticalFormulaForLongShort(String pct,boolean accepted,PatternDirection direction) throws Exception {
        var detector=signalDetector(direction);var enabled=new PatternV2Configuration.Direction(true,detector.patternType(),bd("3"));var disabled=new PatternV2Configuration.Direction(false,null,null);
        var c=config().withSymbols(List.of(new PatternV2Configuration.Symbol(true,"AAPL",direction==PatternDirection.LONG?enabled:disabled,direction==PatternDirection.SHORT?enabled:disabled,null,null,null,null))).withRangeFilter(true,bd("2.50"));
        var first=new HistoricalBar("AAPL",START.atStartOfDay(),bd("100"),bd("100").add(new BigDecimal(pct)),bd("100"),bd("100"),1000,BarInterval.DAILY,"TEST");
        var next=direction==PatternDirection.LONG?bar("AAPL",1,101,102,101,101.5):bar("AAPL",1,101,102,100,100.5);
        var r=service(List.of(detector)).simulate(c,Map.of("AAPL",List.of(first,next)));
        assertEquals(accepted,r.rangeSignals().getFirst().accepted());assertEquals(0,bd(pct).compareTo(r.rangeSignals().getFirst().range().percent()));
        assertEquals(accepted?1:0,r.portfolio().trades().size());
        assertEquals(accepted?0:1,r.portfolio().events().stream().filter(e->e.eventType().equals("REJECTED_PATTERN_RANGE")).count());
        if(!accepted){assertEquals("PATTERN_RANGE_FILTER",r.rangeSignals().getFirst().rejectReason());assertTrue(r.portfolio().events().stream().noneMatch(e->e.eventType().equals("ENTRY")||e.eventType().equals("SIGNAL")));}
    }
    @Test void offPreservesV1ParityAndIgnoresThreshold() throws Exception {
        var frozen=configuration();var before=mapper.valueToTree(frozen);var c=config();var bars=Map.of("AAPL",engulfing("AAPL"));
        var s=service(detectors());var off=s.simulate(c,bars);var huge=s.simulate(c.withRangeFilter(false,bd("9999")),bars);
        assertEquals(off.portfolio().summary(),huge.portfolio().summary());assertEquals(off.portfolio().equityCurve(),huge.portfolio().equityCurve());assertEquals(off.costs(),huge.costs());
        var v1=new PatternV1PortfolioEngine(sizing,detectors()).run(new PatternV1Request(null,START,START.plusDays(30),BarInterval.DAILY,bd("10000"),null,null),frozen,trading.getRisk(),bars);
        assertEquals(v1.summary(),off.portfolio().summary());assertEquals(mapper.valueToTree(v1.equityCurve()),mapper.valueToTree(off.portfolio().equityCurve()));assertEquals(before,mapper.valueToTree(frozen));
    }
    @Test void persistedPairedReplayHasReliableRejectedOutcomeAndExactExcelFields() throws Exception {
        var c=config().withRangeFilter(true,bd("3.00"));var bars=new ArrayList<>(engulfing("AAPL"));bars.set(2,bar("AAPL",2,100,107,97,103));
        var r=service(detectors()).runOnSnapshot(c,Map.of("AAPL",bars));var store=new PatternV2Store(directory);var base=store.run(r.pairedRunId());
        assertEquals(base.datasetId(),r.datasetId());assertEquals(r.runId(),base.pairedRunId());assertEquals(c,base.configuration().withName(c.name()).withRangeFilter(true,bd("3.00")));
        assertEquals(bd("3.00"),store.run(r.runId()).configuration().minimumPatternRangePct());assertTrue(r.portfolio().trades().isEmpty());assertEquals(1,base.portfolio().trades().size());
        assertEquals("STOP",r.patternRangeAnalysis().baselineOutcomes().getFirst().status());assertEquals(base.portfolio().trades().getFirst().netPnl().doubleValue(),r.patternRangeAnalysis().baselineOutcomes().getFirst().netPnl());
        assertEquals(1,r.patternRangeAnalysis().groups().get(2).trades());assertEquals(17,r.patternRangeAnalysis().comparison().size());
        var replay=service(detectors()).replay(r.runId());assertEquals(r.portfolio().equityCurve(),replay.portfolio().equityCurve());assertEquals(r.datasetId(),replay.datasetId());
        assertEquals(1,r.patternRangeAnalysis().baselineBuckets().stream().mapToLong(PatternRangeAnalysis.Row::signals).sum());
        try(var book=new XSSFWorkbook(new java.io.ByteArrayInputStream(new PatternV2ExcelExporter().export(base)))) {
            var headers=new HashSet<String>();book.getSheet("TRADES").getRow(0).forEach(cell->headers.add(cell.getStringCellValue()));
            assertTrue(headers.containsAll(List.of("Pattern High","Pattern Low","Pattern Close","Pattern Range","Pattern Range %","Pattern Range Filter Enabled","Minimum Pattern Range %","Pattern Range Accepted")));
            Map<String,String> config=new HashMap<>();for(var row:book.getSheet("CONFIGURATION"))config.put(row.getCell(0).toString(),row.getCell(1)==null?null:row.getCell(1).toString());
            assertEquals("3.0",config.get("MinimumPatternRangePct"));assertEquals("FALSE",config.get("PatternRangeFilterEnabled"));
            assertNotNull(book.getSheet("PATTERN_RANGE_ANALYSIS"));assertNotNull(book.getSheet("PATTERN_RANGE_SIGNALS"));
            var symbolHeaders=new HashSet<String>();book.getSheet("SYMBOL_RESULTS").getRow(0).forEach(cell->symbolHeaders.add(cell.toString()));assertTrue(symbolHeaders.contains("PF Net Accepted"));
        }
    }
    @ParameterizedTest @CsvSource({"0.99,0","1,1","1.5,2","2,3","2.5,4","3,5","4,6","5,7","9,7"})
    void bucketBoundaries(String percent,int bucket){assertEquals(bucket,PatternRangeAnalysis.bucket(new PatternRange(bd("100").add(bd(percent)),bd("100"),bd("100"))));}
    @Test void positionSkippedAndOpenBaselineSignalsKeepUnavailableOutcomes() throws Exception {
        var detector=new PatternDetector() {
            public PatternType patternType(){return PatternType.HAMMER;}
            public PatternDirection direction(){return PatternDirection.LONG;}
            public Optional<PatternDetectionResult> detect(List<HistoricalBar> bars,int index,PatternDetectorParameters p){return index<2?Optional.of(new PatternDetectionResult(patternType(),direction(),bars.get(index).dateTime().toLocalDate(),index,index,bars.get(index).getClose(),null)):Optional.empty();}
        };
        var c=config().withSymbols(List.of(new PatternV2Configuration.Symbol(true,"AAPL",new PatternV2Configuration.Direction(true,PatternType.HAMMER,bd("3")),null,null,null,null,null))).withRangeFilter(true,bd("3"));
        var bars=Map.of("AAPL",List.of(bar("AAPL",0,101,102,100,101),bar("AAPL",1,101,102.5,100.5,101),bar("AAPL",2,101,102,100.5,101)));
        var s=service(List.of(detector));var raw=s.simulate(c,bars);
        assertTrue(raw.patternRangeAnalysis().baselineOutcomes().stream().allMatch(o->o.status().equals("BASELINE_NOT_AVAILABLE")&&o.netPnl()==null));
        var r=s.runOnSnapshot(c,bars);var a=r.patternRangeAnalysis();
        assertEquals(2,a.statistics().signalsBeforeFilter());assertEquals(2,a.statistics().signalsRejected());
        assertEquals("OPEN",a.baselineOutcomes().get(0).status());assertNull(a.baselineOutcomes().get(0).netPnl());
        assertEquals("SIGNAL_SKIPPED_POSITION_ALREADY_OPEN",a.baselineOutcomes().get(1).status());assertNull(a.baselineOutcomes().get(1).baselineTradeId());
        assertNull(a.groups().get(2).netPnl());assertEquals(1,a.groups().get(2).openTrades());
    }
    @Test void legacyConfigurationAndRunRemainReadableAndThresholdValidated() throws Exception {
        var json=(ObjectNode)mapper.valueToTree(config());json.remove(List.of("patternRangeFilterEnabled","minimumPatternRangePct"));var legacy=mapper.treeToValue(json,PatternV2Configuration.class);
        assertFalse(legacy.patternRangeFilterEnabled());assertEquals(bd("2.50"),legacy.minimumPatternRangePct());
        assertThrows(IllegalArgumentException.class,()->legacy.withRangeFilter(false,bd("-1")).validate(detectors()));
        var run=service(detectors()).simulate(legacy,Map.of());assertNull(run.patternRangeAnalysis().statistics().acceptanceRatePercent());
        var node=(ObjectNode)mapper.valueToTree(run);node.remove(List.of("rangeSignals","patternRangeAnalysis","datasetId","pairedRunId"));var old=mapper.treeToValue(node,PatternV2Run.class);
        assertNull(old.patternRangeAnalysis());assertDoesNotThrow(()->new PatternV2ExcelExporter().export(old));
    }
}
