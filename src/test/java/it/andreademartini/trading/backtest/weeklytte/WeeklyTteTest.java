package it.andreademartini.trading.backtest.weeklytte;

import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.backtest.patternv2.*;
import it.andreademartini.trading.backtest.patternv3.ResearchSessionCalendar;
import it.andreademartini.trading.risk.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.junit.jupiter.api.io.TempDir;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.weeklytte.WeeklyTteRun.*;

class WeeklyTteTest {
    @TempDir Path temp;
    static WeeklyTteConfiguration config(Object...changes){
        var n=(com.fasterxml.jackson.databind.node.ObjectNode)WeeklyTteStore.JSON.valueToTree(WeeklyTteConfiguration.defaults());
        // Regression suite pins v1 rules: old saved snapshots must remain reproducible.
        n.remove(List.of("rulesVersion","allowLong","allowShort"));
        n.put("startDate","2024-05-13");n.put("endDate","2024-05-31");n.set("symbols",WeeklyTteStore.JSON.valueToTree(List.of("TEST")));
        for(int i=0;i<changes.length;i+=2)n.set(changes[i].toString(),WeeklyTteStore.JSON.valueToTree(changes[i+1]));
        return WeeklyTteStore.JSON.convertValue(n,WeeklyTteConfiguration.class);
    }
    static HistoricalBar bar(String day,double o,double h,double l,double c){return new HistoricalBar("TEST",LocalDate.parse(day).atStartOfDay(),java.math.BigDecimal.valueOf(o),java.math.BigDecimal.valueOf(h),java.math.BigDecimal.valueOf(l),java.math.BigDecimal.valueOf(c),1000,BarInterval.DAILY,"SYNTHETIC",PriceAdjustment.RAW);}
    static List<HistoricalBar> fixture(){return new ArrayList<>(List.of(
        bar("2024-05-06",90,110,88,90),bar("2024-05-07",90,98,88,90),bar("2024-05-08",90,98,88,90),bar("2024-05-09",90,98,88,90),bar("2024-05-10",90,99,88,90),
        bar("2024-05-13",99,102,98,101),bar("2024-05-14",99,100,95,96),bar("2024-05-15",96,100,95.5,99),
        bar("2024-05-16",100.5,102,99,101),bar("2024-05-17",105,111,103,110)));
    }
    static WeeklyTteEngine engine(){return new WeeklyTteEngine(new PositionSizingService(new TradePricingService()));}
    static WeeklyTteRun run(WeeklyTteConfiguration c,List<HistoricalBar> bars){var snapshot=PatternV2Dataset.capture(Map.of("TEST",bars));return engine().simulate(c,snapshot.bars(),snapshot.id(),WeeklyTteStore.hash(c));}
    static WeeklyTteRun.WeeklyLevel reference(List<HistoricalBar> bars){return WeeklyTteLevels.calculate("TEST",bars,LocalDate.parse("2024-05-13"),LocalDate.parse("2024-05-31")).getFirst();}
    static Setup signal(WeeklyTteRun r){return r.setups().stream().filter(a->a.signalDate.equals(LocalDate.parse("2024-05-15"))).findFirst().orElseThrow();}
    @Test void previousCalendarWeekHighAndFixedReference(){var r=run(config(),fixture());assertEquals(110,reference(fixture()).previousWeekHigh());assertEquals(110,signal(r).previousWeekHigh);assertEquals(110,r.trades().getFirst().previousWeekHigh());}
    @Test void currentHighCannotDefineReference(){var bars=fixture();bars.set(9,bar("2024-05-17",105,1000,103,110));assertEquals(110,reference(bars).previousWeekHigh());assertEquals(signal(run(config(),fixture())).entryTrigger,signal(run(config(),bars)).entryTrigger);}
    @Test void holidayWeekIsComplete(){var bars=new ArrayList<HistoricalBar>();for(var d:ResearchSessionCalendar.weekSessions(LocalDate.parse("2024-07-01")))bars.add(bar(d.toString(),90,110,80,100));assertEquals(4,bars.size());assertTrue(WeeklyTteLevels.complete(LocalDate.parse("2024-07-01"),bars));}
    @Test void lastPartialWeekExcludedFromProbability(){var r=run(config("endDate","2024-05-16"),fixture());assertFalse(r.weeklyLevels().getFirst().completedWeek());assertEquals(0L,((Map<?,?>)r.analysis().get("weeklyTouchOverall")).get("sample"));}
    @Test void missingPreviousSessionExcludesSetup(){var bars=fixture();bars.remove(2);var r=run(config(),bars);assertEquals("INCOMPLETE_REFERENCE_WEEK",signal(r).rejectionReason);assertTrue(r.trades().isEmpty());}
    @Test void absentImmediateWeekNeverUsesOlderWeek(){var bars=fixture();bars.removeIf(b->b.dateTime().toLocalDate().isBefore(LocalDate.parse("2024-05-13")));assertNull(reference(bars).previousWeekHigh());}
    @Test void momentumValidAndRecoveryDeterministic(){var a=signal(run(config(),fixture()));assertTrue(a.momentumValue>0);assertEquals("ACCEPTED",a.setupStatus);assertEquals(95,a.pullbackLow);assertEquals(LocalDate.parse("2024-05-14"),a.pullbackStartDate);}
    @Test void momentumRejectsAndCanBeDisabled(){var bars=fixture();bars.set(2,bar("2024-05-08",105,108,100,105));assertEquals("MOMENTUM_NOT_UP",signal(run(config(),bars)).rejectionReason);assertEquals("ACCEPTED",signal(run(config("momentumFilter",false),bars)).setupStatus);}
    @Test void pullbackInvalidWithRisingHigh(){var bars=fixture();bars.set(6,bar("2024-05-14",99,103,95,96));assertNull(WeeklyTteEngine.detect(config(),"TEST",bars,7,reference(bars)));}
    @ParameterizedTest @CsvSource({"99,100,95,98","96,100,95,96","99,100,95,99"})
    void invalidRecovery(double o,double h,double l,double close){var bars=fixture();bars.set(7,bar("2024-05-15",o,h,l,close));assertNull(WeeklyTteEngine.detect(config(),"TEST",bars,7,reference(bars)));}
    @Test void noFuturePivotRequired(){var bars=fixture();var prefix=new ArrayList<>(bars.subList(0,8));var a=WeeklyTteEngine.detect(config(),"TEST",prefix,7,reference(prefix));assertNotNull(a);assertEquals(signal(run(config(),bars)).entryTrigger,a.entryTrigger);}
    @Test void noDuplicateSetupIds(){var r=run(config(),fixture());assertEquals(r.setups().size(),r.setups().stream().map(s->s.setupId).distinct().count());}
    @Test void previousTouchRejects(){var bars=fixture();bars.set(5,bar("2024-05-13",99,111,98,101));assertEquals("TARGET_ALREADY_TOUCHED",signal(run(config(),bars)).rejectionReason);}
    @Test void weekChangeExpiresPending(){var bars=fixture();bars.set(8,bar("2024-05-16",99,99.5,97,98));bars.set(9,bar("2024-05-17",98,99,96,97));bars.add(bar("2024-05-20",99,102,98,101));assertEquals("EXPIRED_WEEK_CHANGE",signal(run(config(),bars)).setupStatus);}
    @Test void entryNeverOnSignal(){var r=run(config(),fixture());assertEquals(LocalDate.parse("2024-05-16"),signal(r).entryDate);assertTrue(signal(r).entryDate.isAfter(signal(r).signalDate));}
    @Test void untriggeredSignalExpires(){var bars=fixture();bars.set(8,bar("2024-05-16",99,99.5,97,98));bars.set(9,bar("2024-05-17",98,99,96,97));assertFalse(signal(run(config("entryValidityBars",1),bars)).orderTriggered);assertEquals("EXPIRED",signal(run(config("entryValidityBars",1),bars)).setupStatus);}
    @Test void openGapUsesOpenAndBuySlippage(){var a=signal(run(config(),fixture()));assertTrue(a.gapEntry);assertEquals(100.5,a.entryBasePrice);assertEquals(100.5*1.0003,a.entryExecutionPrice,1e-10);}
    @Test void intrabarUsesTrigger(){var bars=fixture();bars.set(8,bar("2024-05-16",99,102,98,101));var a=signal(run(config(),bars));assertFalse(a.gapEntry);assertEquals(100.01,a.entryBasePrice,1e-10);}
    @Test void gapRecalculatesRewardRisk(){var bars=fixture();bars.set(8,bar("2024-05-16",108,109,106,108));var a=signal(run(config(),bars));assertEquals("INSUFFICIENT_REWARD_RISK",a.rejectionReason);assertTrue(a.theoreticalRewardRisk>1.5);assertTrue(a.realizedRewardRisk<1.5);}
    @Test void targetReachedAtOpenBeforeEntry(){var bars=fixture();bars.set(8,bar("2024-05-16",111,112,108,110));assertEquals("TARGET_REACHED_BEFORE_ENTRY",signal(run(config(),bars)).setupStatus);}
    @Test void invalidStopRejects(){assertEquals("INVALID_STOP",signal(run(config("stopBuffer",10000),fixture())).rejectionReason);}
    @Test void capitalTooSmallRejects(){var a=signal(run(config("initialCapital",1),fixture()));assertEquals("INSUFFICIENT_CAPITAL_OR_RISK_BUDGET",a.rejectionReason);}
    @ParameterizedTest @CsvSource({"90,100,88,99,1,10,STOP_LOSS,GAP_STOP,90","115,120,90,100,1,10,TAKE_PROFIT,GAP_TARGET,115","100,112,94,105,1,10,STOP_LOSS,NONE,95","100,112,96,105,1,10,TAKE_PROFIT,NONE,110","100,105,96,102,10,10,TIMEOUT,NONE,102","100,105,94,102,1,10,STOP_LOSS,NONE,95"})
    void exitRules(double o,double h,double l,double close,int held,int max,String reason,String gap,double price){var e=WeeklyTteEngine.exit(bar("2024-05-16",o,h,l,close),95,110,5,held,max,false);assertEquals(reason,e.reason());assertEquals(gap,e.gap());assertEquals(price,e.price());}
    @Test void noExitWithoutLevelOrTimeout(){assertNull(WeeklyTteEngine.exit(bar("2024-05-16",100,105,96,102),95,110,5,1,10,false));}
    @Test void targetFillAndSellSlippage(){var t=run(config(),fixture()).trades().getFirst();assertEquals("TAKE_PROFIT",t.exitReason());assertEquals(110,t.exitBasePrice());assertEquals(110*.9997,t.exitExecutionPrice(),1e-10);assertEquals(2,t.holdingDays());}
    @Test void entryBarAmbiguityIsExplicitScenario(){var bars=fixture();bars.set(8,bar("2024-05-16",99,112,94,101));var r=run(config(),bars);var t=r.trades().getFirst();assertTrue(t.intrabarAmbiguous());assertEquals("AMBIGUOUS_ENTRY_ADVERSE",t.exitReason());assertTrue(signal(r).intrabarAmbiguous);var alt=run(config("ambiguousBarPolicy","SENSITIVITY_FAVORABLE"),bars);assertEquals("AMBIGUOUS_ENTRY_FAVORABLE",alt.trades().getFirst().exitReason());}
    @Test void entryAtOpenAppliesOrdinaryStopFirst(){var bars=fixture();bars.set(8,bar("2024-05-16",100.5,112,94,101));var t=run(config(),bars).trades().getFirst();assertFalse(t.intrabarAmbiguous());assertEquals("STOP_LOSS",t.exitReason());}
    @Test void timeoutCountsEntryAsOne(){var t=run(config("maxHoldingDays",1),fixture()).trades().getFirst();assertEquals("TIMEOUT",t.exitReason());assertEquals(t.entryDate(),t.exitDate());assertEquals(1,t.holdingDays());}
    @Test void riskOrderValueCashAndExposureLimits(){var r=run(config(),fixture());var t=r.trades().getFirst();assertTrue(t.initialRisk()<=200);assertTrue(t.quantity()*t.entryExecutionPrice()<=3000);assertTrue(t.quantity()>0);assertTrue(t.cashAfter()>=0);assertTrue(r.equity().stream().allMatch(e->e.cash()>=0&&e.exposurePercent()<=100));}
    @Test void exactAccountingAndNoDoubleSlippage(){var r=run(config(),fixture());var t=r.trades().getFirst();double pnl=(t.exitExecutionPrice()-t.entryExecutionPrice())*t.quantity()-t.entryCommission()-t.exitCommissions();assertEquals(pnl,t.netPnL(),1e-8);assertEquals(10000+pnl,r.equity().getLast().equity(),1e-8);assertEquals(2,t.entryCommission()+t.exitCommissions(),1e-8);assertEquals((100.5+110)*.0003*t.quantity(),t.slippageCosts(),1e-8);}
    @Test void percentAndTickBuffers(){var c=config();assertEquals(.05,c.buffer(196,WeeklyTteConfiguration.BufferMode.TICKS,5),1e-10);assertEquals(1.96,c.buffer(196,WeeklyTteConfiguration.BufferMode.PERCENT,1),1e-10);}
    @ParameterizedTest @ValueSource(strings={"initialCapital","riskPercent","tickSize","maxOrderValuePercent","maxPositionValuePercent","maxLongNotionalPercent"})
    void rejectsZeroPositiveParameters(String field){assertThrows(IllegalArgumentException.class,()->config(field,0).validate());}
    @ParameterizedTest @ValueSource(strings={"entryBuffer","stopBuffer","slippagePercentPerSide","minRewardRiskRatio"})
    void rejectsNegativeParameters(String field){assertThrows(IllegalArgumentException.class,()->config(field,-1).validate());}
    @Test void splitAwareFeesUseSharedCatalog(){var split=PatternV2SplitCosts.defaults();var b=new HistoricalBar("NVDA",LocalDate.parse("2020-01-02").atStartOfDay(),java.math.BigDecimal.TEN,java.math.BigDecimal.TEN,java.math.BigDecimal.TEN,java.math.BigDecimal.TEN,1,BarInterval.DAILY,"STOOQ",PriceAdjustment.ADJUSTED);assertEquals(40,split.factor("NVDA",b).intValue());assertEquals(new java.math.BigDecimal("0.000125"),PatternV2SplitCosts.commission(config().commission(),split.factor("NVDA",b)).perShare());}
    @Test void configurationHashStableAndSensitive(){assertEquals(WeeklyTteStore.hash(config()),WeeklyTteStore.hash(config()));assertNotEquals(WeeklyTteStore.hash(config()),WeeklyTteStore.hash(config("entryBuffer",2)));}
    @Test void independentRunHistoryAndReplaySnapshot(){var store=new WeeklyTteStore(temp);var service=new WeeklyTteService(store,new PositionSizingService(new TradePricingService()));var snapshot=PatternV2Dataset.capture(Map.of("TEST",fixture()));var r=service.onSnapshot(config(),snapshot);assertEquals(r.runId(),store.runs().getFirst().runId());assertEquals(r.configurationHash(),store.run(r.runId()).configurationHash());assertEquals(snapshot.id(),store.dataset(r.datasetId()).id());assertEquals(r.trades().getFirst().netPnL(),service.replay(r.runId()).trades().getFirst().netPnL());assertEquals(2,store.runs().size());}
    @Test void exportRequiredSheetsHeadersAndHash() throws Exception {var r=run(config(),fixture());try(var wb=new XSSFWorkbook(new ByteArrayInputStream(new WeeklyTteExcelExporter().export(r)))){for(String s:List.of("WEEKLY_LEVEL_STATISTICS","TTE_SETUP_AUDIT","TRADES","TTE_PERFORMANCE_ANALYSIS","CONFIGURATION","RUN_HISTORY"))assertNotNull(wb.getSheet(s));assertEquals(r.setups().size()+1,wb.getSheet("TTE_SETUP_AUDIT").getPhysicalNumberOfRows());assertTrue(wb.getSheet("CONFIGURATION").iterator().hasNext());assertTrue(wb.getSheet("CONFIGURATION").getLastRowNum()>20);}}
    @Test void partialExecutionsReconcileWithoutChangingMainMode(){var bars=fixture();bars.add(bar("2024-05-20",116,120,114,119));var main=run(config(),bars);var partial=run(config("mode","WEEKLY_HIGH_TTE_PARTIAL"),bars);var t=partial.trades().getFirst();assertEquals(2,t.exits().size());assertEquals(t.quantity(),t.exits().stream().mapToLong(Fill::quantity).sum());assertEquals(10000+t.netPnL(),partial.equity().getLast().equity(),1e-8);assertEquals(1,main.trades().getFirst().exits().size());}
    @Test void comparisonRequiresIdenticalDatasetAndCapital(){var store=new WeeklyTteStore(temp);var service=new WeeklyTteService(store,new PositionSizingService(new TradePricingService()));var snapshot=PatternV2Dataset.capture(Map.of("TEST",fixture()));var a=service.onSnapshot(config(),snapshot);var b=service.onSnapshot(config("mode","WEEKLY_HIGH_BREAKOUT"),snapshot);assertNotNull(service.compare(List.of(a.runId(),b.runId())));var other=service.onSnapshot(config("initialCapital",20000),snapshot);assertThrows(IllegalArgumentException.class,()->service.compare(List.of(a.runId(),other.runId())));}
    @Test void breakoutIsIndependentWeeklyBenchmark(){var r=run(config("mode","WEEKLY_HIGH_BREAKOUT","entryValidityBars",5),fixture());var a=r.setups().getFirst();assertEquals(LocalDate.parse("2024-05-10"),a.signalDate);assertEquals(110.01,a.entryTrigger,1e-8);assertEquals(88-.01,a.initialStop,1e-8);assertEquals(2,a.theoreticalRewardRisk);}
    @Test void noOrdersOrPaperDependencies() throws Exception {try(var files=Files.list(Path.of("src/main/java/it/andreademartini/trading/backtest/weeklytte"))){for(var p:files.toList()){String s=Files.readString(p);assertFalse(s.contains("import it.andreademartini.trading.ibkr"));assertFalse(s.contains("import it.andreademartini.trading.orders"));assertFalse(s.contains("import it.andreademartini.trading.paper"));assertFalse(s.contains("placeOrder("));}}}
    @Test void intradaySaleCannotFinanceEarlierOpeningEntry(){
        var a=fixture();var b=fixture();b.set(7,bar("2024-05-15",96,104,95.5,99));b.set(8,bar("2024-05-16",102,103,101,102));b.set(9,bar("2024-05-17",104.5,107,103,106));
        var c=config("symbols",List.of("A","B"),"initialCapital",1000,"riskPercent",100,"maxOrderValuePercent",100,"minRewardRiskRatio",0);
        var snapshot=PatternV2Dataset.capture(Map.of("A",a,"B",b));var r=engine().simulate(c,snapshot.bars(),snapshot.id(),WeeklyTteStore.hash(c));
        var bs=r.setups().stream().filter(x->x.symbol.equals("B")&&x.signalDate.equals(LocalDate.parse("2024-05-15"))).findFirst().orElseThrow();
        assertEquals("INSUFFICIENT_CAPITAL_OR_RISK_BUDGET",bs.rejectionReason);assertEquals(1,r.trades().size());
    }
    @Test void partialTimeoutClosesResidualAtLastAllowedClose(){var t=run(config("mode","WEEKLY_HIGH_TTE_PARTIAL","maxHoldingDays",2),fixture()).trades().getFirst();assertEquals(0,t.remainingQuantity());assertEquals(2,t.exits().size());assertEquals("TIMEOUT",t.exitReason());assertEquals(2,t.holdingDays());}
    @Test void fiveProbabilityEventsAreDifferentAndShadowCanContinueAfterStop(){
        var bars=fixture();bars.set(8,bar("2024-05-16",100.5,102,94,101));var r=run(config(),bars);
        var events=(Map<?,?>)r.analysis().get("fiveDistinctEvents");assertTrue(events.containsKey("1_weeklyLevelTouched"));
        var observations=(List<?>)r.analysis().get("entryOutcomeObservations");var observation=(Map<?,?>)observations.getFirst();
        assertEquals(true,observation.get("targetTouchedAfterEntryIgnoringStop"));assertEquals(false,observation.get("targetBeforeStopScenario"));
    }
    @Test void fractionalVolumeAdapterPreservesOhlcAndSource()throws Exception{
        var csv=temp.resolve("TEST.US.csv");String original="Date,Open,High,Low,Close,Volume\n2024-05-13,100,102,98,101,1234.75\n";Files.writeString(csv,original);
        var data=WeeklyTteCsvData.load(csv,"TEST");assertEquals(1,data.fractionalVolumes());assertEquals(1234,data.bars().getFirst().volume());assertEquals(102,data.bars().getFirst().high());assertEquals(original,Files.readString(csv));assertEquals(64,data.sourceSha256().length());
    }
    @Test void firstWeek2010HasExplicitUnavailableReference(){var bars=List.of(bar("2010-01-04",90,100,88,99));var level=WeeklyTteLevels.calculate("TEST",bars,LocalDate.parse("2010-01-01"),LocalDate.parse("2010-01-08")).getFirst();assertNull(level.previousWeekHigh());assertEquals("INCOMPLETE_REFERENCE_WEEK",level.dataQualityStatus());}
    @Test void breakoutWeekChangePreservesExpiredAudit(){
        var bars=fixture();bars.set(9,bar("2024-05-17",98,102,96,99));bars.add(bar("2024-05-20",99,102,98,101));
        var r=run(config("mode","WEEKLY_HIGH_BREAKOUT","entryValidityBars",10),bars);
        assertEquals("EXPIRED_WEEK_CHANGE",r.setups().getFirst().setupStatus);
        assertEquals(2,r.setups().size());assertNotEquals(r.setups().getFirst().setupId,r.setups().getLast().setupId);
    }
    @Test void expiryIsKnownAtCloseOfLastValidSession(){
        var bars=fixture();bars.set(8,bar("2024-05-16",99,99.5,97,98));
        var r=run(config("entryValidityBars",1,"endDate","2024-05-16"),bars);
        assertEquals("EXPIRED",signal(r).setupStatus);
    }
    @Test void entryBarSaleIsNotAvailableToOtherSameDayEntries(){
        var a=fixture();a.set(8,bar("2024-05-16",99,102,94,101));
        var c=config("symbols",List.of("A","B"),"initialCapital",1000,"riskPercent",100,"maxOrderValuePercent",60,"minRewardRiskRatio",0);
        var snapshot=PatternV2Dataset.capture(Map.of("A",a,"B",fixture()));
        var r=engine().simulate(c,snapshot.bars(),snapshot.id(),WeeklyTteStore.hash(c));
        var first=r.trades().stream().filter(t->t.symbol().equals("A")).findFirst().orElseThrow();
        var second=r.trades().stream().filter(t->t.symbol().equals("B")).findFirst().orElseThrow();
        assertEquals("AMBIGUOUS_ENTRY_ADVERSE",first.exitReason());
        assertEquals(1000-first.entryExecutionPrice()*first.quantity()-first.entryCommission(),second.cashBefore(),1e-8);
        assertTrue(r.equity().stream().allMatch(e->e.cash()>=0));
    }
    @Test void conditionedStopIndependentOutcomeIsSeparateFromTradeResult(){
        var bars=fixture();bars.set(8,bar("2024-05-16",100.5,102,94,101));var r=run(config(),bars);
        var conditional=(Map<?,?>)r.analysis().get("conditionalAnalysis");
        var row=(Map<?,?>)((List<?>)conditional.get("symbol")).getFirst();
        var shadow=(Map<?,?>)row.get("targetAfterEntryIgnoringStopProbability");
        var trading=(Map<?,?>)row.get("scenarioTargetBeforeStopProbability");
        assertEquals(1L,shadow.get("successes"));assertEquals(0L,trading.get("successes"));
        assertEquals(1L,shadow.get("sample"));
    }
    @Test void repeatedConfigurationReplacementKeepsValidJson(){
        var store=new WeeklyTteStore(temp);
        for(int i=1;i<=10;i++){var c=config("entryBuffer",i);store.configuration(c);assertEquals(c,store.current());}
        assertEquals(10,store.configurations().size());
    }
    @Test void partialTargetTimingMeasuresFirstFillAndSymbolGroupsIncludeGaps(){
        var bars=fixture();bars.add(bar("2024-05-20",118,120,117,119));
        var r=run(config("mode","WEEKLY_HIGH_TTE_PARTIAL"),bars);
        assertEquals(2.0,((Map<?,?>)r.analysis().get("setupAnalysis")).get("meanSessionsToTarget"));
        var symbol=(Map<?,?>)((List<?>)r.analysis().get("symbols")).getFirst();
        assertTrue(symbol.containsKey("gapStops"));assertEquals(1L,symbol.get("gapTargets"));
    }
}
