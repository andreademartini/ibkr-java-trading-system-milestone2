package it.andreademartini.trading.backtest.patternv2;

import it.andreademartini.trading.backtest.patternv1.PatternV1Result;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** V2-owned persisted artifact. The portfolio contract is immutable shared data, never V1 state. */
public record PatternV2Run(String runId, String strategy, Instant timestamp, PatternV2Configuration configuration,
        PatternV1Result portfolio, List<String> equityTimestamps, List<Execution> executions,
        PatternV2Analysis.Analysis analysis, Costs costs, PatternV1Result benchmark,
        List<PatternRangeAnalysis.SignalAudit> rangeSignals, PatternRangeAnalysis.Analysis patternRangeAnalysis,
        String datasetId, String pairedRunId, List<CloseStrengthAnalysis.SignalAudit> closeStrengthSignals,
        CloseStrengthAnalysis.Analysis closeStrengthAnalysis,List<CapitalSkipAudit> capitalSkipAudits,CapitalSkipShadowAnalysis.Analysis capitalSkipShadowAnalysis,List<BollingerAtrAnalysis.SignalAudit> bollingerAtrSignals,BollingerAtrAnalysis.Analysis bollingerAtrAnalysis,MarketRegimeAnalysis.Analysis marketRegimeAnalysis,MarketRegimeFilter.Analysis marketRegimeFilterAnalysis,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) GapExecutionAudit executionAudit) {
    public PatternV2Run(String runId, String strategy, Instant timestamp, PatternV2Configuration configuration,
        PatternV1Result portfolio, List<String> equityTimestamps, List<Execution> executions,
        PatternV2Analysis.Analysis analysis, Costs costs, PatternV1Result benchmark,
        List<PatternRangeAnalysis.SignalAudit> rangeSignals, PatternRangeAnalysis.Analysis patternRangeAnalysis,
        String datasetId, String pairedRunId, List<CloseStrengthAnalysis.SignalAudit> closeStrengthSignals,
        CloseStrengthAnalysis.Analysis closeStrengthAnalysis,List<CapitalSkipAudit> capitalSkipAudits,CapitalSkipShadowAnalysis.Analysis capitalSkipShadowAnalysis,List<BollingerAtrAnalysis.SignalAudit> bollingerAtrSignals,BollingerAtrAnalysis.Analysis bollingerAtrAnalysis,MarketRegimeAnalysis.Analysis marketRegimeAnalysis,MarketRegimeFilter.Analysis marketRegimeFilterAnalysis) {
        this(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,marketRegimeAnalysis,marketRegimeFilterAnalysis,null);
    }
    public PatternV2Run withExecutionAudit(GapExecutionAudit value) {
        return new PatternV2Run(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,marketRegimeAnalysis,marketRegimeFilterAnalysis,value);
    }
    public PatternV2Run(String runId,String strategy,Instant timestamp,PatternV2Configuration configuration,PatternV1Result portfolio,List<String> equityTimestamps,List<Execution> executions,PatternV2Analysis.Analysis analysis,Costs costs,PatternV1Result benchmark,List<PatternRangeAnalysis.SignalAudit> rangeSignals,PatternRangeAnalysis.Analysis patternRangeAnalysis,String datasetId,String pairedRunId,List<CloseStrengthAnalysis.SignalAudit> closeStrengthSignals,CloseStrengthAnalysis.Analysis closeStrengthAnalysis,List<CapitalSkipAudit> capitalSkipAudits,CapitalSkipShadowAnalysis.Analysis capitalSkipShadowAnalysis,List<BollingerAtrAnalysis.SignalAudit> bollingerAtrSignals,BollingerAtrAnalysis.Analysis bollingerAtrAnalysis,MarketRegimeAnalysis.Analysis marketRegimeAnalysis) {
        this(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,marketRegimeAnalysis,null);
    }
    public PatternV2Run withMarketRegimeFilterAnalysis(MarketRegimeFilter.Analysis value) {return new PatternV2Run(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,marketRegimeAnalysis,value,executionAudit);}
    public PatternV2Run(String runId,String strategy,Instant timestamp,PatternV2Configuration configuration,PatternV1Result portfolio,List<String> equityTimestamps,List<Execution> executions,PatternV2Analysis.Analysis analysis,Costs costs,PatternV1Result benchmark,List<PatternRangeAnalysis.SignalAudit> rangeSignals,PatternRangeAnalysis.Analysis patternRangeAnalysis,String datasetId,String pairedRunId,List<CloseStrengthAnalysis.SignalAudit> closeStrengthSignals,CloseStrengthAnalysis.Analysis closeStrengthAnalysis,List<CapitalSkipAudit> capitalSkipAudits,CapitalSkipShadowAnalysis.Analysis capitalSkipShadowAnalysis,List<BollingerAtrAnalysis.SignalAudit> bollingerAtrSignals,BollingerAtrAnalysis.Analysis bollingerAtrAnalysis) {
        this(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,null);
    }
    public PatternV2Run(String runId, String strategy, Instant timestamp, PatternV2Configuration configuration,
        PatternV1Result portfolio, List<String> equityTimestamps, List<Execution> executions,
        PatternV2Analysis.Analysis analysis, Costs costs, PatternV1Result benchmark,
        List<PatternRangeAnalysis.SignalAudit> rangeSignals, PatternRangeAnalysis.Analysis patternRangeAnalysis,
        String datasetId, String pairedRunId, List<CloseStrengthAnalysis.SignalAudit> closeStrengthSignals,
        CloseStrengthAnalysis.Analysis closeStrengthAnalysis,List<CapitalSkipAudit> capitalSkipAudits,CapitalSkipShadowAnalysis.Analysis capitalSkipShadowAnalysis) {this(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,null,null);}
    public PatternV2Run(String runId,String strategy,Instant timestamp,PatternV2Configuration configuration,PatternV1Result portfolio,List<String> equityTimestamps,List<Execution> executions,PatternV2Analysis.Analysis analysis,Costs costs,PatternV1Result benchmark,List<PatternRangeAnalysis.SignalAudit> rangeSignals,PatternRangeAnalysis.Analysis rangeAnalysis,String datasetId,String pairedRunId,List<CloseStrengthAnalysis.SignalAudit> closeSignals,CloseStrengthAnalysis.Analysis closeAnalysis){this(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,rangeAnalysis,datasetId,pairedRunId,closeSignals,closeAnalysis,null,null);}
    public PatternV2Run(String runId,String strategy,Instant timestamp,PatternV2Configuration configuration,
            PatternV1Result portfolio,List<String> equityTimestamps,List<Execution> executions,
            PatternV2Analysis.Analysis analysis,Costs costs,PatternV1Result benchmark,
            List<PatternRangeAnalysis.SignalAudit> rangeSignals,PatternRangeAnalysis.Analysis patternRangeAnalysis,
            String datasetId,String pairedRunId) {
        this(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,null,null);
    }
    public PatternV2Run(String runId,String strategy,Instant timestamp,PatternV2Configuration configuration,
            PatternV1Result portfolio,List<String> equityTimestamps,List<Execution> executions,
            PatternV2Analysis.Analysis analysis,Costs costs,PatternV1Result benchmark) {
        this(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,null,null,null,null);
    }
    public PatternV2Run withMarketRegimeAnalysis(MarketRegimeAnalysis.Analysis value) {return new PatternV2Run(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,value,marketRegimeFilterAnalysis,executionAudit);}
    public record Costs(BigDecimal commissions, BigDecimal slippage, BigDecimal transactionCosts) {
        public BigDecimal total() { return commissions.add(slippage).add(transactionCosts); }
    }
    public record Execution(PatternV1Result.Trade trade, String entryTimestamp, String exitTimestamp,
            BigDecimal entryPriceAfterSlippage, BigDecimal exitPriceAfterSlippage,
            BigDecimal commission, BigDecimal slippageCost, BigDecimal transactionCosts, SplitExecution splitExecution, PatternRange patternRange, BigDecimal closeStrength,Boolean closeStrengthAccepted,PatternV2CapitalLimits.Audit capitalLimits,BollingerAtrAnalysis.SignalAudit bollingerAtr) {
        public Execution(PatternV1Result.Trade trade,String entryTimestamp,String exitTimestamp,BigDecimal entryPriceAfterSlippage,BigDecimal exitPriceAfterSlippage,BigDecimal commission,BigDecimal slippageCost,BigDecimal transactionCosts,SplitExecution splitExecution,PatternRange patternRange,BigDecimal closeStrength,Boolean closeStrengthAccepted,PatternV2CapitalLimits.Audit capitalLimits){this(trade,entryTimestamp,exitTimestamp,entryPriceAfterSlippage,exitPriceAfterSlippage,commission,slippageCost,transactionCosts,splitExecution,patternRange,closeStrength,closeStrengthAccepted,capitalLimits,null);}
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
    public PatternV2Run withBollingerAtr(List<BollingerAtrAnalysis.SignalAudit> signals,BollingerAtrAnalysis.Analysis value) {return new PatternV2Run(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,signals,value,marketRegimeAnalysis,marketRegimeFilterAnalysis,executionAudit);}
    public PatternV2Run withBenchmark(PatternV1Result value) {
        return new PatternV2Run(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,value,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,marketRegimeAnalysis,marketRegimeFilterAnalysis,executionAudit);
    }
    public PatternV2Run withResearch(PatternRangeAnalysis.Analysis research,String dataset,String paired) {
        return new PatternV2Run(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,research,dataset,paired,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,marketRegimeAnalysis,marketRegimeFilterAnalysis,executionAudit);
    }
    public PatternV2Run withCloseStrengthResearch(CloseStrengthAnalysis.Analysis research) {
        return new PatternV2Run(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,research,capitalSkipAudits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,marketRegimeAnalysis,marketRegimeFilterAnalysis,executionAudit);
    }
    public PatternV2Run withCapitalSkipAudits(List<CapitalSkipAudit> audits){return new PatternV2Run(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,audits,capitalSkipShadowAnalysis,bollingerAtrSignals,bollingerAtrAnalysis,marketRegimeAnalysis,marketRegimeFilterAnalysis,executionAudit);}
    public PatternV2Run withCapitalSkipShadowAnalysis(CapitalSkipShadowAnalysis.Analysis shadow){return new PatternV2Run(runId,strategy,timestamp,configuration,portfolio,equityTimestamps,executions,analysis,costs,benchmark,rangeSignals,patternRangeAnalysis,datasetId,pairedRunId,closeStrengthSignals,closeStrengthAnalysis,capitalSkipAudits,shadow,bollingerAtrSignals,bollingerAtrAnalysis,marketRegimeAnalysis,marketRegimeFilterAnalysis,executionAudit);}
}
