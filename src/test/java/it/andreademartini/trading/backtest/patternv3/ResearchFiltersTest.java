package it.andreademartini.trading.backtest.patternv3;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import it.andreademartini.trading.backtest.patternstable.PatternStableStore;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.research.pattern.*;
import it.andreademartini.trading.risk.*;
import java.math.*;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import java.io.ByteArrayInputStream;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResearchFiltersTest {
 @TempDir Path temp;
 PatternV3Service service(){return new PatternV3Service(configuration(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),new PatternV3Store(temp));}
 PatternV3Configuration config(){var c=PatternV3Configuration.baseline(configuration(),new TradingSystemProperties().getRisk());var d=new PatternV3Configuration.Direction(true,PatternType.BULLISH_ENGULFING,bd("3"));return c.withGlobal(c.global().withDates(START,START.plusDays(20))).withSymbols(List.of(new PatternV3Configuration.Symbol(true,"AAPL",d,null,null,null,null,2)));}
 static HistoricalBar volume(HistoricalBar b,long volume,String provider){return new HistoricalBar(b.symbol(),b.dateTime(),b.getOpen(),b.getHigh(),b.getLow(),b.getClose(),volume,b.interval(),provider,b.priceAdjustment());}
 @Test void metricFormulaRemainsDirectionalAndNewMinimumDoesNotRedefineLegacyMaximum(){
  var r=new PatternRange(bd("110"),bd("100"),bd("109.5"));assertEquals(0,bd(".95").compareTo(CloseStrength.value(r,PatternDirection.LONG)));assertEquals(0,bd(".05").compareTo(CloseStrength.value(r,PatternDirection.SHORT)));
  assertTrue(CloseStrength.acceptsMinimum(r,PatternDirection.LONG,bd(".906")));assertFalse(CloseStrength.acceptsMinimum(r,PatternDirection.SHORT,bd(".906")));assertFalse(CloseStrength.accepts(r,PatternDirection.LONG,bd(".8")));assertTrue(CloseStrength.accepts(r,PatternDirection.SHORT,bd(".8")));
  var shortRange=new PatternRange(bd("110"),bd("100"),bd("100.5"));assertTrue(CloseStrength.acceptsMinimum(shortRange,PatternDirection.SHORT,bd(".906")));
  var flat=new PatternRange(bd("100"),bd("100"),bd("100"));assertNull(CloseStrength.value(flat,PatternDirection.LONG));assertFalse(CloseStrength.acceptsMinimum(flat,PatternDirection.SHORT,bd("0")));
 }
 @Test void rvolExcludesCurrentBarAndHandlesWarmupZeroAndUnverifiedSplitScale(){
  var indicator=new RelativeVolume(2,RelativeVolume.Basis.CONSISTENT_DATASET_UNITS);var bars=engulfing("AAPL");assertEquals("RVOL_WARMUP",indicator.onBar(volume(bars.get(0),100,"TEST")).issue());assertNull(indicator.onBar(volume(bars.get(1),200,"TEST")).relativeVolume());var value=indicator.onBar(volume(bars.get(2),300,"TEST"));assertEquals(0,bd("150").compareTo(value.average()));assertEquals(0,bd("2").compareTo(value.relativeVolume()));
  assertEquals("CURRENT_VOLUME_NOT_AVAILABLE",indicator.onBar(volume(bar("AAPL",3,100,101,99,100),0,"TEST")).issue());assertEquals("HISTORICAL_VOLUME_NOT_AVAILABLE",indicator.onBar(volume(bar("AAPL",4,100,101,99,100),100,"TEST")).issue());
  var unknown=new RelativeVolume(1,RelativeVolume.Basis.UNVERIFIED);unknown.onBar(volume(bars.get(0),100,"STOOQ"));var uncertain=unknown.onBar(volume(bars.get(1),400,"STOOQ"));assertNull(uncertain.relativeVolume());assertEquals("UNVERIFIED_VOLUME_ADJUSTMENT",uncertain.issue());
  var mixed=new RelativeVolume(1,RelativeVolume.Basis.CONSISTENT_DATASET_UNITS);mixed.onBar(bars.get(0));assertEquals("MIXED_VOLUME_PROVENANCE",mixed.onBar(volume(bars.get(1),1000,"OTHER")).issue());
 }
 @Test void everyRawSignalRecordsAllRejectionsEvenWhenAdxWouldPreviouslyHideIt(){
  var c=config().withResearchFilters(true,bd(".906"),true,bd("5"),null,true,1,bd("2"),null).withRelativeVolumeBasis(RelativeVolume.Basis.CONSISTENT_DATASET_UNITS).withMarketRegimeFilter(true,14,25).withBollingerAtr(true,20,bd("2"),14,bd(".5"));
  var run=service().simulate(c,Map.of("AAPL",engulfing("AAPL")));assertTrue(run.executions().isEmpty());assertEquals(1,run.signalFilterAudit().size());var row=run.signalFilterAudit().getFirst();
  assertFalse(row.finalSignalAccepted());assertFalse(row.orderAttempted());assertFalse(row.tradeExecuted());assertEquals(List.of("CLOSE_STRENGTH_FILTER","PATTERN_RANGE_FILTER","RELATIVE_VOLUME_FILTER","INSUFFICIENT_REGIME_DATA","BOLLINGER_ATR_INSUFFICIENT_DATA"),row.rejectionReasons());assertNotNull(row.patternRangePct());assertNotNull(row.closeStrength());assertNotNull(row.relativeVolume());assertEquals("MIN_GE",row.closeStrengthRule());
 }
 @Test void eachFilterCanBeDisabledWithoutLosingItsThresholdOrRejectingAValidSignal(){
  var bars=new ArrayList<>(engulfing("AAPL"));bars.add(bar("AAPL",3,100,100.1,99.9,100));var off=config().withResearchFilters(false,bd(".906"),false,bd("5"),bd("6"),false,20,bd("1.2"),null);
  var baseline=service().simulate(config(),Map.of("AAPL",bars));var run=service().simulate(off,Map.of("AAPL",bars));assertEquals(baseline.portfolio().finalCapital(),run.portfolio().finalCapital());assertEquals(baseline.costs(),run.costs());assertEquals(baseline.portfolio().summary(),run.portfolio().summary());assertTrue(run.signalFilterAudit().getFirst().finalSignalAccepted());assertEquals("RVOL_WARMUP",run.signalFilterAudit().getFirst().relativeVolumeIssue());
  var min=service().simulate(off.withResearchFilters(true,bd(".906"),false,bd("5"),bd("6"),false,20,bd("1.2"),null),Map.of("AAPL",bars));assertTrue(min.executions().isEmpty());
  var max=service().simulate(off.withResearchFilters(false,bd(".906"),true,bd("1.38"),bd("2"),false,20,bd("1.2"),null),Map.of("AAPL",bars));assertTrue(max.executions().isEmpty());
  var volume=service().simulate(off.withResearchFilters(false,bd(".906"),false,bd("5"),bd("6"),true,20,bd("1.2"),null),Map.of("AAPL",bars));assertTrue(volume.executions().isEmpty());assertNull(volume.signalFilterAudit().getFirst().relativeVolumePassed());
 }
 @Test void acceptedFilteredSignalRecordsEntryExecutionAndNotJustAcceptance(){
  var c=config().withResearchFilters(true,bd(".8"),true,bd("1.38"),bd("3.63"),true,1,bd("1"),bd("1")).withRelativeVolumeBasis(RelativeVolume.Basis.CONSISTENT_DATASET_UNITS);
  var run=service().simulate(c,Map.of("AAPL",engulfing("AAPL")));var a=run.signalFilterAudit().getFirst();assertTrue(a.finalSignalAccepted());assertTrue(a.orderPlanned());assertTrue(a.orderAttempted());assertTrue(a.tradeExecuted());assertEquals(run.executions().getFirst().trade().tradeId(),a.tradeId());assertNull(a.notExecutedReason());
  var noNext=service().simulate(c,Map.of("AAPL",engulfing("AAPL").subList(0,2))).signalFilterAudit().getFirst();assertTrue(noNext.finalSignalAccepted());assertFalse(noNext.orderAttempted());assertFalse(noNext.tradeExecuted());assertEquals("SIGNAL_SKIPPED_NO_NEXT_BAR",noNext.notExecutedReason());
  var invalidEntry=new ArrayList<>(engulfing("AAPL"));invalidEntry.set(2,bar("AAPL",2,97,97.1,96.9,97));var rejected=service().simulate(c,Map.of("AAPL",invalidEntry)).signalFilterAudit().getFirst();assertTrue(rejected.finalSignalAccepted());assertTrue(rejected.orderAttempted());assertFalse(rejected.tradeExecuted());assertEquals("SIGNAL_SKIPPED_INVALID_RISK",rejected.notExecutedReason());
 }
 @Test void weeklyVolumeUsesCompletedWeekTotalsIncludingHolidayWeeks()throws Exception{
  var helper=new SignalTimeframeTest();helper.temp=temp;var c=helper.config(PatternType.BULLISH_ENGULFING,true,2).withResearchFilters(false,bd(".906"),false,bd("1.38"),null,true,1,bd(".9"),null).withRelativeVolumeBasis(RelativeVolume.Basis.CONSISTENT_DATASET_UNITS);
  var daily=helper.engulfing();daily.add(SignalTimeframeTest.bar(LocalDate.of(2026,1,20),100,100.1,99.9,100,10));var run=service().simulate(c,Map.of("AAPL",daily));var a=run.signalFilterAudit().getFirst();assertEquals(SignalTimeframe.WEEKLY,a.signalTimeframe());assertEquals(50,a.signalVolume());assertEquals(0,bd("50").compareTo(a.relativeVolumeAverage()));assertEquals(0,BigDecimal.ONE.compareTo(a.relativeVolume()));assertEquals(LocalDate.of(2026,1,20),run.executions().getFirst().trade().entryDate());
  var weeks=SignalBars.of(new ArrayList<>(SignalTimeframeTest.week(LocalDate.of(2026,1,12),100,101,99,100)),SignalTimeframe.WEEKLY,new ArrayList<>());var holiday=SignalBars.of(SignalTimeframeTest.week(LocalDate.of(2026,1,19),100,101,99,100),SignalTimeframe.WEEKLY,new ArrayList<>());
  var stream=new RelativeVolume(1,RelativeVolume.Basis.CONSISTENT_DATASET_UNITS);stream.onBar(weeks.bars().getFirst());assertEquals(0,bd(".8").compareTo(stream.onBar(holiday.bars().getFirst()).relativeVolume()));
 }
 @Test void validationAndCopiesPreserveExplicitBoundsAndLegacyConfigurationHashes()throws Exception{
  var c=config().withResearchFilters(true,bd(".906"),true,bd("1.38"),null,false,20,bd("1.20"),null);assertEquals(bd(".906"),c.withName("copy").withSignalTimeframe(SignalTimeframe.WEEKLY).withMarketRegimeFilter(false,14,25).closeStrengthMin());assertNull(c.patternRangeMaxPct());c.validate(detectors());
  for(var invalid:List.of(c.withResearchFilters(true,bd("1.01"),true,bd("1"),null,false,20,bd("1.2"),null),c.withResearchFilters(false,null,true,bd("4"),bd("3"),false,20,bd("1.2"),null),c.withResearchFilters(false,null,false,bd("1"),null,true,0,bd("1.2"),null),c.withResearchFilters(false,null,false,bd("1"),null,true,20,bd("2"),bd("1"))))assertThrows(IllegalArgumentException.class,()->invalid.validate(detectors()));
  // Keep legacy compatibility checks independent of the application's mutable saved configuration.
  PatternV3Configuration old;
  try(var fixture=ResearchFiltersTest.class.getResourceAsStream("/pattern-v3/legacy-configuration.json")){
   assertNotNull(fixture,"Missing legacy configuration fixture");
   old=PatternStableStore.JSON.readValue(fixture,PatternV3Configuration.class);
  }
  assertNull(old.closeStrengthMin());var hash=PatternStableStore.hash(old);assertEquals(hash,PatternStableStore.hash(PatternStableStore.JSON.readValue(PatternStableStore.JSON.writeValueAsBytes(old),PatternV3Configuration.class)));assertNotEquals(hash,PatternStableStore.hash(c));
  var invalidPeriod=(com.fasterxml.jackson.databind.node.ObjectNode)PatternStableStore.JSON.valueToTree(c);invalidPeriod.put("relativeVolumePeriod",1.5);assertThrows(Exception.class,()->PatternStableStore.JSON.treeToValue(invalidPeriod,PatternV3Configuration.class));
 }
 @Test void persistedAuditsAndExcelAgreeAndOldRunsDoNotReceiveInventedAudits()throws Exception{
  var bars=new ArrayList<>(engulfing("AAPL"));bars.add(bar("AAPL",3,100,100.1,99.9,100));var run=service().simulate(config(),Map.of("AAPL",bars));var store=new PatternV3Store(temp);store.save(run);assertEquals(run.signalFilterAudit(),store.run(run.runId()).signalFilterAudit());assertEquals(run.signalFilterAudit(),run.withBenchmark(null).withResearch(run.patternRangeAnalysis(),"dataset",null).signalFilterAudit());
  var groups=ResearchFilterAnalysis.groups(run);assertEquals(run.portfolio().summary().closedTrades(),groups.stream().filter(g->g.section().equals("CloseStrength")&&g.stratum().equals("ALL")&&g.bucket().equals("ALL")).findFirst().orElseThrow().statistics().trades());
  try(var book=new XSSFWorkbook(new ByteArrayInputStream(new PatternV3ExcelExporter().export(run)))){assertNotNull(book.getSheet("GAP_EXIT_ANALYSIS"));assertNotNull(book.getSheet("FILTER_ANALYSIS"));var audit=book.getSheet("SIGNAL_FILTER_AUDIT");assertEquals(run.signalFilterAudit().size(),audit.getLastRowNum());var headers=new HashSet<String>();for(var cell:book.getSheet("TRADES").getRow(0))assertTrue(headers.add(cell.toString()),"Duplicate trade column: "+cell);assertTrue(headers.containsAll(ResearchFilterExcel.TRADE_HEADERS));
   for(var sheet:book)for(var row:sheet)for(var cell:row){assertNotEquals(CellType.ERROR,cell.getCellType());if(cell.getCellType()==CellType.NUMERIC)assertTrue(Double.isFinite(cell.getNumericCellValue()));}}
  var legacy=PatternStableStore.JSON.valueToTree(run);((com.fasterxml.jackson.databind.node.ObjectNode)legacy).remove("signalFilterAudit");var restored=PatternStableStore.JSON.treeToValue(legacy,PatternV3Run.class);assertNull(restored.signalFilterAudit());assertNull(restored.filterModelVersion());
 }
}
