package it.andreademartini.trading.backtest.weeklytte;

import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.backtest.weeklytte.WeeklyTteRun.WeeklyLevel;
import it.andreademartini.trading.risk.*;
import it.andreademartini.trading.backtest.patternv2.PatternV2SplitCosts;
import it.andreademartini.trading.backtest.patternv3.ResearchSessionCalendar;
import java.time.*;
import java.util.*;
import static it.andreademartini.trading.backtest.weeklytte.WeeklyTteConfiguration.*;
import static it.andreademartini.trading.backtest.weeklytte.WeeklyTteRun.*;
import static it.andreademartini.trading.backtest.weeklytte.WeeklyTteLevels.*;
import static it.andreademartini.trading.backtest.weeklytte.WeeklyTteEngine.bd;
import static it.andreademartini.trading.backtest.weeklytte.WeeklyTteEngine.execution;

/** Version 2 research only. The legacy engine remains available for exact old-snapshot replay. */
final class HistoricalWeeklyTteEngine {
    private final PositionSizingService sizing;
    HistoricalWeeklyTteEngine(PositionSizingService sizing){this.sizing=sizing;}
    private static final class Level {
        String id,symbol,type;double price,high,low;LocalDate available,touch;
        List<LocalDate> origins=new ArrayList<>();boolean uncertain;
        HistoricalLevel view(){return new HistoricalLevel(id,symbol,type,price,List.copyOf(origins),available,high,low,uncertain?"UNKNOWN_DATA_GAP":touch==null?"ACTIVE":"REACHED",uncertain?null:touch);}
    }
    private static final class Position {
        Setup a;int sign,index,holding=1;long quantity,remaining;double entry,base,fee,stop,target,risk,equity,before,after,factor;
        boolean partial,ambiguous;List<Fill> fills=new ArrayList<>();
        double value(double mark){return sign==1?remaining*mark:remaining*(2*entry-mark);}
        Trade trade(WeeklyTteConfiguration c){
            long exited=quantity-remaining;double costs=fills.stream().mapToDouble(Fill::commission).sum();
            double pnl=fills.stream().mapToDouble(f->sign*(f.executionPrice()-entry)*f.quantity()-f.commission()).sum()-fee;
            double slip=sign*(entry-base)*quantity+fills.stream().mapToDouble(Fill::slippage).sum();
            Fill last=fills.isEmpty()?null:fills.getLast();
            Double exitBase=exited==0?null:fills.stream().mapToDouble(f->f.basePrice()*f.quantity()).sum()/exited;
            Double exitExec=exited==0?null:fills.stream().mapToDouble(f->f.executionPrice()*f.quantity()).sum()/exited;
            return new Trade(a.tradeId,a.setupId,a.symbol,c.mode().name(),a.levelPrice,a.entryTrigger,a.pullbackLow,stop,a.realizedRewardRisk,
                    a.signalDate,a.entryDate,remaining==0?last.date():null,base,entry,exitBase,exitExec,quantity,remaining,fee,costs,slip,pnl,
                    pnl/(risk*quantity),holding,remaining>0?"OPEN":last.reason(),ambiguous,equity,before,after,risk*quantity,factor,
                    last==null?"NONE":last.gapExitType(),last==null?0:last.gapSizePercent(),last==null?0:last.gapSizeR(),List.copyOf(fills),
                    a.direction,a.levelId,a.levelType,a.levelPrice,a.originWeeks);
        }
    }
    private static int sign(Setup a){return "SHORT".equals(a.direction)?-1:1;}
    private static String key(String symbol,int sign){return symbol+":"+(sign==1?"LONG":"SHORT");}
    private static double favorable(HistoricalBar b,int sign){return sign==1?b.high():b.low();}
    private static double adverse(HistoricalBar b,int sign){return sign==1?b.low():b.high();}
    private static boolean beyond(double price,double level,int sign){return sign*(price-level)>=0;}
    private static void reject(Setup a,String status,String reason){a.setupStatus=status;a.rejectionReason=reason;}
    private static LocalDate nextSession(LocalDate d){do{d=d.plusDays(1);}while(!ResearchSessionCalendar.isSession(d));return d;}
    private static boolean contiguous(List<HistoricalBar> bars,int from,int to){
        for(int j=from+1;j<=to;j++)if(!nextSession(date(bars.get(j-1))).equals(date(bars.get(j))))return false;return true;
    }
    private static Map<LocalDate,List<Level>> schedule(String symbol,List<HistoricalBar> bars){
        var weeks=new TreeMap<LocalDate,List<HistoricalBar>>();bars.forEach(b->weeks.computeIfAbsent(monday(date(b)),k->new ArrayList<>()).add(b));
        var result=new TreeMap<LocalDate,List<Level>>();
        for(var w:weeks.entrySet())if(complete(w.getKey(),w.getValue())){
            double high=w.getValue().stream().mapToDouble(HistoricalBar::high).max().orElseThrow();
            double low=w.getValue().stream().mapToDouble(HistoricalBar::low).min().orElseThrow();
            var available=nextSession(date(w.getValue().getLast()));
            for(String type:List.of("HIGH","LOW")){
                var l=new Level();l.symbol=symbol;l.type=type;l.price=type.equals("HIGH")?high:low;l.high=high;l.low=low;
                l.origins.add(w.getKey());l.available=available;l.id=symbol+":"+type+":"+w.getKey();
                result.computeIfAbsent(available,k->new ArrayList<>()).add(l);
            }
        }return result;
    }
    private static Level nearest(List<Level> levels,String symbol,int sign,double price){
        return nearest(levels,symbol,sign,price,false);
    }
    private static Level nearest(List<Level> levels,String symbol,int sign,double price,boolean includeEqual){
        return levels.stream().filter(l->l.symbol.equals(symbol)&&l.touch==null&&l.type.equals(sign==1?"HIGH":"LOW")&&(includeEqual?sign*(l.price-price)>=0:sign*(l.price-price)>0))
                .min(Comparator.comparingDouble((Level l)->sign*(l.price-price)).thenComparing(l->l.id)).orElse(null);
    }
    private static void attach(Setup a,Level l,int sign){
        a.direction=sign==1?"LONG":"SHORT";a.levelId=l.id;a.levelType=l.type;a.levelPrice=l.price;a.previousWeekHigh=l.price;
        a.originWeeks=List.copyOf(l.origins);a.referenceWeekEnd=l.origins.getFirst().plusDays(6);
    }
    private static Setup breakout(WeeklyTteConfiguration c,String symbol,List<HistoricalBar> bars,int i,Level l,int sign){
        var a=new Setup();a.symbol=symbol;attach(a,l,sign);a.signalIndex=i-1;a.signalDate=date(bars.get(i-1));a.weekStart=monday(a.signalDate);
        a.setupId=key(symbol,sign)+":"+a.signalDate+":"+l.id+":BREAKOUT";a.entryValidityBars=c.entryValidityBars();
        a.pullbackStartDate=l.origins.getFirst();a.pullbackEndDate=a.referenceWeekEnd;a.recoveryBarDate=a.signalDate;
        a.pullbackLow=sign==1?l.low:l.high;a.recoveryBarHigh=favorable(bars.get(i-1),sign);
        a.initialStop=a.pullbackLow-sign*c.buffer(a.pullbackLow,c.stopBufferMode(),c.stopBuffer());
        a.entryTrigger=l.price+sign*c.buffer(l.price,c.entryBufferMode(),c.entryBuffer());
        a.theoreticalEntryRisk=sign*(execution(a.entryTrigger,c.slippagePercentPerSide(),sign==1)-a.initialStop);
        a.theoreticalRewardRisk=c.breakoutTargetR();a.trendContext="ORIGIN_WEEK_LEVEL";
        a.signalEligible=true;if(a.initialStop<=0||a.entryTrigger<=0||a.theoreticalEntryRisk<=0){reject(a,"REJECTED","INVALID_STOP_OR_TRIGGER");a.signalEligible=false;}
        return a;
    }
    private static Setup detect(WeeklyTteConfiguration c,String symbol,List<HistoricalBar> bars,int i,Level l,int sign){
        if(i<2)return null;var b=bars.get(i);var prev=bars.get(i-1);
        if(!(sign*(b.close()-b.open())>0&&sign*(b.close()-prev.close())>0))return null;
        int peak=-1,start=-1;
        for(int n=c.minPullbackBars();n<=c.maxPullbackBars();n++){
            int k=i-n-1;if(k<1||i-k>c.pullbackLookbackBars()+1)continue;
            if(sign*(favorable(bars.get(k),sign)-favorable(bars.get(k-1),sign))<0)continue;
            boolean valid=true;for(int j=k+1;j<i;j++)if(!(sign*(bars.get(j).close()-bars.get(j-1).close())<0
                    &&sign*(favorable(bars.get(j),sign)-favorable(bars.get(j-1),sign))<=0)){valid=false;break;}
            if(valid){peak=k;start=k+1;break;}
        }
        if(peak<0)return null;
        var a=new Setup();a.symbol=symbol;a.signalIndex=i;a.signalDate=date(b);a.recoveryBarDate=a.signalDate;a.weekStart=monday(a.signalDate);
        a.direction=sign==1?"LONG":"SHORT";a.pullbackPeakIndex=peak;a.pullbackStartDate=date(bars.get(start));a.pullbackEndDate=date(prev);
        a.setupId=key(symbol,sign)+":"+a.signalDate+":"+(l==null?"NONE":l.id)+":"+date(bars.get(peak));
        a.entryValidityBars=c.entryValidityBars();a.recoveryBarHigh=favorable(b,sign);
        a.pullbackLow=sign==1?bars.subList(start,i).stream().mapToDouble(HistoricalBar::low).min().orElseThrow():bars.subList(start,i).stream().mapToDouble(HistoricalBar::high).max().orElseThrow();
        a.initialStop=a.pullbackLow-sign*c.buffer(a.pullbackLow,c.stopBufferMode(),c.stopBuffer());
        a.entryTrigger=a.recoveryBarHigh+sign*c.buffer(a.recoveryBarHigh,c.entryBufferMode(),c.entryBuffer());
        a.momentumValue=i>=c.momentumLookback()?b.close()/bars.get(i-c.momentumLookback()).close()-1:null;
        a.trendContext=a.momentumValue==null?"UNAVAILABLE":a.momentumValue>0?"MOMENTUM_UP":"MOMENTUM_DOWN_OR_FLAT";
        a.pullbackAmplitudePct=Math.abs(favorable(bars.get(peak),sign)-a.pullbackLow)/favorable(bars.get(peak),sign)*100;
        a.volatilityPct=bars.subList(Math.max(0,i-19),i+1).stream().mapToDouble(x->(x.high()-x.low())/x.close()*100).average().orElse(0);
        if(l==null){reject(a,"REJECTED","NO_ACTIVE_LEVEL");return a;}attach(a,l,sign);
        a.theoreticalEntryRisk=sign*(execution(a.entryTrigger,c.slippagePercentPerSide(),sign==1)-a.initialStop);
        a.theoreticalRewardRisk=a.theoreticalEntryRisk>0?sign*(l.price-execution(a.entryTrigger,c.slippagePercentPerSide(),sign==1))/a.theoreticalEntryRisk:null;
        a.distanceFromLevelPct=sign*(l.price-b.close())/l.price*100;
        if(!contiguous(bars,Math.max(0,i-Math.max(c.momentumLookback(),c.pullbackLookbackBars()+1)),i))reject(a,"REJECTED","MISSING_DAILY_SESSION");
        else if(c.momentumFilter()&&(a.momentumValue==null||sign*a.momentumValue<=0))reject(a,"REJECTED","MOMENTUM_WRONG_DIRECTION");
        else if(a.initialStop<=0||a.entryTrigger<=0||a.theoreticalEntryRisk<=0)reject(a,"REJECTED","INVALID_STOP_OR_TRIGGER");
        else if(beyond(a.entryTrigger,l.price,sign))reject(a,"REJECTED","TRIGGER_AT_OR_BEYOND_TARGET");
        a.signalEligible=a.setupStatus.equals("PENDING");return a;
    }
    private static WeeklyTteEngine.Exit exit(HistoricalBar b,Position p,int max,boolean openOnly){
        if(beyond(b.open(),p.stop,-p.sign))return new WeeklyTteEngine.Exit(b.open(),"STOP_LOSS","GAP_STOP",p.sign*(b.open()-p.stop)/p.stop*100,p.sign*(b.open()-p.stop)/p.risk);
        if(beyond(b.open(),p.target,p.sign))return new WeeklyTteEngine.Exit(b.open(),"TAKE_PROFIT","GAP_TARGET",p.sign*(b.open()-p.target)/p.target*100,p.sign*(b.open()-p.target)/p.risk);
        if(openOnly)return null;
        if(beyond(adverse(b,p.sign),p.stop,-p.sign)&&beyond(favorable(b,p.sign),p.target,p.sign)){p.ambiguous=true;p.a.intrabarAmbiguous=true;}
        if(beyond(adverse(b,p.sign),p.stop,-p.sign))return new WeeklyTteEngine.Exit(p.stop,"STOP_LOSS","NONE",0,0);
        if(beyond(favorable(b,p.sign),p.target,p.sign))return new WeeklyTteEngine.Exit(p.target,"TAKE_PROFIT","NONE",0,0);
        return p.holding>=max?new WeeklyTteEngine.Exit(b.close(),"TIMEOUT","NONE",0,0):null;
    }
    private static double fill(WeeklyTteConfiguration c,Position p,HistoricalBar b,WeeklyTteEngine.Exit e){
        boolean target=e.reason().equals("TAKE_PROFIT")||e.reason().equals("AMBIGUOUS_ENTRY_FAVORABLE");long q=p.remaining;
        if(target&&c.mode()==Mode.WEEKLY_HIGH_TTE_PARTIAL&&!p.partial&&q>1){q=Math.min(q-1,Math.max(1,(long)Math.floor(p.quantity*c.partialClosePercent()/100)));p.partial=true;}
        double exec=execution(e.price(),c.slippagePercentPerSide(),p.sign==-1);
        double factor=c.splitCosts().factor(p.a.symbol,b).doubleValue();double fee=PatternV2SplitCosts.commission(c.commission(),bd(factor)).charge(q,bd(exec)).doubleValue();
        p.fills.add(new Fill(date(b),q,e.price(),exec,fee,p.sign*(e.price()-exec)*q,e.reason(),e.gap(),e.gapPct(),e.gapR()));p.remaining-=q;
        if(p.remaining>0&&target)p.target=p.sign==1?Math.max(p.a.levelPrice+c.tickSize(),p.entry+p.risk*c.residualTargetR()):Math.min(p.a.levelPrice-c.tickSize(),p.entry-p.risk*c.residualTargetR());
        // SHORT sale proceeds stay restricted. Release collateral plus realized P&L only on cover.
        return (p.sign==1?exec:2*p.entry-exec)*q-fee;
    }
    WeeklyTteRun simulate(WeeklyTteConfiguration c,Map<String,List<HistoricalBar>> input,String datasetId,String hash){
        c.validate();var data=new TreeMap<String,List<HistoricalBar>>();var indices=new HashMap<String,Map<LocalDate,Integer>>();
        var schedules=new HashMap<String,Map<LocalDate,List<Level>>>();var days=new TreeSet<LocalDate>();var descriptive=new ArrayList<WeeklyLevel>();
        for(String symbol:c.symbols()){
            var bars=input.getOrDefault(symbol,List.of()).stream().filter(b->!date(b).isAfter(c.endDate())).sorted(Comparator.comparing(HistoricalBar::dateTime)).toList();
            LocalDate prior=null;var ix=new HashMap<LocalDate,Integer>();
            for(int i=0;i<bars.size();i++){var b=bars.get(i);var d=date(b);
                if(b.interval()!=BarInterval.DAILY||!ResearchSessionCalendar.isSession(d)||d.equals(prior)||!Double.isFinite(b.high())||!Double.isFinite(b.low())||!Double.isFinite(b.open())||!Double.isFinite(b.close())||b.low()<=0||b.high()<Math.max(b.open(),b.close())||b.low()>Math.min(b.open(),b.close()))throw new IllegalArgumentException("Invalid DAILY bar: "+symbol+" "+d);
                prior=d;ix.put(d,i);days.add(d);
            }data.put(symbol,bars);indices.put(symbol,ix);schedules.put(symbol,schedule(symbol,bars));descriptive.addAll(calculate(symbol,bars,c.startDate(),c.endDate()));
        }
        if(days.stream().noneMatch(d->!d.isBefore(c.startDate())))throw new IllegalArgumentException("No DAILY bars in requested period");
        var levels=new ArrayList<Level>();var pending=new TreeMap<String,Setup>();var setups=new ArrayList<Setup>();var consumed=new HashSet<String>();
        var positions=new TreeMap<String,Position>();var trades=new ArrayList<Trade>();var curve=new ArrayList<Equity>();var marks=new HashMap<String,Double>();
        var gaps=new ArrayList<Map<String,Object>>();double cash=c.initialCapital();
        for(var day:days){
            for(String symbol:c.symbols())for(var activation:new ArrayList<>(schedules.get(symbol).keySet()).stream().filter(d->!d.isAfter(day)).sorted().toList())for(var incoming:schedules.get(symbol).remove(activation)){
                var same=levels.stream().filter(l->l.touch==null&&l.symbol.equals(symbol)&&l.type.equals(incoming.type)&&Double.compare(l.price,incoming.price)==0).findFirst();
                if(same.isPresent())same.get().origins.addAll(incoming.origins);else levels.add(incoming);
            }
            // A missing observed session can have consumed a level. Do not claim it survived an unknown path.
            for(String symbol:c.symbols()){
                Integer i=indices.get(symbol).get(day);if(i==null||i==0)continue;
                if(!nextSession(date(data.get(symbol).get(i-1))).equals(day)){
                    var position=positions.get(symbol);if(position!=null){position.ambiguous=true;position.a.intrabarAmbiguous=true;gaps.add(Map.of("symbol",symbol,"date",day,"reason","OPEN_POSITION_PATH_UNKNOWN_DURING_DATA_GAP"));}
                    var ids=new ArrayList<String>();for(var l:levels)if(l.symbol.equals(symbol)&&l.touch==null&&l.available.isBefore(day)){l.touch=day;l.uncertain=true;ids.add(l.id);}
                    if(!ids.isEmpty())gaps.add(Map.of("symbol",symbol,"date",day,"reason","MISSING_SESSION_LEVEL_STATE_UNKNOWN","levelIds",ids));
                    pending.values().stream().filter(a->a.symbol.equals(symbol)&&a.setupStatus.equals("PENDING")).forEach(a->reject(a,"REJECTED","MISSING_DAILY_SESSION"));
                }
            }
            boolean trading=!day.isBefore(c.startDate());
            if(trading){
                pending.entrySet().removeIf(e->!e.getValue().setupStatus.equals("PENDING"));
                if(c.mode()==Mode.WEEKLY_HIGH_BREAKOUT)for(String s:c.symbols()){
                    Integer i=indices.get(s).get(day);if(i==null||i<1||positions.containsKey(s))continue;
                    for(int direction:new int[]{1,-1})if(direction==1?c.longEnabled():c.shortEnabled()){
                        String k=key(s,direction);if(pending.containsKey(k))continue;
                        var l=nearest(levels,s,direction,data.get(s).get(i-1).close(),true);if(l==null)continue;
                        var a=breakout(c,s,data.get(s),i,l,direction);setups.add(a);if(a.signalEligible)pending.put(k,a);
                    }
                }
                // Opening exits are known before entries. Intraday releases are deferred until all entries are processed.
                for(String s:new ArrayList<>(positions.keySet())){
                    Integer i=indices.get(s).get(day);if(i==null)continue;var p=positions.get(s);p.holding++;var b=data.get(s).get(i);
                    var e=exit(b,p,c.maxHoldingDays(),true);if(e!=null){cash+=fill(c,p,b,e);if(p.remaining==0){trades.add(p.trade(c));positions.remove(s);}}
                }
                double equity=cash+positions.values().stream().mapToDouble(p->p.value(marks.getOrDefault(p.a.symbol,p.entry))).sum();
                double gross=notional(positions,marks,0),longs=notional(positions,marks,1),shorts=notional(positions,marks,-1),deferred=0;
                var entryKeys=new ArrayList<>(pending.keySet());
                entryKeys.sort(Comparator.comparingInt((String k)->{var a=pending.get(k);var i=indices.get(a.symbol).get(day);return i!=null&&beyond(data.get(a.symbol).get(i).open(),a.entryTrigger,sign(a))?0:1;}).thenComparing(k->k));
                for(String k:entryKeys){
                    var a=pending.get(k);String s=a.symbol;Integer i=indices.get(s).get(day);if(i==null)continue;var b=data.get(s).get(i);int direction=sign(a),age=i-a.signalIndex;
                    if(!a.setupStatus.equals("PENDING")){pending.remove(k);continue;}
                    if(age<1)continue;
                    if(age>c.entryValidityBars()){reject(a,"EXPIRED","SETUP_VALIDITY");pending.remove(k);continue;}
                    if(!contiguous(data.get(s),a.signalIndex,i)){reject(a,"REJECTED","MISSING_DAILY_SESSION");pending.remove(k);continue;}
                    if(c.mode()==Mode.WEEKLY_HIGH_BREAKOUT&&beyond(b.open(),a.levelPrice,direction)&&!beyond(b.open(),a.entryTrigger,direction)){
                        reject(a,"LEVEL_REACHED_BEFORE_TRIGGER","OPEN_TOUCH_BEFORE_BUFFERED_TRIGGER");pending.remove(k);continue;
                    }
                    if(c.mode()!=Mode.WEEKLY_HIGH_BREAKOUT&&beyond(b.open(),a.levelPrice,direction)){reject(a,"TARGET_REACHED_BEFORE_ENTRY","OPEN_AT_OR_BEYOND_TARGET");pending.remove(k);continue;}
                    if(!beyond(favorable(b,direction),a.entryTrigger,direction))continue;
                    a.orderTriggered=true;a.entryDate=day;a.entryBasePrice=direction==1?Math.max(b.open(),a.entryTrigger):Math.min(b.open(),a.entryTrigger);
                    a.gapEntry=direction*(b.open()-a.entryTrigger)>0;a.entryExecutionPrice=execution(a.entryBasePrice,c.slippagePercentPerSide(),direction==1);
                    double risk=direction*(a.entryExecutionPrice-a.initialStop),target=c.mode()==Mode.WEEKLY_HIGH_BREAKOUT?a.entryExecutionPrice+direction*risk*c.breakoutTargetR():a.levelPrice;
                    double rr=direction*(target-a.entryExecutionPrice)/risk;a.realizedRewardRisk=Double.isFinite(rr)?rr:null;
                    String invalid=!(risk>0&&Double.isFinite(risk))?"INVALID_STOP":target<=0||direction*(target-a.entryExecutionPrice)<=0?"ENTRY_AT_OR_BEYOND_TARGET":rr<c.minRewardRiskRatio()?"INSUFFICIENT_REWARD_RISK":positions.containsKey(s)?"POSITION_ALREADY_OPEN":null;
                    if(invalid!=null){reject(a,"REJECTED",invalid);pending.remove(k);continue;}
                    var touched=levels.stream().filter(l->l.symbol.equals(s)&&l.touch==null&&beyond(l.type.equals("HIGH")?b.high():b.low(),l.price,l.type.equals("HIGH")?1:-1)).map(l->l.id).toList();
                    a.touchedLevelIds=touched;a.multipleLevelsTouched=touched.size()>1;
                    // Two opposing intraday triggers have no known sequence. Conservative policy skips the conflicting entries.
                    boolean opposing=pending.values().stream().anyMatch(other->other!=a&&other.symbol.equals(s)&&sign(other)!=direction&&beyond(favorable(b,-direction),other.entryTrigger,-direction));
                    a.intrabarAmbiguous=a.multipleLevelsTouched||opposing;
                    if(opposing&&!beyond(b.open(),a.entryTrigger,direction)&&c.ambiguousBarPolicy()==Ambiguity.CONSERVATIVE){
                        for(var other:pending.values())if(other.symbol.equals(s)){other.intrabarAmbiguous=true;other.orderTriggered=true;other.entryDate=day;other.touchedLevelIds=touched;other.multipleLevelsTouched=touched.size()>1;reject(other,"REJECTED","AMBIGUOUS_OPPOSING_INTRABAR_TRIGGERS");}
                        continue;
                    }
                    double limit=Math.min(equity*c.maxOrderValuePercent()/100,equity*c.maxPositionValuePercent()/100);
                    limit=Math.min(limit,Math.max(0,equity*(direction==1?c.maxLongNotionalPercent():c.maxShortNotionalPercent())/100-(direction==1?longs:shorts)));
                    if(!c.useLeverage())limit=Math.min(limit,Math.max(0,equity-gross));
                    double factor=c.splitCosts().factor(s,b).doubleValue();var fees=PatternV2SplitCosts.commission(c.commission(),bd(factor));long q=0;
                    if(equity>0&&limit>0)try{q=sizing.calculate(bd(equity),bd(c.riskPercent()),bd(a.entryExecutionPrice),bd(a.initialStop),bd(a.initialStop*c.slippagePercentPerSide()/100),bd(0),bd(limit),c.useLeverage(),Long.MAX_VALUE/4).quantity();}catch(RiskRejectedException|IllegalArgumentException ignored){}
                    long lo=0,hi=q;while(lo<hi){long mid=lo+(hi-lo)/2+(hi-lo)%2;double entryFee=fees.charge(mid,bd(a.entryExecutionPrice)).doubleValue(),exitFee=fees.charge(mid,bd(a.initialStop)).doubleValue();
                        boolean fits=(risk+a.initialStop*c.slippagePercentPerSide()/100)*mid+entryFee+exitFee<=equity*c.riskPercent()/100&&(c.useLeverage()||a.entryExecutionPrice*mid+entryFee<=cash);
                        if(fits)lo=mid;else hi=mid-1;
                    }q=lo;
                    if(q<1){reject(a,"REJECTED","INSUFFICIENT_CAPITAL_OR_RISK_BUDGET");pending.remove(k);continue;}
                    var p=new Position();p.a=a;p.sign=direction;p.index=i;p.quantity=q;p.remaining=q;p.entry=a.entryExecutionPrice;p.base=a.entryBasePrice;p.stop=a.initialStop;p.target=target;p.risk=risk;p.equity=equity;p.before=cash;p.factor=factor;p.ambiguous=a.intrabarAmbiguous;
                    p.fee=fees.charge(q,bd(p.entry)).doubleValue();cash-=q*p.entry+p.fee;p.after=cash;gross+=q*p.entry;if(direction==1)longs+=q*p.entry;else shorts+=q*p.entry;
                    a.tradeId=UUID.randomUUID().toString();a.setupStatus="ACCEPTED";positions.put(s,p);pending.remove(k);
                    if(!beyond(b.open(),a.entryTrigger,direction)){
                        boolean stop=beyond(adverse(b,direction),p.stop,-direction),hit=beyond(favorable(b,direction),p.target,direction);
                        if(stop||hit){a.intrabarAmbiguous=true;p.ambiguous=true;WeeklyTteEngine.Exit e=null;
                            if(c.ambiguousBarPolicy()==Ambiguity.CONSERVATIVE&&stop)e=new WeeklyTteEngine.Exit(p.stop,"AMBIGUOUS_ENTRY_ADVERSE","NONE",0,0);
                            else if(c.ambiguousBarPolicy()==Ambiguity.SENSITIVITY_FAVORABLE&&hit)e=new WeeklyTteEngine.Exit(p.target,"AMBIGUOUS_ENTRY_FAVORABLE","NONE",0,0);
                            if(e!=null){deferred+=fill(c,p,b,e);if(p.remaining==0){trades.add(p.trade(c));positions.remove(s);}}
                        }
                        if(positions.containsKey(s)&&beyond(b.close(),p.stop,-direction)){deferred+=fill(c,p,b,new WeeklyTteEngine.Exit(b.close(),"ENTRY_CLOSE_BEYOND_STOP","NONE",0,0));trades.add(p.trade(c));positions.remove(s);}
                        else if(positions.containsKey(s)&&c.maxHoldingDays()==1){deferred+=fill(c,p,b,new WeeklyTteEngine.Exit(b.close(),"TIMEOUT","NONE",0,0));trades.add(p.trade(c));positions.remove(s);}
                    }
                }
                cash+=deferred;
                for(String s:new ArrayList<>(positions.keySet())){
                    Integer i=indices.get(s).get(day);if(i==null)continue;var p=positions.get(s);var b=data.get(s).get(i);
                    if(p.index==i&&!beyond(b.open(),p.a.entryTrigger,p.sign))continue;
                    var e=exit(b,p,c.maxHoldingDays(),false);if(e!=null){cash+=fill(c,p,b,e);if(p.remaining==0){trades.add(p.trade(c));positions.remove(s);}}
                    if(positions.containsKey(s)&&p.holding>=c.maxHoldingDays()){cash+=fill(c,p,b,new WeeklyTteEngine.Exit(b.close(),"TIMEOUT","NONE",0,0));trades.add(p.trade(c));positions.remove(s);}
                }
            }
            // Consume all touched levels after evaluating orders that existed before this bar.
            for(var l:levels)if(l.touch==null){Integer i=indices.get(l.symbol).get(day);if(i!=null&&beyond(l.type.equals("HIGH")?data.get(l.symbol).get(i).high():data.get(l.symbol).get(i).low(),l.price,l.type.equals("HIGH")?1:-1))l.touch=day;}
            if(trading){
                pending.entrySet().removeIf(e->{var a=e.getValue();var l=levels.stream().filter(x->x.id.equals(a.levelId)).findFirst().orElseThrow();
                    if(!a.setupStatus.equals("PENDING"))return true;
                    if(l.touch!=null){reject(a,c.mode()==Mode.WEEKLY_HIGH_BREAKOUT?"LEVEL_REACHED_BEFORE_TRIGGER":"TARGET_REACHED_BEFORE_ENTRY","FIRST_TOUCH_CONSUMED_LEVEL");return true;}
                    Integer i=indices.get(a.symbol).get(day);if(i!=null&&i-a.signalIndex>=c.entryValidityBars()){reject(a,"EXPIRED","SETUP_VALIDITY");return true;}return false;
                });
            }
            for(String s:c.symbols()){
                Integer i=indices.get(s).get(day);if(i==null)continue;var bars=data.get(s);marks.put(s,bars.get(i).close());
                if(!trading||c.mode()==Mode.WEEKLY_HIGH_BREAKOUT||positions.containsKey(s))continue;
                for(int direction:new int[]{1,-1})if(direction==1?c.longEnabled():c.shortEnabled()){
                    String k=key(s,direction);if(pending.containsKey(k))continue;var l=nearest(levels,s,direction,bars.get(i).close());var a=detect(c,s,bars,i,l,direction);
                    if(a==null||!consumed.add(k+":"+a.levelId+":"+a.pullbackPeakIndex))continue;setups.add(a);if(a.signalEligible)pending.put(k,a);
                }
            }
            if(trading){double longs=notional(positions,marks,1),shorts=notional(positions,marks,-1);double eq=cash+positions.values().stream().mapToDouble(p->p.value(marks.getOrDefault(p.a.symbol,p.entry))).sum();
                double collateral=positions.values().stream().filter(p->p.sign==-1).mapToDouble(p->p.entry*p.remaining).sum();curve.add(new Equity(day,eq,cash,longs,eq>0?(longs+shorts)/eq*100:0,shorts,longs+shorts,collateral));}
        }
        pending.values().forEach(a->reject(a,"CENSORED_END_OF_DATA","PENDING_ORDER"));positions.values().forEach(p->trades.add(p.trade(c)));
        var analysis=WeeklyTteAnalysis.calculateWithOutcomes(c,descriptive,setups,trades,curve,data);
        analysis.put("historicalLevels",levels.stream().map(Level::view).toList());analysis.put("levelDataGaps",gaps);analysis.put("rulesVersion",HISTORICAL);
        var chart=new TreeMap<String,Object>();data.forEach((symbol,bars)->{var visible=bars.stream().filter(b->!date(b).isBefore(c.startDate())).toList();chart.put(symbol,visible.subList(Math.max(0,visible.size()-250),visible.size()).stream().map(b->Map.of("date",date(b),"open",b.open(),"high",b.high(),"low",b.low(),"close",b.close())).toList());});analysis.put("levelChartData",chart);
        analysis.put("levelSelection","Nearest active level per direction; fixed pending reference; duplicate prices share earliest active origin for Breakout stop. One position per symbol. Opposing intrabar triggers: conservative skips both; sensitivity uses LONG-first flagged scenario. Opening triggers precede intraday triggers.");
        analysis.put("historicalLevelCounts",Map.of("active",levels.stream().filter(l->l.touch==null).count(),"reached",levels.stream().filter(l->l.touch!=null&&!l.uncertain).count(),"unknownDataGap",levels.stream().filter(l->l.uncertain).count()));
        analysis.put("historicalLevelOutcomes",List.of("HIGH","LOW").stream().map(type->{var group=levels.stream().filter(l->l.type.equals(type)).toList();return Map.of("levelType",type,"reached",group.stream().filter(l->l.touch!=null&&!l.uncertain).count(),"activeCensoredAtEnd",group.stream().filter(l->l.touch==null).count(),"unknownDataGap",group.stream().filter(l->l.uncertain).count());}).toList());
        analysis.put("directionPerformance",List.of("LONG","SHORT").stream().map(d->{var m=WeeklyTteAnalysis.metrics(trades.stream().filter(t->t.direction().equals(d)&&t.remainingQuantity()==0).toList());m.put("direction",d);return m;}).toList());
        return new WeeklyTteRun(UUID.randomUUID().toString(),STRATEGY,Instant.now(),hash,datasetId,ResearchSessionCalendar.VERSION,c,List.copyOf(descriptive),List.copyOf(setups),List.copyOf(trades),List.copyOf(curve),analysis,
                List.of("RESEARCH ONLY: historical untouched weekly HIGH/LOW, LONG and SHORT. No broker orders.","SHORT proceeds restricted; full entry notional reserved, no borrow fees/locate simulation. Losses may exceed collateral on gaps.","DAILY ambiguity uses explicit scenarios; opposing intrabar triggers are skipped conservatively or LONG-first in sensitivity. Multiple levels touched are audited.","Levels use all available local history from 2010, not unavailable earlier history. Missing sessions invalidate uncertain level state; see levelDataGaps.","weeklyTouchOverall describes previous-week HIGH only; historicalLevels is the authoritative historical HIGH/LOW inventory."));
    }
    private static double notional(Map<String,Position> ps,Map<String,Double> marks,int direction){return ps.values().stream().filter(p->direction==0||p.sign==direction).mapToDouble(p->p.remaining*marks.getOrDefault(p.a.symbol,p.entry)).sum();}
}
