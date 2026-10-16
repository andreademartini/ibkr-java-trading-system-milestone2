package it.andreademartini.trading.backtest.patternv2;

import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.research.pattern.PatternDirection;
import java.math.*;
import java.util.*;

/** Observations from a NEW simulation. Never used to reconstruct missing historical audit data. */
public record GapExecutionAudit(String schemaVersion, String executionModelVersion,
        String slippageModel, BigDecimal slippagePercentPerSide, String priceAdjustmentModel,
        Map<String, PriceBasis> priceBasisBySymbol, List<TradeAudit> trades, List<String> limitations) {
    public enum GapType { NONE, GAP_STOP, GAP_TARGET }
    public record PriceBasis(List<String> providers, List<String> declaredAdjustments,
            boolean splitCostRulePresent, String dividendAdjustment) {}
    public record TradeAudit(String tradeId, BigDecimal exitBarOpen, GapType gapExitType,
            BigDecimal gapSizePercent, BigDecimal gapSizeR, BigDecimal exitBasePrice,
            BigDecimal exitExecutionPrice, BigDecimal initialStopPrice, BigDecimal triggerLevel,
            BigDecimal exitSlippageAmount, Boolean gapExitFlag,
            BigDecimal initialRiskPerShareExecution, BigDecimal initialRiskPerSharePriceBasis,
            BigDecimal gapSizeRPriceBasis, String issue) {}

    public GapExecutionAudit {
        priceBasisBySymbol=Map.copyOf(priceBasisBySymbol);
        trades=List.copyOf(trades);limitations=List.copyOf(limitations);
    }
    public static GapExecutionAudit capture(PatternV2Run run, Map<String,List<HistoricalBar>> input) {
        return capture(it.andreademartini.trading.backtest.execution.ExecutionRunView.of(run),input);
    }
    public static GapExecutionAudit capture(it.andreademartini.trading.backtest.execution.ExecutionRunView run, Map<String,List<HistoricalBar>> input) {
        boolean openFirst=run.fillPolicy()==ExitFillPolicy.OPEN_FIRST;
        Map<String,Map<String,HistoricalBar>> indexed=new HashMap<>();
        Map<String,PriceBasis> basis=new TreeMap<>();
        input.forEach((symbol,bars)->{
            Map<String,HistoricalBar> byTime=new HashMap<>();
            bars.forEach(b->byTime.put(b.timestamp().toString(),b));indexed.put(symbol,byTime);
            boolean rule=run.splitRuleSymbols().contains(symbol);
            basis.put(symbol,new PriceBasis(bars.stream().map(HistoricalBar::provider).distinct().sorted().toList(),
                bars.stream().map(b->b.priceAdjustment().name()).distinct().sorted().toList(),rule,"UNKNOWN_NOT_RECONSTRUCTED"));
        });
        Map<String,Boolean> openingDecisions=new HashMap<>();
        for(var event:run.portfolio().events())if(event.eventType().equals("EXIT_EXECUTION"))
            openingDecisions.put(event.tradeId(),Boolean.TRUE.equals(event.details().get("atOpen")));
        List<TradeAudit> rows=new ArrayList<>();
        for(var execution:run.executions()){
            var t=execution.trade();if(!t.closed())continue;
            var bar=indexed.getOrDefault(t.symbol(),Map.of()).get(execution.exitTimestamp());
            BigDecimal open=bar==null?null:bar.getOpen();
            BigDecimal entryExecution=execution.entryPriceAfterSlippage();
            BigDecimal executionRisk=entryExecution==null?null:entryExecution.subtract(t.stopPrice()).abs();
            BigDecimal slip=execution.exitPriceAfterSlippage()==null?null:execution.exitPriceAfterSlippage().subtract(t.exitPrice()).abs();
            String issue=bar==null?"EXIT_BAR_UNAVAILABLE":null;
            GapType type=GapType.NONE;
            if(openFirst&&!openingDecisions.containsKey(t.tradeId())){type=null;issue="EXIT_DECISION_UNAVAILABLE";}
            // An OPEN preceding creation of the position is never a gap exit.
            if(openFirst&&Boolean.TRUE.equals(openingDecisions.get(t.tradeId()))){
                if(t.exitIndex()<=t.entryIndex()){type=null;issue="OPEN_EXIT_ON_ENTRY_BAR";}
                else if("STOP".equals(t.exitReason()))type=GapType.GAP_STOP;
                else if("TARGET".equals(t.exitReason()))type=GapType.GAP_TARGET;
                else {type=null;issue="OPEN_EXIT_WITHOUT_PROTECTIVE_TRIGGER";}
            }
            BigDecimal trigger="STOP".equals(t.exitReason())?t.stopPrice():"TARGET".equals(t.exitReason())?t.takeProfitPrice():null;
            BigDecimal percent=null,gapR=null,priceR=null;
            boolean gap=type!=null&&type!=GapType.NONE;
            if(gap&&open!=null&&trigger!=null){
                boolean isLong=t.direction()==PatternDirection.LONG;
                BigDecimal distance=(type==GapType.GAP_STOP)==isLong?trigger.subtract(open):open.subtract(trigger);
                if(distance.signum()<0||open.compareTo(t.exitPrice())!=0){type=null;issue="INCONSISTENT_GAP_EXECUTION";}
                else {
                    percent=ratio(distance.multiply(BigDecimal.valueOf(100)),trigger.abs());
                    gapR=ratio(distance,executionRisk);priceR=ratio(distance,t.riskPerShare());
                    if(gapR==null||priceR==null||percent==null)issue="INVALID_GAP_DENOMINATOR";
                }
            }
            if(type==null){percent=null;gapR=null;priceR=null;}
            rows.add(new TradeAudit(t.tradeId(),open,type,percent,gapR,t.exitPrice(),execution.exitPriceAfterSlippage(),
                t.stopPrice(),trigger,slip,type==null?null:type!=GapType.NONE,executionRisk,t.riskPerShare(),priceR,issue));
        }
        return new GapExecutionAudit("GAP_AUDIT/1",openFirst?"OPEN_FIRST/1":"LEVEL_ONLY/1",
            "ADVERSE_PERCENT_PER_SIDE/1",run.slippagePercent(),
            !run.splitAware()?"DATASET_OHLC_LEGACY_COST_BASIS":"DATASET_OHLC_WITH_SPLIT_AWARE_V1_COSTS",basis,rows,List.of(
                "Price R, target and sizing use base entry prices; GapSizeR uses entry after slippage. GapSizeRPriceBasis uses existing price R risk.",
                "GapSizePercent is distance beyond the trigger level, not the change from previous Close. Equality at a trigger is a zero-size opening exit.",
                "ExitSlippageAmount is per dataset share; execution slippageCost includes entry and exit multiplied by quantity.",
                "Commissions (including the order-value cap) use base prices. Costs are deducted once; after-slippage prices must not be charged slippage again.",
                "Targets in PAPER are LMT; the backtest assumes a complete fill and applies adverse slippage, even below a SELL limit or above a BUY limit. No queue, liquidity or opening auction guarantee.",
                "STOOQ may label adjusted OHLC RAW. Split rules cover only configured symbols/dates. Dividend factors and original executable prices are not available; split-unadjusted output can retain dividend adjustments.",
                "Dataset quantities may imply fractional historical shares. No fractional/whole historical lot reconstruction, financing or stock borrow costs is added."));
    }
    static BigDecimal ratio(BigDecimal value,BigDecimal denominator){
        return value==null||denominator==null||denominator.signum()<=0?null:value.divide(denominator,12,RoundingMode.HALF_UP);
    }
}
