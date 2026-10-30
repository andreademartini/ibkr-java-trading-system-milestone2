package it.andreademartini.trading.backtest.reboundv2;

import it.andreademartini.trading.backtest.rebound.TrendReboundConfiguration;
import it.andreademartini.trading.backtest.patternv1.PatternV1Commission;
import it.andreademartini.trading.backtest.patternv2.PatternV2SplitCosts;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

public record TrendReboundV2Configuration(String name,List<String> symbols,List<String> excludedSymbols,
        LocalDate startDate,LocalDate endDate,double initialCapital,int trendSmaPeriod,int recoverySmaPeriod,
        int atrPeriod,double stopLossAtrMultiplier,int decliningCloses,int maxHoldingBars,double riskPercent,
        double maxOrderValuePercent,double maxExposurePercent,boolean useLeverage,double slippagePercentPerSide,
        PatternV1Commission commission,PatternV2SplitCosts splitCosts,ExitMode exitMode,boolean takeProfitEnabled,
        double takeProfitAtrMultiplier,BigDecimal tickSize,Map<String,BigDecimal> tickSizes) {
    public static final String ID="TREND_PULLBACK_REBOUND_V2_RESEARCH",VERSION="TREND_PULLBACK_REBOUND/2";
    public enum ExitMode { ATR_TARGET_ONLY, RECOVERY_ONLY, FIRST_OF_BOTH }
    public TrendReboundV2Configuration {
        symbols=symbols==null?List.of():symbols.stream().map(s->s.trim().toUpperCase(Locale.ROOT)).distinct().sorted().toList();
        excludedSymbols=excludedSymbols==null?List.of():excludedSymbols.stream().map(s->s.trim().toUpperCase(Locale.ROOT)).distinct().sorted().toList();
        tickSizes=tickSizes==null?Map.of():Collections.unmodifiableMap(new TreeMap<>(tickSizes));
    }
    public static TrendReboundV2Configuration defaults(){return fromV1(TrendReboundConfiguration.defaults(),ExitMode.ATR_TARGET_ONLY);}
    public static TrendReboundV2Configuration fromV1(TrendReboundConfiguration c,ExitMode mode){return new TrendReboundV2Configuration("Trend Pullback Rebound V2 - Research",c.symbols(),c.excludedSymbols(),c.startDate(),c.endDate(),c.initialCapital(),c.trendSmaPeriod(),c.recoverySmaPeriod(),c.atrPeriod(),c.stopAtrMultiplier(),c.decliningCloses(),c.maxHoldingBars(),c.riskPercent(),c.maxPositionValuePercent(),c.maxExposurePercent(),false,c.slippagePercentPerSide(),c.commission(),c.splitCosts(),mode,true,1.5,new BigDecimal("0.01"),Map.of());}
    public TrendReboundConfiguration base(){return new TrendReboundConfiguration(name,symbols,excludedSymbols,startDate,endDate,initialCapital,trendSmaPeriod,recoverySmaPeriod,atrPeriod,stopLossAtrMultiplier,decliningCloses,maxHoldingBars,riskPercent,maxOrderValuePercent,maxExposurePercent,slippagePercentPerSide,commission,splitCosts);}
    public boolean targetEnabled(){return exitMode!=ExitMode.RECOVERY_ONLY&&takeProfitEnabled;}
    public boolean recoveryEnabled(){return exitMode!=ExitMode.ATR_TARGET_ONLY;}
    public void validate(){base().validate();if(useLeverage)throw new IllegalArgumentException("V2 preserves V1 sizing: leverage must remain disabled");if(exitMode==null||!Double.isFinite(takeProfitAtrMultiplier)||takeProfitAtrMultiplier<=0||tickSize==null||tickSize.signum()<=0||tickSizes.entrySet().stream().anyMatch(e->!symbols.contains(e.getKey())||e.getValue()==null||e.getValue().signum()<=0))throw new IllegalArgumentException("Invalid exit mode, ATR target multiplier or instrument tick");}
    public BigDecimal tick(String symbol,double entry){return tickSizes.getOrDefault(symbol,entry<1?tickSize.min(new BigDecimal("0.0001")):tickSize);}
    public double target(String symbol,double entry,double atr){return target(symbol,entry,atr,BigDecimal.ONE);}
    public double target(String symbol,double entry,double atr,BigDecimal splitFactor){double raw=entry+takeProfitAtrMultiplier*atr;if(!Double.isFinite(raw))throw new IllegalArgumentException("Non-finite ATR target");var tick=tick(symbol,entry*splitFactor.doubleValue());return BigDecimal.valueOf(raw).multiply(splitFactor).divide(tick,0,RoundingMode.CEILING).multiply(tick).divide(splitFactor,MathContext.DECIMAL128).doubleValue();}
}
