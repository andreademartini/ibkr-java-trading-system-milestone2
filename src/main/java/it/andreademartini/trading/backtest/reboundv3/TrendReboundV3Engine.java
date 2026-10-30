package it.andreademartini.trading.backtest.reboundv3;

import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.risk.PositionSizingService;
import it.andreademartini.trading.backtest.execution.*;
import it.andreademartini.trading.backtest.patternv2.PatternV2SplitCosts;
import it.andreademartini.trading.backtest.patternv3.ResearchSessionCalendar;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static it.andreademartini.trading.backtest.reboundv3.TrendReboundV3Run.*;
import it.andreademartini.trading.backtest.rebound.TrendReboundRun.Trade;
import it.andreademartini.trading.backtest.rebound.TrendReboundRun.Equity;
import it.andreademartini.trading.backtest.rebound.TrendReboundRun.DataIssue;

/** Fresh DAILY portfolio, never a transformation of an existing trade list. No broker dependencies. */
@Component
public final class TrendReboundV3Engine {
    private final CostAwareLongSizing sizing;
    public TrendReboundV3Engine(PositionSizingService sizing){this.sizing=new CostAwareLongSizing(sizing);}
    public static LocalDate date(HistoricalBar b){return b.dateTime().toLocalDate();}
    public static LocalDate nextSession(LocalDate date){var d=date.plusDays(1);while(!ResearchSessionCalendar.isSession(d))d=d.plusDays(1);return d;}
    static BigDecimal bd(double n){return BigDecimal.valueOf(n);}
    public static double execution(double price,double percent,boolean buy){double slip=Slippage.perShare(bd(price),bd(percent)).doubleValue();return buy?price+slip:price-slip;}
    private static class Position {
        Signal signal;String id;LocalDate entryDate,recoveryDate,exitDate;long quantity;int holding=1;
        double entryBase,entry,stop,fee,risk,sizingRisk,factor,exitFee,slippage;Double exitBase,exit;
        String reason="OPEN",dataStatus="VALID";
        Trade trade(Double mark){boolean closed=exitDate!=null;Double pnl=closed?(exit-entry)*quantity-fee-exitFee:null;
            return new Trade(id,signal.signalId,signal.symbol,"LONG",signal.signalDate,signal.closes,signal.trendSma,signal.atr,entryDate,exitDate,recoveryDate,entryBase,entry,exitBase,exit,stop,quantity,risk,sizingRisk,fee,exitFee,slippage,pnl,pnl==null?null:pnl/risk,holding,reason,factor,mark,closed||mark==null?null:(mark-entry)*quantity-fee,dataStatus);}
    }
    public TrendReboundV3Run simulate(TrendReboundV3Configuration c,Map<String,List<HistoricalBar>> input,String datasetId,String configHash){
        c.validate();var tickReport=V3TickSupport.prevalidate(c,input,datasetId);if(!tickReport.passed())throw new V3TickSupport.ValidationException(tickReport);
        var tickContext=new V3TickContext(c,input,datasetId);var tickAudit=new LinkedHashMap<String,Map<String,Object>>();
        var issues=new ArrayList<DataIssue>();var warnings=new ArrayList<String>();
        if(V3TickSupport.isResearch(c))warnings.add("EXPLICIT RESEARCH ASSUMPTION: CSV decimal precision is used as a price increment. It is NOT a verified market tick, does not reconstruct tradable raw prices, and is not directly comparable to the verified-tick strategy.");
        warnings.add("EXPERIMENTAL: no claim of profitability or scientific validation. LONG only, no leverage, no partial exits.");
        warnings.add("Current-symbol universe: potential survivorship bias; delisted securities and point-in-time membership are not certified.");
        warnings.add("US equity calendar "+ResearchSessionCalendar.VERSION+"; missing bars can censor positions; stale marks are identified.");
        warnings.add("ATR seed = mean of N complete true ranges; first bar supplies previous close only (N+1 bars). Warm-up is outside the equity curve.");
        var data=new TreeMap<String,Map<LocalDate,HistoricalBar>>();var indicators=new TreeMap<String,Map<LocalDate,it.andreademartini.trading.backtest.rebound.TrendReboundIndicators.Value>>();
        LocalDate firstReady=null,last=null;
        for(String symbol:c.symbols()){
            if(c.excludedSymbols().contains(symbol)){issues.add(new DataIssue(symbol,"WARNING","EXPLICIT_EXCLUSION","Excluded by saved configuration"));continue;}
            var bars=input.getOrDefault(symbol,List.of()).stream().filter(b->!date(b).isAfter(c.endDate())).sorted(Comparator.comparing(HistoricalBar::dateTime)).toList();
            if(bars.isEmpty()){issues.add(new DataIssue(symbol,"ERROR","NO_DATA","No local DAILY input"));continue;}
            var dates=new TreeMap<LocalDate,HistoricalBar>();var values=new TreeMap<LocalDate,it.andreademartini.trading.backtest.rebound.TrendReboundIndicators.Value>();String invalid=null;
            for(var b:bars){
                if(b.interval()!=BarInterval.DAILY||date(b).getYear()<2010||!ResearchSessionCalendar.isSession(date(b))||dates.put(date(b),b)!=null
                        ||!symbol.equals(b.symbol())||!Double.isFinite(b.high())||b.low()<=0||b.low()>Math.min(b.open(),b.close())||b.high()<Math.max(b.open(),b.close())||b.volume()<0){invalid="Invalid/duplicate/non-session DAILY OHLC: "+date(b);break;}
            }
            if(invalid==null&&bars.stream().map(HistoricalBar::priceAdjustment).distinct().count()>1)invalid="Mixed adjustment modes";
            if(invalid==null&&bars.getFirst().priceAdjustment()!=PriceAdjustment.ADJUSTED){
                for(var rule:c.splitCosts().rules())if(rule.symbol().equals(symbol)&&rule.splits().stream().anyMatch(s->!s.effectiveDate().isBefore(date(bars.getFirst()))&&!s.effectiveDate().isAfter(date(bars.getLast()))))invalid="Raw data crosses a catalogued split; provide consistently adjusted OHLC";
            }
            if(invalid!=null){issues.add(new DataIssue(symbol,"ERROR","EXCLUDED_INVALID_DATA",invalid));continue;}
            it.andreademartini.trading.backtest.rebound.TrendReboundIndicators state=new it.andreademartini.trading.backtest.rebound.TrendReboundIndicators(c.benchmark());LocalDate previous=null;int missing=0,ready=0;
            for(var b:bars){
                if(previous!=null&&!nextSession(previous).equals(date(b))){state=new it.andreademartini.trading.backtest.rebound.TrendReboundIndicators(c.benchmark());missing++;}
                var v=state.onBar(b);values.put(date(b),v);previous=date(b);
                if(v.ready()&&!date(b).isBefore(c.startDate())){ready++;if(firstReady==null||date(b).isBefore(firstReady))firstReady=date(b);}
            }
            if(missing>0)issues.add(new DataIssue(symbol,"WARNING","MISSING_SESSIONS","Indicator state restarted after "+missing+" discontinuities"));
            if(ready==0)issues.add(new DataIssue(symbol,"WARNING","INSUFFICIENT_WARMUP","No ready indicator observation in requested period"));
            if(date(bars.getLast()).isBefore(c.endDate()))issues.add(new DataIssue(symbol,"WARNING","COVERAGE_END","Last available DAILY bar: "+date(bars.getLast())));
            if(!date(bars.getFirst()).isBefore(c.startDate()))issues.add(new DataIssue(symbol,"WARNING","LIMITED_WARMUP","Input starts on/after the requested performance start; effective start waits for completed warm-up"));
            data.put(symbol,dates);indicators.put(symbol,values);if(last==null||date(bars.getLast()).isAfter(last))last=date(bars.getLast());
            if(bars.getFirst().priceAdjustment()!=PriceAdjustment.ADJUSTED)issues.add(new DataIssue(symbol,"WARNING","RAW_PRICES","Unknown splits/dividend adjustments are not certified"));
        }
        if(firstReady==null||last==null||last.isBefore(firstReady))throw new IllegalArgumentException("No valid DAILY observations after warm-up. "+issues.stream().map(i->i.symbol()+": "+i.code()+" ("+i.detail()+")").toList());
        var signals=new ArrayList<Signal>();var trades=new ArrayList<Trade>();var curve=new ArrayList<Equity>();
        var positions=new TreeMap<String,Position>();var pending=new TreeMap<String,Signal>();var marks=new HashMap<String,Double>();
        double cash=c.initialCapital(),peak=c.initialCapital();
        for(LocalDate day=firstReady;!day.isAfter(last)&&!day.isAfter(c.endDate());day=day.plusDays(1)){
            if(!ResearchSessionCalendar.isSession(day))continue;
            var openingMarks=new HashMap<>(marks);
            for(var item:data.entrySet()){var b=item.getValue().get(day);if(b!=null)openingMarks.put(item.getKey(),b.open());}
            // 1. Existing opening exits: a stop gap wins the reason tie with a scheduled recovery sale.
            for(String symbol:new ArrayList<>(positions.keySet())){
                var p=positions.get(symbol);if(!p.dataStatus.equals("VALID"))continue;p.holding++;
                var b=data.get(symbol).get(day);
                if(b==null){p.dataStatus="DATA_GAP_UNRESOLVED";issues.add(new DataIssue(symbol,"ERROR","OPEN_POSITION_DATA_GAP","Unknown path on "+day+"; position censored without invented fills"));continue;}
                if(b.open()<=p.stop||p.recoveryDate!=null){String reason=b.open()<p.stop?"GAP_STOP":b.open()==p.stop?"STOP":"RECOVERY";cash+=close(c,p,b,b.open(),reason);trades.add(p.trade(b.open()));positions.remove(symbol);}
            }
            // 2. Opening entries, alphabetical symbol priority. Later intraday exits cannot finance these orders.
            for(String symbol:new ArrayList<>(pending.keySet())){
                var a=pending.get(symbol);if(day.isBefore(a.plannedEntryDate))continue;pending.remove(symbol);
                var b=data.get(symbol).get(day);a.attemptDate=day;
                if(!day.equals(a.plannedEntryDate)||b==null){reject(a,"MISSING_NEXT_OPEN");continue;}
                var third=data.get(symbol).get(a.signalDate);var d=a.diagnostics;
                d.put("FourthBarDate",day);d.put("FourthBarOpen",b.open());d.put("FourthBarHigh",b.high());d.put("FourthBarLow",b.low());d.put("FourthBarClose",b.close());
                boolean openPassed=b.open()<third.high();d.put("EntryOpenConditionPassed",openPassed);
                if(!openPassed){reject(a,"ENTRY_REJECTED_OPEN_AT_OR_ABOVE_PREVIOUS_HIGH");continue;}
                var resolved=tickContext.resolve(symbol,third,day);
                V3TickSupport.diagnostics(d,resolved,c.entryBufferTicks());
                tickAudit.putIfAbsent(symbol+":"+resolved.tickRuleId()+":"+resolved.minimumTick()+":"+resolved.priceAdjustmentMode(),V3TickSupport.row(resolved));
                double trigger=resolved.buyStopTrigger().doubleValue();
                boolean reached=b.getHigh().compareTo(resolved.buyStopTrigger())>=0;d.put("EntryTriggerReached",reached);
                if(!reached){reject(a,"ENTRY_NOT_TRIGGERED");continue;}
                if(positions.containsKey(symbol)){reject(a,"POSITION_ALREADY_OPEN");continue;}
                a.entryBasePrice=trigger;a.entryExecutionPrice=execution(trigger,c.slippagePercentPerSide(),true);a.initialStop=a.entryExecutionPrice-c.stopLossAtrMultiplier()*a.atr;
                if(!Double.isFinite(a.initialStop)||a.initialStop<=0||a.initialStop>=a.entryExecutionPrice){reject(a,"INVALID_STOP");continue;}
                double gross=notional(positions,openingMarks),eq=cash+gross;
                var factor=c.splitCosts().factor(symbol,b);var commission=PatternV2SplitCosts.commission(c.commission(),factor);
                var size=sizing.calculate(eq,cash,gross,trigger,a.entryExecutionPrice,a.initialStop,c.riskPercent(),c.maxOrderValuePercent(),c.maxExposurePercent(),c.slippagePercentPerSide(),commission);
                if(size.quantity()<1){reject(a,"INSUFFICIENT_CAPITAL_OR_RISK_BUDGET");continue;}
                var p=new Position();p.signal=a;p.id=stableId(datasetId,configHash,symbol,day,"trade");p.entryDate=day;p.quantity=size.quantity();p.entryBase=trigger;p.entry=a.entryExecutionPrice;p.stop=a.initialStop;p.fee=size.entryFee();p.risk=(p.entry-p.stop)*p.quantity;p.sizingRisk=size.riskWithCosts();p.factor=factor.doubleValue();p.slippage=(p.entry-p.entryBase)*p.quantity;
                cash-=p.quantity*p.entry+p.fee;positions.put(symbol,p);a.status="EXECUTED";a.reason="FOURTH_BAR_HIGH_BREAKOUT";a.entryDate=day;a.tradeId=p.id;
            }
            // 3. Stop intraday, including the entry session. All opens have already been processed.
            for(String symbol:new ArrayList<>(positions.keySet())){
                var p=positions.get(symbol);if(!p.dataStatus.equals("VALID"))continue;var b=data.get(symbol).get(day);if(b==null)continue;
                if(b.low()<=p.stop){boolean entryDay=day.equals(p.entryDate);if(entryDay)p.signal.diagnostics.put("IntrabarAmbiguity","ENTRY_AND_POSSIBLE_STOP_SEQUENCE_UNKNOWN_CONSERVATIVE_STOP");double base=entryDay?p.stop:b.open()<=p.stop?b.open():p.stop;String reason=!entryDay&&b.open()<p.stop?"GAP_STOP":"STOP_LOSS";cash+=close(c,p,b,base,reason);trades.add(p.trade(base));positions.remove(symbol);}
            }
            // 4. Precommitted close of holding session N (entry is session 1).
            for(String symbol:new ArrayList<>(positions.keySet())){
                var p=positions.get(symbol);if(!p.dataStatus.equals("VALID")||p.holding<c.maxHoldingBars())continue;var b=data.get(symbol).get(day);
                cash+=close(c,p,b,b.close(),"TIMEOUT");trades.add(p.trade(b.close()));positions.remove(symbol);
            }
            // 5. Completed-bar recovery decisions, then new signals for tomorrow's open.
            for(var item:data.entrySet()){
                String symbol=item.getKey();var b=item.getValue().get(day);if(b==null)continue;marks.put(symbol,b.close());var v=indicators.get(symbol).get(day);
                var p=positions.get(symbol);if(p!=null&&p.dataStatus.equals("VALID")&&v.recoverySma()!=null&&b.close()>v.recoverySma()&&p.recoveryDate==null)p.recoveryDate=day;
                if(!v.ready()||!v.declining()||b.close()<=v.trendSma())continue;
                var a=new Signal();a.symbol=symbol;a.signalDate=day;a.plannedEntryDate=nextSession(day);a.closes=v.closes();a.trendSma=v.trendSma();a.atr=v.atr();a.signalId=stableId(datasetId,configHash,symbol,day,"signal");V3Diagnostics.signal(c,a,item.getValue(),b);signals.add(a);
                if(positions.containsKey(symbol))reject(a,"POSITION_ALREADY_OPEN");else if(pending.containsKey(symbol))reject(a,"ORDER_ALREADY_PENDING");else pending.put(symbol,a);
            }
            double gross=notional(positions,marks),equity=cash+gross;peak=Math.max(peak,equity);
            curve.add(new Equity(day,equity,cash,gross,equity>0?gross/equity*100:0,(peak-equity)/peak*100,positions.size(),positions.values().stream().anyMatch(p->!p.dataStatus.equals("VALID"))));
            if(cash < -1e-7)throw new IllegalStateException("Negative unlevered cash");
        }
        pending.values().forEach(a->{a.status="CENSORED";a.reason="END_OF_DATA_BEFORE_NEXT_OPEN";});
        positions.forEach((symbol,p)->trades.add(p.trade(marks.get(symbol))));
        V3Diagnostics.enrich(c,signals,trades,data,datasetId,tickContext);
        var analysis=it.andreademartini.trading.backtest.rebound.TrendReboundAnalysis.calculate(c.benchmark(),trades,curve);var auditRows=new ArrayList<Map<String,Object>>();for(var item:tickReport.rows()){var row=new LinkedHashMap<String,Object>(item);row.put("UsedForEntryDecision",tickAudit.values().stream().anyMatch(used->Objects.equals(used.get("Symbol"),item.get("Symbol"))&&Objects.equals(used.get("TickRuleId"),item.get("TickRuleId"))&&Objects.equals(used.get("MinimumTick"),item.get("MinimumTick"))));auditRows.add(row);}analysis.put("minimumTickAudit",List.copyOf(auditRows));analysis.put("minimumTickPrevalidation",tickReport);analysis.put("minimumTickMetadataVersion",V3TickSupport.metadataVersion(c));analysis.put("minimumTickConfigurationHash",V3TickSupport.hash(c));analysis.put("priority","OPEN_EXITS_AND_GAPS, ALPHABETICAL_OPEN_ENTRIES, INTRADAY_STOPS, TIMEOUT_CLOSE, RECOVERY_CLOSE_SIGNALS");
        if(issues.stream().anyMatch(i->i.severity().equals("ERROR")))warnings.add("DATA QUALITY ERRORS: inspect exclusions and censored positions; aggregate results may be incomplete.");
        return new TrendReboundV3Run(UUID.randomUUID().toString(),TrendReboundV3Configuration.ID,TrendReboundV3Configuration.VERSION,Instant.now(),configHash,datasetId,ResearchSessionCalendar.VERSION,c,List.copyOf(signals),List.copyOf(trades),List.copyOf(curve),List.copyOf(issues),analysis,List.copyOf(warnings));
    }
    private static String stableId(String dataset,String config,String symbol,LocalDate day,String type){return UUID.nameUUIDFromBytes((dataset+":"+config+":"+symbol+":"+day+":"+type).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();}
    private static void reject(Signal signal,String reason){signal.status="REJECTED";signal.reason=reason;}
    private static double notional(Map<String,Position> positions,Map<String,Double> marks){return positions.entrySet().stream().mapToDouble(e->e.getValue().quantity*marks.getOrDefault(e.getKey(),e.getValue().entry)).sum();}
    private static double close(TrendReboundV3Configuration c,Position p,HistoricalBar b,double base,String reason){
        p.exitDate=date(b);p.exitBase=base;p.exit=execution(base,c.slippagePercentPerSide(),false);p.reason=reason;
        p.exitFee=PatternV2SplitCosts.commission(c.commission(),c.splitCosts().factor(p.signal.symbol,b)).charge(p.quantity,bd(p.exit)).doubleValue();p.slippage+=(base-p.exit)*p.quantity;
        return p.quantity*p.exit-p.exitFee;
    }
}
