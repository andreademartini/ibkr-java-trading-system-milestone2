package it.andreademartini.trading.backtest.weeklytte;

import java.time.*;
import java.util.*;

public record WeeklyTteRun(String runId, String strategy, Instant timestamp, String configurationHash,
        String datasetId, String calendarVersion, WeeklyTteConfiguration configuration,
        List<WeeklyLevel> weeklyLevels, List<Setup> setups, List<Trade> trades, List<Equity> equity,
        Map<String,Object> analysis, List<String> warnings) {
    public static final String STRATEGY="WEEKLY_HIGH_TTE_RESEARCH";
    public record WeeklyLevel(String symbol, LocalDate weekStart, LocalDate weekEnd, LocalDate referenceWeekEnd,
            Double previousWeekHigh, double currentWeekOpen, double currentWeekHigh, double currentWeekLow,
            double currentWeekClose, Boolean previousWeekHighTouched, LocalDate firstTouchDate,
            Integer firstTouchSessionIndex, Double distanceFromLevelAtWeekOpenPct, double weekRangePct,
            String trendContext, String marketRegime, String dataQualityStatus, boolean completedWeek) {}
    /** Mutable only within one disposable simulation; persisted with the entire run. */
    public static class Setup {
        public String direction="LONG",levelId,levelType;
        public Double levelPrice;
        public List<LocalDate> originWeeks=List.of();
        public List<String> touchedLevelIds=List.of();
        public boolean multipleLevelsTouched;
        public String setupId,symbol,setupStatus="PENDING",rejectionReason,tradeId,trendContext,marketRegime="UNAVAILABLE";
        public LocalDate weekStart,referenceWeekEnd,signalDate,pullbackStartDate,pullbackEndDate,recoveryBarDate,entryDate;
        public Double previousWeekHigh,momentumValue,pullbackLow,recoveryBarHigh,entryTrigger,initialStop,
                theoreticalEntryRisk,theoreticalRewardRisk,entryBasePrice,entryExecutionPrice,realizedRewardRisk,
                distanceFromLevelPct,pullbackAmplitudePct,volatilityPct;
        public int entryValidityBars,signalIndex,pullbackPeakIndex;
        public boolean signalEligible,orderTriggered,gapEntry,intrabarAmbiguous;
    }
    public record HistoricalLevel(String levelId,String symbol,String levelType,double levelPrice,
            List<LocalDate> originWeeks,LocalDate availableFrom,double originHigh,double originLow,
            String status,LocalDate firstTouchDate) {}
    public record Fill(LocalDate date, long quantity, double basePrice, double executionPrice,
            double commission, double slippage, String reason, String gapExitType, double gapSizePercent, double gapSizeR) {}
    public record Trade(String tradeId,String setupId,String symbol,String strategyMode,double previousWeekHigh,
            double entryTrigger,double pullbackLow,double initialStop,double initialRewardRisk,LocalDate signalDate,
            LocalDate entryDate,LocalDate exitDate,double entryBasePrice,double entryExecutionPrice,
            Double exitBasePrice,Double exitExecutionPrice,long quantity,long remainingQuantity,double entryCommission,
            double exitCommissions,double slippageCosts,double netPnL,double netR,int holdingDays,String exitReason,
            boolean intrabarAmbiguous,double equityAtEntry,double cashBefore,double cashAfter,double initialRisk,
            double entrySplitFactor,String gapExitType,double gapSizePercent,double gapSizeR,List<Fill> exits, String direction,String levelId,String levelType,Double levelPrice,List<LocalDate> originWeeks) {
        public Trade {direction=direction==null?"LONG":direction;originWeeks=originWeeks==null?List.of():List.copyOf(originWeeks);}
        public Trade(String tradeId,String setupId,String symbol,String strategyMode,double previousWeekHigh,
            double entryTrigger,double pullbackLow,double initialStop,double initialRewardRisk,LocalDate signalDate,
            LocalDate entryDate,LocalDate exitDate,double entryBasePrice,double entryExecutionPrice,
            Double exitBasePrice,Double exitExecutionPrice,long quantity,long remainingQuantity,double entryCommission,
            double exitCommissions,double slippageCosts,double netPnL,double netR,int holdingDays,String exitReason,
            boolean intrabarAmbiguous,double equityAtEntry,double cashBefore,double cashAfter,double initialRisk,
            double entrySplitFactor,String gapExitType,double gapSizePercent,double gapSizeR,List<Fill> exits){this(tradeId,setupId,symbol,strategyMode,previousWeekHigh,entryTrigger,pullbackLow,initialStop,initialRewardRisk,signalDate,entryDate,exitDate,entryBasePrice,entryExecutionPrice,exitBasePrice,exitExecutionPrice,quantity,remainingQuantity,entryCommission,exitCommissions,slippageCosts,netPnL,netR,holdingDays,exitReason,intrabarAmbiguous,equityAtEntry,cashBefore,cashAfter,initialRisk,entrySplitFactor,gapExitType,gapSizePercent,gapSizeR,exits,"LONG",null,null,null,List.of());}
    }
    public record Equity(LocalDate date,double equity,double cash,double longNotional,double exposurePercent, double shortNotional,double grossNotional,double reservedCollateral) {
        public Equity {if(grossNotional==0)grossNotional=longNotional+shortNotional;}
        public Equity(LocalDate date,double equity,double cash,double longNotional,double exposurePercent){this(date,equity,cash,longNotional,exposurePercent,0,longNotional,0);}
    }
}
