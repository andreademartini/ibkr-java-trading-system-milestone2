package it.andreademartini.trading.backtest.patternv3;

import it.andreademartini.trading.backtest.patternv1.PatternV1Commission;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.research.pattern.*;
import it.andreademartini.trading.strategy.patternv1.PatternV1Properties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Immutable run snapshot: never stores a V1 bean, mutable collection or risk configuration. */
public record PatternV3Configuration(String name, Global global, List<Symbol> symbols, PatternV3SplitCosts splitCosts,
        boolean patternRangeFilterEnabled, BigDecimal minimumPatternRangePct,
        boolean closeStrengthFilterEnabled, BigDecimal maxCloseStrength, boolean capitalSkipShadowAnalysisEnabled,
        boolean bollingerAtrFilterEnabled,Integer bollingerPeriod,BigDecimal bollingerStdDev,Integer atrPeriod,BigDecimal maxBollingerDistanceAtr,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT) Boolean marketRegimeAnalysisEnabled,
        @com.fasterxml.jackson.annotation.JsonInclude(value=com.fasterxml.jackson.annotation.JsonInclude.Include.CUSTOM,valueFilter=MarketRegimeFilterJson.DefaultValue.class)
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=MarketRegimeFilterJson.Enabled.class)
        @tools.jackson.databind.annotation.JsonDeserialize(using=MarketRegimeFilterHttpJson.Enabled.class) Boolean marketRegimeFilterEnabled,
        @com.fasterxml.jackson.annotation.JsonInclude(value=com.fasterxml.jackson.annotation.JsonInclude.Include.CUSTOM,valueFilter=MarketRegimeFilterJson.DefaultValue.class)
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=MarketRegimeFilterJson.Period.class)
        @tools.jackson.databind.annotation.JsonDeserialize(using=MarketRegimeFilterHttpJson.Period.class) Integer marketRegimeFilterAdxPeriod,
        @com.fasterxml.jackson.annotation.JsonInclude(value=com.fasterxml.jackson.annotation.JsonInclude.Include.CUSTOM,valueFilter=MarketRegimeFilterJson.DefaultValue.class)
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=MarketRegimeFilterJson.Threshold.class)
        @tools.jackson.databind.annotation.JsonDeserialize(using=MarketRegimeFilterHttpJson.Threshold.class) Double marketRegimeFilterAdxThreshold,
        @com.fasterxml.jackson.annotation.JsonInclude(value=com.fasterxml.jackson.annotation.JsonInclude.Include.CUSTOM,valueFilter=MarketRegimeFilterJson.DefaultValue.class)
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=MarketRegimeFilterJson.Mode.class)
        @tools.jackson.databind.annotation.JsonDeserialize(using=MarketRegimeFilterHttpJson.Mode.class) MarketRegimeFilterMode marketRegimeFilterMode,
        @com.fasterxml.jackson.annotation.JsonInclude(value=com.fasterxml.jackson.annotation.JsonInclude.Include.CUSTOM,valueFilter=SignalTimeframe.DefaultDaily.class) SignalTimeframe signalTimeframe,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) BigDecimal closeStrengthMin,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) BigDecimal patternRangeMinPct,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) BigDecimal patternRangeMaxPct,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT) Boolean relativeVolumeFilterEnabled,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=ResearchFilterJson.Period.class)
        @tools.jackson.databind.annotation.JsonDeserialize(using=ResearchFilterJson.HttpPeriod.class) Integer relativeVolumePeriod,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) BigDecimal relativeVolumeMin,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) BigDecimal relativeVolumeMax,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) RelativeVolume.Basis relativeVolumeBasis) {
    public PatternV3Configuration(String name,Global global,List<Symbol> symbols,PatternV3SplitCosts splitCosts,boolean range,BigDecimal minimum,boolean close,BigDecimal maximum,boolean shadow,boolean bollinger,Integer period,BigDecimal stddev,Integer atr,BigDecimal distance,Boolean analysis,Boolean regime,Integer adx,Double threshold,MarketRegimeFilterMode mode) {
        this(name,global,symbols,splitCosts,range,minimum,close,maximum,shadow,bollinger,period,stddev,atr,distance,analysis,regime,adx,threshold,mode,SignalTimeframe.DAILY);
    }
    public PatternV3Configuration withSignalTimeframe(SignalTimeframe timeframe) {
        return new PatternV3Configuration(name,global,symbols,splitCosts,patternRangeFilterEnabled,minimumPatternRangePct,closeStrengthFilterEnabled,maxCloseStrength,capitalSkipShadowAnalysisEnabled,bollingerAtrFilterEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxBollingerDistanceAtr,marketRegimeAnalysisEnabled,marketRegimeFilterEnabled,marketRegimeFilterAdxPeriod,marketRegimeFilterAdxThreshold,marketRegimeFilterMode,timeframe,closeStrengthMin,patternRangeMinPct,patternRangeMaxPct,relativeVolumeFilterEnabled,relativeVolumePeriod,relativeVolumeMin,relativeVolumeMax,relativeVolumeBasis);
    }
    public PatternV3Configuration(String name,Global global,List<Symbol> symbols,PatternV3SplitCosts splitCosts,boolean range,BigDecimal minimum,boolean close,BigDecimal maximum,boolean shadow,boolean bollinger,Integer period,BigDecimal stddev,Integer atr,BigDecimal distance,Boolean analysis,Boolean regime,Integer adx,Double threshold,MarketRegimeFilterMode mode,SignalTimeframe timeframe){
        this(name,global,symbols,splitCosts,range,minimum,close,maximum,shadow,bollinger,period,stddev,atr,distance,analysis,regime,adx,threshold,mode,timeframe,null,null,null,false,null,null,null,null);
    }
    public BigDecimal rangeMinimum(){return patternRangeMinPct==null?minimumPatternRangePct:patternRangeMinPct;}
    public int rvolPeriod(){return relativeVolumePeriod==null?20:relativeVolumePeriod;}
    public BigDecimal rvolMinimum(){return relativeVolumeMin==null?new BigDecimal("1.20"):relativeVolumeMin;}
    public RelativeVolume.Basis rvolBasis(){return relativeVolumeBasis==null?RelativeVolume.Basis.UNVERIFIED:relativeVolumeBasis;}
    public boolean rvolEnabled(){return Boolean.TRUE.equals(relativeVolumeFilterEnabled);}
    public PatternV3Configuration withResearchFilters(boolean closeEnabled,BigDecimal closeMin,boolean rangeEnabled,BigDecimal rangeMin,BigDecimal rangeMax,boolean volumeEnabled,int volumePeriod,BigDecimal volumeMin,BigDecimal volumeMax){
        return new PatternV3Configuration(name,global,symbols,splitCosts,rangeEnabled,rangeMin,closeEnabled,maxCloseStrength,capitalSkipShadowAnalysisEnabled,bollingerAtrFilterEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxBollingerDistanceAtr,marketRegimeAnalysisEnabled,marketRegimeFilterEnabled,marketRegimeFilterAdxPeriod,marketRegimeFilterAdxThreshold,marketRegimeFilterMode,signalTimeframe,closeMin,rangeMin,rangeMax,volumeEnabled,volumePeriod,volumeMin,volumeMax,relativeVolumeBasis);
    }
    public PatternV3Configuration withRelativeVolumeBasis(RelativeVolume.Basis basis){
        return new PatternV3Configuration(name,global,symbols,splitCosts,patternRangeFilterEnabled,minimumPatternRangePct,closeStrengthFilterEnabled,maxCloseStrength,capitalSkipShadowAnalysisEnabled,bollingerAtrFilterEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxBollingerDistanceAtr,marketRegimeAnalysisEnabled,marketRegimeFilterEnabled,marketRegimeFilterAdxPeriod,marketRegimeFilterAdxThreshold,marketRegimeFilterMode,signalTimeframe,closeStrengthMin,patternRangeMinPct,patternRangeMaxPct,relativeVolumeFilterEnabled,relativeVolumePeriod,relativeVolumeMin,relativeVolumeMax,basis);
    }
    public enum MarketRegimeFilterMode { EXCLUDE_WITH_TREND }
    public PatternV3Configuration(String name,Global global,List<Symbol> symbols,PatternV3SplitCosts splitCosts,boolean rangeEnabled,BigDecimal minimum,boolean closeEnabled,BigDecimal maximum,boolean shadowEnabled,boolean bollingerEnabled,Integer bollingerPeriod,BigDecimal bollingerStdDev,Integer atrPeriod,BigDecimal maxDistance,Boolean analysisEnabled) {
        this(name,global,symbols,splitCosts,rangeEnabled,minimum,closeEnabled,maximum,shadowEnabled,bollingerEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxDistance,analysisEnabled,false,14,25.0,MarketRegimeFilterMode.EXCLUDE_WITH_TREND);
    }
    public PatternV3Configuration(String name, Global global,List<Symbol> symbols,PatternV3SplitCosts splitCosts,boolean rangeEnabled,BigDecimal minimum,boolean closeEnabled,BigDecimal maximum,boolean shadowEnabled,boolean bollingerEnabled,Integer bollingerPeriod,BigDecimal bollingerStdDev,Integer atrPeriod,BigDecimal maxDistance) {
        this(name,global,symbols,splitCosts,rangeEnabled,minimum,closeEnabled,maximum,shadowEnabled,bollingerEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxDistance,false);
    }
    public PatternV3Configuration(String name,Global global,List<Symbol> symbols,PatternV3SplitCosts splitCosts,boolean rangeEnabled,BigDecimal minimum,boolean closeEnabled,BigDecimal maximum,boolean shadowEnabled) {
        this(name,global,symbols,splitCosts,rangeEnabled,minimum,closeEnabled,maximum,shadowEnabled,false,null,null,null,null);
    }
    public PatternV3Configuration(String name,Global global,List<Symbol> symbols,PatternV3SplitCosts splitCosts,boolean rangeEnabled,BigDecimal minimum,boolean closeEnabled,BigDecimal maximum){this(name,global,symbols,splitCosts,rangeEnabled,minimum,closeEnabled,maximum,false);}
    public PatternV3Configuration(String name, Global global,List<Symbol> symbols,PatternV3SplitCosts splitCosts,
            boolean patternRangeFilterEnabled,BigDecimal minimumPatternRangePct) {
        this(name,global,symbols,splitCosts,patternRangeFilterEnabled,minimumPatternRangePct,false,new BigDecimal("0.80"));
    }
    public PatternV3Configuration(String name, Global global, List<Symbol> symbols, PatternV3SplitCosts splitCosts) {
        this(name,global,symbols,splitCosts,false,new BigDecimal("2.50"));
    }
    /** Missing splitCosts identifies legacy snapshots; do not rewrite historical results on deserialization. */
    public PatternV3Configuration(String name, Global global, List<Symbol> symbols) { this(name,global,symbols,null); }
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
        public Global withDates(LocalDate from,LocalDate to) {
            return new Global(initialCapital,riskPercent,maxOrderValuePercent,maxOrderValue,maxOrderValueMode,maxPositionValue,maxQuantity,useLeverage,minQuantity,minOrderValue,from,to,timeframe,maxHoldingBars,stopBufferTicks,tickSize,sameBarPolicy,entryMode,stopMode,commissionEnabled,commission,slippageEnabled,slippagePercentPerSide,maxLongNotional,maxShortNotional,transactionCostsPerShare,allowLong,allowShort,smallQuantityWarningThreshold,maxPositionValueMode,maxPositionValuePercent,maxLongNotionalMode,maxLongNotionalPercent,maxShortNotionalMode,maxShortNotionalPercent);
        }
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
    public PatternV3Configuration {
        relativeVolumeFilterEnabled=Boolean.TRUE.equals(relativeVolumeFilterEnabled);
        signalTimeframe=signalTimeframe==null?SignalTimeframe.DAILY:signalTimeframe;
        marketRegimeAnalysisEnabled=Boolean.TRUE.equals(marketRegimeAnalysisEnabled);
        if(marketRegimeFilterEnabled==null||marketRegimeFilterAdxPeriod==null||marketRegimeFilterAdxThreshold==null||marketRegimeFilterMode==null)throw new IllegalArgumentException("Market Regime Filter parameters cannot be null");
        marketRegimeFilterAdxPeriod=marketRegimeFilterAdxPeriod==null?14:marketRegimeFilterAdxPeriod;
        marketRegimeFilterAdxThreshold=marketRegimeFilterAdxThreshold==null?25.0:marketRegimeFilterAdxThreshold;
        marketRegimeFilterMode=marketRegimeFilterMode==null?MarketRegimeFilterMode.EXCLUDE_WITH_TREND:marketRegimeFilterMode;
        if(marketRegimeFilterAdxPeriod<2||!Double.isFinite(marketRegimeFilterAdxThreshold)||marketRegimeFilterAdxThreshold<0||marketRegimeFilterAdxThreshold>100)throw new IllegalArgumentException("Invalid Market Regime Filter period (minimum 2) or threshold (0..100)");
        name = name == null || name.isBlank() ? "V2_BASELINE" : name.trim();
        if (name.length() > 100) throw new IllegalArgumentException("Nome configurazione troppo lungo");
        symbols = symbols == null ? List.of() : List.copyOf(symbols);
        minimumPatternRangePct = minimumPatternRangePct == null ? new BigDecimal("2.50") : minimumPatternRangePct;
        bollingerPeriod=bollingerPeriod==null?20:bollingerPeriod;atrPeriod=atrPeriod==null?14:atrPeriod;
        bollingerStdDev=bollingerStdDev==null?new BigDecimal("2.0"):bollingerStdDev;maxBollingerDistanceAtr=maxBollingerDistanceAtr==null?new BigDecimal("0.50"):maxBollingerDistanceAtr;
        maxCloseStrength = maxCloseStrength == null ? new BigDecimal("0.80") : maxCloseStrength;
    }
    public PatternV3Configuration withGlobal(Global value) { return new PatternV3Configuration(name,value,symbols,splitCosts,patternRangeFilterEnabled,minimumPatternRangePct,closeStrengthFilterEnabled,maxCloseStrength,capitalSkipShadowAnalysisEnabled,bollingerAtrFilterEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxBollingerDistanceAtr,marketRegimeAnalysisEnabled,marketRegimeFilterEnabled,marketRegimeFilterAdxPeriod,marketRegimeFilterAdxThreshold,marketRegimeFilterMode,signalTimeframe,closeStrengthMin,patternRangeMinPct,patternRangeMaxPct,relativeVolumeFilterEnabled,relativeVolumePeriod,relativeVolumeMin,relativeVolumeMax,relativeVolumeBasis); }
    public PatternV3Configuration withSymbols(List<Symbol> value) { return new PatternV3Configuration(name,global,value,splitCosts,patternRangeFilterEnabled,minimumPatternRangePct,closeStrengthFilterEnabled,maxCloseStrength,capitalSkipShadowAnalysisEnabled,bollingerAtrFilterEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxBollingerDistanceAtr,marketRegimeAnalysisEnabled,marketRegimeFilterEnabled,marketRegimeFilterAdxPeriod,marketRegimeFilterAdxThreshold,marketRegimeFilterMode,signalTimeframe,closeStrengthMin,patternRangeMinPct,patternRangeMaxPct,relativeVolumeFilterEnabled,relativeVolumePeriod,relativeVolumeMin,relativeVolumeMax,relativeVolumeBasis); }
    public PatternV3Configuration withSplitCosts(PatternV3SplitCosts value) { return new PatternV3Configuration(name,global,symbols,value,patternRangeFilterEnabled,minimumPatternRangePct,closeStrengthFilterEnabled,maxCloseStrength,capitalSkipShadowAnalysisEnabled,bollingerAtrFilterEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxBollingerDistanceAtr,marketRegimeAnalysisEnabled,marketRegimeFilterEnabled,marketRegimeFilterAdxPeriod,marketRegimeFilterAdxThreshold,marketRegimeFilterMode,signalTimeframe,closeStrengthMin,patternRangeMinPct,patternRangeMaxPct,relativeVolumeFilterEnabled,relativeVolumePeriod,relativeVolumeMin,relativeVolumeMax,relativeVolumeBasis); }
    public PatternV3Configuration forNewRun() { return splitCosts==null?withSplitCosts(PatternV3SplitCosts.defaults()):this; }
    public PatternV3Configuration withRangeFilter(boolean enabled, BigDecimal minimum) {
        return new PatternV3Configuration(name,global,symbols,splitCosts,enabled,minimum,closeStrengthFilterEnabled,maxCloseStrength,capitalSkipShadowAnalysisEnabled,bollingerAtrFilterEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxBollingerDistanceAtr,marketRegimeAnalysisEnabled,marketRegimeFilterEnabled,marketRegimeFilterAdxPeriod,marketRegimeFilterAdxThreshold,marketRegimeFilterMode,signalTimeframe,closeStrengthMin,patternRangeMinPct==null?null:minimum,patternRangeMaxPct,relativeVolumeFilterEnabled,relativeVolumePeriod,relativeVolumeMin,relativeVolumeMax,relativeVolumeBasis);
    }
    public PatternV3Configuration withName(String value) {
        return new PatternV3Configuration(value,global,symbols,splitCosts,patternRangeFilterEnabled,minimumPatternRangePct,closeStrengthFilterEnabled,maxCloseStrength,capitalSkipShadowAnalysisEnabled,bollingerAtrFilterEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxBollingerDistanceAtr,marketRegimeAnalysisEnabled,marketRegimeFilterEnabled,marketRegimeFilterAdxPeriod,marketRegimeFilterAdxThreshold,marketRegimeFilterMode,signalTimeframe,closeStrengthMin,patternRangeMinPct,patternRangeMaxPct,relativeVolumeFilterEnabled,relativeVolumePeriod,relativeVolumeMin,relativeVolumeMax,relativeVolumeBasis);
    }
    public PatternV3Configuration withCloseStrengthFilter(boolean enabled,BigDecimal maximum) {
        return new PatternV3Configuration(name,global,symbols,splitCosts,patternRangeFilterEnabled,minimumPatternRangePct,enabled,maximum,capitalSkipShadowAnalysisEnabled,bollingerAtrFilterEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxBollingerDistanceAtr,marketRegimeAnalysisEnabled,marketRegimeFilterEnabled,marketRegimeFilterAdxPeriod,marketRegimeFilterAdxThreshold,marketRegimeFilterMode,signalTimeframe,closeStrengthMin,patternRangeMinPct,patternRangeMaxPct,relativeVolumeFilterEnabled,relativeVolumePeriod,relativeVolumeMin,relativeVolumeMax,relativeVolumeBasis);
    }
    public PatternV3Configuration withCapitalSkipShadowAnalysis(boolean enabled){return new PatternV3Configuration(name,global,symbols,splitCosts,patternRangeFilterEnabled,minimumPatternRangePct,closeStrengthFilterEnabled,maxCloseStrength,enabled,bollingerAtrFilterEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxBollingerDistanceAtr,marketRegimeAnalysisEnabled,marketRegimeFilterEnabled,marketRegimeFilterAdxPeriod,marketRegimeFilterAdxThreshold,marketRegimeFilterMode,signalTimeframe,closeStrengthMin,patternRangeMinPct,patternRangeMaxPct,relativeVolumeFilterEnabled,relativeVolumePeriod,relativeVolumeMin,relativeVolumeMax,relativeVolumeBasis);}
    public PatternV3Configuration withBollingerAtr(boolean enabled,int period,BigDecimal stdDev,int atr,BigDecimal distance) {
        return new PatternV3Configuration(name,global,symbols,splitCosts,patternRangeFilterEnabled,minimumPatternRangePct,closeStrengthFilterEnabled,maxCloseStrength,capitalSkipShadowAnalysisEnabled,enabled,period,stdDev,atr,distance,marketRegimeAnalysisEnabled,marketRegimeFilterEnabled,marketRegimeFilterAdxPeriod,marketRegimeFilterAdxThreshold,marketRegimeFilterMode,signalTimeframe,closeStrengthMin,patternRangeMinPct,patternRangeMaxPct,relativeVolumeFilterEnabled,relativeVolumePeriod,relativeVolumeMin,relativeVolumeMax,relativeVolumeBasis);
    }
    public PatternV3Configuration withMarketRegimeAnalysis(boolean enabled) {return new PatternV3Configuration(name,global,symbols,splitCosts,patternRangeFilterEnabled,minimumPatternRangePct,closeStrengthFilterEnabled,maxCloseStrength,capitalSkipShadowAnalysisEnabled,bollingerAtrFilterEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxBollingerDistanceAtr,enabled,marketRegimeFilterEnabled,marketRegimeFilterAdxPeriod,marketRegimeFilterAdxThreshold,marketRegimeFilterMode,signalTimeframe,closeStrengthMin,patternRangeMinPct,patternRangeMaxPct,relativeVolumeFilterEnabled,relativeVolumePeriod,relativeVolumeMin,relativeVolumeMax,relativeVolumeBasis);}
    public PatternV3Configuration withMarketRegimeFilter(boolean enabled,int period,double threshold) {
        return new PatternV3Configuration(name,global,symbols,splitCosts,patternRangeFilterEnabled,minimumPatternRangePct,closeStrengthFilterEnabled,maxCloseStrength,capitalSkipShadowAnalysisEnabled,bollingerAtrFilterEnabled,bollingerPeriod,bollingerStdDev,atrPeriod,maxBollingerDistanceAtr,marketRegimeAnalysisEnabled,enabled,period,threshold,MarketRegimeFilterMode.EXCLUDE_WITH_TREND,signalTimeframe,closeStrengthMin,patternRangeMinPct,patternRangeMaxPct,relativeVolumeFilterEnabled,relativeVolumePeriod,relativeVolumeMin,relativeVolumeMax,relativeVolumeBasis);
    }
    public void validate(List<PatternDetector> detectors) {
        if(closeStrengthMin!=null&&(closeStrengthMin.signum()<0||closeStrengthMin.compareTo(BigDecimal.ONE)>0))throw new IllegalArgumentException("Minimum CloseStrength must be in 0..1");
        if(rangeMinimum().signum()<0||patternRangeMaxPct!=null&&(patternRangeMaxPct.signum()<0||patternRangeMaxPct.compareTo(rangeMinimum())<0))throw new IllegalArgumentException("Invalid Pattern Range min/max percentages");
        if(rvolPeriod()<1||rvolMinimum().signum()<0||relativeVolumeMax!=null&&(relativeVolumeMax.signum()<0||relativeVolumeMax.compareTo(rvolMinimum())<0))throw new IllegalArgumentException("Invalid RVOL period or min/max");
        if((marketRegimeAnalysisEnabled||marketRegimeFilterEnabled)&&global!=null&&global.timeframe()!=BarInterval.DAILY)throw new IllegalArgumentException("Market Regime Analysis requires DAILY bars");
        if(bollingerPeriod<2||atrPeriod<1||bollingerStdDev.signum()<=0||maxBollingerDistanceAtr.signum()<0)throw new IllegalArgumentException("Invalid Bollinger/ATR periods, multiplier or distance");
        if(bollingerAtrFilterEnabled&&global!=null&&global.timeframe()!=BarInterval.DAILY)throw new IllegalArgumentException("Bollinger + ATR filter requires DAILY bars");
        if(maxCloseStrength.signum()<0||maxCloseStrength.compareTo(BigDecimal.ONE)>0)
            throw new IllegalArgumentException("Maximum Close Strength must be between 0 and 1");
        if (minimumPatternRangePct.signum()<0) throw new IllegalArgumentException("Minimum Pattern Range % deve essere non negativo");
        if (global == null) throw new IllegalArgumentException("Parametri globali mancanti");
        var g=global;
        if(g.timeframe()!=BarInterval.DAILY)throw new IllegalArgumentException("V3 execution and position management require DAILY bars; use signalTimeframe for signals");
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
    public static PatternV3Configuration baseline(PatternV1Properties v1, TradingSystemProperties.RiskProperties risk) {
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
        return new PatternV3Configuration("V2_BASELINE",g,symbols,PatternV3SplitCosts.defaults());
    }
}
