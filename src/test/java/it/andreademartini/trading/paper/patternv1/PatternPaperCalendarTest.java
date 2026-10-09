package it.andreademartini.trading.paper.patternv1;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.time.*;

class PatternPaperCalendarTest {
    @Test void holidaysAndEarlyCloseComeFromBroker(){var c=PatternPaperCalendar.parse("SPY","US/Eastern","20261126:CLOSED;20261127:0930-20261127:1300;20261128:CLOSED;20261129:CLOSED;20261130:0930-20261130:1600",Instant.now());assertTrue(c.session(LocalDate.of(2026,11,26)).isEmpty());assertEquals(Instant.parse("2026-11-27T18:00:00Z"),c.session(LocalDate.of(2026,11,27)).orElseThrow().close());assertEquals(2,c.holdingBars(LocalDate.of(2026,11,27),LocalDate.of(2026,11,30)));}
    @Test void daylightSavingUsesIanaZone(){var c=PatternPaperCalendar.parse("SPY","US/Eastern","20261030:0930-20261030:1600;20261102:0930-20261102:1600",Instant.now());assertEquals(Instant.parse("2026-10-30T13:30:00Z"),c.sessions().getFirst().open());assertEquals(Instant.parse("2026-11-02T14:30:00Z"),c.sessions().getLast().open());}
    @Test void absentCoverageIsRejectedInsteadOfAssumingWeekdays(){var c=PatternPaperCalendar.parse("SPY","US/Eastern","20261009:0930-20261009:1600",Instant.now());assertThrows(IllegalStateException.class,()->c.holdingBars(LocalDate.of(2026,10,9),LocalDate.of(2026,10,12)));}
    @Test void incompleteDailyBarIsNotClosed(){var now=Instant.parse("2026-10-09T19:00:00Z");var c=PatternPaperCalendar.parse("SPY","US/Eastern","20261009:0930-20261009:1600",now);assertTrue(c.latestClosed(now).isEmpty());assertTrue(c.latestClosed(Instant.parse("2026-10-09T20:01:00Z")).isPresent());}
    @Test void unknownTimezoneAndMalformedSessionFailClosed(){assertThrows(RuntimeException.class,()->PatternPaperCalendar.parse("SPY","INVALID","20261009:0930-1600",Instant.now()));assertThrows(RuntimeException.class,()->PatternPaperCalendar.parse("SPY","US/Eastern","20261009:0930-1200,1300-1600",Instant.now()));}
}
