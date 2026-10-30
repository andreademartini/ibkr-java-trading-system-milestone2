package it.andreademartini.trading.backtest.reporting;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.io.*;
import static it.andreademartini.trading.backtest.reporting.PortfolioSummaryDto.*;

/** Read-only adapters. Never feeds a value back into strategy execution or persisted results. */
public final class PortfolioSummaryMapper {
    private static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final JsonNode DEFINITIONS;
    static {try(var in=PortfolioSummaryMapper.class.getResourceAsStream("/static/portfolio-summary-definitions.json")){DEFINITIONS=JSON.readTree(in);}catch(IOException e){throw new ExceptionInInitializerError(e);}}
    private PortfolioSummaryMapper(){}
    public static PortfolioSummaryDto from(Object run){return fromNode(JSON.valueToTree(run));}
    public static boolean isRun(JsonNode r){return r.isObject()&&((r.has("portfolio")&&(r.path("portfolio").has("summary")||r.path("portfolio").has("equity")))||(r.has("summary")&&r.has("equityCurve"))||(r.has("configuration")&&r.has("equity")&&r.has("analysis"))||(r.has("parameters")&&r.has("metrics")&&r.has("backtestId")));}
    private static JsonNode at(JsonNode n,String key){return n.path(key);}
    private static Double num(JsonNode n){return n.isNumber()&&Double.isFinite(n.asDouble())?n.asDouble():null;}
    private static Object value(JsonNode n){return n.isMissingNode()||n.isNull()?null:JSON.convertValue(n,Object.class);}
    private static JsonNode first(JsonNode... ns){for(var n:ns)if(!n.isMissingNode()&&!n.isNull())return n;return JSON.nullNode();}
    private static Double number(JsonNode n,String... keys){for(String k:keys){var x=num(n.path(k));if(x!=null)return x;}return null;}
    private static String text(JsonNode n,String k){return n.path(k).isTextual()?n.path(k).asText():null;}
    private static List<JsonNode> list(JsonNode n){var xs=new ArrayList<JsonNode>();if(n.isArray())n.forEach(xs::add);return xs;}
    private static void put(Map<String,Double> m,String k,Double v){if(v!=null&&Double.isFinite(v))m.put(k,v);}
    private static Double sum(List<JsonNode> xs,String key){if(xs.stream().anyMatch(x->num(x.path(key))==null))return null;return xs.stream().mapToDouble(x->x.path(key).asDouble()).sum();}
    private static Double pf(List<Double> values){double gain=0,loss=0;for(var v:values){if(v==null)return null;if(v>0)gain+=v;else loss-=v;}return loss>0?gain/loss:null;}
    private static Double avg(List<Double> xs){return xs.isEmpty()||xs.stream().anyMatch(Objects::isNull)?null:xs.stream().mapToDouble(Double::doubleValue).average().orElseThrow();}
    private static Double add(Double a,Double b){return a==null||b==null?null:a+b;}
    private static Double sub(Double a,Double b){return a==null||b==null?null:a-b;}
    public static PortfolioSummaryDto fromNode(JsonNode r){
        if(r.has("simulation")&&r.has("strategyId")){
            var merged=((com.fasterxml.jackson.databind.node.ObjectNode)r.path("simulation")).deepCopy();
            for(String k:List.of("runId","strategyVersion","datasetId","configurationHash"))if(r.has(k))merged.set(k,r.path(k));
            merged.set("strategy",r.path("strategyId"));r=merged;
        }
        if(r.has("portfolio")&&!r.path("portfolio").has("summary")){
            var merged=((com.fasterxml.jackson.databind.node.ObjectNode)r).deepCopy();var nested=r.path("portfolio");
            for(String k:List.of("trades","signals","equity","analysis"))if(nested.has(k))merged.set(k,nested.path(k));
            merged.remove("portfolio");r=merged;
        }
        var p=r.has("portfolio")?r.path("portfolio"):r;
        var c=r.path("configuration");var g=c.has("global")?c.path("global"):c;var settings=p.path("settings");
        var context=new LinkedHashMap<String,Object>();
        context.put("Strategy",value(first(r.path("strategy"),p.path("strategy"))));context.put("Strategy Version",value(first(r.path("strategyVersion"),c.path("version"),c.path("rulesVersion"))));
        context.put("Run ID",value(first(r.path("runId"),p.path("backtestId"))));
        context.put("Requested Start Date",value(first(g.path("startDate"),p.path("startDate"))));context.put("Requested End Date",value(first(g.path("endDate"),p.path("endDate"))));
        var performance=r.path("analysis").path("performance");
        var curve=list(first(p.path("equityCurve"),r.path("equity")));
        context.put("Effective Start Date",value(first(p.path("actualStartDate"),performance.path("effectiveStartDate"),performance.path("actualDataStart"),curve.isEmpty()?JSON.nullNode():curve.getFirst().path("date"))));
        context.put("Effective End Date",value(first(p.path("actualEndDate"),performance.path("effectiveEndDate"),performance.path("actualDataEnd"),curve.isEmpty()?JSON.nullNode():curve.getLast().path("date"))));
        context.put("CAGR Coverage","Effective Start Date / Effective End Date; elapsed days / 365.25");
        context.put("Max Order Value Percent",value(first(g.path("maxOrderValuePercent"),g.path("maxPositionValuePercent"))));
        context.put("Max Order Value Mode",value(first(g.path("maxOrderValueMode"),g.path("maxPositionValueMode"))));
        context.put("Max Order Value",value(first(g.path("maxOrderValue"),settings.path("maxOrderValue"))));
        context.put("Risk Percent",value(first(g.path("riskPercent"),settings.path("riskPercent"))));context.put("Max Quantity",value(first(g.path("maxQuantity"),settings.path("maxQuantity"))));
        var fees=first(g.path("commission"),settings.path("commission"));
        for(String[] fee:new String[][]{{"Model","model"},{"Per Share","perShare"},{"Minimum Per Order","minimumPerOrder"},{"Maximum Percent Order Value","maximumPercentOrderValue"},{"Fixed Per Order","fixedPerOrder"}})context.put("Commission "+fee[0],value(fees.path(fee[1])));
        context.put("Commission Enabled",value(g.path("commissionEnabled")));context.put("Slippage Enabled",value(g.path("slippageEnabled")));
        context.put("Slippage Percent Per Execution",value(first(g.path("slippagePercentPerSide"),settings.path("slippagePercent"))));
        var leverage=first(g.path("useLeverage"),settings.path("useLeverage"));
        String strategy=Objects.toString(context.get("Strategy"),"");
        boolean rebound=strategy.startsWith("TREND_PULLBACK_REBOUND"),weekly=strategy.startsWith("WEEKLY_")&&r.has("setups");
        context.put("Leverage Enabled",leverage.isBoolean()?Boolean.valueOf(leverage.asBoolean()):(rebound||weekly?Boolean.FALSE:null));
        if((rebound||weekly)&&context.get("Max Order Value Mode")==null)context.put("Max Order Value Mode","CURRENT_EQUITY_PERCENT");
        context.put("Dataset",value(first(r.path("datasetId"),r.path("datasetFingerprint"))));context.put("Universe",value(c.path("symbols")));
        // Retain the exact saved run configuration for audit; never read the current form.
        context.put("Applied Configuration",value(first(r.path("configuration"),p.path("settings"))));
        var metrics=new LinkedHashMap<String,Double>();
        boolean pattern=p.has("summary");
        if(pattern){
            for(var d:DEFINITIONS){var source=d.path("source").asText().equals("run")?p:p.path("summary");Double v=num(source.path(d.path("key").asText()));if(d.path("key").asText().equals("winRate")&&v!=null)v*=100;put(metrics,d.path("key").asText(),v);}
        }else if(r.has("metrics")&&r.has("parameters")){
            var m=r.path("metrics");var params=r.path("parameters");
            for(String[] pair:new String[][]{{"initialCapital","initialCapital"},{"finalCapital","finalEquity"},{"returnPercent","totalReturnPercent"},{"maxDrawdownPercent","maxDrawdownPercent"}})put(metrics,pair[0],number(m,pair[1]));
            var trades=list(r.path("trades"));var closed=trades.stream().filter(t->t.hasNonNull("exitAt")&&t.path("remainingQuantity").asLong(-1)==0).toList();
            put(metrics,"trades",(double)trades.size());put(metrics,"closedTrades",(double)closed.size());put(metrics,"openTrades",(double)(trades.size()-closed.size()));
            var nets=closed.stream().map(t->number(t,"netPnl")).toList();var gross=closed.stream().map(t->number(t,"grossPnl")).toList();var rs=closed.stream().map(t->number(t,"rMultiple")).toList();
            put(metrics,"realizedNetPnl",sum(closed,"netPnl"));put(metrics,"realizedGrossPnl",sum(closed,"grossPnl"));put(metrics,"profitFactorNet",pf(nets));put(metrics,"profitFactor",pf(gross));put(metrics,"profitFactorR",pf(rs));put(metrics,"averageR",avg(rs));put(metrics,"expectancyR",avg(rs));
            for(boolean win:List.of(true,false)){var group=closed.stream().filter(t->number(t,"netPnl")!=null&&(win?t.path("netPnl").asDouble()>0:t.path("netPnl").asDouble()<0)).toList();put(metrics,win?"averageWinnerR":"averageLoserR",avg(group.stream().map(t->number(t,"rMultiple")).toList()));}
            if(nets.stream().noneMatch(Objects::isNull)){double wins=nets.stream().filter(v->v>0).count(),losses=nets.stream().filter(v->v<0).count();put(metrics,"wins",wins);put(metrics,"losses",losses);put(metrics,"breakevens",closed.size()-wins-losses);put(metrics,"winRate",wins+losses>0?100*wins/(wins+losses):null);put(metrics,"averageWinner",avg(nets.stream().filter(v->v>0).toList()));put(metrics,"averageLoser",avg(nets.stream().filter(v->v<0).toList()));}
            put(metrics,"timeouts",(double)closed.stream().filter(t->t.path("exitReason").asText().equals("TIMEOUT")).count());
            if(r.path("signals").isArray())put(metrics,"signalsDetected",(double)r.path("signals").size());
            put(metrics,"timeInMarketPercent",number(m,"exposurePercent"));
            context.put("Requested Start Date",value(r.path("startAt")));context.put("Requested End Date",value(r.path("endAt")));
            var bars=list(r.path("bars"));if(!bars.isEmpty()){context.put("Effective Start Date",value(bars.getFirst().path("timestamp")));context.put("Effective End Date",value(bars.getLast().path("timestamp")));}
            context.put("Applied Configuration",value(params));context.put("Slippage Percent Per Execution",value(params.path("slippagePercent")));context.put("Commission Fixed Per Order",value(params.path("commissionPerOrder")));context.put("Commission Model","FIXED_PER_ORDER");context.put("Quantity Applied",value(params.path("quantity")));if(r.hasNonNull("symbol"))context.put("Universe",List.of(r.path("symbol").asText()));
        }else if(rebound||weekly){
            put(metrics,"initialCapital",number(g,"initialCapital"));
            var last=curve.isEmpty()?JSON.nullNode():curve.getLast();put(metrics,"finalCash",number(last,"cash"));put(metrics,"finalCapital",number(last,"equity"));
            Double longValue=number(last,"longNotional","notional"),shortValue=number(last,"shortNotional");
            if(rebound&&longValue!=null)shortValue=0.0;
            put(metrics,"finalLongMarketValue",longValue);put(metrics,"finalShortMarketValue",shortValue);put(metrics,"finalOpenPositionValue",sub(longValue,shortValue));
            // Weekly SHORT cash is encumbered by proceeds and collateral; only use an explicit available-cash field.
            put(metrics,"finalAvailableCash",rebound?number(last,"cash"):number(last,"availableCash"));
            if(weekly&&"WEEKLY_UNTOUCHED/2".equals(text(c,"rulesVersion"))&&number(last,"cash")!=null&&number(last,"reservedCollateral")!=null){
                // Historical weekly engine deducts collateral instead of crediting restricted short proceeds.
                // Convert only the reporting view to standard ledger cash; available cash is already free cash.
                put(metrics,"finalCash",number(last,"cash")+2*number(last,"reservedCollateral"));put(metrics,"finalAvailableCash",number(last,"cash"));
                context.put("SHORT Cash Convention","Ledger cash = engine free cash + 2 × reserved entry notional; short market value remains a liability");
            }else if(weekly&&shortValue!=null&&shortValue==0.0)put(metrics,"finalAvailableCash",number(last,"cash"));
            var trades=list(r.path("trades"));var closed=trades.stream().filter(t->weekly?number(t,"remainingQuantity")!=null&&t.path("remainingQuantity").asLong()==0:t.hasNonNull("exitDate")).toList();
            var open=trades.stream().filter(t->!closed.contains(t)).toList();
            put(metrics,"trades",(double)trades.size());put(metrics,"closedTrades",(double)closed.size());put(metrics,"openTrades",(double)open.size());
            var net=closed.stream().map(t->number(t,"netPnL")).toList();put(metrics,"realizedNetPnl",sum(closed,"netPnL"));put(metrics,"profitFactorNet",pf(net));
            var rs=closed.stream().map(t->number(t,"netR")).toList();put(metrics,"profitFactorR",pf(rs));put(metrics,"expectancyR",avg(rs));put(metrics,"averageR",avg(rs));
            if(net.stream().noneMatch(Objects::isNull)){
                long wins=net.stream().filter(v->v>0).count(),losses=net.stream().filter(v->v<0).count();put(metrics,"wins",(double)wins);put(metrics,"losses",(double)losses);put(metrics,"breakevens",(double)(closed.size()-wins-losses));put(metrics,"winRate",wins+losses>0?100.0*wins/(wins+losses):null);
                put(metrics,"averageWinner",avg(net.stream().filter(v->v>0).toList()));put(metrics,"averageLoser",avg(net.stream().filter(v->v<0).toList()));
                for(boolean win:List.of(true,false)){var group=closed.stream().filter(t->win?t.path("netPnL").asDouble()>0:t.path("netPnL").asDouble()<0).toList();put(metrics,win?"averageWinnerR":"averageLoserR",avg(group.stream().map(t->number(t,"netR")).toList()));}
            }
            if(closed.stream().allMatch(t->t.hasNonNull("exitReason")))put(metrics,"timeouts",(double)closed.stream().filter(t->t.path("exitReason").asText().equals("TIMEOUT")).count());
            if(rebound){
                var gross=new ArrayList<Double>();for(var t:closed){Double exit=number(t,"exitBasePrice"),entry=number(t,"entryBasePrice"),qty=number(t,"quantity");gross.add(exit==null||entry==null||qty==null?null:(exit-entry)*qty);}
                put(metrics,"realizedGrossPnl",gross.stream().anyMatch(Objects::isNull)?null:gross.stream().mapToDouble(Double::doubleValue).sum());put(metrics,"profitFactor",pf(gross));
                put(metrics,"netUnrealizedPnl",sum(open,"unrealizedNetPnL"));
                var openGross=new ArrayList<Double>();for(var t:open){Double mark=number(t,"lastMark"),entry=number(t,"entryBasePrice"),qty=number(t,"quantity");openGross.add(mark==null||entry==null||qty==null?null:(mark-entry)*qty);}
                put(metrics,"unrealizedPnl",openGross.stream().anyMatch(Objects::isNull)?null:openGross.stream().mapToDouble(Double::doubleValue).sum());
            }
            if(weekly&&trades.stream().allMatch(t->t.path("exits").isArray()&&number(t,"entryBasePrice")!=null&&number(t,"entryExecutionPrice")!=null&&number(t,"quantity")!=null&&number(t,"entryCommission")!=null&&number(t,"remainingQuantity")!=null&&number(t,"slippageCosts")!=null&&number(t,"exitCommissions")!=null)&&trades.stream().flatMap(t->list(t.path("exits")).stream()).allMatch(f->number(f,"quantity")!=null&&number(f,"basePrice")!=null&&number(f,"executionPrice")!=null&&number(f,"commission")!=null)){
                double grossRealized=0,netRealized=0,totalCosts=0;var grossClosed=new ArrayList<Double>();
                for(var t:trades){double sign="SHORT".equals(text(t,"direction"))?-1:1,tradeGross=0,tradeNet=0,exited=0;
                    for(var f:list(t.path("exits"))){double qty=number(f,"quantity");exited+=qty;tradeGross+=sign*(number(f,"basePrice")-number(t,"entryBasePrice"))*qty;tradeNet+=sign*(number(f,"executionPrice")-number(t,"entryExecutionPrice"))*qty-number(f,"commission");}
                    tradeNet-=number(t,"entryCommission")*exited/number(t,"quantity");grossRealized+=tradeGross;netRealized+=tradeNet;totalCosts+=number(t,"entryCommission")+number(t,"exitCommissions")+number(t,"slippageCosts");if(closed.contains(t))grossClosed.add(tradeGross);
                }
                put(metrics,"realizedGrossPnl",grossRealized);put(metrics,"realizedNetPnl",netRealized);put(metrics,"profitFactor",pf(grossClosed));
                put(metrics,"grossPnl",add(sub(metrics.get("finalCapital"),metrics.get("initialCapital")),totalCosts));put(metrics,"unrealizedPnl",sub(metrics.get("grossPnl"),grossRealized));
                context.put("Partial Exit Accounting","Realized fills; entry commission allocated by exited quantity. Remaining entry costs stay in Net Unrealized PnL.");
            }
            var signals=list(rebound?r.path("signals"):r.path("setups"));if((rebound?r.path("signals"):r.path("setups")).isArray()){
                put(metrics,"signalsDetected",(double)signals.size());
                String reasonKey=rebound?"reason":"rejectionReason";
                if(signals.stream().allMatch(s->s.has("status")||s.has("setupStatus"))){
                    put(metrics,"skippedPositionOpen",(double)signals.stream().filter(s->s.path(reasonKey).asText().contains("POSITION_ALREADY_OPEN")||s.path(reasonKey).asText().equals("POSITION_OPEN")).count());
                    put(metrics,"skippedCapital",(double)signals.stream().filter(s->s.path(reasonKey).asText().contains("CAPITAL")||s.path(reasonKey).asText().contains("RISK_BUDGET")).count());
                }
            }
            if(weekly&&trades.stream().allMatch(t->t.has("intrabarAmbiguous")))put(metrics,"ambiguities",(double)trades.stream().filter(t->t.path("intrabarAmbiguous").asBoolean()).count());
            if(strategy.equals("TREND_PULLBACK_REBOUND_V2_RESEARCH")&&r.path("tradeAudit").isArray()&&list(r.path("tradeAudit")).stream().allMatch(t->t.path("intrabarAmbiguous").isBoolean()))put(metrics,"ambiguities",(double)list(r.path("tradeAudit")).stream().filter(t->t.path("intrabarAmbiguous").asBoolean()).count());
            if(strategy.equals("TREND_PULLBACK_REBOUND_V3_RESEARCH")&&signals.stream().allMatch(s->s.has("diagnostics")))put(metrics,"ambiguities",(double)signals.stream().filter(s->s.path("diagnostics").hasNonNull("IntrabarAmbiguity")).count());
            if(!curve.isEmpty()&&curve.stream().allMatch(e->num(e.path("exposurePercent"))!=null))put(metrics,"averageGrossExposurePercent",curve.stream().mapToDouble(e->e.path("exposurePercent").asDouble()).average().orElseThrow());
            if(!curve.isEmpty()&&trades.stream().allMatch(t->t.hasNonNull("entryDate"))){
                long active=curve.stream().filter(e->trades.stream().anyMatch(t->e.path("date").asText().compareTo(t.path("entryDate").asText())>=0&&(!t.hasNonNull("exitDate")||e.path("date").asText().compareTo(t.path("exitDate").asText())<=0))).count();put(metrics,"timeInMarketPercent",100.0*active/curve.size());
            }
            put(metrics,"grossPnl",add(metrics.get("realizedGrossPnl"),metrics.get("unrealizedPnl")));
        }
        Double initial=metrics.get("initialCapital"),ending=metrics.get("finalCapital");
        put(metrics,"netPnl",sub(ending,initial));
        if(!pattern)put(metrics,"returnPercent",initial!=null&&initial>0&&ending!=null?(ending/initial-1)*100:null);
        if(!pattern)put(metrics,"netUnrealizedPnl",sub(metrics.get("netPnl"),metrics.get("realizedNetPnl")));
        // Only reconstruct on an actual equity curve, preserving engine metrics when already supplied.
        if(!pattern&&initial!=null&&!curve.isEmpty()&&curve.stream().allMatch(e->num(e.path("equity"))!=null)){
            double peak=initial,dd=0,pct=0;for(var e:curve){double equity=e.path("equity").asDouble();peak=Math.max(peak,equity);dd=Math.max(dd,peak-equity);if(peak>0)pct=Math.max(pct,(peak-equity)/peak*100);}
            put(metrics,"maxDrawdown",dd);put(metrics,"maxDrawdownPercent",pct);
        }
        if(!metrics.containsKey("cagr")&&initial!=null&&initial>0&&ending!=null&&ending>0){
            try{String start=Objects.toString(context.get("Effective Start Date")),end=Objects.toString(context.get("Effective End Date"));long days=ChronoUnit.DAYS.between(LocalDate.parse(start.substring(0,10)),LocalDate.parse(end.substring(0,10)));if(days>0)put(metrics,"cagr",100*(Math.pow(ending/initial,365.25/days)-1));}catch(DateTimeException|IndexOutOfBoundsException ignored){}
        }
        var result=new ArrayList<Metric>();for(var d:DEFINITIONS){String key=d.path("key").asText();Double v=metrics.get(key);result.add(new Metric(key,d.path("label").asText(),d.path("format").asText(),v,v==null?"Not recorded, not applicable, or undefined for this saved run":null));}
        context.put("R Convention",pattern?"Engine price Result R; closed trades":r.has("parameters")?"Engine rMultiple; closed trades":"Engine netR; closed trades");
        context.put("Win Rate Convention","Wins / (Wins + Losses); breakevens excluded");
        context.put("Time In Market Convention",pattern?"Engine session convention":r.has("parameters")?"Engine barsInPosition / totalBars":"Sessions from equity curve intersecting trade entry/exit dates, inclusive");
        return new PortfolioSummaryDto("PORTFOLIO_SUMMARY/1",Collections.unmodifiableMap(context),List.copyOf(result));
    }
}
