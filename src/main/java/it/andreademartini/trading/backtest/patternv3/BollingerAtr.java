package it.andreademartini.trading.backtest.patternv3;

import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.strategy.joeross.*;
import it.andreademartini.trading.research.pattern.PatternDirection;
import java.math.*;

/** Causal CLOSE/population Bollinger and Wilder ATR. Seed: mean of N complete TRs;
 * first bar supplies previous close only, so ATR(N) needs N+1 bars. No pre-start warm-up. */
public final class BollingerAtr {
    private static final MathContext MATH=MathContext.DECIMAL128;
    private final BollingerBandsCalculator bollinger;
    private final int period;
    private int ranges;
    private BigDecimal previousClose,seed=BigDecimal.ZERO,atr;
    public record Value(BigDecimal middle,BigDecimal upper,BigDecimal lower,BigDecimal atr) {}
    public BollingerAtr(PatternV3Configuration config) {
        period=config.atrPeriod();bollinger=new BollingerBandsCalculator(new BollingerPoint1FilterParameters(true,config.bollingerPeriod(),config.bollingerStdDev(),BollingerSource.CLOSE,Point1PriceMode.EXTREME));
    }
    public Value onBar(HistoricalBar bar) {
        var bands=bollinger.onBar(bar);
        if(previousClose!=null){var tr=trueRange(bar,previousClose);ranges++;
            if(ranges<=period){seed=seed.add(tr);if(ranges==period)atr=seed.divide(BigDecimal.valueOf(period),MATH);}
            else atr=atr.multiply(BigDecimal.valueOf(period-1)).add(tr).divide(BigDecimal.valueOf(period),MATH);
        }
        previousClose=bar.getClose();
        return new Value(bands.map(BollingerBandsValue::middle).orElse(null),bands.map(BollingerBandsValue::upper).orElse(null),bands.map(BollingerBandsValue::lower).orElse(null),atr);
    }
    public static BigDecimal trueRange(HistoricalBar bar,BigDecimal previousClose) {
        return bar.getHigh().subtract(bar.getLow()).max(bar.getHigh().subtract(previousClose).abs()).max(bar.getLow().subtract(previousClose).abs());
    }
    public static BigDecimal distance(PatternDirection direction,BigDecimal high,BigDecimal low,Value value) {
        if(value==null||value.lower()==null||value.upper()==null||value.atr()==null||value.atr().signum()<=0)return null;
        return (direction==PatternDirection.LONG?low.subtract(value.lower()):value.upper().subtract(high)).divide(value.atr(),MATH);
    }
}
