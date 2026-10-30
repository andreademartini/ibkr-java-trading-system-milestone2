package it.andreademartini.trading.backtest.reboundv3;

import it.andreademartini.trading.backtest.patternv1.PatternV1Commission;
import it.andreademartini.trading.backtest.patternv2.PatternV2SplitCosts;
import it.andreademartini.trading.backtest.weeklytte.WeeklyTteConfiguration;
import java.time.LocalDate;
import java.util.*;

/** Research-only parameters. Percentages use 0.5 for half a percent. */
public record TrendReboundV3Configuration(String name,List<String> symbols,List<String> excludedSymbols,
        LocalDate startDate,LocalDate endDate,double initialCapital,int trendSmaPeriod,int recoverySmaPeriod,
        int atrPeriod,double stopLossAtrMultiplier,int decliningCloses,int maxHoldingBars,
        double riskPercent,double maxOrderValuePercent,double maxExposurePercent,
        double slippagePercentPerSide,PatternV1Commission commission,PatternV2SplitCosts splitCosts,
 String entryMode,boolean requireFourthBarOpenBelowPreviousHigh,String entryBufferMode,int entryBufferTicks,int entryValidityBars,
 String exitMode,boolean enableProfitSpaceAnalysis,int prePullbackHighLookbackBars,String prePullbackHighMethod,
 double analysisTakeProfitAtrMultiplier,Map<String,Double> minimumTickSizes) {
    public static final String ID="TREND_PULLBACK_REBOUND_V3_RESEARCH",VERSION="TREND_PULLBACK_REBOUND/3";
    public TrendReboundV3Configuration {
        minimumTickSizes=minimumTickSizes==null?Map.of():Map.copyOf(minimumTickSizes);symbols=normalize(symbols);excludedSymbols=normalize(excludedSymbols);
    }
    private static List<String> normalize(List<String> values){return values==null?List.of():values.stream().map(s->s.trim().toUpperCase(Locale.ROOT)).distinct().sorted().toList();}
    public static TrendReboundV3Configuration defaults(){return new TrendReboundV3Configuration("Trend Pullback Rebound",WeeklyTteConfiguration.defaults().symbols(),List.of(),LocalDate.of(2017,1,1),LocalDate.now().minusDays(1),10000,200,5,14,1.5,3,5,0.5,30,100,0.03,PatternV1Commission.ibkr(),WeeklyTteConfiguration.defaultSplitCosts(),"FOURTH_BAR_HIGH_BREAKOUT",true,"TICKS",1,1,"RECOVERY_ONLY",true,10,"HIGHEST_HIGH_BEFORE_PULLBACK",1.5,Map.of());}
    public it.andreademartini.trading.backtest.rebound.TrendReboundConfiguration benchmark(){return new it.andreademartini.trading.backtest.rebound.TrendReboundConfiguration(name,symbols,excludedSymbols,startDate,endDate,initialCapital,trendSmaPeriod,recoverySmaPeriod,atrPeriod,stopLossAtrMultiplier,decliningCloses,maxHoldingBars,riskPercent,maxOrderValuePercent,maxExposurePercent,slippagePercentPerSide,commission,splitCosts);}
 public void validate(){
 if(!"FOURTH_BAR_HIGH_BREAKOUT".equals(entryMode)||!requireFourthBarOpenBelowPreviousHigh||!"TICKS".equals(entryBufferMode)||entryBufferTicks<1||entryValidityBars!=1||!"RECOVERY_ONLY".equals(exitMode)||decliningCloses!=3||prePullbackHighLookbackBars<1||!"HIGHEST_HIGH_BEFORE_PULLBACK".equals(prePullbackHighMethod)||!Double.isFinite(analysisTakeProfitAtrMultiplier)||analysisTakeProfitAtrMultiplier<=0)throw new IllegalArgumentException("Invalid V3 research modes");
 if(minimumTickSizes.values().stream().anyMatch(t->t==null||!Double.isFinite(t)||t<=0))throw new IllegalArgumentException("Invalid instrument tick");
        if(name==null||name.isBlank()||symbols.isEmpty()||symbols.size()>500||symbols.stream().anyMatch(s->!s.matches("[A-Z0-9][A-Z0-9._-]{0,31}"))||!symbols.containsAll(excludedSymbols))throw new IllegalArgumentException("Invalid name, universe or explicit exclusions");
        if(startDate==null||endDate==null||startDate.isBefore(LocalDate.of(2010,1,1))||endDate.getYear()>2028||endDate.isBefore(startDate)||!endDate.isBefore(LocalDate.now()))throw new IllegalArgumentException("Use completed DAILY dates in calendar coverage 2010..2028");
        for(int p:new int[]{trendSmaPeriod,recoverySmaPeriod,atrPeriod,decliningCloses,maxHoldingBars})if(p<1||p>2520)throw new IllegalArgumentException("Periods must be 1..2520");
        for(double n:new double[]{initialCapital,stopLossAtrMultiplier,riskPercent,maxOrderValuePercent,maxExposurePercent})if(!Double.isFinite(n)||n<=0)throw new IllegalArgumentException("Positive finite capital, risk and limits required");
        if(riskPercent>100||maxOrderValuePercent>100||maxExposurePercent>100||!Double.isFinite(slippagePercentPerSide)||slippagePercentPerSide<0||slippagePercentPerSide>=100||commission==null||splitCosts==null)throw new IllegalArgumentException("LONG research without leverage; invalid costs or limits");
    }
}
