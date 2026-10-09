package it.andreademartini.trading.backtest.patternstable.engine;
import it.andreademartini.trading.backtest.patternv2.*;

import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.research.pattern.*;
import it.andreademartini.trading.strategy.patternv1.PatternV1Strategy.EntryPlan;
import it.andreademartini.trading.strategy.patternv1.PatternV1Strategy.Signal;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

/** Stateless strategy adapter over the existing Research detectors; no new pattern algorithms. */
public final class ConfiguredPatternStrategy {
    private final PatternV2Configuration configuration;
    private final Map<PatternType,PatternDetector> detectors=new EnumMap<>(PatternType.class);
    public ConfiguredPatternStrategy(PatternV2Configuration config,List<PatternDetector> detectors) {
        this.configuration=config;detectors.forEach(d->this.detectors.put(d.patternType(),d));
    }
    public List<Signal> onClosedBar(String symbol,List<HistoricalBar> history,int index) {
        var s=configuration.symbols().stream().filter(c->c.enabled()&&c.symbol().equals(symbol)).findFirst().orElse(null);
        if(s==null) return List.of();
        List<Signal> signals=new ArrayList<>();var closed=history.subList(0,index+1);
        for(var direction:PatternDirection.values()) {
            var c=s.direction(direction);if(!c.enabled()) continue;
            detectors.get(c.pattern()).detect(closed,index,PatternDetectorParameters.defaults()).ifPresent(d->{
                var low=closed.get(d.patternStartBarIndex()).getLow();var high=closed.get(d.patternStartBarIndex()).getHigh();
                for(int i=d.patternStartBarIndex()+1;i<=index;i++) { low=low.min(closed.get(i).getLow());high=high.max(closed.get(i).getHigh()); }
                signals.add(new Signal(symbol,d,c.targetR(),low,high,closed.get(index).getClose()));
            });
        }
        return List.copyOf(signals);
    }
    public EntryPlan onNextOpen(Signal signal,int index,LocalDate date,BigDecimal open) {
        if(index!=signal.detection().signalBarIndex()+1) throw new IllegalArgumentException("Entry requires next bar");
        boolean isLong=signal.detection().direction()==PatternDirection.LONG;
        var buffer=BigDecimal.valueOf(configuration.global().tickSize()).multiply(BigDecimal.valueOf(configuration.global().stopBufferTicks()));
        var stop=isLong?signal.patternLow().subtract(buffer):signal.patternHigh().add(buffer);
        var math=MathContext.DECIMAL64;
        var risk=isLong?open.subtract(stop,math):stop.subtract(open,math);
        if(open.signum()<=0||stop.signum()<=0||risk.signum()<=0) throw new IllegalArgumentException("INVALID_RISK_DISTANCE");
        var target=isLong?open.add(risk.multiply(signal.targetR(),math),math):open.subtract(risk.multiply(signal.targetR(),math),math);
        if(target.signum()<=0) throw new IllegalArgumentException("INVALID_TARGET_PRICE");
        return new EntryPlan(signal,index,date,open,stop,risk,target);
    }
}
