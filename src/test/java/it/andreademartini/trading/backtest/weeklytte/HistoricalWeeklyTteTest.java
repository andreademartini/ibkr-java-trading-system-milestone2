package it.andreademartini.trading.backtest.weeklytte;

import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.backtest.patternv2.PatternV2Dataset;
import it.andreademartini.trading.backtest.patternv3.ResearchSessionCalendar;
import it.andreademartini.trading.risk.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.weeklytte.WeeklyTteRun.*;
import static it.andreademartini.trading.backtest.weeklytte.WeeklyTteTest.bar;

class HistoricalWeeklyTteTest {
    @TempDir Path temp;
    static WeeklyTteConfiguration config(Object... changes){
        var n=(com.fasterxml.jackson.databind.node.ObjectNode)WeeklyTteStore.JSON.valueToTree(WeeklyTteTest.config());
        n.put("rulesVersion",WeeklyTteConfiguration.HISTORICAL);n.put("allowLong",true);n.put("allowShort",true);
        for(int i=0;i<changes.length;i+=2)n.set(changes[i].toString(),WeeklyTteStore.JSON.valueToTree(changes[i+1]));
        return WeeklyTteStore.JSON.convertValue(n,WeeklyTteConfiguration.class);
    }
    static WeeklyTteRun run(WeeklyTteConfiguration c,List<HistoricalBar> bars){return WeeklyTteTest.run(c,bars);}
    static List<HistoricalLevel> levels(WeeklyTteRun r){return ((List<?>)r.analysis().get("historicalLevels")).stream().map(l->WeeklyTteStore.JSON.convertValue(l,HistoricalLevel.class)).toList();}
    static List<HistoricalBar> mirror(List<HistoricalBar> bars){return bars.stream().map(b->bar(b.dateTime().toLocalDate().toString(),200-b.open(),200-b.low(),200-b.high(),200-b.close())).toList();}
    static HistoricalLevel origin(WeeklyTteRun r,String type,String week){return levels(r).stream().filter(l->l.levelType().equals(type)&&l.originWeeks().contains(LocalDate.parse(week))).findFirst().orElseThrow();}
    static List<HistoricalBar> weeks(){
        var bars=new ArrayList<HistoricalBar>();for(var d=LocalDate.parse("2024-01-08");!d.isAfter(LocalDate.parse("2024-05-17"));d=d.plusDays(1))if(ResearchSessionCalendar.isSession(d)){
            boolean first=d.isBefore(LocalDate.parse("2024-01-15"));bars.add(bar(d.toString(),100,first?120:110,first?80:90,100));
        }return bars;
    }
    @Test void historicalLevelsSurviveMonthsAndDuplicatePricesKeepOrigins(){
        var r=run(config("startDate","2024-01-08"),weeks());var high=origin(r,"HIGH","2024-01-08");var low=origin(r,"LOW","2024-01-08");
        assertEquals("ACTIVE",high.status());assertEquals("ACTIVE",low.status());assertEquals(LocalDate.parse("2024-01-16"),high.availableFrom());
        assertTrue(levels(r).stream().filter(l->l.levelPrice()==110).flatMap(l->l.originWeeks().stream()).distinct().count()>1);
        assertTrue(levels(r).stream().noneMatch(l->l.firstTouchDate()!=null&&!l.firstTouchDate().isAfter(l.originWeeks().getFirst().plusDays(4))));
    }
    @Test void maximumAndMinimumAreIndependentAndEqualityConsumes(){
        var bars=weeks();bars.set(bars.size()-1,bar("2024-05-17",100,120,95,101));var r=run(config(),bars);
        assertEquals("REACHED",origin(r,"HIGH","2024-01-08").status());assertEquals(LocalDate.parse("2024-05-17"),origin(r,"HIGH","2024-01-08").firstTouchDate());
        assertEquals("ACTIVE",origin(r,"LOW","2024-01-08").status());
    }
    @Test void incompleteOriginWeekDoesNotGenerateLevels(){
        var bars=WeeklyTteTest.fixture();bars.remove(2);var r=run(config(),bars);assertTrue(levels(r).stream().noneMatch(l->l.originWeeks().contains(LocalDate.parse("2024-05-06")))) ;
        assertTrue(r.trades().isEmpty());
    }
    @Test void finalIncompleteWeekAndOriginWeekCannotBecomeSignals(){
        var r=run(config("startDate","2024-05-06","endDate","2024-05-16"),WeeklyTteTest.fixture());
        assertTrue(levels(r).stream().noneMatch(l->l.originWeeks().contains(LocalDate.parse("2024-05-13"))));
        assertTrue(levels(r).stream().allMatch(l->l.firstTouchDate()==null||!l.firstTouchDate().isBefore(l.availableFrom())));
    }
    @Test void olderUntouchedLevelWorksWhenImmediateReferenceWeekIsIncomplete(){
        var bars=weeks();bars.removeIf(b->b.dateTime().toLocalDate().equals(LocalDate.parse("2024-05-08")));
        var r=run(config(),bars);assertEquals("UNKNOWN_DATA_GAP",origin(r,"HIGH","2024-01-08").status());assertNull(origin(r,"HIGH","2024-01-08").firstTouchDate());
        assertFalse(((List<?>)r.analysis().get("levelDataGaps")).isEmpty());
    }
    @Test void longAndShortTteUseSymmetricStopsTargetsAndSlippage(){
        var longRun=run(config("allowShort",false),WeeklyTteTest.fixture());var shortRun=run(config("allowLong",false),mirror(WeeklyTteTest.fixture()));
        var l=longRun.trades().getFirst();var s=shortRun.trades().getFirst();assertEquals("LONG",l.direction());assertEquals("SHORT",s.direction());
        assertEquals(110,l.levelPrice());assertEquals(90,s.levelPrice());assertEquals("LOW",s.levelType());
        assertEquals(200-l.initialStop(),s.initialStop(),1e-9);assertEquals(200-l.entryBasePrice(),s.entryBasePrice(),1e-9);
        assertTrue(s.entryExecutionPrice()<s.entryBasePrice());assertTrue(s.exitExecutionPrice()>s.exitBasePrice());
        double pnl=(s.entryExecutionPrice()-s.exitExecutionPrice())*s.quantity()-s.entryCommission()-s.exitCommissions();
        assertEquals(pnl,s.netPnL(),1e-8);assertEquals(10000+pnl,shortRun.equity().getLast().equity(),1e-8);
        assertTrue(shortRun.equity().stream().anyMatch(e->e.shortNotional()>0&&e.reservedCollateral()>0));
    }
    @Test void directionsCanBeDisabledIndependently(){
        assertTrue(run(config("allowLong",false),WeeklyTteTest.fixture()).trades().isEmpty());
        assertTrue(run(config("allowShort",false),mirror(WeeklyTteTest.fixture())).trades().isEmpty());
        assertThrows(IllegalArgumentException.class,()->config("allowLong",false,"allowShort",false).validate());
    }
    @Test void firstBreakoutCrossingExecutesBeforeLevelIsConsumedInBothDirections(){
        for(boolean shortSide:List.of(false,true)){
            var r=run(config("mode","WEEKLY_HIGH_BREAKOUT","entryValidityBars",10,"allowLong",!shortSide,"allowShort",shortSide,"momentumFilter",false),shortSide?mirror(WeeklyTteTest.fixture()):WeeklyTteTest.fixture());
            var t=r.trades().getFirst();assertEquals(LocalDate.parse("2024-05-17"),t.entryDate());assertEquals(shortSide?112.01:87.99,t.initialStop(),1e-8);
            assertEquals("REACHED",origin(r,shortSide?"LOW":"HIGH","2024-05-06").status());
        }
    }
    @Test void bufferedBreakoutCannotEnterAfterUntriggeredFirstTouch(){
        var bars=WeeklyTteTest.fixture();bars.set(9,bar("2024-05-17",105,110,103,109));bars.add(bar("2024-05-20",109,115,108,114));
        var r=run(config("mode","WEEKLY_HIGH_BREAKOUT","allowShort",false,"entryValidityBars",10),bars);
        assertTrue(r.setups().stream().anyMatch(a->a.setupStatus.equals("LEVEL_REACHED_BEFORE_TRIGGER")));
        assertTrue(r.trades().stream().noneMatch(t->t.originWeeks().contains(LocalDate.parse("2024-05-06"))));
    }
    @Test void nearestLevelChosenAndDuplicatePricesDoNotCreateDuplicateOrders(){
        var bars=weeks();
        bars.set(bars.size()-6,bar("2024-05-10",100,101,90,100));
        // January's 120 remains, but 110 from later weeks is closer at the decision time.
        bars.set(bars.size()-3,bar("2024-05-15",96,100,95,99));bars.set(bars.size()-4,bar("2024-05-14",99,100,95,96));bars.set(bars.size()-5,bar("2024-05-13",99,102,98,101));
        var r=run(config("allowShort",false,"momentumFilter",false),bars);
        var a=r.setups().stream().filter(x->x.signalDate.equals(LocalDate.parse("2024-05-15"))).findFirst().orElseThrow();
        assertEquals(110,a.levelPrice);assertEquals(r.setups().size(),r.setups().stream().map(x->x.setupId).distinct().count());
        assertEquals(1,levels(r).stream().filter(l->l.levelPrice()==110&&l.status().equals("ACTIVE")).count()+levels(r).stream().filter(l->l.levelPrice()==110&&LocalDate.parse("2024-05-16").equals(l.firstTouchDate())).count());
    }
    @Test void tteOpeningTargetTouchCancelsBothDirections(){
        var bars=WeeklyTteTest.fixture();bars.set(8,bar("2024-05-16",111,112,108,110));
        for(boolean shortSide:List.of(false,true)){
            var r=run(config("allowLong",!shortSide,"allowShort",shortSide),shortSide?mirror(bars):bars);
            assertTrue(r.setups().stream().anyMatch(a->a.setupStatus.equals("TARGET_REACHED_BEFORE_ENTRY")));assertTrue(r.trades().isEmpty());
        }
    }
    @Test void opposingIntrabarBreakoutsAreSkippedConservatively(){
        var bars=WeeklyTteTest.fixture();bars.set(5,bar("2024-05-13",100,112,86,100));
        var r=run(config("mode","WEEKLY_HIGH_BREAKOUT"),bars);
        assertEquals(2,r.setups().stream().filter(a->"AMBIGUOUS_OPPOSING_INTRABAR_TRIGGERS".equals(a.rejectionReason)).count());
        assertTrue(r.setups().stream().filter(a->"AMBIGUOUS_OPPOSING_INTRABAR_TRIGGERS".equals(a.rejectionReason)).allMatch(a->a.intrabarAmbiguous));assertTrue(r.trades().isEmpty());
    }
    @Test void openingShortTriggerPrecedesOpposingIntradayLongTrigger(){
        var bars=WeeklyTteTest.fixture();bars.set(5,bar("2024-05-13",87,112,86,100));
        var r=run(config("mode","WEEKLY_HIGH_BREAKOUT"),bars);assertEquals("SHORT",r.trades().getFirst().direction());
        assertEquals("STOP_LOSS",r.trades().getFirst().exitReason());assertEquals("NONE",r.trades().getFirst().gapExitType());
    }
    @Test void openingTouchBeforeBufferedTriggerCancelsBreakout(){
        var bars=WeeklyTteTest.fixture();bars.set(5,bar("2024-05-13",110,112,109,111));
        var r=run(config("mode","WEEKLY_HIGH_BREAKOUT","allowShort",false),bars);assertTrue(r.trades().isEmpty());
        assertTrue(r.setups().stream().anyMatch(a->"OPEN_TOUCH_BEFORE_BUFFERED_TRIGGER".equals(a.rejectionReason)));
    }
    @Test void originCloseAtItsOwnHighDoesNotPreventNextOpeningBreakout(){
        var bars=WeeklyTteTest.fixture();bars.set(4,bar("2024-05-10",105,110,103,110));bars.set(5,bar("2024-05-13",111,112,110.5,111));
        var r=run(config("mode","WEEKLY_HIGH_BREAKOUT","allowShort",false),bars);
        assertEquals(LocalDate.parse("2024-05-13"),r.trades().getFirst().entryDate());assertEquals(111,r.trades().getFirst().entryBasePrice());
    }
    @Test void reachingReferenceDoesNotDeleteOpenTradeOrPartialResidual(){
        var bars=WeeklyTteTest.fixture();bars.add(bar("2024-05-20",116,120,114,119));
        var r=run(config("mode","WEEKLY_HIGH_TTE_PARTIAL","allowLong",false),mirror(bars));var t=r.trades().getFirst();
        assertEquals(2,t.exits().size());assertEquals(0,t.remainingQuantity());assertEquals(t.quantity(),t.exits().stream().mapToLong(Fill::quantity).sum());
        assertEquals(10000+t.netPnL(),r.equity().getLast().equity(),1e-8);assertEquals("REACHED",origin(r,"LOW","2024-05-06").status());
    }
    @Test void pendingSurvivesWeekChangeWhileLevelIsUntouched(){
        var bars=WeeklyTteTest.fixture();bars.set(8,bar("2024-05-16",99,99.5,97,98));bars.set(9,bar("2024-05-17",98,99,96,97));bars.add(bar("2024-05-20",100.5,102,99,101));
        var r=run(config("allowShort",false,"entryValidityBars",4),bars);
        assertEquals(LocalDate.parse("2024-05-20"),r.trades().getFirst().entryDate());assertTrue(r.setups().stream().noneMatch(a->a.setupStatus.equals("EXPIRED_WEEK_CHANGE")));
    }
    @Test void shortIntrabarAmbiguityAndGapStopAreExplicit(){
        var bars=WeeklyTteTest.fixture();bars.set(8,bar("2024-05-16",99,112,94,101));
        var r=run(config("allowLong",false),mirror(bars));assertEquals("AMBIGUOUS_ENTRY_ADVERSE",r.trades().getFirst().exitReason());assertTrue(r.trades().getFirst().intrabarAmbiguous());
        assertEquals("AMBIGUOUS_ENTRY_FAVORABLE",run(config("allowLong",false,"ambiguousBarPolicy","SENSITIVITY_FAVORABLE"),mirror(bars)).trades().getFirst().exitReason());
        bars=WeeklyTteTest.fixture();bars.set(9,bar("2024-05-17",90,94,88,92));var t=run(config("allowLong",false),mirror(bars)).trades().getFirst();
        assertEquals("GAP_STOP",t.gapExitType());assertTrue(t.netPnL()<0);assertTrue(t.gapSizeR()<0);
    }
    @Test void multipleTouchedLevelsAreAuditedWithoutDuplicateTrades(){
        var bars=weeks();for(int i=bars.size()-5;i<bars.size()-1;i++)bars.set(i,bar(bars.get(i).dateTime().toLocalDate().toString(),100,108,95,100));bars.set(bars.size()-1,bar("2024-05-17",105,121,103,110));
        var r=run(config("mode","WEEKLY_HIGH_BREAKOUT","allowShort",false,"entryValidityBars",10),bars);
        assertTrue(r.setups().stream().anyMatch(a->a.multipleLevelsTouched&&a.touchedLevelIds.size()>1));
        assertEquals(1,r.trades().stream().filter(t->t.entryDate().equals(LocalDate.parse("2024-05-17"))).count());
    }
    @Test void shortProceedsCannotFinanceAnotherOpeningOrder(){
        var c=config("symbols",List.of("A","B"),"allowLong",false,"initialCapital",1000,"maxOrderValuePercent",100,"riskPercent",100,"minRewardRiskRatio",0);
        var snapshot=PatternV2Dataset.capture(Map.of("A",mirror(WeeklyTteTest.fixture()),"B",mirror(WeeklyTteTest.fixture())));
        var r=WeeklyTteTest.engine().simulate(c,snapshot.bars(),snapshot.id(),WeeklyTteStore.hash(c));
        assertEquals(1,r.trades().size());assertTrue(r.setups().stream().anyMatch(a->"INSUFFICIENT_CAPITAL_OR_RISK_BUDGET".equals(a.rejectionReason)));
        assertTrue(r.equity().stream().allMatch(e->e.cash()>=0));
    }
    @Test void signalsAndEquityPrefixDoNotDependOnFutureBars(){
        var bars=WeeklyTteTest.fixture();var prefix=bars.subList(0,8);var shortRun=run(config("endDate","2024-05-15"),prefix);var full=run(config(),bars);
        var a=shortRun.setups().stream().filter(s->s.levelId!=null).findFirst().orElseThrow();var b=full.setups().stream().filter(s->s.setupId.equals(a.setupId)).findFirst().orElseThrow();
        assertEquals(a.entryTrigger,b.entryTrigger);assertEquals(a.initialStop,b.initialStop);assertEquals(a.originWeeks,b.originWeeks);
        assertEquals(shortRun.equity(),full.equity().subList(0,shortRun.equity().size()));
    }
    @Test void versionedSnapshotReplayAndExportsPreserveLevelsDirectionsAndResults()throws Exception{
        var store=new WeeklyTteStore(temp);var service=new WeeklyTteService(store,new PositionSizingService(new TradePricingService()));
        var snapshot=PatternV2Dataset.capture(Map.of("TEST",mirror(WeeklyTteTest.fixture())));var r=service.onSnapshot(config("allowLong",false),snapshot);var replay=service.replay(r.runId());
        assertEquals(r.configurationHash(),replay.configurationHash());assertEquals(r.equity(),replay.equity());assertEquals(levels(r),levels(replay));assertEquals(r.trades().getFirst().netPnL(),replay.trades().getFirst().netPnL());
        assertEquals("SHORT",store.run(r.runId()).trades().getFirst().direction());
        try(var wb=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(new WeeklyTteExcelExporter().export(r)))){assertNotNull(wb.getSheet("HISTORICAL_LEVELS"));assertNotNull(wb.getSheet("DIRECTION_PERFORMANCE"));}
        var legacy=service.onSnapshot(WeeklyTteTest.config(),PatternV2Dataset.capture(Map.of("TEST",WeeklyTteTest.fixture())));assertFalse(legacy.configuration().historical());
        assertEquals(legacy.equity(),service.replay(legacy.runId()).equity());assertThrows(IllegalArgumentException.class,()->service.compare(List.of(legacy.runId(),r.runId())));
    }
}
