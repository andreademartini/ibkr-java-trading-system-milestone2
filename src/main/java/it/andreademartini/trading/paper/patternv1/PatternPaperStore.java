package it.andreademartini.trading.paper.patternv1;

import static it.andreademartini.trading.paper.patternv1.PatternPaperModel.*;
import it.andreademartini.trading.backtest.patternstable.PatternStableStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

@Service
public class PatternPaperStore {
    private final PatternPaperRepository repository;
    private final TransactionTemplate transaction;
    public PatternPaperStore(PatternPaperRepository repository,PlatformTransactionManager manager){this.repository=repository;transaction=new TransactionTemplate(manager);}
    @PostConstruct public synchronized void initialize(){
        transaction.executeWithoutResult(s->{if(!repository.existsById("SESSION"))repository.saveAndFlush(new PatternPaperEntity("SESSION","SESSION",json(new Session())));});
    }
    /** One database lock serializes controls, fills and outbox reservations across requests. */
    public synchronized <T>T update(Function<Session,T> action){
        return transaction.execute(tx->{var entity=repository.locked("SESSION").orElseThrow();var session=read(entity.payload,Session.class);
            var result=action.apply(session);entity.payload=json(session);repository.saveAndFlush(entity);return result;});
    }
    public Session session(){return repository.findById("SESSION").map(e->read(e.payload,Session.class)).orElseGet(Session::new);}
    public void event(Session session,String type,String symbol,Integer orderId,String signalId,String message,boolean critical){
        var event=new Event(Instant.now(),type,"PATTERN_V1",session.strategyVersion,symbol,orderId,signalId,message,critical);
        repository.save(new PatternPaperEntity("EVENT:"+event.time()+":"+UUID.randomUUID(),"EVENT",json(event)));
        if(critical)repository.save(new PatternPaperEntity("ALERT:"+UUID.randomUUID(),"ALERT",json(event)));
    }
    public List<Event> events(){var all=repository.findByKindOrderByIdAsc("EVENT");return all.stream().skip(Math.max(0,all.size()-500)).map(e->read(e.payload,Event.class)).toList();}
    public List<Event> allEvents(){return repository.findByKindOrderByIdAsc("EVENT").stream().map(e->read(e.payload,Event.class)).toList();}
    public static String json(Object value){try{return PatternStableStore.JSON.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("PAPER serialization failed",e);}}
    public static <T>T read(String value,Class<T> type){try{return PatternStableStore.JSON.readValue(value,type);}catch(Exception e){throw new IllegalStateException("PAPER persistence invalid",e);}}
}
