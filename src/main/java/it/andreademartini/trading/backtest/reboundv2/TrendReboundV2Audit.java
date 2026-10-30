package it.andreademartini.trading.backtest.reboundv2;

import it.andreademartini.trading.backtest.rebound.*;
import it.andreademartini.trading.backtest.weeklytte.WeeklyTteStore;
import java.util.*;
import java.security.MessageDigest;

public final class TrendReboundV2Audit {
    private TrendReboundV2Audit(){}
    static Map<String,Object> map(Object o){return TrendReboundStore.JSON.convertValue(o,new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String,Object>>(){});}
    public static String engineFingerprint(){return fingerprint(TrendReboundEngine.class);}
    public static String modelFingerprint(){return WeeklyTteStore.hash(List.of(TrendReboundV2Engine.class,TrendReboundV2Configuration.class,TrendReboundIndicators.class,TrendReboundAnalysis.class,it.andreademartini.trading.backtest.execution.CostAwareLongSizing.class,it.andreademartini.trading.risk.PositionSizingService.class,it.andreademartini.trading.risk.TradePricingService.class,it.andreademartini.trading.backtest.patternv1.PatternV1Commission.class,it.andreademartini.trading.backtest.patternv2.PatternV2SplitCosts.class,it.andreademartini.trading.backtest.execution.Slippage.class,it.andreademartini.trading.backtest.patternv3.ResearchSessionCalendar.class).stream().map(type->type.getName()+":"+fingerprint(type)).toList());}
    public static String fingerprint(Class<?> type){try(var in=type.getResourceAsStream(type.getSimpleName()+".class")){return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Objects.requireNonNull(in).readAllBytes()));}catch(Exception e){throw new IllegalStateException(e);}}
    public static String baseSignature(TrendReboundConfiguration c){var m=map(c);m.remove("name");return WeeklyTteStore.hash(m);}
    public static String comparisonSignature(TrendReboundV2Configuration c){var m=map(c);m.remove("name");m.remove("exitMode");return WeeklyTteStore.hash(m);}
    public static String economics(TrendReboundRun r){
        var trades=r.trades().stream().map(t->{var m=map(t);m.remove("tradeId");m.remove("signalId");return m;}).toList();
        var signals=r.signals().stream().map(s->{var m=map(s);m.remove("signalId");m.remove("tradeId");return m;}).toList();
        return WeeklyTteStore.hash(Map.of("trades",trades,"signals",signals,"equity",r.equity()));
    }
    public static Map<String,Object> regression(TrendReboundV2Configuration c,TrendReboundRun baseline,TrendReboundRun result,TrendReboundRun reference){
        var expected=reference==null?baseline:reference;var m=new LinkedHashMap<String,Object>();m.put("referenceType",reference==null?"ORIGINAL_V1_ENGINE_ON_SAME_FROZEN_SNAPSHOT":"SAVED_V1_RUN_AND_ORIGINAL_ENGINE");m.put("referenceRunId",reference==null?null:reference.runId());m.put("referenceStrategyVersion",expected.strategyVersion());m.put("v1EngineFingerprint",engineFingerprint());m.put("dataFingerprint",result.datasetId());
        boolean compatible=expected.datasetId().equals(baseline.datasetId())&&baseSignature(expected.configuration()).equals(baseSignature(baseline.configuration()))&&expected.strategyVersion().equals(TrendReboundConfiguration.VERSION);
        String expectedHash=economics(expected),baselineHash=economics(baseline);m.put("referenceEconomicHash",expectedHash);m.put("originalEngineEconomicHash",baselineHash);m.put("originalEngineMatchesReference",compatible&&expectedHash.equals(baselineHash));
        boolean recovery=c.exitMode()==TrendReboundV2Configuration.ExitMode.RECOVERY_ONLY;m.put("v2RecoveryEconomicHash",recovery?economics(result):null);m.put("recoveryOnlyMatchesV1",recovery?compatible&&expectedHash.equals(economics(result)):null);m.put("status",!compatible||!expectedHash.equals(baselineHash)?"FAIL":recovery?(expectedHash.equals(economics(result))?"PASS":"FAIL"):"REFERENCE_VERIFIED_NOT_RECOVERY_MODE");m.put("note",recovery?"All economic trades, signal decisions and DAILY equity compared; identity fields excluded only.":"This mode changes exits. RECOVERY_ONLY parity is evaluated only on an executed RECOVERY_ONLY run, never inferred for this mode.");return m;
    }
    public static List<Map<String,Object>> exits(List<TrendReboundV2Run.TradeAudit> audits,List<TrendReboundRun.Trade> trades){
        var byId=new HashMap<String,TrendReboundRun.Trade>();trades.forEach(t->byId.put(t.tradeId(),t));long total=trades.stream().filter(t->t.exitDate()!=null).count();var rows=new ArrayList<Map<String,Object>>();
        for(String reason:List.of("RECOVERY","TAKE_PROFIT_ATR","STOP_LOSS","GAP_STOP","GAP_TARGET","TIMEOUT")){
            var xs=audits.stream().filter(a->a.netPnL()!=null&&reason.equals(a.exitReason())).toList();var row=new LinkedHashMap<String,Object>();row.put("ExitReason",reason);row.put("Trades",xs.size());row.put("Percentage",total>0?100.0*xs.size()/total:null);row.put("NetPnL",xs.stream().mapToDouble(TrendReboundV2Run.TradeAudit::netPnL).sum());row.put("WinRate",xs.isEmpty()?null:100.0*xs.stream().filter(a->a.netPnL()>0).count()/xs.size());row.put("AverageNetR",xs.isEmpty()?null:xs.stream().mapToDouble(TrendReboundV2Run.TradeAudit::netR).average().orElseThrow());row.put("AverageHoldingBars",xs.isEmpty()?null:xs.stream().mapToDouble(a->byId.get(a.tradeId()).holdingBars()).average().orElseThrow());rows.add(row);
        }return rows;
    }
    public static Map<String,Object> comparisonRow(TrendReboundV2Run r){
        var p=(Map<String,Object>)r.portfolio().analysis().get("performance");var row=new LinkedHashMap<String,Object>();row.put("RunId",r.runId());row.put("ExitMode",r.configuration().exitMode());row.put("Trades",p.get("closedTrades"));row.put("OpenPositions",p.get("openPositions"));row.put("WinRate",p.get("winRatePercent"));row.put("ProfitFactorNet",p.get("profitFactor"));row.put("CAGR",p.get("cagrPercent"));row.put("MaxDrawdown",p.get("maxDrawdownPercent"));row.put("ExpectancyNetR",p.get("expectancyR"));
        var xs=r.portfolio().trades().stream().filter(t->t.exitDate()!=null).toList();var wins=xs.stream().filter(t->t.netPnL()>0).toList();var losses=xs.stream().filter(t->t.netPnL()<0).toList();row.put("AverageWin",wins.isEmpty()?null:wins.stream().mapToDouble(TrendReboundRun.Trade::netPnL).average().orElseThrow());row.put("AverageLoss",losses.isEmpty()?null:losses.stream().mapToDouble(TrendReboundRun.Trade::netPnL).average().orElseThrow());row.put("CommissionTotal",p.get("commissions"));row.put("SlippageTotal",p.get("slippageCosts"));row.put("ExposureAverage",p.get("meanExposurePercent"));row.put("FinalEquity",p.get("finalEquity"));row.put("PortfolioNetPnL",p.get("portfolioNetPnL"));return row;
    }
}
