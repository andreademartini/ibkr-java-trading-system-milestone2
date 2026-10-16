package it.andreademartini.trading.paper.multi;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import it.andreademartini.trading.paper.patternv2.*;
import it.andreademartini.trading.paper.patternv1.*;
import it.andreademartini.trading.backtest.patternv2stable.FrozenPatternV2Service;
import it.andreademartini.trading.marketdata.HistoricalBar;
import java.util.*;
import org.junit.jupiter.api.Test;

class PatternV2PaperPlannerTest {
    @Test void frozenAdxDecisionIsCausalAndDoesNotChangeV1(){
        var frozen=new FrozenPatternV2Service(null,null,java.nio.file.Path.of("unused")).version("1.0");var planner=new PatternV2PaperPlanner(frozen);
        var bars=new ArrayList<HistoricalBar>();for(int n=0;n<40;n++)bars.add(bar("AAPL",n,100+n,101+n,99+n,100+n));bars.add(bar("AAPL",40,140,141,138,139));bars.add(bar("AAPL",41,138.5,144,138,143));
        var signal=planner.signals("AAPL",bars,41).getFirst();var audit=planner.regime(signal,bars,41);assertFalse(audit.accepted());assertEquals("MARKET_REGIME_WITH_TREND",audit.rejectionReason());bars.add(bar("AAPL",42,100,150,1,2));assertEquals(audit,planner.regime(signal,bars,41));
        var v1=new it.andreademartini.trading.backtest.patternstable.PatternStableService(null,null,null,null).version("2.1");assertFalse(v1.configuration().marketRegimeFilterEnabled());assertEquals(13,v1.configuration().global().maxHoldingBars());assertEquals("2.1",v1.strategyVersion());assertNotEquals(PatternPaperService.signalId("2.1",signal),PatternV2PaperService.signalId("1.0",signal));
    }
    @Test void missingIndicatorsAreRejectedAndShadowDoesNotInventTheRejectedTrade(){
        var frozen=new FrozenPatternV2Service(null,null,java.nio.file.Path.of("unused")).version("1.0");var planner=new PatternV2PaperPlanner(frozen);var bars=engulfing("AAPL");var signal=planner.signals("AAPL",bars,1).getFirst();assertEquals("INSUFFICIENT_REGIME_DATA",planner.regime(signal,bars,1).rejectionReason());
        var s=new PatternPaperModel.Session();s.frozen=frozen;s.strategyVersion="1.0";var record=new PatternPaperModel.SignalRecord();record.id="rejected";record.symbol="AAPL";record.signal=signal;record.mode="SHADOW";record.status="REJECTED";record.reason="INSUFFICIENT_REGIME_DATA";var day=bars.get(2).getDateTime().toLocalDate();record.entrySession=day;s.signals.add(record);var open=day.atTime(9,30).atZone(java.time.ZoneId.of("America/New_York")).toInstant();var calendar=new PatternPaperCalendar("AAPL","America/New_York",java.time.Instant.now(),Set.of(day),List.of(new PatternPaperCalendar.Session(day,open,open.plusSeconds(23400))));PatternV2PaperShadow.closeBatch(s,Map.of("AAPL",bars.get(2)),Map.of("AAPL",calendar),open.plusSeconds(23401));assertTrue(s.shadowTrades.isEmpty());assertTrue(s.trades.isEmpty());assertEquals("REJECTED",record.status);
    }
}
