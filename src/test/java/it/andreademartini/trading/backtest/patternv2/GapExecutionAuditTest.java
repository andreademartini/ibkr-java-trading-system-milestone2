package it.andreademartini.trading.backtest.patternv2;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import static it.andreademartini.trading.backtest.patternv2.GapAuditTestSupport.*;
import it.andreademartini.trading.backtest.patternstable.PatternStableStore;
import it.andreademartini.trading.research.pattern.PatternDirection;
import java.math.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GapExecutionAuditTest {
    @TempDir Path temp;
    @ParameterizedTest @CsvSource({
        "true,95,110,90,GAP_STOP", "true,109,112,90,GAP_TARGET",
        "false,105,110,90,GAP_STOP", "false,91,110,88,GAP_TARGET",
        "true,98,110,90,GAP_STOP", "true,106,110,90,GAP_TARGET",
        "false,102,110,90,GAP_STOP", "false,94,110,90,GAP_TARGET",
        "true,100,107,97,NONE", "false,100,103,93,NONE",
        "true,100,107,99,NONE", "false,100,101,93,NONE",
        "true,85,110,80,GAP_STOP", "false,115,120,90,GAP_STOP",
        "true,115,120,90,GAP_TARGET", "false,85,110,80,GAP_TARGET"
    })
    void gapFormulasSlippageAndEconomicsAreExactInBothKernels(boolean isLong,double open,double high,double low,String type) throws Exception {
        for(boolean v1:List.of(false,true)){
            var f=fixture(isLong,100,open,high,low);var run=audit(f,v1);var e=run.executions().getFirst();var t=e.trade();var a=run.executionAudit().trades().getFirst();
            assertEquals(type,a.gapExitType().name());assertEquals(0,BigDecimal.valueOf(open).compareTo(a.exitBarOpen()));assertNull(a.issue());
            assertEquals(0,(isLong?bd("100.03"):bd("99.97")).compareTo(e.entryPriceAfterSlippage()));
            var exitFactor=isLong?bd("0.9997"):bd("1.0003");assertEquals(0,t.exitPrice().multiply(exitFactor).compareTo(a.exitExecutionPrice()));
            BigDecimal q=BigDecimal.valueOf(t.quantity());
            var executionGross=(isLong?a.exitExecutionPrice().subtract(e.entryPriceAfterSlippage()):e.entryPriceAfterSlippage().subtract(a.exitExecutionPrice())).multiply(q);
            assertEquals(0,executionGross.subtract(e.commission()).subtract(e.transactionCosts()).compareTo(t.netPnl()),"No duplicate slippage");
            assertEquals(0,a.exitSlippageAmount().compareTo(t.exitPrice().multiply(bd("0.0003"))));
            if(!type.equals("NONE")){
                var distance=a.exitBarOpen().subtract(a.triggerLevel()).abs();assertFalse(t.sameBarAmbiguous());
                assertEquals(0,GapExecutionAudit.ratio(distance.multiply(bd("100")),a.triggerLevel().abs()).compareTo(a.gapSizePercent()));
                assertEquals(0,GapExecutionAudit.ratio(distance,e.entryPriceAfterSlippage().subtract(t.stopPrice()).abs()).compareTo(a.gapSizeR()));
                assertEquals(0,GapExecutionAudit.ratio(distance,t.riskPerShare()).compareTo(a.gapSizeRPriceBasis()));
                if(open==85||open==115)assertTrue(t.resultR().abs().compareTo(bd("5"))>0);
            }else{assertNull(a.gapSizePercent());assertNull(a.gapSizeR());assertNull(a.gapSizeRPriceBasis());assertFalse(a.gapExitFlag());}
            var store=new PatternStableStore(temp.resolve(UUID.randomUUID().toString()));store.immutable("simulation.json",run);assertEquals(run.executionAudit(),store.read("simulation.json",PatternV2Run.class).executionAudit());
            var copied=run.withResearch(run.patternRangeAnalysis(),"dataset","pair").withBenchmark(null).withMarketRegimeAnalysis(run.marketRegimeAnalysis()).withMarketRegimeFilterAnalysis(run.marketRegimeFilterAnalysis())
                .withBollingerAtr(run.bollingerAtrSignals(),run.bollingerAtrAnalysis()).withCapitalSkipAudits(run.capitalSkipAudits()).withCapitalSkipShadowAnalysis(null).withCloseStrengthResearch(run.closeStrengthAnalysis());
            assertEquals(run.executionAudit(),copied.executionAudit());
        }
    }
    @ParameterizedTest @CsvSource({"true,108,98,10", "false,92,102,10"})
    void entryGapRecalculatesBaseRiskTargetAndSizingWithoutRetroactiveExit(boolean isLong,double entry,String stop,String risk) {
        for(boolean v1:List.of(false,true)){
            var f=fixture(isLong,entry,entry,entry+.1,entry-.1);var run=audit(f,v1);var t=run.portfolio().trades().getFirst();var e=run.executions().getFirst();
            assertEquals(0,BigDecimal.valueOf(entry).compareTo(t.entryPrice()));assertEquals(0,bd(stop).compareTo(t.stopPrice()));assertEquals(0,bd(risk).compareTo(t.riskPerShare()));
            assertEquals(0,(isLong?BigDecimal.valueOf(entry).add(bd(risk).multiply(bd("3"))):BigDecimal.valueOf(entry).subtract(bd(risk).multiply(bd("3")))).compareTo(t.takeProfitPrice()));
            assertEquals(0,BigDecimal.valueOf(entry).multiply(isLong?bd("1.0003"):bd("0.9997")).compareTo(e.entryPriceAfterSlippage()));
            assertEquals(1,t.entryIndex()-t.signalIndex());assertFalse(t.closed());assertEquals(2,t.holdingBars());assertTrue(run.executionAudit().trades().isEmpty());
            assertTrue(t.actualRiskAmount().compareTo(bd("200"))<=0);assertTrue(t.orderValue().compareTo(bd("3000"))<=0);
            assertTrue(run.portfolio().events().stream().noneMatch(event->event.eventType().equals("EXIT_EXECUTION")));
        }
    }
    @ParameterizedTest @CsvSource({"true,97", "true,98", "false,102", "false,103"})
    void invalidEntryDistanceIsRejectedBeforeOpening(boolean isLong,double entry) {
        for(boolean v1:List.of(false,true)){
            var f=fixture(isLong,entry,entry,entry+.1,entry-.1);var run=audit(f,v1);
            assertTrue(run.executions().isEmpty());assertEquals(0,bd("10000").compareTo(run.portfolio().finalCapital()));
            assertTrue(run.portfolio().events().stream().anyMatch(e->e.eventType().equals("SIGNAL_SKIPPED_INVALID_RISK")&&"INVALID_RISK_DISTANCE".equals(e.details().get("reason"))));
        }
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
    void entryBarMayStopIntradayButIsNeverAnOpeningGap(boolean isLong){
        var f=fixture(isLong,100,100,101,99);String symbol=isLong?"AAPL":"TSLA";int entryIndex=isLong?2:1;
        var bars=new ArrayList<>(f.bars().get(symbol));bars.set(entryIndex,bar(symbol,entryIndex,100,110,90,100));
        f=new Fixture(f.config(),Map.of(symbol,bars));
        for(boolean v1:List.of(false,true)){var r=audit(f,v1);var t=r.executions().getFirst().trade();assertEquals(t.entryIndex(),t.exitIndex());assertEquals("STOP",t.exitReason());assertTrue(t.sameBarAmbiguous());assertEquals(GapExecutionAudit.GapType.NONE,r.executionAudit().trades().getFirst().gapExitType());}
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
    void entryBarTargetIsAnIntradayFillAndLimitModelDivergenceIsExplicit(boolean isLong){
        var f=fixture(isLong,100,100,101,99);String symbol=isLong?"AAPL":"TSLA";int entryIndex=isLong?2:1;
        var bars=new ArrayList<>(f.bars().get(symbol));bars.set(entryIndex,bar(symbol,entryIndex,100,isLong?107:101,isLong?99:93,100));
        f=new Fixture(f.config(),Map.of(symbol,bars));
        for(boolean v1:List.of(false,true)){
            var r=audit(f,v1);var e=r.executions().getFirst();var t=e.trade();assertEquals(t.entryIndex(),t.exitIndex());assertEquals("TARGET",t.exitReason());assertFalse(t.sameBarAmbiguous());
            assertEquals(GapExecutionAudit.GapType.NONE,r.executionAudit().trades().getFirst().gapExitType());
            assertTrue(isLong?e.exitPriceAfterSlippage().compareTo(t.takeProfitPrice())<0:e.exitPriceAfterSlippage().compareTo(t.takeProfitPrice())>0);
            assertTrue(r.executionAudit().limitations().stream().anyMatch(s->s.contains("Targets in PAPER are LMT")));
        }
    }
    @Test void invalidDenominatorsReturnUnavailableInsteadOfNonFinite(){
        assertNull(GapExecutionAudit.ratio(bd("1"),null));assertNull(GapExecutionAudit.ratio(bd("1"),bd("0")));assertNull(GapExecutionAudit.ratio(bd("1"),bd("-1")));
    }
    @Test void auditDoesNotChangeAnyFinancialOrConfigurationField() throws Exception {
        var f=fixture(true,100,95,110,90);var before=simulate(f,false);var after=before.withExecutionAudit(GapExecutionAudit.capture(before,f.bars()));
        var old=PatternStableStore.JSON.valueToTree(before);var next=(com.fasterxml.jackson.databind.node.ObjectNode)PatternStableStore.JSON.valueToTree(after);next.remove("executionAudit");
        assertTrue(old.equals(next));assertFalse(old.has("executionAudit"));assertEquals(before.configuration(),after.configuration());
    }
}
