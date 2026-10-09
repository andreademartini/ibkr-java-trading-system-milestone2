package it.andreademartini.trading.backtest.patternstable.engine;
import it.andreademartini.trading.backtest.patternv2.*;

import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.research.pattern.SameBarResolution;
import it.andreademartini.trading.strategy.patternv1.PatternV1Strategy.EntryPlan;
import it.andreademartini.trading.research.pattern.PatternDirection;
import java.math.BigDecimal;

/** Pure V2 rules shared by the portfolio and independent historical observations. */
public final class PatternV2BarRules {
    private PatternV2BarRules(){}
    public record Exit(boolean stop,boolean target,boolean exitOnStop,boolean completed,String reason,BigDecimal price){}
    public static Exit exit(EntryPlan plan,HistoricalBar bar,int holding,int maximum,SameBarResolution policy){
        boolean isLong=plan.signal().detection().direction()==PatternDirection.LONG;
        boolean stop=isLong?bar.getLow().compareTo(plan.stopPrice())<=0:bar.getHigh().compareTo(plan.stopPrice())>=0;
        boolean target=isLong?bar.getHigh().compareTo(plan.takeProfitPrice())>=0:bar.getLow().compareTo(plan.takeProfitPrice())<=0;
        boolean exitOnStop=stop&&(!target||policy==SameBarResolution.STOP_FIRST);
        return new Exit(stop,target,exitOnStop,stop||target||holding>=maximum,exitOnStop?"STOP":target?"TARGET":"TIMEOUT",exitOnStop?plan.stopPrice():target?plan.takeProfitPrice():bar.getClose());
    }
    public static final class Excursions {
        public BigDecimal mfe=BigDecimal.ZERO,mae=BigDecimal.ZERO,rawMfe=BigDecimal.ZERO,rawMae=BigDecimal.ZERO;
        private static BigDecimal favorable(EntryPlan plan,BigDecimal price){return (plan.signal().detection().direction()==PatternDirection.LONG?price.subtract(plan.entryPrice()):plan.entryPrice().subtract(price)).max(BigDecimal.ZERO);}
        private static BigDecimal adverse(EntryPlan plan,BigDecimal price){return (plan.signal().detection().direction()==PatternDirection.LONG?plan.entryPrice().subtract(price):price.subtract(plan.entryPrice())).max(BigDecimal.ZERO);}
        public void measure(EntryPlan plan,HistoricalBar bar,boolean stop,boolean target){
            boolean isLong=plan.signal().detection().direction()==PatternDirection.LONG;
            var barMfe=favorable(plan,isLong?bar.getHigh():bar.getLow());var barMae=adverse(plan,isLong?bar.getLow():bar.getHigh());
            rawMfe=rawMfe.max(barMfe);rawMae=rawMae.max(barMae);
            if(stop){mfe=mfe.max(favorable(plan,bar.getOpen()).min(plan.riskPerShare().multiply(plan.signal().targetR())));mae=mae.max(plan.riskPerShare()).min(plan.riskPerShare());}
            else if(target){mfe=mfe.max(plan.riskPerShare().multiply(plan.signal().targetR()));mae=mae.max(adverse(plan,bar.getOpen()).min(plan.riskPerShare()));}
            else{mfe=mfe.max(barMfe);mae=mae.max(barMae);}
        }
    }
}
