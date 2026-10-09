package it.andreademartini.trading.paper.patternv1;

import static it.andreademartini.trading.paper.patternv1.PatternPaperModel.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import it.andreademartini.trading.backtest.patternstable.PatternStableService;
import it.andreademartini.trading.config.*;
import it.andreademartini.trading.ibkr.*;
import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.orders.*;
import it.andreademartini.trading.risk.KillSwitchService;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;

final class PatternPaperTestSupport {
    static final String ACCOUNT="DU_TEST_ALLOWED";
    static BigDecimal bd(double value){return BigDecimal.valueOf(value);}
    static PatternStableService stable(){return new PatternStableService(null,null,null,null);}
    static Limits limits(){return new Limits(bd(500),bd(30),10,20,bd(100));}
    static Connection connection(boolean identity,boolean writes){return new Connection(true,"127.0.0.1",4002,ACCOUNT,List.of(ACCOUNT),identity,writes,true,"Identity missing",Instant.now(),Instant.now(),1);}
    static PatternPaperCalendar calendar(String symbol,Instant now){
        var date=now.atZone(ZoneId.of("America/New_York")).toLocalDate();var dates=new HashSet<LocalDate>();var sessions=new ArrayList<PatternPaperCalendar.Session>();
        for(int i=-30;i<=30;i++){var d=date.plusDays(i);dates.add(d);var open=i==0?now.minusSeconds(5):d.atTime(9,30).atZone(ZoneId.of("America/New_York")).toInstant();var close=i==0?now.plusSeconds(3600):d.atTime(16,0).atZone(ZoneId.of("America/New_York")).toInstant();sessions.add(new PatternPaperCalendar.Session(d,open,close));}
        return new PatternPaperCalendar(symbol,"America/New_York",now,dates,sessions);
    }
    static HistoricalBar bar(String symbol,LocalDate date,int index,double open,double high,double low,double close){return new HistoricalBar(symbol,date.atStartOfDay(),bd(open),bd(high),bd(low),bd(close),1000,BarInterval.DAILY,"TEST").withIndex(index);}
    static SignalRecord signal(String symbol){
        var day=LocalDate.now(ZoneId.of("America/New_York"));
        var bars=List.of(bar(symbol,day.minusDays(2),0,100,101,98,99),bar(symbol,day.minusDays(1),1,98.5,101,98.2,100.5));
        var record=new SignalRecord();record.signal=new PatternPaperPlanner(stable().version()).signals(symbol,bars,1).getFirst();record.symbol=symbol;record.id=PatternPaperService.signalId("2.1",record.signal);record.mode="PAPER";record.status="PENDING";record.generatedAt=Instant.now();record.entrySession=day;return record;
    }
    static Session ledgerSession(){var session=new Session();session.frozen=stable().version();session.strategyVersion="2.1";session.configurationHash=session.frozen.configurationHash();session.account=ACCOUNT;session.limits=limits();var signal=signal("AAPL");signal.expectedEntry=bd(100);signal.expectedStop=bd(98);signal.expectedTarget=bd(106);signal.expectedQuantity=10;session.signals.add(signal);
        for(var role:List.of(Role.ENTRY,Role.STOP_LOSS,Role.TAKE_PROFIT)){var order=new OrderRecord();order.orderId=100+role.ordinal();order.signalId=signal.id;order.role=role;order.account=ACCOUNT;order.symbol="AAPL";order.action=role==Role.ENTRY?"BUY":"SELL";order.quantity=10;order.clientOrderId="PV1:"+signal.id+":"+role;order.status="Submitted";order.createdAt=Instant.now();order.ocaGroup="PV1:"+signal.id;session.orders.add(order);}return session;
    }
    static ExecutionRecord fill(Session s,String id,int orderId,double quantity,double price){var order=s.orders.stream().filter(o->o.orderId==orderId).findFirst().orElseThrow();return new ExecutionRecord(id,null,orderId,orderId+1000,ACCOUNT,"AAPL",order.action,bd(quantity),bd(price),Instant.now(),null);}
    static final class Harness {
        Session session=new Session();
        final PatternPaperStore store=mock(PatternPaperStore.class);
        final PatternPaperBroker broker=mock(PatternPaperBroker.class);
        final TradingSystemProperties properties=new TradingSystemProperties();
        final KillSwitchService kill=new KillSwitchService();
        final PatternPaperService service;
        final List<Event> events=new ArrayList<>();
        final Instant now=Instant.now();
        final Map<String,PatternPaperCalendar> calendars=new HashMap<>();
        BrokerSnapshot snapshot;
        int nextId=100;
        Harness(){
            properties.setMode(TradingMode.PAPER);properties.getIbkr().setAccountId(ACCOUNT);properties.getIbkr().setPort(4002);
            when(store.session()).thenAnswer(i->session);when(store.update(any())).thenAnswer(i->((Function<Session,Object>)i.getArgument(0)).apply(session));
            doAnswer(i->{events.add(new Event(Instant.now(),i.getArgument(1),"PATTERN_V1",session.strategyVersion,i.getArgument(2),i.getArgument(3),i.getArgument(4),i.getArgument(5),i.getArgument(6)));return null;}).when(store).event(any(),anyString(),any(),any(),any(),anyString(),anyBoolean());
            when(store.events()).thenReturn(events);when(broker.connection()).thenAnswer(i->connection(true,true));when(broker.limitations()).thenReturn(List.of());
            when(broker.drainExecutions()).thenReturn(List.of());when(broker.drainCommissions()).thenReturn(List.of());when(broker.drainOrders()).thenReturn(List.of());when(broker.drainErrors()).thenReturn(List.of());
            snapshot=new BrokerSnapshot(1,Instant.now(),List.of(),List.of(),List.of(),List.of(),bd(1000000),true,"OK");when(broker.reconcile()).thenAnswer(i->snapshot);
            for(var symbol:stable().version().configuration().symbols())calendars.put(symbol.symbol(),calendar(symbol.symbol(),now));
            when(broker.calendar(anyString())).thenAnswer(i->calendars.get(i.getArgument(0)));
            when(broker.quote(anyString())).thenAnswer(i->new Quote(bd(100),bd(100),bd(99.99),bd(100.01),Instant.now(),LocalDate.now(ZoneId.of("America/New_York")),true,bd(10000),bd(1)));
            when(broker.dailyBars(anyString())).thenReturn(List.of());
            when(broker.bracket(any(),anyLong(),anyString())).thenAnswer(i->{var signal=(SignalRecord)i.getArgument(0);long qty=i.getArgument(1);var commands=new ArrayList<IbkrOrderCommand>();int parent=nextId++;
                for(var role:List.of(Role.ENTRY,Role.TAKE_PROFIT,Role.STOP_LOSS)){
                    int id=role==Role.ENTRY?parent:nextId++;var c=new com.ib.client.Contract();c.symbol(signal.symbol);c.secType("STK");c.currency("USD");c.exchange("SMART");var o=new com.ib.client.Order();o.orderId(id);o.account(ACCOUNT);o.action(role==Role.ENTRY?"BUY":"SELL");o.totalQuantity(com.ib.client.Decimal.get(qty));o.orderType(role==Role.ENTRY?"MKT":role==Role.STOP_LOSS?"STP":"LMT");o.orderRef("PV1:"+signal.id+":"+role);o.parentId(role==Role.ENTRY?0:parent);o.ocaGroup("PV1:"+signal.id);o.transmit(role==Role.STOP_LOSS);
                    commands.add(new IbkrOrderCommand(id,role==Role.ENTRY?null:parent,signal.id,role.name(),signal.symbol,role==Role.ENTRY?OrderSide.BUY:OrderSide.SELL,PaperOrderType.BRACKET,null,null,qty,100d,role==Role.TAKE_PROFIT?signal.expectedTarget.doubleValue():null,role==Role.STOP_LOSS?signal.expectedStop.doubleValue():null,null,c,o));
                }return commands;});
            service=new PatternPaperService(store,stable(),broker,properties,kill);service.recover();session.limits=limits();
        }
        void start(){service.start(UUID.randomUUID().toString());}
        void enter(){start();session.signals.add(signal("AAPL"));service.tick(Instant.now());}
        List<BrokerOrder> brokerOrders(){return session.orders.stream().map(o->new BrokerOrder(o.orderId,o.permId,o.parentId,o.account,o.symbol,o.clientOrderId,o.action,o.type,bd(o.quantity),o.filled,o.role==Role.ENTRY?"Filled":"Submitted",o.stopPrice,o.limitPrice,o.ocaGroup)).toList();}
    }
}
