package it.andreademartini.trading.backtest.patternv2;

import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import it.andreademartini.trading.backtest.patternstable.PatternStableService;
import it.andreademartini.trading.backtest.patternstable.engine.StableSimulation;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.research.pattern.PatternType;
import it.andreademartini.trading.risk.*;
import java.util.*;
import java.nio.file.Path;

final class GapAuditTestSupport {
    record Fixture(PatternV2Configuration config,Map<String,List<HistoricalBar>> bars) {}
    static Fixture fixture(boolean isLong,double entryOpen,double exitOpen,double high,double low){
        String symbol=isLong?"AAPL":"TSLA";
        var base=new PatternStableService(null,null,null,null).version("2.2").configuration();
        var direction=new PatternV2Configuration.Direction(true,isLong?PatternType.BULLISH_ENGULFING:PatternType.SHOOTING_STAR,bd("3"));
        var config=base.withSymbols(List.of(new PatternV2Configuration.Symbol(true,symbol,isLong?direction:null,isLong?null:direction,null,null,null,null)));
        List<HistoricalBar> bars=new ArrayList<>();
        if(isLong)bars.addAll(engulfing(symbol).subList(0,2));
        else bars.add(bar(symbol,0,100,102,99.9,100.1));
        int index=bars.size();bars.add(bar(symbol,index,entryOpen,entryOpen+.1,entryOpen-.1,entryOpen));
        bars.add(bar(symbol,index+1,exitOpen,high,low,exitOpen));
        return new Fixture(config,Map.of(symbol,bars));
    }
    static PatternV2Run simulate(Fixture f,boolean v1){
        if(v1)return new StableSimulation().simulate(f.config(),f.bars(),UUID.randomUUID().toString());
        return new PatternV2Service(configuration(),new TradingSystemProperties(),null,detectors(),
            new PositionSizingService(new TradePricingService()),new PatternV2Store(Path.of("target/unused-audit-store"))).simulate(f.config(),f.bars());
    }
    static PatternV2Run audit(Fixture f,boolean v1){var r=simulate(f,v1);return r.withExecutionAudit(GapExecutionAudit.capture(r,f.bars()));}
    private GapAuditTestSupport(){}
}
