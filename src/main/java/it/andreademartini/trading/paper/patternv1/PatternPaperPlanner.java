package it.andreademartini.trading.paper.patternv1;

import static it.andreademartini.trading.paper.patternv1.PatternPaperModel.*;
import it.andreademartini.trading.backtest.patternstable.PatternStableService;
import it.andreademartini.trading.backtest.patternstable.engine.*;
import it.andreademartini.trading.config.TradingSystemProperties.RiskProperties;
import it.andreademartini.trading.research.pattern.PatternDirection;
import it.andreademartini.trading.strategy.patternv1.PatternV1Properties;
import java.math.*;
import java.time.*;
import java.util.*;

public final class PatternPaperPlanner {
    private final PatternStableService.Version frozen;
    private final ConfiguredPatternStrategy strategy;
    private final PatternV1PositionSizing sizing=new PatternV1PositionSizing(new PositionSizingService(new TradePricingService()));
    public PatternPaperPlanner(PatternStableService.Version frozen){
        this.frozen=frozen;
        strategy=new ConfiguredPatternStrategy(copyConfiguration(),List.of(new BullishEngulfingDetector(),new BullishHaramiDetector(),new HammerDetector(),new PiercingPatternDetector(),new ShootingStarDetector()));
        if(frozen.configuration().global().useLeverage())throw new IllegalStateException("PAPER V1 vieta la leva");
    }
    private PatternV2Configuration copyConfiguration(){return it.andreademartini.trading.backtest.patternstable.PatternStableStore.JSON.convertValue(frozen.configuration(),PatternV2Configuration.class);}
    public List<it.andreademartini.trading.strategy.patternv1.PatternV1Strategy.Signal> signals(String symbol,List<it.andreademartini.trading.marketdata.HistoricalBar> bars,int index){return strategy.onClosedBar(symbol,bars,index);}
    public PatternV1PositionSizing.Decision prepare(SignalRecord record,Quote quote,EquityPoint capital,BigDecimal brokerFunds,Limits limits){
        if(quote==null||!quote.realtime()||quote.open()==null||quote.open().signum()<=0||!record.entrySession.equals(quote.sessionDate()))throw new IllegalStateException("Prezzo OPEN della sessione mancante/non realtime");
        var signal=record.signal;var config=frozen.configuration();var g=config.global();
        var symbol=config.symbols().stream().filter(s->s.symbol().equals(signal.symbol())).findFirst().orElseThrow();
        var plan=strategy.onNextOpen(signal,signal.detection().signalBarIndex()+1,record.entrySession,quote.open());
        boolean shortSide=signal.detection().direction()==PatternDirection.SHORT;
        if(shortSide&&(quote.shortableShares()==null||quote.shortableShares().signum()<=0||quote.shortMargin()==null||quote.shortMargin().signum()<=0))throw new IllegalStateException("SHORT: prestito titoli e margine non verificati");
        var risk=new RiskProperties();var equity=capital.equity();
        risk.setTradingCapital(equity.doubleValue());risk.setRiskPerTradePercent(symbol.riskPercent()==null?g.riskPercent():symbol.riskPercent());
        risk.setMaxOrderValue(equity.multiply(BigDecimal.valueOf(symbol.maxOrderValuePercent()==null?g.maxOrderValuePercent():symbol.maxOrderValuePercent())).divide(BigDecimal.valueOf(100)).doubleValue());
        risk.setMaxPositionValue(equity.multiply(BigDecimal.valueOf(g.maxPositionValuePercent()/100)).doubleValue());
        risk.setMaxLongNotional(equity.multiply(BigDecimal.valueOf(g.maxLongNotionalPercent()/100)).doubleValue());
        risk.setMaxShortNotional(equity.multiply(BigDecimal.valueOf(g.maxShortNotionalPercent()/100)).doubleValue());
        risk.setMaxQuantityPerOrder(symbol.maxQuantity()==null?g.maxQuantity():symbol.maxQuantity());risk.setUseLeverage(false);risk.setTransactionCostsPerShare(g.transactionCostsPerShare());
        if(shortSide)risk.setMaxQuantityPerOrder(Math.min(risk.getMaxQuantityPerOrder(),quote.shortableShares().setScale(0,RoundingMode.DOWN).longValueExact()));
        var properties=new PatternV1Properties();properties.setMinQuantity(g.minQuantity());properties.setMinOrderValue(BigDecimal.valueOf(g.minOrderValue()));
        var exposureBudget=equity.multiply(limits.maxAggregateExposurePercent()).divide(BigDecimal.valueOf(100)).subtract(capital.longExposure().add(capital.shortExposure())).max(BigDecimal.ZERO);
        var available=capital.available().min(brokerFunds).min(exposureBudget);
        if(shortSide)available=available.divide(quote.shortMargin().max(BigDecimal.ONE),MathContext.DECIMAL64);
        var decision=sizing.calculate(plan,equity,available,risk,properties,g.commission(),BigDecimal.valueOf(g.slippagePercentPerSide()));
        record.expectedEntry=plan.entryPrice();record.expectedStop=plan.stopPrice();record.expectedTarget=plan.takeProfitPrice();record.expectedQuantity=decision.quantity();
        return decision;
    }
}
