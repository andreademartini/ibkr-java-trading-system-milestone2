package it.andreademartini.trading.backtest.patternv2;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import static it.andreademartini.trading.backtest.patternv2.GapAuditTestSupport.*;
import it.andreademartini.trading.backtest.execution.*;
import it.andreademartini.trading.backtest.patternstable.PatternStableStore;
import it.andreademartini.trading.backtest.patternv3.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.risk.*;
import java.math.BigDecimal;
import java.util.*;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SharedGapStrategiesTest {
    @TempDir Path temp;
    PatternV3Service service(PatternV3Store store){return new PatternV3Service(configuration(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),store);}
    PatternV3Configuration researchFixture(Fixture f)throws Exception{
        // Identical synthetic test settings only; production V3 current/default are never changed.
        var json=(com.fasterxml.jackson.databind.node.ObjectNode)PatternStableStore.JSON.valueToTree(f.config());json.remove("exitFillPolicy");
        return PatternStableStore.JSON.treeToValue(json,PatternV3Configuration.class);
    }
    @ParameterizedTest @CsvSource({
        "true,95,110,90", "true,109,112,90", "false,105,110,90", "false,91,110,88",
        "true,98,110,90", "true,106,110,90", "false,102,110,90", "false,94,110,90",
        "true,100,101,97", "false,100,103,99", "true,100,107,99", "false,100,101,93",
        "true,100,107,97", "false,100,103,93", "true,85,110,80", "false,115,120,90",
        "true,115,120,90", "false,85,110,80", "false,500,500,90"
    })
    void threeStrategiesShareIdenticalExitAuditAndAccounting(boolean isLong,double open,double high,double low)throws Exception{
        var f=fixture(isLong,100,open,high,low);var config=researchFixture(f);var before=PatternStableStore.hash(config);
        var store=new PatternV3Store(temp);var r=service(store).simulate(config,f.bars());var v3=ExecutionRunView.of(r);
        assertEquals(ExecutionModel.OPEN_FIRST,r.executionModelVersion());assertEquals(before,r.configurationHash());
        for(boolean v1:List.of(false,true)){
            var reference=audit(f,v1);var expected=ExecutionRunView.of(reference);var a=expected.executions().getFirst();var b=v3.executions().getFirst();
            assertEquals(a.trade().exitReason(),b.trade().exitReason());assertEquals(a.trade().exitPrice(),b.trade().exitPrice());assertEquals(a.trade().quantity(),b.trade().quantity());
            assertEquals(a.trade().netPnl(),b.trade().netPnl());assertEquals(a.trade().resultR(),b.trade().resultR());assertEquals(a.trade().sameBarAmbiguous(),b.trade().sameBarAmbiguous());
            assertEquals(a.entryPriceAfterSlippage(),b.entryPriceAfterSlippage());assertEquals(a.exitPriceAfterSlippage(),b.exitPriceAfterSlippage());assertEquals(a.commission(),b.commission());assertEquals(a.slippageCost(),b.slippageCost());
            var gap=reference.executionAudit().trades().getFirst();var actual=r.executionAudit().trades().getFirst();
            assertEquals(gap.gapExitType(),actual.gapExitType());assertEquals(gap.gapSizePercent(),actual.gapSizePercent());assertEquals(gap.gapSizeR(),actual.gapSizeR());assertNull(actual.issue());
            assertEquals(GapExitAnalysis.groups(expected),GapExitAnalysis.groups(v3));assertEquals(expected.portfolio().finalCapital(),v3.portfolio().finalCapital());
        }
        store.save(r);assertEquals(r.executionAudit(),store.run(r.runId()).executionAudit());assertEquals(r.executionModelVersion(),store.run(r.runId()).executionModelVersion());
        assertEquals(r.executionAudit(),r.withResearch(r.patternRangeAnalysis(),"dataset",null).withBenchmark(null).withCapitalSkipShadowAnalysis(null).executionAudit());
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(new PatternV3ExcelExporter().export(r)))){
            assertTrue(GapAuditExcelTest.columns(book.getSheet("TRADES")).keySet().containsAll(GapAuditExcel.HEADERS));assertNotNull(book.getSheet("GAP_EXIT_ANALYSIS"));
            var columns=GapAuditExcelTest.columns(book.getSheet("TRADES"));assertEquals(r.executionAudit().trades().getFirst().gapExitType().name(),book.getSheet("TRADES").getRow(1).getCell(columns.get("GapExitType")).toString());
            GapAuditExcelTest.finite(book);
        }
    }
    @ParameterizedTest @CsvSource({"true,97", "true,98", "false,102", "false,103"})
    void researchRejectsInvalidEntryBeforeAnyExit(boolean isLong,double open)throws Exception{
        var f=fixture(isLong,open,open,open+.1,open-.1);var r=service(new PatternV3Store(temp)).simulate(researchFixture(f),f.bars());
        assertTrue(r.executions().isEmpty());assertEquals(0,bd("10000").compareTo(r.portfolio().finalCapital()));
        assertTrue(r.portfolio().events().stream().anyMatch(e->e.eventType().equals("SIGNAL_SKIPPED_INVALID_RISK")));
    }
    @Test void legacyReplayKeepsModelAndNewRunUsesOpenFirstWithoutMixingBaselines()throws Exception{
        var f=fixture(true,100,95,110,90);var config=researchFixture(f);var store=new PatternV3Store(temp);var svc=service(store);
        var snapshot=PatternV3Dataset.capture(f.bars());store.saveDataset(snapshot);
        var old=svc.simulate(config,f.bars(),ExitFillPolicy.LEVEL_ONLY).withResearch(null,snapshot.id(),null).withExecutionMetadata(null,null);
        store.save(old);var bytes=java.nio.file.Files.readAllBytes(temp.resolve("runs/"+old.runId()+".json"));
        var replay=svc.replay(old.runId());assertEquals(ExecutionModel.LEVEL_ONLY,replay.executionModelVersion());assertEquals(old.portfolio().finalCapital(),replay.portfolio().finalCapital());
        assertNotEquals(old.runId(),replay.runId());assertArrayEquals(bytes,java.nio.file.Files.readAllBytes(temp.resolve("runs/"+old.runId()+".json")));
        var current=svc.runOnSnapshot(config,f.bars());assertEquals(ExecutionModel.OPEN_FIRST,current.executionModelVersion());assertNotEquals(old.portfolio().finalCapital(),current.portfolio().finalCapital());
        assertThrows(IllegalArgumentException.class,()->PatternV3RunComparison.compare(List.of(replay,current)));
        assertThrows(IllegalArgumentException.class,()->PatternV3CapitalComparison.compare(List.of(replay,current)));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(new PatternV3ExcelExporter().export(store.run(old.runId()))))){
            var s=book.getSheet("TRADES");var c=GapAuditExcelTest.columns(s);assertEquals(org.apache.poi.ss.usermodel.CellType.BLANK,s.getRow(1).getCell(c.get("GapExitType")).getCellType());
        }
    }
    @Test void researchEvaluatesEntryIntradayAndTimeoutWithoutOpeningGap()throws Exception{
        for(boolean isLong:List.of(true,false)){
            var f=fixture(isLong,100,100,101,99);String symbol=isLong?"AAPL":"TSLA";int entry=isLong?2:1;
            var bars=new ArrayList<>(f.bars().get(symbol));bars.set(entry,bar(symbol,entry,100,110,90,100));
            var r=service(new PatternV3Store(temp)).simulate(researchFixture(f),Map.of(symbol,bars));var t=r.executions().getFirst().trade();
            assertEquals(t.entryIndex(),t.exitIndex());assertTrue(t.sameBarAmbiguous());assertEquals(GapExecutionAudit.GapType.NONE,r.executionAudit().trades().getFirst().gapExitType());
            bars=new ArrayList<>(f.bars().get(symbol).subList(0,entry));for(int i=entry;i<entry+13;i++)bars.add(bar(symbol,i,100,101,99,100));
            r=service(new PatternV3Store(temp)).simulate(researchFixture(f),Map.of(symbol,bars));t=r.executions().getFirst().trade();
            assertEquals("TIMEOUT",t.exitReason());assertEquals(13,t.holdingBars());assertEquals(GapExecutionAudit.GapType.NONE,r.executionAudit().trades().getFirst().gapExitType());
            assertEquals(0,bd("-0.06").multiply(BigDecimal.valueOf(t.quantity())).subtract(r.costs().commissions()).compareTo(t.netPnl()));
        }
    }
}
