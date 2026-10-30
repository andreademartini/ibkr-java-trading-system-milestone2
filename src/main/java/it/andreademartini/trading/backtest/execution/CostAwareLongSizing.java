package it.andreademartini.trading.backtest.execution;

import it.andreademartini.trading.risk.*;
import it.andreademartini.trading.backtest.patternv1.PatternV1Commission;
import java.math.BigDecimal;

/** Adapter around the existing sizing engine, with actual commissions and opening cash constraints. */
public final class CostAwareLongSizing {
    private final PositionSizingService sizing;
    public CostAwareLongSizing(PositionSizingService sizing){this.sizing=sizing;}
    public record Result(long quantity,double entryFee,double riskWithCosts){}
    private static BigDecimal bd(double n){return BigDecimal.valueOf(n);}
    public Result calculate(double equity,double cash,double existingNotional,double openingPrice,double execution,
            double stop,double riskPercent,double maxPositionPercent,double maxExposurePercent,double slipPercent,PatternV1Commission commission){
        double perShareRisk=execution-stop,stopSlip=Slippage.perShare(bd(stop),bd(slipPercent)).doubleValue();
        double limit=Math.min(equity*maxPositionPercent/100,Math.min(cash,equity*maxExposurePercent/100-existingNotional));
        long upper=0;if(equity>0&&limit>0)try{upper=sizing.calculate(bd(equity),bd(riskPercent),bd(execution),bd(stop),bd(stopSlip),BigDecimal.ZERO,bd(limit),false,1_000_000_000L).quantity();}catch(RiskRejectedException|IllegalArgumentException ignored){}
        long lo=0,hi=upper;
        while(lo<hi){long q=lo+(hi-lo)/2+(hi-lo)%2;double fee=commission.charge(q,bd(execution)).doubleValue(),exitFee=commission.charge(q,bd(stop)).doubleValue();
            double postEquity=equity-fee-(execution-openingPrice)*q;
            boolean fits=execution*q+fee<=cash+1e-9&&(perShareRisk+stopSlip)*q+fee+exitFee<=equity*riskPercent/100+1e-9
                    &&execution*q<=postEquity*maxPositionPercent/100+1e-9&&existingNotional+execution*q<=postEquity*maxExposurePercent/100+1e-9;
            if(fits)lo=q;else hi=q-1;
        }
        double fee=commission.charge(lo,bd(execution)).doubleValue();return new Result(lo,fee,(perShareRisk+stopSlip)*lo+fee+commission.charge(lo,bd(stop)).doubleValue());
    }
}
