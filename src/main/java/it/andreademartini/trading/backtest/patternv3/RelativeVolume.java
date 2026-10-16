package it.andreademartini.trading.backtest.patternv3;

import it.andreademartini.trading.marketdata.HistoricalBar;
import java.math.*;
import java.util.*;

/** Causal signal-timeframe volume ratio. Never adjusts prices, quantities or source volumes. */
public final class RelativeVolume {
    public enum Basis { UNVERIFIED, CONSISTENT_DATASET_UNITS }
    public record Value(Long signalVolume,int period,BigDecimal average,BigDecimal relativeVolume,String issue) {}
    private final int period;private final Basis basis;private final Deque<HistoricalBar> previous=new ArrayDeque<>();
    public RelativeVolume(int period,Basis basis){if(period<1)throw new IllegalArgumentException("RVOL period must be positive");this.period=period;this.basis=basis==null?Basis.UNVERIFIED:basis;}
    public Value onBar(HistoricalBar current){
        if(!previous.isEmpty()&&!current.timestamp().isAfter(previous.getLast().timestamp()))throw new IllegalArgumentException("RVOL bars must be chronological");
        BigDecimal average=null,ratio=null;String issue=null;
        if(current.volume()<=0)issue="CURRENT_VOLUME_NOT_AVAILABLE";
        else if(previous.size()<period)issue="RVOL_WARMUP";
        else if(previous.stream().anyMatch(b->b.volume()<=0))issue="HISTORICAL_VOLUME_NOT_AVAILABLE";
        else if(previous.stream().anyMatch(b->!b.provider().equals(current.provider())||b.priceAdjustment()!=current.priceAdjustment()))issue="MIXED_VOLUME_PROVENANCE";
        else {
            var sum=previous.stream().map(b->BigDecimal.valueOf(b.volume())).reduce(BigDecimal.ZERO,BigDecimal::add);
            average=sum.divide(BigDecimal.valueOf(period),MathContext.DECIMAL128);
            if(basis==Basis.UNVERIFIED)issue="UNVERIFIED_VOLUME_ADJUSTMENT";
            else if(sum.signum()<=0)issue="NON_POSITIVE_VOLUME_AVERAGE";
            else ratio=BigDecimal.valueOf(current.volume()).multiply(BigDecimal.valueOf(period)).divide(sum,MathContext.DECIMAL128);
        }
        previous.addLast(current);while(previous.size()>period)previous.removeFirst();
        return new Value(current.volume()>0?current.volume():null,period,average,ratio,issue);
    }
}
