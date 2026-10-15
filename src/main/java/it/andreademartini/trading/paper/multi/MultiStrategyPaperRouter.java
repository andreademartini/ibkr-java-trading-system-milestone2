package it.andreademartini.trading.paper.multi;

import static it.andreademartini.trading.paper.patternv1.PatternPaperModel.*;
import it.andreademartini.trading.paper.patternv1.*;
import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.orders.IbkrOrderCommand;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/** Single broker consumer and serialized write boundary. No account-netting is used as strategy ownership. */
public final class MultiStrategyPaperRouter {
    public static final String V1="PATTERN_V1",V2="PATTERN_V2";
    public record Reconciliation(String account,Instant time,boolean complete,boolean simultaneousCertified,
            BigDecimal availableFunds,List<Quantity> positions,List<String> issues) {}
    public record Quantity(String symbol,BigDecimal v1,BigDecimal v2,BigDecimal attributed,BigDecimal broker,boolean reconciled) {}
    private final PatternPaperBroker raw;
    private final Map<String,Supplier<Session>> sessions;
    private final HistoricalDataService historical;
    private final boolean simultaneousCertified;
    private java.util.function.Consumer<String> faultHandler=reason->{};
    private BrokerSnapshot last;
    private final Map<String,ArrayDeque<ExecutionRecord>> fills=new HashMap<>();
    private final Map<String,ArrayDeque<CommissionRecord>> fees=new HashMap<>();
    private final Map<String,ArrayDeque<BrokerOrder>> orders=new HashMap<>();
    private final Map<String,ArrayDeque<String>> errors=new HashMap<>();
    private final Map<String,CommissionRecord> pendingFees=new LinkedHashMap<>();
    public MultiStrategyPaperRouter(PatternPaperBroker raw,Supplier<Session> v1,Supplier<Session> v2,HistoricalDataService historical,boolean simultaneousCertified){
        this.raw=raw;this.sessions=Map.of(V1,v1,V2,v2);this.historical=historical;this.simultaneousCertified=simultaneousCertified;
        for(var id:sessions.keySet()){fills.put(id,new ArrayDeque<>());fees.put(id,new ArrayDeque<>());orders.put(id,new ArrayDeque<>());errors.put(id,new ArrayDeque<>());}
    }
    public PatternPaperBroker view(String id){if(!sessions.containsKey(id))throw new IllegalArgumentException("Research strategies cannot use PAPER broker");return new View(id);}
    public void onAccountFault(java.util.function.Consumer<String> handler){faultHandler=Objects.requireNonNull(handler);}
    private Session state(String id){return sessions.get(id).get();}
    private String owner(int orderId){String owner=null;for(var id:sessions.keySet())if(state(id).orders.stream().anyMatch(o->o.orderId==orderId)){if(owner!=null)throw new IllegalStateException("Duplicate broker order identity across strategies: "+orderId);owner=id;}return owner;}
    private static boolean engaged(Session s){return s.mode==Mode.PAPER_AUTO||s.resumeRequested||s.trades.stream().anyMatch(t->t.remaining().signum()>0)||s.orders.stream().anyMatch(o->o.role==Role.ENTRY&&!o.terminal());}
    private static boolean terminal(String s){return Set.of("Filled","Cancelled","ApiCancelled","Inactive","REJECTED").contains(s);}
    private void broadcast(String error){faultHandler.accept(error);errors.values().forEach(q->q.add(error));}
    private void pump(){
        for(var f:raw.drainExecutions()){var id=owner(f.orderId());if(id!=null)fills.get(id).add(f);else broadcast("UNATTRIBUTED_EXECUTION: "+f.executionId());}
        for(var o:raw.drainOrders()){var id=owner(o.id());if(id!=null)orders.get(id).add(o);else if(!terminal(o.status()))broadcast("UNATTRIBUTED_ORDER: "+o.id());}
        for(var fee:raw.drainCommissions())pendingFees.put(fee.executionId(),fee);
        var it=pendingFees.entrySet().iterator();while(it.hasNext()){var item=it.next();String owner=null;for(var id:sessions.keySet())if(state(id).executions.stream().anyMatch(e->e.executionId().equals(item.getKey()))||fills.get(id).stream().anyMatch(e->e.executionId().equals(item.getKey())))owner=id;if(owner!=null){fees.get(owner).add(item.getValue());it.remove();}}
        raw.drainErrors().forEach(this::broadcast);
    }
    private Session projected(String id,BrokerSnapshot snapshot){
        var s=PatternPaperStore.read(PatternPaperStore.json(state(id)),Session.class);
        if(s.account.isBlank())s.account=raw.connection().account();
        for(var f:snapshot.executions())if(s.orders.stream().anyMatch(o->o.orderId==f.orderId()))PatternPaperLedger.execution(s,f);
        for(var f:snapshot.commissions())if(s.executions.stream().anyMatch(e->e.executionId().equals(f.executionId())))PatternPaperLedger.commission(s,f);
        return s;
    }
    private static BigDecimal quantity(Session s,String symbol){return s.trades.stream().filter(t->t.symbol.equals(symbol)).map(t->t.remaining().multiply(t.direction.equals("LONG")?BigDecimal.ONE:BigDecimal.ONE.negate())).reduce(BigDecimal.ZERO,BigDecimal::add);}
    public synchronized Reconciliation reconciliation(){return reconcileReport(last);}
    private Reconciliation reconcileReport(BrokerSnapshot snapshot){
        var c=raw.connection();if(snapshot==null)return new Reconciliation(c.account(),null,false,simultaneousCertified,null,List.of(),List.of("No account reconciliation snapshot"));
        var issues=new ArrayList<String>();var rows=new ArrayList<Quantity>();
        if(!snapshot.complete())issues.add(snapshot.reason());if(snapshot.epoch()!=c.epoch())issues.add("Broker connection epoch changed");
        if(snapshot.time()==null||Duration.between(snapshot.time(),Instant.now()).compareTo(Duration.ofSeconds(45))>0)issues.add("Account snapshot expired");
        var a=projected(V1,snapshot);var b=projected(V2,snapshot);var symbols=new TreeSet<String>();
        a.trades.forEach(t->symbols.add(t.symbol));b.trades.forEach(t->symbols.add(t.symbol));snapshot.positions().stream().filter(p->p.account().equals(c.account())).forEach(p->symbols.add(p.symbol()));
        for(var symbol:symbols){var v1=quantity(a,symbol);var v2=quantity(b,symbol);var actual=snapshot.positions().stream().filter(p->p.account().equals(c.account())&&p.symbol().equals(symbol)).map(BrokerPosition::quantity).reduce(BigDecimal.ZERO,BigDecimal::add);var expected=v1.add(v2);boolean ok=expected.compareTo(actual)==0;if(!ok)issues.add("ACCOUNT_POSITION_MISMATCH: "+symbol);if(v1.signum()*v2.signum()<0)issues.add("MULTI_STRATEGY_POSITION_CONFLICT: "+symbol);rows.add(new Quantity(symbol,v1,v2,expected,actual,ok));}
        for(var o:snapshot.orders())if(o.account().equals(c.account())&&!terminal(o.status())&&owner(o.id())==null)issues.add("UNATTRIBUTED_OPEN_ORDER: "+o.id());
        if(snapshot.availableFunds()==null||snapshot.availableFunds().signum()<0)issues.add("Account AvailableFunds unavailable");
        return new Reconciliation(c.account(),snapshot.time(),issues.isEmpty(),simultaneousCertified,snapshot.availableFunds(),List.copyOf(rows),List.copyOf(issues));
    }
    private synchronized BrokerSnapshot scoped(String id){
        last=raw.reconcile();var report=reconcileReport(last);if(!report.complete())faultHandler.accept(String.join("; ",report.issues()));var s=projected(id,last);
        var positions=s.trades.stream().map(t->t.symbol).distinct().map(symbol->new BrokerPosition(raw.connection().account(),symbol,quantity(s,symbol),BigDecimal.ZERO)).toList();
        var ownOrders=last.orders().stream().filter(o->id.equals(owner(o.id()))).toList();var ownFills=last.executions().stream().filter(f->id.equals(owner(f.orderId()))).toList();
        var ownFees=last.commissions().stream().filter(f->s.executions.stream().anyMatch(e->e.executionId().equals(f.executionId()))).toList();
        // Reserve other pending entries conservatively, even if broker already reflects part of the reservation.
        var other=state(id.equals(V1)?V2:V1);var reserved=pendingNotional(other);
        return new BrokerSnapshot(last.epoch(),last.time(),positions,ownOrders,ownFills,ownFees,last.availableFunds()==null?BigDecimal.ZERO:last.availableFunds().subtract(reserved).max(BigDecimal.ZERO),report.complete(),String.join("; ",report.issues()));
    }
    private static BigDecimal pendingNotional(Session s){var sum=BigDecimal.ZERO;for(var o:s.orders)if(o.role==Role.ENTRY&&!o.terminal()){var signal=s.signals.stream().filter(r->r.id.equals(o.signalId)).findFirst().orElseThrow();if(signal.expectedEntry==null)throw new IllegalStateException("Entry reservation price missing");var filled=s.executions.stream().filter(e->e.orderId()==o.orderId).map(ExecutionRecord::quantity).reduce(BigDecimal.ZERO,BigDecimal::add);sum=sum.add(BigDecimal.valueOf(o.quantity).subtract(filled).max(BigDecimal.ZERO).multiply(signal.expectedEntry));}return sum;}
    private synchronized void assertEntry(String id,String symbol,boolean isLong){
        var other=state(id.equals(V1)?V2:V1);
        if(other.trades.stream().anyMatch(t->t.symbol.equals(symbol)&&t.remaining().signum()>0&&t.direction.equals("LONG")!=isLong)||other.orders.stream().anyMatch(o->o.symbol.equals(symbol)&&o.role==Role.ENTRY&&!o.terminal()&&o.action.equals("BUY")!=isLong))throw new IllegalStateException("MULTI_STRATEGY_POSITION_CONFLICT");
        if(!simultaneousCertified&&engaged(other))throw new IllegalStateException("MULTI_STRATEGY_VALIDATION_REQUIRED: simultaneous PAPER_AUTO is disabled");
        if(!simultaneousCertified)throw new IllegalStateException("ACCOUNT_CASH_BUDGET_UNVERIFIED: AvailableFunds alone does not prove unlevered account cash capacity");
    }
    private synchronized void place(String id,IbkrOrderCommand command){
        var c=raw.connection();if(!c.connected()||!c.paperIdentityVerified()||!c.writesVerified()||!c.account().equals(command.order().account()))throw new IllegalStateException("Verified PAPER account/write capability required");
        if(!id.equals(owner(command.orderId())))throw new IllegalStateException("Order ownership not durably reserved");
        var report=reconcileReport(last);if(!report.complete())throw new IllegalStateException("Account reconciliation required: "+report.issues());
        var s=state(id);
        if(command.role().equals("ENTRY")){
            assertEntry(id,command.symbol(),command.side().name().equals("BUY"));
            if(pendingNotional(state(V1)).add(pendingNotional(state(V2))).compareTo(last.availableFunds())>0)throw new IllegalStateException("AGGREGATE_ACCOUNT_FUNDS_EXCEEDED");
        }else{
            var order=s.orders.stream().filter(o->o.orderId==command.orderId()).findFirst().orElseThrow();var trade=s.trades.stream().filter(t->t.signalId.equals(order.signalId)).findFirst().orElseThrow();
            if(command.quantity()<1||BigDecimal.valueOf(command.quantity()).compareTo(trade.remaining())>0||command.side().name().equals(trade.direction.equals("LONG")?"BUY":"SELL"))throw new IllegalStateException("Exit exceeds owned strategy quantity or has wrong side");
        }
        raw.place(command);
    }
    private static BrokerOrder translated(BrokerOrder o,String from,String to){return new BrokerOrder(o.id(),o.permId(),o.parentId(),o.account(),o.symbol(),o.clientOrderId().replace(from,to),o.action(),o.type(),o.quantity(),o.filled(),o.status(),o.stop(),o.limit(),o.ocaGroup()==null?null:o.ocaGroup().replace(from,to));}
    private static List<IbkrOrderCommand> tag(List<IbkrOrderCommand> commands,String id){if(id.equals(V2))for(var cmd:commands){cmd.order().orderRef(cmd.order().orderRef().replace("PV1:","PV2:"));if(cmd.order().ocaGroup()!=null)cmd.order().ocaGroup(cmd.order().ocaGroup().replace("PV1:","PV2:"));}return commands;}
    private final class View implements PatternPaperBroker {
        private final String id;View(String id){this.id=id;}
        public Connection connection(){return raw.connection();}
        public void connectReadOnly(){synchronized(MultiStrategyPaperRouter.this){raw.connectReadOnly();}}
        public BrokerSnapshot reconcile(){return scoped(id);}
        public PatternPaperCalendar calendar(String symbol){synchronized(MultiStrategyPaperRouter.this){return raw.calendar(symbol);}}
        public List<HistoricalBar> dailyBars(String symbol){synchronized(MultiStrategyPaperRouter.this){var bars=raw.dailyBars(symbol);if(id.equals(V1)||historical==null||state(id).frozen==null)return bars;var c=state(id).frozen.configuration();var merged=new TreeMap<LocalDate,HistoricalBar>();historical.load(symbol,c.global().startDate(),LocalDate.now(),BarInterval.DAILY).forEach(b->merged.put(b.getDateTime().toLocalDate(),b));bars.forEach(b->merged.put(b.getDateTime().toLocalDate(),b));var result=new ArrayList<HistoricalBar>();for(var b:merged.values())result.add(b.withIndex(result.size()));return List.copyOf(result);}}
        public Quote quote(String symbol){return raw.quote(symbol);}
        public List<IbkrOrderCommand> bracket(SignalRecord signal,long quantity,String account){synchronized(MultiStrategyPaperRouter.this){assertEntry(id,signal.symbol,signal.signal.detection().direction().name().equals("LONG"));return tag(raw.bracket(signal,quantity,account),id);}}
        public IbkrOrderCommand timeout(Trade trade,long quantity,String account,String group){synchronized(MultiStrategyPaperRouter.this){return tag(List.of(raw.timeout(trade,quantity,account,group)),id).getFirst();}}
        public void place(IbkrOrderCommand command){MultiStrategyPaperRouter.this.place(id,command);}
        public void cancelVerifiedUnfilledEntry(BrokerOrder order){synchronized(MultiStrategyPaperRouter.this){if(!id.equals(owner(order.id())))throw new IllegalStateException("Cannot cancel another strategy order");raw.cancelVerifiedUnfilledEntry(id.equals(V2)?translated(order,"PV2:","PV1:"):order);}}
        public List<IbkrOrderCommand> restoreProtection(Trade trade,List<BrokerOrder> existing,String account){synchronized(MultiStrategyPaperRouter.this){if(state(id).trades.stream().noneMatch(t->t.signalId.equals(trade.signalId)&&t.remaining().compareTo(trade.remaining())==0))throw new IllegalStateException("Unowned protective order");return tag(raw.restoreProtection(trade,id.equals(V2)?existing.stream().map(o->translated(o,"PV2:","PV1:")).toList():existing,account),id);}}
        public List<ExecutionRecord> drainExecutions(){synchronized(MultiStrategyPaperRouter.this){pump();var list=List.copyOf(fills.get(id));fills.get(id).clear();return list;}}
        public List<CommissionRecord> drainCommissions(){synchronized(MultiStrategyPaperRouter.this){pump();var list=List.copyOf(fees.get(id));fees.get(id).clear();return list;}}
        public List<BrokerOrder> drainOrders(){synchronized(MultiStrategyPaperRouter.this){pump();var list=List.copyOf(orders.get(id));orders.get(id).clear();return list;}}
        public List<String> drainErrors(){synchronized(MultiStrategyPaperRouter.this){pump();var list=List.copyOf(errors.get(id));errors.get(id).clear();return list;}}
        public List<String> limitations(){var list=new ArrayList<>(raw.limitations());if(!simultaneousCertified){list.add("ACCOUNT_CASH_BUDGET_UNVERIFIED: AvailableFunds alone does not certify account cash/net-liquidation gross exposure limits without leverage");if(engaged(state(id.equals(V1)?V2:V1)))list.add("MULTI_STRATEGY_VALIDATION_REQUIRED: simultaneous PAPER_AUTO disabled pending certified broker protection/recovery");}return List.copyOf(list);}
    }
}
