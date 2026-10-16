package it.andreademartini.trading.backtest.patternv2;

import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import it.andreademartini.trading.backtest.patternv1.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.research.pattern.*;
import it.andreademartini.trading.risk.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

class PatternV2Test {
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final List<PatternDetector> detectors=List.of(new BullishEngulfingDetector(),new BearishEngulfingDetector(),new HammerDetector(),new ShootingStarDetector(),new BullishHaramiDetector(),new BearishHaramiDetector(),new PiercingPatternDetector(),new DarkCloudCoverDetector());
    final PositionSizingService sizing=new PositionSizingService(new TradePricingService());
    final TradingSystemProperties trading=new TradingSystemProperties();
    final it.andreademartini.trading.strategy.patternv1.PatternV1Properties frozen=configuration();
    @TempDir Path directory;
    PatternV2Service service(){return new PatternV2Service(frozen,trading,null,detectors,sizing,new PatternV2Store(directory));}
    PatternV2Configuration baseline() throws Exception {
        var json=(ObjectNode)mapper.valueToTree(service().baseline());var g=(ObjectNode)json.get("global");g.put("startDate",START.toString());g.put("endDate",START.plusDays(30).toString());
        return mapper.treeToValue(json,PatternV2Configuration.class);
    }
    PatternV2Configuration change(PatternV2Configuration c,String key,Object value) throws Exception {
        var json=(ObjectNode)mapper.valueToTree(c);((ObjectNode)json.get("global")).set(key,mapper.valueToTree(value));return mapper.treeToValue(json,PatternV2Configuration.class);
    }
    @ParameterizedTest @CsvSource({"AAPL,STOP", "AAPL,TARGET", "AAPL,TIMEOUT", "AAPL,OPEN", "TSLA,STOP", "TSLA,TARGET", "TSLA,TIMEOUT", "TSLA,OPEN"})
    void baselineProducesIdenticalV1TradesCashEquityAndMetrics(String symbol,String reason) throws Exception {
        var config=baseline();boolean isLong=symbol.equals("AAPL");
        var bars=new ArrayList<>(isLong?engulfing(symbol).subList(0,2):List.of(bar(symbol,0,266,270,265.8,266.5)));
        int entry=bars.size();
        if(reason.equals("TIMEOUT"))for(int i=entry;i<entry+20;i++)bars.add(isLong?bar(symbol,i,100,105.5,99,105):bar(symbol,i,265,267,259,260));
        else bars.add(isLong?reason.equals("STOP")?bar(symbol,entry,100,107,97,103):reason.equals("TARGET")?bar(symbol,entry,100,107,99,106):bar(symbol,entry,100,105,99,101)
                :reason.equals("STOP")?bar(symbol,entry,265,271,264,270):reason.equals("TARGET")?bar(symbol,entry,265,266,244,245):bar(symbol,entry,265,267,259,260));
        var input=Map.of(symbol,(List<HistoricalBar>)bars);
        var before=mapper.valueToTree(frozen);var riskBefore=mapper.valueToTree(trading.getRisk());
        var v1=new PatternV1PortfolioEngine(sizing,detectors).run(new PatternV1Request(null,START,START.plusDays(30),BarInterval.DAILY,bd("10000"),null,null),frozen,trading.getRisk(),input);
        var v2=service().simulate(config,input);
        assertEquals(reason,v2.portfolio().trades().getFirst().exitReason());
        assertEquals(0,v1.finalCapital().compareTo(v2.portfolio().finalCapital()));
        assertEquals(0,v1.finalCash().compareTo(v2.portfolio().finalCash()));
        assertEquals(v1.summary(),v2.portfolio().summary());
        assertEquals(mapper.valueToTree(v1.equityCurve()),mapper.valueToTree(v2.portfolio().equityCurve()));
        for(int i=0;i<v1.trades().size();i++) {
            ObjectNode t1=mapper.valueToTree(v1.trades().get(i)),t2=mapper.valueToTree(v2.portfolio().trades().get(i));
            t1.remove(List.of("tradeId","strategy"));t2.remove(List.of("tradeId","strategy"));assertEquals(t1,t2);
        }
        assertEquals(before,mapper.valueToTree(frozen));assertEquals(riskBefore,mapper.valueToTree(trading.getRisk()));
    }
    @Test void baselineIsIndependentAndChangingTargetOrDisablingSymbolAffectsOnlyV2() throws Exception {
        var first=baseline();var second=baseline();assertNotSame(first.symbols(),second.symbols());assertNotSame(first.symbols().getFirst().longConfig(),second.symbols().getFirst().longConfig());
        var v1Before=mapper.valueToTree(frozen);var bars=Map.of("AAPL",engulfing("AAPL"));
        var json=(ObjectNode)mapper.valueToTree(first);var row=(ObjectNode)json.get("symbols").get(0);((ObjectNode)row.get("longConfig")).put("targetR",4);
        var changed=mapper.treeToValue(json,PatternV2Configuration.class);var t=service().simulate(changed,bars).portfolio().trades().getFirst();
        assertEquals(0,bd("108").compareTo(t.takeProfitPrice()));assertEquals(0,bd("3").compareTo(first.symbols().getFirst().longConfig().targetR()));
        row.put("enabled",false);changed=mapper.treeToValue(json,PatternV2Configuration.class);assertTrue(service().simulate(changed,bars).portfolio().trades().isEmpty());
        assertEquals(v1Before,mapper.valueToTree(frozen));
    }
    @Test void symbolOverridesResolveRiskOrderQuantityHoldingAndLongShortIndependently() throws Exception {
        var c=baseline();ObjectNode json=mapper.valueToTree(c);var row=(ObjectNode)json.get("symbols").get(0);
        row.put("riskPercent",1);row.put("maxOrderValuePercent",20);row.put("maxQuantity",7);row.put("maxHoldingBars",2);
        var bars=new ArrayList<>(engulfing("AAPL"));bars.add(bar("AAPL",3,100,105,99,102));
        var run=service().simulate(mapper.treeToValue(json,PatternV2Configuration.class),Map.of("AAPL",bars));var t=run.portfolio().trades().getFirst();
        assertEquals(7,t.quantity());assertEquals(0,bd("1").compareTo(t.desiredRiskPercent()));assertEquals(2,t.holdingBars());assertEquals("TIMEOUT",t.exitReason());
        assertTrue(t.orderValue().doubleValue()<=2000);assertEquals(20,c.global().maxHoldingBars());
        var tsla=c.symbols().stream().filter(s->s.symbol().equals("TSLA")).findFirst().orElseThrow();assertTrue(tsla.longConfig().enabled());assertTrue(tsla.shortConfig().enabled());
        var shortBars=List.of(bar("TSLA",0,266,270,265.8,266.5),bar("TSLA",1,265,266,259,260));
        var shortRun=service().simulate(c,Map.of("TSLA",shortBars));assertEquals(PatternDirection.SHORT,shortRun.portfolio().trades().getFirst().direction());
    }
    @Test void maxOrderPercentSupportsInitialAndCurrentEquity() throws Exception {
        var c=baseline();var bars=new ArrayList<>(engulfing("AAPL").subList(0,2));bars.add(bar("AAPL",2,100,107,99,106));
        bars.add(bar("AAPL",3,100,102,98,99));bars.add(bar("AAPL",4,98.5,102,98.2,101));bars.add(bar("AAPL",5,100,105,99,101));
        var fixed=service().simulate(c,Map.of("AAPL",bars));
        var current=service().simulate(change(c,"maxOrderValueMode","CURRENT_EQUITY_PERCENT"),Map.of("AAPL",bars));
        assertEquals(60,fixed.portfolio().trades().getLast().quantity());assertEquals(62,current.portfolio().trades().getLast().quantity());
        assertTrue(current.portfolio().trades().getLast().orderValue().compareTo(current.portfolio().trades().getLast().equityAtEntry().multiply(bd("0.60")))<=0);
    }
    @Test void commissionsSlippageAndMinQuantityAreAudited() throws Exception {
        var c=baseline();var run=service().simulate(c,Map.of("AAPL",engulfing("AAPL")));var e=run.executions().getFirst();
        assertTrue(e.entryPriceAfterSlippage().compareTo(e.trade().entryPrice())>0);assertEquals(0,bd("1").compareTo(e.commission()));
        var bars=new ArrayList<>(engulfing("AAPL"));bars.set(2,bar("AAPL",2,100,107,97,103));var closed=service().simulate(c,Map.of("AAPL",bars));
        assertTrue(closed.executions().getFirst().exitPriceAfterSlippage().compareTo(closed.executions().getFirst().trade().exitPrice())<0);
        assertEquals(0,bd("5.564").compareTo(closed.costs().total()));
        var free=service().simulate(change(change(c,"commissionEnabled",false),"slippageEnabled",false),Map.of("AAPL",bars));assertEquals(0,free.costs().total().signum());
        assertTrue(service().simulate(change(c,"minQuantity",61),Map.of("AAPL",bars)).portfolio().trades().isEmpty());
        var shortBars=List.of(bar("TSLA",0,266,270,265.8,266.5),bar("TSLA",1,265,271,264,270));var shortRun=service().simulate(c,Map.of("TSLA",shortBars));var s=shortRun.executions().getFirst();
        assertTrue(s.entryPriceAfterSlippage().compareTo(s.trade().entryPrice())<0);assertTrue(s.exitPriceAfterSlippage().compareTo(s.trade().exitPrice())>0);
    }
    @Test void sameBarTargetFirstAndStopBufferAreConfigurable() throws Exception {
        var c=baseline();var bars=new ArrayList<>(engulfing("AAPL"));bars.set(2,bar("AAPL",2,100,107,97,103));
        assertEquals("STOP",service().simulate(c,Map.of("AAPL",bars)).portfolio().trades().getFirst().exitReason());
        assertEquals("TARGET",service().simulate(change(c,"sameBarPolicy","TARGET_FIRST"),Map.of("AAPL",bars)).portfolio().trades().getFirst().exitReason());
        var buffered=service().simulate(change(c,"stopBufferTicks",2),Map.of("AAPL",bars)).portfolio().trades().getFirst();assertEquals(0,bd("97.98").compareTo(buffered.stopPrice()));
    }
    @Test void intradayUsesNextBarAndDoesNotCollapseBarsOnSameDate() throws Exception {
        var c=change(baseline(),"timeframe","HOURLY");var bars=new ArrayList<HistoricalBar>();int i=0;
        for(var b:engulfing("AAPL"))bars.add(new HistoricalBar("AAPL",START.atTime(10+i++,0),b.getOpen(),b.getHigh(),b.getLow(),b.getClose(),1000,BarInterval.HOURLY,"TEST"));
        var run=service().simulate(c,Map.of("AAPL",bars));assertEquals(3,run.portfolio().equityCurve().size());assertEquals(1,run.portfolio().trades().size());
        assertTrue(run.executions().getFirst().entryTimestamp().contains("12:00"));
    }
    @Test void persistenceHistoryResetAndExcelContainExactRunSnapshot() throws Exception {
        var s=new PatternV2Store(directory);var c=baseline();s.initialize(c);var altered=change(c,"maxQuantity",10);var saved=s.saveConfiguration(altered);
        assertEquals(altered,s.configuration(saved.id()).configuration());assertEquals(c,s.reset());assertEquals(1,s.configurations().size());
        var run=service().simulate(altered,Map.of("AAPL",engulfing("AAPL")));s.save(run);assertEquals(10,s.runs().getFirst().portfolio().settings().maxQuantity());assertEquals(altered,s.run(run.runId()).configuration());
        try(var book=new XSSFWorkbook(new java.io.ByteArrayInputStream(new PatternV2ExcelExporter().export(run)))) {
            assertEquals(24,book.getNumberOfSheets());assertNotNull(book.getSheet("GAP_EXIT_ANALYSIS"));assertNotNull(book.getSheet("BOLLINGER_ATR_ANALYSIS"));assertNotNull(book.getSheet("CAPITAL_LIMITS_AUDIT"));for(var name:List.of("SUMMARY","SYMBOL_RESULTS","TRADES","EQUITY_CURVE","CONFIGURATION","RESULTS_BY_TARGET_R","RESULTS_BY_PATTERN","RESULTS_BY_SYMBOL","PERIOD_ANALYSIS","RUN_INFO"))assertNotNull(book.getSheet(name));
            Map<String,String> values=new HashMap<>();for(var row:book.getSheet("CONFIGURATION"))values.put(row.getCell(0).toString(),row.getCell(1)==null?null:row.getCell(1).toString());
            assertEquals("10.0",values.get("global.maxQuantity"));assertEquals("IBKR_US_FIXED",values.get("global.commission.model"));assertEquals("0.03",values.get("global.slippagePercentPerSide"));
        }
        assertThrows(IllegalArgumentException.class,()->s.run("../../pattern-v1"));
    }
    @Test void validationAndAnalysisFiltersDoNotMutateConfiguration() throws Exception {
        var c=baseline();for(var key:List.of("riskPercent","maxOrderValuePercent","maxQuantity","maxHoldingBars"))assertThrows(IllegalArgumentException.class,()->change(c,key,0).validate(detectors));
        var run=service().simulate(c,Map.of("AAPL",engulfing("AAPL")));assertEquals(1,PatternV2Analysis.calculate(run.portfolio(),3.0,null).summary().trades());
        assertEquals(0,PatternV2Analysis.calculate(run.portfolio(),null,4.0).summary().trades());assertEquals(c,run.configuration());
    }
}
