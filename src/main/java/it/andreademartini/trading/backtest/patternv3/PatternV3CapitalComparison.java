package it.andreademartini.trading.backtest.patternv3;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Strict comparison of capital-limit methods; existing filter comparisons retain their guard. */
public final class PatternV3CapitalComparison {
    private static final ObjectMapper MAPPER=new ObjectMapper().findAndRegisterModules();
    private static final List<String> FIELDS=List.of("maxPositionValueMode","maxPositionValuePercent","maxLongNotionalMode","maxLongNotionalPercent","maxShortNotionalMode","maxShortNotionalPercent");
    private PatternV3CapitalComparison() {}
    public record Run(String runId,String name,PatternV3Configuration.Global limits) {}
    public record Metric(String metric,List<Double> values,Double difference) {}
    public record Comparison(String datasetId,List<Run> runs,List<Metric> metrics) {}
    private static ObjectNode comparable(PatternV3Configuration c) {
        ObjectNode node=MAPPER.valueToTree(c);node.remove("name");((ObjectNode)node.get("global")).remove(FIELDS);return node;
    }
    public static void requireSameConfiguration(PatternV3Configuration a,PatternV3Configuration b) {
        if(!comparable(a).equals(comparable(b)))throw new IllegalArgumentException("Capital experiment requires identical data, filters, sizing, costs and fixed amounts; only name and the six capital-limit settings may differ");
    }
    public static Comparison compare(List<PatternV3Run> runs) {
        if(runs.size()<2)throw new IllegalArgumentException("Select at least two runs");
        var first=runs.getFirst();if(first.datasetId()==null)throw new IllegalArgumentException("Exact dataset snapshot unavailable");
        for(var r:runs){if(!first.datasetId().equals(r.datasetId()))throw new IllegalArgumentException("Different or unverifiable datasets");requireSameConfiguration(first.configuration(),r.configuration());}
        var metrics=runs.stream().map(PatternV3CapitalComparison::metrics).toList();
        return new Comparison(first.datasetId(),runs.stream().map(r->new Run(r.runId(),r.configuration().name(),r.configuration().global())).toList(),
                metrics.getFirst().keySet().stream().map(k->{var values=metrics.stream().map(m->m.get(k)).toList();return new Metric(k,values,values.size()==2&&values.get(0)!=null&&values.get(1)!=null?values.get(1)-values.get(0):null);}).toList());
    }
    public static Map<String,Double> metrics(PatternV3Run run) {
        var p=run.portfolio();var s=p.summary();var closed=p.trades().stream().filter(t->t.closed()&&t.actualRiskAmount()!=null&&t.actualRiskAmount().signum()>0).toList();
        Map<String,Double> m=new LinkedHashMap<>();m.put("Final Equity",p.finalCapital().doubleValue());m.put("CAGR %",p.cagr());m.put("Max Drawdown %",p.maxDrawdownPercent());
        m.put("PF Net",s.profitFactorNet());m.put("Expectancy Net R",closed.isEmpty()?null:closed.stream().mapToDouble(t->t.netPnl().doubleValue()/t.actualRiskAmount().doubleValue()).average().orElseThrow());
        m.put("Trade Count",(double)p.trades().size());m.put("Win Rate %",s.winRate()==null?null:s.winRate()*100);m.put("Capital Skips",(double)s.skippedCapital());
        m.put("Average Actual Risk %",s.averageActualRiskPercent());m.put("Average Order Value",s.averageOrderValue());m.put("Average Gross Exposure %",p.averageGrossExposurePercent());
        m.put("CAGR / Max Drawdown",p.cagr()!=null&&p.maxDrawdownPercent()>0?p.cagr()/p.maxDrawdownPercent():null);return m;
    }
    public static long limited(PatternV3Run run,String constraint,boolean binding) {
        return run.executions().stream().filter(e->e.capitalLimits()!=null&&e.capitalLimits().steps().stream().anyMatch(s->s.constraint().equals(constraint)&&(binding?s.binding():s.reduced()))).count();
    }
}
