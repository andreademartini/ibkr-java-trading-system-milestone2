package it.andreademartini.trading.marketdata.ticks;

import it.andreademartini.trading.marketdata.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

/** Offline, immutable, dataset-bound price grids. No broker or network dependency. */
public final class MinimumTickResolver {
    public record Instrument(String symbol,List<String> aliases,String exchange,String currency,String instrumentType) {
        public Instrument {
            symbol=normalize(symbol);aliases=aliases==null?List.of():aliases.stream().map(MinimumTickResolver::normalize).toList();
            require(exchange,"Exchange");require(currency,"Currency");require(instrumentType,"InstrumentType");
        }
    }
    public record Band(@com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal lowEdge,
            @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal increment) {
        public Band {if(lowEdge==null||lowEdge.signum()<0||increment==null||increment.signum()<=0)throw new IllegalArgumentException("Invalid price band");}
    }
    /** DATASET rules must bind the exact fingerprint. RAW rules require a verified conversion for ADJUSTED bars. */
    public record Rule(String id,String symbol,String exchange,String currency,String instrumentType,
            LocalDate validFrom,LocalDate validTo,String source,String provenance,boolean validated,
            String domain,String datasetId,List<Band> bands) {
        public Rule {
            require(id,"Rule ID");symbol=normalize(symbol);require(exchange,"Exchange");require(currency,"Currency");require(instrumentType,"InstrumentType");
            require(provenance,"Tick provenance");
            if(!List.of("DATASET_METADATA","INSTRUMENT_MASTER","SAVED_MARKET_RULE").contains(source)||!List.of("RAW","DATASET").contains(domain)||validFrom==null||validTo==null||validTo.isBefore(validFrom))throw new IllegalArgumentException("Invalid tick rule");
            if(domain.equals("DATASET")||source.equals("DATASET_METADATA"))require(datasetId,"Dataset fingerprint");
            bands=bands==null?List.of():bands.stream().sorted(Comparator.comparing(Band::lowEdge)).toList();
            if(bands.isEmpty()||bands.getFirst().lowEdge().signum()!=0||bands.stream().map(b->b.lowEdge().stripTrailingZeros()).distinct().count()!=bands.size())throw new IllegalArgumentException("Price grid must start at zero with unique edges");
        }
    }
    public record PriceBasis(String symbol,String datasetId,LocalDate validFrom,LocalDate validTo,
            String adjustmentMode,@com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal rawPerDatasetPrice,String provenance,boolean validated) {
        public PriceBasis {
            symbol=normalize(symbol);require(datasetId,"Dataset fingerprint");require(provenance,"Price basis provenance");
            if(validFrom==null||validTo==null||validTo.isBefore(validFrom)||!List.of("RAW","SPLIT_ADJUSTED","TOTAL_RETURN_ADJUSTED","PROVIDER_ADJUSTED").contains(adjustmentMode)||rawPerDatasetPrice!=null&&rawPerDatasetPrice.signum()<=0)throw new IllegalArgumentException("Invalid historical price basis");
            if("RAW".equals(adjustmentMode)&&rawPerDatasetPrice!=null&&rawPerDatasetPrice.compareTo(BigDecimal.ONE)!=0)throw new IllegalArgumentException("RAW factor must equal one");
        }
    }
    public record Catalog(String version,List<Instrument> instruments,List<Rule> rules,List<PriceBasis> priceBases) {
        public Catalog {
            require(version,"Metadata version");instruments=instruments==null?List.of():List.copyOf(instruments);rules=rules==null?List.of():List.copyOf(rules);priceBases=priceBases==null?List.of():List.copyOf(priceBases);
            if("UNCONFIGURED".equals(version)&&(!instruments.isEmpty()||!rules.isEmpty()||!priceBases.isEmpty()))throw new IllegalArgumentException("A populated catalog requires an explicit metadata version");
            var symbols=new HashSet<String>();for(var i:instruments){if(!symbols.add(i.symbol()))throw new IllegalArgumentException("Duplicate instrument identity");for(String a:i.aliases())if(!symbols.add(a))throw new IllegalArgumentException("Duplicate instrument alias");}
            if(rules.stream().map(Rule::id).distinct().count()!=rules.size())throw new IllegalArgumentException("Duplicate tick rule ID");
        }
        public static Catalog empty(){return new Catalog("UNCONFIGURED",List.of(),List.of(),List.of());}
    }
    public record Resolution(String symbol,String exchange,String currency,String instrumentType,LocalDate referenceDate,
            BigDecimal referencePrice,BigDecimal minimumTick,String tickSource,String tickRuleId,String priceAdjustmentMode,
            String adjustedPriceCompatibility,BigDecimal buyStopTrigger,String resolutionStatus,String resolutionError) {}
    public static final class TickException extends IllegalArgumentException {public TickException(String message){super(message);}}
    private record Grid(Instrument instrument,Rule rule,BigDecimal factor,String mode,String compatibility,List<Band> bands) {}
    private final Catalog catalog;private final Map<String,BigDecimal> overrides;private final String overrideDomain;
    public MinimumTickResolver(Catalog catalog,Map<String,BigDecimal> overrides,String overrideDomain){
        this.catalog=Objects.requireNonNull(catalog);this.overrides=Map.copyOf(overrides);this.overrideDomain=overrideDomain;
        for(var e:overrides.entrySet())if(!normalize(e.getKey()).equals(e.getKey())||e.getValue()==null||e.getValue().signum()<=0)throw new IllegalArgumentException("Invalid tick override: "+e.getKey());
        if(!List.of("UNSPECIFIED","RAW","DATASET").contains(overrideDomain))throw new IllegalArgumentException("Specify RAW or DATASET for overrides");
        for(String symbol:overrides.keySet())instrument(symbol);
    }
    private static void require(String s,String label){if(s==null||s.isBlank())throw new IllegalArgumentException(label+" required");}
    private static String normalize(String s){require(s,"Symbol");return s.trim().toUpperCase(Locale.ROOT);}
    private Instrument instrument(String symbol){
        String key=normalize(symbol);return catalog.instruments().stream().filter(i->i.symbol().equals(key)||i.aliases().contains(key)).findFirst().orElseThrow(()->new TickException(key+": INSTRUMENT_METADATA_UNAVAILABLE (exchange/currency/type or explicit alias missing)"));
    }
    private static boolean in(LocalDate d,LocalDate from,LocalDate to){return !d.isBefore(from)&&!d.isAfter(to);}
    private Grid grid(String symbol,LocalDate date,PriceAdjustment adjustment,String dataset){
        var i=instrument(symbol);
        var bases=catalog.priceBases().stream().filter(b->b.validated()&&b.symbol().equals(i.symbol())&&b.datasetId().equals(dataset)&&in(date,b.validFrom(),b.validTo())).toList();
        if(bases.size()>1)throw new TickException(symbol+": AMBIGUOUS_PRICE_BASIS");
        PriceBasis basis=bases.isEmpty()?null:bases.getFirst();
        String mode=adjustment==PriceAdjustment.RAW?"RAW":basis==null?"ADJUSTED_UNSPECIFIED":basis.adjustmentMode();
        if(basis!=null&&(("RAW".equals(basis.adjustmentMode()))!=(adjustment==PriceAdjustment.RAW)))throw new TickException(symbol+": PRICE_ADJUSTMENT_MODE_MISMATCH");
        var matches=catalog.rules().stream().filter(r->r.validated()&&r.symbol().equals(i.symbol())&&r.exchange().equals(i.exchange())&&r.currency().equals(i.currency())&&r.instrumentType().equals(i.instrumentType())&&in(date,r.validFrom(),r.validTo())&&(r.datasetId()==null||dataset.equals(r.datasetId()))).sorted(Comparator.comparingInt(r->priority(r.source()))).toList();
        Rule rule=null;List<Band> bands;String domain;
        if(!matches.isEmpty()){
            rule=matches.getFirst();int rank=priority(rule.source());
            if(matches.stream().filter(r->priority(r.source())==rank).count()>1)throw new TickException(symbol+": CONFLICTING_TICK_RULES");
            bands=rule.bands();domain=rule.domain();
        }else{
            BigDecimal tick=overrides.getOrDefault(normalize(symbol),overrides.get(i.symbol()));
            if(tick==null)throw new TickException(symbol+": MINIMUM_TICK_UNAVAILABLE (no validated rule or override for "+date+")");
            if("UNSPECIFIED".equals(overrideDomain))throw new TickException(symbol+": OVERRIDE_PRICE_DOMAIN_UNSPECIFIED");
            domain=overrideDomain;bands=List.of(new Band(BigDecimal.ZERO,tick));
        }
        if(adjustment==PriceAdjustment.ADJUSTED&&basis==null)throw new TickException(symbol+": ADJUSTED_PRICE_COMPATIBILITY_UNVERIFIED (dataset-bound basis and provenance required)");
        BigDecimal factor=BigDecimal.ONE;String compatibility="RAW_PRICE_GRID";
        if(adjustment==PriceAdjustment.ADJUSTED){
            if(domain.equals("RAW")){
                if(basis.rawPerDatasetPrice()==null)throw new TickException(symbol+": HISTORICAL_ADJUSTMENT_FACTOR_UNAVAILABLE");
                factor=basis.rawPerDatasetPrice();compatibility="EXACT_RAW_TO_DATASET_TRANSFORMATION";
            }else compatibility=rule==null?"EXPLICIT_DATASET_DOMAIN_OVERRIDE":"VALIDATED_DATASET_PRICE_GRID";
        }else if(basis!=null&&domain.equals("RAW"))factor=BigDecimal.ONE;
        return new Grid(i,rule,factor,mode,compatibility,bands);
    }
    private static int priority(String s){return switch(s){case "DATASET_METADATA"->0;case "INSTRUMENT_MASTER"->1;case "SAVED_MARKET_RULE"->2;default->throw new IllegalArgumentException("Unknown tick source");};}
    /** Successor on a piecewise price lattice; boundary prices use the final applicable band. */
    private static BigDecimal successor(BigDecimal p,List<Band> bands){
        BigDecimal best=null;
        for(int i=0;i<bands.size();i++){
            var b=bands.get(i);BigDecimal candidate;
            if(p.compareTo(b.lowEdge())<0)candidate=b.lowEdge();
            else candidate=b.lowEdge().add(p.subtract(b.lowEdge()).divide(b.increment(),0,RoundingMode.FLOOR).add(BigDecimal.ONE).multiply(b.increment()));
            if(i+1<bands.size()&&candidate.compareTo(bands.get(i+1).lowEdge())>=0)continue;
            if(candidate.compareTo(p)>0&&(best==null||candidate.compareTo(best)<0))best=candidate;
        }
        if(best==null)throw new TickException("No valid price above reference");return best;
    }
    private static BigDecimal increment(BigDecimal price,List<Band> bands){
        Band selected=bands.getFirst();for(var b:bands)if(price.compareTo(b.lowEdge())>=0)selected=b;return selected.increment();
    }
    public Resolution resolve(String symbol,LocalDate date,BigDecimal referencePrice,PriceAdjustment adjustment,String dataset,int bufferTicks){
        if(date==null||referencePrice==null||referencePrice.signum()<=0||bufferTicks<1||bufferTicks>10000)throw new TickException("Positive reference price and buffer 1..10000 required");
        var grid=grid(symbol,date,adjustment,dataset);BigDecimal tradingPrice=referencePrice.multiply(grid.factor()),candidate=tradingPrice;
        for(int n=0;n<bufferTicks;n++)candidate=successor(candidate,grid.bands());
        BigDecimal tick,trigger;
        try{tick=increment(candidate,grid.bands()).divide(grid.factor());trigger=candidate.divide(grid.factor());}
        catch(ArithmeticException e){throw new TickException(symbol+": NON_TERMINATING_ADJUSTED_GRID (exact BigDecimal representation unavailable)");}
        var i=grid.instrument();var rule=grid.rule();
        return new Resolution(symbol,i.exchange(),i.currency(),i.instrumentType(),date,referencePrice,tick,rule==null?"EXPLICIT_OVERRIDE":rule.source(),rule==null?"OVERRIDE:"+i.symbol():rule.id(),grid.mode(),grid.compatibility(),trigger,"RESOLVED",null);
    }
}
