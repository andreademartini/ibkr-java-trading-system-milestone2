package it.andreademartini.trading.backtest.patternv2;

import it.andreademartini.trading.backtest.patternv1.PatternV1Commission;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.research.pattern.*;
import it.andreademartini.trading.strategy.patternv1.PatternV1Properties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Immutable run snapshot: never stores a V1 bean, mutable collection or risk configuration. */
public record PatternV2Configuration(String name, Global global, List<Symbol> symbols, PatternV2SplitCosts splitCosts,
        boolean patternRangeFilterEnabled, BigDecimal minimumPatternRangePct,
        boolean closeStrengthFilterEnabled, BigDecimal maxCloseStrength, boolean capitalSkipShadowAnalysisEnabled,
        boolean bollingerAtrFilterEnabled,Integer bollingerPeriod,BigDecimal bollingerStdDev,Integer atrPeriod,BigDecimal maxBollingerDistanceAtr,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT) Boolean marketRegimeAnalysisEnabled) {
    public PatternV2Configuration(String name, Global global,List<Symbol> symbols,PatternV2SplitCosts splitCosts,boolean rangeEnabled,BigDecimal minimum,boolean closeEnabled,BigDecimal maximum,boolean shadowEnabled,boolean bollingerEnabled,Integer bollingerPeriod,BigDecimal bollingerStdDev,Integer atrPeriod,BigDecimal maxDistance) {
        this(name,global,symbols,splitCosts,rangeEnabled,minimum,closeEnabled,maximum,shadowEnabled,bollingerEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxDistance,false);
    }
    public PatternV2Configuration(String name,Global global,List<Symbol> symbols,PatternV2SplitCosts splitCosts,boolean rangeEnabled,BigDecimal minimum,boolean closeEnabled,BigDecimal maximum,boolean shadowEnabled) {
        this(name,global,symbols,splitCosts,rangeEnabled,minimum,closeEnabled,maximum,shadowEnabled,false,null,null,null,null);
    }
    public PatternV2Configuration(String name,Global global,List<Symbol> symbols,PatternV2SplitCosts splitCosts,boolean rangeEnabled,BigDecimal minimum,boolean closeEnabled,BigDecimal maximum){this(name,global,symbols,splitCosts,rangeEnabled,minimum,closeEnabled,maximum,false);}
    public PatternV2Configuration(String name, Global global,List<Symbol> symbols,PatternV2SplitCosts splitCosts,
            boolean patternRangeFilterEnabled,BigDecimal minimumPatternRangePct) {
        this(name,global,symbols,splitCosts,patternRangeFilterEnabled,minimumPatternRangePct,false,new BigDecimal("0.80"));
    }
    public PatternV2Configuration(String name, Global global, List<Symbol> symbols, PatternV2SplitCosts splitCosts) {
        this(name,global,symbols,splitCosts,false,new BigDecimal("2.50"));
    }
    /** Missing splitCosts identifies legacy snapshots; do not rewrite historical results on deserialization. */
    public PatternV2Configuration(String name, Global global, List<Symbol> symbols) { this(name,global,symbols,null); }
    public enum OrderValueMode { FIXED_INITIAL_CAPITAL_PERCENT, CURRENT_EQUITY_PERCENT }
    public enum CapitalLimitMode { FIXED_AMOUNT, CURRENT_EQUITY_PERCENT }
    public record Global(double initialCapital, double riskPercent, double maxOrderValuePercent,
            Double maxOrderValue, OrderValueMode maxOrderValueMode, double maxPositionValue,
            long maxQuantity, boolean useLeverage, long minQuantity, double minOrderValue,
            LocalDate startDate, LocalDate endDate, BarInterval timeframe, int maxHoldingBars,
            int stopBufferTicks, double tickSize, SameBarResolution sameBarPolicy,
            EntryMethod entryMode, StopMethod stopMode, boolean commissionEnabled,
            PatternV1Commission commission, boolean slippageEnabled, double slippagePercentPerSide,
            double maxLongNotional, double maxShortNotional, double transactionCostsPerShare,
            boolean allowLong, boolean allowShort, long smallQuantityWarningThreshold,
            CapitalLimitMode maxPositionValueMode, Double maxPositionValuePercent, CapitalLimitMode maxLongNotionalMode, Double maxLongNotionalPercent, CapitalLimitMode maxShortNotionalMode, Double maxShortNotionalPercent) {
        public Global {
            maxPositionValueMode = maxPositionValueMode == null ? CapitalLimitMode.FIXED_AMOUNT : maxPositionValueMode;
            maxPositionValuePercent = maxPositionValuePercent == null ? 100.0 : maxPositionValuePercent;
            maxLongNotionalMode = maxLongNotionalMode == null ? CapitalLimitMode.FIXED_AMOUNT : maxLongNotionalMode;
            maxLongNotionalPercent = maxLongNotionalPercent == null ? 100.0 : maxLongNotionalPercent;
            maxShortNotionalMode = maxShortNotionalMode == null ? CapitalLimitMode.FIXED_AMOUNT : maxShortNotionalMode;
            maxShortNotionalPercent = maxShortNotionalPercent == null ? 100.0 : maxShortNotionalPercent;
        }
        public Global(double initialCapital, double riskPercent, double maxOrderValuePercent,
            Double maxOrderValue, OrderValueMode maxOrderValueMode, double maxPositionValue,
            long maxQuantity, boolean useLeverage, long minQuantity, double minOrderValue,
            LocalDate startDate, LocalDate endDate, BarInterval timeframe, int maxHoldingBars,
            int stopBufferTicks, double tickSize, SameBarResolution sameBarPolicy,
            EntryMethod entryMode, StopMethod stopMode, boolean commissionEnabled,
            PatternV1Commission commission, boolean slippageEnabled, double slippagePercentPerSide,
            double maxLongNotional, double maxShortNotional, double transactionCostsPerShare,
            boolean allowLong, boolean allowShort, long smallQuantityWarningThreshold) {
            this(initialCapital,riskPercent,maxOrderValuePercent,maxOrderValue,maxOrderValueMode,maxPositionValue,maxQuantity,useLeverage,minQuantity,minOrderValue,startDate,endDate,timeframe,maxHoldingBars,stopBufferTicks,tickSize,sameBarPolicy,entryMode,stopMode,commissionEnabled,commission,slippageEnabled,slippagePercentPerSide,maxLongNotional,maxShortNotional,transactionCostsPerShare,allowLong,allowShort,smallQuantityWarningThreshold,null,null,null,null,null,null);
        }
    }
    public record Direction(boolean enabled, PatternType pattern, BigDecimal targetR) {}
    public record Symbol(boolean enabled, String symbol, Direction longConfig, Direction shortConfig,
            Double riskPercent, Double maxOrderValuePercent, Long maxQuantity, Integer maxHoldingBars) {
        public Symbol {
            if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("Simbolo vuoto");
            symbol = symbol.trim().toUpperCase(Locale.ROOT);
            if (!symbol.matches("[A-Z0-9][A-Z0-9._-]{0,31}")) throw new IllegalArgumentException("Simbolo non valido: " + symbol);
            longConfig = longConfig == null ? new Direction(false,null,null) : longConfig;
            shortConfig = shortConfig == null ? new Direction(false,null,null) : shortConfig;
        }
        public Direction direction(PatternDirection value) { return value == PatternDirection.LONG ? longConfig : shortConfig; }
        public int holding(Global g) { return maxHoldingBars == null ? g.maxHoldingBars() : maxHoldingBars; }
    }
    public PatternV2Configuration {
        marketRegimeAnalysisEnabled=Boolean.TRUE.equals(marketRegimeAnalysisEnabled);
        name = name == null || name.isBlank() ? "V2_BASELINE" : name.trim();
        if (name.length() > 100) throw new IllegalArgumentException("Nome configurazione troppo lungo");
        symbols = symbols == null ? List.of() : List.copyOf(symbols);
        minimumPatternRangePct = minimumPatternRangePct == null ? new BigDecimal("2.50") : minimumPatternRangePct;
        bollingerPeriod=bollingerPeriod==null?20:bollingerPeriod;atrPeriod=atrPeriod==null?14:atrPeriod;
        bollingerStdDev=bollingerStdDev==null?new BigDecimal("2.0"):bollingerStdDev;maxBollingerDistanceAtr=maxBollingerDistanceAtr==null?new BigDecimal("0.50"):maxBollingerDistanceAtr;
        maxCloseStrength = maxCloseStrength == null ? new BigDecimal("0.80") : maxCloseStrength;
    }
    public PatternV2Configuration withGlobal(Global value) { return new PatternV2Configuration(name,value,symbols,splitCosts,patternRangeFilterEnabled,minimumPatternRangePct,closeStrengthFilterEnabled,maxCloseStrength,capitalSkipShadowAnalysisEnabled,bollingerAtrFilterEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxBollingerDistanceAtr,marketRegimeAnalysisEnabled); }
    public PatternV2Configuration withSymbols(List<Symbol> value) { return new PatternV2Configuration(name,global,value,splitCosts,patternRangeFilterEnabled,minimumPatternRangePct,closeStrengthFilterEnabled,maxCloseStrength,capitalSkipShadowAnalysisEnabled,bollingerAtrFilterEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxBollingerDistanceAtr,marketRegimeAnalysisEnabled); }
    public PatternV2Configuration withSplitCosts(PatternV2SplitCosts value) { return new PatternV2Configuration(name,global,symbols,value,patternRangeFilterEnabled,minimumPatternRangePct,closeStrengthFilterEnabled,maxCloseStrength,capitalSkipShadowAnalysisEnabled,bollingerAtrFilterEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxBollingerDistanceAtr,marketRegimeAnalysisEnabled); }
    public PatternV2Configuration forNewRun() { return splitCosts==null?withSplitCosts(PatternV2SplitCosts.defaults()):this; }
    public PatternV2Configuration withRangeFilter(boolean enabled, BigDecimal minimum) {
        return new PatternV2Configuration(name,global,symbols,splitCosts,enabled,minimum,closeStrengthFilterEnabled,maxCloseStrength,capitalSkipShadowAnalysisEnabled,bollingerAtrFilterEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxBollingerDistanceAtr,marketRegimeAnalysisEnabled);
    }
    public PatternV2Configuration withName(String value) {
        return new PatternV2Configuration(value,global,symbols,splitCosts,patternRangeFilterEnabled,minimumPatternRangePct,closeStrengthFilterEnabled,maxCloseStrength,capitalSkipShadowAnalysisEnabled,bollingerAtrFilterEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxBollingerDistanceAtr,marketRegimeAnalysisEnabled);
    }
    public PatternV2Configuration withCloseStrengthFilter(boolean enabled,BigDecimal maximum) {
        return new PatternV2Configuration(name,global,symbols,splitCosts,patternRangeFilterEnabled,minimumPatternRangePct,enabled,maximum,capitalSkipShadowAnalysisEnabled,bollingerAtrFilterEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxBollingerDistanceAtr,marketRegimeAnalysisEnabled);
    }
    public PatternV2Configuration withCapitalSkipShadowAnalysis(boolean enabled){return new PatternV2Configuration(name,global,symbols,splitCosts,patternRangeFilterEnabled,minimumPatternRangePct,closeStrengthFilterEnabled,maxCloseStrength,enabled,bollingerAtrFilterEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxBollingerDistanceAtr,marketRegimeAnalysisEnabled);}
    public PatternV2Configuration withBollingerAtr(boolean enabled,int period,BigDecimal stdDev,int atr,BigDecimal distance) {
        return new PatternV2Configuration(name,global,symbols,splitCosts,patternRangeFilterEnabled,minimumPatternRangePct,closeStrengthFilterEnabled,maxCloseStrength,capitalSkipShadowAnalysisEnabled,enabled,period,stdDev,atr,distance,marketRegimeAnalysisEnabled);
    }
    public PatternV2Configuration withMarketRegimeAnalysis(boolean enabled) {return new PatternV2Configuration(name,global,symbols,splitCosts,patternRangeFilterEnabled,minimumPatternRangePct,closeStrengthFilterEnabled,maxCloseStrength,capitalSkipShadowAnalysisEnabled,bollingerAtrFilterEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxBollingerDistanceAtr,enabled);}
    public void validate(List<PatternDetector> detectors) {
        if(marketRegimeAnalysisEnabled&&global!=null&&global.timeframe()!=BarInterval.DAILY)throw new IllegalArgumentException("Market Regime Analysis requires DAILY bars");
        if(bollingerPeriod<2||atrPeriod<1||bollingerStdDev.signum()<=0||maxBollingerDistanceAtr.signum()<0)throw new IllegalArgumentException("Invalid Bollinger/ATR periods, multiplier or distance");
        if(bollingerAtrFilterEnabled&&global!=null&&global.timeframe()!=BarInterval.DAILY)throw new IllegalArgumentException("Bollinger + ATR filter requires DAILY bars");
        if(maxCloseStrength.signum()<0||maxCloseStrength.compareTo(BigDecimal.ONE)>0)
            throw new IllegalArgumentException("Maximum Close Strength must be between 0 and 1");
        if (minimumPatternRangePct.signum()<0) throw new IllegalArgumentException("Minimum Pattern Range % deve essere non negativo");
        if (global == null) throw new IllegalArgumentException("Parametri globali mancanti");
        var g=global;
        positive(g.initialCapital(),"Initial Capital");positive(g.riskPercent(),"Risk Percent");
        positive(g.maxOrderValuePercent(),"Max Order Value Percent");positive(g.maxPositionValue(),"Max Position Value");
        positive(g.maxLongNotional(),"Max Long Notional");positive(g.maxShortNotional(),"Max Short Notional");
        positive(g.maxPositionValuePercent(),"maxPositionValuePercent");
        positive(g.maxLongNotionalPercent(),"maxLongNotionalPercent");
        positive(g.maxShortNotionalPercent(),"maxShortNotionalPercent");
        if (g.maxOrderValue()!=null) positive(g.maxOrderValue(),"Max Order Value");
        positive(g.tickSize(),"Tick Size");nonnegative(g.minOrderValue(),"Min Order Value");
        nonnegative(g.transactionCostsPerShare(),"Transaction Costs Per Share");nonnegative(g.slippagePercentPerSide(),"Slippage");
        if (g.slippagePercentPerSide()>=100 || g.maxQuantity()<1 || g.minQuantity()<1 || g.maxHoldingBars()<1
                || g.stopBufferTicks()<0 || g.smallQuantityWarningThreshold()<0)
            throw new IllegalArgumentException("Quantità/holding devono essere positivi; slippage deve essere < 100%; buffer non negativo");
        if (g.startDate()==null || g.timeframe()==null || g.sameBarPolicy()==null || g.maxOrderValueMode()==null
                || g.entryMode()!=EntryMethod.NEXT_BAR_OPEN || g.stopMode()!=StopMethod.PATTERN_EXTREME
                || g.commission()==null) throw new IllegalArgumentException("Date, timeframe, modalità o commissioni mancanti/non supportati");
        if (g.endDate()!=null && g.endDate().isBefore(g.startDate())) throw new IllegalArgumentException("Data finale precedente alla data iniziale");
        Map<PatternType,PatternDirection> supported=new EnumMap<>(PatternType.class);
        detectors.forEach(d->supported.put(d.patternType(),d.direction()));
        Set<String> names=new HashSet<>();boolean active=false;
        for (var s:symbols) {
            if (!names.add(s.symbol())) throw new IllegalArgumentException("Simbolo duplicato: "+s.symbol());
            if(s.riskPercent()!=null) positive(s.riskPercent(),s.symbol()+" Risk %");
            if(s.maxOrderValuePercent()!=null) positive(s.maxOrderValuePercent(),s.symbol()+" Max Order %");
            if(s.maxQuantity()!=null && s.maxQuantity()<1 || s.maxHoldingBars()!=null && s.maxHoldingBars()<1)
                throw new IllegalArgumentException(s.symbol()+": quantità/holding non validi");
            for(var direction:PatternDirection.values()) {
                var d=s.direction(direction);
                if(d.enabled()) {
                    if(d.targetR()==null || d.targetR().signum()<=0 || d.pattern()==null || supported.get(d.pattern())!=direction)
                        throw new IllegalArgumentException(s.symbol()+" "+direction+": scegliere un pattern valido e Target R positivo");
                    active |= s.enabled() && (direction==PatternDirection.LONG?g.allowLong():g.allowShort());
                }
            }
        }
        if(!active) throw new IllegalArgumentException("Abilitare almeno un simbolo e una direzione consentita");
    }
    private static void positive(double n,String name) { if(!Double.isFinite(n)||n<=0) throw new IllegalArgumentException(name+" deve essere positivo"); }
    private static void nonnegative(double n,String name) { if(!Double.isFinite(n)||n<0) throw new IllegalArgumentException(name+" deve essere non negativo"); }
    public static PatternV2Configuration baseline(PatternV1Properties v1, TradingSystemProperties.RiskProperties risk) {
        var g=new Global(risk.getTradingCapital(),risk.getRiskPerTradePercent(),60,null,OrderValueMode.FIXED_INITIAL_CAPITAL_PERCENT,
                risk.getMaxPositionValue(),1_000_000,risk.isUseLeverage(),v1.getMinQuantity(),v1.getMinOrderValue().doubleValue(),
                LocalDate.of(2010,1,1),null,v1.getTimeframe(),v1.getMaxHoldingBars(),v1.getStopBufferTicks(),0.01,
                v1.getSameBarResolution(),EntryMethod.NEXT_BAR_OPEN,StopMethod.PATTERN_EXTREME,true,PatternV1Commission.ibkr(),true,0.03,
                risk.getMaxLongNotional(),risk.getMaxShortNotional(),risk.getTransactionCostsPerShare(),risk.isAllowLong(),risk.isAllowShortSelling(),v1.getSmallQuantityWarningThreshold());
        List<Symbol> symbols=new ArrayList<>();
        new TreeMap<>(v1.getSymbols()).forEach((symbol,c)-> {
            var l=c.longConfig();var s=c.shortConfig();
            symbols.add(new Symbol(true,symbol,new Direction(l!=null&&l.enabled(),l==null?null:l.pattern(),l==null?null:l.targetR()),
                    new Direction(s!=null&&s.enabled(),s==null?null:s.pattern(),s==null?null:s.targetR()),null,null,null,null));
        });
        return new PatternV2Configuration("V2_BASELINE",g,symbols,PatternV2SplitCosts.defaults());
    }
}
