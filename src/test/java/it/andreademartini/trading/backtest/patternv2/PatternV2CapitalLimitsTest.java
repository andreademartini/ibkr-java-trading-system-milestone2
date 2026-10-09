package it.andreademartini.trading.backtest.patternv2;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.risk.*;
import it.andreademartini.trading.marketdata.HistoricalBar;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

class PatternV2CapitalLimitsTest {
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    @TempDir Path directory;
    PatternV2Service service(){return new PatternV2Service(configuration(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),new PatternV2Store(directory));}
    PatternV2Configuration config(boolean dynamic) throws Exception {
        ObjectNode node=mapper.valueToTree(service().baseline());var g=(ObjectNode)node.get("global");
        g.put("initialCapital",50000);g.put("maxOrderValuePercent",30);g.put("maxOrderValueMode","CURRENT_EQUITY_PERCENT");
        g.put("startDate",START.toString());g.put("endDate",START.plusDays(30).toString());
        for(var key:List.of("maxPositionValue","maxLongNotional","maxShortNotional")){g.put(key,10000);g.put(key+"Mode",dynamic?"CURRENT_EQUITY_PERCENT":"FIXED_AMOUNT");g.put(key+"Percent",100);}
        return mapper.treeToValue(node,PatternV2Configuration.class);
    }
    @ParameterizedTest @ValueSource(longs={10000,15000,25000,50000,100000,25000,15000})
    void limitsUseCurrentEquityIncreasingAndDecreasing(long equity) throws Exception {
        var limits=PatternV2CapitalLimits.effective(config(true),null,BigDecimal.valueOf(equity));
        assertEquals(0,limits.maxOrderValue().compareTo(BigDecimal.valueOf(equity).multiply(new BigDecimal("0.30"))));
        for(var n:List.of(limits.maxPositionValue(),limits.maxLongNotional(),limits.maxShortNotional()))assertEquals(0,n.compareTo(BigDecimal.valueOf(equity)));
        assertEquals(10000,PatternV2CapitalLimits.effective(config(false),null,BigDecimal.valueOf(equity)).maxPositionValue().intValueExact());
    }
    @Test void legacyRoundTripAndValidation() throws Exception {
        ObjectNode node=mapper.valueToTree(config(false));var g=(ObjectNode)node.get("global");for(var k:List.of("maxPositionValue","maxLongNotional","maxShortNotional")){g.remove(k+"Mode");g.remove(k+"Percent");}
        var legacy=mapper.treeToValue(node,PatternV2Configuration.class);assertEquals(config(false),legacy);legacy.validate(detectors());
        for(var invalid:List.of(0.0,-1.0,Double.POSITIVE_INFINITY)){g.put("maxPositionValuePercent",invalid);assertThrows(IllegalArgumentException.class,()->mapper.treeToValue(node,PatternV2Configuration.class).validate(detectors()));}
        assertThrows(IllegalArgumentException.class,()->PatternV2CapitalLimits.effective(legacy,null,BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,()->PatternV2CapitalLimits.effective(legacy,null,BigDecimal.ONE.negate()));
        assertThrows(IllegalArgumentException.class,()->PatternV2CapitalLimits.effective(legacy,null,null));
    }
    @Test void independentModesAndDirectionLimits() throws Exception {
        ObjectNode node=mapper.valueToTree(config(true));var g=(ObjectNode)node.get("global");g.put("maxPositionValuePercent",10);g.put("maxLongNotionalMode","FIXED_AMOUNT");g.put("maxShortNotionalPercent",30);
        var c=mapper.treeToValue(node,PatternV2Configuration.class);var limits=PatternV2CapitalLimits.effective(c,null,bd("50000"));assertEquals(5000,limits.maxPositionValue().intValue());assertEquals(10000,limits.maxLongNotional().intValue());assertEquals(15000,limits.maxShortNotional().intValue());
    }
    @Test void longAndShortSizingCostsCashAndAudit() throws Exception {
        for(var input:List.of(Map.of("AAPL",engulfing("AAPL")),Map.of("TSLA",List.of(bar("TSLA",0,266,270,265.8,266.5),bar("TSLA",1,265,267,259,260))))){
            var fixed=service().simulate(config(false),input);var dynamic=service().simulate(config(true),input);
            assertFalse(dynamic.executions().isEmpty());assertTrue(dynamic.portfolio().trades().getFirst().quantity()>fixed.portfolio().trades().getFirst().quantity());
            for(var e:dynamic.executions()){var t=e.trade();var a=e.capitalLimits();assertEquals(50000,a.maxPositionValueEffective().intValue());assertTrue(t.actualRiskPercent().doubleValue()<=2);assertTrue(t.availableCashAfterEntry().signum()>=0);assertTrue(t.orderValue().doubleValue()<=15000);assertEquals(0,t.entryCosts().compareTo(e.commission().subtract(t.closed()?config(true).global().commission().charge(t.quantity(),t.exitPrice()):BigDecimal.ZERO).add(t.entryPrice().multiply(bd("0.0003")).multiply(BigDecimal.valueOf(t.quantity())))));assertEquals(0,t.costs().compareTo(e.commission().add(e.slippageCost()).add(e.transactionCosts())));}
            assertEquals(config(false).global().commission(),config(true).global().commission());assertEquals(config(false).global().slippagePercentPerSide(),config(true).global().slippagePercentPerSide());
        }
    }
    @Test void futureClosesCannotChangeEntryLimits() throws Exception {
        var bars=new ArrayList<>(engulfing("AAPL"));var a=service().simulate(config(true),Map.of("AAPL",bars));bars.set(2,bar("AAPL",2,100,1000,99,999));var b=service().simulate(config(true),Map.of("AAPL",bars));assertEquals(a.executions().getFirst().capitalLimits(),b.executions().getFirst().capitalLimits());
    }
    @Test void simultaneousSignalsRespectCashAndDoNotCreateLeverage() throws Exception {
        var symbols=List.of("AAPL","BBB","CCC","DDD","EEE");var base=config(true);var pattern=base.symbols().stream().filter(s->s.symbol().equals("AAPL")).findFirst().orElseThrow();
        var c=base.withSymbols(symbols.stream().map(s->new PatternV2Configuration.Symbol(true,s,pattern.longConfig(),pattern.shortConfig(),null,null,null,null)).toList());
        Map<String,List<HistoricalBar>> bars=new TreeMap<>();symbols.forEach(s->bars.put(s,engulfing(s)));var result=service().simulate(c,bars);
        assertEquals(4,result.executions().size());assertEquals(1,result.portfolio().summary().skippedCapital());
        assertTrue(result.executions().getLast().capitalLimits().cashLimited());
        for(var e:result.executions())assertTrue(e.trade().availableCashAfterEntry().signum()>=0);
        assertTrue(result.portfolio().events().stream().filter(e->e.eventType().equals("ENTRY")).map(e->e.symbol()).toList().equals(symbols.subList(0,4)));
    }
    @Test void strictComparisonAndPersistenceAndExcel() throws Exception {
        var service=service();var fixed=service.runOnSnapshot(config(false),Map.of("AAPL",engulfing("AAPL")));var dynamic=service.replayCapitalLimits(fixed.runId(),config(true));
        var comparison=service.compareCapitalLimits(List.of(fixed.runId(),dynamic.runId()));assertEquals(2,comparison.runs().size());assertTrue(comparison.metrics().stream().anyMatch(m->m.metric().equals("Expectancy Net R")));
        assertThrows(IllegalArgumentException.class,()->PatternV2RunComparison.requireSameTradingConfiguration(config(false),config(true)));
        ObjectNode changed=mapper.valueToTree(config(true));((ObjectNode)changed.get("global")).put("riskPercent",3);assertThrows(IllegalArgumentException.class,()->service.replayCapitalLimits(fixed.runId(),mapper.treeToValue(changed,PatternV2Configuration.class)));
        var store=new PatternV2Store(directory);assertEquals(dynamic,store.run(dynamic.runId()));var preset=service.savePreset(config(true));assertEquals(config(true),store.configuration(preset.id()).configuration());
        try(var book=new XSSFWorkbook(new java.io.ByteArrayInputStream(new PatternV2ExcelExporter().export(dynamic)))){
            var sheet=book.getSheet("TRADES");int column=-1;for(var cell:sheet.getRow(0))if(cell.getStringCellValue().equals("MaxPositionValueEffective"))column=cell.getColumnIndex();assertTrue(column>=0);assertEquals(50000,sheet.getRow(1).getCell(column).getNumericCellValue());
            assertNotNull(book.getSheet("CAPITAL_LIMITS_AUDIT"));assertTrue(book.getSheet("CONFIGURATION").getLastRowNum()>0);
            assertTrue(java.util.stream.StreamSupport.stream(book.getSheet("CONFIGURATION").spliterator(),false).anyMatch(r->r.getCell(0)!=null&&r.getCell(0).toString().contains("maxPositionValueMode")));
        }
    }
}
