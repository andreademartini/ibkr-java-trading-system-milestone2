package it.andreademartini.trading.backtest.patternv3;

import it.andreademartini.trading.marketdata.HistoricalBar;
import java.math.BigDecimal;

/** Causal Wilder ADX/DI (TA-Lib seed convention), existing Wilder ATR, SMA-seeded EMA. */
public final class MarketRegimeIndicators {
    public record Value(Double adx14,Double plusDI14,Double minusDI14,Double ema20,Double ema50,Double ema200,Double atr14,Double close) {
        public Value {
            adx14=valid(adx14);plusDI14=valid(plusDI14);minusDI14=valid(minusDI14);
            ema20=valid(ema20);ema50=valid(ema50);ema200=valid(ema200);atr14=valid(atr14);close=valid(close);
        }
        private static Double valid(Double value){return value!=null&&Double.isFinite(value)?value:null;}
        public boolean complete(){return adx14!=null&&plusDI14!=null&&minusDI14!=null&&ema20!=null&&ema50!=null&&ema200!=null&&atr14!=null;}
    }
    private final Ema fast=new Ema(20),medium=new Ema(50),slow=new Ema(200);
    private final BollingerAtr atr;
    private final int period;
    private HistoricalBar previous;
    private int transitions,dxCount;
    private double trSum,plusSum,minusSum,dxSeed;
    private Double adx;
    public MarketRegimeIndicators(PatternV3Configuration configuration){this(configuration,14);}
    public MarketRegimeIndicators(PatternV3Configuration configuration,int period){if(period<2)throw new IllegalArgumentException("ADX period must be >= 2");this.period=period;atr=new BollingerAtr(configuration.withBollingerAtr(false,20,new BigDecimal("2"),14,new BigDecimal(".5")));}
    public Value onBar(HistoricalBar bar){
        if(previous!=null&&!bar.timestamp().isAfter(previous.timestamp()))throw new IllegalArgumentException("Indicator history must be strictly chronological");
        double close=bar.getClose().doubleValue();Double plus=null,minus=null;
        if(previous!=null){
            transitions++;
            double tr=BollingerAtr.trueRange(bar,previous.getClose()).doubleValue();
            double up=bar.getHigh().subtract(previous.getHigh()).doubleValue(),down=previous.getLow().subtract(bar.getLow()).doubleValue();
            double positive=up>0&&up>down?up:0,negative=down>0&&down>up?down:0;
            if(transitions<period){trSum+=tr;plusSum+=positive;minusSum+=negative;}
            else{
                trSum=trSum-trSum/period+tr;plusSum=plusSum-plusSum/period+positive;minusSum=minusSum-minusSum/period+negative;
                plus=trSum==0?0:100*plusSum/trSum;minus=trSum==0?0:100*minusSum/trSum;
                double dx=plus+minus==0?0:100*Math.abs(plus-minus)/(plus+minus);
                if(dxCount<period){dxSeed+=dx;dxCount++;if(dxCount==period)adx=dxSeed/period;}
                else adx=(adx*(period-1)+dx)/period;
            }
        }
        previous=bar;var value=atr.onBar(bar);
        return new Value(adx,plus,minus,fast.next(close),medium.next(close),slow.next(close),value.atr()==null?null:value.atr().doubleValue(),close);
    }
    static final class Ema {
        private final int period;private int count;private double seed;private Double value;
        Ema(int period){this.period=period;}
        Double next(double close){if(count<period){seed+=close;if(++count==period)value=seed/period;}else value+=(close-value)*2/(period+1);return value;}
    }
}
