package it.andreademartini.trading.paper.multi;

import static it.andreademartini.trading.paper.patternv1.PatternPaperModel.*;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;

class PaperComparisonTest {
    final Instant start=Instant.parse("2026-10-01T14:00:00Z");
    EquityPoint point(Instant time,long equity,String mode){return new EquityPoint(time,mode,BigDecimal.valueOf(equity),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.valueOf(equity),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);}
    Session session(){var s=new Session();s.startedAt=start;s.activationDate=LocalDate.of(2026,10,1);s.equityCurve.add(point(start,10000,"PAPER"));return s;}
    @Test void comparesOnlyCommonActualPaperWindowWithoutPaddingOrShadowPnl(){var a=session();var b=session();a.equityCurve.add(point(start.plusSeconds(3600),10050,"PAPER"));b.equityCurve.add(point(start.plusSeconds(1800),9990,"PAPER"));b.equityCurve.add(point(start.plusSeconds(3600),9980,"PAPER"));a.shadowEquity.add(point(start.plusSeconds(3600),999999,"SHADOW"));var c=PaperDeskService.compare(a,b,null,null);assertTrue(c.available());assertEquals(start,c.from());assertEquals(2,c.curves().get("PATTERN_V1").size());assertEquals(3,c.curves().get("PATTERN_V2").size());assertEquals(new BigDecimal("10050"),c.metrics().stream().filter(m->m.metric().equals("Equity")).findFirst().orElseThrow().v1());assertEquals(0,a.executions.size());}
    @Test void ignoresEquityBeforeExplicitActivationAndRejectsUnalignedSessions(){var a=session();var b=session();a.equityCurve.add(0,point(start.minusSeconds(86400),10000,"PAPER"));b.equityCurve.add(0,point(start.minusSeconds(86400),10000,"PAPER"));assertEquals(start,PaperDeskService.compare(a,b,null,null).from());b.activationDate=b.activationDate.plusDays(1);assertFalse(PaperDeskService.compare(a,b,null,null).available());}
}
