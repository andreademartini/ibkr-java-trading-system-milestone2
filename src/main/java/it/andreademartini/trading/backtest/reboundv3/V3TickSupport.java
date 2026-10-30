package it.andreademartini.trading.backtest.reboundv3;

import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.marketdata.ticks.MinimumTickResolver;
import it.andreademartini.trading.backtest.weeklytte.WeeklyTteStore;
import java.math.BigDecimal;
import java.util.*;

/** Preflight checks use the same resolver as execution; exact rule snapshots live in the configuration. */
public final class V3TickSupport {
    private V3TickSupport(){}
    public record Report(boolean passed,String metadataVersion,String configurationHash,String datasetId,List<Map<String,Object>> rows) {}
    public static final class ValidationException extends IllegalArgumentException {
        private final Report report;
        public ValidationException(Report report){super("Tick prevalidation failed: "+report.rows().stream().filter(r->"ERROR".equals(r.get("ResolutionStatus"))).map(r->r.get("Symbol")+": "+r.get("ResolutionError")).toList());this.report=report;}
        public Report report(){return report;}
    }
    public static MinimumTickResolver resolver(TrendReboundV3Configuration c){return new MinimumTickResolver(c.minimumTickMetadata(),c.minimumTickSizes(),c.minimumTickOverrideDomain());}
    public static boolean isResearch(TrendReboundV3Configuration c){return "RESEARCH_DATASET_GRID".equals(c.minimumTickResolutionPolicy());}
    public static String metadataVersion(TrendReboundV3Configuration c){return isResearch(c)?"CSV_GRID_RESEARCH/1":c.minimumTickMetadata().version();}
    public static String hash(TrendReboundV3Configuration c){return WeeklyTteStore.hash(Map.of("catalog",c.minimumTickMetadata(),"overrides",c.minimumTickSizes(),"overrideDomain",c.minimumTickOverrideDomain(),"bufferTicks",c.entryBufferTicks(),"policy",c.minimumTickResolutionPolicy(),"researchAcknowledged",c.researchDatasetGridAcknowledged()));}
    public static Report prevalidate(TrendReboundV3Configuration c,Map<String,List<HistoricalBar>> data,String datasetId){
        var rows=new LinkedHashMap<String,Map<String,Object>>();V3TickContext context;
        try{context=new V3TickContext(c,data,datasetId);}catch(IllegalArgumentException e){var row=error("*",null,e.getMessage());return new Report(false,metadataVersion(c),hash(c),datasetId,List.of(row));}
        for(String symbol:c.symbols()){
            if(c.excludedSymbols().contains(symbol))continue;
            var bars=data.getOrDefault(symbol,List.of()).stream().filter(b->!b.dateTime().toLocalDate().isBefore(c.startDate())&&!TrendReboundV3Engine.nextSession(b.dateTime().toLocalDate()).isAfter(c.endDate())).toList();
            if(bars.isEmpty()){var row=error(symbol,null,"NO_DATA_FOR_TICK_PREVALIDATION");if(!data.containsKey(symbol)){row.put("ResolutionStatus","EXCLUDED");row.put("ResolutionError","EXCLUDED_BY_EXISTING_DATA_QUALITY_POLICY");}rows.put(symbol,row);continue;}
            for(var b:bars){
                try{
                    var resolution=context.resolve(symbol,b,TrendReboundV3Engine.nextSession(b.dateTime().toLocalDate()));
                    var row=row(resolution);String key=symbol+":"+resolution.tickRuleId()+":"+resolution.minimumTick()+":"+resolution.priceAdjustmentMode()+":"+resolution.adjustedPriceCompatibility();
                    if(!rows.containsKey(key))rows.put(key,row);else rows.get(key).put("ObservationsValidated",((Number)rows.get(key).get("ObservationsValidated")).longValue()+1);
                }catch(IllegalArgumentException e){rows.put(symbol+":ERROR",error(symbol,b,e.getMessage()));break;}
            }
        }
        return new Report(rows.values().stream().anyMatch(r->"RESOLVED".equals(r.get("ResolutionStatus"))||(isResearch(c)&&"RESEARCH_ASSUMPTION".equals(r.get("ResolutionStatus"))))&&rows.values().stream().noneMatch(r->"ERROR".equals(r.get("ResolutionStatus"))),c.minimumTickMetadata().version(),hash(c),datasetId,List.copyOf(rows.values()));
    }
    public static Map<String,Object> row(MinimumTickResolver.Resolution r){
        var m=new LinkedHashMap<String,Object>();m.put("Symbol",r.symbol());m.put("Exchange",r.exchange());m.put("Currency",r.currency());m.put("InstrumentType",r.instrumentType());m.put("ReferenceDate",r.referenceDate());m.put("ReferencePrice",r.referencePrice());m.put("MinimumTick",r.minimumTick());m.put("TickSource",r.tickSource());m.put("TickRuleId",r.tickRuleId());m.put("PriceAdjustmentMode",r.priceAdjustmentMode());m.put("AdjustedPriceCompatibility",r.adjustedPriceCompatibility());m.put("ResolutionStatus",r.resolutionStatus());m.put("ResolutionError",r.resolutionError());m.put("PriceIncrementMeaning","CSV_DECIMAL_GRID_NOT_MARKET_TICK".equals(r.tickSource())?"DATASET_QUANTUM_NOT_MARKET_TICK":"VERIFIED_OR_EXPLICIT_OVERRIDE");m.put("ObservationsValidated",1);return m;
    }
    private static Map<String,Object> error(String symbol,HistoricalBar b,String message){
        var m=new LinkedHashMap<String,Object>();for(String key:List.of("Exchange","Currency","InstrumentType","MinimumTick","TickSource","TickRuleId","AdjustedPriceCompatibility"))m.put(key,null);
        m.put("Symbol",symbol);m.put("ReferenceDate",b==null?null:TrendReboundV3Engine.nextSession(b.dateTime().toLocalDate()));m.put("ReferencePrice",b==null?null:b.getHigh());m.put("PriceAdjustmentMode",b==null?null:b.priceAdjustment());m.put("ResolutionStatus","ERROR");m.put("ResolutionError",message);return m;
    }
    public static void diagnostics(Map<String,Object> d,MinimumTickResolver.Resolution r,int ticks){
        d.put("MinimumTick",r.minimumTick());d.put("MinimumTickResolved",r.minimumTick());d.put("MinimumTickSource",r.tickSource());d.put("EntryBufferTicks",ticks);d.put("BuyStopTrigger",r.buyStopTrigger());d.put("TickResolutionStatus",r.resolutionStatus());d.put("TickRuleId",r.tickRuleId());d.put("PriceAdjustmentMode",r.priceAdjustmentMode());d.put("AdjustedPriceCompatibility",r.adjustedPriceCompatibility());
    }
}
