package it.andreademartini.trading.backtest.weeklytte;

import it.andreademartini.trading.backtest.patternv2.PatternV2Dataset;
import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.marketdata.stooq.StooqCsvParser;
import it.andreademartini.trading.risk.PositionSizingService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import static it.andreademartini.trading.backtest.weeklytte.WeeklyTteConfiguration.*;

/** Deliberately offline: uses the existing STOOQ parser and never calls a market-data refresh or broker. */
@Service
public class WeeklyTteService {
    private final WeeklyTteStore store;private final WeeklyTteEngine engine;
    public WeeklyTteService(WeeklyTteStore store,PositionSizingService sizing){this.store=store;engine=new WeeklyTteEngine(sizing);}
    public WeeklyTteRun run(WeeklyTteConfiguration c){
        c.validate();var input=new TreeMap<String,List<HistoricalBar>>();var manifest=new TreeMap<String,Object>();
        for(String s:c.symbols()){
            var path=Path.of("data","stooq",s+".US.csv");if(!Files.isRegularFile(path))throw new IllegalArgumentException("Local DAILY CSV missing: "+s);
            try{var parsed=WeeklyTteCsvData.load(path,s);
                manifest.put(s,Map.of("sourceSha256",parsed.sourceSha256(),"fractionalVolumesRoundedDown",parsed.fractionalVolumes(),"volumeUsedInSignals",false));
                
                // Warm-up also includes the complete immediately preceding calendar week.
                var from=c.historical()?java.time.LocalDate.of(2010,1,1):c.startDate().minusDays(Math.max(40,(c.momentumLookback()+c.pullbackLookbackBars()+5)*2L));
                if(from.isBefore(java.time.LocalDate.of(2010,1,1)))from=java.time.LocalDate.of(2010,1,1);
                final var first=from;
                input.put(s,parsed.bars().stream().filter(b->!b.dateTime().toLocalDate().isBefore(first)&&!b.dateTime().toLocalDate().isAfter(c.endDate())).toList());
            }catch(IOException e){throw new UncheckedIOException(e);}
        }
        var result=onSnapshot(c,PatternV2Dataset.capture(input));result.analysis().put("dataManifest",manifest);store.save(result);return result;
    }
    public WeeklyTteRun onSnapshot(WeeklyTteConfiguration c,PatternV2Dataset snapshot){
        c.validate();store.configuration(c);store.dataset(snapshot);var r=engine.simulate(c,snapshot.bars(),snapshot.id(),WeeklyTteStore.hash(c));store.save(r);return r;
    }
    public WeeklyTteRun replay(String id){var r=store.run(id);return onSnapshot(r.configuration(),store.dataset(r.datasetId()));}
    public List<WeeklyTteRun> modes(String id){var r=store.run(id);var snapshot=store.dataset(r.datasetId());return Arrays.stream(Mode.values()).map(m->onSnapshot(change(r.configuration(),"mode",m.name()),snapshot)).toList();}
    public WeeklyTteRun sensitivity(String id){var r=store.run(id);return onSnapshot(change(r.configuration(),"ambiguousBarPolicy",r.configuration().ambiguousBarPolicy()==Ambiguity.CONSERVATIVE?Ambiguity.SENSITIVITY_FAVORABLE.name():Ambiguity.CONSERVATIVE.name()),store.dataset(r.datasetId()));}
    static WeeklyTteConfiguration change(WeeklyTteConfiguration c,String field,String value){ObjectNode n=WeeklyTteStore.JSON.valueToTree(c);n.put(field,value);return WeeklyTteStore.JSON.convertValue(n,WeeklyTteConfiguration.class);}
    public Map<String,Object> compare(List<String> ids){
        if(ids==null||ids.size()<2||new HashSet<>(ids).size()!=ids.size())throw new IllegalArgumentException("Select at least two distinct runs");
        var runs=ids.stream().map(store::run).toList();var first=runs.getFirst();
        var base=comparisonConfig(first.configuration());
        for(var r:runs)if(!first.datasetId().equals(r.datasetId())||!base.equals(comparisonConfig(r.configuration())))throw new IllegalArgumentException("Comparison requires identical snapshot, dates, capital, costs, setup and ambiguity policy");
        return Map.of("datasetId",first.datasetId(),"rows",runs.stream().map(r->Map.of("runId",r.runId(),"mode",r.configuration().mode(),"configurationHash",r.configurationHash(),"metrics",r.analysis().get("performance"))).toList(),
                "rules","Versioned rules: historical untouched HIGH/LOW; LONG/SHORT; Breakout stop uses opposite extreme of the same origin week, target uses actual entry risk. Legacy snapshots retain previous-week LONG rules. No automatic winner selection.");
    }
    private static ObjectNode comparisonConfig(WeeklyTteConfiguration c){ObjectNode n=WeeklyTteStore.JSON.valueToTree(c);n.remove(List.of("name","mode"));return n;}
}
