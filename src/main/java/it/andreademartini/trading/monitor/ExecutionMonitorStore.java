package it.andreademartini.trading.monitor;

import it.andreademartini.trading.paper.patternv1.*;
import it.andreademartini.trading.backtest.patternstable.PatternStableStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

/** Append-only audit using the existing H2/JPA repository. */
@Service
public class ExecutionMonitorStore {
    private final PatternPaperRepository repository;
    public ExecutionMonitorStore(PatternPaperRepository repository){this.repository=repository;}
    @Transactional public synchronized void append(String kind,String key,Object value){
        String id="V2MON:"+kind+":"+key;
        if(!repository.existsById(id))repository.saveAndFlush(new PatternPaperEntity(id,"V2MON_"+kind,PatternPaperStore.json(value)));
    }
    public void event(String kind,Object value){append(kind,Instant.now()+":"+UUID.randomUUID(),value);}
    public <T> List<T> read(String kind,Class<T> type){return repository.findByKindOrderByIdAsc("V2MON_"+kind).stream().map(e->{
        try{return PatternStableStore.JSON.readValue(e.payload,type);}catch(Exception ex){throw new IllegalStateException("Invalid monitor audit",ex);}
    }).toList();}
}
