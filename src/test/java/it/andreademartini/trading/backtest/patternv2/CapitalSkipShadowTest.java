package it.andreademartini.trading.backtest.patternv2;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.research.pattern.*;
import java.nio.file.Path;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CapitalSkipShadowTest {
    @TempDir Path directory;
    PatternRangeFilterTest support;
    @BeforeEach void setup(){support=new PatternRangeFilterTest();support.directory=directory;}
    PatternV2Configuration config(PatternDirection direction) throws Exception {
        ObjectNode json=support.mapper.valueToTree(support.config());((ObjectNode)json.get("global")).put("minQuantity",40).put("maxHoldingBars",2);
        var c=support.mapper.treeToValue(json,PatternV2Configuration.class);var enabled=new PatternV2Configuration.Direction(true,direction==PatternDirection.LONG?PatternType.HAMMER:PatternType.SHOOTING_STAR,bd("3"));
        return c.withSymbols(List.of("AAA","BBB","CCC").stream().map(s->new PatternV2Configuration.Symbol(true,s,direction==PatternDirection.LONG?enabled:null,direction==PatternDirection.SHORT?enabled:null,null,null,null,null)).toList());
    }
    Map<String,List<HistoricalBar>> bars(PatternDirection d,String outcome){
        Map<String,List<HistoricalBar>> input=new TreeMap<>();for(String s:List.of("AAA","BBB","CCC")){
            var first=d==PatternDirection.LONG?bar(s,0,100,102,99,100):bar(s,0,100,101,98,100);var entry=bar(s,1,100,100.5,99.5,100);
            var last=outcome.equals("TARGET")?d==PatternDirection.LONG?bar(s,2,100,104,99.5,103):bar(s,2,100,100.5,96,97):outcome.equals("STOP")?d==PatternDirection.LONG?bar(s,2,100,100.5,98,99):bar(s,2,100,102,99.5,101):outcome.equals("AMBIGUOUS")?bar(s,2,100,104,96,100):bar(s,2,100,100.5,99.5,100.25);
            input.put(s,outcome.equals("INCOMPLETE")?List.of(first,entry):List.of(first,entry,last));
        }return input;
    }
    PatternV2Service service(PatternDirection d){return support.service(List.of(support.signalDetector(d)));}
    @ParameterizedTest @CsvSource({"LONG,TARGET,3","SHORT,TARGET,3","LONG,STOP,-1","SHORT,STOP,-1","LONG,AMBIGUOUS,-1","SHORT,AMBIGUOUS,-1","LONG,TIMEOUT,.25","SHORT,TIMEOUT,-.25"})
    void exitsAndAccounting(PatternDirection d,String outcome,String expectedR) throws Exception {
        var c=config(d).withCapitalSkipShadowAnalysis(true);var r=service(d).runOnSnapshot(c,bars(d,outcome));var a=r.capitalSkipShadowAnalysis();assertEquals(2,a.totalCapitalSkips());assertEquals(2,a.completedShadowTrades());
        for(var t:a.trades()){
            assertEquals(outcome.equals("AMBIGUOUS")?"STOP":outcome,t.exitReason());assertEquals(2,t.holdingBars());assertEquals(0,bd(expectedR).compareTo(t.grossResultR()));assertTrue(t.shadowQuantity()>=40);assertEquals(10000,t.equityReferenceAtSignal().doubleValue());
            assertEquals(0,t.grossPnl().subtract(t.totalCommission()).subtract(t.slippageCost()).subtract(t.transactionCosts()).compareTo(t.netPnl()));
            var executionPnl=(d==PatternDirection.LONG?t.exitPriceAfterSlippage().subtract(t.entryPriceAfterSlippage()):t.entryPriceAfterSlippage().subtract(t.exitPriceAfterSlippage())).multiply(java.math.BigDecimal.valueOf(t.shadowQuantity()));
            assertEquals(0,executionPnl.subtract(t.totalCommission()).subtract(t.transactionCosts()).compareTo(t.netPnl()));assertEquals(t.netPnl().doubleValue()/t.actualShadowRisk().doubleValue(),t.netResultR().doubleValue(),1e-10);assertEquals(2,t.totalCommission().doubleValue());
        }
    }
    @Test void onOffPreservesEntirePortfolioAndExactTradeIds() throws Exception {
        var c=config(PatternDirection.LONG);var input=bars(PatternDirection.LONG,"TARGET");var service=service(PatternDirection.LONG);String identity=UUID.randomUUID().toString();
        var off=service.simulate(c,input,identity);var on=service.simulate(c.withCapitalSkipShadowAnalysis(true),input,identity);
        assertEquals(off.portfolio(),on.portfolio());assertEquals(off.executions(),on.executions());assertEquals(off.analysis(),on.analysis());assertEquals(off.costs(),on.costs());assertEquals(off.capitalSkipAudits(),on.capitalSkipAudits());assertNull(off.capitalSkipShadowAnalysis());assertNotNull(on.capitalSkipShadowAnalysis());assertEquals(2,on.portfolio().summary().skippedCapital());
        var snapshot=on.capitalSkipAudits().getFirst().context();assertEquals(2,snapshot.processingOrder());assertEquals(List.of("AAA","BBB","CCC"),snapshot.competingSignals().stream().map(CapitalSkipAudit.Candidate::symbol).toList());assertEquals("ENTRY",snapshot.competingSignals().getFirst().outcome());assertEquals("SIGNAL_SKIPPED_CAPITAL",snapshot.competingSignals().get(1).outcome());assertEquals(1,snapshot.openPositions());assertTrue(snapshot.capitalShortfall().signum()>0);
    }
    @Test void residualCashAndFinalEquityAreNotUsedForShadowSizing() throws Exception {
        var r=service(PatternDirection.LONG).runOnSnapshot(config(PatternDirection.LONG).withCapitalSkipShadowAnalysis(true),bars(PatternDirection.LONG,"TARGET"));var audit=r.capitalSkipAudits().getFirst();var shadow=r.capitalSkipShadowAnalysis().trades().getFirst();
        assertTrue(shadow.shadowOrderValue().compareTo(audit.context().availableCash())>0);assertEquals(200,shadow.riskBudget().doubleValue());assertNotEquals(r.portfolio().finalCapital().doubleValue(),shadow.equityReferenceAtSignal().doubleValue());assertNotEquals(audit.context().equity(),shadow.equityReferenceAtSignal());
        var zeroCash=new CapitalSkipAudit(audit.signalId(),audit.signal(),audit.signalTimestamp(),audit.decisionTimestamp(),audit.entryIndex(),audit.equityReferenceAtSignal(),audit.rejectReason(),new CapitalSkipAudit.Context(bd("0"),bd("0"),bd("999999"),0,bd("0"),List.of(),List.of(),1,bd("1"),bd("1")));
        var simulator=new CapitalSkipShadowAnalysis(support.sizing);var again=simulator.simulate(r.configuration(),zeroCash,bars(PatternDirection.LONG,"TARGET").get("BBB"));assertEquals(shadow.shadowQuantity(),again.shadowQuantity());assertEquals(shadow.netPnl(),again.netPnl());
    }
    @Test void incompleteDataHasNoInventedOutcomeAndEarlyExitIsComplete() throws Exception {
        var c=config(PatternDirection.LONG).withCapitalSkipShadowAnalysis(true);var r=service(PatternDirection.LONG).runOnSnapshot(c,bars(PatternDirection.LONG,"INCOMPLETE"));assertEquals(2,r.capitalSkipShadowAnalysis().incompleteShadowTrades());assertEquals(0,r.capitalSkipShadowAnalysis().shadow().trades());
        for(var t:r.capitalSkipShadowAnalysis().trades()){assertEquals(CapitalSkipShadowAnalysis.Status.INCOMPLETE_DATA,t.shadowStatus());assertNull(t.netPnl());assertNull(t.grossResultR());assertNull(t.netResultR());}
        var audit=r.capitalSkipAudits().getFirst();var early=List.of(bar("BBB",0,100,102,99,100),bar("BBB",1,100,104,99.5,103));assertEquals(CapitalSkipShadowAnalysis.Status.COMPLETED,new CapitalSkipShadowAnalysis(support.sizing).simulate(c,audit,early).shadowStatus());
    }
    @Test void limitsCanRejectTheoreticalQuantityAndFutureBarsCannotChangeSizing() throws Exception {
        var c=config(PatternDirection.LONG);var r=service(PatternDirection.LONG).runOnSnapshot(c,bars(PatternDirection.LONG,"TARGET"));var audit=r.capitalSkipAudits().getFirst();var simulator=new CapitalSkipShadowAnalysis(support.sizing);
        var one=simulator.simulate(c,audit,bars(PatternDirection.LONG,"TARGET").get("BBB"));var two=simulator.simulate(c,audit,bars(PatternDirection.LONG,"STOP").get("BBB"));assertEquals(one.entryPrice(),two.entryPrice());assertEquals(one.stop(),two.stop());assertEquals(one.target(),two.target());assertEquals(one.shadowQuantity(),two.shadowQuantity());assertEquals(one.riskBudget(),two.riskBudget());
        ObjectNode json=support.mapper.valueToTree(c);((ObjectNode)json.get("global")).put("maxQuantity",1);var limited=support.mapper.treeToValue(json,PatternV2Configuration.class);var rejected=simulator.simulate(limited,audit,bars(PatternDirection.LONG,"TARGET").get("BBB"));assertEquals(CapitalSkipShadowAnalysis.Status.NOT_EXECUTABLE,rejected.shadowStatus());assertNull(rejected.netPnl());
    }
    @Test void otherRejectionsExcludedAndLegacyDefaultsRemainReadable() throws Exception {
        var c=config(PatternDirection.LONG).withRangeFilter(true,bd("99")).withCapitalSkipShadowAnalysis(true);var r=service(PatternDirection.LONG).runOnSnapshot(c,bars(PatternDirection.LONG,"TARGET"));assertEquals(0,r.capitalSkipShadowAnalysis().totalCapitalSkips());assertTrue(r.capitalSkipAudits().isEmpty());
        var invalid=bars(PatternDirection.LONG,"TARGET");invalid.replaceAll((s,b)->List.of(b.getFirst(),bar(s,1,98,100,97,99),b.getLast()));var risk=service(PatternDirection.LONG).runOnSnapshot(config(PatternDirection.LONG).withCapitalSkipShadowAnalysis(true),invalid);assertEquals(0,risk.capitalSkipShadowAnalysis().totalCapitalSkips());
        ObjectNode json=support.mapper.valueToTree(config(PatternDirection.LONG));json.remove("capitalSkipShadowAnalysisEnabled");assertFalse(support.mapper.treeToValue(json,PatternV2Configuration.class).capitalSkipShadowAnalysisEnabled());support.offPreservesV1ParityAndIgnoresThreshold();
    }
    @Test void separateAnalysisPersistsWithoutRewritingRunAndExcelRetainsEveryExistingSheet() throws Exception {
        var c=config(PatternDirection.LONG);var service=service(PatternDirection.LONG);var r=service.runOnSnapshot(c,bars(PatternDirection.LONG,"TARGET"));var store=new PatternV2Store(directory);var before=store.run(r.runId());var a=service.capitalSkipsAnalysis(r.runId());assertEquals(before,store.run(r.runId()));assertEquals(support.mapper.readTree(support.mapper.writeValueAsBytes(a)),support.mapper.readTree(support.mapper.writeValueAsBytes(service.result(r.runId()).capitalSkipShadowAnalysis())));
        try(var old=new XSSFWorkbook(new java.io.ByteArrayInputStream(new PatternV2ExcelExporter().export(r)));var book=new XSSFWorkbook(new java.io.ByteArrayInputStream(new PatternV2ExcelExporter().export(service.result(r.runId()))))){assertEquals(old.getNumberOfSheets()+5,book.getNumberOfSheets());for(var sheet:old)assertNotNull(book.getSheet(sheet.getSheetName()));for(String name:List.of("CAPITAL_SKIPS_SUMMARY","SHADOW_TRADES","CAPITAL_SKIPS_BY_SYMBOL","CAPITAL_SKIPS_BY_PERIOD","CAPITAL_SKIPS_CONTEXT"))assertNotNull(book.getSheet(name));assertEquals(2,book.getSheet("SHADOW_TRADES").getLastRowNum());}
        var restored=store.run(r.runId());assertEquals(2,restored.capitalSkipAudits().size());assertFalse(restored.configuration().capitalSkipShadowAnalysisEnabled());
    }
    @Test void classificationIsConservative(){var empty=new CapitalSkipShadowAnalysis.Metrics(0,0,0,0,null,null,null,null,null,null,null,null,null,null,null,null);assertEquals("Inconclusive",CapitalSkipShadowAnalysis.classify(empty,empty));}

    @Test void configuredTargetFirstAndBufferAreInherited() throws Exception {
        var c=config(PatternDirection.LONG);ObjectNode json=support.mapper.valueToTree(c);((ObjectNode)json.get("global")).put("sameBarPolicy","TARGET_FIRST").put("stopBufferTicks",2);c=support.mapper.treeToValue(json,PatternV2Configuration.class).withCapitalSkipShadowAnalysis(true);var r=service(PatternDirection.LONG).runOnSnapshot(c,bars(PatternDirection.LONG,"AMBIGUOUS"));for(var t:r.capitalSkipShadowAnalysis().trades()){assertEquals("TARGET",t.exitReason());assertEquals(98.98,t.stop().doubleValue(),1e-12);assertEquals(3,t.grossResultR().doubleValue());assertTrue(t.sameBarAmbiguous());}
    }
    @Test void closeFilterRejectsAreNotCapitalSkips() throws Exception {
        var c=config(PatternDirection.LONG).withCloseStrengthFilter(true,bd(".1")).withCapitalSkipShadowAnalysis(true);var r=service(PatternDirection.LONG).runOnSnapshot(c,bars(PatternDirection.LONG,"TARGET"));assertEquals(3,r.closeStrengthAnalysis().statistics().signalsRejected());assertEquals(0,r.capitalSkipShadowAnalysis().totalCapitalSkips());
    }
    @Test void splitAwareFeesAndOtherCostsRemainSingleCounted() throws Exception {
        ObjectNode json=support.mapper.valueToTree(config(PatternDirection.LONG));var global=(ObjectNode)json.get("global");global.put("minQuantity",40).put("transactionCostsPerShare",2);((ObjectNode)global.get("commission")).put("perShare",.5).put("minimumPerOrder",0);
        var c=support.mapper.treeToValue(json,PatternV2Configuration.class);c=c.withSymbols(c.symbols().stream().map(v->new PatternV2Configuration.Symbol(v.enabled(),v.symbol().equals("BBB")?"NVDA":v.symbol(),v.longConfig(),v.shortConfig(),null,null,null,null)).toList());
        c=c.withSplitCosts(new PatternV2SplitCosts(List.of(new PatternV2SplitCosts.Rule("NVDA",PatternV2SplitCosts.Basis.SPLIT_ADJUSTED,java.time.LocalDate.of(2010,1,1),java.time.LocalDate.of(2024,6,10),List.of(new PatternV2SplitCosts.Split(java.time.LocalDate.of(2021,7,20),bd("4")),new PatternV2SplitCosts.Split(java.time.LocalDate.of(2024,6,10),bd("10"))))))).withCapitalSkipShadowAnalysis(true);
        var input=new TreeMap<>(bars(PatternDirection.LONG,"TARGET"));input.put("NVDA",input.remove("BBB").stream().map(b->new HistoricalBar("NVDA",b.dateTime(),b.getOpen(),b.getHigh(),b.getLow(),b.getClose(),1000,b.interval(),"STOOQ")).toList());
        var r=service(PatternDirection.LONG).runOnSnapshot(c,input);var t=r.capitalSkipShadowAnalysis().trades().stream().filter(v->v.symbol().equals("NVDA")).findFirst().orElseThrow();assertEquals(60,t.shadowQuantity());assertEquals(1.5,t.totalCommission().doubleValue(),1e-12);assertEquals(6,t.transactionCosts().doubleValue(),1e-12);assertEquals(168.846,t.netPnl().doubleValue(),1e-10);
    }
}
