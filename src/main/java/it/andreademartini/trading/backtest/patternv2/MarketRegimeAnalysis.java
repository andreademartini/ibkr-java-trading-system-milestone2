package it.andreademartini.trading.backtest.patternv2;

import it.andreademartini.trading.backtest.patternv1.PatternV1Result.Trade;
import it.andreademartini.trading.marketdata.HistoricalBar;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import static it.andreademartini.trading.backtest.patternv2.MarketRegimeIndicators.Value;

/** Read-only postprocessor. Economic rows always refer to actual closed trades, never skipped signals. */
public final class MarketRegimeAnalysis {
    public static final BigDecimal ZERO_TOLERANCE=new BigDecimal("0.00000001");
    public static final List<String> REGIMES=List.of("SIDEWAYS_WEAK_TREND","TRANSITION","STRONG_UPTREND","STRONG_DOWNTREND","MIXED_UNCLASSIFIED","INSUFFICIENT_DATA");
    public static final List<String> BUCKETS=List.of("ADX < 15","15 <= ADX < 20","20 <= ADX < 25","25 <= ADX < 30","30 <= ADX < 40","ADX >= 40","INSUFFICIENT_DATA");
    public static final List<String> PERIODS=List.of("2010-2013","2014-2017","2018-2021","2022-END","OUTSIDE_PERIODS");
    public record Context(Value indicators,String marketRegime,String trendStrength,String trendDirection,String secondaryRegime,
            String withOrCounterTrend,String emaWithOrCounterTrend,Boolean closeAboveEMA20,Boolean closeAboveEMA50,Boolean closeAboveEMA200,
            Boolean ema20AboveEMA50,Boolean ema50AboveEMA200,Boolean plusDIAboveMinusDI) {}
    public record TradeRow(String tradeId,String symbol,String pattern,String direction,LocalDate signalDate,LocalDate entryDate,LocalDate exitDate,
            boolean closed,String period,Context context,BigDecimal netPnl,BigDecimal grossPnl,BigDecimal priceR,BigDecimal netR,
            String winLoss,String exitReason,String loserType,int holdingBars,Double entryStopDistanceAtr) {}
    public record SignalRow(String symbol,String pattern,String direction,LocalDate signalDate,int signalIndex,String status,String reason,String tradeId,Context context) {}
    public record Metrics(long trades,long winners,long losers,long breakeven,long openTrades,Double winRate,Double lossRate,
            double grossProfit,double grossLoss,double netPnl,Double pfNet,Double expectancyPriceR,Double expectancyNetR,
            Double averageWinNetR,Double averageLossNetR,Double averageADX,Double averageADXWinners,Double averageADXLosers,
            Double averageHoldingBars,long stopLossCount,long takeProfitCount,long timeoutCount,Double stopRate,Double timeoutRate,long netRSample,long adxSample,boolean smallSample) {}
    public record Group(String key,String symbol,String pattern,String direction,String regime,String period,Metrics metrics) {}
    public record Analysis(String methodology,List<TradeRow> trades,List<SignalRow> signals,List<Group> overview,List<Group> secondaryOverview,
            List<Group> adxBuckets,List<Group> trendDirection,List<Group> emaTrendDirection,List<Group> patternRegimes,List<Group> periods,List<Group> loserGroups) {}
    private MarketRegimeAnalysis() {}
    public static Context classify(Value v,String direction){
        if(v==null)v=new Value(null,null,null,null,null,null,null,null);
        String strength=v.adx14()==null?"INSUFFICIENT_DATA":v.adx14()<20?"WEAK":v.adx14()<25?"TRANSITION":"STRONG";
        String trend=v.plusDI14()==null||v.minusDI14()==null?"INSUFFICIENT_DATA":v.plusDI14()>v.minusDI14()?"UP":v.plusDI14()<v.minusDI14()?"DOWN":"MIXED";
        boolean up=greater(v.ema20(),v.ema50())==Boolean.TRUE&&greater(v.ema50(),v.ema200())==Boolean.TRUE&&trend.equals("UP");
        boolean down=less(v.ema20(),v.ema50())&&less(v.ema50(),v.ema200())&&trend.equals("DOWN");
        String regime=!v.complete()?"INSUFFICIENT_DATA":v.adx14()<20?"SIDEWAYS_WEAK_TREND":v.adx14()<25?"TRANSITION":up?"STRONG_UPTREND":down?"STRONG_DOWNTREND":"MIXED_UNCLASSIFIED";
        String secondary=v.adx14()==null||v.plusDI14()==null||v.minusDI14()==null?"INSUFFICIENT_DATA":v.adx14()<20?"WEAK_TREND":v.adx14()>=25&&trend.equals("UP")?"DIRECTIONAL_UP":v.adx14()>=25&&trend.equals("DOWN")?"DIRECTIONAL_DOWN":"TRANSITION_OR_MIXED";
        String relation=!MarketRegimeFilter.available(v)?"INSUFFICIENT_DATA":v.adx14()>=25?relation(direction,trend):"NEUTRAL_OR_MIXED";
        String emaTrend=v.ema50()==null||v.ema200()==null?"MIXED":v.ema50()>v.ema200()?"UP":v.ema50()<v.ema200()?"DOWN":"MIXED";
        return new Context(v,regime,strength,trend,secondary,relation,relation(direction,emaTrend),greater(v.close(),v.ema20()),greater(v.close(),v.ema50()),greater(v.close(),v.ema200()),greater(v.ema20(),v.ema50()),greater(v.ema50(),v.ema200()),greater(v.plusDI14(),v.minusDI14()));
    }
    private static Boolean greater(Double a,Double b){return a==null||b==null?null:a>b;}
    private static boolean less(Double a,Double b){return a!=null&&b!=null&&a<b;}
    static String relation(String trade,String trend){return !List.of("UP","DOWN").contains(trend)?"NEUTRAL_OR_MIXED":trade.equals("LONG")==trend.equals("UP")?"WITH_TREND":"COUNTER_TREND";}
    public static String bucket(Double adx){if(adx==null)return "INSUFFICIENT_DATA";double[] limits={15,20,25,30,40};for(int i=0;i<limits.length;i++)if(adx<limits[i])return BUCKETS.get(i);return BUCKETS.get(5);}
    public static String period(LocalDate date){int year=date.getYear();return year<2010?"OUTSIDE_PERIODS":year<2014?"2010-2013":year<2018?"2014-2017":year<2022?"2018-2021":"2022-END";}
    public static String outcome(BigDecimal pnl){return pnl.abs().compareTo(ZERO_TOLERANCE)<=0?"BREAKEVEN":pnl.signum()>0?"WINNER":"LOSER";}
    public static BigDecimal netR(Trade t){var risk=t.riskPerShare().multiply(BigDecimal.valueOf(t.quantity()));return risk.signum()>0?t.netPnl().divide(risk,MathContext.DECIMAL128):null;}
    public static Analysis calculate(PatternV2Run run){
        if(!run.configuration().marketRegimeAnalysisEnabled())throw new IllegalArgumentException("Market Regime Analysis is disabled");
        Map<String,List<Value>> indicators=new TreeMap<>();
        run.portfolio().priceSeries().forEach((symbol,bars)->{
            var stream=new MarketRegimeIndicators(run.configuration());var values=new ArrayList<Value>();
            for(var b:bars)values.add(stream.onBar(new HistoricalBar(b.index(),Instant.parse(b.timestamp()),b.label(),b.open(),b.high(),b.low(),b.close(),b.volume())));
            indicators.put(symbol,List.copyOf(values));
        });
        var rows=new ArrayList<TradeRow>();Map<String,TradeRow> traded=new HashMap<>();
        for(var t:run.portfolio().trades()){
            var context=classify(at(indicators,t.symbol(),t.signalIndex()),t.direction().name());
            var winLoss=t.closed()?outcome(t.netPnl()):"OPEN";
            String loserType=!winLoss.equals("LOSER")?null:stop(t.exitReason())?"STOP_LOSS":"TIMEOUT".equals(t.exitReason())?"TIMEOUT_LOSS":"OTHER_LOSS";
            var atr=context.indicators().atr14();Double distance=atr==null||atr<=0?null:t.entryPrice().subtract(t.stopPrice()).abs().doubleValue()/atr;
            var row=new TradeRow(t.tradeId(),t.symbol(),t.pattern().name(),t.direction().name(),t.signalDate(),t.entryDate(),t.exitDate(),t.closed(),period(t.signalDate()),context,t.netPnl(),t.grossPnl(),t.resultR(),t.closed()?netR(t):null,winLoss,t.exitReason(),loserType,t.holdingBars(),distance);
            rows.add(row);traded.put(key(t.symbol(),t.direction().name(),t.pattern().name(),t.signalIndex()),row);
        }
        Map<String,String> skipped=new HashMap<>();
        for(var event:run.portfolio().events())if(event.eventType().startsWith("SIGNAL_SKIPPED"))skipped.put(key(event.symbol(),event.direction().name(),event.pattern().name(),((Number)event.details().get("signalIndex")).intValue()),event.eventType());
        var signals=new ArrayList<SignalRow>();
        if(run.rangeSignals()!=null)for(var signal:run.rangeSignals()){
            var k=key(signal.symbol(),signal.direction(),signal.pattern(),signal.signalIndex());var trade=traded.get(k);String reason=skipped.get(k);
            if(trade==null&&reason==null){if(!signal.accepted())reason=signal.rejectReason();else if(run.closeStrengthSignals()!=null)reason=run.closeStrengthSignals().stream().filter(s->s.symbol().equals(signal.symbol())&&s.direction().equals(signal.direction())&&s.pattern().equals(signal.pattern())&&s.signalIndex()==signal.signalIndex()&&!s.accepted()).map(CloseStrengthAnalysis.SignalAudit::rejectReason).findFirst().orElse(null);}
            if(trade==null&&reason==null&&run.bollingerAtrSignals()!=null)reason=run.bollingerAtrSignals().stream().filter(s->s.symbol().equals(signal.symbol())&&s.direction().equals(signal.direction())&&s.pattern().equals(signal.pattern())&&s.signalIndex()==signal.signalIndex()&&!s.accepted()).map(BollingerAtrAnalysis.SignalAudit::rejectReason).findFirst().orElse(null);
            String status=trade!=null?"EXECUTED":reason!=null&&reason.contains("POSITION")?"SKIPPED_POSITION_OPEN":reason!=null&&reason.contains("CAPITAL")?"SKIPPED_CAPITAL":"OTHER_SKIPPED";
            signals.add(new SignalRow(signal.symbol(),signal.pattern(),signal.direction(),signal.signalDate(),signal.signalIndex(),status,reason,trade==null?null:trade.tradeId(),classify(at(indicators,signal.symbol(),signal.signalIndex()),signal.direction())));
        }
        var overview=groups(rows,r->r.context().marketRegime(),REGIMES);
        var secondary=groups(rows,r->r.context().secondaryRegime(),List.of("WEAK_TREND","TRANSITION_OR_MIXED","DIRECTIONAL_UP","DIRECTIONAL_DOWN","INSUFFICIENT_DATA"));
        var byADX=groups(rows,r->bucket(r.context().indicators().adx14()),BUCKETS);
        var direction=groups(rows,r->r.context().withOrCounterTrend(),List.of("WITH_TREND","COUNTER_TREND","NEUTRAL_OR_MIXED"));
        var emaDirection=groups(rows,r->r.context().emaWithOrCounterTrend(),List.of("WITH_TREND","COUNTER_TREND","NEUTRAL_OR_MIXED"));
        // Include zero-trade cells from the actual configured combinations, not a hardcoded symbol list.
        var detailed=new ArrayList<Group>();
        for(var s:run.configuration().symbols())if(s.enabled())for(String dir:List.of("LONG","SHORT")){
            var config=dir.equals("LONG")?s.longConfig():s.shortConfig();if(!config.enabled())continue;
            for(String regime:REGIMES)for(String time:PERIODS.subList(0,4)){
                var subset=rows.stream().filter(r->r.symbol().equals(s.symbol())&&r.direction().equals(dir)&&r.pattern().equals(config.pattern().name())&&r.context().marketRegime().equals(regime)&&r.period().equals(time)).toList();
                detailed.add(new Group(s.symbol()+"|"+config.pattern()+"|"+dir+"|"+regime+"|"+time,s.symbol(),config.pattern().name(),dir,regime,time,metrics(subset)));
            }
        }
        var periods=new ArrayList<Group>();for(String time:PERIODS.subList(0,4))for(String regime:REGIMES){var subset=rows.stream().filter(r->r.period().equals(time)&&r.context().marketRegime().equals(regime)).toList();periods.add(new Group(time+"|"+regime,null,null,null,regime,time,metrics(subset)));}
        return new Analysis("ANALYSIS ONLY. Closed actual trades; signal-date periods; net PnL tolerance 1e-8; Net R = netPnL/(initial riskPerShare*quantity). EMA SMA seed; Wilder ADX/DI TA-Lib seed. Warm-up starts at first stored run bar. Small samples <30 are descriptive, not causal evidence.",List.copyOf(rows),List.copyOf(signals),overview,secondary,byADX,direction,emaDirection,List.copyOf(detailed),List.copyOf(periods),groups(rows.stream().filter(r->r.loserType()!=null).toList(),TradeRow::loserType,List.of("STOP_LOSS","TIMEOUT_LOSS","OTHER_LOSS")));
    }
    private static Value at(Map<String,List<Value>> values,String symbol,int index){var list=values.get(symbol);return list==null||index<0||index>=list.size()?null:list.get(index);}
    private static String key(String symbol,String direction,String pattern,int index){return symbol+"|"+direction+"|"+pattern+"|"+index;}
    private static boolean stop(String reason){return "STOP".equals(reason)||"STOP_LOSS".equals(reason);}
    private static boolean target(String reason){return "TARGET".equals(reason)||"TAKE_PROFIT".equals(reason);}
    private static List<Group> groups(List<TradeRow> rows,Function<TradeRow,String> key,List<String> defaults){
        Map<String,List<TradeRow>> groups=new LinkedHashMap<>();defaults.forEach(k->groups.put(k,new ArrayList<>()));rows.forEach(r->groups.computeIfAbsent(key.apply(r),k->new ArrayList<>()).add(r));
        return groups.entrySet().stream().map(e->new Group(e.getKey(),null,null,null,null,null,metrics(e.getValue()))).toList();
    }
    public static Metrics metrics(List<TradeRow> rows){
        var closed=rows.stream().filter(TradeRow::closed).toList();var wins=closed.stream().filter(r->r.winLoss().equals("WINNER")).toList();var losses=closed.stream().filter(r->r.winLoss().equals("LOSER")).toList();
        long zero=closed.size()-wins.size()-losses.size(),stop=closed.stream().filter(r->stop(r.exitReason())).count(),target=closed.stream().filter(r->target(r.exitReason())).count(),timeout=closed.stream().filter(r->"TIMEOUT".equals(r.exitReason())).count();
        double profit=wins.stream().mapToDouble(r->r.netPnl().doubleValue()).sum(),loss=-losses.stream().mapToDouble(r->r.netPnl().doubleValue()).sum();
        double grossProfit=closed.stream().mapToDouble(r->r.grossPnl().doubleValue()).filter(v->v>0).sum(),grossLoss=-closed.stream().mapToDouble(r->r.grossPnl().doubleValue()).filter(v->v<0).sum();
        return new Metrics(closed.size(),wins.size(),losses.size(),zero,rows.size()-closed.size(),rate(wins.size(),closed.size()),rate(losses.size(),closed.size()),grossProfit,grossLoss,closed.stream().mapToDouble(r->r.netPnl().doubleValue()).sum(),loss==0?null:profit/loss,average(closed,r->number(r.priceR())),average(closed,r->number(r.netR())),average(wins,r->number(r.netR())),average(losses,r->number(r.netR())),average(closed,r->r.context().indicators().adx14()),average(wins,r->r.context().indicators().adx14()),average(losses,r->r.context().indicators().adx14()),average(closed,r->(double)r.holdingBars()),stop,target,timeout,rate(stop,closed.size()),rate(timeout,closed.size()),closed.stream().filter(r->r.netR()!=null).count(),closed.stream().filter(r->r.context().indicators().adx14()!=null).count(),closed.size()<30);
    }
    private static Double number(BigDecimal value){return value==null?null:value.doubleValue();}
    private static Double rate(long count,long total){return total==0?null:100.0*count/total;}
    private static Double average(List<TradeRow> rows,Function<TradeRow,Double> value){var values=rows.stream().map(value).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average();return values.isPresent()?values.getAsDouble():null;}
}
