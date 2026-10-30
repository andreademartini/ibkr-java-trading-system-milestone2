package it.andreademartini.trading.backtest.reboundv2;

import it.andreademartini.trading.backtest.rebound.*;
import it.andreademartini.trading.backtest.patternv2.PatternV2Dataset;
import it.andreademartini.trading.backtest.weeklytte.WeeklyTteCsvData;
import it.andreademartini.trading.marketdata.HistoricalBar;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import static it.andreademartini.trading.backtest.rebound.TrendReboundRun.*;

/** Offline V2 storage only. V1 store is a read-only reference, never configured or saved here. */
@Service
public class TrendReboundV2Service {
    private final TrendReboundV2Store store;private final TrendReboundV2Engine engine;private final TrendReboundStore v1;
    public TrendReboundV2Service(TrendReboundV2Store store,TrendReboundV2Engine engine,TrendReboundStore v1){this.store=store;this.engine=engine;this.v1=v1;}
    public TrendReboundV2Run run(TrendReboundV2Configuration c){
        c.validate();var input=new TreeMap<String,List<HistoricalBar>>();var issues=new ArrayList<DataIssue>();
        for(String symbol:c.symbols()){
            if(c.excludedSymbols().contains(symbol))continue;var path=Path.of("data","stooq",symbol+".US.csv");
            try{var loaded=WeeklyTteCsvData.load(path,symbol);input.put(symbol,loaded.bars().stream().filter(b->b.dateTime().getYear()>=2010&&!TrendReboundEngine.date(b).isAfter(c.endDate())).toList());issues.add(new DataIssue(symbol,"INFO","SOURCE_SHA256",loaded.sourceSha256()));if(loaded.fractionalVolumes()>0)issues.add(new DataIssue(symbol,"WARNING","FRACTIONAL_VOLUME",loaded.fractionalVolumes()+" adjusted volume values rounded down; volume not used for signals"));}
            catch(IOException|IllegalArgumentException e){issues.add(new DataIssue(symbol,"ERROR","CSV_EXCLUDED",Objects.toString(e.getMessage(),e.getClass().getSimpleName())));}
        }
        var identities=new HashMap<String,List<String>>();input.forEach((symbol,bars)->{if(!bars.isEmpty())identities.computeIfAbsent(PatternV2Dataset.capture(Map.of("IDENTITY",bars)).id(),k->new ArrayList<>()).add(symbol);});
        for(var names:identities.values())if(names.size()>1)for(String symbol:names){input.remove(symbol);issues.add(new DataIssue(symbol,"ERROR","IDENTICAL_PRICE_VOLUME_DATA","Excluded duplicate dataset shared by "+names));}
        if(input.isEmpty())throw new IllegalArgumentException("No usable local datasets: "+issues);
        return execute(c,PatternV2Dataset.capture(input),issues,null);
    }
    public TrendReboundV2Run onSnapshot(TrendReboundV2Configuration c,PatternV2Dataset snapshot){return execute(c,snapshot,List.of(),null);}
    public TrendReboundV2Configuration configurationFromV1(String id,TrendReboundV2Configuration.ExitMode mode){return TrendReboundV2Configuration.fromV1(v1.run(id).configuration(),mode);}
    public TrendReboundV2Run fromV1(TrendReboundV2Configuration c,String referenceId){var reference=v1.run(referenceId);return execute(c,v1.dataset(reference.datasetId()),sourceIssues(reference),reference);}
    private static List<DataIssue> sourceIssues(TrendReboundRun r){return r.dataQuality().stream().filter(i->Set.of("SOURCE_SHA256","FRACTIONAL_VOLUME","CSV_EXCLUDED","IDENTICAL_PRICE_VOLUME_DATA").contains(i.code())).toList();}
    private TrendReboundV2Run execute(TrendReboundV2Configuration c,PatternV2Dataset snapshot,List<DataIssue> provenance,TrendReboundRun reference){
        return execute(c,snapshot,provenance,reference,null);
    }
    private TrendReboundV2Run execute(TrendReboundV2Configuration c,PatternV2Dataset snapshot,List<DataIssue> provenance,TrendReboundRun reference,TrendReboundV2Run replayExpected){
        c.validate();if(!snapshot.id().equals(PatternV2Dataset.capture(snapshot.bars()).id()))throw new IllegalArgumentException("Input dataset fingerprint mismatch");
        var r=engine.simulate(c,snapshot.bars(),snapshot.id(),reference);if(replayExpected!=null&&(!TrendReboundV2Audit.economics(replayExpected.portfolio()).equals(TrendReboundV2Audit.economics(r.portfolio()))||!replayExpected.tradeAudit().equals(r.tradeAudit())))throw new IllegalStateException("Replay differs from saved economic decisions or exit audit; no run saved");var p=r.portfolio();var issues=new ArrayList<>(provenance);issues.addAll(p.dataQuality());var warnings=new ArrayList<>(p.warnings());if(provenance.stream().anyMatch(i->i.severity().equals("ERROR")))warnings.add("Source exclusions present: consult DATA_QUALITY; suspicious datasets are not included.");
        var portfolio=new TrendReboundRun(p.runId(),p.strategy(),p.strategyVersion(),p.timestamp(),p.configurationHash(),p.datasetId(),p.calendarVersion(),p.configuration(),p.signals(),p.trades(),p.equity(),List.copyOf(issues),p.analysis(),List.copyOf(warnings));
        var result=new TrendReboundV2Run(r.runId(),r.strategy(),r.strategyVersion(),r.timestamp(),r.configurationHash(),r.datasetId(),r.referenceV1RunId(),r.configuration(),portfolio,r.tradeAudit(),r.exitAnalysis(),r.regressionAudit(),r.v1EngineFingerprint(),r.v2EngineFingerprint());
        store.dataset(snapshot);store.configuration(c);store.save(result);return result;
    }
    public TrendReboundV2Run replay(String id){var r=store.run(id);if(!r.v1EngineFingerprint().equals(TrendReboundV2Audit.engineFingerprint())||!r.v2EngineFingerprint().equals(TrendReboundV2Audit.modelFingerprint()))throw new IllegalArgumentException("Replay requires the recorded V1/V2 engine fingerprints");return execute(r.configuration(),store.dataset(r.datasetId()),sourceIssues(r.portfolio()),r.referenceV1RunId()==null?null:v1.run(r.referenceV1RunId()),r);}
    public List<TrendReboundV2Run> comparable(List<String> ids){
        if(ids==null||ids.size()<2||ids.size()>3||new HashSet<>(ids).size()!=ids.size())throw new IllegalArgumentException("Select two or three distinct real runs");var runs=ids.stream().map(store::run).toList();var first=runs.getFirst();String signature=TrendReboundV2Audit.comparisonSignature(first.configuration());var modes=EnumSet.noneOf(TrendReboundV2Configuration.ExitMode.class);
        for(var r:runs)if(!modes.add(r.configuration().exitMode())||!first.datasetId().equals(r.datasetId())||!first.strategyVersion().equals(r.strategyVersion())||!first.v1EngineFingerprint().equals(r.v1EngineFingerprint())||!first.v2EngineFingerprint().equals(r.v2EngineFingerprint())||!signature.equals(TrendReboundV2Audit.comparisonSignature(r.configuration())))throw new IllegalArgumentException("Comparison requires distinct exit modes, same dataset, engine, dates, universe, costs, risk, target settings and ticks");return runs;
    }
    public Map<String,Object> compare(List<String> ids){var runs=comparable(ids);return Map.of("datasetId",runs.getFirst().datasetId(),"rows",runs.stream().map(TrendReboundV2Audit::comparisonRow).toList(),"note","Executed real runs only. No unexecuted mode is assigned fabricated metrics.");}
}
