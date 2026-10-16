package it.andreademartini.trading.backtest.patternv2;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import static it.andreademartini.trading.backtest.patternv2.GapAuditTestSupport.*;
import it.andreademartini.trading.backtest.patternstable.PatternStableStore;
import java.io.ByteArrayInputStream;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class GapAuditExcelTest {
    static Map<String,Integer> columns(Sheet sheet){Map<String,Integer> result=new HashMap<>();for(var cell:sheet.getRow(0))result.put(cell.getStringCellValue(),cell.getColumnIndex());return result;}
    static void finite(Workbook book){
        for(var s:book)for(var row:s)for(var cell:row){
            assertNotEquals(CellType.ERROR,cell.getCellType(),s.getSheetName()+" "+cell.getAddress());
            if(cell.getCellType()==CellType.NUMERIC)assertTrue(Double.isFinite(cell.getNumericCellValue()));
            if(cell.getCellType()==CellType.STRING)assertFalse(Set.of("NaN","Infinity","-Infinity").contains(cell.getStringCellValue()));
        }
    }
    @Test void exportedValuesAndAnalysisReconcileWithPersistedAudit() throws Exception {
        var f=fixture(true,100,95,110,90);var r=audit(f,false);var a=r.executionAudit().trades().getFirst();
        try(var b=new XSSFWorkbook(new ByteArrayInputStream(new PatternV2ExcelExporter().export(r)))){
            var s=b.getSheet("TRADES");var columns=columns(s);for(var header:GapAuditExcel.HEADERS)assertTrue(columns.containsKey(header));
            assertEquals(95,s.getRow(1).getCell(columns.get("ExitBarOpen")).getNumericCellValue());
            assertEquals("GAP_STOP",s.getRow(1).getCell(columns.get("GapExitType")).getStringCellValue());
            assertEquals(a.gapSizePercent().doubleValue(),s.getRow(1).getCell(columns.get("GapSizePercent")).getNumericCellValue());
            assertEquals(a.gapSizeR().doubleValue(),s.getRow(1).getCell(columns.get("GapSizeR")).getNumericCellValue());
            assertEquals(94.9715,s.getRow(1).getCell(columns.get("ExitExecutionPrice")).getNumericCellValue(),1e-12);
            assertEquals(0.0285,s.getRow(1).getCell(columns.get("ExitSlippageAmount")).getNumericCellValue(),1e-12);
            var analysis=b.getSheet("GAP_EXIT_ANALYSIS");var ac=columns(analysis);
            assertEquals(1,analysis.getRow(1).getCell(ac.get("totalClosedTrades")).getNumericCellValue());
            assertEquals(1,analysis.getRow(1).getCell(ac.get("gapStopCount")).getNumericCellValue());
            assertEquals(100,analysis.getRow(1).getCell(ac.get("gapExitRatePercent")).getNumericCellValue());
            assertEquals(r.portfolio().trades().getFirst().netPnl().doubleValue(),analysis.getRow(1).getCell(ac.get("netPnlGapStop")).getNumericCellValue());
            assertEquals(r.costs().commissions().doubleValue(),analysis.getRow(1).getCell(ac.get("gapCommissions")).getNumericCellValue());
            Set<String> sections=new HashSet<>();for(var row:analysis)sections.add(row.getCell(0).toString());assertTrue(sections.containsAll(List.of("GENERAL","SYMBOL","PERIOD","YEAR")));
            assertTrue(java.util.stream.StreamSupport.stream(b.getSheet("RUN_INFO").spliterator(),false).anyMatch(row->row.getCell(0).toString().equals("Execution Model Version")&&row.getCell(1).toString().equals("OPEN_FIRST/1")));
            finite(b);
        }
    }
    @Test void historicalMissingAuditStaysUnavailableEvenWhenPricesAndEventsExist() throws Exception {
        var f=fixture(true,100,95,110,90);var r=simulate(f,false);
        assertNull(r.executionAudit());
        var old=PatternStableStore.JSON.readValue(PatternStableStore.JSON.writeValueAsBytes(r),PatternV2Run.class);assertNull(old.executionAudit());
        try(var b=new XSSFWorkbook(new ByteArrayInputStream(new PatternV2ExcelExporter().export(old)))){
            var s=b.getSheet("TRADES");var cols=columns(s);
            for(var header:GapAuditExcel.HEADERS)assertEquals(CellType.BLANK,s.getRow(1).getCell(cols.get(header)).getCellType(),header);
            var analysis=b.getSheet("GAP_EXIT_ANALYSIS");var ac=columns(analysis);
            assertEquals(1,analysis.getRow(1).getCell(ac.get("unauditedClosedTrades")).getNumericCellValue());
            assertEquals(CellType.BLANK,analysis.getRow(1).getCell(ac.get("gapStopCount")).getCellType());
            finite(b);
        }
    }
    @Test void noneAndTimeoutKeepGapSizesBlankAndRealExitOpen() throws Exception {
        var f=fixture(true,100,100,107,99);var r=audit(f,false);
        try(var b=new XSSFWorkbook(new ByteArrayInputStream(new PatternV2ExcelExporter().export(r)))){
            var s=b.getSheet("TRADES");var c=columns(s);assertEquals("NONE",s.getRow(1).getCell(c.get("GapExitType")).toString());
            assertEquals(100,s.getRow(1).getCell(c.get("ExitBarOpen")).getNumericCellValue());
            assertEquals(CellType.BLANK,s.getRow(1).getCell(c.get("GapSizeR")).getCellType());assertEquals(CellType.BLANK,s.getRow(1).getCell(c.get("GapSizePercent")).getCellType());finite(b);
        }
        for(boolean isLong:List.of(false,true)){
            var template=fixture(isLong,100,100,101,99);String symbol=isLong?"AAPL":"TSLA";int entry=isLong?2:1;
            var bars=new ArrayList<>(template.bars().get(symbol).subList(0,entry));for(int i=entry;i<entry+13;i++)bars.add(bar(symbol,i,100,101,99,100));
            var timeout=new Fixture(template.config(),Map.of(symbol,bars));
            for(boolean v1:List.of(false,true)){
                var run=audit(timeout,v1);var e=run.executions().getFirst();assertEquals("TIMEOUT",e.trade().exitReason());assertEquals(13,e.trade().holdingBars());
                assertEquals(GapExecutionAudit.GapType.NONE,run.executionAudit().trades().getFirst().gapExitType());assertNull(run.executionAudit().trades().getFirst().gapSizeR());
                assertEquals(0,(isLong?bd("99.97"):bd("100.03")).compareTo(e.exitPriceAfterSlippage()));
                assertEquals(0,bd("-0.06").multiply(java.math.BigDecimal.valueOf(e.trade().quantity())).subtract(e.commission()).compareTo(e.trade().netPnl()));
            }
        }
    }
    @Test void generalSymbolAndDateCohortsCountOnlyRealClosedGapTrades(){
        var runs=List.of(audit(fixture(true,100,95,110,90),false),audit(fixture(true,100,109,112,90),false),
            audit(fixture(false,100,105,110,90),false),audit(fixture(false,100,91,110,88),false));
        // Independent actual executions form an analysis-only cohort, not a combined financial portfolio.
        var first=runs.getFirst();var executions=runs.stream().flatMap(r->r.executions().stream()).toList();var audits=runs.stream().flatMap(r->r.executionAudit().trades().stream()).toList();
        var cohort=new PatternV2Run("cohort",first.strategy(),first.timestamp(),new it.andreademartini.trading.backtest.patternstable.PatternStableService(null,null,null,null).version().configuration(),
            first.portfolio(),first.equityTimestamps(),executions,first.analysis(),first.costs(),null);
        var metadata=first.executionAudit();cohort=cohort.withExecutionAudit(new GapExecutionAudit(metadata.schemaVersion(),metadata.executionModelVersion(),metadata.slippageModel(),metadata.slippagePercentPerSide(),metadata.priceAdjustmentModel(),metadata.priceBasisBySymbol(),audits,metadata.limitations()));
        var groups=GapExitAnalysis.groups(cohort);var s=groups.getFirst().statistics();
        assertEquals(4,s.totalClosedTrades());assertEquals(2,s.gapStopCount());assertEquals(2,s.gapTargetCount());assertEquals(1,s.longGapStops());assertEquals(1,s.shortGapStops());assertEquals(1,s.longGapTargets());assertEquals(1,s.shortGapTargets());assertEquals(100,s.gapExitRatePercent().doubleValue());
        var net=runs.stream().map(r->r.portfolio().trades().getFirst().netPnl()).reduce(bd("0"),java.math.BigDecimal::add);assertEquals(0,net.compareTo(s.netPnlGapStop().add(s.netPnlGapTarget())));
        assertEquals(4,groups.stream().filter(g->g.section().equals("YEAR")).mapToLong(g->g.statistics().totalClosedTrades()).sum());
        assertEquals(4,groups.stream().filter(g->g.section().equals("PERIOD")).mapToLong(g->g.statistics().totalClosedTrades()).sum());
        assertEquals(4,groups.stream().filter(g->g.section().equals("SYMBOL")).mapToLong(g->g.statistics().totalClosedTrades()).sum());
        var percentages=audits.stream().map(GapExecutionAudit.TradeAudit::gapSizePercent).sorted().toList();
        assertEquals(0,percentages.get(1).add(percentages.get(2)).divide(bd("2")).compareTo(s.medianGapSizePercent()));
    }
}
