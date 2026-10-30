package it.andreademartini.trading.backtest.reboundv3;

import it.andreademartini.trading.backtest.BacktestEngine;
import it.andreademartini.trading.backtest.patternv2.PatternV2Dataset;
import it.andreademartini.trading.backtest.weeklytte.WeeklyTteCsvData;
import it.andreademartini.trading.marketdata.HistoricalBar;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import it.andreademartini.trading.backtest.rebound.TrendReboundRun.DataIssue;
import static it.andreademartini.trading.backtest.reboundv3.TrendReboundV3Run.*;

/** Reads local input only. No HistoricalDataService refresh and no operational strategy registration. */
@Service
public class TrendReboundV3Service {
    private final TrendReboundV3Store store;private final BacktestEngine backtests;private final TrendReboundV3Engine engine;
    public TrendReboundV3Service(TrendReboundV3Store store,BacktestEngine backtests,TrendReboundV3Engine engine){this.store=store;this.backtests=backtests;this.engine=engine;}
    private record Prepared(TrendReboundV3Configuration configuration,PatternV2Dataset snapshot,List<DataIssue> issues) {}
    public TrendReboundV3Configuration withLocalMetadata(TrendReboundV3Configuration c){
        if(V3TickSupport.isResearch(c))return c;
        var path=Path.of("data","instrument-master","minimum-tick-rules.json");
        if(!"UNCONFIGURED".equals(c.minimumTickMetadata().version())||!Files.isRegularFile(path))return c;
        try{
            var catalog=TrendReboundV3Store.JSON.readValue(path.toFile(),it.andreademartini.trading.marketdata.ticks.MinimumTickResolver.Catalog.class);
            var node=(com.fasterxml.jackson.databind.node.ObjectNode)TrendReboundV3Store.JSON.valueToTree(c);node.set("minimumTickMetadata",TrendReboundV3Store.JSON.valueToTree(catalog));
            return TrendReboundV3Store.JSON.treeToValue(node,TrendReboundV3Configuration.class);
        }catch(IOException e){throw new IllegalArgumentException("Cannot load offline minimum-tick metadata: "+path,e);}
    }
    public TrendReboundV3Run run(TrendReboundV3Configuration c){var p=prepare(withLocalMetadata(c));return execute(p.configuration(),p.snapshot(),p.issues());}
    public V3TickSupport.Report prevalidate(TrendReboundV3Configuration c){var p=prepare(withLocalMetadata(c));return V3TickSupport.prevalidate(p.configuration(),p.snapshot().bars(),p.snapshot().id());}
    private Prepared prepare(TrendReboundV3Configuration c){
        c.validate();var input=new TreeMap<String,List<HistoricalBar>>();var issues=new ArrayList<DataIssue>();
        for(var symbol:c.symbols()){
            if(c.excludedSymbols().contains(symbol))continue;
            var path=Path.of("data","stooq",symbol+".US.csv");
            try{var loaded=WeeklyTteCsvData.load(path,symbol);input.put(symbol,loaded.bars().stream().filter(b->b.dateTime().getYear()>=2010&&!TrendReboundV3Engine.date(b).isAfter(c.endDate())).toList());issues.add(new DataIssue(symbol,"INFO","SOURCE_SHA256",loaded.sourceSha256()));if(loaded.fractionalVolumes()>0)issues.add(new DataIssue(symbol,"WARNING","FRACTIONAL_VOLUME",loaded.fractionalVolumes()+" adjusted volume values rounded down by the existing CSV adapter; volume not used for signals"));}
            catch(IOException|IllegalArgumentException e){issues.add(new DataIssue(symbol,"ERROR","CSV_EXCLUDED",e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));}
        }
        // Exclude every duplicate instrument; do not arbitrarily choose BA or BAC as the correct identity.
        var byHash=new HashMap<String,List<String>>();input.forEach((symbol,bars)->{if(!bars.isEmpty())byHash.computeIfAbsent(PatternV2Dataset.capture(Map.of("IDENTITY",bars)).id(),k->new ArrayList<>()).add(symbol);});
        for(var symbols:byHash.values())if(symbols.size()>1)for(String symbol:symbols){input.remove(symbol);issues.add(new DataIssue(symbol,"ERROR","IDENTICAL_PRICE_VOLUME_DATA","Excluded duplicate dataset shared by "+symbols));}
        if(input.isEmpty())throw new IllegalArgumentException("No usable local datasets: "+issues.stream().filter(i->i.severity().equals("ERROR")).map(i->i.symbol()+": "+i.code()+" "+i.detail()).toList());
        return new Prepared(c,PatternV2Dataset.capture(input),List.copyOf(issues));
    }
    public TrendReboundV3Run onSnapshot(TrendReboundV3Configuration c,PatternV2Dataset snapshot){return execute(c,snapshot,List.of());}
    private TrendReboundV3Run execute(TrendReboundV3Configuration c,PatternV2Dataset snapshot,List<DataIssue> sourceIssues){
        c.validate();var r=engine.simulate(c,snapshot.bars(),snapshot.id(),it.andreademartini.trading.backtest.weeklytte.WeeklyTteStore.hash(c));store.configuration(c);store.dataset(snapshot);
 var baseline=new it.andreademartini.trading.backtest.rebound.TrendReboundEngine(new it.andreademartini.trading.risk.PositionSizingService(new it.andreademartini.trading.risk.TradePricingService())).simulate(c.benchmark(),snapshot.bars(),snapshot.id(),it.andreademartini.trading.backtest.weeklytte.WeeklyTteStore.hash(c.benchmark()));
 r.analysis().put("entryModeComparison",List.of(V3Comparison.row("V1_NEXT_OPEN",baseline.trades(),baseline.signals().size(),baseline.analysis()),V3Comparison.row(V3TickSupport.isResearch(c)?"V3_RESEARCH_CSV_GRID_UNVERIFIED":"V3_BUY_STOP",r.trades(),r.signals().size(),r.analysis())));
        var issues=new ArrayList<>(sourceIssues);issues.addAll(r.dataQuality());var warnings=new ArrayList<>(r.warnings());if(sourceIssues.stream().anyMatch(i->i.severity().equals("ERROR")))warnings.add("Source exclusions present: inspect DATA_QUALITY. No suspicious instrument was silently included.");
        var result=new TrendReboundV3Run(r.runId(),r.strategy(),r.strategyVersion(),r.timestamp(),r.configurationHash(),r.datasetId(),r.calendarVersion(),r.configuration(),r.signals(),r.trades(),r.equity(),List.copyOf(issues),r.analysis(),List.copyOf(warnings));store.save(result);return result;
    }
    public TrendReboundV3Run replay(String id){var run=store.run(id);var reproduced=execute(run.configuration(),store.dataset(run.datasetId()),run.dataQuality().stream().filter(i->Set.of("SOURCE_SHA256","CSV_EXCLUDED","IDENTICAL_PRICE_VOLUME_DATA","FRACTIONAL_VOLUME").contains(i.code())).toList());return reproduced;}
}
