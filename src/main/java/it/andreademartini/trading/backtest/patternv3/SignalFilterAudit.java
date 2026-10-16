package it.andreademartini.trading.backtest.patternv3;

import it.andreademartini.trading.backtest.patternv1.PatternV1Result;
import it.andreademartini.trading.strategy.patternv1.PatternV1Strategy.Signal;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

/** All predicates are evaluated independently, including predicates after a rejection.
 * Disposition is joined only after the DAILY portfolio simulation. No hypothetical outcomes. */
public final class SignalFilterAudit {
    public static final String VERSION="V3_RESEARCH_FILTERS/1";
    public record Row(String signalId,String symbol,String pattern,String direction,LocalDate signalDate,int signalIndex,SignalTimeframe signalTimeframe,
        BigDecimal closeStrength,boolean closeStrengthFilterEnabled,BigDecimal closeStrengthThreshold,String closeStrengthRule,Boolean closeStrengthPassed,
        BigDecimal patternRangePct,boolean patternRangeFilterEnabled,BigDecimal patternRangeMinPct,BigDecimal patternRangeMaxPct,Boolean patternRangePassed,
        Long signalVolume,int relativeVolumePeriod,BigDecimal relativeVolumeAverage,BigDecimal relativeVolume,boolean relativeVolumeFilterEnabled,
        BigDecimal relativeVolumeMin,BigDecimal relativeVolumeMax,RelativeVolume.Basis relativeVolumeBasis,String relativeVolumeIssue,Boolean relativeVolumePassed,
        Double adx14,String marketRegime,boolean marketRegimeFilterEnabled,boolean marketRegimePassed,Boolean bollingerAtrPassed,
        boolean finalSignalAccepted,List<String> rejectionReasons,boolean orderPlanned,boolean orderAttempted,boolean tradeExecuted,String tradeId,String notExecutedReason) {
        public Row {rejectionReasons=List.copyOf(rejectionReasons);}
    }
    private SignalFilterAudit(){}
    public static BigDecimal rangePercent(PatternRange range){return range==null||range.close()==null||range.close().signum()<=0||range.high()==null||range.low()==null||range.high().compareTo(range.low())<0?null:range.percent();}
    public static boolean rangeWithin(PatternRange range,PatternV3Configuration config){
        var pct=rangePercent(range);return pct!=null&&range.reaches(config.rangeMinimum())&&(config.patternRangeMaxPct()==null||range.range().multiply(BigDecimal.valueOf(100)).compareTo(config.patternRangeMaxPct().multiply(range.close()))<=0);
    }
    private static Boolean passed(boolean enabled,BigDecimal value,boolean predicate){if(!enabled)return Boolean.TRUE;if(value==null)return null;return predicate;}
    public static boolean closeWithin(PatternRange range,it.andreademartini.trading.research.pattern.PatternDirection direction,PatternV3Configuration c){
        return c.closeStrengthMin()==null?CloseStrength.accepts(range,direction,c.maxCloseStrength()):CloseStrength.acceptsMinimum(range,direction,c.closeStrengthMin());
    }
    public static Row evaluate(String id,Signal signal,PatternV3Configuration c,RelativeVolume.Value volume,MarketRegimeFilter.SignalAudit regime,BollingerAtrAnalysis.SignalAudit bb){
        var range=new PatternRange(signal.patternHigh(),signal.patternLow(),signal.patternClose());var close=CloseStrength.value(range,signal.detection().direction());var pct=rangePercent(range);
        var closePass=passed(c.closeStrengthFilterEnabled(),close,closeWithin(range,signal.detection().direction(),c));
        var rangePass=passed(c.patternRangeFilterEnabled(),pct,rangeWithin(range,c));
        var v=volume.relativeVolume();var volumePass=passed(c.rvolEnabled(),v,v!=null&&v.compareTo(c.rvolMinimum())>=0&&(c.relativeVolumeMax()==null||v.compareTo(c.relativeVolumeMax())<=0));
        var reasons=new ArrayList<String>();
        if(c.closeStrengthFilterEnabled()&&!Boolean.TRUE.equals(closePass))reasons.add(close==null?"INVALID_CLOSE_STRENGTH_DATA":"CLOSE_STRENGTH_FILTER");
        if(c.patternRangeFilterEnabled()&&!Boolean.TRUE.equals(rangePass))reasons.add(pct==null?"INVALID_PATTERN_RANGE_DATA":"PATTERN_RANGE_FILTER");
        if(c.rvolEnabled()&&!Boolean.TRUE.equals(volumePass))reasons.add(v==null?"RELATIVE_VOLUME_NOT_AVAILABLE":"RELATIVE_VOLUME_FILTER");
        if(!regime.accepted())reasons.add(regime.rejectionReason());
        if(bb.rejectReason()!=null)reasons.add(bb.rejectReason());
        return new Row(id,signal.symbol(),signal.detection().patternType().name(),signal.detection().direction().name(),signal.detection().signalDate(),signal.detection().signalBarIndex(),c.signalTimeframe(),
            close,c.closeStrengthFilterEnabled(),c.closeStrengthMin()==null?c.maxCloseStrength():c.closeStrengthMin(),c.closeStrengthMin()==null?"LEGACY_MAX_LE":"MIN_GE",closePass,
            pct,c.patternRangeFilterEnabled(),c.rangeMinimum(),c.patternRangeMaxPct(),rangePass,volume.signalVolume(),volume.period(),volume.average(),v,c.rvolEnabled(),c.rvolMinimum(),c.relativeVolumeMax(),c.rvolBasis(),volume.issue(),volumePass,
            regime.indicators()==null?null:regime.indicators().adx14(),regime.marketRegime(),c.marketRegimeFilterEnabled(),regime.accepted(),bb.accepted(),reasons.isEmpty(),reasons,false,false,false,null,null);
    }
    private static String key(String symbol,String pattern,String direction,int index){return symbol+"|"+pattern+"|"+direction+"|"+index;}
    public static List<Row> complete(List<Row> raw,List<PatternV1Result.Trade> trades,List<PatternV1Result.Event> events){
        var executed=new HashMap<String,PatternV1Result.Trade>();for(var t:trades)executed.put(key(t.symbol(),t.pattern().name(),t.direction().name(),t.signalIndex()),t);
        var skipped=new HashMap<String,String>();var planned=new HashSet<String>();
        for(var e:events){var i=e.details().get("signalIndex");if(!(i instanceof Number index))continue;var k=key(e.symbol(),e.pattern().name(),e.direction().name(),index.intValue());
            if(e.eventType().startsWith("SIGNAL_SKIPPED"))skipped.putIfAbsent(k,e.eventType());if(e.eventType().equals("SIGNAL"))planned.add(k);}
        var result=new ArrayList<Row>();for(var r:raw){var k=key(r.symbol(),r.pattern(),r.direction(),r.signalIndex());var t=executed.get(k);var reason=r.finalSignalAccepted()?skipped.get(k):"FILTER_REJECTED";
            boolean orderPlanned=r.finalSignalAccepted()&&planned.contains(k)&&!"SIGNAL_SKIPPED_NO_NEXT_BAR".equals(reason);
            boolean attempted=t!=null||reason!=null&&!reason.equals("FILTER_REJECTED")&&!reason.equals("SIGNAL_SKIPPED_NO_NEXT_BAR")&&!reason.equals("SIGNAL_SKIPPED_POSITION_ALREADY_OPEN");
            result.add(new Row(r.signalId(),r.symbol(),r.pattern(),r.direction(),r.signalDate(),r.signalIndex(),r.signalTimeframe(),r.closeStrength(),r.closeStrengthFilterEnabled(),r.closeStrengthThreshold(),r.closeStrengthRule(),r.closeStrengthPassed(),r.patternRangePct(),r.patternRangeFilterEnabled(),r.patternRangeMinPct(),r.patternRangeMaxPct(),r.patternRangePassed(),r.signalVolume(),r.relativeVolumePeriod(),r.relativeVolumeAverage(),r.relativeVolume(),r.relativeVolumeFilterEnabled(),r.relativeVolumeMin(),r.relativeVolumeMax(),r.relativeVolumeBasis(),r.relativeVolumeIssue(),r.relativeVolumePassed(),r.adx14(),r.marketRegime(),r.marketRegimeFilterEnabled(),r.marketRegimePassed(),r.bollingerAtrPassed(),r.finalSignalAccepted(),r.rejectionReasons(),orderPlanned,attempted,t!=null,t==null?null:t.tradeId(),t!=null?null:reason==null?"NOT_EXECUTED":reason));
        }return List.copyOf(result);
    }
}
