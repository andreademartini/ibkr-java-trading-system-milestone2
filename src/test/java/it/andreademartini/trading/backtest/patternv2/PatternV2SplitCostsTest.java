package it.andreademartini.trading.backtest.patternv2;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import com.fasterxml.jackson.databind.*;
import it.andreademartini.trading.backtest.patternv1.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.research.pattern.*;
import it.andreademartini.trading.risk.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

class PatternV2SplitCostsTest {
    @TempDir Path directory;
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final List<PatternDetector> detectors=List.of(new BullishEngulfingDetector(),new BearishEngulfingDetector(),new PiercingPatternDetector());
    final TradingSystemProperties trading=new TradingSystemProperties();
    final it.andreademartini.trading.strategy.patternv1.PatternV1Properties frozen=configuration();
    final PositionSizingService sizing=new PositionSizingService(new TradePricingService());
    PatternV2Service service(){return new PatternV2Service(frozen,trading,null,detectors,sizing,new PatternV2Store(directory));}
    HistoricalBar barAt(LocalDate date,String provider,PriceAdjustment adjustment){return new HistoricalBar("NVDA",date.atStartOfDay(),bd(".4"),bd(".5"),bd(".3"),bd(".45"),1000,BarInterval.DAILY,provider,adjustment);}
    PatternV2Configuration config(PatternV2SplitCosts costs,LocalDate start,boolean shortDirection) throws Exception {
        var c=service().baseline();var json=mapper.valueToTree(c);var g=(com.fasterxml.jackson.databind.node.ObjectNode)json.get("global");
        g.put("startDate",start.toString());g.put("endDate",start.plusDays(30).toString());g.put("tickSize",.0001);
        c=mapper.treeToValue(json,PatternV2Configuration.class);
        return c.withSymbols(List.of(new PatternV2Configuration.Symbol(true,"NVDA",
                new PatternV2Configuration.Direction(!shortDirection,PatternType.BULLISH_ENGULFING,bd("3")),
                new PatternV2Configuration.Direction(shortDirection,PatternType.BEARISH_ENGULFING,bd("3")),null,null,null,null))).withSplitCosts(costs);
    }
    List<HistoricalBar> fixture(LocalDate start,boolean shortDirection) {
        // Both directions enter at .405164, stop on the entry candle; OHLC are dataset-adjusted units.
        double[][] prices=shortDirection?new double[][]{{.399,.41,.39,.403},{.405,.41,.39,.397},{.405164,.415,.38,.40}}
                :new double[][]{{.410,.42,.39,.400},{.395,.42,.39,.415},{.405164,.419,.38,.40}};
        var bars=new ArrayList<HistoricalBar>();for(int i=0;i<prices.length;i++)bars.add(new HistoricalBar("NVDA",start.plusDays(i).atStartOfDay(),
                BigDecimal.valueOf(prices[i][0]),BigDecimal.valueOf(prices[i][1]),BigDecimal.valueOf(prices[i][2]),BigDecimal.valueOf(prices[i][3]),1000,BarInterval.DAILY,"STOOQ",PriceAdjustment.RAW));return bars;
    }
    @Test void nvdaExampleChargesHistoricalEquivalentSharesAndPreservesMinimumAndCap() {
        var costs=PatternV2SplitCosts.defaults();var fee=PatternV1Commission.ibkr();var price=bd(".405164");
        var factor=costs.factor("NVDA",barAt(LocalDate.of(2010,1,5),"STOOQ",PriceAdjustment.RAW));assertEquals(bd("40"),factor);
        assertEquals(0,bd("230.125").compareTo(PatternV2SplitCosts.equivalentQuantity(9205,factor)));
        assertEquals(0,bd("16.20656").compareTo(price.multiply(factor)));
        assertEquals(0,bd("1.150625").compareTo(PatternV2SplitCosts.commission(fee,factor).charge(9205,price)));
        assertEquals(0,bd("1").compareTo(PatternV2SplitCosts.commission(fee,factor).charge(1000,price)));
        assertEquals(0,bd(".00405164").compareTo(PatternV2SplitCosts.commission(fee,factor).charge(1,price)));
        assertEquals(0,bd("2").compareTo(PatternV2SplitCosts.commission(PatternV1Commission.fixed(bd("2")),factor).charge(9205,price)));
        assertEquals(0,bd("3.10475").compareTo(PatternV2SplitCosts.commission(fee,factor).charge(24838,price)));
    }
    @Test void factorsUseExecutionDateAndDatasetBasisRatherThanBacktestEndOrPrice() {
        var c=PatternV2SplitCosts.defaults();
        for(var entry:Map.of("2021-07-19",40,"2021-07-20",10,"2024-06-09",10,"2024-06-10",1).entrySet())
            assertEquals(0,BigDecimal.valueOf(entry.getValue()).compareTo(c.factor("NVDA",barAt(LocalDate.parse(entry.getKey()),"STOOQ",PriceAdjustment.RAW))));
        assertEquals(BigDecimal.ONE,c.factor("NVDA",barAt(LocalDate.of(2010,1,1),"IBKR",PriceAdjustment.RAW)));
        assertEquals(bd("40"),c.factor("NVDA",barAt(LocalDate.of(2010,1,1),"IBKR",PriceAdjustment.ADJUSTED)));
        assertEquals(BigDecimal.ONE,c.factor("UNKNOWN",barAt(LocalDate.of(2010,1,1),"STOOQ",PriceAdjustment.RAW)));
        var rule=c.rules().getFirst();var raw=new PatternV2SplitCosts(List.of(new PatternV2SplitCosts.Rule("NVDA",PatternV2SplitCosts.Basis.RAW,rule.validFrom(),rule.adjustedThrough(),rule.splits())));
        assertEquals(BigDecimal.ONE,raw.factor("NVDA",barAt(LocalDate.of(2010,1,1),"STOOQ",PriceAdjustment.ADJUSTED)));
        var oldBasis=new PatternV2SplitCosts(List.of(new PatternV2SplitCosts.Rule("NVDA",PatternV2SplitCosts.Basis.SPLIT_ADJUSTED,rule.validFrom(),LocalDate.of(2021,7,20),List.of(rule.splits().getFirst()))));
        assertEquals(bd("4"),oldBasis.factor("NVDA",barAt(LocalDate.of(2010,1,1),"STOOQ",PriceAdjustment.RAW)));
        assertThrows(IllegalArgumentException.class,()->c.factor("NVDA",barAt(LocalDate.of(2009,12,31),"STOOQ",PriceAdjustment.RAW)));
    }
    @Test void longAndShortAccountingSizingAndExportsUseCorrectedFeesWithoutChangingV1() throws Exception {
        var before=mapper.valueToTree(frozen);var riskBefore=mapper.valueToTree(trading.getRisk());
        for(boolean shortDirection:List.of(false,true)) {
            var start=LocalDate.of(2010,1,4);var input=Map.of("NVDA",fixture(start,shortDirection));
            var c=config(PatternV2SplitCosts.defaults(),start,shortDirection);
            var corrected=service().simulate(c,input);var legacy=service().simulate(c.withSplitCosts(new PatternV2SplitCosts(List.of())),input);
            var e=corrected.executions().getFirst();assertTrue(e.trade().closed());assertEquals(0,bd("40").compareTo(e.splitExecution().entryFactor()));
            assertTrue(corrected.costs().commissions().compareTo(legacy.costs().commissions())<0);
            assertTrue(e.trade().quantity()>=legacy.executions().getFirst().trade().quantity());
            assertEquals(0,corrected.costs().total().compareTo(e.trade().costs()));
            assertEquals(0,corrected.portfolio().initialCapital().add(e.trade().netPnl()).compareTo(corrected.portfolio().finalCapital()));
            assertTrue(e.trade().orderValue().compareTo(bd("6000"))<=0);assertTrue(e.trade().quantity()<=c.global().maxQuantity());
            assertEquals(0,e.trade().entryPrice().multiply(bd(".0003")).multiply(BigDecimal.valueOf(e.trade().quantity())).add(e.trade().exitPrice().multiply(bd(".0003")).multiply(BigDecimal.valueOf(e.trade().quantity()))).compareTo(e.slippageCost()));
            assertTrue(shortDirection?e.entryPriceAfterSlippage().compareTo(e.trade().entryPrice())<0:e.entryPriceAfterSlippage().compareTo(e.trade().entryPrice())>0);
            var store=new PatternV2Store(directory);store.save(corrected);assertEquals(mapper.valueToTree(corrected),mapper.valueToTree(store.run(corrected.runId())));
            try(var book=new XSSFWorkbook(new java.io.ByteArrayInputStream(new PatternV2ExcelExporter().export(corrected)))) {
                var sheet=book.getSheet("TRADES");int factorCol=-1;for(var cell:sheet.getRow(0))if("entrySplitFactor".equals(cell.getStringCellValue()))factorCol=cell.getColumnIndex();
                assertTrue(factorCol>=0);assertEquals(40,sheet.getRow(1).getCell(factorCol).getNumericCellValue());
                assertTrue(java.util.stream.StreamSupport.stream(book.getSheet("CONFIGURATION").spliterator(),false).anyMatch(row->row.getCell(0).toString().equals("splitCosts.rules[0].splits[0].ratio")&&row.getCell(1).getNumericCellValue()==4));
            }
        }
        assertEquals(before,mapper.valueToTree(frozen));assertEquals(riskBefore,mapper.valueToTree(trading.getRisk()));
    }
    @Test void crossingSplitChangesExitEquivalentQuantityAndPerShareCosts() throws Exception {
        var start=LocalDate.of(2021,7,17);var c=config(PatternV2SplitCosts.defaults(),start,false);
        var json=mapper.valueToTree(c);((com.fasterxml.jackson.databind.node.ObjectNode)json.get("global")).put("transactionCostsPerShare",.01);
        c=mapper.treeToValue(json,PatternV2Configuration.class);
        var bars=new ArrayList<>(fixture(start,false));bars.set(2,new HistoricalBar("NVDA",start.plusDays(2).atStartOfDay(),bd(".405164"),bd(".42"),bd(".395"),bd(".410"),1000,BarInterval.DAILY,"STOOQ",PriceAdjustment.RAW));
        bars.add(new HistoricalBar("NVDA",LocalDate.of(2021,7,20).atStartOfDay(),bd(".4"),bd(".42"),bd(".38"),bd(".4"),1000,BarInterval.DAILY,"STOOQ",PriceAdjustment.RAW));
        var run=service().simulate(c,Map.of("NVDA",bars));var e=run.executions().getFirst();
        assertEquals(0,bd("40").compareTo(e.splitExecution().entryFactor()));assertEquals(0,bd("10").compareTo(e.splitExecution().exitFactor()));
        assertEquals(0,e.splitExecution().entryQuantityEquivalent().multiply(bd("4")).compareTo(e.splitExecution().exitQuantityEquivalent()));
        assertEquals(0,bd(".01").multiply(e.splitExecution().entryQuantityEquivalent().add(e.splitExecution().exitQuantityEquivalent())).compareTo(e.transactionCosts()));
    }
    @Test void legacySnapshotsStayLegacyAndOnlyNewRunsReceiveDefaultRules() throws Exception {
        var c=config(null,LocalDate.of(2010,1,1),false);var legacyJson=mapper.valueToTree(c);((com.fasterxml.jackson.databind.node.ObjectNode)legacyJson).remove("splitCosts");
        var read=mapper.treeToValue(legacyJson,PatternV2Configuration.class);assertNull(read.splitCosts());assertNotNull(read.forNewRun().splitCosts());assertNull(read.splitCosts());
        var run=service().simulate(read,Map.of("NVDA",fixture(LocalDate.of(2010,1,1),false)));var runJson=mapper.valueToTree(run);
        ((com.fasterxml.jackson.databind.node.ObjectNode)runJson.get("configuration")).remove("splitCosts");
        for(var e:runJson.get("executions"))((com.fasterxml.jackson.databind.node.ObjectNode)e).remove("splitExecution");
        var old=mapper.treeToValue(runJson,PatternV2Run.class);assertNull(old.configuration().splitCosts());assertNull(old.executions().getFirst().splitExecution());
        assertDoesNotThrow(()->new PatternV2ExcelExporter().export(old));
    }
}
