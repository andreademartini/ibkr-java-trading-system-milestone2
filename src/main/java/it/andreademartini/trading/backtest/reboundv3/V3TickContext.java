package it.andreademartini.trading.backtest.reboundv3;

import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.marketdata.ticks.MinimumTickResolver;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

/** Run-bound price increment policy. Research precision is explicitly NOT a verified market tick. */
final class V3TickContext {
    private final TrendReboundV3Configuration c;
    private final String dataset;
    private final MinimumTickResolver verified;
    private final Map<String,NavigableMap<LocalDate,Integer>> precision=new HashMap<>();
    V3TickContext(TrendReboundV3Configuration c,Map<String,List<HistoricalBar>> data,String dataset){
        c.validate();this.c=c;this.dataset=dataset;
        if(!V3TickSupport.isResearch(c)){verified=V3TickSupport.resolver(c);return;}
        verified=null;
        data.forEach((symbol,bars)->{
            int scale=0;var states=new TreeMap<LocalDate,Integer>();
            for(var b:bars.stream().sorted(Comparator.comparing(HistoricalBar::dateTime)).toList()){
                scale=Math.max(scale,Math.max(Math.max(b.getOpen().stripTrailingZeros().scale(),b.getHigh().stripTrailingZeros().scale()),Math.max(b.getLow().stripTrailingZeros().scale(),b.getClose().stripTrailingZeros().scale())));
                states.put(b.dateTime().toLocalDate(),scale);
            }
            precision.put(symbol,states);
        });
    }
    MinimumTickResolver.Resolution resolve(String symbol,HistoricalBar signal,LocalDate entryDate){
        if(signal.getHigh().signum()<=0)throw new IllegalArgumentException(symbol+": NON_POSITIVE_REFERENCE_PRICE");
        if(verified!=null)return verified.resolve(symbol,entryDate,signal.getHigh(),signal.priceAdjustment(),dataset,c.entryBufferTicks());
        var states=precision.get(symbol);var state=states==null?null:states.floorEntry(signal.dateTime().toLocalDate());
        if(state==null)throw new IllegalArgumentException(symbol+": CSV_PRECISION_UNAVAILABLE");
        BigDecimal quantum=BigDecimal.ONE.scaleByPowerOfTen(-state.getValue());
        BigDecimal trigger=signal.getHigh().divide(quantum,0,RoundingMode.FLOOR).add(BigDecimal.valueOf(c.entryBufferTicks())).multiply(quantum);
        if(!Double.isFinite(trigger.doubleValue())||trigger.doubleValue()<=signal.high())throw new IllegalArgumentException(symbol+": CSV_GRID_BELOW_EXECUTION_MODEL_PRECISION");
        return new MinimumTickResolver.Resolution(symbol,"UNVERIFIED","UNVERIFIED","UNVERIFIED",entryDate,signal.getHigh(),quantum,
                "CSV_DECIMAL_GRID_NOT_MARKET_TICK","CSV_GRID/1:"+symbol+":scale"+state.getValue(),signal.priceAdjustment().name(),
                "UNVERIFIED_TRADABILITY_RESEARCH_ONLY",trigger,"RESEARCH_ASSUMPTION",null);
    }
}
