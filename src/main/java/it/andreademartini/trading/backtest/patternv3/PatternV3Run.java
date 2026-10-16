package it.andreademartini.trading.backtest.patternv3;

import it.andreademartini.trading.backtest.patternv1.PatternV1Result;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import it.andreademartini.trading.backtest.patternv2.ExitFillPolicy;
import it.andreademartini.trading.backtest.patternv2.GapExecutionAudit;
import it.andreademartini.trading.backtest.execution.ExecutionModel;

/** V3-owned persisted artifact. The portfolio contract is immutable shared data, never V1 state. */
public record PatternV3Run(String runId, String strategy, Instant timestamp, PatternV3Configuration configuration,
        PatternV1Result portfolio, List<String> equityTimestamps, List<Execution> executions,
        PatternV3Analysis.Analysis analysis, Costs costs, PatternV1Result benchmark,
        List<PatternRangeAnalysis.SignalAudit> rangeSignals, PatternRangeAnalysis.Analysis patternRangeAnalysis,
        String datasetId, String pairedRunId, List<CloseStrengthAnalysis.SignalAudit> closeStrengthSignals,
        CloseStrengthAnalysis.Analysis closeStrengthAnalysis,List<CapitalSkipAudit> capitalSkipAudits,CapitalSkipShadowAnalysis.Analysis capitalSkipShadowAnalysis,List<BollingerAtrAnalysis.SignalAudit> bollingerAtrSignals,BollingerAtrAnalysis.Analysis bollingerAtrAnalysis,MarketRegimeAnalysis.Analysis marketRegimeAnalysis,MarketRegimeFilter.Analysis marketRegimeFilterAnalysis,String strategyVersion,String configurationHash,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String executionModelVersion,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) GapExecutionAudit executionAudit,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) List<SignalFilterAudit.Row> signalFilterAudit) {
    public PatternV3Run(String runId,String strategy,Instant timestamp,PatternV3Configuration configuration,PatternV1Result portfolio,List<String> equityTimestamps,List<Execution> executions,PatternV3Analysis.Analysis analysis,Costs costs,PatternV1Result benchmark,List<PatternRangeAnalysis.SignalAudit> rangeSignals,PatternRangeAnalysis.Analysis patternRangeAnalysis,String datasetId,String pairedRunId,List<CloseStrengthAnalysis.SignalAudit> closeStrengthSignals,CloseStrengthAnalysis.Analysis closeStrengthAnalysis,List<CapitalSkipAudit> capitalSkipAudits,CapitalSkipShadowAnalysis.Analysis capitalSkipShadowAnalysis,List<BollingerAtrAnalysis.SignalAudit> bollingerAtrSignals,BollingerAtrAnalysis.Analysis bollingerAtrAnalysis,MarketRegimeAnalysis.Analysis marketRegimeAnalysis,MarketRegimeFilter.Analysis marketRegimeFilterAnalysis,String strategyVersion,String configurationHash,String executionModelVersion,GapExecutionAudit executionAudit){
        this(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,marketRegimeAnalysis,marketRegimeFilterAnalysis,strategyVersion,configurationHash,executionModelVersion,executionAudit,null);
    }
    public PatternV3Run withFilterAudit(List<SignalFilterAudit.Row> rows){return new PatternV3Run(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,marketRegimeAnalysis,marketRegimeFilterAnalysis,strategyVersion,configurationHash,executionModelVersion,executionAudit,rows);}
    @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY) public String filterModelVersion(){return signalFilterAudit==null?null:SignalFilterAudit.VERSION;}
    public PatternV3Run {
        if(signalFilterAudit!=null)signalFilterAudit=List.copyOf(signalFilterAudit);
        ExecutionModel.policy(executionModelVersion);
        if(executionAudit!=null&&!ExecutionModel.version(ExecutionModel.policy(executionModelVersion)).equals(executionAudit.executionModelVersion()))throw new IllegalArgumentException("Execution audit/model mismatch");
        strategyVersion=strategyVersion==null?"3.0-research":strategyVersion;
        if(strategyVersion.isBlank())throw new IllegalArgumentException("Strategy version required");
        var actual=it.andreademartini.trading.backtest.patternstable.PatternStableStore.hash(configuration);
        if(configurationHash==null)configurationHash=actual;else if(!configurationHash.equals(actual))throw new IllegalArgumentException("Run configuration hash mismatch");
    }
    public PatternV3Run(String runId, String strategy, Instant timestamp, PatternV3Configuration configuration,
        PatternV1Result portfolio, List<String> equityTimestamps, List<Execution> executions,
        PatternV3Analysis.Analysis analysis, Costs costs, PatternV1Result benchmark,
        List<PatternRangeAnalysis.SignalAudit> rangeSignals, PatternRangeAnalysis.Analysis patternRangeAnalysis,
        String datasetId, String pairedRunId, List<CloseStrengthAnalysis.SignalAudit> closeStrengthSignals,
        CloseStrengthAnalysis.Analysis closeStrengthAnalysis,List<CapitalSkipAudit> capitalSkipAudits,CapitalSkipShadowAnalysis.Analysis capitalSkipShadowAnalysis,List<BollingerAtrAnalysis.SignalAudit> bollingerAtrSignals,BollingerAtrAnalysis.Analysis bollingerAtrAnalysis,MarketRegimeAnalysis.Analysis marketRegimeAnalysis,MarketRegimeFilter.Analysis marketRegimeFilterAnalysis,String strategyVersion,String configurationHash) {
        this(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,marketRegimeAnalysis,marketRegimeFilterAnalysis,strategyVersion,configurationHash,null,null);
    }
    @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY) public SignalTimeframe signalTimeframe(){return configuration.signalTimeframe();}
    @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY) public String executionTimeframe(){return "DAILY";}
    @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY) public String managementTimeframe(){return "DAILY";}
    @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY) public String signalCalendarVersion(){return configuration.signalTimeframe()==SignalTimeframe.WEEKLY?ResearchSessionCalendar.VERSION:null;}
    public java.time.LocalDate signalPeriodStart(PatternV1Result.Trade trade){
        if(signalTimeframe()==SignalTimeframe.DAILY)return trade.signalDate();
        var monday=trade.signalDate().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        return portfolio.priceSeries().get(trade.symbol()).stream().map(b->java.time.Instant.parse(b.timestamp()).atOffset(java.time.ZoneOffset.UTC).toLocalDate()).filter(d->!d.isBefore(monday)&&!d.isAfter(trade.signalDate())).min(java.util.Comparator.naturalOrder()).orElse(null);
    }
    public ExitFillPolicy executionPolicy(){return ExecutionModel.policy(executionModelVersion);}
    public PatternV3Run withExecutionMetadata(String model,GapExecutionAudit audit){
        return new PatternV3Run(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,marketRegimeAnalysis,marketRegimeFilterAnalysis,strategyVersion,configurationHash,model,audit,signalFilterAudit);
    }
    public PatternV3Run(String runId, String strategy, Instant timestamp, PatternV3Configuration configuration,
        PatternV1Result portfolio, List<String> equityTimestamps, List<Execution> executions,
        PatternV3Analysis.Analysis analysis, Costs costs, PatternV1Result benchmark,
        List<PatternRangeAnalysis.SignalAudit> rangeSignals, PatternRangeAnalysis.Analysis patternRangeAnalysis,
        String datasetId, String pairedRunId, List<CloseStrengthAnalysis.SignalAudit> closeStrengthSignals,
        CloseStrengthAnalysis.Analysis closeStrengthAnalysis,List<CapitalSkipAudit> capitalSkipAudits,CapitalSkipShadowAnalysis.Analysis capitalSkipShadowAnalysis,List<BollingerAtrAnalysis.SignalAudit> bollingerAtrSignals,BollingerAtrAnalysis.Analysis bollingerAtrAnalysis,MarketRegimeAnalysis.Analysis marketRegimeAnalysis,MarketRegimeFilter.Analysis marketRegimeFilterAnalysis) {this(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,marketRegimeAnalysis,marketRegimeFilterAnalysis,"3.0-research",it.andreademartini.trading.backtest.patternstable.PatternStableStore.hash(configuration));}
    @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY) public String strategyId(){return strategy;}
    @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY) public PatternV3Configuration configurationSnapshot(){return configuration;}
    public PatternV3Run(String runId,String strategy,Instant timestamp,PatternV3Configuration configuration,PatternV1Result portfolio,List<String> equityTimestamps,List<Execution> executions,PatternV3Analysis.Analysis analysis,Costs costs,PatternV1Result benchmark,List<PatternRangeAnalysis.SignalAudit> rangeSignals,PatternRangeAnalysis.Analysis patternRangeAnalysis,String datasetId,String pairedRunId,List<CloseStrengthAnalysis.SignalAudit> closeStrengthSignals,CloseStrengthAnalysis.Analysis closeStrengthAnalysis,List<CapitalSkipAudit> capitalSkipAudits,CapitalSkipShadowAnalysis.Analysis capitalSkipShadowAnalysis,List<BollingerAtrAnalysis.SignalAudit> bollingerAtrSignals,BollingerAtrAnalysis.Analysis bollingerAtrAnalysis,MarketRegimeAnalysis.Analysis marketRegimeAnalysis) {
        this(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,marketRegimeAnalysis,null);
    }
    public PatternV3Run withMarketRegimeFilterAnalysis(MarketRegimeFilter.Analysis value) {return new PatternV3Run(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,marketRegimeAnalysis,value,strategyVersion,configurationHash,executionModelVersion,executionAudit,signalFilterAudit);}
    public PatternV3Run(String runId,String strategy,Instant timestamp,PatternV3Configuration configuration,PatternV1Result portfolio,List<String> equityTimestamps,List<Execution> executions,PatternV3Analysis.Analysis analysis,Costs costs,PatternV1Result benchmark,List<PatternRangeAnalysis.SignalAudit> rangeSignals,PatternRangeAnalysis.Analysis patternRangeAnalysis,String datasetId,String pairedRunId,List<CloseStrengthAnalysis.SignalAudit> closeStrengthSignals,CloseStrengthAnalysis.Analysis closeStrengthAnalysis,List<CapitalSkipAudit> capitalSkipAudits,CapitalSkipShadowAnalysis.Analysis capitalSkipShadowAnalysis,List<BollingerAtrAnalysis.SignalAudit> bollingerAtrSignals,BollingerAtrAnalysis.Analysis bollingerAtrAnalysis) {
        this(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,null);
    }
    public PatternV3Run(String runId, String strategy, Instant timestamp, PatternV3Configuration configuration,
        PatternV1Result portfolio, List<String> equityTimestamps, List<Execution> executions,
        PatternV3Analysis.Analysis analysis, Costs costs, PatternV1Result benchmark,
        List<PatternRangeAnalysis.SignalAudit> rangeSignals, PatternRangeAnalysis.Analysis patternRangeAnalysis,
        String datasetId, String pairedRunId, List<CloseStrengthAnalysis.SignalAudit> closeStrengthSignals,
        CloseStrengthAnalysis.Analysis closeStrengthAnalysis,List<CapitalSkipAudit> capitalSkipAudits,CapitalSkipShadowAnalysis.Analysis capitalSkipShadowAnalysis) {this(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,null,null);}
    public PatternV3Run(String runId,String strategy,Instant timestamp,PatternV3Configuration configuration,PatternV1Result portfolio,List<String> equityTimestamps,List<Execution> executions,PatternV3Analysis.Analysis analysis,Costs costs,PatternV1Result benchmark,List<PatternRangeAnalysis.SignalAudit> rangeSignals,PatternRangeAnalysis.Analysis rangeAnalysis,String datasetId,String pairedRunId,List<CloseStrengthAnalysis.SignalAudit> closeSignals,CloseStrengthAnalysis.Analysis closeAnalysis){this(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,rangeAnalysis,datasetId,pairedRunId,closeSignals,closeAnalysis,null,null);}
    public PatternV3Run(String runId,String strategy,Instant timestamp,PatternV3Configuration configuration,
            PatternV1Result portfolio,List<String> equityTimestamps,List<Execution> executions,
            PatternV3Analysis.Analysis analysis,Costs costs,PatternV1Result benchmark,
            List<PatternRangeAnalysis.SignalAudit> rangeSignals,PatternRangeAnalysis.Analysis patternRangeAnalysis,
            String datasetId,String pairedRunId) {
        this(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,null,null);
    }
    public PatternV3Run(String runId,String strategy,Instant timestamp,PatternV3Configuration configuration,
            PatternV1Result portfolio,List<String> equityTimestamps,List<Execution> executions,
            PatternV3Analysis.Analysis analysis,Costs costs,PatternV1Result benchmark) {
        this(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,null,null,null,null);
    }
    public PatternV3Run withMarketRegimeAnalysis(MarketRegimeAnalysis.Analysis value) {return new PatternV3Run(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,value,marketRegimeFilterAnalysis,strategyVersion,configurationHash,executionModelVersion,executionAudit,signalFilterAudit);}
    public record Costs(BigDecimal commissions, BigDecimal slippage, BigDecimal transactionCosts) {
        public BigDecimal total() { return commissions.add(slippage).add(transactionCosts); }
    }
    public record Execution(PatternV1Result.Trade trade, String entryTimestamp, String exitTimestamp,
            BigDecimal entryPriceAfterSlippage, BigDecimal exitPriceAfterSlippage,
            BigDecimal commission, BigDecimal slippageCost, BigDecimal transactionCosts, SplitExecution splitExecution, PatternRange patternRange, BigDecimal closeStrength,Boolean closeStrengthAccepted,PatternV3CapitalLimits.Audit capitalLimits,BollingerAtrAnalysis.SignalAudit bollingerAtr) {
        public Execution(PatternV1Result.Trade trade,String entryTimestamp,String exitTimestamp,BigDecimal entryPriceAfterSlippage,BigDecimal exitPriceAfterSlippage,BigDecimal commission,BigDecimal slippageCost,BigDecimal transactionCosts,SplitExecution splitExecution,PatternRange patternRange,BigDecimal closeStrength,Boolean closeStrengthAccepted,PatternV3CapitalLimits.Audit capitalLimits){this(trade,entryTimestamp,exitTimestamp,entryPriceAfterSlippage,exitPriceAfterSlippage,commission,slippageCost,transactionCosts,splitExecution,patternRange,closeStrength,closeStrengthAccepted,capitalLimits,null);}
        public Execution(PatternV1Result.Trade trade,String entryTimestamp,String exitTimestamp,BigDecimal entryPriceAfterSlippage,BigDecimal exitPriceAfterSlippage,BigDecimal commission,BigDecimal slippageCost,BigDecimal transactionCosts,SplitExecution splitExecution,PatternRange patternRange,BigDecimal closeStrength,Boolean closeStrengthAccepted) {
            this(trade,entryTimestamp,exitTimestamp,entryPriceAfterSlippage,exitPriceAfterSlippage,commission,slippageCost,transactionCosts,splitExecution,patternRange,closeStrength,closeStrengthAccepted,null);
        }
        public Execution(PatternV1Result.Trade trade,String entryTimestamp,String exitTimestamp,
                BigDecimal entryPriceAfterSlippage,BigDecimal exitPriceAfterSlippage,BigDecimal commission,
                BigDecimal slippageCost,BigDecimal transactionCosts,SplitExecution splitExecution,PatternRange patternRange) {
            this(trade,entryTimestamp,exitTimestamp,entryPriceAfterSlippage,exitPriceAfterSlippage,commission,slippageCost,transactionCosts,splitExecution,patternRange,null,null);
        }
        public Execution(PatternV1Result.Trade trade,String entryTimestamp,String exitTimestamp,
                BigDecimal entryPriceAfterSlippage,BigDecimal exitPriceAfterSlippage,BigDecimal commission,
                BigDecimal slippageCost,BigDecimal transactionCosts,SplitExecution splitExecution) {
            this(trade,entryTimestamp,exitTimestamp,entryPriceAfterSlippage,exitPriceAfterSlippage,commission,slippageCost,transactionCosts,splitExecution,null);
        }
    }
    public record SplitExecution(BigDecimal entryFactor, BigDecimal exitFactor, BigDecimal entryQuantityEquivalent,
            BigDecimal exitQuantityEquivalent, BigDecimal entryPriceSplitUnadjusted, BigDecimal exitPriceSplitUnadjusted) {}
    public PatternV3Run withBollingerAtr(List<BollingerAtrAnalysis.SignalAudit> signals,BollingerAtrAnalysis.Analysis value) {return new PatternV3Run(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,signals,value,marketRegimeAnalysis,marketRegimeFilterAnalysis,strategyVersion,configurationHash,executionModelVersion,executionAudit,signalFilterAudit);}
    public PatternV3Run withBenchmark(PatternV1Result value) {
        return new PatternV3Run(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,value,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,marketRegimeAnalysis,marketRegimeFilterAnalysis,strategyVersion,configurationHash,executionModelVersion,executionAudit,signalFilterAudit);
    }
    public PatternV3Run withResearch(PatternRangeAnalysis.Analysis research,String dataset,String paired) {
        return new PatternV3Run(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,research,dataset,paired,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,marketRegimeAnalysis,marketRegimeFilterAnalysis,strategyVersion,configurationHash,executionModelVersion,executionAudit,signalFilterAudit);
    }
    public PatternV3Run withCloseStrengthResearch(CloseStrengthAnalysis.Analysis research) {
        return new PatternV3Run(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,research,capitalSkipAudits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,marketRegimeAnalysis,marketRegimeFilterAnalysis,strategyVersion,configurationHash,executionModelVersion,executionAudit,signalFilterAudit);
    }
    public PatternV3Run withCapitalSkipAudits(List<CapitalSkipAudit> audits){return new PatternV3Run(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,audits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,marketRegimeAnalysis,marketRegimeFilterAnalysis,strategyVersion,configurationHash,executionModelVersion,executionAudit,signalFilterAudit);}
    public PatternV3Run withCapitalSkipShadowAnalysis(CapitalSkipShadowAnalysis.Analysis shadow){return new PatternV3Run(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,shadow,bollingerAtrSignals,bollingerAtrAnalysis,marketRegimeAnalysis,marketRegimeFilterAnalysis,strategyVersion,configurationHash,executionModelVersion,executionAudit,signalFilterAudit);}
}
