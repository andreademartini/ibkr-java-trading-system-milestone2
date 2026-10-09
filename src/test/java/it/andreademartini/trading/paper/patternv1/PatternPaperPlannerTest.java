package it.andreademartini.trading.paper.patternv1;
import static it.andreademartini.trading.paper.patternv1.PatternPaperModel.*;
import static it.andreademartini.trading.paper.patternv1.PatternPaperTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;

class PatternPaperPlannerTest {
    @Test void frozenSizingUsesStrategyEquityAndAllTradingCosts(){var s=ledgerSession();s.orders.clear();var p=PatternPaperLedger.capital(s,Instant.now());var record=signal("AAPL");var quote=new Quote(bd(100),bd(100),bd(100),bd(100),Instant.now(),record.entrySession,true,null,null);var result=new PatternPaperPlanner(s.frozen).prepare(record,quote,p,bd(1000000),limits());assertTrue(result.quantity()<=30);assertTrue(result.actualRiskAmount().compareTo(bd(200))<=0);assertTrue(result.entryCosts().signum()>0);assertEquals(0,record.expectedTarget.compareTo(bd(106)));}
    @Test void shortsRequireActualBorrowAndMarginVerification(){var s=ledgerSession();s.orders.clear();var day=LocalDate.now(ZoneId.of("America/New_York"));var bars=List.of(bar("TSLA",day.minusDays(2),0,99,102,98,101),bar("TSLA",day.minusDays(1),1,101,110,99,100));var planner=new PatternPaperPlanner(s.frozen);var signal=planner.signals("TSLA",bars,1).stream().filter(x->x.detection().direction().name().equals("SHORT")).findFirst().orElseThrow();var r=new SignalRecord();r.signal=signal;r.symbol="TSLA";r.entrySession=day;
        assertThrows(IllegalStateException.class,()->planner.prepare(r,new Quote(bd(100),bd(100),bd(100),bd(100),Instant.now(),day,true,null,null),PatternPaperLedger.capital(s,Instant.now()),bd(1000000),limits()));
        var d=planner.prepare(r,new Quote(bd(100),bd(100),bd(100),bd(100),Instant.now(),day,true,bd(7),bd(1.5)),PatternPaperLedger.capital(s,Instant.now()),bd(1000000),limits());assertTrue(d.quantity()<=7);assertTrue(d.requiredCapital().compareTo(bd(10000))<=0);}
    @Test void noLookAheadEntryUsesOnlySessionOpen(){var r=signal("AAPL");var session=ledgerSession();session.orders.clear();var planner=new PatternPaperPlanner(session.frozen);planner.prepare(r,new Quote(bd(100),bd(200),bd(199),bd(201),Instant.now(),r.entrySession,true,null,null),PatternPaperLedger.capital(session,Instant.now()),bd(1000000),limits());assertEquals(0,r.expectedEntry.compareTo(bd(100)));assertEquals(0,r.expectedStop.compareTo(bd(98)));}
}
