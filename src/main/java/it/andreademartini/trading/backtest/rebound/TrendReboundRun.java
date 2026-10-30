package it.andreademartini.trading.backtest.rebound;

import java.time.*;
import java.util.*;

public record TrendReboundRun(String runId,String strategy,String strategyVersion,Instant timestamp,String configurationHash,
        String datasetId,String calendarVersion,TrendReboundConfiguration configuration,List<Signal> signals,
        List<Trade> trades,List<Equity> equity,List<DataIssue> dataQuality,Map<String,Object> analysis,List<String> warnings) {
    public static final class Signal {
        public String signalId,symbol,status="PENDING",reason,tradeId;
        public LocalDate signalDate,plannedEntryDate,attemptDate,entryDate;
        public List<Double> closes=List.of();
        public Double trendSma,atr,entryBasePrice,entryExecutionPrice,initialStop;
    }
    public record Trade(String tradeId,String signalId,String symbol,String direction,LocalDate signalDate,
            List<Double> signalCloses,double trendSmaAtSignal,double atrAtSignal,LocalDate entryDate,LocalDate exitDate,
            LocalDate recoverySignalDate,double entryBasePrice,double entryExecutionPrice,Double exitBasePrice,
            Double exitExecutionPrice,double initialStop,long quantity,double initialRisk,double sizingRiskWithCosts,
            double entryCommission,double exitCommission,double slippageCosts,Double netPnL,Double netR,
            int holdingBars,String exitReason,double splitFactor,Double lastMark,Double unrealizedNetPnL,String dataStatus){}
    public record Equity(LocalDate date,double equity,double cash,double notional,double exposurePercent,
            double drawdownPercent,int openPositions,boolean dataIncomplete){}
    public record DataIssue(String symbol,String severity,String code,String detail){}
}
