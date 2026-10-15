package it.andreademartini.trading.backtest.patternv3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import it.andreademartini.trading.marketdata.*;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/** Exact input prices and cost basis, shared by both independent portfolio replays. */
public record PatternV3Dataset(String id,Map<String,List<Bar>> symbols) {
    public record Bar(LocalDateTime dateTime,BigDecimal open,BigDecimal high,BigDecimal low,BigDecimal close,
            long volume,BarInterval interval,String provider,PriceAdjustment priceAdjustment) {}
    public PatternV3Dataset {var sorted=new TreeMap<String,List<Bar>>();symbols.forEach((s,b)->sorted.put(s,List.copyOf(b)));symbols=Collections.unmodifiableMap(sorted);}
    public static PatternV3Dataset capture(Map<String,List<HistoricalBar>> input) {
        Map<String,List<Bar>> symbols=new TreeMap<>();input.forEach((s,bars)->symbols.put(s,bars.stream().map(b->new Bar(b.dateTime(),b.getOpen(),b.getHigh(),b.getLow(),b.getClose(),b.volume(),b.interval(),b.provider(),b.priceAdjustment())).toList()));
        try {
            var mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(symbols)));
            return new PatternV3Dataset(hash,symbols);
        }catch(Exception e){throw new IllegalStateException("Cannot snapshot V2 data",e);}
    }
    public Map<String,List<HistoricalBar>> bars() {
        Map<String,List<HistoricalBar>> result=new TreeMap<>();symbols.forEach((s,bars)->result.put(s,bars.stream().map(b->new HistoricalBar(s,b.dateTime(),b.open(),b.high(),b.low(),b.close(),b.volume(),b.interval(),b.provider(),b.priceAdjustment())).toList()));return Collections.unmodifiableMap(result);
    }
}
