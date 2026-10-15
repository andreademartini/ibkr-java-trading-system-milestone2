package it.andreademartini.trading.paper.multi;

import static it.andreademartini.trading.paper.patternv1.PatternPaperModel.*;
import it.andreademartini.trading.paper.patternv1.*;
import it.andreademartini.trading.paper.patternv2.*;
import it.andreademartini.trading.risk.KillSwitchService;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class PaperDeskService {
    public record Regime(boolean enabled,int adxPeriod,double adxThreshold,String mode) {}
    public record Strategy(String strategyId,String strategyVersion,String paperSessionId,String strategyInstanceId,
            String accountId,String configurationHash,Object configurationSnapshot,Regime regime,Dashboard dashboard) {}
    public record Metric(String metric,Object v1,Object v2) {}
    public record Comparison(boolean available,String reason,Instant from,Instant to,List<Metric> metrics,Map<String,List<EquityPoint>> curves) {}
    public record Desk(boolean globalKill,String globalReason,boolean simultaneousCertified,List<Strategy> strategies,
            MultiStrategyPaperRouter.Reconciliation reconciliation,Comparison comparison,List<Event> accountEvents) {}
    private final PatternPaperService v1;private final PatternV2PaperService v2;private final PatternPaperStore s1;private final PatternV2PaperStore s2;
    private final PatternPaperAdmin admin;private final MultiStrategyPaperRouter router;private final PaperDeskStore store;private final KillSwitchService global;
    public PaperDeskService(PatternPaperService v1,PatternV2PaperService v2,PatternPaperStore s1,PatternV2PaperStore s2,PatternPaperAdmin admin,MultiStrategyPaperRouter router,PaperDeskStore store,KillSwitchService global){this.v1=v1;this.v2=v2;this.s1=s1;this.s2=s2;this.admin=admin;this.router=router;this.store=store;this.global=global;}
    @PostConstruct public void recover(){if(store.state().globalKill)global.activate("Persisted PAPER account kill switch: "+store.state().reason);}
    public Desk status(Instant from,Instant to){
        validateDates(from,to);var meta=store.state();var a=v1.dashboard(admin.configured());var b=v2.dashboard(admin.configured());var strategies=List.of(view(MultiStrategyPaperRouter.V1,a,meta),view(MultiStrategyPaperRouter.V2,b,meta));
        return new Desk(meta.globalKill||global.isActive(),meta.reason,router.reconciliation().simultaneousCertified(),strategies,router.reconciliation(),compare(a.session(),b.session(),from,to),store.events());
    }
    private Strategy view(String id,Dashboard d,PaperDeskStore.State meta){var s=d.session();var c=s.frozen==null?null:s.frozen.configuration();return new Strategy(id,s.strategyVersion,meta.paperSessionId,meta.instances.get(id),s.account,s.configurationHash,c,c==null?null:new Regime(c.marketRegimeFilterEnabled(),c.marketRegimeFilterAdxPeriod(),c.marketRegimeFilterAdxThreshold(),c.marketRegimeFilterMode().name()),d);}
    private static void requireStrategy(String id){if(!Set.of("PATTERN_V1","PATTERN_V2").contains(id))throw new IllegalArgumentException("Only frozen V1/V2 may use PAPER; research V3 is prohibited");}
    public synchronized void command(String id,String operation,String commandId,String confirmation,Limits limits){
        requireStrategy(id);if(commandId==null||!commandId.matches("[a-zA-Z0-9-]{8,80}"))throw new IllegalArgumentException("Command ID required");
        if(operation.equals("start")){
            if(store.state().globalKill||global.isActive())throw new IllegalStateException("Persistent account kill switch requires explicit reset");
            var other=id.equals("PATTERN_V1")?s2.session():s1.session();
            if(!router.reconciliation().simultaneousCertified()&&(other.mode==Mode.PAPER_AUTO||other.resumeRequested||other.trades.stream().anyMatch(t->t.remaining().signum()>0)||other.orders.stream().anyMatch(o->o.role==Role.ENTRY&&!o.terminal())))throw new IllegalStateException("MULTI_STRATEGY_VALIDATION_REQUIRED: simultaneous PAPER_AUTO is disabled; existing positions remain protected");
        }
        boolean first=id.equals("PATTERN_V1");
        switch(operation){
            case "start"->{if(first)v1.start(commandId);else v2.start(commandId);}
            case "stop"->{if(first)v1.stop(commandId);else v2.stop(commandId);}
            case "kill"->{if(first)v1.kill(commandId,confirmation);else v2.kill(commandId,confirmation);}
            case "reset"->{if(first)v1.reset(commandId,confirmation);else v2.reset(commandId,confirmation);}
            case "shadow"->{if(first)v1.shadow(commandId);else v2.shadow(commandId);}
            case "settings"->{if(first)v1.configure(limits,commandId);else v2.configure(limits,commandId);}
            case "connect"->{if(first)v1.connect(commandId);else v2.connect(commandId);}
            case "refresh"->{if(first)v1.refresh();else v2.refresh();checkAccount();}
            default->throw new IllegalArgumentException("Unsupported PAPER operation");
        }
    }
    private void checkAccount(){var r=router.reconciliation();if(r.time()!=null&&!r.complete()){store.update(s->{s.globalKill=true;s.reason="Account reconciliation failed: "+r.issues();return null;});global.activate("Account reconciliation failed");store.event("ACCOUNT_RECONCILIATION_ERROR",String.join("; ",r.issues()),true);}}
    public synchronized void accountCommand(String operation,String commandId,String confirmation){
        if(commandId==null||!commandId.matches("[a-zA-Z0-9-]{8,80}"))throw new IllegalArgumentException("Command ID required");
        if(operation.equals("kill")){
            if(!"ATTIVA KILL SWITCH".equals(confirmation))throw new IllegalArgumentException("Explicit kill confirmation required");
            store.update(s->{s.commands.add(commandId);s.globalKill=true;s.reason="Explicit global kill: new entries blocked; protections retained";return null;});global.activate("PAPER account emergency");
            // Persistent commands in each strategy make retries safe; pending-entry cancellations remain verified.
            v1.kill(commandId+"-v1",confirmation);v2.kill(commandId+"-v2",confirmation);store.event("GLOBAL_KILL","Both strategy entry gates disabled; no liquidation requested",true);
        }else if(operation.equals("reset")){
            if(!"RESET KILL SWITCH".equals(confirmation))throw new IllegalArgumentException("Explicit reset confirmation required");
            v1.refresh();v2.refresh();if(!router.reconciliation().complete())throw new IllegalStateException("Complete account reconciliation required before reset");
            if(s1.session().killSwitch)v1.reset(commandId+"-v1",confirmation);if(s2.session().killSwitch)v2.reset(commandId+"-v2",confirmation);
            store.update(s->{s.commands.add(commandId);s.globalKill=false;s.reason="Global reset; strategies require explicit activation";return null;});global.deactivate();store.event("GLOBAL_RESET","No strategy was started",false);
        }else throw new IllegalArgumentException("Unsupported account operation");
    }
    public List<Event> allEvents(){var events=new ArrayList<Event>();events.addAll(s1.allEvents());events.addAll(s2.allEvents());events.addAll(store.events());return events.stream().sorted(Comparator.comparing(Event::time)).toList();}
    static void validateDates(Instant from,Instant to){if(from!=null&&to!=null&&from.isAfter(to))throw new IllegalArgumentException("from must precede to");}
    public static Comparison compare(Session a,Session b,Instant requestedFrom,Instant requestedTo){
        validateDates(requestedFrom,requestedTo);
        if(a.startedAt==null||b.startedAt==null||a.activationDate==null||b.activationDate==null||!a.activationDate.equals(b.activationDate)||!a.activationDate.equals(a.startedAt.atZone(ZoneId.of("America/New_York")).toLocalDate())||!b.activationDate.equals(b.startedAt.atZone(ZoneId.of("America/New_York")).toLocalDate())||a.initialCapital.compareTo(b.initialCapital)!=0||a.equityCurve.isEmpty()||b.equityCurve.isEmpty())return new Comparison(false,"Comparable PAPER observations unavailable: require same activation date/capital and actual PAPER equity for both strategies. SHADOW is separate.",requestedFrom,requestedTo,List.of(),Map.of());
        var observedA=a.equityCurve.stream().filter(p->p.mode().equals("PAPER")&&!p.time().isBefore(a.startedAt)).sorted(Comparator.comparing(EquityPoint::time)).toList();
        var observedB=b.equityCurve.stream().filter(p->p.mode().equals("PAPER")&&!p.time().isBefore(b.startedAt)).sorted(Comparator.comparing(EquityPoint::time)).toList();
        if(observedA.isEmpty()||observedB.isEmpty())return new Comparison(false,"No actual equity observations after explicit PAPER activation",requestedFrom,requestedTo,List.of(),Map.of());
        var start=observedA.getFirst().time().isAfter(observedB.getFirst().time())?observedA.getFirst().time():observedB.getFirst().time();
        var end=observedA.getLast().time().isBefore(observedB.getLast().time())?observedA.getLast().time():observedB.getLast().time();
        if(requestedFrom!=null&&requestedFrom.isAfter(start))start=requestedFrom;if(requestedTo!=null&&requestedTo.isBefore(end))end=requestedTo;
        if(end.isBefore(start))return new Comparison(false,"No overlapping actual PAPER observations",start,end,List.of(),Map.of());
        var x=window(a,start,end);var y=window(b,start,end);var metrics=new ArrayList<Metric>();for(var key:x.keySet())metrics.add(new Metric(key,x.get(key),y.get(key)));
        Instant from=start,to=end;return new Comparison(true,"Actual PAPER fills only. Sparse equity points are preserved; no invented dates or normalized synthetic curve.",start,end,List.copyOf(metrics),Map.of("PATTERN_V1",a.equityCurve.stream().filter(p->inside(p.time(),from,to)&&p.mode().equals("PAPER")).toList(),"PATTERN_V2",b.equityCurve.stream().filter(p->inside(p.time(),from,to)&&p.mode().equals("PAPER")).toList()));
    }
    private static Map<String,Object> window(Session s,Instant from,Instant to){
        var points=s.equityCurve.stream().filter(p->p.mode().equals("PAPER")&&!p.time().isAfter(to)).sorted(Comparator.comparing(EquityPoint::time)).toList();
        var initial=points.stream().filter(p->!p.time().isAfter(from)).reduce((a,b)->b).orElseThrow();var last=points.getLast();
        var closed=s.trades.stream().filter(Trade::closed).filter(t->"PAPER".equals(t.mode)&&inside(t.exitAt,from,to)).toList();
        var positive=closed.stream().map(Trade::netPnl).filter(p->p.signum()>0).reduce(BigDecimal.ZERO,BigDecimal::add);var negative=closed.stream().map(Trade::netPnl).filter(p->p.signum()<0).reduce(BigDecimal.ZERO,BigDecimal::add).abs();
        var observed=points.stream().filter(p->inside(p.time(),from,to)).toList();BigDecimal peak=initial.equity(),dd=BigDecimal.ZERO;
        for(var p:observed){peak=peak.max(p.equity());if(peak.signum()>0)dd=dd.max(peak.subtract(p.equity()).multiply(BigDecimal.valueOf(100)).divide(peak,MathContext.DECIMAL64));}
        var m=new LinkedHashMap<String,Object>();m.put("Initial virtual capital",s.initialCapital);m.put("Observation opening equity",initial.equity());m.put("Equity",last.equity());m.put("Return %",initial.equity().signum()>0?last.equity().subtract(initial.equity()).multiply(BigDecimal.valueOf(100)).divide(initial.equity(),MathContext.DECIMAL64):null);m.put("Net PnL",last.equity().subtract(initial.equity()));m.put("PF net",negative.signum()==0?null:positive.divide(negative,MathContext.DECIMAL64));m.put("Win rate %",closed.isEmpty()?null:100.0*closed.stream().filter(t->t.netPnl().signum()>0).count()/closed.size());m.put("Max drawdown %",dd);m.put("Closed trades",closed.size());m.put("Open trades at end",s.trades.stream().filter(t->"PAPER".equals(t.mode)&&t.entryAt!=null&&!t.entryAt.isAfter(to)&&(t.exitAt==null||t.exitAt.isAfter(to)||t.remaining().signum()>0)).count());
        m.put("Commissions",s.commissions.stream().filter(f->inside(f.reportedAt(),from,to)).map(CommissionRecord::amount).reduce(BigDecimal.ZERO,BigDecimal::add));
        m.put("Slippage (entry vs expected open)",s.executions.stream().filter(e->inside(e.time(),from,to)).filter(e->e.role()==Role.ENTRY).map(e->{var r=s.signals.stream().filter(v->v.id.equals(e.signalId())).findFirst().orElseThrow();return r.expectedEntry==null?null:e.price().subtract(r.expectedEntry).multiply(e.quantity()).multiply(e.side().equals("BUY")?BigDecimal.ONE:BigDecimal.ONE.negate());}).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add));
        m.put("Average exposure %",observed.stream().filter(p->p.equity().signum()>0).mapToDouble(p->p.longExposure().add(p.shortExposure()).doubleValue()/p.equity().doubleValue()*100).average().isPresent()?observed.stream().filter(p->p.equity().signum()>0).mapToDouble(p->p.longExposure().add(p.shortExposure()).doubleValue()/p.equity().doubleValue()*100).average().orElseThrow():null);
        for(var reason:List.of("STOP","TARGET","TIMEOUT"))m.put(reason+" count",closed.stream().filter(t->reason.equals(t.exitReason)).count());return m;
    }
    public static boolean inside(Instant time,Instant from,Instant to){return time!=null&&(from==null||!time.isBefore(from))&&(to==null||!time.isAfter(to));}
}
