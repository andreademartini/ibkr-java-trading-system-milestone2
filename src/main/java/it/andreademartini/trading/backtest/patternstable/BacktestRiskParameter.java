package it.andreademartini.trading.backtest.patternstable;

import it.andreademartini.trading.backtest.patternv2.PatternV2Configuration;
import java.util.Map;

/** The sole permitted override of a stable historical backtest. Never used by PAPER. */
public final class BacktestRiskParameter {
    private BacktestRiskParameter() {}
    public static Double parse(Map<String,Object> request){
        if(request==null||request.isEmpty())return null;
        if(request.size()!=1||!request.containsKey("riskPercent"))throw new IllegalArgumentException("Only riskPercent can be changed in this backtest");
        if(!(request.get("riskPercent") instanceof Number number))throw new IllegalArgumentException("Risk Percent % must be a number greater than 0 and at most 100");
        double value=number.doubleValue();validate(value);return value;
    }
    public static void validate(double value){
        if(!Double.isFinite(value)||value<=0||value>100)throw new IllegalArgumentException("Risk Percent % must be greater than 0 and at most 100");
    }
    public static PatternV2Configuration apply(PatternV2Configuration base,Double risk){
        if(risk==null)return base;
        validate(risk);
        var json=PatternStableStore.JSON.valueToTree(base);
        ((com.fasterxml.jackson.databind.node.ObjectNode)json.path("global")).put("riskPercent",risk);
        return PatternStableStore.JSON.convertValue(json,PatternV2Configuration.class);
    }
    public static String runVersion(String base,PatternV2Configuration original,PatternV2Configuration actual){
        return Double.compare(original.global().riskPercent(),actual.global().riskPercent())==0?base:base+"-risk-test";
    }
}
