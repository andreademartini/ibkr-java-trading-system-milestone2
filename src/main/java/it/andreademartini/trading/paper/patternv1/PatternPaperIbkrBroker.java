package it.andreademartini.trading.paper.patternv1;

import static it.andreademartini.trading.paper.patternv1.PatternPaperModel.*;
import com.ib.client.*;
import it.andreademartini.trading.config.*;
import it.andreademartini.trading.ibkr.*;
import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.orders.*;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Read-only production adapter. No invented account attestation or automatic write-mode switch. */
@Service
public class PatternPaperIbkrBroker implements PatternPaperBroker,IbkrEventListener {
    private final TradingSystemProperties properties;
    private final IbkrConnectionService connection;
    private final IbkrApiClient api;
    private final IbkrOrderFactory orders;
    private final IbkrContractFactory contracts;
    private final MarketDataService marketData;
    private final IbkrHistoricalDataClient historical;
    private final AtomicInteger ids=new AtomicInteger(800_000);
    private final ConcurrentLinkedQueue<ExecutionRecord> executionQueue=new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<CommissionRecord> commissionQueue=new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<BrokerOrder> orderQueue=new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> errors=new ConcurrentLinkedQueue<>();
    private final Map<Integer,BrokerOrder> observedOrders=new ConcurrentHashMap<>();
    private final Map<Integer,List<ContractDetails>> contractResults=new ConcurrentHashMap<>();
    private final Map<Integer,CompletableFuture<List<ContractDetails>>> contractFutures=new ConcurrentHashMap<>();
    private final Map<String,PatternPaperCalendar> calendars=new ConcurrentHashMap<>();
    private final Map<String,Integer> subscriptions=new ConcurrentHashMap<>();
    private final Map<Integer,Integer> marketTypes=new ConcurrentHashMap<>();
    private final Map<String,BigDecimal> shortable=new ConcurrentHashMap<>();
    private volatile List<String> accounts=List.of();
    private volatile Instant connectedAt,heartbeat;
    private volatile long epoch;
    private volatile Pending pending;
    public PatternPaperIbkrBroker(TradingSystemProperties properties,IbkrConnectionService connection,IbkrApiClient api,
            IbkrOrderFactory orders,IbkrContractFactory contracts,MarketDataService marketData,IbkrHistoricalDataClient historical,IbkrEWrapper wrapper){
        this.properties=properties;this.connection=connection;this.api=api;this.orders=orders;this.contracts=contracts;this.marketData=marketData;this.historical=historical;wrapper.addListener(this);
    }
    public List<String> limitations(){return List.of(
            "PAPER_IDENTITY_UNVERIFIED: TWS managedAccounts/AccountType non provano che il conto sia simulato. Manca una fonte indipendente attendibile legata alla sessione.",
            "API_WRITE_CAPABILITY_UNVERIFIED: nessun ordine di prova viene inviato per verificare il permesso di scrittura.",
            "NEXT_BAR_OPEN_UNVERIFIED: occorre un feed realtime con OPEN identificato per sessione prima di abilitare l'esecuzione.",
            "SHORT_CAPABILITY_UNVERIFIED: disponibilita prestito e margine non confermati tramite un controllo dedicato per ordine.",
            "BRACKET_RECOVERY_UNVALIDATED: partial fill, ripristino stop e uscita TIMEOUT/OCA richiedono una validazione PAPER esplicita prima dell'uso reale.");}
    public Connection connection(){
        return new Connection(connection.isReady(),properties.getIbkr().getHost(),properties.getIbkr().getPort(),properties.getIbkr().getAccountId(),accounts,
                false,false,properties.getMarketData().getMode()==MarketDataMode.LIVE,limitations().getFirst(),connectedAt,heartbeat,epoch);
    }
    public void connectReadOnly(){
        if(properties.getMode()!=TradingMode.PAPER||properties.isLiveTradingEnabled()||properties.isOrderSubmissionEnabled()||properties.getStrategy().isAutoExecuteSignals())
            throw new IllegalStateException("Connessione iniziale richiede PAPER, ordini globali e auto-execute disabilitati");
        connection.connect();api.requestCurrentTime();
    }
    @Override public void onNextValidId(int id){epoch++;connectedAt=Instant.now();heartbeat=null;accounts=List.of();calendars.clear();subscriptions.clear();marketTypes.clear();}
    @Override public void onManagedAccounts(List<String> accounts){this.accounts=List.copyOf(accounts);}
    @Override public void onCurrentTime(long seconds){heartbeat=Instant.ofEpochSecond(seconds);}
    @Override public void onConnectionClosed(){epoch++;accounts=List.of();heartbeat=null;var p=pending;if(p!=null)p.finished.completeExceptionally(new IllegalStateException("Connessione persa durante riconciliazione"));}
    public synchronized BrokerSnapshot reconcile(){
        if(!connection.isReady())return new BrokerSnapshot(epoch,Instant.now(),List.of(),List.of(),List.of(),List.of(),BigDecimal.ZERO,false,"IBKR disconnesso");
        var p=new Pending(epoch,ids.getAndIncrement(),ids.getAndIncrement());pending=p;
        try{
            api.requestCurrentTime();api.requestPositions();api.requestAllOpenOrders();api.requestCompletedOrders();
            api.requestExecutions(p.executionId,properties.getIbkr().getAccountId());
            api.requestAccountSummary(p.accountId,"AvailableFunds,AccountType");
            p.finished.get(20,TimeUnit.SECONDS);
            return new BrokerSnapshot(p.epoch,Instant.now(),List.copyOf(p.positions.values()),List.copyOf(p.orders.values()),List.copyOf(p.executions),List.copyOf(p.commissions),p.funds,
                    epoch==p.epoch&&connection.isReady(),epoch==p.epoch?"Snapshot completo":"Sessione cambiata");
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Riconciliazione interrotta",e);}
        catch(Exception e){return new BrokerSnapshot(p.epoch,Instant.now(),List.copyOf(p.positions.values()),List.copyOf(p.orders.values()),List.copyOf(p.executions),List.copyOf(p.commissions),p.funds,false,"Riconciliazione incompleta: "+e.getClass().getSimpleName());}
        finally{pending=null;try{api.cancelAccountSummary(p.accountId);}catch(RuntimeException ignored){}}
    }
    @Override public void onPosition(String account,Contract contract,Object position,double avg){var p=pending;if(p!=null&&account.equals(properties.getIbkr().getAccountId()))p.positions.put(contract.symbol(),new BrokerPosition(account,contract.symbol(),IbkrNumber.toBigDecimal(position),BigDecimal.valueOf(avg)));}
    @Override public void onPositionEnd(){var p=pending;if(p!=null){p.positionEnd=true;p.complete();}}
    @Override public void onOpenOrder(int id,Contract contract,Order order,OrderState state){
        var record=order(id,contract,order,state);observedOrders.put(id,record);orderQueue.add(record);var p=pending;if(p!=null)p.orders.put(id,record);
    }
    @Override public void onOpenOrderEnd(){var p=pending;if(p!=null){p.openEnd=true;p.complete();}}
    @Override public void onCompletedOrder(Contract c,Order o,OrderState state){onOpenOrder(o.orderId(),c,o,state);}
    @Override public void onCompletedOrdersEnd(){var p=pending;if(p!=null){p.completedEnd=true;p.complete();}}
    private BrokerOrder order(int id,Contract c,Order o,OrderState state){
        return new BrokerOrder(id,o.permId(),o.parentId(),o.account(),c.symbol(),o.orderRef(),o.action().toString(),o.orderType().toString(),IbkrNumber.toBigDecimal(o.totalQuantity()),BigDecimal.ZERO,state.status().toString(),positive(o.auxPrice()),positive(o.lmtPrice()),o.ocaGroup());
    }
    @Override public void onOrderStatus(IbkrOrderStatusUpdate u){
        var prior=observedOrders.get(u.orderId());if(prior==null)return;
        var updated=new BrokerOrder(prior.id(),u.permanentId(),prior.parentId(),prior.account(),prior.symbol(),prior.clientOrderId(),prior.action(),prior.type(),prior.quantity(),u.filled(),u.status(),prior.stop(),prior.limit(),prior.ocaGroup());
        observedOrders.put(u.orderId(),updated);orderQueue.add(updated);var p=pending;if(p!=null)p.orders.put(u.orderId(),updated);
    }
    @Override public void onExecution(int requestId,Contract c,Execution e){
        if(!e.acctNumber().equals(properties.getIbkr().getAccountId()))return;
        try{
            if(e.pendingPriceRevision())throw new IllegalStateException("Execution pending price revision: "+e.execId());
            var time=parseExecutionTime(e.time());var fill=new ExecutionRecord(e.execId(),null,e.orderId(),e.permId(),e.acctNumber(),c.symbol(),e.side().equals("BOT")?"BUY":"SELL",IbkrNumber.toBigDecimal(e.shares()),BigDecimal.valueOf(e.price()),time,null);
            executionQueue.add(fill);var p=pending;if(p!=null&&requestId==p.executionId)p.executions.add(fill);
        }catch(RuntimeException failure){errors.add(failure.getMessage());}
    }
    static Instant parseExecutionTime(String value){
        if(value==null)throw new IllegalArgumentException("Execution timestamp missing");
        String normalized=value.trim().replaceAll(" +"," ");String[] fields=normalized.split(" ");
        if(fields.length!=3)throw new IllegalArgumentException("Execution timezone missing; configure Operator Time Zone in TWS API");
        var date=LocalDate.parse(fields[0],DateTimeFormatter.BASIC_ISO_DATE);var time=LocalTime.parse(fields[1]);
        var zone=ZoneId.of(fields[2].equals("US/Eastern")?"America/New_York":fields[2]);
        return LocalDateTime.of(date,time).atZone(zone).toInstant();
    }
    @Override public void onExecutionEnd(int id){var p=pending;if(p!=null&&id==p.executionId){p.executionEnd=true;p.complete();}}
    @Override public void onCommissionAndFees(CommissionAndFeesReport report){
        if(!Double.isFinite(report.commissionAndFees())){errors.add("Invalid commission report");return;}
        var fee=new CommissionRecord(report.execId(),BigDecimal.valueOf(report.commissionAndFees()),report.currency(),Instant.now());commissionQueue.add(fee);var p=pending;if(p!=null)p.commissions.add(fee);
    }
    @Override public void onAccountSummary(int id,String account,String tag,String value,String currency){var p=pending;if(p!=null&&id==p.accountId&&account.equals(properties.getIbkr().getAccountId())&&tag.equals("AvailableFunds")&&currency.equals("USD"))try{p.funds=new BigDecimal(value);}catch(NumberFormatException e){errors.add("AvailableFunds invalid");}}
    @Override public void onAccountSummaryEnd(int id){var p=pending;if(p!=null&&id==p.accountId){p.accountEnd=true;p.complete();}}
    @Override public void onError(IbkrError error){if(!Set.of(2104,2106,2158,2174).contains(error.code()))errors.add("IBKR "+error.code()+": "+error.message());}
    public PatternPaperCalendar calendar(String symbol){
        var existing=calendars.get(symbol);if(existing!=null&&existing.fresh(Instant.now()))return existing;
        int id=ids.getAndIncrement();var future=new CompletableFuture<List<ContractDetails>>();contractResults.put(id,new CopyOnWriteArrayList<>());contractFutures.put(id,future);
        try{
            api.requestContractDetails(id,contracts.create(marketData.requireInstrument(symbol)));
            var details=future.get(20,TimeUnit.SECONDS);
            if(details.size()!=1)throw new IllegalStateException("Contract ambiguity for "+symbol);
            var d=details.getFirst();if(!d.contract().currency().equals("USD")||!d.contract().secType().toString().equals("STK"))throw new IllegalStateException("Only frozen USD stocks/ETFs supported");
            var result=PatternPaperCalendar.parse(symbol,d.timeZoneId(),d.liquidHours(),Instant.now());calendars.put(symbol,result);
            subscriptions.computeIfAbsent(symbol,s->{int request=ids.getAndIncrement();api.requestPatternMarketData(request,contracts.create(marketData.requireInstrument(s)));return request;});
            return result;
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
        catch(Exception e){throw new IllegalStateException("Calendario non disponibile: "+symbol,e);}
        finally{contractResults.remove(id);contractFutures.remove(id);}
    }
    @Override public void onContractDetails(int id,ContractDetails details){var list=contractResults.get(id);if(list!=null)list.add(details);}
    @Override public void onContractDetailsEnd(int id){var f=contractFutures.get(id);if(f!=null)f.complete(List.copyOf(contractResults.get(id)));}
    @Override public void onMarketDataType(int id,int type){marketTypes.put(id,type);}
    @Override public void onTickSize(int id,int field,Object size){if(field==89)subscriptions.forEach((symbol,request)->{if(request==id)shortable.put(symbol,IbkrNumber.toBigDecimal(size));});}
    public List<HistoricalBar> dailyBars(String symbol){return historical.historical(marketData.requireInstrument(symbol),"DAILY","compact",80);}
    public Quote quote(String symbol){
        var q=marketData.snapshot(symbol).orElse(null);if(q==null)return null;
        var id=subscriptions.get(symbol);boolean realtime=id!=null&&marketTypes.getOrDefault(id,0)==1;
        // MarketSnapshot lacks a session-qualified OPEN. Do not relabel a previous day's OPEN as today's.
        return new Quote(null,positive(q.last()),positive(q.bid()),positive(q.ask()),q.updatedAt(),null,realtime,shortable.get(symbol),null);
    }
    public List<IbkrOrderCommand> bracket(SignalRecord signal,long quantity,String account){
        assertVerifiedPaper();
        var request=new PaperOrderRequest(signal.symbol,signal.signal.detection().direction().name().equals("LONG")?OrderSide.BUY:OrderSide.SELL,PaperOrderType.BRACKET,quantity,null,signal.expectedStop.doubleValue(),signal.expectedTarget.doubleValue(),null,"PV1:"+signal.id);
        var commands=orders.create(request,marketData.requireInstrument(signal.symbol),signal.expectedEntry.doubleValue());
        for(var cmd:commands){cmd.order().account(account);cmd.order().orderRef("PV1:"+signal.id+":"+cmd.role());cmd.order().outsideRth(false);cmd.order().tif(cmd.parentOrderId()==null?"DAY":"GTC");if(cmd.parentOrderId()!=null){cmd.order().ocaGroup("PV1:"+signal.id);cmd.order().ocaType(2);}}
        return commands;
    }
    public IbkrOrderCommand timeout(Trade trade,long quantity,String account,String group){
        assertVerifiedPaper();
        var request=new PaperOrderRequest(trade.symbol,trade.direction.equals("LONG")?OrderSide.SELL:OrderSide.BUY,PaperOrderType.MARKET,quantity,null,null,null,null,"PV1:"+trade.signalId+":TIMEOUT");
        var cmd=orders.create(request,marketData.requireInstrument(trade.symbol),trade.mark.doubleValue()).getFirst();
        cmd.order().account(account);cmd.order().orderType("MOC");cmd.order().tif("DAY");cmd.order().ocaGroup(group);cmd.order().ocaType(2);cmd.order().outsideRth(false);
        return new IbkrOrderCommand(cmd.orderId(),null,trade.signalId,"TIMEOUT",cmd.symbol(),cmd.side(),cmd.type(),cmd.direction(),cmd.tradeState(),quantity,cmd.referencePrice(),null,null,null,cmd.contract(),cmd.order());
    }
    public List<IbkrOrderCommand> restoreProtection(Trade trade,List<BrokerOrder> existing,String account){
        assertVerifiedPaper();
        long remaining=trade.remaining().longValueExact();if(remaining<=0)return List.of();
        var repairs=new ArrayList<IbkrOrderCommand>();
        String side=trade.direction.equals("LONG")?"SELL":"BUY";
        for(String role:List.of("STOP_LOSS","TAKE_PROFIT")){
            var prior=existing.stream().filter(o->o.account().equals(account)&&o.clientOrderId().equals("PV1:"+trade.signalId+":"+role)&&!Set.of("Filled","Cancelled","ApiCancelled","Inactive").contains(o.status())).findFirst();
            var request=new PaperOrderRequest(trade.symbol,OrderSide.valueOf(side),role.equals("STOP_LOSS")?PaperOrderType.STOP:PaperOrderType.LIMIT,remaining,role.equals("TAKE_PROFIT")?trade.target.doubleValue():null,role.equals("STOP_LOSS")?trade.stop.doubleValue():null,null,null,"PV1:"+trade.signalId+":"+role);
            var cmd=orders.create(request,marketData.requireInstrument(trade.symbol),trade.entryPrice().doubleValue()).getFirst();
            var order=cmd.order();order.account(account);order.orderRef("PV1:"+trade.signalId+":"+role);order.ocaGroup("PV1:"+trade.signalId);order.ocaType(2);order.tif("GTC");order.parentId(0);order.transmit(true);
            if(prior.isPresent())order.orderId(prior.get().id());
            repairs.add(new IbkrOrderCommand(order.orderId(),null,trade.signalId,role,cmd.symbol(),cmd.side(),cmd.type(),cmd.direction(),cmd.tradeState(),remaining,cmd.referencePrice(),cmd.limitPrice(),cmd.stopPrice(),null,cmd.contract(),order));
        }
        return List.copyOf(repairs);
    }
    public void place(IbkrOrderCommand command){assertVerifiedPaper();if(!command.order().account().equals(properties.getIbkr().getAccountId()))throw new IllegalStateException("Account mismatch");api.placeOrder(command.orderId(),command.contract(),command.order());}
    public void cancelVerifiedUnfilledEntry(BrokerOrder order){
        assertVerifiedPaper();
        if(!order.account().equals(properties.getIbkr().getAccountId())||order.parentId()!=0||!order.clientOrderId().startsWith("PV1:")||!order.clientOrderId().endsWith(":ENTRY")||order.quantity().subtract(order.filled()).signum()<=0||Set.of("Filled","Cancelled","Inactive").contains(order.status()))throw new IllegalStateException("Cancel refused: entry remainder not verified");
        api.cancelOrder(order.id());
    }
    private void assertVerifiedPaper(){var c=connection();if(!c.connected()||!c.paperIdentityVerified()||!c.writesVerified())throw new IllegalStateException("PAPER identity/write permission unverified: no broker mutation permitted");}
    private static BigDecimal positive(double value){return Double.isFinite(value)&&value>0&&value<Double.MAX_VALUE/2?BigDecimal.valueOf(value):null;}
    private static <T>List<T> drain(Queue<T> queue){List<T> result=new ArrayList<>();for(T e;(e=queue.poll())!=null;)result.add(e);return result;}
    public List<ExecutionRecord> drainExecutions(){return drain(executionQueue);}
    public List<CommissionRecord> drainCommissions(){return drain(commissionQueue);}
    public List<BrokerOrder> drainOrders(){return drain(orderQueue);}
    public List<String> drainErrors(){return drain(errors);}
    private static final class Pending {
        final long epoch;final int executionId,accountId;final CompletableFuture<Void> finished=new CompletableFuture<>();
        final Map<String,BrokerPosition> positions=new ConcurrentHashMap<>();final Map<Integer,BrokerOrder> orders=new ConcurrentHashMap<>();
        final List<ExecutionRecord> executions=new CopyOnWriteArrayList<>();final List<CommissionRecord> commissions=new CopyOnWriteArrayList<>();
        volatile boolean positionEnd,openEnd,completedEnd,executionEnd,accountEnd;volatile BigDecimal funds=BigDecimal.ZERO;
        Pending(long epoch,int executionId,int accountId){this.epoch=epoch;this.executionId=executionId;this.accountId=accountId;}
        void complete(){if(positionEnd&&openEnd&&completedEnd&&executionEnd&&accountEnd)finished.complete(null);}
    }
}
