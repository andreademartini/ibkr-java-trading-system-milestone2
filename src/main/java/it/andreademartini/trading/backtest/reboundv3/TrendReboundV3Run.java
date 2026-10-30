package it.andreademartini.trading.backtest.reboundv3;

import java.time.*;
import java.util.*;
import it.andreademartini.trading.backtest.rebound.TrendReboundRun.*;

public record TrendReboundV3Run(String runId,String strategy,String strategyVersion,Instant timestamp,String configurationHash,
        String datasetId,String calendarVersion,TrendReboundV3Configuration configuration,List<Signal> signals,
        List<Trade> trades,List<Equity> equity,List<DataIssue> dataQuality,Map<String,Object> analysis,List<String> warnings) {
    public static final class Signal {
        public String signalId,symbol,status="PENDING",reason,tradeId;
        public LocalDate signalDate,plannedEntryDate,attemptDate,entryDate;
        public List<Double> closes=List.of();
        public Map<String,Object> diagnostics=new LinkedHashMap<>();
        public Double trendSma,atr,entryBasePrice,entryExecutionPrice,initialStop;
    }
}
