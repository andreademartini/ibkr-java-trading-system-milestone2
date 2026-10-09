package it.andreademartini.trading.backtest.patternstable.engine;
import it.andreademartini.trading.backtest.patternv1.*;
import it.andreademartini.trading.backtest.patternv2.*;
import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.research.pattern.PatternDetector;
import it.andreademartini.trading.research.pattern.PatternDirection;
import java.util.*;
import java.math.*;
import java.time.*;
/** Version 2.0 frozen financial path, independent of V2 service, current configuration and stores. */
public final class StableSimulation {
 private final PositionSizingService sizing=new PositionSizingService(new TradePricingService());
 private final List<PatternDetector> detectors=List.of(new BullishEngulfingDetector(),new BullishHaramiDetector(),new HammerDetector(),new PiercingPatternDetector(),new ShootingStarDetector());
 public PatternV2Run simulate(it.andreademartini.trading.backtest.patternv2.PatternV2Configuration configuration,Map<String,List<HistoricalBar>> bars,String identity) {
        var config=it.andreademartini.trading.backtest.patternstable.PatternStableStore.JSON.convertValue(configuration,PatternV2Configuration.class).forNewRun();
        var simulation=new PatternPortfolioSimulator(sizing,detectors).run(config,bars,identity);
        var p=simulation.portfolio();var g=config.global();
        var fee=g.commissionEnabled()?g.commission():PatternV1Commission.fixed(BigDecimal.ZERO);
        var slipPercent=BigDecimal.valueOf(g.slippageEnabled()?g.slippagePercentPerSide():0);
        List<PatternV2Run.Execution> executions=new ArrayList<>();
        BigDecimal fees=BigDecimal.ZERO,slippage=BigDecimal.ZERO,other=BigDecimal.ZERO;
        for(var t:p.trades()) {
            var q=BigDecimal.valueOf(t.quantity());boolean isLong=t.direction()==PatternDirection.LONG;
            var entryBar=bars.get(t.symbol()).stream().filter(b->b.timestamp().toString().equals(p.priceSeries().get(t.symbol()).get(t.entryIndex()).timestamp())).findFirst().orElseThrow();
            var entryFactor=config.splitCosts().factor(t.symbol(),entryBar);
            var exitBar=t.closed()?bars.get(t.symbol()).stream().filter(b->b.timestamp().toString().equals(p.priceSeries().get(t.symbol()).get(t.exitIndex()).timestamp())).findFirst().orElseThrow():null;
            var exitFactor=t.closed()?config.splitCosts().factor(t.symbol(),exitBar):null;
            var commission=PatternV2SplitCosts.commission(fee,entryFactor).charge(t.quantity(),t.entryPrice())
                    .add(t.closed()?PatternV2SplitCosts.commission(fee,exitFactor).charge(t.quantity(),t.exitPrice()):BigDecimal.ZERO);
            var entrySlip=t.entryPrice().multiply(slipPercent).divide(BigDecimal.valueOf(100),java.math.MathContext.DECIMAL64);
            var exitSlip=t.closed()?t.exitPrice().multiply(slipPercent).divide(BigDecimal.valueOf(100),java.math.MathContext.DECIMAL64):BigDecimal.ZERO;
            var slipCost=entrySlip.add(exitSlip).multiply(q);
            var extra=BigDecimal.valueOf(PatternV2SplitCosts.perShare(BigDecimal.valueOf(g.transactionCostsPerShare()),entryFactor).doubleValue()).multiply(q);
            if(t.closed())extra=extra.add(BigDecimal.valueOf(PatternV2SplitCosts.perShare(BigDecimal.valueOf(g.transactionCostsPerShare()),exitFactor).doubleValue()).multiply(q));
            var prices=p.priceSeries().get(t.symbol());
            var entry=isLong?t.entryPrice().add(entrySlip):t.entryPrice().subtract(entrySlip);
            var exit=!t.closed()?null:isLong?t.exitPrice().subtract(exitSlip):t.exitPrice().add(exitSlip);
            var splitExecution=new PatternV2Run.SplitExecution(entryFactor,exitFactor,PatternV2SplitCosts.equivalentQuantity(t.quantity(),entryFactor),
                    t.closed()?PatternV2SplitCosts.equivalentQuantity(t.quantity(),exitFactor):null,t.entryPrice().multiply(entryFactor),t.closed()?t.exitPrice().multiply(exitFactor):null);
            executions.add(new PatternV2Run.Execution(t,prices.get(t.entryIndex()).timestamp(),t.closed()?prices.get(t.exitIndex()).timestamp():null,entry,exit,commission,slipCost,extra,splitExecution,new PatternRange(t.patternHigh(),t.patternLow(),t.patternClose()),CloseStrengthAnalysis.value(t),true,simulation.capitalLimitsAudits().get(t.tradeId()),simulation.bollingerAtrSignals().stream().filter(a->a.symbol().equals(t.symbol())&&a.direction().equals(t.direction().name())&&a.pattern().equals(t.pattern().name())&&a.signalIndex()==t.signalIndex()).findFirst().orElse(null)));
            fees=fees.add(commission);slippage=slippage.add(slipCost);other=other.add(extra);
            if(t.costs().compareTo(commission.add(slipCost).add(extra))!=0)throw new IllegalStateException("Cost reconciliation failed: "+t.tradeId());
        }
        var result = new PatternV2Run(p.backtestId(),"PATTERN_V1",Instant.now(),configuration,p,simulation.equityTimestamps(),List.copyOf(executions),PatternV2Analysis.calculate(p,null,null),new PatternV2Run.Costs(fees,slippage,other),null,simulation.signals(),null,null,null,simulation.closeStrengthSignals(),null);
        result=result.withBollingerAtr(simulation.bollingerAtrSignals(),null);result=result.withBollingerAtr(simulation.bollingerAtrSignals(),BollingerAtrAnalysis.calculate(result));
        result=result.withCapitalSkipAudits(simulation.capitalSkipAudits()).withResearch(PatternRangeAnalysis.calculate(result,null),null,null).withCloseStrengthResearch(CloseStrengthAnalysis.calculate(result,null));
        if(config.capitalSkipShadowAnalysisEnabled()){var source=result.withResearch(result.patternRangeAnalysis(),PatternV2Dataset.capture(bars).id(),null);result=result.withCapitalSkipShadowAnalysis(new CapitalSkipShadowAnalysis(new it.andreademartini.trading.risk.PositionSizingService(new it.andreademartini.trading.risk.TradePricingService())).analyze(source,bars));}
        return result;
    }
}
