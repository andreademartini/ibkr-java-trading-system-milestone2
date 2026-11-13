package it.andreademartini.trading.monitor;

import com.ib.client.*;
import it.andreademartini.trading.ibkr.*;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.orders.IbkrOrderCommand;
import it.andreademartini.trading.paper.patternv1.PatternPaperModel.*;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Observation only. No broker command API is available to this component. */
@Service
public class ExecutionMonitor implements IbkrEventListener {
    public record Feed(String symbol,int conId,String exchange,String currency,String type,Double last,Double bid,Double ask,Instant sourceTime,Instant receivedAt){}
    public record Signal(String strategy,String version,String id,String symbol,String direction,Instant generatedAt,BigDecimal price,
                         String dataType,Instant sourceTime,Feed feed,boolean connected,boolean paperVerified,String timeframe,
                         Instant barStart,Instant barEnd,BigDecimal open,BigDecimal high,BigDecimal low,BigDecimal close,boolean complete,String origin){}
    public record Intent(String signalId,int orderId,String account,String side,String type,Double reference,Double limit,Double stop,long quantity,Instant sentAt,Feed quote){}
    public record Fill(String execId,int orderId,long permId,String symbol,int conId,String side,BigDecimal quantity,double price,String brokerTime,Instant executionTime,Instant receivedAt){}
    public record Settings(double warning,double critical,boolean blockNewPaperOrdersOnUnreliableMarketData){
        public Settings{if(!Double.isFinite(warning)||!Double.isFinite(critical)||warning<=0||critical<warning)throw new IllegalArgumentException("Invalid deviation thresholds");}
    }
    public record Row(Signal signal,Intent order,Fill fill,Double deviation,Double absoluteDeviation,String favorability,Double slippage,Double quoteDeviation,List<String> states){}
    private final ExecutionMonitorStore store;
    private final Map<Integer,Feed> feeds=new ConcurrentHashMap<>();
    private volatile boolean connected;
    private volatile Settings settings=new Settings(.5,1,false);
    public ExecutionMonitor(ExecutionMonitorStore store,IbkrEWrapper wrapper){this.store=store;wrapper.addListener(this);}
    @jakarta.annotation.PostConstruct public void restore(){var saved=store.read("SETTINGS",Settings.class);if(!saved.isEmpty())settings=saved.getLast();}
    private void invalidateFeeds(){feeds.replaceAll((k,f)->new Feed(f.symbol,f.conId,f.exchange,f.currency,"UNKNOWN",null,null,null,null,null));}
    public Settings settings(){return settings;}
    public void configure(Settings value){store.event("SETTINGS",value);settings=value;}
    public void register(int id,Contract c){feeds.put(id,new Feed(c.symbol(),c.conid(),c.exchange(),c.currency(),"UNKNOWN",null,null,null,null,null));}
    @Override public void onMarketDataType(int id,int type){feeds.computeIfPresent(id,(k,f)->new Feed(f.symbol,f.conId,f.exchange,f.currency,type(type),f.last,f.bid,f.ask,null,f.receivedAt));}
    public static String type(int value){return switch(value){case 1->"LIVE";case 2->"FROZEN";case 3->"DELAYED";case 4->"DELAYED_FROZEN";default->"UNKNOWN";};}
    @Override public void onTickPrice(int id,int field,double price){if(!Double.isFinite(price)||price<=0)return;
        if(!Set.of(1,2,4,66,67,68).contains(field))return;
        feeds.computeIfPresent(id,(k,f)->{var q=new Feed(f.symbol,f.conId,f.exchange,f.currency,f.type,
                field==4||field==68?price:f.last,field==1||field==66?price:f.bid,field==2||field==67?price:f.ask,
                null,Instant.now());store.event("QUOTE",q);return q;});
    }
    // IB lastTimestamp applies to Last only. It never qualifies Bid/Ask as contemporaneous.
    @Override public void onTickString(int id,int field,String value){if(field!=45&&field!=88)return;try{
        Instant source=Instant.ofEpochSecond(Long.parseLong(value));feeds.computeIfPresent(id,(k,f)->new Feed(f.symbol,f.conId,f.exchange,f.currency,f.type,f.last,f.bid,f.ask,source,Instant.now()));
    }catch(RuntimeException ignored){}}
    private Feed feed(String symbol){return feeds.values().stream().filter(f->f.symbol.equals(symbol)).max(Comparator.comparing(f->f.receivedAt==null?Instant.MIN:f.receivedAt)).orElse(null);}
    public void signal(String version,SignalRecord r,HistoricalBar bar,Instant start,Instant end,Connection connection){
        if(!"1.2".equals(version))return;
        var f=feed(r.symbol);
        // Historical DAILY close is the actual pattern input; streaming type cannot establish historical provenance.
        var s=new Signal("PATTERN_V2_STABLE",version,r.id,r.symbol,r.signal.detection().direction().name(),r.generatedAt,r.signal.patternClose(),
                "UNKNOWN",end,f,connection.connected(),connection.paperIdentityVerified(),"DAILY",start,end,bar.getOpen(),bar.getHigh(),bar.getLow(),bar.getClose(),true,bar.getProvider());
        store.append("SIGNAL",r.id,s);
    }
    public boolean unreliable(String symbol){var f=feed(symbol);return !connected||f==null||!"LIVE".equals(f.type)||f.sourceTime==null||f.receivedAt==null||Duration.between(f.receivedAt,Instant.now()).getSeconds()>30;}
    public void intent(String signalId,IbkrOrderCommand c){
        if(store.read("SIGNAL",Signal.class).stream().noneMatch(s->s.id.equals(signalId)))return;
        store.event("INTENT",new Intent(signalId,c.orderId(),c.order().account(),c.order().action().toString(),c.order().orderType().toString(),
                c.referencePrice(),valid(c.order().lmtPrice()),valid(c.order().auxPrice()),c.quantity(),Instant.now(),feed(c.symbol())));
    }
    private static Double valid(double value){return Double.isFinite(value)&&value>0&&value<Double.MAX_VALUE?value:null;}
    @Override public void onExecution(int request,Contract c,Execution e){
        var intent=store.read("INTENT",Intent.class).stream().filter(i->i.orderId==e.orderId()&&Objects.equals(i.account,e.acctNumber())).findFirst();
        if(intent.isEmpty())return;
        var f=new Fill(e.execId(),e.orderId(),e.permId(),c.symbol(),c.conid(),e.side(),IbkrNumber.toBigDecimal(e.shares()),e.price(),e.time(),executionTime(e.time()),Instant.now());
        store.append("FILL",e.execId(),f);
        store.append("ANOMALY",e.execId(),row(f));
    }
    @Override public void onOpenOrder(int id,Contract c,Order o,OrderState state){if(known(id))store.event("ORDER",Map.of("orderId",id,"permId",o.permId(),"status",state.status(),"receivedAt",Instant.now(),"limit",o.lmtPrice(),"stop",o.auxPrice()));}
    private boolean known(int id){return store.read("INTENT",Intent.class).stream().anyMatch(i->i.orderId==id);}
    @Override public void onOrderStatus(IbkrOrderStatusUpdate u){if(known(u.orderId()))store.event("STATUS",u);}
    @Override public void onCommissionAndFees(CommissionAndFeesReport r){store.append("COMMISSION",r.execId(),Map.of("execId",r.execId(),"amount",r.commissionAndFees(),"currency",r.currency()));}
    @Override public void onNextValidId(int id){connected=true;store.event("CONNECTION",Map.of("connected",true,"time",Instant.now()));}
    @Override public void onConnectionClosed(){connected=false;invalidateFeeds();store.event("CONNECTION",Map.of("connected",false,"time",Instant.now()));}
    @Override public void onError(IbkrError e){
        if(Objects.equals(e.code(),1100)){connected=false;feeds.replaceAll((k,f)->new Feed(f.symbol,f.conId,f.exchange,f.currency,"UNKNOWN",null,null,null,null,null));}
        if(Objects.equals(e.code(),1101)||Objects.equals(e.code(),1102)){connected=true;invalidateFeeds();}
        store.event("ERROR",e);
    }
    private static Instant executionTime(String raw){try{return it.andreademartini.trading.paper.patternv1.PatternPaperIbkrBroker.parseExecutionTime(raw);}catch(RuntimeException e){return null;}}
    public static Double deviation(double fill,BigDecimal signal){return signal==null||signal.signum()<=0||!Double.isFinite(fill)?null:(fill-signal.doubleValue())/signal.doubleValue()*100;}
    public Row row(Fill fill){
        var order=store.read("INTENT",Intent.class).stream().filter(i->i.orderId==fill.orderId).reduce((a,b)->b).orElse(null);
        var signal=order==null?null:store.read("SIGNAL",Signal.class).stream().filter(s->s.id.equals(order.signalId)).findFirst().orElse(null);
        Double d=signal==null?null:deviation(fill.price,signal.price);var states=new ArrayList<String>();
        if(signal==null)states.add("SIGNAL_NOT_ASSOCIATED");
        if(signal==null||!signal.paperVerified)states.add("PAPER_SESSION_NOT_VERIFIED");
        if(signal!=null&&!signal.connected)states.add("IBKR_DISCONNECTED");
        if(signal!=null&&signal.feed!=null&&signal.feed.type.startsWith("DELAYED"))states.add("DELAYED_MARKET_DATA");
        states.add("LATENCY_UNKNOWN");states.add("QUOTE_NOT_COMPARABLE");
        if(d!=null&&Math.abs(d)>=settings.critical)states.add("EXECUTION_DEVIATION_CRITICAL");else if(d!=null&&Math.abs(d)>=settings.warning)states.add("EXECUTION_DEVIATION_WARNING");
        String favorable=d==null?"UNKNOWN":d==0?"NEUTRAL":(signal.direction.equals("LONG")?d<0:d>0)?"FAVORABLE":"UNFAVORABLE";
        Double slip=order!=null&&"MKT".equals(order.type)&&order.reference!=null?deviation(fill.price,BigDecimal.valueOf(order.reference)):null;
        return new Row(signal,order,fill,d,d==null?null:Math.abs(d),favorable,slip,null,List.copyOf(states));
    }
    public List<Row> rows(){return store.read("FILL",Fill.class).stream().map(this::row).toList();}
    public Map<String,Object> dashboard(){var rows=rows();return Map.of("connected",connected,"accountMode","UNKNOWN","settings",settings,"quotes",List.copyOf(feeds.values()),"rows",rows,"monitoredOrders",store.read("INTENT",Intent.class).stream().map(Intent::orderId).distinct().count(),"temporalWarning","Bid/Ask lack trustworthy event timestamps: NOT_COMPARABLE. Historical bar end is not feed latency.");}
}
