package it.andreademartini.trading.backtest.rebound;

import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.backtest.patternv2.BollingerAtr;
import java.util.*;

/** Causal indicator state. A missing session resets the whole warm-up. */
public final class TrendReboundIndicators {
    public record Value(Double trendSma,Double recoverySma,Double atr,List<Double> closes,boolean declining,boolean ready){}
    private final TrendReboundConfiguration c;
    private final ArrayDeque<Double> closes=new ArrayDeque<>();
    private Double previousClose,atr;private int ranges;private double seed;
    public TrendReboundIndicators(TrendReboundConfiguration c){this.c=c;}
    public Value onBar(HistoricalBar b){
        if(previousClose!=null){double tr=BollingerAtr.trueRange(b,java.math.BigDecimal.valueOf(previousClose)).doubleValue();ranges++;if(ranges<=c.atrPeriod()){seed+=tr;if(ranges==c.atrPeriod())atr=seed/c.atrPeriod();}else atr=(atr*(c.atrPeriod()-1)+tr)/c.atrPeriod();}
        previousClose=b.close();closes.addLast(b.close());int limit=Math.max(Math.max(c.trendSmaPeriod(),c.recoverySmaPeriod()),c.decliningCloses()+1);if(closes.size()>limit)closes.removeFirst();
        var xs=new ArrayList<>(closes);Double trend=sma(xs,c.trendSmaPeriod()),recovery=sma(xs,c.recoverySmaPeriod());
        var observed=List.copyOf(xs.subList(Math.max(0,xs.size()-c.decliningCloses()-1),xs.size()));boolean declining=observed.size()==c.decliningCloses()+1;
        for(int i=1;i<observed.size();i++)declining&=observed.get(i)<observed.get(i-1);
        return new Value(trend,recovery,atr,observed,declining,trend!=null&&recovery!=null&&atr!=null&&Double.isFinite(atr)&&atr>0&&observed.size()==c.decliningCloses()+1);
    }
    private static Double sma(List<Double> xs,int period){return xs.size()<period?null:xs.subList(xs.size()-period,xs.size()).stream().mapToDouble(Double::doubleValue).average().orElseThrow();}
}
