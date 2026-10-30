package it.andreademartini.trading.backtest.reboundv2;

import it.andreademartini.trading.backtest.rebound.TrendReboundRun;
import java.time.*;
import java.util.*;

public record TrendReboundV2Run(String runId,String strategy,String strategyVersion,Instant timestamp,
        String configurationHash,String datasetId,String referenceV1RunId,TrendReboundV2Configuration configuration,
        TrendReboundRun portfolio,List<TradeAudit> tradeAudit,List<Map<String,Object>> exitAnalysis,
        Map<String,Object> regressionAudit,String v1EngineFingerprint,String v2EngineFingerprint) {
    public record TradeAudit(String tradeId,String signalId,String symbol,double atr14AtSignal,
            double stopLossAtrMultiplier,double takeProfitAtrMultiplier,double initialStopLoss,
            Double rawTakeProfit,Double initialTakeProfit,Double initialRewardRisk,boolean takeProfitEnabled,
            boolean recoveryEnabled,String exitMode,Double exitBarOpen,String exitReason,String gapExitType,
            boolean intrabarAmbiguous,String ambiguityReason,Double grossPnL,double commission,double slippage,
            Double netPnL,Double netR,Double tickSize){}
}
