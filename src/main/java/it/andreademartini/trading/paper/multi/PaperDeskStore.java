package it.andreademartini.trading.paper.multi;

import it.andreademartini.trading.paper.patternv1.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/** Additive metadata row; legacy SESSION and its event stream remain unchanged. */
@Service
public class PaperDeskStore {
    public static class State {
        public String paperSessionId=UUID.randomUUID().toString();
        public Map<String,String> instances=new LinkedHashMap<>(Map.of("PATTERN_V1",UUID.randomUUID().toString(),"PATTERN_V2",UUID.randomUUID().toString()));
        public boolean globalKill;
        public String reason="Not activated";
        public Instant createdAt=Instant.now();
        public Set<String> commands=new HashSet<>();
    }
    private final PatternPaperRepository repo;private final TransactionTemplate tx;
    public PaperDeskStore(PatternPaperRepository repo,PlatformTransactionManager manager){this.repo=repo;tx=new TransactionTemplate(manager);}
    @PostConstruct public void initialize(){tx.executeWithoutResult(s->{if(!repo.existsById("MULTI_STATE"))repo.saveAndFlush(new PatternPaperEntity("MULTI_STATE","MULTI_STATE",PatternPaperStore.json(new State())));});}
    public State state(){return repo.findById("MULTI_STATE").map(e->PatternPaperStore.read(e.payload,State.class)).orElseThrow();}
    public synchronized <T>T update(Function<State,T> change){return tx.execute(t->{var row=repo.locked("MULTI_STATE").orElseThrow();var s=PatternPaperStore.read(row.payload,State.class);var result=change.apply(s);row.payload=PatternPaperStore.json(s);repo.saveAndFlush(row);return result;});}
    public void event(String type,String message,boolean critical){repo.saveAndFlush(new PatternPaperEntity("MULTI_EVENT:"+UUID.randomUUID(),"MULTI_EVENT",PatternPaperStore.json(new PatternPaperModel.Event(Instant.now(),type,"PAPER_ACCOUNT","1.0",null,null,null,message,critical))));}
    public List<PatternPaperModel.Event> events(){return repo.findByKindOrderByIdAsc("MULTI_EVENT").stream().map(e->PatternPaperStore.read(e.payload,PatternPaperModel.Event.class)).sorted(Comparator.comparing(PatternPaperModel.Event::time)).toList();}
}
