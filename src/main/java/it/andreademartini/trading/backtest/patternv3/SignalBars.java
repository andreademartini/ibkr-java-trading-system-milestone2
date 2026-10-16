package it.andreademartini.trading.backtest.patternv3;

import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.research.pattern.PatternDetectionResult;
import it.andreademartini.trading.strategy.patternv1.PatternV1Strategy.Signal;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Immutable signal view with explicit mapping back to DAILY execution indices.
 * Aggregation is offline, but a completed bar is exposed only at its final session. */
public final class SignalBars {
    private final List<HistoricalBar> bars;
    private final List<Integer> starts,ends;
    private final Map<Integer,Integer> confirmations;
    private SignalBars(List<HistoricalBar> bars,List<Integer> starts,List<Integer> ends){
        this.bars=List.copyOf(bars);this.starts=List.copyOf(starts);this.ends=List.copyOf(ends);
        var map=new HashMap<Integer,Integer>();for(int i=0;i<ends.size();i++)map.put(ends.get(i),i);confirmations=Map.copyOf(map);
    }
    public List<HistoricalBar> bars(){return bars;}
    public int atDailyIndex(int index){return confirmations.getOrDefault(index,-1);}
    public static SignalBars of(List<HistoricalBar> daily,SignalTimeframe timeframe,List<String> warnings){
        var bars=new ArrayList<HistoricalBar>();var starts=new ArrayList<Integer>();var ends=new ArrayList<Integer>();
        if(timeframe==SignalTimeframe.DAILY){for(int i=0;i<daily.size();i++){bars.add(daily.get(i));starts.add(i);ends.add(i);}return new SignalBars(bars,starts,ends);}
        var weeks=new LinkedHashMap<LocalDate,List<Integer>>();
        for(int i=0;i<daily.size();i++){var date=daily.get(i).dateTime().toLocalDate();weeks.computeIfAbsent(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),ignored->new ArrayList<>()).add(i);}
        for(var entry:weeks.entrySet()){
            var indices=entry.getValue();var expected=ResearchSessionCalendar.weekSessions(entry.getKey());
            var dates=indices.stream().map(i->daily.get(i).dateTime().toLocalDate()).toList();
            if(!dates.equals(expected)){warnings.add("WEEKLY_INCOMPLETE: "+(daily.isEmpty()?"":daily.getFirst().symbol())+" "+entry.getKey()+" expected="+expected+" available="+dates);continue;}
            var first=daily.get(indices.getFirst());var last=daily.get(indices.getLast());
            BigDecimal high=first.getHigh(),low=first.getLow();long volume=0;
            for(int i:indices){var b=daily.get(i);if(b.priceAdjustment()!=first.priceAdjustment()||!b.provider().equals(first.provider()))throw new IllegalArgumentException("Mixed WEEKLY price provenance: "+entry.getKey());high=high.max(b.getHigh());low=low.min(b.getLow());volume=Math.addExact(volume,b.volume());}
            // HistoricalBar has no WEEKLY enum: this is an internal signal view; never passed to execution or providers.
            bars.add(new HistoricalBar(bars.size(),first.symbol(),last.timestamp(),entry.getKey().toString(),last.dateTime(),first.getOpen(),high,low,last.getClose(),volume,BarInterval.DAILY,first.provider(),first.priceAdjustment()));
            starts.add(indices.getFirst());ends.add(indices.getLast());
        }
        return new SignalBars(bars,starts,ends);
    }
    public Signal toDaily(Signal signal){
        var d=signal.detection();
        var converted=new PatternDetectionResult(d.patternType(),d.direction(),d.signalDate(),ends.get(d.signalBarIndex()),starts.get(d.patternStartBarIndex()),d.referencePrice(),d.confidenceMetadata());
        return new Signal(signal.symbol(),converted,signal.targetR(),signal.patternLow(),signal.patternHigh(),signal.patternClose());
    }
}
