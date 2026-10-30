package it.andreademartini.trading.backtest.reboundv2;

import it.andreademartini.trading.backtest.rebound.*;
import it.andreademartini.trading.backtest.execution.CostAwareLongSizing;
import it.andreademartini.trading.backtest.patternv2.PatternV2SplitCosts;
import it.andreademartini.trading.backtest.weeklytte.WeeklyTteStore;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.risk.PositionSizingService;
import org.springframework.stereotype.Component;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static it.andreademartini.trading.backtest.rebound.TrendReboundRun.*;
import static it.andreademartini.trading.backtest.rebound.TrendReboundEngine.date;
import static it.andreademartini.trading.backtest.rebound.TrendReboundEngine.execution;

/** V1 source is frozen. Its complete price-candidate stream is reused; every V2 portfolio is recomputed. */
@Component
public final class TrendReboundV2Engine {
    private final PositionSizingService rawSizing;private final CostAwareLongSizing sizing;
    public TrendReboundV2Engine(PositionSizingService sizing){rawSizing=sizing;this.sizing=new CostAwareLongSizing(sizing);}
    private static BigDecimal bd(double n){return BigDecimal.valueOf(n);}
    private static class Position {
        Signal signal;String id,reason="OPEN",dataStatus="VALID",ambiguityReason;LocalDate entryDate,recoveryDate,exitDate;
        long quantity;int holding=1;double entryBase,entry,stop,fee,risk,sizingRisk,factor,exitFee,slippage;
        Double target,rawTarget,exitBase,exit,exitOpen;boolean ambiguous;
        Trade trade(Double mark){boolean closed=exitDate!=null;Double pnl=closed?(exit-entry)*quantity-fee-exitFee:null;return new Trade(id,signal.signalId,signal.symbol,"LONG",signal.signalDate,signal.closes,signal.trendSma,signal.atr,entryDate,exitDate,recoveryDate,entryBase,entry,exitBase,exit,stop,quantity,risk,sizingRisk,fee,exitFee,slippage,pnl,pnl==null?null:pnl/risk,holding,reason,factor,mark,closed||mark==null?null:(mark-entry)*quantity-fee,dataStatus);}
    }
    public TrendReboundV2Run simulate(TrendReboundV2Configuration v2,Map<String,List<HistoricalBar>> input,String datasetId,TrendReboundRun reference){
        v2.validate();var c=v2.base();String hash=WeeklyTteStore.hash(v2);
        // V1 records ALL price-qualified candidates, including blocked ones. Ignore its execution status in other modes.
        var baseline=new TrendReboundEngine(rawSizing).simulate(c,input,datasetId,WeeklyTteStore.hash(c));
        if(reference!=null&&(!datasetId.equals(reference.datasetId())||!TrendReboundV2Audit.baseSignature(c).equals(TrendReboundV2Audit.baseSignature(reference.configuration()))))throw new IllegalArgumentException("V1 reference requires identical dataset, dates, indicators, capital, risk and costs");
        var audits=new ArrayList<TrendReboundV2Run.TradeAudit>();
        if(v2.exitMode()==TrendReboundV2Configuration.ExitMode.RECOVERY_ONLY){
            // Exact delegation, including warm-up, sizing, fees, stop levels, gap semantics and pending decisions.
            for(var t:baseline.trades()){HistoricalBar b=t.exitDate()==null?null:input.get(t.symbol()).stream().filter(x->date(x).equals(t.exitDate())).findFirst().orElseThrow();audits.add(audit(v2,t,null,null,b==null?null:b.open(),false,null));}
            return finish(v2,baseline,baseline,reference,audits,hash);
        }
        var data=new TreeMap<String,Map<LocalDate,HistoricalBar>>();var recovery=new HashMap<String,Map<LocalDate,Double>>();
        var invalid=new HashSet<String>();baseline.dataQuality().stream().filter(i->Set.of("NO_DATA","EXCLUDED_INVALID_DATA","EXPLICIT_EXCLUSION").contains(i.code())).forEach(i->invalid.add(i.symbol()));
        for(String symbol:c.symbols())if(!invalid.contains(symbol)){
            var bars=input.getOrDefault(symbol,List.of()).stream().filter(b->!date(b).isAfter(c.endDate())).sorted(Comparator.comparing(HistoricalBar::dateTime)).toList();var byDate=new TreeMap<LocalDate,HistoricalBar>();var sma=new HashMap<LocalDate,Double>();TrendReboundIndicators state=new TrendReboundIndicators(c);LocalDate previous=null;
            for(var b:bars){byDate.put(date(b),b);if(v2.recoveryEnabled()){if(previous!=null&&!TrendReboundEngine.nextSession(previous).equals(date(b)))state=new TrendReboundIndicators(c);sma.put(date(b),state.onBar(b).recoverySma());previous=date(b);}}data.put(symbol,byDate);recovery.put(symbol,sma);
        }
        var candidates=new HashMap<LocalDate,List<Signal>>();for(var a:baseline.signals())candidates.computeIfAbsent(a.signalDate,k->new ArrayList<>()).add(a);
        var issues=new ArrayList<>(baseline.dataQuality().stream().filter(i->!i.code().equals("OPEN_POSITION_DATA_GAP")).toList());
        var signals=new ArrayList<Signal>();var trades=new ArrayList<Trade>();var curve=new ArrayList<Equity>();var positions=new TreeMap<String,Position>();var pending=new TreeMap<String,Signal>();var marks=new HashMap<String,Double>();double cash=c.initialCapital(),peak=c.initialCapital();
        for(var originalDay:baseline.equity()){
            LocalDate day=originalDay.date();var openingMarks=new HashMap<>(marks);data.forEach((symbol,bars)->{var b=bars.get(day);if(b!=null)openingMarks.put(symbol,b.open());});
            // Known opening events precede every new opening entry. Price gaps win simultaneous recovery reason ties.
            for(String symbol:new ArrayList<>(positions.keySet())){
                var p=positions.get(symbol);if(!p.dataStatus.equals("VALID"))continue;p.holding++;var b=data.get(symbol).get(day);
                if(b==null){p.dataStatus="DATA_GAP_UNRESOLVED";issues.add(new DataIssue(symbol,"ERROR","OPEN_POSITION_DATA_GAP","Unknown path on "+day+"; position censored without invented fills"));continue;}
                String reason=b.open()<=p.stop?(b.open()<p.stop?"GAP_STOP":"STOP_LOSS"):p.target!=null&&b.open()>=p.target?"GAP_TARGET":p.recoveryDate!=null?"RECOVERY":null;
                if(reason!=null){cash+=close(v2,p,b,b.open(),reason);record(v2,p,b.open(),trades,audits);positions.remove(symbol);}
            }
            for(String symbol:new ArrayList<>(pending.keySet())){
                var a=pending.get(symbol);if(day.isBefore(a.plannedEntryDate))continue;pending.remove(symbol);a.attemptDate=day;var b=data.get(symbol).get(day);
                if(!day.equals(a.plannedEntryDate)||b==null){reject(a,"MISSING_NEXT_OPEN");continue;}if(positions.containsKey(symbol)){reject(a,"POSITION_ALREADY_OPEN");continue;}
                a.entryBasePrice=b.open();a.entryExecutionPrice=execution(b.open(),c.slippagePercentPerSide(),true);a.initialStop=a.entryExecutionPrice-c.stopAtrMultiplier()*a.atr;
                if(!Double.isFinite(a.initialStop)||a.initialStop<=0||a.initialStop>=a.entryExecutionPrice){reject(a,"INVALID_STOP");continue;}
                var factor=c.splitCosts().factor(symbol,b);Double target=null,rawTarget=null;if(v2.targetEnabled()){try{rawTarget=a.entryExecutionPrice+v2.takeProfitAtrMultiplier()*a.atr;target=v2.target(symbol,a.entryExecutionPrice,a.atr,factor);if(!Double.isFinite(target)||target<=a.entryExecutionPrice)throw new IllegalArgumentException();}catch(IllegalArgumentException e){reject(a,"INVALID_ATR_TARGET");continue;}}
                double gross=notional(positions,openingMarks),equity=cash+gross;var commission=PatternV2SplitCosts.commission(c.commission(),factor);
                var size=sizing.calculate(equity,cash,gross,b.open(),a.entryExecutionPrice,a.initialStop,c.riskPercent(),c.maxPositionValuePercent(),c.maxExposurePercent(),c.slippagePercentPerSide(),commission);
                if(size.quantity()<1){reject(a,"INSUFFICIENT_CAPITAL_OR_RISK_BUDGET");continue;}
                var p=new Position();p.signal=a;p.id=UUID.nameUUIDFromBytes((hash+":"+datasetId+":"+a.signalId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();p.entryDate=day;p.quantity=size.quantity();p.entryBase=b.open();p.entry=a.entryExecutionPrice;p.stop=a.initialStop;p.fee=size.entryFee();p.risk=(p.entry-p.stop)*p.quantity;p.sizingRisk=size.riskWithCosts();p.factor=factor.doubleValue();p.slippage=(p.entry-p.entryBase)*p.quantity;p.target=target;p.rawTarget=rawTarget;
                cash-=p.quantity*p.entry+p.fee;positions.put(symbol,p);a.status="EXECUTED";a.reason="NEXT_OPEN_AFTER_TREND_PULLBACK";a.entryDate=day;a.tradeId=p.id;
            }
            // No intraday proceeds can fund an earlier opening entry. OPEN stop/target precedes ambiguous H/L.
            for(String symbol:new ArrayList<>(positions.keySet())){
                var p=positions.get(symbol);if(!p.dataStatus.equals("VALID"))continue;var b=data.get(symbol).get(day);if(b==null)continue;
                boolean stop=b.low()<=p.stop,target=p.target!=null&&b.high()>=p.target;String reason=null;double base=0;
                if(b.open()<=p.stop){base=b.open();reason=b.open()<p.stop?"GAP_STOP":"STOP_LOSS";}
                else if(p.target!=null&&b.open()>=p.target){base=b.open();reason="GAP_TARGET";}
                else if(stop||target){p.ambiguous=stop&&target;if(p.ambiguous)p.ambiguityReason="DAILY_OHLC_UNORDERED_STOP_AND_TARGET_STOP_FIRST";base=stop?p.stop:p.target;reason=stop?"STOP_LOSS":"TAKE_PROFIT_ATR";}
                if(reason!=null){cash+=close(v2,p,b,base,reason);record(v2,p,base,trades,audits);positions.remove(symbol);}
            }
            for(String symbol:new ArrayList<>(positions.keySet())){
                var p=positions.get(symbol);if(!p.dataStatus.equals("VALID")||p.holding<c.maxHoldingBars())continue;var b=data.get(symbol).get(day);cash+=close(v2,p,b,b.close(),"TIMEOUT");record(v2,p,b.close(),trades,audits);positions.remove(symbol);
            }
            for(var item:data.entrySet()){
                String symbol=item.getKey();var b=item.getValue().get(day);if(b==null)continue;marks.put(symbol,b.close());var p=positions.get(symbol);Double sma=recovery.get(symbol).get(day);
                if(v2.recoveryEnabled()&&p!=null&&p.dataStatus.equals("VALID")&&sma!=null&&b.close()>sma&&p.recoveryDate==null)p.recoveryDate=day;
            }
            for(var source:candidates.getOrDefault(day,List.of())){
                var a=new Signal();a.signalId=source.signalId;a.symbol=source.symbol;a.signalDate=source.signalDate;a.plannedEntryDate=source.plannedEntryDate;a.closes=source.closes;a.trendSma=source.trendSma;a.atr=source.atr;signals.add(a);
                if(positions.containsKey(a.symbol))reject(a,"POSITION_ALREADY_OPEN");else if(pending.containsKey(a.symbol))reject(a,"ORDER_ALREADY_PENDING");else pending.put(a.symbol,a);
            }
            double gross=notional(positions,marks),equity=cash+gross;peak=Math.max(peak,equity);curve.add(new Equity(day,equity,cash,gross,equity>0?gross/equity*100:0,(peak-equity)/peak*100,positions.size(),positions.values().stream().anyMatch(p->!p.dataStatus.equals("VALID"))));if(cash < -1e-7)throw new IllegalStateException("Negative unlevered cash");
        }
        pending.values().forEach(a->{a.status="CENSORED";a.reason="END_OF_DATA_BEFORE_NEXT_OPEN";});positions.forEach((symbol,p)->record(v2,p,marks.get(symbol),trades,audits));
        var analysis=TrendReboundAnalysis.calculate(c,trades,curve);analysis.put("priority","OPEN_GAP_STOP, OPEN_GAP_TARGET, SCHEDULED_RECOVERY_OPEN, OPEN_ENTRIES, STOP_FIRST_INTRADAY, TIMEOUT_CLOSE, RECOVERY_CLOSE_SIGNAL");
        var warnings=new ArrayList<>(baseline.warnings().stream().filter(w->!w.startsWith("DATA QUALITY ERRORS:")).toList());if(issues.stream().anyMatch(i->i.severity().equals("ERROR")))warnings.add("DATA QUALITY ERRORS: inspect exclusions and censored V2 positions; aggregate results may be incomplete.");warnings.add("V2 research: fixed target is tick-rounded upward once from execution entry and signal ATR; STOP_FIRST for unresolved DAILY high/low. Target touch models a SELL with existing slippage, not a guaranteed limit fill.");warnings.add("RECOVERY enabled="+v2.recoveryEnabled()+"; ATR target enabled="+v2.targetEnabled()+"; raw takeProfitEnabled setting="+v2.takeProfitEnabled());
        var result=new TrendReboundRun(UUID.randomUUID().toString(),TrendReboundV2Configuration.ID,TrendReboundV2Configuration.VERSION,Instant.now(),hash,datasetId,baseline.calendarVersion(),c,List.copyOf(signals),List.copyOf(trades),List.copyOf(curve),List.copyOf(issues),analysis,List.copyOf(warnings));
        return finish(v2,baseline,result,reference,audits,hash);
    }
    private static TrendReboundV2Run finish(TrendReboundV2Configuration c,TrendReboundRun baseline,TrendReboundRun result,TrendReboundRun reference,List<TrendReboundV2Run.TradeAudit> audits,String hash){
        String id=UUID.randomUUID().toString();var portfolio=new TrendReboundRun(id,TrendReboundV2Configuration.ID,TrendReboundV2Configuration.VERSION,result.timestamp(),hash,result.datasetId(),result.calendarVersion(),result.configuration(),result.signals(),result.trades(),result.equity(),result.dataQuality(),result.analysis(),result.warnings());
        var regression=TrendReboundV2Audit.regression(c,baseline,result,reference);if("FAIL".equals(regression.get("status")))throw new IllegalStateException("V1 regression audit failed; run not accepted");
        return new TrendReboundV2Run(id,TrendReboundV2Configuration.ID,TrendReboundV2Configuration.VERSION,portfolio.timestamp(),hash,portfolio.datasetId(),reference==null?null:reference.runId(),c,portfolio,List.copyOf(audits),TrendReboundV2Audit.exits(audits,portfolio.trades()),regression,TrendReboundV2Audit.engineFingerprint(),TrendReboundV2Audit.modelFingerprint());
    }
    private static TrendReboundV2Run.TradeAudit audit(TrendReboundV2Configuration c,Trade t,Double rawTarget,Double target,Double exitOpen,boolean ambiguous,String ambiguityReason){
        String reason=t.exitReason().equals("STOP")?"STOP_LOSS":t.exitReason();double commission=t.entryCommission()+t.exitCommission();
        return new TrendReboundV2Run.TradeAudit(t.tradeId(),t.signalId(),t.symbol(),t.atrAtSignal(),c.stopLossAtrMultiplier(),c.takeProfitAtrMultiplier(),t.initialStop(),rawTarget,target,target==null?null:(target-t.entryExecutionPrice())/(t.entryExecutionPrice()-t.initialStop()),c.targetEnabled(),c.recoveryEnabled(),c.exitMode().name(),exitOpen,reason,reason.equals("GAP_STOP")||reason.equals("GAP_TARGET")?reason:"NONE",ambiguous,ambiguityReason,t.netPnL()==null?null:t.netPnL()+commission,commission,t.slippageCosts(),t.netPnL(),t.netR(),c.tick(t.symbol(),t.entryExecutionPrice()*t.splitFactor()).divide(bd(t.splitFactor()),java.math.MathContext.DECIMAL128).doubleValue());
    }
    private static void record(TrendReboundV2Configuration c,Position p,Double mark,List<Trade> trades,List<TrendReboundV2Run.TradeAudit> audits){var t=p.trade(mark);trades.add(t);audits.add(audit(c,t,p.rawTarget,p.target,p.exitOpen,p.ambiguous,p.ambiguityReason));}
    private static void reject(Signal a,String reason){a.status="REJECTED";a.reason=reason;}
    private static double notional(Map<String,Position> positions,Map<String,Double> marks){return positions.entrySet().stream().mapToDouble(e->e.getValue().quantity*marks.getOrDefault(e.getKey(),e.getValue().entry)).sum();}
    private static double close(TrendReboundV2Configuration c,Position p,HistoricalBar b,double base,String reason){p.exitDate=date(b);p.exitOpen=b.open();p.exitBase=base;p.exit=execution(base,c.slippagePercentPerSide(),false);p.reason=reason;p.exitFee=PatternV2SplitCosts.commission(c.commission(),c.splitCosts().factor(p.signal.symbol,b)).charge(p.quantity,bd(p.exit)).doubleValue();p.slippage+=(base-p.exit)*p.quantity;return p.quantity*p.exit-p.exitFee;}
}
