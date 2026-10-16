package it.andreademartini.trading.backtest.patternv2;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import it.andreademartini.trading.backtest.patternstable.*;
import it.andreademartini.trading.backtest.patternstable.engine.StableSimulation;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.risk.*;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GapPortfolioAccountingTest {
    @TempDir Path temp;
    PatternV2Service engine(){return new PatternV2Service(configuration(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),new PatternV2Store(temp));}
    PatternV2Configuration config(){return new PatternStableService(null,null,null,null).version().configuration();}

    @ParameterizedTest @CsvSource({"true,95,STOP", "true,109,TARGET", "false,105,STOP", "false,91,TARGET", "false,500,STOP"})
    void fullTradeUsesGapPriceAndReconcilesBothStableKernels(boolean isLong,double open,String reason) throws Exception {
        String symbol=isLong?"AAPL":"TSLA";
        var c=config();var original=c.symbols().stream().filter(s->s.symbol().equals(symbol)).findFirst().orElseThrow();
        var direction=isLong?original.longConfig():new PatternV2Configuration.Direction(true,it.andreademartini.trading.research.pattern.PatternType.SHOOTING_STAR,bd("3"));
        c=c.withSymbols(List.of(new PatternV2Configuration.Symbol(true,symbol,isLong?direction:null,isLong?null:direction,null,null,null,null)));
        List<HistoricalBar> bars=new ArrayList<>();
        if(isLong){bars.addAll(engulfing(symbol));bars.set(2,bar(symbol,2,100,101,99,100));}
        else {bars.add(bar(symbol,0,100,102,99.9,100.1));bars.add(bar(symbol,1,100,101,99,100));}
        bars.add(bar(symbol,bars.size(),open,Math.max(open,110),Math.min(open,90),open));
        var input=Map.of(symbol,bars);var v2=engine().simulate(c,input);var v1=new StableSimulation().simulate(c,input,UUID.randomUUID().toString());
        for(var run:List.of(v1,v2)){
            var t=run.portfolio().trades().getFirst();assertTrue(t.closed());assertEquals(reason,t.exitReason());assertEquals(0,BigDecimal.valueOf(open).compareTo(t.exitPrice()));assertFalse(t.sameBarAmbiguous());
            var gross=(isLong?t.exitPrice().subtract(t.entryPrice()):t.entryPrice().subtract(t.exitPrice())).multiply(BigDecimal.valueOf(t.quantity()));
            assertEquals(0,gross.compareTo(t.grossPnl()));assertEquals(0,gross.subtract(t.costs()).compareTo(t.netPnl()));
            assertEquals(0,run.costs().commissions().add(run.costs().slippage()).compareTo(t.costs()));
            assertEquals(0,bd("10000").add(t.netPnl()).compareTo(run.portfolio().finalCapital()));
            assertEquals(run.portfolio().finalCapital(),run.portfolio().finalCash());
            assertEquals(0,t.resultR().compareTo(t.researchResultR()));
            var execution=run.executions().getFirst();
            var expectedFee=c.global().commission().charge(t.quantity(),t.entryPrice()).add(c.global().commission().charge(t.quantity(),t.exitPrice()));
            assertEquals(0,expectedFee.compareTo(execution.commission()));
            var expectedSlip=t.entryPrice().add(t.exitPrice()).multiply(bd("0.0003")).multiply(BigDecimal.valueOf(t.quantity()));
            assertEquals(0,expectedSlip.compareTo(execution.slippageCost()));
            assertTrue(run.portfolio().events().stream().anyMatch(e->e.eventType().equals("EXIT_EXECUTION")&&Boolean.TRUE.equals(e.details().get("atOpen"))));
            assertTrue(run.portfolio().events().stream().noneMatch(e->e.eventType().equals("SAME_BAR_AMBIGUITY")));
        }
        assertEquals(v1.portfolio().finalCapital(),v2.portfolio().finalCapital());assertEquals(v1.costs(),v2.costs());
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(new PatternV2ExcelExporter().export(v2)))){
            var sheet=book.getSheet("CONFIGURATION");boolean found=false;
            for(var row:sheet)if(row.getCell(0)!=null&&row.getCell(0).toString().equals("exitFillPolicy")){assertEquals("OPEN_FIRST",row.getCell(1).toString());found=true;}
            assertTrue(found);assertNotNull(book.getSheet("TRADES"));
        }
    }

    @Test void correctedVersionsStillTimeoutAtThirteenthEntryInclusiveBar() {
        var c=config();var bars=new ArrayList<>(engulfing("AAPL").subList(0,2));
        for(int index=2;index<15;index++)bars.add(bar("AAPL",index,100,101,99,100));
        for(var run:List.of(engine().simulate(c,Map.of("AAPL",bars)),new StableSimulation().simulate(c,Map.of("AAPL",bars),UUID.randomUUID().toString()))){
            var t=run.portfolio().trades().getFirst();assertEquals("TIMEOUT",t.exitReason());assertEquals(13,t.holdingBars());assertEquals(START.plusDays(14),t.exitDate());assertEquals(0,bd("100").compareTo(t.exitPrice()));
        }
    }

    @Test void newVersionsChangeOnlyFillPolicyAndLegacyHashesStayStable() throws Exception {
        var svc=new PatternStableService(null,null,null,null);
        var v1old=svc.version("2.1");var v1new=svc.version("2.2");
        assertEquals("2.2",v1new.strategyVersion());assertEquals(v1old.configuration().withExitFillPolicy(ExitFillPolicy.OPEN_FIRST),v1new.configuration());
        var svc2=new it.andreademartini.trading.backtest.patternv2stable.FrozenPatternV2Service(null,null,temp);
        assertEquals("1.1",svc2.version("1.1").strategyVersion());assertEquals(svc2.version("1.0").configuration().withExitFillPolicy(ExitFillPolicy.OPEN_FIRST),svc2.version("1.1").configuration());
        for(String version:List.of("2.0","2.1")){
            try(var in=getClass().getResourceAsStream("/pattern-stable/"+version+".json")){
                var original=PatternStableStore.JSON.readTree(in);var restored=svc.version(version).configuration();
                assertFalse(PatternStableStore.JSON.valueToTree(restored).has("exitFillPolicy"));assertEquals(PatternStableStore.hash(original),PatternStableStore.hash(restored));
            }
        }
        var copied=config().withGlobal(config().global()).withName("COPY").withMarketRegimeFilter(false,14,25).withMarketRegimeAnalysis(false).withSplitCosts(config().splitCosts());
        assertEquals(ExitFillPolicy.OPEN_FIRST,copied.exitFillPolicy());
        var stableCopy=PatternStableStore.JSON.convertValue(copied,it.andreademartini.trading.backtest.patternstable.engine.PatternV2Configuration.class);
        assertEquals(ExitFillPolicy.OPEN_FIRST,stableCopy.forNewRun().exitFillPolicy());
    }
}
