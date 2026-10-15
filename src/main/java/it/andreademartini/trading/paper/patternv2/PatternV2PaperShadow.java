package it.andreademartini.trading.paper.patternv2;

import it.andreademartini.trading.paper.patternv1.*;

import static it.andreademartini.trading.paper.patternv1.PatternPaperModel.*;
import it.andreademartini.trading.backtest.patternv2.PatternV2BarRules;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.strategy.patternv1.PatternV1Strategy.EntryPlan;
import java.math.*;
import java.time.*;
import java.util.*;

/** Prospective closed-bar simulation on the same recorded signals. Never generates PAPER fills. */
public final class PatternV2PaperShadow {
    private PatternV2PaperShadow() {}
    public static void close(Session s,String symbol,HistoricalBar bar,PatternPaperCalendar calendar,Instant observedAt){
        closeBatch(s,Map.of(symbol,bar),Map.of(symbol,calendar),observedAt);
    }
    public static void closeBatch(Session s,Map<String,HistoricalBar> incoming,Map<String,PatternPaperCalendar> calendars,Instant observedAt){
        var bars=new TreeMap<String,HistoricalBar>();incoming.forEach((symbol,bar)->{if(!s.shadowProcessedCloses.contains(s.strategyVersion+":"+symbol+":"+bar.getDateTime().toLocalDate()))bars.put(symbol,bar);});
        if(bars.isEmpty())return;
        var g=s.frozen.configuration().global();
        // Mark all existing positions at OPEN. No same-day high/low/close or exit proceeds
        // can influence sizing of this session's entries.
        for(var trade:s.shadowTrades)if(bars.containsKey(trade.symbol)&&trade.remaining().signum()>0)trade.mark=bars.get(trade.symbol).getOpen();
        for(var entry:bars.entrySet()){
        String symbol=entry.getKey();var bar=entry.getValue();var calendar=calendars.get(symbol);var date=bar.getDateTime().toLocalDate();
        for(var original:s.signals.stream().filter(r->r.symbol.equals(symbol)&&date.equals(r.entrySession)&&!"REJECTED".equals(r.status)).toList()){
            if(s.shadowTrades.stream().anyMatch(t->t.signalId.equals(original.id)))continue;
            if(s.shadowTrades.stream().anyMatch(t->t.symbol.equals(symbol)&&t.remaining().signum()>0))continue;
            var signal=PatternV2PaperStore.read(PatternV2PaperStore.json(original),SignalRecord.class);
            var virtual=virtual(s);var capital=PatternPaperLedger.capital(virtual,observedAt);
            // SHADOW uses the strategy's unlevered portfolio limits, not unconfigured operational guardrails.
            var limits=new Limits(BigDecimal.valueOf(Double.MAX_VALUE),BigDecimal.valueOf(100),Integer.MAX_VALUE,Integer.MAX_VALUE,BigDecimal.valueOf(100));
            var quote=new Quote(bar.getOpen(),bar.getOpen(),bar.getOpen(),bar.getOpen(),observedAt,date,true,BigDecimal.valueOf(g.maxQuantity()),BigDecimal.ONE);
            it.andreademartini.trading.backtest.patternstable.engine.PatternV1PositionSizing.Decision decision;
            try{decision=new PatternV2PaperPlanner(s.frozen).prepare(signal,quote,capital,capital.available(),limits);}catch(IllegalArgumentException rejected){if(original.mode.equals("SHADOW")){original.status="SHADOW_SKIPPED";original.reason=rejected.getMessage();}continue;}
            if(decision.quantity()<1)continue;
            var t=new Trade();t.signalId=original.id;t.symbol=symbol;t.direction=original.signal.detection().direction().name();t.pattern=original.signal.detection().patternType().name();t.mode="SHADOW";t.signalDate=original.signal.detection().signalDate();t.entryDate=date;t.entryAt=calendar.session(date).orElseThrow().open();t.entered=BigDecimal.valueOf(decision.quantity());t.entryValue=bar.getOpen().multiply(t.entered);t.expectedEntry=bar.getOpen();t.expectedQuantity=decision.quantity();t.stop=signal.expectedStop;t.target=signal.expectedTarget;t.expectedRisk=bar.getOpen().subtract(t.stop).abs();t.expectedSlippage=BigDecimal.valueOf(g.slippagePercentPerSide());t.commission=g.commission().charge(decision.quantity(),bar.getOpen());t.shadowSlippage=bar.getOpen().multiply(t.entered).multiply(BigDecimal.valueOf(g.slippagePercentPerSide()/100));t.holdingBars=1;t.mark=bar.getClose();s.shadowTrades.add(t);
            t.mark=bar.getOpen();
            if(original.mode.equals("SHADOW")){original.expectedEntry=signal.expectedEntry;original.expectedStop=signal.expectedStop;original.expectedTarget=signal.expectedTarget;original.expectedQuantity=signal.expectedQuantity;original.status="SHADOW_SIMULATED";}
        }
        }
        // Once every entry has been sized, apply STOP_FIRST/target/timeout to closed bars.
        for(var trade:s.shadowTrades)if(bars.containsKey(trade.symbol)&&trade.remaining().signum()>0){
            var bar=bars.get(trade.symbol);var day=bar.getDateTime().toLocalDate();trade.holdingBars=calendars.get(trade.symbol).holdingBars(trade.entryDate,day);exit(s,trade,bar,day,observedAt);
        }
        bars.forEach((symbol,bar)->s.shadowProcessedCloses.add(s.strategyVersion+":"+symbol+":"+bar.getDateTime().toLocalDate()));
        var virtual=virtual(s);var point=PatternPaperLedger.capital(virtual,observedAt);s.shadowPeak=s.shadowPeak.max(point.equity());s.shadowMaximumDrawdown=s.shadowMaximumDrawdown.max(point.drawdownPercent());
        s.shadowEquity.add(new EquityPoint(observedAt,"SHADOW",point.equity(),point.realized(),point.unrealized(),point.commissions(),point.available(),point.committed(),point.longExposure(),point.shortExposure(),point.drawdownPercent()));
        for(var trade:s.trades)s.shadowTrades.stream().filter(t->t.signalId.equals(trade.signalId)&&t.closed()).findFirst().ifPresent(t->trade.expectedExit=t.exitPrice());
    }
    private static void exit(Session s,Trade t,HistoricalBar bar,LocalDate day,Instant now){
        var signal=s.signals.stream().filter(r->r.id.equals(t.signalId)).findFirst().orElseThrow().signal;
        var g=s.frozen.configuration().global();int maximum=s.frozen.configuration().symbols().stream().filter(c->c.symbol().equals(t.symbol)).findFirst().orElseThrow().holding(g);
        var plan=new EntryPlan(signal,signal.detection().signalBarIndex()+1,t.entryDate,t.entryPrice(),t.stop,t.expectedRisk,t.target);
        var exit=PatternV2BarRules.exit(plan,bar,t.holdingBars,maximum,g.sameBarPolicy());t.mark=bar.getClose();
        if(exit.completed()){t.exited=t.entered;t.exitValue=exit.price().multiply(t.entered);t.exitReason=exit.reason();t.exitAt=now;t.realized=exit.price().subtract(t.entryPrice()).multiply(t.entered).multiply(t.direction.equals("LONG")?BigDecimal.ONE:BigDecimal.ONE.negate());t.commission=t.commission.add(g.commission().charge(t.entered.longValueExact(),exit.price()));t.shadowSlippage=t.shadowSlippage.add(exit.price().multiply(t.entered).multiply(BigDecimal.valueOf(g.slippagePercentPerSide()/100)));}
    }
    public static Session virtual(Session s){var virtual=new Session();virtual.frozen=s.frozen;virtual.initialCapital=s.initialCapital;virtual.peakEquity=s.shadowPeak;virtual.maximumDrawdown=s.shadowMaximumDrawdown;virtual.trades=s.shadowTrades;return virtual;}
}
