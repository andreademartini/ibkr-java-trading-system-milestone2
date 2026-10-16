package it.andreademartini.trading.backtest.patternstable;

import it.andreademartini.trading.backtest.patternv2.*;
import it.andreademartini.trading.backtest.patternstable.engine.StableSimulation;
import it.andreademartini.trading.strategy.patternv1.PatternV1Properties;
import it.andreademartini.trading.config.TradingSystemProperties;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

/** Historical-only version registry. No execution, gateway, operational strategy or V2 service dependency. */
@Service
public class PatternStableService {
    public static final String REFERENCE="10aae805-4987-4a29-a634-b3819cddc427";
    public static final String VERSION="2.3", ID="PATTERN_V1_STABLE";
    private final PatternStableStore store;
    private final PatternV2Store references;
    private final PatternV1Properties legacy;
    private final TradingSystemProperties trading;
    public record Safety(boolean liveTradingEnabled,boolean orderSubmissionEnabled,boolean autoExecuteSignals,boolean paperTradingEnabled){}
    public record Version(String strategyId,String strategyVersion,String description,String referenceRunId,String configurationHash,PatternV2Configuration configuration,Safety safety){}
    public record Run(String runId,String strategyId,String strategyVersion,Instant timestamp,String referenceRunId,String configurationHash,String datasetId,PatternV2Run simulation){}
    public PatternStableService(PatternStableStore store,PatternV2Store references,PatternV1Properties legacy,TradingSystemProperties trading){this.store=store;this.references=references;this.legacy=legacy;this.trading=trading;}
    public Version version(){return version(VERSION);}
    public Version version(String requestedVersion){
        if(!Set.of("2.0","2.1","2.2","2.3").contains(requestedVersion))throw new IllegalArgumentException("Unknown stable version: "+requestedVersion);
        try(var input=getClass().getResourceAsStream("/pattern-stable/"+requestedVersion+".json")){
            var config=PatternStableStore.JSON.readValue(input,PatternV2Configuration.class);
            String description="Promoted from PATTERN_V2 — Dynamic equity sizing 30%, no leverage, realistic trading costs.";
            if(requestedVersion.equals("2.1"))description+=" Timeout changed to 13 bars from version 2.0.";
            if(requestedVersion.equals("2.2"))description+=" 13 holding bars; opening gaps beyond stop/target fill at OPEN before intrabar checks.";
            if(requestedVersion.equals("2.3"))description+=" 13 holding bars; OPEN_FIRST gap fills; theoretical risk 4% of equity.";
            return new Version(ID,requestedVersion,description,REFERENCE,PatternStableStore.hash(config),config,new Safety(false,false,false,false));
        }catch(java.io.IOException e){throw new IllegalStateException("Stable configuration missing",e);}
    }
    /** Explicit promotion validates source before archiving; never rewrites original runs. */
    public Version promote(){return promote(VERSION);}
    public Version promote(String requestedVersion){
        var version=version(requestedVersion);var baseline=version("2.0");var reference=references.run(REFERENCE);
        if(!PatternStableStore.hash(reference.configuration().withName(ID)).equals(baseline.configurationHash()))throw new IllegalStateException("Reference configuration differs from frozen version 2.0");
        if(requestedVersion.equals("2.1")){
            var expected=PatternStableStore.JSON.valueToTree(baseline.configuration());
            ((com.fasterxml.jackson.databind.node.ObjectNode)expected.path("global")).put("maxHoldingBars",13);
            if(!PatternStableStore.hash(expected).equals(version.configurationHash()))throw new IllegalStateException("Version 2.1 must only change timeout to 13 bars");
        }
        if(requestedVersion.equals("2.2")){
            var expected=version("2.1").configuration().withExitFillPolicy(ExitFillPolicy.OPEN_FIRST);
            if(!PatternStableStore.hash(expected).equals(version.configurationHash()))throw new IllegalStateException("Version 2.2 must only change exit fill policy to OPEN_FIRST");
        }
        if(requestedVersion.equals("2.3")){
            var expected=PatternStableStore.JSON.valueToTree(version("2.2").configuration());
            ((com.fasterxml.jackson.databind.node.ObjectNode)expected.path("global")).put("riskPercent",4);
            if(!PatternStableStore.hash(expected).equals(version.configurationHash()))throw new IllegalStateException("Version 2.3 must only change riskPercent to 4");
        }
        if(reference.datasetId()==null)throw new IllegalStateException("Reference exact dataset missing");
        var dataset=references.dataset(reference.datasetId());
        // Capture complete bean and global risk, not only the symbol mapping.
        store.immutable("versions/PATTERN_V1_LEGACY.json",Map.of("strategyId","PATTERN_V1","strategyVersion","1.0-legacy","label","PATTERN_V1_LEGACY","configuration",PatternStableStore.JSON.valueToTree(legacy),"globalRisk",PatternStableStore.JSON.valueToTree(trading.getRisk())));
        store.immutable("versions/2.0.json",baseline);
        store.immutable("versions/"+requestedVersion+".json",version);
        store.immutable("references/"+REFERENCE+".json",reference);
        store.immutable("datasets/"+dataset.id()+".json",dataset);
        return version;
    }
    public Run run(){return run(VERSION);}
    public Run runWithRisk(double riskPercent){BacktestRiskParameter.validate(riskPercent);return run(VERSION,riskPercent);}
    public Run run(String requestedVersion){return run(requestedVersion,null);}
    private Run run(String requestedVersion,Double riskPercent){
        var version=version(requestedVersion);Version archived;
        String archive="versions/"+requestedVersion+".json";
        try{archived=store.read(archive,Version.class);}catch(NoSuchElementException e){promote(requestedVersion);archived=store.read(archive,Version.class);}
        if(!archived.configurationHash().equals(version.configurationHash()))throw new IllegalStateException("Frozen version mismatch; explicit new version required");
        var reference=store.read("references/"+REFERENCE+".json",PatternV2Run.class);
        var dataset=store.read("datasets/"+reference.datasetId()+".json",PatternV2Dataset.class);
        if(!dataset.id().equals(PatternV2Dataset.capture(dataset.bars()).id()))throw new IllegalStateException("Dataset checksum mismatch");
        var configuration=BacktestRiskParameter.apply(version.configuration(),riskPercent);
        String id=UUID.randomUUID().toString();var simulation=new StableSimulation().simulate(configuration,dataset.bars(),id);
        simulation=simulation.withResearch(simulation.patternRangeAnalysis(),dataset.id(),REFERENCE);
        simulation=simulation.withExecutionAudit(GapExecutionAudit.capture(simulation,dataset.bars()));
        var run=new Run(id,ID,BacktestRiskParameter.runVersion(requestedVersion,version.configuration(),configuration),Instant.now(),REFERENCE,PatternStableStore.hash(configuration),dataset.id(),simulation);store.save(run);return run;
    }
    public List<Run> history(){return store.runs();}
    public Run result(String id){return store.run(id);}
}
