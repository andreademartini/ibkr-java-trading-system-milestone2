package it.andreademartini.trading.backtest.patternv2stable;

import it.andreademartini.trading.backtest.patternstable.*;
import it.andreademartini.trading.backtest.patternv2.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** New append-only stable registry. Never writes V1 or historical V2 research configuration/runs. */
@Service
public class FrozenPatternV2Service {
    public static final String ID="PATTERN_V2",VERSION="1.0",REFERENCE="a856a14e-4046-4fdb-9940-e84fc4c967a6";
    private final PatternV2Service engine;
    private final PatternV2Store historical;
    private final PatternStableStore archive;
    private final Path root;
    @Autowired public FrozenPatternV2Service(PatternV2Service engine,PatternV2Store historical){this(engine,historical,Path.of("data/pattern-v2-stable"));}
    public FrozenPatternV2Service(PatternV2Service engine,PatternV2Store historical,Path root){this.engine=engine;this.historical=historical;this.root=root;this.archive=new PatternStableStore(root);}
    public PatternStableService.Version version(){
        try(var in=getClass().getResourceAsStream("/pattern-v2-stable/1.0.json")){
            var document=PatternStableStore.JSON.readTree(in);var c=PatternStableStore.JSON.treeToValue(document.get("configuration"),PatternV2Configuration.class);
            var g=c.global();
            if(!c.marketRegimeFilterEnabled()||c.marketRegimeFilterAdxPeriod()!=14||c.marketRegimeFilterAdxThreshold()!=25||c.marketRegimeFilterMode()!=PatternV2Configuration.MarketRegimeFilterMode.EXCLUDE_WITH_TREND||g.maxHoldingBars()!=13||g.useLeverage()||c.patternRangeFilterEnabled()||c.closeStrengthFilterEnabled()||c.bollingerAtrFilterEnabled())throw new IllegalStateException("Frozen V2 configuration invalid");
            return new PatternStableService.Version(ID,VERSION,"STABLE / FROZEN / BACKTEST; ADX14 >=25 EXCLUDE_WITH_TREND",REFERENCE,PatternStableStore.hash(c),c,new PatternStableService.Safety(false,false,false,false));
        }catch(java.io.IOException e){throw new IllegalStateException("Frozen V2 snapshot unavailable",e);}
    }
    public PatternStableService.Run run(){
        var v=version();var reference=historical.run(REFERENCE);
        if(!PatternStableStore.hash(reference.configuration()).equals(v.configurationHash()))throw new IllegalStateException("Reference configuration mismatch; explicit new version required");
        var dataset=historical.dataset(reference.datasetId());
        archive.immutable("versions/1.0.json",v);archive.immutable("datasets/"+dataset.id()+".json",dataset);
        var simulation=engine.simulate(v.configuration(),dataset.bars());
        simulation=simulation.withResearch(simulation.patternRangeAnalysis(),dataset.id(),null);
        var run=new PatternStableService.Run(simulation.runId(),ID,VERSION,Instant.now(),REFERENCE,v.configurationHash(),dataset.id(),simulation);
        archive.immutable("runs/"+UUID.fromString(run.runId())+".json",run);return run;
    }
    public PatternStableService.Run result(String id){return archive.read("runs/"+UUID.fromString(id)+".json",PatternStableService.Run.class);}
    public List<PatternStableService.Run> history(){
        var folder=root.resolve("runs");if(!Files.isDirectory(folder))return List.of();
        try(var files=Files.list(folder)){return files.filter(p->p.toString().endsWith(".json")).map(p->result(p.getFileName().toString().replace(".json",""))).sorted(Comparator.comparing(PatternStableService.Run::timestamp).reversed()).toList();}
        catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
