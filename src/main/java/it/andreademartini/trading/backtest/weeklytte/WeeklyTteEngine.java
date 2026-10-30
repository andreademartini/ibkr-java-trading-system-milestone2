package it.andreademartini.trading.backtest.weeklytte;

import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.backtest.weeklytte.WeeklyTteRun.WeeklyLevel;
import it.andreademartini.trading.risk.*;
import it.andreademartini.trading.backtest.execution.Slippage;
import it.andreademartini.trading.backtest.patternv2.PatternV2SplitCosts;
import it.andreademartini.trading.backtest.patternv3.ResearchSessionCalendar;
import java.math.*;
import java.time.*;
import java.util.*;
import static it.andreademartini.trading.backtest.weeklytte.WeeklyTteRun.*;
import static it.andreademartini.trading.backtest.weeklytte.WeeklyTteLevels.*;
import static it.andreademartini.trading.backtest.weeklytte.WeeklyTteConfiguration.*;

/** Offline portfolio. All state is local to a run; no broker, paper router or strategy beans. */
public final class WeeklyTteEngine {
    private final PositionSizingService sizing;
    public WeeklyTteEngine(PositionSizingService sizing){this.sizing=sizing;}
    static BigDecimal bd(double n){return BigDecimal.valueOf(n);}
    public static double execution(double base,double percent,boolean buy){double slip=Slippage.perShare(bd(base),bd(percent)).doubleValue();return buy?base+slip:base-slip;}
    public record Exit(double price,String reason,String gap,double gapPct,double gapR) {}
    /** OPEN_FIRST precedes STOP_FIRST, for positions already alive at the open. */
    public static Exit exit(HistoricalBar b,double stop,double target,double risk,int holding,int max,boolean openOnly){
        if(b.open()<=stop)return new Exit(b.open(),"STOP_LOSS","GAP_STOP",(b.open()-stop)/stop*100,(b.open()-stop)/risk);
        if(b.open()>=target)return new Exit(b.open(),"TAKE_PROFIT","GAP_TARGET",(b.open()-target)/target*100,(b.open()-target)/risk);
        if(openOnly)return null;
        if(b.low()<=stop)return new Exit(stop,"STOP_LOSS","NONE",0,0);
        if(b.high()>=target)return new Exit(target,"TAKE_PROFIT","NONE",0,0);
        return holding>=max?new Exit(b.close(),"TIMEOUT","NONE",0,0):null;
    }
    private static class Position {
        Setup setup;long quantity,remaining;double entry,base,fee,stop,target,risk,equity,cashBefore,cashAfter,factor;int entryIndex,holding;
        boolean firstPartial=false,ambiguous=false;List<Fill> fills=new ArrayList<>();
        Trade trade(WeeklyTteConfiguration c){
            double proceeds=fills.stream().mapToDouble(f->f.executionPrice()*f.quantity()-f.commission()).sum();
            double exited=fills.stream().mapToLong(Fill::quantity).sum();
            double pnl=proceeds-entry*exited-fee;double costs=fills.stream().mapToDouble(Fill::commission).sum();
            double slip=(entry-base)*quantity+fills.stream().mapToDouble(Fill::slippage).sum();
            Fill last=fills.isEmpty()?null:fills.getLast();
            double exitBase=exited==0?0:fills.stream().mapToDouble(f->f.basePrice()*f.quantity()).sum()/exited;
            double exitExec=exited==0?0:fills.stream().mapToDouble(f->f.executionPrice()*f.quantity()).sum()/exited;
            return new Trade(setup.tradeId,setup.setupId,setup.symbol,c.mode().name(),setup.previousWeekHigh,setup.entryTrigger,
                    setup.pullbackLow,stop,setup.realizedRewardRisk,setup.signalDate,setup.entryDate,remaining==0?last.date():null,
                    base,entry,last==null?null:exitBase,last==null?null:exitExec,quantity,remaining,fee,costs,slip,pnl,pnl/(risk*quantity),holding,
                    remaining>0?"OPEN":last.reason(),ambiguous,equity,cashBefore,cashAfter,risk*quantity,factor,last==null?"NONE":last.gapExitType(),last==null?0:last.gapSizePercent(),last==null?0:last.gapSizeR(),List.copyOf(fills));
        }
    }
    public WeeklyTteRun simulate(WeeklyTteConfiguration c,Map<String,List<HistoricalBar>> input,String datasetId,String configHash){
        if(c.historical())return new HistoricalWeeklyTteEngine(sizing).simulate(c,input,datasetId,configHash);
        c.validate();Map<String,List<HistoricalBar>> data=new TreeMap<>();List<WeeklyLevel> levels=new ArrayList<>();
        var levelIndex=new HashMap<String,Map<LocalDate,WeeklyLevel>>();
        var dates=new TreeSet<LocalDate>();
        for(String symbol:c.symbols()) {
            var bars=input.getOrDefault(symbol,List.of()).stream().filter(b->!date(b).isAfter(c.endDate())).sorted(Comparator.comparing(HistoricalBar::dateTime)).toList();
            validateBars(symbol,bars);data.put(symbol,bars);
            var ls=calculate(symbol,bars,c.startDate(),c.endDate());levels.addAll(ls);
            var index=new HashMap<LocalDate,WeeklyLevel>();ls.forEach(l->index.put(l.weekStart(),l));levelIndex.put(symbol,index);
            bars.stream().map(WeeklyTteLevels::date).filter(d->!d.isBefore(c.startDate())).forEach(dates::add);
        }
        var setups=new ArrayList<Setup>();var pending=new TreeMap<String,Setup>();var positions=new TreeMap<String,Position>();
        var consumedPeaks=new HashSet<String>();
        var closed=new ArrayList<Trade>();var curve=new ArrayList<Equity>();var marks=new HashMap<String,Double>();
        var indices=new HashMap<String,Map<LocalDate,Integer>>();
        data.forEach((s,b)->{var m=new HashMap<LocalDate,Integer>();for(int i=0;i<b.size();i++)m.put(date(b.get(i)),i);indices.put(s,m);});
        double cash=c.initialCapital();
        if(dates.isEmpty())throw new IllegalArgumentException("No DAILY bars in requested period");
        for(LocalDate day:dates) {
            // Expire the old reference before a new weekly benchmark can replace its pending order.
            pending.entrySet().removeIf(item->{
                if(monday(day).equals(item.getValue().weekStart))return false;
                reject(item.getValue(),"EXPIRED_WEEK_CHANGE",null);return true;
            });
            if(c.mode()==Mode.WEEKLY_HIGH_BREAKOUT)for(String symbol:c.symbols()){
                Integer index=indices.get(symbol).get(day);if(index==null||index<1||!day.equals(ResearchSessionCalendar.weekSessions(day).getFirst()))continue;
                var level=levelIndex.get(symbol).get(monday(day));var a=breakout(c,symbol,data.get(symbol),index,level);
                setups.add(a);if(a.setupStatus.equals("PENDING"))pending.put(symbol,a);
            }
            // Only open exits release cash before today's entries. Intraday proceeds cannot finance an earlier open.
            for(String s:new ArrayList<>(positions.keySet())) {
                Integer i=indices.get(s).get(day);if(i==null)continue;var p=positions.get(s);var b=data.get(s).get(i);p.holding++;
                var e=exit(b,p.stop,p.target,p.risk,p.holding,c.maxHoldingDays(),true);
                if(e!=null){cash+=fill(c,p,b,e);if(p.remaining==0){closed.add(p.trade(c));positions.remove(s);}}
            }
            double equityAtDecision=cash+positions.values().stream().mapToDouble(p->p.remaining*marks.getOrDefault(p.setup.symbol,p.entry)).sum();
            double notionalAtDecision=positions.values().stream().mapToDouble(p->p.remaining*marks.getOrDefault(p.setup.symbol,p.entry)).sum();
            double entryBarProceeds=0;
            for(String s:new ArrayList<>(pending.keySet())) {
                var a=pending.get(s);Integer i=indices.get(s).get(day);
                if(!monday(day).equals(a.weekStart)){reject(a,"EXPIRED_WEEK_CHANGE",null);pending.remove(s);continue;}
                if(i==null)continue;var b=data.get(s).get(i);int age=i-a.signalIndex;
                if(age>c.entryValidityBars()){reject(a,"EXPIRED",null);pending.remove(s);continue;}
                if(hasMissingSessions(data.get(s),a.signalIndex,i)){reject(a,"REJECTED","MISSING_DAILY_SESSION");pending.remove(s);continue;}
                // An opening touch is chronologically prior to any intraday trigger in anticipation modes.
                if(c.mode()!=Mode.WEEKLY_HIGH_BREAKOUT&&b.open()>=a.previousWeekHigh){reject(a,"TARGET_REACHED_BEFORE_ENTRY",null);pending.remove(s);continue;}
                if(b.high()<a.entryTrigger) {
                    if(c.mode()!=Mode.WEEKLY_HIGH_BREAKOUT&&b.high()>=a.previousWeekHigh){reject(a,"TARGET_REACHED_BEFORE_ENTRY",null);pending.remove(s);}
                    else if(age>=c.entryValidityBars()){reject(a,"EXPIRED",null);pending.remove(s);}
                    continue;
                }
                a.orderTriggered=true;a.entryDate=day;a.entryBasePrice=Math.max(b.open(),a.entryTrigger);a.gapEntry=b.open()>a.entryTrigger;
                a.entryExecutionPrice=execution(a.entryBasePrice,c.slippagePercentPerSide(),true);
                double risk=a.entryExecutionPrice-a.initialStop;
                double target=c.mode()==Mode.WEEKLY_HIGH_BREAKOUT?a.entryExecutionPrice+risk*c.breakoutTargetR():a.previousWeekHigh;
                double rr=(target-a.entryExecutionPrice)/risk;a.realizedRewardRisk=Double.isFinite(rr)?rr:null;
                String invalid=!(risk>0&&Double.isFinite(risk))?"INVALID_STOP":!(target>a.entryExecutionPrice)?"ENTRY_AT_OR_ABOVE_TARGET":rr<c.minRewardRiskRatio()?"INSUFFICIENT_REWARD_RISK":positions.containsKey(s)?"POSITION_ALREADY_OPEN":null;
                if(invalid!=null){reject(a,"REJECTED",invalid);pending.remove(s);continue;}
                double factor=c.splitCosts().factor(s,b).doubleValue();
                var feeModel=PatternV2SplitCosts.commission(c.commission(),bd(factor));
                double limit=Math.min(equityAtDecision*c.maxOrderValuePercent()/100,equityAtDecision*c.maxPositionValuePercent()/100);
                limit=Math.min(limit,Math.max(0,equityAtDecision*c.maxLongNotionalPercent()/100-notionalAtDecision));
                long q=0;
                if(equityAtDecision>0&&limit>0)try{
                    q=sizing.calculate(bd(equityAtDecision),bd(c.riskPercent()),bd(a.entryExecutionPrice),bd(a.initialStop),
                            bd(Slippage.perShare(bd(a.initialStop),bd(c.slippagePercentPerSide())).doubleValue()),BigDecimal.ZERO,
                            bd(limit),c.useLeverage(),Long.MAX_VALUE/4).quantity();
                }catch(RiskRejectedException|IllegalArgumentException ignored){q=0;}
                // Cost-aware monotone binary search preserves risk and cash constraints without slow share-by-share loops.
                long lo=0,hi=q;final double freeCash=cash;final double eq=equityAtDecision;final double executionPrice=a.entryExecutionPrice;
                while(lo<hi){long mid=lo+(hi-lo)/2+(hi-lo)%2;
                    double entryFee=feeModel.charge(mid,bd(executionPrice)).doubleValue();
                    double exitFee=feeModel.charge(mid,bd(a.initialStop)).doubleValue();
                    boolean fits=(risk+Slippage.perShare(bd(a.initialStop),bd(c.slippagePercentPerSide())).doubleValue())*mid+entryFee+exitFee<=eq*c.riskPercent()/100
                            &&(c.useLeverage()||executionPrice*mid+entryFee<=freeCash);
                    if(fits)lo=mid;else hi=mid-1;
                }q=lo;
                if(q<1){reject(a,"REJECTED","INSUFFICIENT_CAPITAL_OR_RISK_BUDGET");pending.remove(s);continue;}
                var p=new Position();p.setup=a;p.quantity=q;p.remaining=q;p.entry=a.entryExecutionPrice;p.base=a.entryBasePrice;p.stop=a.initialStop;
                p.target=target;p.risk=risk;p.entryIndex=i;p.holding=1;p.equity=equityAtDecision;p.cashBefore=cash;p.factor=factor;
                p.fee=feeModel.charge(q,bd(p.entry)).doubleValue();cash-=q*p.entry+p.fee;p.cashAfter=cash;
                notionalAtDecision+=q*p.entry;
                a.tradeId=UUID.randomUUID().toString();a.setupStatus="ACCEPTED";pending.remove(s);positions.put(s,p);
                if(b.open()<a.entryTrigger) {
                    boolean stop=b.low()<=p.stop,hit=b.high()>=p.target;
                    if(stop||hit){a.intrabarAmbiguous=true;p.ambiguous=true;
                        Exit e=null;
                        if(c.ambiguousBarPolicy()==Ambiguity.CONSERVATIVE&&stop)e=new Exit(p.stop,"AMBIGUOUS_ENTRY_ADVERSE","NONE",0,0);
                        else if(c.ambiguousBarPolicy()==Ambiguity.SENSITIVITY_FAVORABLE&&hit)e=new Exit(p.target,"AMBIGUOUS_ENTRY_FAVORABLE","NONE",0,0);
                        if(e!=null){entryBarProceeds+=fill(c,p,b,e);if(p.remaining==0){closed.add(p.trade(c));positions.remove(s);}}
                    }
                    // Remaining intraday extremes are unknown relative to entry. Close is chronologically known.
                    if(positions.containsKey(s)&&b.close()<=p.stop){entryBarProceeds+=fill(c,p,b,new Exit(b.close(),"ENTRY_CLOSE_BELOW_STOP","NONE",0,0));closed.add(p.trade(c));positions.remove(s);}
                    else if(positions.containsKey(s)&&c.maxHoldingDays()==1){entryBarProceeds+=fill(c,p,b,new Exit(b.close(),"TIMEOUT","NONE",0,0));closed.add(p.trade(c));positions.remove(s);}
                }
            }
            cash+=entryBarProceeds;
            for(String s:new ArrayList<>(positions.keySet())) {
                Integer i=indices.get(s).get(day);if(i==null)continue;var p=positions.get(s);var b=data.get(s).get(i);
                if(p.entryIndex==i&&b.open()<p.setup.entryTrigger)continue;
                var e=exit(b,p.stop,p.target,p.risk,p.holding,c.maxHoldingDays(),false);
                if(e!=null){cash+=fill(c,p,b,e);if(p.remaining==0){closed.add(p.trade(c));positions.remove(s);}}
                if(positions.containsKey(s)&&p.holding>=c.maxHoldingDays()){cash+=fill(c,p,b,new Exit(b.close(),"TIMEOUT","NONE",0,0));closed.add(p.trade(c));positions.remove(s);}
            }
            // Signals are evaluated at the close, after all execution decisions for this session.
            for(String s:c.symbols()) {
                Integer i=indices.get(s).get(day);if(i==null)continue;var bars=data.get(s);var b=bars.get(i);marks.put(s,b.close());
                if(pending.containsKey(s))continue;
                if(c.mode()==Mode.WEEKLY_HIGH_BREAKOUT)continue;
                var a=detect(c,s,bars,i,levelIndex.get(s).get(monday(day)));
                if(a!=null&&!consumedPeaks.add(s+":"+a.pullbackPeakIndex))continue;
                if(a!=null){setups.add(a);if("PENDING".equals(a.setupStatus))pending.put(s,a);}
            }
            double notional=positions.values().stream().mapToDouble(p->p.remaining*marks.getOrDefault(p.setup.symbol,p.entry)).sum();
            double equity=cash+notional;curve.add(new Equity(day,equity,cash,notional,equity>0?notional/equity*100:0));
        }
        pending.values().forEach(a->reject(a,"CENSORED_END_OF_DATA",null));
        positions.values().forEach(p->closed.add(p.trade(c)));
        var warnings=new ArrayList<String>();warnings.add("RESEARCH: TTE-inspired deterministic rule; not official Joe Ross rules. No broker orders.");
        warnings.add("DAILY entry extremes are scenarios, not known intraday paths. Compare CONSERVATIVE with SENSITIVITY_FAVORABLE.");
        warnings.add("Incomplete current weeks excluded from weekly touch probabilities. Open trades and pending setups are censored at end of data.");
        warnings.add("US equity calendar 2010..2028; split cost catalog must be verified per symbol. Unavailable market regime is never a filter.");
        return new WeeklyTteRun(UUID.randomUUID().toString(),STRATEGY,Instant.now(),configHash,datasetId,ResearchSessionCalendar.VERSION,c,
                List.copyOf(levels),List.copyOf(setups),List.copyOf(closed),List.copyOf(curve),WeeklyTteAnalysis.calculateWithOutcomes(c,levels,setups,closed,curve,data),List.copyOf(warnings));
    }
    private static void reject(Setup a,String status,String reason){a.setupStatus=status;a.rejectionReason=reason;}
    private static void validateBars(String symbol,List<HistoricalBar> bars){
        LocalDate previous=null;for(var b:bars){var d=date(b);
            if(b.interval()!=BarInterval.DAILY||!ResearchSessionCalendar.isSession(d)||d.equals(previous)||b.low()<=0||b.high()<Math.max(b.open(),b.close())||b.low()>Math.min(b.open(),b.close())||!Double.isFinite(b.high()))
                throw new IllegalArgumentException("Invalid/duplicate/non-session DAILY bar: "+symbol+" "+d);
            previous=d;
        }
    }
    private static boolean hasMissingSessions(List<HistoricalBar> bars,int from,int to){
        int actual=to-from;int expected=0;for(var d=date(bars.get(from)).plusDays(1);!d.isAfter(date(bars.get(to)));d=d.plusDays(1))if(ResearchSessionCalendar.isSession(d))expected++;
        return actual!=expected;
    }
    /** Latest local high, followed by strictly declining closes with lower highs, then one bullish recovery. */
    public static Setup detect(WeeklyTteConfiguration c,String symbol,List<HistoricalBar> bars,int i,WeeklyLevel reference){
        if(i<2)return null;var recovery=bars.get(i);var prev=bars.get(i-1);
        if(!(recovery.close()>recovery.open()&&recovery.close()>prev.close()))return null;
        int peak=-1,start=-1;
        for(int n=c.minPullbackBars();n<=c.maxPullbackBars();n++){
            int k=i-n-1;if(k<1||i-k>c.pullbackLookbackBars()+1)continue;
            if(bars.get(k).high()<bars.get(k-1).high())continue;
            boolean valid=true;for(int j=k+1;j<i;j++)if(!(bars.get(j).close()<bars.get(j-1).close()&&bars.get(j).high()<=bars.get(j-1).high())){valid=false;break;}
            if(valid){peak=k;start=k+1;break;}
        }
        if(peak<0)return null;
        var a=new Setup();a.symbol=symbol;a.signalDate=date(recovery);a.recoveryBarDate=a.signalDate;a.weekStart=monday(a.signalDate);
        a.referenceWeekEnd=a.weekStart.minusDays(1);a.setupId=symbol+":"+a.signalDate+":"+date(bars.get(peak));
        a.signalIndex=i;a.pullbackPeakIndex=peak;a.pullbackStartDate=date(bars.get(start));a.pullbackEndDate=date(prev);
        a.pullbackLow=bars.subList(start,i).stream().mapToDouble(HistoricalBar::low).min().orElseThrow();a.recoveryBarHigh=recovery.high();
        a.entryValidityBars=c.entryValidityBars();a.initialStop=a.pullbackLow-c.buffer(a.pullbackLow,c.stopBufferMode(),c.stopBuffer());
        a.momentumValue=i>=c.momentumLookback()?recovery.close()/bars.get(i-c.momentumLookback()).close()-1:null;
        a.trendContext=a.momentumValue==null?"UNAVAILABLE":a.momentumValue>0?"MOMENTUM_UP":"MOMENTUM_DOWN_OR_FLAT";
        a.pullbackAmplitudePct=(bars.get(peak).high()-a.pullbackLow)/bars.get(peak).high()*100;
        a.volatilityPct=bars.subList(Math.max(0,i-19),i+1).stream().mapToDouble(b->(b.high()-b.low())/b.close()*100).average().orElse(0);
        if(reference==null||reference.previousWeekHigh()==null){reject(a,"REJECTED","INCOMPLETE_REFERENCE_WEEK");return a;}
        a.previousWeekHigh=reference.previousWeekHigh();
        a.entryTrigger=(c.mode()==Mode.WEEKLY_HIGH_BREAKOUT?a.previousWeekHigh:recovery.high());
        a.entryTrigger+=c.buffer(a.entryTrigger,c.entryBufferMode(),c.entryBuffer());
        double entry=execution(a.entryTrigger,c.slippagePercentPerSide(),true);a.theoreticalEntryRisk=entry-a.initialStop;
        a.theoreticalRewardRisk=a.theoreticalEntryRisk>0?(c.mode()==Mode.WEEKLY_HIGH_BREAKOUT?c.breakoutTargetR():(a.previousWeekHigh-entry)/a.theoreticalEntryRisk):null;
        a.distanceFromLevelPct=(a.previousWeekHigh-recovery.close())/a.previousWeekHigh*100;
        boolean touched=false;for(int j=i;j>=0&&monday(date(bars.get(j))).equals(a.weekStart);j--)if(bars.get(j).high()>=a.previousWeekHigh){touched=true;break;}
        if(touched)reject(a,"REJECTED","TARGET_ALREADY_TOUCHED");
        else if(c.mode()!=Mode.WEEKLY_HIGH_BREAKOUT&&recovery.close()>=a.previousWeekHigh)reject(a,"REJECTED","PRICE_NOT_BELOW_LEVEL");
        else if(bars.subList(0,i+1).stream().filter(b->monday(date(b)).equals(a.weekStart)).count()!=ResearchSessionCalendar.weekSessions(a.weekStart).stream().filter(d->!d.isAfter(a.signalDate)).count())reject(a,"REJECTED","INCOMPLETE_CURRENT_WEEK_TO_SIGNAL");
        else if(hasMissingSessions(bars,Math.max(0,i-Math.max(c.momentumLookback(),c.pullbackLookbackBars()+1)),i))reject(a,"REJECTED","MISSING_DAILY_SESSION");
        else if(c.momentumFilter()&&(a.momentumValue==null||a.momentumValue<=0))reject(a,"REJECTED","MOMENTUM_NOT_UP");
        else if(!(a.initialStop>0&&a.theoreticalEntryRisk>0))reject(a,"REJECTED","INVALID_STOP");
        else if(c.mode()!=Mode.WEEKLY_HIGH_BREAKOUT&&a.entryTrigger>=a.previousWeekHigh)reject(a,"REJECTED","TRIGGER_AT_OR_ABOVE_TARGET");
        a.signalEligible=a.setupStatus.equals("PENDING");return a;
    }
    private static Setup breakout(WeeklyTteConfiguration c,String symbol,List<HistoricalBar> bars,int index,WeeklyLevel level){
        var a=new Setup();a.symbol=symbol;a.weekStart=monday(date(bars.get(index)));a.referenceWeekEnd=a.weekStart.minusDays(1);
        a.signalIndex=index-1;a.signalDate=date(bars.get(index-1));a.recoveryBarDate=a.signalDate;a.recoveryBarHigh=bars.get(index-1).high();
        a.setupId=symbol+":"+a.weekStart+":BREAKOUT";a.entryValidityBars=c.entryValidityBars();a.trendContext=level==null?"UNAVAILABLE":level.trendContext();
        if(level==null||level.previousWeekHigh()==null){reject(a,"REJECTED","INCOMPLETE_REFERENCE_WEEK");return a;}
        a.previousWeekHigh=level.previousWeekHigh();
        a.pullbackLow=bars.stream().filter(b->!date(b).isBefore(a.weekStart.minusWeeks(1))&&date(b).isBefore(a.weekStart)).mapToDouble(HistoricalBar::low).min().orElseThrow();
        a.pullbackStartDate=a.weekStart.minusWeeks(1);a.pullbackEndDate=a.signalDate;
        a.initialStop=a.pullbackLow-c.buffer(a.pullbackLow,c.stopBufferMode(),c.stopBuffer());
        a.entryTrigger=a.previousWeekHigh+c.buffer(a.previousWeekHigh,c.entryBufferMode(),c.entryBuffer());
        a.theoreticalEntryRisk=execution(a.entryTrigger,c.slippagePercentPerSide(),true)-a.initialStop;a.theoreticalRewardRisk=c.breakoutTargetR();
        if(a.initialStop<=0)reject(a,"REJECTED","INVALID_STOP");a.signalEligible=a.setupStatus.equals("PENDING");return a;
    }
    private static double fill(WeeklyTteConfiguration c,Position p,HistoricalBar b,Exit e){
        boolean target=e.reason().equals("TAKE_PROFIT")||e.reason().equals("AMBIGUOUS_ENTRY_FAVORABLE");
        long q=p.remaining;
        if(target&&c.mode()==Mode.WEEKLY_HIGH_TTE_PARTIAL&&!p.firstPartial&&p.remaining>1){q=Math.max(1,(long)Math.floor(p.quantity*c.partialClosePercent()/100));q=Math.min(q,p.remaining-1);p.firstPartial=true;}
        double exec=execution(e.price(),c.slippagePercentPerSide(),false);
        double factor=c.splitCosts().factor(p.setup.symbol,b).doubleValue();
        double fee=PatternV2SplitCosts.commission(c.commission(),bd(factor)).charge(q,bd(exec)).doubleValue();
        p.fills.add(new Fill(date(b),q,e.price(),exec,fee,(e.price()-exec)*q,e.reason(),e.gap(),e.gapPct(),e.gapR()));p.remaining-=q;
        if(p.remaining>0&&target)p.target=Math.max(p.setup.previousWeekHigh+c.tickSize(),p.entry+p.risk*c.residualTargetR());
        return exec*q-fee;
    }
}
