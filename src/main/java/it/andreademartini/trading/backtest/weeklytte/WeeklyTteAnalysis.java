package it.andreademartini.trading.backtest.weeklytte;

import java.util.*;
import java.util.function.Function;
import java.time.temporal.ChronoUnit;
import static it.andreademartini.trading.backtest.weeklytte.WeeklyTteRun.*;

/** Descriptive outcomes are never passed back into signal generation. Null means undefined, not zero. */
public final class WeeklyTteAnalysis {
    private WeeklyTteAnalysis(){}
    public static Map<String,Object> calculate(WeeklyTteConfiguration c,List<WeeklyLevel> levels,List<Setup> setups,List<Trade> trades,List<Equity> equity){
        var a=new LinkedHashMap<String,Object>();var closed=trades.stream().filter(t->t.remainingQuantity()==0).toList();
        var summary=metrics(closed);double ending=equity.isEmpty()?c.initialCapital():equity.getLast().equity();
        summary.put("finalEquity",ending);summary.put("initialCapital",c.initialCapital());
        double years=equity.size()<2?0:ChronoUnit.DAYS.between(equity.getFirst().date(),equity.getLast().date())/365.25;
        summary.put("actualDataStart",equity.isEmpty()?null:equity.getFirst().date().toString());summary.put("actualDataEnd",equity.isEmpty()?null:equity.getLast().date().toString());
        summary.put("cagrPercent",years>0&&ending>0?(Math.pow(ending/c.initialCapital(),1/years)-1)*100:null);
        double peak=c.initialCapital(),dd=0;for(var e:equity){peak=Math.max(peak,e.equity());dd=Math.max(dd,(peak-e.equity())/peak*100);}
        summary.put("maxDrawdownPercent",dd);summary.put("averageExposurePercent",equity.stream().mapToDouble(Equity::exposurePercent).average().orElse(0));
        summary.put("averageCapitalEmployed",equity.stream().mapToDouble(Equity::grossNotional).average().orElse(0));
        summary.put("commissions",trades.stream().mapToDouble(t->t.entryCommission()+t.exitCommissions()).sum());
        summary.put("slippageCosts",trades.stream().mapToDouble(Trade::slippageCosts).sum());summary.put("openTrades",trades.size()-closed.size());
        summary.put("ambiguousTrades",trades.stream().filter(Trade::intrabarAmbiguous).count());
        summary.put("gapStops",trades.stream().flatMap(t->t.exits().stream()).filter(f->f.gapExitType().equals("GAP_STOP")).count());
        summary.put("gapTargets",trades.stream().flatMap(t->t.exits().stream()).filter(f->f.gapExitType().equals("GAP_TARGET")).count());
        a.put("performance",summary);
        var audit=new LinkedHashMap<String,Object>();audit.put("setupsFound",setups.size());audit.put("accepted",setups.stream().filter(s->s.tradeId!=null).count());
        audit.put("rejected",setups.stream().filter(s->s.setupStatus.equals("REJECTED")).count());
        audit.put("expired",setups.stream().filter(s->s.setupStatus.startsWith("EXPIRED")).count());audit.put("trades",trades.size());
        audit.put("triggerActivationAmongEligibleSignals",probability(setups.stream().filter(s->s.signalEligible&&s.orderTriggered).count(),setups.stream().filter(s->s.signalEligible&&!s.setupStatus.equals("CENSORED_END_OF_DATA")).count()));
        audit.put("targetReachedBeforeEntry",setups.stream().filter(s->s.setupStatus.equals("TARGET_REACHED_BEFORE_ENTRY")).count());
        var targetTimes=trades.stream().filter(WeeklyTteAnalysis::target).mapToLong(t->{
            var first=t.exits().stream().filter(WeeklyTteAnalysis::targetFill).findFirst().orElseThrow();
            return t.entryDate().datesUntil(first.date().plusDays(1)).filter(it.andreademartini.trading.backtest.patternv3.ResearchSessionCalendar::isSession).count();
        }).average();
        audit.put("meanSessionsToTarget",targetTimes.isPresent()?targetTimes.getAsDouble():null);
        a.put("setupAnalysis",audit);
        a.put("rewardRiskGroups",groupTrades(closed,t->t.initialRewardRisk()<1?"<1R":t.initialRewardRisk()<1.5?"1.0-1.5R":t.initialRewardRisk()<2?"1.5-2.0R":t.initialRewardRisk()<3?"2.0-3.0R":t.initialRewardRisk()<=4?"3.0-4.0R":">4R"));
        a.put("symbols",groupTrades(closed,Trade::symbol));a.put("years",groupTrades(closed,t->""+t.entryDate().getYear()));
        a.put("periods",groupTrades(closed,t->period(t.entryDate().getYear())));
        var valid=levels.stream().filter(w->w.completedWeek()&&w.previousWeekHigh()!=null&&w.dataQualityStatus().equals("VALID")).toList();
        a.put("weeklyTouchOverall",weekly(valid));a.put("weeklyBySymbol",groupWeeks(valid,WeeklyLevel::symbol));
        a.put("weeklyByYear",groupWeeks(valid,w->""+w.weekStart().getYear()));a.put("weeklyByPeriod",groupWeeks(valid,w->period(w.weekStart().getYear())));
        a.put("weeklyByPriorTrend",groupWeeks(valid,WeeklyLevel::trendContext));
        a.put("weeklyInitialDistance",groupWeeks(valid,w->bucket(w.distanceFromLevelAtWeekOpenPct(),new double[]{0,1,2,5,10})));
        var tradeIndex=new HashMap<String,Trade>();trades.forEach(t->tradeIndex.put(t.setupId(),t));
        var conditioned=new LinkedHashMap<String,Object>();
        for(var key:signalDimensions().entrySet()){
            var groups=new TreeMap<String,List<Setup>>();setups.stream().filter(s->s.signalEligible).forEach(s->groups.computeIfAbsent(key.getValue().apply(s),k->new ArrayList<>()).add(s));
            var rows=new ArrayList<Map<String,Object>>();for(var g:groups.entrySet()){
                var row=new LinkedHashMap<String,Object>();row.put("group",g.getKey());row.put("setups",g.getValue().size());
                var resolved=g.getValue().stream().filter(s->!s.setupStatus.equals("CENSORED_END_OF_DATA")).toList();
                row.put("triggerProbability",probability(resolved.stream().filter(s->s.orderTriggered).count(),resolved.size()));
                var ts=g.getValue().stream().map(s->tradeIndex.get(s.setupId)).filter(Objects::nonNull).filter(t->t.remainingQuantity()==0).toList();
                row.put("scenarioTargetBeforeStopProbability",probability(ts.stream().filter(WeeklyTteAnalysis::target).count(),ts.size()));
                var unambiguous=ts.stream().filter(t->!t.intrabarAmbiguous()).toList();
                row.put("unambiguousTargetBeforeStopProbability",probability(unambiguous.stream().filter(WeeklyTteAnalysis::target).count(),unambiguous.size()));
                row.put("netPerformance",metrics(ts));row.put("censoredOpenTrades",g.getValue().stream().map(s->tradeIndex.get(s.setupId)).filter(Objects::nonNull).filter(t->t.remainingQuantity()>0).count());rows.add(row);
            }conditioned.put(key.getKey(),rows);
        }a.put("conditionalAnalysis",conditioned);return a;
    }
    /** Five distinct events. The shadow target observation deliberately continues after a simulated stop. */
    public static Map<String,Object> calculateWithOutcomes(WeeklyTteConfiguration c,List<WeeklyLevel> levels,List<Setup> setups,List<Trade> trades,List<Equity> equity,Map<String,List<it.andreademartini.trading.marketdata.HistoricalBar>> data){
        var result=calculate(c,levels,setups,trades,equity);var observations=new ArrayList<Map<String,Object>>();
        for(var t:trades){
            var bars=data.get(t.symbol());int index=-1;for(int i=0;i<bars.size();i++)if(WeeklyTteLevels.date(bars.get(i)).equals(t.entryDate())){index=i;break;}
            if(index<0)continue;
            boolean touched=false;Integer firstTouch=null;int available=Math.min(c.maxHoldingDays(),bars.size()-index);
            boolean shortTrade="SHORT".equals(t.direction());
            double target=c.mode()==WeeklyTteConfiguration.Mode.WEEKLY_HIGH_BREAKOUT?t.entryExecutionPrice()+(t.entryExecutionPrice()-t.initialStop())*c.breakoutTargetR():t.previousWeekHigh();
            for(int j=0;j<available;j++)if(shortTrade?bars.get(index+j).low()<=target:bars.get(index+j).high()>=target){touched=true;firstTouch=j+1;break;}
            boolean resolved=touched||available>=c.maxHoldingDays();
            var row=new LinkedHashMap<String,Object>();row.put("setupId",t.setupId());row.put("symbol",t.symbol());row.put("direction",t.direction());
            row.put("targetTouchedAfterEntryIgnoringStop",resolved?touched:null);row.put("shadowObservationSessions",available);row.put("firstTargetSession",firstTouch);
            row.put("entryBarSequenceAmbiguous",t.intrabarAmbiguous());row.put("targetBeforeStopScenario",t.remainingQuantity()==0?target(t):null);
            row.put("netPnL",t.netPnL());row.put("netR",t.netR());observations.add(row);
        }
        result.put("entryOutcomeObservations",observations);
        var outcomesBySetup=new HashMap<String,Map<String,Object>>();
        observations.forEach(o->outcomesBySetup.put((String)o.get("setupId"),o));
        // Add the stop-independent event to each existing signal-time conditioning bucket.
        var conditional=(Map<String,Object>)result.get("conditionalAnalysis");
        for(var dimension:signalDimensions().entrySet()){
            var rows=(List<Map<String,Object>>)conditional.get(dimension.getKey());
            for(var row:rows){
                var cohort=setups.stream().filter(s->s.signalEligible&&dimension.getValue().apply(s).equals(row.get("group")))
                        .map(s->outcomesBySetup.get(s.setupId)).filter(Objects::nonNull).toList();
                var observed=cohort.stream().filter(o->o.get("targetTouchedAfterEntryIgnoringStop")!=null).toList();
                row.put("targetAfterEntryIgnoringStopProbability",probability(observed.stream().filter(o->Boolean.TRUE.equals(o.get("targetTouchedAfterEntryIgnoringStop"))).count(),observed.size()));
                row.put("censoredTargetObservations",cohort.size()-observed.size());
            }
        }
        var events=new LinkedHashMap<String,Object>();events.put("1_weeklyLevelTouched",result.get("weeklyTouchOverall"));
        var eligible=setups.stream().filter(s->s.signalEligible&&!s.setupStatus.equals("CENSORED_END_OF_DATA")).toList();
        events.put(c.historical()?"2_entryStopActivatedAmongEligibleSignals":"2_buyStopActivatedAmongEligibleSignals",probability(eligible.stream().filter(s->s.orderTriggered).count(),eligible.size()));
        var resolved=observations.stream().filter(o->o.get("targetTouchedAfterEntryIgnoringStop")!=null).toList();
        events.put("3_targetAfterEntryIgnoringStopWithinHoldingHorizon",probability(resolved.stream().filter(o->Boolean.TRUE.equals(o.get("targetTouchedAfterEntryIgnoringStop"))).count(),resolved.size()));
        var closed=trades.stream().filter(t->t.remainingQuantity()==0).toList();events.put("4_targetBeforeStopScenario",probability(closed.stream().filter(WeeklyTteAnalysis::target).count(),closed.size()));
        events.put("5_netStrategyPerformance",result.get("performance"));
        events.put("interpretation","Event 3 is a descriptive fixed-session shadow observation including entry-bar high, regardless of simulated stop; null means censored. It is not a profitable trade probability. Event 4 depends on the recorded DAILY ambiguity scenario. Conditional buckets use only signalEligible setups; signal variables use past/current close data.");
        result.put("fiveDistinctEvents",events);
        return result;
    }
    private static Map<String,Function<Setup,String>> signalDimensions(){
        Map<String,Function<Setup,String>> keys=new LinkedHashMap<>();keys.put("symbol",s->s.symbol);keys.put("weekday",s->s.signalDate.getDayOfWeek().name());
        keys.put("distancePct",s->bucket(s.distanceFromLevelPct,new double[]{0,1,2,5,10}));keys.put("momentum",s->bucket(s.momentumValue,new double[]{0,.01,.03,.05}));
        keys.put("pullbackPct",s->bucket(s.pullbackAmplitudePct,new double[]{1,2,3,5,10}));keys.put("theoreticalRR",s->bucket(s.theoreticalRewardRisk,new double[]{1,1.5,2,3,4}));
        keys.put("volatilityPct",s->bucket(s.volatilityPct,new double[]{1,2,3,5}));keys.put("trend",s->s.trendContext);keys.put("marketRegime",s->s.marketRegime);return keys;
    }
    static String bucket(Double x,double[] limits){if(x==null||!Double.isFinite(x))return "UNAVAILABLE";for(double l:limits)if(x<l)return "<"+l;return ">="+limits[limits.length-1];}
    static String period(int year){return year<2010?"PRE_2010":year<2014?"2010-2013":year<2018?"2014-2017":year<2022?"2018-2021":"2022-LATEST";}
    private static boolean targetFill(Fill f){return f.reason().equals("TAKE_PROFIT")||f.reason().equals("AMBIGUOUS_ENTRY_FAVORABLE");}
    static boolean target(Trade t){return t.exits().stream().anyMatch(WeeklyTteAnalysis::targetFill);}
    static Map<String,Object> probability(long successes,long n){
        var m=new LinkedHashMap<String,Object>();m.put("successes",successes);m.put("sample",n);m.put("probability",n==0?null:(double)successes/n);m.put("smallSample",n<30);
        if(n>0){double p=(double)successes/n,z=1.96,den=1+z*z/n,center=(p+z*z/(2*n))/den,half=z*Math.sqrt(p*(1-p)/n+z*z/(4.0*n*n))/den;m.put("wilson95Low",Math.max(0,center-half));m.put("wilson95High",Math.min(1,center+half));}return m;
    }
    static Map<String,Object> metrics(List<Trade> ts){
        var m=new LinkedHashMap<String,Object>();double gain=ts.stream().mapToDouble(Trade::netPnL).filter(x->x>0).sum(),loss=-ts.stream().mapToDouble(Trade::netPnL).filter(x->x<0).sum();
        m.put("trades",ts.size());m.put("winRate",ts.isEmpty()?null:(double)ts.stream().filter(t->t.netPnL()>0).count()/ts.size());
        m.put("profitFactorNet",loss==0?null:gain/loss);m.put("netPnL",gain-loss);m.put("expectancyNetR",ts.isEmpty()?null:ts.stream().mapToDouble(Trade::netR).average().orElse(0));
        m.put("worstTradeR",ts.isEmpty()?null:ts.stream().mapToDouble(Trade::netR).min().orElse(0));
        m.put("gapStops",ts.stream().flatMap(t->t.exits().stream()).filter(f->f.gapExitType().equals("GAP_STOP")).count());
        m.put("gapTargets",ts.stream().flatMap(t->t.exits().stream()).filter(f->f.gapExitType().equals("GAP_TARGET")).count());
        m.put("smallSample",ts.size()<30);return m;
    }
    private static List<Map<String,Object>> groupTrades(List<Trade> ts,Function<Trade,String> key){var groups=new TreeMap<String,List<Trade>>();ts.forEach(t->groups.computeIfAbsent(key.apply(t),k->new ArrayList<>()).add(t));return groups.entrySet().stream().map(g->{var m=metrics(g.getValue());m.put("group",g.getKey());return m;}).toList();}
    private static Map<String,Object> weekly(List<WeeklyLevel> ws){
        var m=probability(ws.stream().filter(w->Boolean.TRUE.equals(w.previousWeekHighTouched())).count(),ws.size());
        var times=ws.stream().map(WeeklyLevel::firstTouchSessionIndex).filter(Objects::nonNull).sorted().toList();
        m.put("meanFirstTouchSession",times.isEmpty()?null:times.stream().mapToInt(Integer::intValue).average().orElse(0));
        m.put("medianFirstTouchSession",times.isEmpty()?null:(times.get((times.size()-1)/2)+times.get(times.size()/2))/2.0);return m;
    }
    private static List<Map<String,Object>> groupWeeks(List<WeeklyLevel> ws,Function<WeeklyLevel,String> key){var groups=new TreeMap<String,List<WeeklyLevel>>();ws.forEach(w->groups.computeIfAbsent(key.apply(w),k->new ArrayList<>()).add(w));return groups.entrySet().stream().map(g->{var m=weekly(g.getValue());m.put("group",g.getKey());return m;}).toList();}
}
