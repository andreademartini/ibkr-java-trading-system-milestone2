package it.andreademartini.trading.paper.patternv1;
import static it.andreademartini.trading.paper.patternv1.PatternPaperTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.time.*;

class PatternPaperShadowTest {
    @Test void sameSignalShadowIsSeparatedAndUsesStopFirstOnlyForSimulation(){var s=ledgerSession();s.orders.clear();var today=s.signals.getFirst().entrySession;var now=Instant.now();PatternPaperShadow.close(s,"AAPL",bar("AAPL",today,2,100,107,97,101),calendar("AAPL",now),now);assertEquals(1,s.shadowTrades.size());var t=s.shadowTrades.getFirst();assertEquals("SHADOW",t.mode);assertEquals("STOP",t.exitReason);assertEquals(0,t.exitPrice().compareTo(bd(98)));assertTrue(s.trades.isEmpty());assertTrue(s.executions.isEmpty());assertEquals(s.signals.getFirst().id,t.signalId);}
    @Test void barHighLowCloseCannotChangeEntrySizingAndReplayIsIdempotent(){var a=ledgerSession();a.orders.clear();var b=ledgerSession();b.orders.clear();var day=a.signals.getFirst().entrySession;var now=Instant.now();var c=calendar("AAPL",now);PatternPaperShadow.close(a,"AAPL",bar("AAPL",day,2,100,101,99,100),c,now);PatternPaperShadow.close(b,"AAPL",bar("AAPL",day,2,100,200,90,199),c,now);assertEquals(a.shadowTrades.getFirst().entered,b.shadowTrades.getFirst().entered);PatternPaperShadow.close(a,"AAPL",bar("AAPL",day,2,100,101,99,100),c,now);assertEquals(1,a.shadowTrades.size());assertEquals(1,a.shadowEquity.size());}
    @Test void shadowTimeoutIsTheFrozenThirteenBars(){var s=ledgerSession();s.orders.clear();var day=s.signals.getFirst().entrySession;var now=Instant.now();var c=calendar("AAPL",now);for(int i=0;i<13;i++)PatternPaperShadow.close(s,"AAPL",bar("AAPL",day.plusDays(i),i+2,100,101,99,100),c,now.plus(Duration.ofDays(i)));var t=s.shadowTrades.getFirst();assertEquals(13,t.holdingBars);assertEquals("TIMEOUT",t.exitReason);assertTrue(t.closed());assertEquals(0,t.exitPrice().compareTo(bd(100)));assertTrue(s.trades.isEmpty());}
}
