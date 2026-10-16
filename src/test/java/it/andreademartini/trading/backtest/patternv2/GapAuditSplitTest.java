package it.andreademartini.trading.backtest.patternv2;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import static it.andreademartini.trading.backtest.patternv2.GapAuditTestSupport.*;
import it.andreademartini.trading.backtest.patternstable.PatternStableStore;
import it.andreademartini.trading.marketdata.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GapAuditSplitTest {
    @ParameterizedTest @ValueSource(ints={2,4})
    void adjustedPricesStayInOneScaleAcrossSplitAndFeesAreAdjustedOnce(int ratio) throws Exception {
        var original=fixture(true,100,100,107,99);
        var s=original.config().symbols().getFirst();var symbol=new PatternV2Configuration.Symbol(true,"NVDA",s.longConfig(),null,null,null,null,null);
        var config=original.config().withSymbols(List.of(symbol)).withSplitCosts(new PatternV2SplitCosts(List.of(new PatternV2SplitCosts.Rule("NVDA",PatternV2SplitCosts.Basis.SPLIT_ADJUSTED,
            START,START.plusDays(3),List.of(new PatternV2SplitCosts.Split(START.plusDays(3),BigDecimal.valueOf(ratio)))))));
        var json=(com.fasterxml.jackson.databind.node.ObjectNode)PatternStableStore.JSON.valueToTree(config);
        var fees=(com.fasterxml.jackson.databind.node.ObjectNode)json.path("global").path("commission");fees.put("perShare",.5).put("minimumPerOrder",0).put("maximumPercentOrderValue",100);
        config=PatternStableStore.JSON.treeToValue(json,PatternV2Configuration.class);
        List<HistoricalBar> prices=original.bars().get("AAPL").stream().map(b->new HistoricalBar("NVDA",b.dateTime(),b.getOpen(),b.getHigh(),b.getLow(),b.getClose(),b.volume(),BarInterval.DAILY,"TEST",PriceAdjustment.ADJUSTED)).toList();
        var fixture=new Fixture(config,Map.of("NVDA",prices));
        for(boolean v1:List.of(false,true)){
            var run=audit(fixture,v1);var e=run.executions().getFirst();var t=e.trade();var a=run.executionAudit().trades().getFirst();
            assertEquals(GapExecutionAudit.GapType.NONE,a.gapExitType());assertEquals(0,bd("100").compareTo(a.exitBarOpen()));assertEquals(0,bd("106").compareTo(t.exitPrice()));
            assertEquals(0,BigDecimal.valueOf(ratio).compareTo(e.splitExecution().entryFactor()));assertEquals(0,bd("1").compareTo(e.splitExecution().exitFactor()));
            var equivalent=BigDecimal.valueOf(t.quantity()).divide(BigDecimal.valueOf(ratio));
            assertEquals(0,equivalent.compareTo(e.splitExecution().entryQuantityEquivalent()));
            assertEquals(0,equivalent.add(BigDecimal.valueOf(t.quantity())).multiply(bd("0.5")).compareTo(e.commission()));
            assertEquals(0,BigDecimal.valueOf(t.quantity()).multiply(t.entryPrice().add(t.exitPrice())).multiply(bd(".0003")).compareTo(e.slippageCost()));
            assertEquals(0,t.riskPerShare().compareTo(bd("2")));assertEquals(0,t.resultR().compareTo(bd("3")));
        }
        var researchJson=(com.fasterxml.jackson.databind.node.ObjectNode)PatternStableStore.JSON.valueToTree(config);researchJson.remove("exitFillPolicy");
        var researchConfig=PatternStableStore.JSON.treeToValue(researchJson,it.andreademartini.trading.backtest.patternv3.PatternV3Configuration.class);
        var service=new it.andreademartini.trading.backtest.patternv3.PatternV3Service(configuration(),new it.andreademartini.trading.config.TradingSystemProperties(),null,detectors(),
            new it.andreademartini.trading.risk.PositionSizingService(new it.andreademartini.trading.risk.TradePricingService()),new it.andreademartini.trading.backtest.patternv3.PatternV3Store(java.nio.file.Path.of("target/unused-split-store")));
        var research=service.simulate(researchConfig,fixture.bars());var reference=audit(fixture,false);
        assertEquals(reference.costs().commissions(),research.costs().commissions());assertEquals(reference.costs().slippage(),research.costs().slippage());
        assertEquals(reference.portfolio().finalCapital(),research.portfolio().finalCapital());assertEquals(reference.executions().getFirst().splitExecution().entryFactor(),research.executions().getFirst().splitExecution().entryFactor());
        assertEquals(GapExecutionAudit.GapType.NONE,research.executionAudit().trades().getFirst().gapExitType());
    }
}
