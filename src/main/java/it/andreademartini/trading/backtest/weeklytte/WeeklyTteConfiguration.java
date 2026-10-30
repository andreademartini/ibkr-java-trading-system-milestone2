package it.andreademartini.trading.backtest.weeklytte;

import it.andreademartini.trading.backtest.patternv1.PatternV1Commission;
import it.andreademartini.trading.backtest.patternv2.PatternV2SplitCosts;
import java.time.LocalDate;
import java.util.*;

/** Independent research parameters. Percentages use 2.0 for two percent. */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record WeeklyTteConfiguration(String name, Mode mode, List<String> symbols, LocalDate startDate,
        LocalDate endDate, double initialCapital, double riskPercent, double maxOrderValuePercent,
        String maxOrderValueMode, boolean useLeverage, double maxPositionValuePercent,
        double maxLongNotionalPercent, double maxShortNotionalPercent, PatternV1Commission commission,
        double slippagePercentPerSide, PatternV2SplitCosts splitCosts, String splitCommissionMode,
        boolean momentumFilter, int momentumLookback, int pullbackLookbackBars, int minPullbackBars,
        int maxPullbackBars, BufferMode entryBufferMode, double entryBuffer, BufferMode stopBufferMode,
        double stopBuffer, double tickSize, int entryValidityBars, double minRewardRiskRatio,
        int maxHoldingDays, String gapHandling, Ambiguity ambiguousBarPolicy,
        double breakoutTargetR, double partialClosePercent, double residualTargetR,
        Boolean allowLong, Boolean allowShort, String rulesVersion) {
    public static final String HISTORICAL = "WEEKLY_UNTOUCHED/2";
    public boolean historical(){return HISTORICAL.equals(rulesVersion);}
    public boolean longEnabled(){return allowLong==null||allowLong;}
    public boolean shortEnabled(){return Boolean.TRUE.equals(allowShort);}
    public enum Mode { WEEKLY_HIGH_BREAKOUT, WEEKLY_HIGH_TTE, WEEKLY_HIGH_TTE_PARTIAL }
    public enum BufferMode { TICKS, PERCENT }
    public enum Ambiguity { CONSERVATIVE, SENSITIVITY_FAVORABLE }
    public WeeklyTteConfiguration {
        symbols = symbols == null ? List.of() : symbols.stream().map(s->s.trim().toUpperCase(Locale.ROOT)).distinct().sorted().toList();
    }
    public static WeeklyTteConfiguration defaults() {
        return new WeeklyTteConfiguration("Weekly high research",Mode.WEEKLY_HIGH_TTE,List.of("AAPL","AMD","AMZN","AVGO","BA","BAC","CAT","CVX","DIA","DIS","GOOGL","GS","IWM","JPM","KO","META","MSFT","NFLX","NKE","NVDA","QQQ","SMH","SPY","TSLA","XLE","XLF","XLI","XLK","XLY","XOM"),
                LocalDate.of(2017,1,1),LocalDate.now().minusDays(1),10000,2,30,"CURRENT_EQUITY_PERCENT",false,
                100,100,100,PatternV1Commission.ibkr(),0.03,defaultSplitCosts(),"SPLIT_AWARE_V1",
                true,5,5,1,3,BufferMode.TICKS,1,BufferMode.TICKS,1,0.01,3,1.5,10,"OPEN_FIRST",Ambiguity.CONSERVATIVE,2,50,3,true,true,HISTORICAL);
    }
    public static PatternV2SplitCosts defaultSplitCosts(){
        var rules=new ArrayList<>(PatternV2SplitCosts.defaults().rules());
        // Official issuer: https://investor.apple.com/dividend-history/default.aspx
        // Trading split-adjusted from 2014-06-09 and 2020-08-31, respectively.
        rules.add(new PatternV2SplitCosts.Rule("AAPL",PatternV2SplitCosts.Basis.AUTO_PROVIDER,LocalDate.of(2010,1,1),LocalDate.of(2020,8,31),
                List.of(new PatternV2SplitCosts.Split(LocalDate.of(2014,6,9),java.math.BigDecimal.valueOf(7)),new PatternV2SplitCosts.Split(LocalDate.of(2020,8,31),java.math.BigDecimal.valueOf(4)))));
        return new PatternV2SplitCosts(rules);
    }
    public void validate() {
        if(rulesVersion!=null&&!historical())throw new IllegalArgumentException("Unknown weekly research rules version");
        if(!longEnabled()&&!shortEnabled())throw new IllegalArgumentException("Enable LONG or SHORT");
        if(!historical()&&(!longEnabled()||shortEnabled()))throw new IllegalArgumentException("Legacy replay supports LONG only; select WEEKLY_UNTOUCHED/2");
        if(name==null||name.isBlank()||mode==null||startDate==null||endDate==null||endDate.isBefore(startDate)
                ||startDate.getYear()<2010||endDate.getYear()>2028||!endDate.isBefore(LocalDate.now())
                ||symbols.isEmpty()||symbols.stream().anyMatch(s->!s.matches("[A-Z0-9][A-Z0-9._-]{0,31}")))
            throw new IllegalArgumentException("Invalid name, symbols or completed DAILY date range (2010..2028)");
        for(double n:new double[]{initialCapital,riskPercent,maxOrderValuePercent,maxPositionValuePercent,
                maxLongNotionalPercent,maxShortNotionalPercent,tickSize,breakoutTargetR,residualTargetR})
            if(!Double.isFinite(n)||n<=0)throw new IllegalArgumentException("Positive finite capital/risk/limits required");
        for(double n:new double[]{entryBuffer,stopBuffer,slippagePercentPerSide,minRewardRiskRatio})
            if(!Double.isFinite(n)||n<0)throw new IllegalArgumentException("Invalid buffer, slippage or R/R");
        if(riskPercent>100||slippagePercentPerSide>=100||momentumLookback<1||momentumLookback>252
                ||minPullbackBars<1||maxPullbackBars<minPullbackBars||maxPullbackBars>pullbackLookbackBars
                ||pullbackLookbackBars>252||entryValidityBars<1||entryValidityBars>252||maxHoldingDays<1||maxHoldingDays>2520
                ||!Double.isFinite(partialClosePercent)||partialClosePercent<=0||partialClosePercent>=100
                ||entryBufferMode==null||stopBufferMode==null||commission==null||splitCosts==null||ambiguousBarPolicy==null
                ||!"OPEN_FIRST".equals(gapHandling)||!"SPLIT_AWARE_V1".equals(splitCommissionMode)
                ||!"CURRENT_EQUITY_PERCENT".equals(maxOrderValueMode))throw new IllegalArgumentException("Unsupported research configuration");
        if(!useLeverage&&(maxOrderValuePercent>100||maxPositionValuePercent>100||maxLongNotionalPercent>100||(historical()&&maxShortNotionalPercent>100)))
            throw new IllegalArgumentException("No leverage limits must be <=100%");
    }
    public double buffer(double price, BufferMode mode, double value) {return mode==BufferMode.TICKS?tickSize*value:price*value/100;}
}
