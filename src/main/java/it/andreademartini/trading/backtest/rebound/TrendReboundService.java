package it.andreademartini.trading.backtest.rebound;

import it.andreademartini.trading.backtest.BacktestEngine;
import it.andreademartini.trading.backtest.patternv2.PatternV2Dataset;
import it.andreademartini.trading.backtest.weeklytte.WeeklyTteCsvData;
import it.andreademartini.trading.marketdata.HistoricalBar;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import static it.andreademartini.trading.backtest.rebound.TrendReboundRun.*;

/** Reads local input only. No HistoricalDataService refresh and no operational strategy registration. */
@Service
public class TrendReboundService {
    private final TrendReboundStore store;private final BacktestEngine backtests;private final TrendReboundEngine engine;
    public TrendReboundService(TrendReboundStore store,BacktestEngine backtests,TrendReboundEngine engine){this.store=store;this.backtests=backtests;this.engine=engine;}
    public TrendReboundRun run(TrendReboundConfiguration c){
        c.validate();var input=new TreeMap<String,List<HistoricalBar>>();var issues=new ArrayList<DataIssue>();
        for(var symbol:c.symbols()){
            if(c.excludedSymbols().contains(symbol))continue;
            var path=Path.of("data","stooq",symbol+".US.csv");
            try{var loaded=WeeklyTteCsvData.load(path,symbol);input.put(symbol,loaded.bars().stream().filter(b->b.dateTime().getYear()>=2010&&!TrendReboundEngine.date(b).isAfter(c.endDate())).toList());issues.add(new DataIssue(symbol,"INFO","SOURCE_SHA256",loaded.sourceSha256()));if(loaded.fractionalVolumes()>0)issues.add(new DataIssue(symbol,"WARNING","FRACTIONAL_VOLUME",loaded.fractionalVolumes()+" adjusted volume values rounded down by the existing CSV adapter; volume not used for signals"));}
            catch(IOException|IllegalArgumentException e){issues.add(new DataIssue(symbol,"ERROR","CSV_EXCLUDED",e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));}
        }
        // Exclude every duplicate instrument; do not arbitrarily choose BA or BAC as the correct identity.
        var byHash=new HashMap<String,List<String>>();input.forEach((symbol,bars)->{if(!bars.isEmpty())byHash.computeIfAbsent(PatternV2Dataset.capture(Map.of("IDENTITY",bars)).id(),k->new ArrayList<>()).add(symbol);});
        for(var symbols:byHash.values())if(symbols.size()>1)for(String symbol:symbols){input.remove(symbol);issues.add(new DataIssue(symbol,"ERROR","IDENTICAL_PRICE_VOLUME_DATA","Excluded duplicate dataset shared by "+symbols));}
        if(input.isEmpty())throw new IllegalArgumentException("No usable local datasets: "+issues.stream().filter(i->i.severity().equals("ERROR")).map(i->i.symbol()+": "+i.code()+" "+i.detail()).toList());
        return execute(c,PatternV2Dataset.capture(input),issues);
    }
    public TrendReboundRun onSnapshot(TrendReboundConfiguration c,PatternV2Dataset snapshot){return execute(c,snapshot,List.of());}
    private TrendReboundRun execute(TrendReboundConfiguration c,PatternV2Dataset snapshot,List<DataIssue> sourceIssues){
        c.validate();var r=backtests.run(c,snapshot,engine);store.configuration(c);store.dataset(snapshot);
        var issues=new ArrayList<>(sourceIssues);issues.addAll(r.dataQuality());var warnings=new ArrayList<>(r.warnings());if(sourceIssues.stream().anyMatch(i->i.severity().equals("ERROR")))warnings.add("Source exclusions present: inspect DATA_QUALITY. No suspicious instrument was silently included.");
        var result=new TrendReboundRun(r.runId(),r.strategy(),r.strategyVersion(),r.timestamp(),r.configurationHash(),r.datasetId(),r.calendarVersion(),r.configuration(),r.signals(),r.trades(),r.equity(),List.copyOf(issues),r.analysis(),List.copyOf(warnings));store.save(result);return result;
    }
    public TrendReboundRun replay(String id){var run=store.run(id);var reproduced=execute(run.configuration(),store.dataset(run.datasetId()),run.dataQuality().stream().filter(i->Set.of("SOURCE_SHA256","CSV_EXCLUDED","IDENTICAL_PRICE_VOLUME_DATA","FRACTIONAL_VOLUME").contains(i.code())).toList());return reproduced;}
}
