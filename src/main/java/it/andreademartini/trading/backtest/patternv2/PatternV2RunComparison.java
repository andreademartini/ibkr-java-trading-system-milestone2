package it.andreademartini.trading.backtest.patternv2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Filter experiments can differ only in their names and filter settings. */
public final class PatternV2RunComparison {
    public record Run(String runId,String name,String mode,boolean patternRangeFilterEnabled,Number minimumPatternRangePct,
            boolean closeStrengthFilterEnabled,Number maxCloseStrength,List<PatternV2Analysis.Period> periods) {}
    public record Metric(String metric,List<Double> values) {}
    public record Comparison(String datasetId,List<Run> runs,List<Metric> metrics) {}
    private static final ObjectMapper MAPPER=new ObjectMapper().findAndRegisterModules();
    private PatternV2RunComparison() {}
    public static void requireSameTradingConfiguration(PatternV2Configuration a,PatternV2Configuration b) {
        if(!tradingConfiguration(a).equals(tradingConfiguration(b)))throw new IllegalArgumentException("Trading parameters, dates, symbols and cost basis must match; only filters and name may differ");
    }
    private static ObjectNode tradingConfiguration(PatternV2Configuration c) {
        ObjectNode json=MAPPER.valueToTree(c);json.remove(List.of("name","marketRegimeAnalysisEnabled","capitalSkipShadowAnalysisEnabled","patternRangeFilterEnabled","minimumPatternRangePct","closeStrengthFilterEnabled","maxCloseStrength","bollingerAtrFilterEnabled","bollingerPeriod","bollingerStdDev","atrPeriod","maxBollingerDistanceAtr"));return json;
    }
    public static String mode(PatternV2Configuration c) {
        if(c.bollingerAtrFilterEnabled())return "E / BOLLINGER_ATR";
        return c.patternRangeFilterEnabled()?c.closeStrengthFilterEnabled()?"D / COMBINED":"B / RANGE":c.closeStrengthFilterEnabled()?"C / CLOSE":"A / BASELINE";
    }
    public static Comparison compare(List<PatternV2Run> input) {
        if(input.size()<2)throw new IllegalArgumentException("Select at least two runs");
        var first=input.getFirst();if(first.datasetId()==null)throw new IllegalArgumentException("Exact dataset snapshot unavailable for legacy run");
        for(var run:input) {
            if(!first.datasetId().equals(run.datasetId()))throw new IllegalArgumentException("Runs use different or unverifiable input datasets");
            requireSameTradingConfiguration(first.configuration(),run.configuration());
        }
        var ordered=input.stream().sorted(Comparator.comparing(r->mode(r.configuration()))).toList();
        var runs=ordered.stream().map(r->new Run(r.runId(),r.configuration().name(),mode(r.configuration()),r.configuration().patternRangeFilterEnabled(),r.configuration().minimumPatternRangePct(),r.configuration().closeStrengthFilterEnabled(),r.configuration().maxCloseStrength(),r.analysis().periods())).toList();
        var values=ordered.stream().map(PatternRangeAnalysis::metrics).toList();
        var metrics=values.getFirst().keySet().stream().map(key->new Metric(key,values.stream().map(v->v.get(key)).toList())).toList();
        return new Comparison(first.datasetId(),runs,metrics);
    }
    public static List<PatternRangeAnalysis.Metric> pair(PatternV2Run baseline,PatternV2Run actual) {
        var a=PatternRangeAnalysis.metrics(baseline);var b=PatternRangeAnalysis.metrics(actual);
        return a.entrySet().stream().map(e->new PatternRangeAnalysis.Metric(e.getKey(),e.getValue(),b.get(e.getKey()),
                e.getValue()==null||b.get(e.getKey())==null?null:b.get(e.getKey())-e.getValue())).toList();
    }
}
