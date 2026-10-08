package it.andreademartini.trading.backtest.patternv1;

import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.research.pattern.*;
import it.andreademartini.trading.risk.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.util.*;

class PatternV1PortfolioEngineTest {
    private final PositionSizingService sizing=new PositionSizingService(new TradePricingService());
    private final PatternV1PortfolioEngine engine=new PatternV1PortfolioEngine(sizing,detectors());
    private final TradingSystemProperties.RiskProperties risk=new TradingSystemProperties.RiskProperties();
    private PatternV1Request request(){return new PatternV1Request(null,START,START.plusDays(100),BarInterval.DAILY,bd("10000"),BigDecimal.ZERO,BigDecimal.ZERO);}
    @Test void stopFirstAndResearchCoherence() {
        var bars=new ArrayList<>(engulfing("AAPL"));bars.set(2,bar("AAPL",2,100,107,97,103));
        var result=engine.run(request(),configuration(),risk,Map.of("AAPL",bars));
        var trade=result.trades().getFirst();
        assertEquals("STOP",trade.exitReason());assertEquals(0,bd("98").compareTo(trade.exitPrice()));
        assertEquals(0,bd("-1").compareTo(trade.resultR()));assertTrue(trade.sameBarAmbiguous());assertTrue(trade.closed());
        assertEquals(0,bd("1").compareTo(trade.executedMaeR()));assertEquals(0,bd("0").compareTo(trade.executedMfeR()));
        assertEquals(60,trade.quantity());assertEquals(1,trade.holdingBars());assertEquals(0,bd("9880").compareTo(result.finalCapital()));
        assertEquals(1,result.summary().ambiguities());assertEquals(0,result.openPositions());
        var detection=new BullishEngulfingDetector().detect(bars,1,PatternDetectorParameters.defaults()).orElseThrow();
        var research=research(bars,detection,bd("3")).getFirst();
        assertEquals(0,research.entryPrice().compareTo(trade.entryPrice()));assertEquals(0,research.stopPrice().compareTo(trade.stopPrice()));
        assertEquals(0,research.targetPrice().compareTo(trade.takeProfitPrice()));assertEquals(research.exitDate(),trade.exitDate());
        assertEquals(0,research.resultR().compareTo(trade.resultR()));assertEquals(0,research.mfe().compareTo(trade.rawBarMfeR()));assertEquals(0,research.mae().compareTo(trade.rawBarMaeR()));
        assertTrue(result.events().stream().anyMatch(e->e.eventType().equals("SAME_BAR_AMBIGUITY")));
    }
    @Test void tslaBothSidesButNeverConcurrent() {
        var bars=List.of(bar("TSLA",0,249,251,240,245),bar("TSLA",1,244,251,241,250),
                bar("TSLA",2,250,251,249,250.5),bar("TSLA",3,250,258,249.8,251),
                bar("TSLA",4,250,261,249,260),bar("TSLA",5,266,270,265.8,266.5),bar("TSLA",6,265,266,244,245));
        var result=engine.run(request(),configuration(),risk,Map.of("TSLA",bars));
        assertEquals(2,result.trades().size());assertEquals(PatternDirection.LONG,result.trades().get(0).direction());
        assertEquals(PatternDirection.SHORT,result.trades().get(1).direction());
        assertTrue(result.summary().skippedPositionOpen()>0);
        assertTrue(result.trades().get(0).exitDate().isBefore(result.trades().get(1).entryDate()));
        assertEquals(0,bd("245").compareTo(result.trades().get(1).takeProfitPrice()));
    }
    @Test void timeoutExactly20BarsClosesAt105AndDoesNotInspectBar21() {
        var bars=new ArrayList<>(engulfing("AAPL").subList(0,2));
        for(int i=2;i<22;i++)bars.add(bar("AAPL",i,100,105.5,99,i==21?105:101));
        bars.add(bar("AAPL",22,100,999,1,900));
        var result=engine.run(request(),configuration(),risk,Map.of("AAPL",bars));
        var t=result.trades().getFirst();assertEquals("TIMEOUT",t.exitReason());assertEquals(20,t.holdingBars());
        assertEquals(0,bd("2.5").compareTo(t.resultR()));assertNull(t.researchResultR());assertTrue(t.closed());assertEquals(0,result.openPositions());
        assertEquals(0,t.remainingQuantity());assertEquals(0,bd("105").compareTo(t.exitPrice()));assertEquals(START.plusDays(21),t.exitDate());
        assertEquals(0,result.finalCapital().compareTo(result.finalCash()));assertEquals(0,bd("10300").compareTo(result.finalCapital()));
        var research=research(bars,new BullishEngulfingDetector().detect(bars,1,PatternDetectorParameters.defaults()).orElseThrow(),bd("3")).getFirst();
        assertEquals(PatternExitReason.TIMEOUT,research.exitReason());assertNull(research.exitPrice());assertNull(research.resultR());
        assertEquals(0,research.mfe().compareTo(t.rawBarMfeR()));assertEquals(0,research.mae().compareTo(t.rawBarMaeR()));
        assertTrue(result.events().stream().anyMatch(e->e.eventType().equals("TIMEOUT")&&e.date().equals(START.plusDays(21))));
    }
    @Test void explicitTimeoutLiquidationAndCostsReconcile() {
        var bars=new ArrayList<>(engulfing("AAPL").subList(0,2));for(int i=2;i<22;i++)bars.add(bar("AAPL",i,100,102,99,101));
        var request=new PatternV1Request(null,START,START.plusDays(100),BarInterval.DAILY,bd("10000"),bd("1"),bd("0.1"));
        var result=engine.run(request,configuration(),risk,Map.of("AAPL",bars));var t=result.trades().getFirst();
        assertEquals("TIMEOUT",t.exitReason());assertEquals(20,t.holdingBars());assertTrue(t.closed());assertEquals(0,bd("101").compareTo(t.exitPrice()));
        assertNull(t.researchResultR());assertEquals(0,bd("0.5").compareTo(t.resultR()));
        assertEquals(0,result.finalCapital().subtract(bd("10000")).compareTo(t.netPnl()));assertTrue(t.costs().signum()>0);
    }
    @Test void twoSymbolsShareCapitalNoFutureExitFunding() {
        risk.setMaxLongNotional(10000);risk.setMaxOrderValue(10000);risk.setRiskPerTradePercent(100);
        var a=new ArrayList<>(engulfing("AAPL"));a.set(2,bar("AAPL",2,100,107,99,106));
        var t=List.of(bar("TSLA",0,100,101,98,99),bar("TSLA",1,98.5,101,98.2,100.5),bar("TSLA",2,100,103,99,102));
        var result=engine.run(request(),configuration(),risk,Map.of("AAPL",a,"TSLA",t));
        assertEquals(2,result.trades().size());assertEquals("AAPL",result.trades().getFirst().symbol());
        assertEquals(0,result.summary().skippedCapital());assertEquals(0,bd("10440").compareTo(result.finalCapital()));
        assertEquals(0,bd("4000").compareTo(result.trades().getLast().availableCashBeforeEntry()));
    }
    @Test void futureEntryCandleDoesNotChangeSignalEntryOrQuantity() {
        var bars=engulfing("AAPL");var changed=new ArrayList<>(bars);changed.set(2,bar("AAPL",2,100,999,1,900));
        var original=engine.run(request(),configuration(),risk,Map.of("AAPL",bars));
        var mutated=engine.run(request(),configuration(),risk,Map.of("AAPL",changed));
        var a=original.trades().getFirst();var b=mutated.trades().getFirst();
        assertEquals(a.signalDate(),b.signalDate());assertEquals(a.entryDate(),b.entryDate());assertEquals(a.entryPrice(),b.entryPrice());assertEquals(a.quantity(),b.quantity());assertEquals(a.stopPrice(),b.stopPrice());
        assertEquals(original.events().stream().filter(e->e.eventType().equals("SIGNAL")).findFirst().orElseThrow().date(),mutated.events().stream().filter(e->e.eventType().equals("SIGNAL")).findFirst().orElseThrow().date());
    }
    @Test void unconfiguredSymbolAndDailyRestriction() {
        assertTrue(engine.run(request(),configuration(),risk,Map.of("MSFT",engulfing("MSFT"))).trades().isEmpty());
        var invalid=new PatternV1Request(null,START,START.plusDays(10),BarInterval.HOURLY,null,null,null);
        assertThrows(IllegalArgumentException.class,()->engine.run(invalid,configuration(),risk,Map.of()));
    }
    @Test void noFutureDataIsNotMislabelledTimeout() {
        var result=engine.run(request(),configuration(),risk,Map.of("AAPL",engulfing("AAPL")));
        var t=result.trades().getFirst();assertEquals("OPEN",t.exitReason());assertEquals(0,result.summary().timeouts());
        assertFalse(t.closed());assertNull(t.exitDate());assertNull(t.exitPrice());assertNull(t.resultR());assertEquals(t.quantity(),t.remainingQuantity());
        assertEquals(0,t.grossPnl().signum());assertEquals(0,t.netPnl().signum());assertEquals(0,bd("60").compareTo(t.unrealizedPnl()));
    }
    @Test void exportAndPersistentRunRoundTrip(@TempDir Path directory) throws Exception {
        var bars=new ArrayList<>(engulfing("AAPL"));bars.set(2,bar("AAPL",2,100,107,97,103));
        var result=engine.run(request(),configuration(),risk,Map.of("AAPL",bars));
        var store=new PatternV1RunStore(directory);store.save(result);assertEquals(result,store.find(result.backtestId()));
        assertThrows(IllegalArgumentException.class,()->store.find("../../outside"));
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(new PatternV1ExcelExporter().export(result)))) {
            assertEquals(5,workbook.getNumberOfSheets());
            for(String sheet:List.of("SUMMARY","SYMBOL_RESULTS","TRADES","EQUITY_CURVE","CONFIGURATION"))assertNotNull(workbook.getSheet(sheet));
            assertEquals(2,workbook.getSheet("EQUITY_CURVE").getDrawingPatriarch().getCharts().size());
            assertEquals("AAPL",workbook.getSheet("TRADES").getRow(1).getCell(1).getStringCellValue());
            assertEquals(20,workbook.getSheet("CONFIGURATION").getRow(20).getRowNum());
            var headings=new HashSet<String>();
            for(var cell:workbook.getSheet("TRADES").getRow(0))headings.add(cell.getStringCellValue());
            for(String column:List.of("Desired Quantity","Final Quantity","Quantity Limiting Reason","Equity At Entry",
                    "Target Risk %","Actual Risk Amount","Actual Risk %","Timeout Exit Price","Remaining Quantity",
                    "Executed MFE R","Executed MAE R","Raw Bar MFE R","Raw Bar MAE R","Closed"))assertTrue(headings.contains(column),column);
            var equitySheet=workbook.getSheet("EQUITY_CURVE");
            assertEquals("Cash",equitySheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("Equity",equitySheet.getRow(0).getCell(6).getStringCellValue());
            assertEquals(result.finalCash().doubleValue(),equitySheet.getRow(3).getCell(1).getNumericCellValue());
            assertEquals(result.finalCapital().doubleValue(),equitySheet.getRow(3).getCell(6).getNumericCellValue());
            assertEquals(0,workbook.getSheet("TRADES").getRow(1).getCell(43).getNumericCellValue());
        }
    }
    private List<PatternOccurrence> research(List<HistoricalBar> bars,PatternDetectionResult detection,BigDecimal target) {
        var config=new PatternResearchConfiguration(START,START.plusDays(100),List.of("AAPL"),List.of(detection.patternType()),ResearchDirection.BOTH,
                List.of(target),List.of(),20,SameBarResolution.STOP_FIRST,70,100,EntryMethod.NEXT_BAR_OPEN,StopMethod.PATTERN_EXTREME,BarInterval.DAILY,bd("0.01"),0,false,PatternDetectorParameters.defaults());
        return new PatternOutcomeAnalyzer().analyze("test","AAPL",bars,detection,DatasetSegment.IN_SAMPLE,config);
    }
}
