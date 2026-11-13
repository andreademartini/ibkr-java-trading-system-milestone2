package it.andreademartini.trading.paper.patternv2;

import it.andreademartini.trading.paper.patternv1.*;

import static it.andreademartini.trading.paper.patternv1.PatternPaperModel.*;
import it.andreademartini.trading.backtest.patternstable.*;
import it.andreademartini.trading.config.*;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.orders.IbkrOrderCommand;
import it.andreademartini.trading.risk.KillSwitchService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Dedicated frozen V2 orchestration. Every control and transmit decision is made server-side. */
@Service
public class PatternV2PaperService {
    @org.springframework.beans.factory.annotation.Autowired(required=false) private it.andreademartini.trading.monitor.ExecutionMonitor executionMonitor;
    private final PatternV2PaperStore store;
    private final it.andreademartini.trading.backtest.patternv2stable.FrozenPatternV2Service stable;
    private final PatternPaperBroker broker;
    private final TradingSystemProperties properties;
    private final KillSwitchService globalKill;
    private volatile BrokerSnapshot lastSnapshot;
    private final Map<String,PatternPaperCalendar> calendars=new HashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean entriesInhibited=new java.util.concurrent.atomic.AtomicBoolean(true);
    public PatternV2PaperService(PatternV2PaperStore store,it.andreademartini.trading.backtest.patternv2stable.FrozenPatternV2Service stable,@org.springframework.beans.factory.annotation.Qualifier("patternV2PaperBroker") PatternPaperBroker broker,TradingSystemProperties properties,KillSwitchService globalKill){this.store=store;this.stable=stable;this.broker=broker;this.properties=properties;this.globalKill=globalKill;}
    @PostConstruct public synchronized void recover(){
        store.update(s->{
            var version=stable.version("1.0");
            if(version==null){s.mode=Mode.DISABLED;s.reason="Configurazione V2 non disponibile: PAPER disabilitato";return null;}
            if(s.frozen==null){s.frozen=version;s.strategyVersion=version.strategyVersion();s.configurationHash=version.configurationHash();s.initialCapital=BigDecimal.valueOf(version.configuration().global().initialCapital());s.peakEquity=s.initialCapital;}
            if(!version.configurationHash().equals(s.configurationHash))error(s,"Versione ufficiale diversa dalla sessione PAPER persistita; migrazione esplicita necessaria");
            if(s.mode==Mode.PAPER_AUTO){s.resumeRequested=true;s.mode=Mode.STOPPING;s.reason="Riavvio: nuovi ingressi sospesi fino alla riconciliazione";}
            s.lastReconciliation=null;s.generation++;store.event(s,"RESTART",null,null,null,s.reason,false);return null;});
        calendars.putAll(store.session().calendars);
    }
    public synchronized Dashboard dashboard(boolean adminConfigured){
        var s=store.session();var point=PatternPaperLedger.capital(s,Instant.now());var next=new LinkedHashMap<String,Instant>();
        calendars.forEach((symbol,calendar)->calendar.next(Instant.now()).ifPresent(session->next.put(symbol,session.open())));
        var performance=new LinkedHashMap<>(PatternPaperLedger.performance(s,point));var shadow=PatternV2PaperShadow.virtual(s);performance.put("shadow",PatternPaperLedger.performance(shadow,PatternPaperLedger.capital(shadow,Instant.now())));
        return new Dashboard(s,broker.connection(),checks(s),point,performance,store.events(),next,adminConfigured,broker.limitations());
    }
    public synchronized void configure(Limits limits,String commandId){
        limits.validate();store.update(s->{if(!claim(s,commandId))return null;
            if(s.mode==Mode.PAPER_AUTO)throw new IllegalStateException("Fermare i nuovi ingressi prima di modificare i limiti");
            var before=s.limits;s.limits=limits;store.event(s,"CONFIGURATION",null,null,null,"Limiti operativi: "+PatternV2PaperStore.json(before)+" -> "+PatternV2PaperStore.json(limits),false);return null;});
    }
    public synchronized void connect(String commandId){
        var shouldConnect=store.update(s->{if(!claim(s,commandId))return false;store.event(s,"CONNECTION",null,null,null,"Richiesta connessione in sola lettura",false);return true;});
        if(shouldConnect){try{broker.connectReadOnly();refresh();}catch(RuntimeException e){fail("CONNECTION ERROR: "+e.getMessage());}}
    }
    public synchronized void refresh(){
        // Read-only: reconciliation can update accounting but cannot create/cancel/modify orders.
        try{lastSnapshot=broker.reconcile();applySnapshot(lastSnapshot);}
        catch(RuntimeException e){lastSnapshot=null;fail("Riconciliazione fallita: "+e.getMessage());}
    }
    public synchronized void start(String commandId){
        refresh();
        if(broker.connection().connected())loadCalendars();
        store.update(s->{if(!claim(s,commandId))return null;
            var blocked=checks(s).stream().filter(c->!c.passed()).toList();
            if(!blocked.isEmpty()){error(s,blocked.stream().map(c->c.name()+": "+c.reason()).reduce((a,b)->a+"; "+b).orElseThrow());return null;}
            s.mode=Mode.PAPER_AUTO;s.resumeRequested=true;s.startedAt=Instant.now();s.activationDate=Instant.now().atZone(ZoneId.of("America/New_York")).toLocalDate();
            s.account=broker.connection().account();s.reason="PAPER_AUTO attivo";s.generation++;entriesInhibited.set(false);store.event(s,"PAPER_AUTO",null,null,null,s.reason,false);return null;});
    }
    public synchronized void shadow(String commandId){
        store.update(s->{if(!claim(s,commandId))return null;if(s.killSwitch)throw new IllegalStateException("Kill switch persistente: reset amministrativo richiesto");
            if(s.mode==Mode.PAPER_AUTO||s.trades.stream().anyMatch(t->t.remaining().signum()>0))throw new IllegalStateException("SHADOW richiede una sessione senza posizioni PAPER aperte");
            s.mode=Mode.SHADOW;s.resumeRequested=false;s.activationDate=Instant.now().atZone(ZoneId.of("America/New_York")).toLocalDate();s.reason="SHADOW: segnali registrati, nessun ordine trasmesso";s.generation++;
            store.event(s,"SHADOW",null,null,null,s.reason,false);return null;});
    }
    public void stop(String commandId){entriesInhibited.set(true);synchronized(this){store.update(s->{if(!claim(s,commandId))return null;s.resumeRequested=false;if(!s.killSwitch)s.mode=Mode.STOPPING;s.reason="Nuovi ingressi fermati; protezioni e riconciliazione mantenute";s.generation++;store.event(s,"STOPPING",null,null,null,s.reason,false);return null;});}}
    public void kill(String commandId,String confirmation){
        if(!"ATTIVA KILL SWITCH".equals(confirmation))throw new IllegalArgumentException("Conferma esplicita mancante");
        entriesInhibited.set(true);synchronized(this){
        boolean changed=store.update(s->{if(!claim(s,commandId))return false;s.killSwitch=true;s.resumeRequested=false;s.mode=Mode.KILL_SWITCH;s.reason="Kill switch persistente: nuovi ingressi bloccati";s.generation++;store.event(s,"KILL_SWITCH",null,null,null,s.reason,true);return true;});
        if(changed)cancelUnfilledEntries();
        }
    }
    public synchronized void reset(String commandId,String confirmation){
        if(!"RESET KILL SWITCH".equals(confirmation))throw new IllegalArgumentException("Conferma reset mancante");
        refresh();store.update(s->{if(!claim(s,commandId))return null;
            if(lastSnapshot==null||!lastSnapshot.complete()||!reconciliationIssues(s,lastSnapshot).isEmpty())throw new IllegalStateException("Reset richiede riconciliazione completa e protezioni coerenti");
            s.killSwitch=false;s.resumeRequested=false;s.mode=Mode.DISABLED;s.reason="Kill switch resettato; avvio automatico non abilitato";s.generation++;store.event(s,"KILL_RESET",null,null,null,s.reason,false);return null;});
    }
    private boolean claim(Session s,String id){if(id==null||!id.matches("[a-zA-Z0-9-]{8,80}"))throw new IllegalArgumentException("Command ID obbligatorio");return s.commands.add(id);}
    public synchronized void tick(Instant now){
        var initial=store.session();if(initial.mode==Mode.DISABLED&&initial.trades.stream().noneMatch(t->t.remaining().signum()>0))return;
        ingest();
        var c=broker.connection();
        if(!c.connected()){
            store.update(s->{if(s.mode==Mode.PAPER_AUTO){s.mode=Mode.ERROR;s.reason="Disconnessione: ingressi bloccati; riconciliazione richiesta";s.lastReconciliation=null;s.generation++;store.event(s,"CONNECTION ERROR",null,null,null,s.reason,true);}return null;});return;
        }
        if(lastSnapshot==null||Duration.between(lastSnapshot.time(),now).compareTo(Duration.ofSeconds(30))>0){refresh();}
        var current=store.session();
        if(lastSnapshot==null||!lastSnapshot.complete())return;
        if(current.killSwitch||current.mode==Mode.STOPPING||current.mode==Mode.ERROR){cancelUnfilledEntries();}
        try{
            loadCalendars();markAndCount(now);manageProtectionAndTimeout(now);
            var s=store.session();
            if(s.resumeRequested&&!s.killSwitch&&(s.mode==Mode.STOPPING||s.mode==Mode.ERROR)&&checks(s).stream().allMatch(Check::passed)){
                store.update(v->{v.mode=Mode.PAPER_AUTO;entriesInhibited.set(false);v.reason="Riconnessione verificata e riconciliata: PAPER_AUTO ripreso";store.event(v,"PAPER_AUTO",null,null,null,v.reason,false);return null;});
            }
            s=store.session();
            if(s.mode==Mode.SHADOW||s.mode==Mode.PAPER_AUTO){scanClosedBars(now);if(s.mode==Mode.PAPER_AUTO)processEntries(now);}
            store.update(v->{v.lastScheduler=now;return null;});
        }catch(RuntimeException e){fail(e.getMessage());}
    }
    private void loadCalendars(){
        for(var config:store.session().frozen.configuration().symbols())if(config.enabled()){
            var incoming=broker.calendar(config.symbol());var prior=calendars.get(config.symbol());
            if(prior!=null){var dates=new HashSet<>(prior.coveredDates());dates.addAll(incoming.coveredDates());var sessions=new TreeMap<LocalDate,PatternPaperCalendar.Session>();prior.sessions().forEach(s->sessions.put(s.date(),s));
                incoming.coveredDates().forEach(sessions::remove);incoming.sessions().forEach(s->sessions.put(s.date(),s));
                incoming=new PatternPaperCalendar(incoming.symbol(),incoming.timezone(),incoming.retrievedAt(),Set.copyOf(dates),List.copyOf(sessions.values()));}
            calendars.put(config.symbol(),incoming);
        }
        store.update(s->{s.calendars=new HashMap<>(calendars);return null;});
    }
    private void scanClosedBars(Instant now){
        var session=store.session();var planner=new PatternV2PaperPlanner(session.frozen);
        var closedBars=new TreeMap<String,HistoricalBar>();
        for(var config:session.frozen.configuration().symbols())if(config.enabled()){
            var calendar=calendars.get(config.symbol());if(!calendar.fresh(now))throw new IllegalStateException("Calendario scaduto: "+config.symbol());
            var closed=calendar.latestClosed(now).orElse(null);if(closed==null||session.activationDate==null||closed.date().isBefore(session.activationDate))continue;
            String key=session.strategyVersion+":"+config.symbol()+":"+closed.date();if(session.processedCloses.contains(key))continue;
            var history=broker.dailyBars(config.symbol()).stream().filter(b->!b.getDateTime().toLocalDate().isAfter(closed.date())).sorted(Comparator.comparing(HistoricalBar::getDateTime)).toList();
            if(history.size()<2||!history.getLast().getDateTime().toLocalDate().equals(closed.date()))throw new IllegalStateException("Barra DAILY completa mancante: "+config.symbol()+" "+closed.date());
            closedBars.put(config.symbol(),history.getLast());
            var next=calendar.next(closed.close()).orElseThrow(()->new IllegalStateException("Prossima sessione non disponibile: "+config.symbol()));
            var signals=planner.signals(config.symbol(),history,history.size()-1);
            store.update(s->{if(s.processedCloses.contains(key))return null;
                for(var signal:signals){String id=signalId(s.strategyVersion,signal);if(s.signals.stream().anyMatch(r->r.id.equals(id)))continue;
                    var record=new SignalRecord();record.id=id;record.signal=signal;record.symbol=config.symbol();record.mode=s.mode==Mode.SHADOW?"SHADOW":"PAPER";record.generatedAt=now;record.entrySession=next.date();record.status="PENDING";
                    var regime=planner.regime(signal,history,history.size()-1);if(!regime.accepted()){record.status="REJECTED";record.reason=regime.rejectionReason();store.event(s,"REGIME_REJECTED",record.symbol,null,id,PatternV2PaperStore.json(regime),false);}
                    if(executionMonitor!=null)executionMonitor.signal(s.strategyVersion,record,history.getLast(),closed.open(),closed.close(),broker.connection());
                    s.signals.add(record);s.lastSignalAt=now;store.event(s,"SIGNAL",record.symbol,null,id,signal.detection().patternType()+" "+signal.detection().direction(),false);
                }s.processedCloses.add(key);return null;});
        }
        store.update(s->{PatternV2PaperShadow.closeBatch(s,closedBars,calendars,now);return null;});
    }
    private void processEntries(Instant now){
        var candidates=store.session().signals.stream().filter(r->r.mode.equals("PAPER")&&r.status.equals("PENDING")).sorted(Comparator.comparing((SignalRecord r)->r.entrySession).thenComparing(r->r.symbol).thenComparing(r->r.signal.detection().direction())).toList();
        for(var candidate:candidates){
            var calendar=calendars.get(candidate.symbol);var session=calendar.session(candidate.entrySession).orElseThrow();
            if(now.isBefore(session.open()))continue;
            if(now.isAfter(session.open().plusSeconds(60))){reject(candidate.id,"NEXT_BAR_OPEN mancato: nessun ingresso tardivo");continue;}
            var connection=broker.connection();var state=store.session();var blocked=checks(state).stream().filter(c->!c.passed()).toList();
            if(!blocked.isEmpty()){fail(blocked.getFirst().reason());return;}
            if(state.trades.stream().anyMatch(t->t.symbol.equals(candidate.symbol)&&t.remaining().signum()>0)||state.orders.stream().anyMatch(o->o.symbol.equals(candidate.symbol)&&o.role==Role.ENTRY&&!o.terminal())){reject(candidate.id,"Posizione/ordine V2 gia presente sul simbolo");continue;}
            var quote=broker.quote(candidate.symbol);var receivedAt=Instant.now();if(quote==null||quote.time()==null||quote.time().isAfter(receivedAt)||Duration.between(quote.time(),receivedAt).compareTo(Duration.ofSeconds(10))>0){reject(candidate.id,"Dati realtime mancanti/obsoleti");continue;}
            try{
                var point=PatternPaperLedger.capital(state,now);var planner=new PatternV2PaperPlanner(state.frozen);var decision=planner.prepare(candidate,quote,point,lastSnapshot.availableFunds(),state.limits);
                if(decision.quantity()<1){reject(candidate.id,"Vincoli capitale/rischio: quantita non valida");continue;}
                var cmds=broker.bracket(candidate,decision.quantity(),connection.account());
                reserveAndTransmit(candidate,cmds,false);
            }catch(RuntimeException e){reject(candidate.id,e.getMessage());}
        }
    }
    private void reserveAndTransmit(SignalRecord candidate,List<IbkrOrderCommand> cmds,boolean protective){
        var state=store.session();long generation=state.generation;long epoch=broker.connection().epoch();
        boolean reserved=store.update(s->{
            if(!protective&&(entriesInhibited.get()||s.mode!=Mode.PAPER_AUTO||s.killSwitch||globalKill.isActive()||s.generation!=generation))return false;
            var original=s.signals.stream().filter(r->r.id.equals(candidate.id)).findFirst().orElseThrow();
            if(!protective&&!original.status.equals("PENDING"))return false;
            for(var cmd:cmds){
                var record=s.orders.stream().filter(o->o.orderId==cmd.orderId()).findFirst().orElseGet(()->{var o=new OrderRecord();s.orders.add(o);return o;});
                record.orderId=cmd.orderId();record.parentId=cmd.parentOrderId()==null?0:cmd.parentOrderId();record.signalId=candidate.id;record.clientOrderId=cmd.order().orderRef();record.account=cmd.order().account();record.symbol=cmd.symbol();record.action=cmd.side().name();record.type=cmd.order().orderType().toString();record.quantity=cmd.quantity();record.remaining=BigDecimal.valueOf(cmd.quantity());record.role=Role.valueOf(cmd.role());record.limitPrice=cmd.limitPrice()==null?null:BigDecimal.valueOf(cmd.limitPrice());record.stopPrice=cmd.stopPrice()==null?null:BigDecimal.valueOf(cmd.stopPrice());record.ocaGroup=cmd.order().ocaGroup();record.transmit=cmd.order().transmit();record.status="INTENT";record.createdAt=Instant.now();
            }
            original.expectedEntry=candidate.expectedEntry;original.expectedStop=candidate.expectedStop;original.expectedTarget=candidate.expectedTarget;original.expectedQuantity=candidate.expectedQuantity;
            if(!protective)original.status="RESERVED";store.event(s,"ORDER_INTENT",candidate.symbol,null,candidate.id,"Ordini persistiti prima della trasmissione",false);return true;
        });
        if(!reserved)return;
        try{
            for(var cmd:cmds){
                // Serialize controls with submission; after a stop there can be no new ENTRY transmit.
                var current=store.session();var c=broker.connection();
                if(c.epoch()!=epoch||!c.connected()||!c.paperIdentityVerified()||!c.writesVerified()||!c.account().equals(cmd.order().account()))throw new IllegalStateException("Sessione PAPER cambiata/non verificata prima dell'invio");
                if(!protective&&(entriesInhibited.get()||current.generation!=generation||current.mode!=Mode.PAPER_AUTO||current.killSwitch||globalKill.isActive()))throw new IllegalStateException("Nuovi ingressi bloccati prima della trasmissione");
                if(executionMonitor!=null){
                      if(!protective && "1.2".equals(current.strategyVersion) && executionMonitor.settings().blockNewPaperOrdersOnUnreliableMarketData() && executionMonitor.unreliable(candidate.symbol))throw new IllegalStateException("Monitor: unreliable market data");
                      executionMonitor.intent(candidate.id,cmd);
                  }
                  broker.place(cmd);
                store.update(s->{var o=s.orders.stream().filter(r->r.orderId==cmd.orderId()).findFirst().orElseThrow();o.status="TRANSMITTED";o.updatedAt=Instant.now();store.event(s,"ORDER_SENT",o.symbol,o.orderId,o.signalId,o.role.name(),false);return null;});
            }
            store.update(s->{if(!protective)s.signals.stream().filter(r->r.id.equals(candidate.id)).findFirst().orElseThrow().status="SUBMITTED";return null;});
        }catch(RuntimeException e){
            store.update(s->{s.signals.stream().filter(r->r.id.equals(candidate.id)).findFirst().orElseThrow().status="UNCERTAIN";error(s,"Trasmissione incerta: nessun retry automatico. "+e.getMessage());return null;});
        }
    }
    private void reject(String id,String reason){store.update(s->{var r=s.signals.stream().filter(v->v.id.equals(id)).findFirst().orElseThrow();if(!r.status.equals("PENDING"))return null;r.status="SKIPPED";r.reason=reason;store.event(s,"SIGNAL_REJECTED",r.symbol,null,id,reason,false);return null;});}
    private void ingest(){
        var fills=broker.drainExecutions();var fees=broker.drainCommissions();var orders=broker.drainOrders();var errors=broker.drainErrors();
        store.update(s->{
            try{for(var f:fills)if(s.orders.stream().anyMatch(o->o.orderId==f.orderId()))if(PatternPaperLedger.execution(s,f))store.event(s,s.orders.stream().filter(o->o.orderId==f.orderId()).findFirst().orElseThrow().role.name(),f.symbol(),f.orderId(),null,f.executionId()+" qty="+f.quantity()+" @ "+f.price(),false);
                for(var fee:fees)PatternPaperLedger.commission(s,fee);
                for(var order:orders)updateOrder(s,order);
                if(!errors.isEmpty())error(s,String.join("; ",errors));
            }catch(RuntimeException e){error(s,e.getMessage());}
            return null;});
    }
    private void applySnapshot(BrokerSnapshot snapshot){
        store.update(s->{
            if(s.account.isBlank())s.account=broker.connection().account();
            try{
                for(var fill:snapshot.executions())if(s.orders.stream().anyMatch(o->o.orderId==fill.orderId()))PatternPaperLedger.execution(s,fill);
                for(var fee:snapshot.commissions())PatternPaperLedger.commission(s,fee);
                for(var order:snapshot.orders())updateOrder(s,order);
                var issues=reconciliationIssues(s,snapshot);
                if(snapshot.complete()&&issues.isEmpty()){s.lastReconciliation=snapshot.time();store.event(s,"RECONCILIATION",null,null,null,"Ordini, esecuzioni e posizioni coerenti",false);}
                else{s.lastReconciliation=null;error(s,snapshot.complete()?String.join("; ",issues):snapshot.reason());}
            }catch(RuntimeException e){s.lastReconciliation=null;error(s,e.getMessage());}
            return null;});
    }
    private void updateOrder(Session s,BrokerOrder brokerOrder){
        var order=s.orders.stream().filter(o->o.orderId==brokerOrder.id()).findFirst();if(order.isEmpty())return;
        var o=order.get();if(!o.account.equals(brokerOrder.account())||!o.symbol.equals(brokerOrder.symbol())||!o.clientOrderId.equals(brokerOrder.clientOrderId()))throw new IllegalStateException("Broker order identity mismatch: "+o.orderId);
        String prior=o.status;o.status=brokerOrder.status();o.filled=brokerOrder.filled();o.permId=brokerOrder.permId();o.remaining=brokerOrder.quantity().subtract(o.filled).max(BigDecimal.ZERO);o.updatedAt=Instant.now();
        if(!o.status.equals(prior))store.event(s,o.status.equals("Inactive")?"ORDER REJECTED":"ORDER_STATUS",o.symbol,o.orderId,o.signalId,o.role+" "+o.status,false);
    }
    private List<String> reconciliationIssues(Session s,BrokerSnapshot snapshot){
        var issues=new ArrayList<String>();if(!snapshot.complete())issues.add("Snapshot broker incompleto");
        if(snapshot.epoch()!=broker.connection().epoch())issues.add("Snapshot della connessione precedente");
        for(var c:s.frozen.configuration().symbols())if(c.enabled()){
            var expected=s.trades.stream().filter(t->t.symbol.equals(c.symbol())).map(t->t.remaining().multiply(t.direction.equals("LONG")?BigDecimal.ONE:BigDecimal.ONE.negate())).reduce(BigDecimal.ZERO,BigDecimal::add);
            var actual=snapshot.positions().stream().filter(p->p.account().equals(s.account)&&p.symbol().equals(c.symbol())).map(BrokerPosition::quantity).reduce(BigDecimal.ZERO,BigDecimal::add);
            if(expected.compareTo(actual)!=0)issues.add("Posizione broker diversa dal ledger V2: "+c.symbol()+" ledger="+expected+" broker="+actual);
        }
        for(var o:s.orders)if(!o.terminal()&&snapshot.orders().stream().noneMatch(r->r.id()==o.orderId))issues.add("Ordine non risolto (nessun reinvio): "+o.orderId);
        for(var t:s.trades)if(t.remaining().signum()>0&&!protectedTrade(t,snapshot.orders(),s.account))issues.add("Protezione mancante/incoerente: "+t.symbol);
        return issues;
    }
    static boolean protectedTrade(Trade t,List<BrokerOrder> orders,String account){
        for(var role:List.of(Role.STOP_LOSS,Role.TAKE_PROFIT)){
            var matching=orders.stream().filter(o->o.account().equals(account)&&o.symbol().equals(t.symbol)&&o.clientOrderId().equals("PV2:"+t.signalId+":"+role)&&Set.of("Submitted","PreSubmitted").contains(o.status())&&o.action().equals(t.direction.equals("LONG")?"SELL":"BUY")&&o.quantity().subtract(o.filled()).compareTo(t.remaining())==0
                    &&("PV2:"+t.signalId).equals(o.ocaGroup())&&(role==Role.STOP_LOSS?o.stop()!=null&&o.stop().compareTo(t.stop)==0&&o.type().equals("STP"):o.limit()!=null&&o.limit().compareTo(t.target)==0&&o.type().equals("LMT"))).toList();
            if(matching.size()!=1)return false;
            // A held attached child is not accepted as protection for a partially filled parent.
            var child=matching.getFirst();if(child.parentId()!=0&&orders.stream().anyMatch(p->p.id()==child.parentId()&&!p.status().equals("Filled")))return false;
        }return true;
    }
    private void markAndCount(Instant now){
        var quotes=new HashMap<String,Quote>();for(var c:store.session().frozen.configuration().symbols())if(c.enabled())quotes.put(c.symbol(),broker.quote(c.symbol()));
        store.update(s->{for(var t:s.trades)if(t.remaining().signum()>0){
            var q=quotes.get(t.symbol);if(q!=null&&q.last()!=null&&q.time()!=null&&!q.time().isAfter(now)&&Duration.between(q.time(),now).compareTo(Duration.ofSeconds(30))<0)t.mark=q.last();
            var calendar=calendars.get(t.symbol);var local=calendar.localDate(now);t.holdingBars=calendar.holdingBars(t.entryDate,local);t.lastCountedSession=local;
            }
            var point=PatternPaperLedger.capital(s,now);s.peakEquity=s.peakEquity.max(point.equity());s.maximumDrawdown=s.maximumDrawdown.max(point.drawdownPercent());
            if(s.equityCurve.isEmpty()||Duration.between(s.equityCurve.getLast().time(),now).compareTo(Duration.ofMinutes(1))>=0)s.equityCurve.add(point);return null;});
    }
    private void manageProtectionAndTimeout(Instant now){
        var state=store.session();var c=broker.connection();
        for(var trade:state.trades)if(trade.remaining().signum()>0){
            if(!protectedTrade(trade,lastSnapshot.orders(),state.account)){
                fail("Posizione senza protezione valida: "+trade.symbol+". Nuovi ingressi bloccati, ripristino richiesto");
                if(c.paperIdentityVerified()&&c.writesVerified()){
                    var repairs=broker.restoreProtection(trade,lastSnapshot.orders(),state.account);var signal=state.signals.stream().filter(r->r.id.equals(trade.signalId)).findFirst().orElseThrow();reserveAndTransmit(signal,repairs,true);
                }continue;
            }
            var config=state.frozen.configuration();int maximum=config.symbols().stream().filter(s->s.symbol().equals(trade.symbol)).findFirst().orElseThrow().holding(config.global());
            var session=calendars.get(trade.symbol).session(calendars.get(trade.symbol).localDate(now)).orElse(null);
            if(trade.holdingBars>=maximum&&!trade.timeoutSubmitted&&session!=null&&!now.isBefore(session.close().minusSeconds(1800))){
                if(!now.isBefore(session.close())){fail("TIMEOUT mancato: Gateway/app non disponibile prima della chiusura. Stop mantenuto su "+trade.symbol);continue;}
                if(!c.paperIdentityVerified()||!c.writesVerified()){fail("TIMEOUT bloccato: account/permessi non verificati. Stop mantenuti");continue;}
                var command=broker.timeout(trade,trade.remaining().longValueExact(),state.account,"PV2:"+trade.signalId);
                store.update(s->{s.trades.stream().filter(t->t.signalId.equals(trade.signalId)).findFirst().orElseThrow().timeoutSubmitted=true;return null;});
                var signal=state.signals.stream().filter(r->r.id.equals(trade.signalId)).findFirst().orElseThrow();reserveAndTransmit(signal,List.of(command),true);
            }
        }
    }
    private void cancelUnfilledEntries(){
        var c=broker.connection();if(!c.connected()||!c.paperIdentityVerified()||!c.writesVerified()||lastSnapshot==null||!lastSnapshot.complete())return;
        var state=store.session();
        for(var order:lastSnapshot.orders())if(order.account().equals(state.account)&&state.orders.stream().anyMatch(o->o.orderId==order.id()&&o.role==Role.ENTRY)&&order.quantity().subtract(order.filled()).signum()>0&&!Set.of("Filled","Cancelled","ApiCancelled","Inactive").contains(order.status())){
            if(order.filled().signum()>0&&state.trades.stream().filter(t->t.symbol.equals(order.symbol())&&t.remaining().signum()>0).anyMatch(t->!protectedTrade(t,lastSnapshot.orders(),state.account))){fail("Cancellazione residuo parent rinviata finche le protezioni del partial fill non sono valide: "+order.symbol());continue;}
            try{broker.cancelVerifiedUnfilledEntry(order);store.update(s->{store.event(s,"ENTRY_CANCEL_REQUEST",order.symbol(),order.id(),null,"Ingresso non eseguito; stop protettivi mantenuti",false);return null;});}
            catch(RuntimeException e){fail("Cancellazione ingresso non confermata: "+order.id());}
        }
    }
    private List<Check> checks(Session s){
        var now=Instant.now();var c=broker.connection();var checks=new ArrayList<Check>();
        checks.add(new Check("PAPER_ONLY",properties.getMode()==TradingMode.PAPER&&!properties.isLiveTradingEnabled(),"Modalita backend deve essere PAPER, LIVE disabilitato"));
        checks.add(new Check("CONNECTION",c.connected(),"IB Gateway non collegato"));
        checks.add(new Check("HEARTBEAT",c.heartbeat()!=null&&!c.heartbeat().isAfter(now.plusSeconds(5))&&Duration.between(c.heartbeat()==null?Instant.EPOCH:c.heartbeat(),now).compareTo(Duration.ofSeconds(45))<0,"Heartbeat broker mancante/obsoleto"));
        checks.add(new Check("ACCOUNT_ALLOWLIST",!c.account().isBlank()&&c.account().equals(properties.getIbkr().getAccountId())&&c.managedAccounts().size()==1&&c.managedAccounts().contains(c.account()),"Account esatto backend non presente come unico account autorizzato"));
        checks.add(new Check("PAPER_IDENTITY",c.paperIdentityVerified(),c.identityReason()));
        checks.add(new Check("WRITE_CAPABILITY",c.writesVerified(),"API in sola lettura o permessi non verificati"));
        checks.add(new Check("REALTIME_DATA",c.realtimeData(),"Market data realtime richiesti per esecuzione automatica"));
        checks.add(new Check("FROZEN_CONFIGURATION",s.frozen!=null&&stable.version("1.0").configurationHash().equals(s.configurationHash)&&PatternStableStore.hash(s.frozen.configuration()).equals(s.configurationHash)&&!s.frozen.configuration().global().useLeverage(),"Snapshot V2/hash/versione non coerenti"));
        checks.add(new Check("KILL_SWITCH",!s.killSwitch&&!globalKill.isActive(),"Kill switch attivo"));
        checks.add(new Check("RECONCILIATION",lastSnapshot!=null&&lastSnapshot.complete()&&s.lastReconciliation!=null&&lastSnapshot.epoch()==c.epoch()&&Duration.between(lastSnapshot.time(),now).compareTo(Duration.ofSeconds(45))<0,"Ordini/posizioni/protezioni non riconciliati"));
        boolean limits=true;try{s.limits.validate();}catch(RuntimeException e){limits=false;}checks.add(new Check("OPERATIONAL_LIMITS",limits,"Limiti supplementari da configurare esplicitamente"));
        boolean calendar=s.frozen.configuration().symbols().stream().filter(v->v.enabled()).allMatch(v->{var value=calendars.get(v.symbol());return value!=null&&value.fresh(now)&&value.coveredDates().contains(value.localDate(now))&&value.next(now).isPresent();});
        checks.add(new Check("MARKET_CALENDARS",calendar,"Copertura calendario/sessione successiva mancante"));
        var capital=PatternPaperLedger.capital(s,now);
        checks.add(new Check("CAPITAL",capital.equity().signum()>0&&capital.available().signum()>0, "Capitale virtuale non disponibile"));
        if(limits){
            var day=now.atZone(ZoneId.of("America/New_York")).toLocalDate();var dayStart=s.equityCurve.stream().filter(p->p.time().atZone(ZoneId.of("America/New_York")).toLocalDate().isBefore(day)).max(Comparator.comparing(EquityPoint::time)).map(EquityPoint::equity).orElse(s.initialCapital);
            checks.add(new Check("DAILY_LOSS",dayStart.subtract(capital.equity()).compareTo(s.limits.dailyLoss())<0,"Daily Loss Limit raggiunto"));
            checks.add(new Check("DRAWDOWN",capital.drawdownPercent().compareTo(s.limits.portfolioDrawdownPercent())<0,"Portfolio Drawdown Limit raggiunto"));
            long positions=s.trades.stream().filter(t->t.remaining().signum()>0).count()+s.orders.stream().filter(o->o.role==Role.ENTRY&&!o.terminal()&&s.trades.stream().noneMatch(t->t.signalId.equals(o.signalId)&&t.remaining().signum()>0)).count();
            checks.add(new Check("MAX_POSITIONS",positions<s.limits.maxOpenPositions(),"Maximum Open Positions raggiunto"));
            long entries=s.orders.stream().filter(o->o.role==Role.ENTRY&&o.createdAt.atZone(ZoneId.of("America/New_York")).toLocalDate().equals(day)).count();
            checks.add(new Check("MAX_DAILY_ORDERS",entries<s.limits.maxNewOrdersPerDay(),"Maximum New Orders Per Day raggiunto"));
            checks.add(new Check("AGGREGATE_EXPOSURE",capital.longExposure().add(capital.shortExposure()).compareTo(capital.equity().multiply(s.limits.maxAggregateExposurePercent()).divide(BigDecimal.valueOf(100)))<=0,"Esposizione aggregata eccessiva"));
        }
        for(var limitation:broker.limitations())checks.add(new Check("PRODUCTION_VALIDATION",false,limitation));
        return checks;
    }
    public static String signalId(String version,it.andreademartini.trading.strategy.patternv1.PatternV1Strategy.Signal signal){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(("PATTERN_V2|"+version+"|"+signal.symbol()+"|"+signal.detection().direction()+"|"+signal.detection().patternType()+"|"+signal.detection().signalDate()).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
    }
    private void error(Session s,String reason){if(!s.killSwitch)s.mode=Mode.ERROR;s.reason=reason;s.generation++;store.event(s,"ERROR",null,null,null,reason,true);}
    private void fail(String reason){store.update(s->{if(!Objects.equals(s.reason,reason))error(s,reason);return null;});}
}
