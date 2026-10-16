package it.andreademartini.trading.backtest.patternv3;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import it.andreademartini.trading.backtest.patternstable.PatternStableStore;
import it.andreademartini.trading.backtest.patternv2.GapExecutionAudit;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.research.pattern.*;
import it.andreademartini.trading.risk.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SignalTimeframeTest {
    @TempDir Path temp;
    static HistoricalBar bar(LocalDate date,double open,double high,double low,double close,long volume){return new HistoricalBar("AAPL",date.atStartOfDay(),BigDecimal.valueOf(open),BigDecimal.valueOf(high),BigDecimal.valueOf(low),BigDecimal.valueOf(close),volume,BarInterval.DAILY,"TEST",PriceAdjustment.ADJUSTED);}
    static List<HistoricalBar> week(LocalDate monday,double open,double high,double low,double close){return ResearchSessionCalendar.weekSessions(monday).stream().map(d->bar(d,open,high,low,close,10)).toList();}
    PatternV3Configuration config(PatternType pattern,boolean longSide,int holding)throws Exception{
        var c=PatternV3Configuration.baseline(configuration(),new TradingSystemProperties().getRisk());
        var node=(com.fasterxml.jackson.databind.node.ObjectNode)PatternStableStore.JSON.valueToTree(c);var g=(com.fasterxml.jackson.databind.node.ObjectNode)node.get("global");
        g.put("startDate","2026-01-01");g.put("endDate","2026-05-29");g.put("maxHoldingBars",holding);g.put("initialCapital",10000);g.put("stopBufferTicks",0);
        c=PatternStableStore.JSON.treeToValue(node,PatternV3Configuration.class);
        var direction=new PatternV3Configuration.Direction(true,pattern,new BigDecimal("3"));
        return c.withSymbols(List.of(new PatternV3Configuration.Symbol(true,"AAPL",longSide?direction:null,longSide?null:direction,null,null,null,null))).withSignalTimeframe(SignalTimeframe.WEEKLY);
    }
    PatternV3Service service(){return new PatternV3Service(configuration(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),new PatternV3Store(temp));}
    @Test void aggregationUsesAllOhlcvAndOnlyExpectedCompleteSessions(){
        var monday=LocalDate.of(2026,3,30);var daily=new ArrayList<HistoricalBar>();
        daily.add(bar(monday,100,105,98,101,10));daily.add(bar(monday.plusDays(1),101,110,97,102,20));daily.add(bar(monday.plusDays(2),102,108,95,103,30));daily.add(bar(monday.plusDays(3),103,109,96,104,40));
        var warnings=new ArrayList<String>();var view=SignalBars.of(daily,SignalTimeframe.WEEKLY,warnings);assertTrue(warnings.isEmpty());assertEquals(1,view.bars().size());var w=view.bars().getFirst();
        assertEquals(100,w.open());assertEquals(110,w.high());assertEquals(95,w.low());assertEquals(104,w.close());assertEquals(100,w.volume());assertEquals(monday.plusDays(3),w.dateTime().toLocalDate());assertEquals(PriceAdjustment.ADJUSTED,w.priceAdjustment());
        assertEquals(-1,view.atDailyIndex(2));assertEquals(0,view.atDailyIndex(3));
        assertTrue(SignalBars.of(daily.subList(0,3),SignalTimeframe.WEEKLY,new ArrayList<>()).bars().isEmpty());
        daily.remove(1);assertTrue(SignalBars.of(daily,SignalTimeframe.WEEKLY,new ArrayList<>()).bars().isEmpty());
    }
    @Test void mondayHolidayAndEarlyCloseRemainValid(){
        assertEquals(DayOfWeek.TUESDAY,ResearchSessionCalendar.weekSessions(LocalDate.of(2026,1,19)).getFirst().getDayOfWeek());
        assertEquals(4,SignalBars.of(week(LocalDate.of(2026,1,19),100,102,99,101),SignalTimeframe.DAILY,new ArrayList<>()).bars().size());
        var thanksgiving=week(LocalDate.of(2026,11,23),100,102,99,101);assertEquals(4,thanksgiving.size());assertEquals(DayOfWeek.FRIDAY,thanksgiving.getLast().dateTime().getDayOfWeek());
        assertEquals(1,SignalBars.of(thanksgiving,SignalTimeframe.WEEKLY,new ArrayList<>()).bars().size());
        assertFalse(ResearchSessionCalendar.isSession(LocalDate.of(2012,10,29)));assertFalse(ResearchSessionCalendar.isSession(LocalDate.of(2018,12,5)));assertFalse(ResearchSessionCalendar.isSession(LocalDate.of(2025,1,9)));
        assertTrue(ResearchSessionCalendar.isSession(LocalDate.of(2021,12,31)));assertTrue(ResearchSessionCalendar.isSession(LocalDate.of(2021,6,18)));assertFalse(ResearchSessionCalendar.isSession(LocalDate.of(2022,6,20)));
        assertThrows(IllegalArgumentException.class,()->ResearchSessionCalendar.isSession(LocalDate.of(2009,1,1)));
    }
    @ParameterizedTest @CsvSource({"HAMMER,true,100,100.2,95,100.1","SHOOTING_STAR,false,100,105,99.9,100.1"})
    void existingSingleCandlePatternsUseWeeklyExtremes(PatternType type,boolean longSide,double open,double high,double low,double close)throws Exception{
        var daily=new ArrayList<>(week(LocalDate.of(2026,1,5),open,high,low,close));daily.add(bar(LocalDate.of(2026,1,12),101,101.1,100.9,101,10));
        var run=service().simulate(config(type,longSide,13),Map.of("AAPL",daily));var trade=run.executions().getFirst().trade();
        assertEquals(LocalDate.of(2026,1,9),trade.signalDate());assertEquals(LocalDate.of(2026,1,12),trade.entryDate());assertEquals(101,trade.entryPrice().doubleValue());assertEquals(longSide?low:high,trade.stopPrice().doubleValue());assertEquals(1,trade.holdingBars());
        assertEquals(3,trade.targetR().doubleValue());assertEquals(longSide?101+(101-low)*3:101-(high-101)*3,trade.takeProfitPrice().doubleValue());
    }
    List<HistoricalBar> engulfing(){var bars=new ArrayList<>(week(LocalDate.of(2026,1,5),100,101,98,99));bars.addAll(week(LocalDate.of(2026,1,12),98.5,102,98,101));return bars;}
    @ParameterizedTest @CsvSource({"true,95,GAP_STOP,95","true,110,GAP_TARGET,110","false,105,GAP_STOP,105","false,90,GAP_TARGET,90"})
    void weeklySignalsRetainDailyGapPriorityAndSingleSlippage(boolean longSide,double open,String kind,double base)throws Exception{
        var bars=longSide?engulfing():new ArrayList<>(week(LocalDate.of(2026,1,12),100,102,99.9,100.1));
        bars.add(bar(LocalDate.of(2026,1,20),100,100.1,99.9,100,10));bars.add(bar(LocalDate.of(2026,1,21),open,115,85,100,10));
        var c=config(longSide?PatternType.BULLISH_ENGULFING:PatternType.SHOOTING_STAR,longSide,13);var run=service().simulate(c,Map.of("AAPL",bars));var e=run.executions().getFirst();var t=e.trade();var a=run.executionAudit().trades().getFirst();
        assertEquals(LocalDate.of(2026,1,16),t.signalDate());assertEquals(LocalDate.of(2026,1,20),t.entryDate());assertEquals(LocalDate.of(2026,1,21),t.exitDate());assertEquals(2,t.holdingBars());assertFalse(t.sameBarAmbiguous());
        assertEquals(kind,a.gapExitType().name());assertEquals(base,a.exitBasePrice().doubleValue());assertNull(a.issue());
        var rate=c.global().slippagePercentPerSide()/100;assertEquals(base*(longSide?1-rate:1+rate),e.exitPriceAfterSlippage().doubleValue(),1e-10);
        assertEquals(100*(longSide?1+rate:1-rate),e.entryPriceAfterSlippage().doubleValue(),1e-10);
        assertEquals(0,t.netPnl().compareTo(t.grossPnl().subtract(e.commission()).subtract(e.slippageCost()).subtract(e.transactionCosts())));
    }
    @ParameterizedTest @CsvSource({"100,101,97,STOP","100,107,99,TARGET","100,107,97,STOP","100,100.1,99.9,TIMEOUT"})
    void intrabarAndTimeoutUseDailySessions(double open,double high,double low,String reason)throws Exception{
        var bars=engulfing();bars.add(bar(LocalDate.of(2026,1,20),100,100.1,99.9,100,10));bars.add(bar(LocalDate.of(2026,1,21),open,high,low,100,10));
        var run=service().simulate(config(PatternType.BULLISH_ENGULFING,true,2),Map.of("AAPL",bars));var t=run.executions().getFirst().trade();
        assertEquals(reason,t.exitReason());assertEquals(2,t.holdingBars());assertEquals(LocalDate.of(2026,1,21),t.exitDate());
    }
    @Test void holidayFridayConfirmsThursdayAndNeverEntersInSignalWeek()throws Exception{
        var bars=new ArrayList<>(week(LocalDate.of(2026,3,30),100,100.2,95,100.1));bars.add(bar(LocalDate.of(2026,4,6),101,101.1,100.9,101,10));
        var run=service().simulate(config(PatternType.HAMMER,true,13),Map.of("AAPL",bars));var t=run.executions().getFirst().trade();assertEquals(LocalDate.of(2026,4,2),t.signalDate());assertEquals(LocalDate.of(2026,4,6),t.entryDate());
        bars.remove(3);assertTrue(service().simulate(config(PatternType.HAMMER,true,13),Map.of("AAPL",bars)).executions().isEmpty());
    }
    @Test void invalidWeeklyEntryRejectedAndEntryIntrabarRemainsActive()throws Exception{
        var bars=engulfing();bars.add(bar(LocalDate.of(2026,1,20),97,97.1,96.9,97,10));var c=config(PatternType.BULLISH_ENGULFING,true,13);
        var r=service().simulate(c,Map.of("AAPL",bars));assertTrue(r.executions().isEmpty());assertTrue(r.portfolio().events().stream().anyMatch(e->e.eventType().equals("SIGNAL_SKIPPED_INVALID_RISK")));
        bars.set(bars.size()-1,bar(LocalDate.of(2026,1,20),100,107,97,100,10));r=service().simulate(c,Map.of("AAPL",bars));var t=r.executions().getFirst().trade();assertEquals(1,t.holdingBars());assertEquals("STOP",t.exitReason());assertTrue(t.sameBarAmbiguous());assertEquals(GapExecutionAudit.GapType.NONE,r.executionAudit().trades().getFirst().gapExitType());
    }
    @Test void indicatorsUseCompletedWeeklyBarsWithTheirOriginalWarmups()throws Exception{
        var c=config(PatternType.HAMMER,true,13);var daily=new ArrayList<HistoricalBar>();for(int i=0;i<35;i++)daily.addAll(week(LocalDate.of(2025,1,13).plusWeeks(i),100+i,103+i,98+i,101+i));
        var view=SignalBars.of(daily,SignalTimeframe.WEEKLY,new ArrayList<>());var bb=new BollingerAtr(c);var regime=new MarketRegimeIndicators(c);BollingerAtr.Value b=null;MarketRegimeIndicators.Value m=null;
        for(int i=0;i<view.bars().size();i++){b=bb.onBar(view.bars().get(i));m=regime.onBar(view.bars().get(i));if(i<14)assertNull(b.atr());if(i<19){assertNull(b.middle());assertNull(m.ema20());}if(i<27)assertNull(m.adx14());}
        assertNotNull(b.atr());assertNotNull(b.upper());assertNotNull(m.adx14());assertNotNull(m.ema20());assertNull(m.ema200());
        assertEquals(5,b.atr().doubleValue(),1e-8);assertEquals(125.5,b.middle().doubleValue(),1e-8);assertEquals(125.5,m.ema20(),1e-8);
    }
    @Test void configPersistenceHashHistoryAndExcelRetainTimeframe()throws Exception{
        var c=config(PatternType.BULLISH_ENGULFING,true,2);var daily=c.withSignalTimeframe(SignalTimeframe.DAILY);assertNotEquals(PatternStableStore.hash(c),PatternStableStore.hash(daily));
        assertEquals(SignalTimeframe.WEEKLY,c.withName("copy").withGlobal(c.global()).withSymbols(c.symbols()).withRangeFilter(false,c.minimumPatternRangePct()).withBollingerAtr(false,20,new BigDecimal("2"),14,new BigDecimal(".5")).withMarketRegimeFilter(false,14,25).signalTimeframe());
        var bars=engulfing();bars.add(bar(LocalDate.of(2026,1,20),100,100.1,99.9,100,10));bars.add(bar(LocalDate.of(2026,1,21),100,100.1,99.9,100,10));var store=new PatternV3Store(temp);var run=service().simulate(c,Map.of("AAPL",bars));store.save(run);assertEquals(SignalTimeframe.WEEKLY,store.run(run.runId()).signalTimeframe());
        assertEquals(SignalTimeframe.WEEKLY,new PatternV3Controller(service(),store,new PatternV3ExcelExporter()).history().getFirst().get("signalTimeframe"));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(new PatternV3ExcelExporter().export(run)))){assertNotNull(book.getSheet("GAP_EXIT_ANALYSIS"));var sheet=book.getSheet("TRADES");var columns=new HashMap<String,Integer>();for(var cell:sheet.getRow(0))columns.put(cell.toString(),cell.getColumnIndex());assertEquals("WEEKLY",sheet.getRow(1).getCell(columns.get("SignalTimeframe")).toString());assertEquals("2026-01-12",sheet.getRow(1).getCell(columns.get("SignalPeriodStart")).toString());assertEquals("2026-01-16",sheet.getRow(1).getCell(columns.get("SignalConfirmationDate")).toString());assertTrue(columns.containsKey("GapExitType"));}
    }
    @Test void productionFiltersAndMarketRegimeSampleWeeklyIndicators()throws Exception{
        var c=config(PatternType.HAMMER,true,1);c=c.withGlobal(c.global().withDates(LocalDate.of(2025,1,13),LocalDate.of(2025,9,12))).withMarketRegimeAnalysis(true);
        var bars=new ArrayList<HistoricalBar>();for(int i=0;i<35;i++)bars.addAll(week(LocalDate.of(2025,1,13).plusWeeks(i),100+i,100.2+i,95+i,100.1+i));
        var view=SignalBars.of(bars,SignalTimeframe.WEEKLY,new ArrayList<>());var bb=new BollingerAtr(c);var mr=new MarketRegimeIndicators(c);var expectedBB=new HashMap<LocalDate,BollingerAtr.Value>();var expectedMR=new HashMap<LocalDate,MarketRegimeIndicators.Value>();
        for(var w:view.bars()){expectedBB.put(w.dateTime().toLocalDate(),bb.onBar(w));expectedMR.put(w.dateTime().toLocalDate(),mr.onBar(w));}
        var run=service().simulate(c,Map.of("AAPL",bars));assertTrue(run.bollingerAtrSignals().size()>25);assertFalse(run.marketRegimeAnalysis().trades().isEmpty());
        for(var audit:run.bollingerAtrSignals())assertEquals(expectedBB.get(audit.signalDate()),audit.indicators());
        for(var trade:run.marketRegimeAnalysis().trades())assertEquals(expectedMR.get(trade.signalDate()),trade.context().indicators());
        var filtered=service().simulate(c.withBollingerAtr(true,20,new BigDecimal("2"),14,new BigDecimal(".5")),Map.of("AAPL",bars));assertTrue(filtered.bollingerAtrSignals().stream().anyMatch(a->a.status()==BollingerAtrAnalysis.Status.INSUFFICIENT_DATA));
    }
    @Test void futureBarsAndBacktestEndCannotCompleteASignalEarly()throws Exception{
        var bars=engulfing();var c=config(PatternType.BULLISH_ENGULFING,true,13);var end=LocalDate.of(2026,1,15);
        var truncated=service().simulate(c.withGlobal(c.global().withDates(LocalDate.of(2026,1,1),end)),Map.of("AAPL",bars));assertTrue(truncated.rangeSignals().isEmpty());assertTrue(truncated.executions().isEmpty());
        var prefix=SignalBars.of(bars.subList(0,7),SignalTimeframe.WEEKLY,new ArrayList<>());var full=SignalBars.of(bars,SignalTimeframe.WEEKLY,new ArrayList<>());
        assertEquals(prefix.bars().getFirst(),full.bars().getFirst());assertEquals(-1,full.atDailyIndex(6));assertEquals(1,full.atDailyIndex(9));
    }
    @Test void adjustedPricesAreNotAdjustedAgainAndMixedProvenanceIsRejected(){
        var bars=new ArrayList<>(week(LocalDate.of(2026,1,5),25,26,24,25.5));var view=SignalBars.of(bars,SignalTimeframe.WEEKLY,new ArrayList<>());assertEquals(25,view.bars().getFirst().open());assertEquals(24,view.bars().getFirst().low());
        var b=bars.get(2);bars.set(2,new HistoricalBar("AAPL",b.dateTime(),b.getOpen(),b.getHigh(),b.getLow(),b.getClose(),b.volume(),BarInterval.DAILY,b.provider(),PriceAdjustment.RAW));
        assertThrows(IllegalArgumentException.class,()->SignalBars.of(bars,SignalTimeframe.WEEKLY,new ArrayList<>()));
    }
    @Test void replayAndComparisonsAllowOnlyTheTimeframeToDiffer()throws Exception{
        var bars=engulfing();bars.add(bar(LocalDate.of(2026,1,20),100,100.1,99.9,100,10));var c=config(PatternType.BULLISH_ENGULFING,true,2).withSignalTimeframe(SignalTimeframe.DAILY);
        var svc=service();var daily=svc.runOnSnapshot(c,Map.of("AAPL",bars));var weekly=svc.replay(daily.runId(),c.withSignalTimeframe(SignalTimeframe.WEEKLY));
        assertEquals(SignalTimeframe.WEEKLY,weekly.signalTimeframe());assertEquals(daily.datasetId(),weekly.datasetId());assertEquals(2,svc.compare(List.of(daily.runId(),weekly.runId())).runs().size());
        assertThrows(IllegalArgumentException.class,()->PatternV3RunComparison.requireSameSignalExperimentConfiguration(c,c.withSignalTimeframe(SignalTimeframe.WEEKLY).withRangeFilter(true,new BigDecimal("3"))));
        var json=PatternStableStore.JSON;var hash=daily.configurationHash();var bytes=json.writeValueAsBytes(daily);assertEquals(hash,json.readValue(bytes,PatternV3Run.class).configurationHash());assertEquals(SignalTimeframe.DAILY,svc.replay(daily.runId()).signalTimeframe());
    }
}
